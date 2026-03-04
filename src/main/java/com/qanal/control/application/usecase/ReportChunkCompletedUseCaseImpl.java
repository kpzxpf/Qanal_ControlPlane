package com.qanal.control.application.usecase;

import com.qanal.control.application.port.in.ReportChunkCompletedUseCase;
import com.qanal.control.application.port.out.ChunkCompletionCounter;
import com.qanal.control.application.port.out.ChunkStore;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.application.port.out.UsageMeterPort;
import com.qanal.control.domain.exception.TransferNotFoundException;
import com.qanal.control.domain.model.ChunkStatus;
import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import com.qanal.control.domain.service.TransferStateMachine;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * B6 Fix: Eliminates optimistic-lock storm.
 *
 * <p>Instead of doing 1024 UPDATE version+1 in parallel (old behaviour),
 * the counter is managed in Redis. The DB Transfer row is updated exactly
 * once — when the Redis counter reaches totalChunks.
 */
@Service
public class ReportChunkCompletedUseCaseImpl implements ReportChunkCompletedUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportChunkCompletedUseCaseImpl.class);

    private final ChunkStore             chunkStore;
    private final TransferStore          transferStore;
    private final ChunkCompletionCounter chunkCounter;
    private final UsageMeterPort         usageMeterPort;
    private final TransferStateMachine   stateMachine;

    public ReportChunkCompletedUseCaseImpl(ChunkStore chunkStore,
                                            TransferStore transferStore,
                                            ChunkCompletionCounter chunkCounter,
                                            UsageMeterPort usageMeterPort,
                                            TransferStateMachine stateMachine) {
        this.chunkStore     = chunkStore;
        this.transferStore  = transferStore;
        this.chunkCounter   = chunkCounter;
        this.usageMeterPort = usageMeterPort;
        this.stateMachine   = stateMachine;
    }

    @Override
    @Transactional
    public void report(String transferId, int chunkIndex, String checksum,
                       long bytes, double throughputBps, long durationMs) {

        // 1 — Find chunk; idempotency guard
        var chunk = chunkStore.findByTransferIdAndIndex(transferId, chunkIndex)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Chunk %d not found for transfer %s".formatted(chunkIndex, transferId)));

        if (chunk.getStatus() == ChunkStatus.COMPLETED) {
            log.debug("Duplicate chunk completion for {}/{} — ignored", transferId, chunkIndex);
            return;
        }

        // 2 — Mark chunk done in DB (one row, no version conflict)
        chunk.setChecksum(checksum);
        chunk.setStatus(ChunkStatus.COMPLETED);
        chunkStore.save(chunk);

        // 3 — Atomically increment Redis counter; check if all done (B6 fix)
        boolean allDone = chunkCounter.incrementAndCheck(transferId);

        if (allDone) {
            // 4 — Only ONE thread ever reaches here — update Transfer row exactly once
            var transfer = transferStore.findById(transferId)
                    .orElseThrow(() -> new TransferNotFoundException(transferId));

            if (transfer.getStatus() == TransferStatus.INITIATED ||
                    transfer.getStatus() == TransferStatus.WAITING_SENDER) {
                transfer.setStatus(TransferStatus.IN_PROGRESS);
                transfer.setStartedAt(OffsetDateTime.now());
            }
            transfer.setBytesTransferred(transfer.getBytesTransferred() + bytes);
            updateThroughput(transfer, throughputBps);

            transfer.setStatus(stateMachine.transition(transfer.getStatus(), TransferStatus.COMPLETING));
            transferStore.save(transfer);

            log.info("Transfer {} all chunks received — transitioning to COMPLETING", transferId);
        }

        // 5 — Record usage asynchronously (REQUIRES_NEW — doesn't block main path)
        var transfer = transferStore.findById(transferId).orElse(null);
        if (transfer != null) {
            usageMeterPort.record(transfer.getOrganization(), transferId, bytes);
        }
    }

    private void updateThroughput(Transfer transfer, double throughputBps) {
        if (transfer.getAvgThroughput() == null) {
            transfer.setAvgThroughput(throughputBps);
        } else {
            transfer.setAvgThroughput(transfer.getAvgThroughput() * 0.8 + throughputBps * 0.2);
        }
    }
}
