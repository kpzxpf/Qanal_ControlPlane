package com.qanal.control.application.usecase;

import com.qanal.control.adapter.in.rest.dto.TransferProgressResponse;
import com.qanal.control.application.port.in.FinalizeTransferUseCase;
import com.qanal.control.application.port.out.ChunkCompletionCounter;
import com.qanal.control.application.port.out.ProgressBus;
import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.exception.TransferNotFoundException;
import com.qanal.control.domain.model.TransferStatus;
import com.qanal.control.domain.service.TransferStateMachine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class FinalizeTransferUseCaseImpl implements FinalizeTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizeTransferUseCaseImpl.class);

    private final TransferStore        transferStore;
    private final RelayStore           relayStore;
    private final TransferStateMachine stateMachine;
    private final ProgressBus          progressBus;
    private final ChunkCompletionCounter chunkCounter;

    private final Counter completedCounter;
    private final Counter failedCounter;
    private final Timer   transferDuration;

    public FinalizeTransferUseCaseImpl(TransferStore transferStore,
                                       RelayStore relayStore,
                                       TransferStateMachine stateMachine,
                                       ProgressBus progressBus,
                                       ChunkCompletionCounter chunkCounter,
                                       MeterRegistry meters) {
        this.transferStore = transferStore;
        this.relayStore    = relayStore;
        this.stateMachine  = stateMachine;
        this.progressBus   = progressBus;
        this.chunkCounter  = chunkCounter;

        this.completedCounter = meters.counter("qanal.transfers.completed");
        this.failedCounter    = meters.counter("qanal.transfers.failed");
        this.transferDuration = Timer.builder("qanal.transfer.duration")
                .description("End-to-end transfer duration")
                .register(meters);
    }

    @Override
    @Transactional
    public FinalizeResult finalize(String transferId, String finalChecksum) {
        var transfer = transferStore.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        boolean verified = finalChecksum != null &&
                finalChecksum.equalsIgnoreCase(transfer.getFileChecksum());

        if (verified) {
            transfer.setStatus(stateMachine.transition(transfer.getStatus(), TransferStatus.COMPLETED));
            transfer.setCompletedAt(OffsetDateTime.now());
            transfer.setCompletedChunks(transfer.getTotalChunks());
            freeRelayCapacity(transfer);
            completedCounter.increment();
            if (transfer.getStartedAt() != null) {
                transferDuration.record(java.time.Duration.between(
                        transfer.getStartedAt(), transfer.getCompletedAt()));
            }
            log.info("Transfer {} COMPLETED — checksum verified", transferId);
        } else {
            transfer.setStatus(stateMachine.transition(transfer.getStatus(), TransferStatus.FAILED));
            transfer.setCompletedAt(OffsetDateTime.now());
            freeRelayCapacity(transfer);
            failedCounter.increment();
            log.error("Transfer {} FAILED — checksum mismatch (expected={}, got={})",
                    transferId, transfer.getFileChecksum(), finalChecksum);
        }

        transferStore.save(transfer);
        chunkCounter.delete(transferId);

        // Publish terminal progress event to close all SSE streams
        progressBus.publish(new TransferProgressResponse(
                transferId,
                transfer.getStatus(),
                transfer.getBytesTransferred(),
                transfer.getFileSize(),
                100,
                0.0,
                0,
                0.0,
                System.currentTimeMillis()
        ));

        // Return egress relay info so the ingress DataPlane knows where to forward.
        // egressRelay is null if ingress == egress (same DataPlane handles both roles).
        var egress = transfer.getEgressRelay();
        if (verified && egress != null && !egress.getId().equals(
                transfer.getAssignedRelay() != null ? transfer.getAssignedRelay().getId() : "")) {
            return new FinalizeResult(true,
                    egress.getHost(),
                    egress.getQuicPort(),
                    transfer.getEgressDownloadPort() != null ? transfer.getEgressDownloadPort() : 0);
        }
        return new FinalizeResult(verified, "", 0, 0);
    }

    private void freeRelayCapacity(com.qanal.control.domain.model.Transfer t) {
        if (t.getAssignedRelay() != null) {
            relayStore.subtractUsedBytes(t.getAssignedRelay().getId(), t.getFileSize());
        }
    }
}
