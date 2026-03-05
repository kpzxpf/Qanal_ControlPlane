package com.qanal.control.adapter.in.rest.dto;

import com.qanal.control.domain.model.TransferStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full transfer representation returned by POST /transfers and GET /transfers/{id}.
 *
 * <p>{@code chunks} is only populated on creation so the sender knows
 * how to split and where to connect.
 */
public record TransferResponse(
        String         id,
        TransferStatus status,
        String         fileName,
        long           fileSize,
        int            totalChunks,
        int            completedChunks,
        int            progressPercent,
        long           bytesTransferred,
        Double         avgThroughputBps,
        // Ingress relay — where the sender uploads to
        String         relayHost,
        int            relayQuicPort,
        // Egress relay — where the recipient downloads from (null = same as ingress)
        String         egressRelayHost,
        Integer        egressDownloadPort,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime completedAt,
        List<ChunkDto> chunks             // null on subsequent GETs
) {

    public record ChunkDto(
            int  chunkIndex,
            long offsetBytes,
            long sizeBytes
    ) {}
}
