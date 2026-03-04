package com.qanal.control.application.usecase;

import com.qanal.control.adapter.in.rest.dto.InitiateTransferRequest;
import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.application.port.in.InitiateTransferUseCase;
import com.qanal.control.application.port.out.*;
import com.qanal.control.domain.model.*;
import com.qanal.control.domain.service.ChunkPlannerStrategy;
import com.qanal.control.domain.service.RouteSelectorStrategy;
import com.qanal.control.infrastructure.config.QanalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class InitiateTransferUseCaseImpl implements InitiateTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateTransferUseCaseImpl.class);

    private final TransferStore        transferStore;
    private final ChunkStore           chunkStore;
    private final RelayStore           relayStore;
    private final ChunkPlannerStrategy chunkPlanner;
    private final RouteSelectorStrategy routeSelector;
    private final QuotaPort            quotaPort;
    private final ChunkCompletionCounter chunkCounter;
    private final QanalProperties      props;

    public InitiateTransferUseCaseImpl(TransferStore transferStore,
                                       ChunkStore chunkStore,
                                       RelayStore relayStore,
                                       ChunkPlannerStrategy chunkPlanner,
                                       RouteSelectorStrategy routeSelector,
                                       QuotaPort quotaPort,
                                       ChunkCompletionCounter chunkCounter,
                                       QanalProperties props) {
        this.transferStore = transferStore;
        this.chunkStore    = chunkStore;
        this.relayStore    = relayStore;
        this.chunkPlanner  = chunkPlanner;
        this.routeSelector = routeSelector;
        this.quotaPort     = quotaPort;
        this.chunkCounter  = chunkCounter;
        this.props         = props;
    }

    @Override
    @Transactional
    public TransferResponse initiate(InitiateTransferRequest req, Organization org) {
        // 1 — Max file size check
        if (req.fileSize() > props.transfer().maxFileSizeBytes()) {
            throw new IllegalArgumentException(
                    "File size %d exceeds maximum allowed %d bytes"
                            .formatted(req.fileSize(), props.transfer().maxFileSizeBytes()));
        }

        // 2 — Quota check (fast path: Redis-cached)
        quotaPort.assertQuotaAvailable(org, req.fileSize());

        // 3 — Route selection
        RelayNode relay = routeSelector
                .select(req.sourceRegion(), req.targetRegion(), req.fileSize())
                .orElseThrow(() -> new IllegalStateException("No healthy relay node available"));

        // 4 — Build transfer aggregate
        var transfer = new Transfer();
        transfer.setOrganization(org);
        transfer.setFileName(req.fileName());
        transfer.setFileSize(req.fileSize());
        transfer.setFileChecksum(req.fileChecksum());
        transfer.setSourceRegion(req.sourceRegion());
        transfer.setTargetRegion(req.targetRegion());
        transfer.setAssignedRelay(relay);
        transfer.setStatus(TransferStatus.INITIATED);
        transfer.setExpiresAt(OffsetDateTime.now()
                .plusHours(props.transfer().defaultExpiryHours()));

        // 5 — Plan chunks
        long bwBps = req.estimatedBandwidthBps() != null ? req.estimatedBandwidthBps() : 1_000_000_000L;
        int  rttMs = req.estimatedRttMs()         != null ? req.estimatedRttMs()        : 50;
        List<TransferChunk> chunks = chunkPlanner.plan(transfer, bwBps, rttMs);

        // 6 — Persist
        Transfer saved = transferStore.save(transfer);
        chunkStore.saveAll(chunks);
        relayStore.addUsedBytes(relay.getId(), req.fileSize());

        // 7 — Initialize Redis completion counter (B6 fix)
        chunkCounter.initialize(saved.getId(), chunks.size());

        log.info("Transfer {} initiated: {} chunks, relay {}:{}",
                saved.getId(), chunks.size(), relay.getHost(), relay.getQuicPort());

        return toResponse(saved, chunks);
    }

    private TransferResponse toResponse(Transfer t, List<TransferChunk> chunks) {
        var relay = t.getAssignedRelay();
        List<TransferResponse.ChunkDto> chunkDtos = chunks == null ? null :
                chunks.stream()
                        .map(c -> new TransferResponse.ChunkDto(
                                c.getChunkIndex(), c.getOffsetBytes(), c.getSizeBytes()))
                        .toList();

        return new TransferResponse(
                t.getId(),
                t.getStatus(),
                t.getFileName(),
                t.getFileSize(),
                t.getTotalChunks(),
                t.getCompletedChunks(),
                t.progressPercent(),
                t.getBytesTransferred(),
                t.getAvgThroughput(),
                relay != null ? relay.getHost() : null,
                relay != null ? relay.getQuicPort() : 0,
                t.getCreatedAt(),
                t.getExpiresAt(),
                t.getCompletedAt(),
                chunkDtos
        );
    }
}
