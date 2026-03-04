package com.qanal.control.domain.service;

import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * BDP-based adaptive chunk planner.
 *
 * <p>No Spring annotations — registered as @Bean in AppConfig with primitives injected from QanalProperties.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>BDP (bytes) = (bandwidthBps / 8) × (rttMs / 1000)</li>
 *   <li>optimal chunk = 4 × BDP  (keeps the pipeline full even under loss)</li>
 *   <li>clamp to [{@code minChunkSizeBytes}, {@code maxChunkSizeBytes}]</li>
 *   <li>if chunk count would exceed {@code maxChunks}: enlarge chunk to fileSize / maxChunks</li>
 * </ol>
 */
public class AdaptiveChunkPlanner implements ChunkPlannerStrategy {

    private final long minChunkSizeBytes;
    private final long maxChunkSizeBytes;
    private final long maxChunks;

    public AdaptiveChunkPlanner(long minChunkSizeBytes, long maxChunkSizeBytes, long maxChunks) {
        this.minChunkSizeBytes = minChunkSizeBytes;
        this.maxChunkSizeBytes = maxChunkSizeBytes;
        this.maxChunks         = maxChunks;
    }

    @Override
    public List<TransferChunk> plan(Transfer transfer, long bandwidthBps, int rttMs) {
        long fileSize = transfer.getFileSize();

        long chunkSize = computeChunkSize(bandwidthBps, rttMs, fileSize);

        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        transfer.setTotalChunks(totalChunks);

        List<TransferChunk> chunks = new ArrayList<>(totalChunks);
        long offset = 0;
        for (int i = 0; i < totalChunks; i++) {
            long size = Math.min(chunkSize, fileSize - offset);
            chunks.add(TransferChunk.of(transfer, i, offset, size));
            offset += size;
        }
        return chunks;
    }

    private long computeChunkSize(long bandwidthBps, int rttMs, long fileSize) {
        // BDP in bytes
        double bdpBytes = (bandwidthBps / 8.0) * (rttMs / 1000.0);

        // Optimal chunk = 4 × BDP, clamped
        long chunk = clamp(
                (long) (4 * bdpBytes),
                minChunkSizeBytes,
                maxChunkSizeBytes
        );

        // Don't exceed maxChunks
        if (fileSize > chunk * maxChunks) {
            chunk = (long) Math.ceil((double) fileSize / maxChunks);
        }

        // Edge case: file smaller than one chunk
        return Math.min(chunk, fileSize);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
