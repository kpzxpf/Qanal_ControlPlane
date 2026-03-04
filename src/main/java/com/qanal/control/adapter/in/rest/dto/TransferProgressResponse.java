package com.qanal.control.adapter.in.rest.dto;

import com.qanal.control.domain.model.TransferStatus;

/**
 * SSE event payload for real-time progress updates.
 */
public record TransferProgressResponse(
        String         transferId,
        TransferStatus status,
        long           bytesTransferred,
        long           totalBytes,
        int            progressPercent,
        double         throughputBps,
        int            activeStreams,
        double         packetLossRate,
        long           timestampMs
) {}
