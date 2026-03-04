package com.qanal.control.transfer.service;

import com.qanal.control.config.QanalProperties;
import com.qanal.control.transfer.model.Transfer;
import com.qanal.control.transfer.model.TransferChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveChunkPlannerTest {

    // Props: min=1MB, max=512MB, maxChunks=1000
    private static final long MIN_CHUNK   = 1L  * 1024 * 1024;
    private static final long MAX_CHUNK   = 512L * 1024 * 1024;
    private static final int  MAX_CHUNKS  = 1_000;

    private AdaptiveChunkPlanner planner;

    @BeforeEach
    void setUp() {
        var transferProps = new QanalProperties.TransferProps(
                24,          // defaultExpiryHours
                100L * 1024 * 1024 * 1024,  // maxFileSizeBytes 100GB
                MIN_CHUNK,
                MAX_CHUNK,
                MAX_CHUNKS,
                4,           // minParallelStreams
                64           // maxParallelStreams
        );
        var props = new QanalProperties(
                new QanalProperties.GrpcServerProps(new QanalProperties.GrpcServerProps.ServerProps(9090)),
                transferProps,
                new QanalProperties.BillingProps(60),
                new QanalProperties.RateLimitProps(1000)
        );
        planner = new AdaptiveChunkPlanner(props);
    }

    private Transfer transferOf(long fileSize) {
        var t = new Transfer();
        t.setFileSize(fileSize);
        return t;
    }

    @Test
    void smallFile_singleChunk() {
        long fileSize = 512 * 1024; // 512 KB — smaller than min chunk
        Transfer t = transferOf(fileSize);

        List<TransferChunk> chunks = planner.plan(t, 1_000_000_000L, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getSizeBytes()).isEqualTo(fileSize);
        assertThat(chunks.get(0).getOffsetBytes()).isZero();
        assertThat(t.getTotalChunks()).isEqualTo(1);
    }

    @Test
    void chunks_coverFullFileSize() {
        long fileSize = 10L * 1024 * 1024 * 1024; // 10 GB
        Transfer t = transferOf(fileSize);

        List<TransferChunk> chunks = planner.plan(t, 10_000_000_000L, 20);

        long totalCovered = chunks.stream().mapToLong(TransferChunk::getSizeBytes).sum();
        assertThat(totalCovered).isEqualTo(fileSize);
    }

    @Test
    void chunks_haveCorrectSequentialOffsets() {
        long fileSize = 5L * 1024 * 1024 * 1024;
        Transfer t = transferOf(fileSize);

        List<TransferChunk> chunks = planner.plan(t, 1_000_000_000L, 50);

        long expectedOffset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            TransferChunk chunk = chunks.get(i);
            assertThat(chunk.getChunkIndex()).isEqualTo(i);
            assertThat(chunk.getOffsetBytes()).isEqualTo(expectedOffset);
            expectedOffset += chunk.getSizeBytes();
        }
    }

    @Test
    void chunkSize_clampedToMin_onSlowNetwork() {
        long fileSize = 1L * 1024 * 1024 * 1024; // 1 GB
        Transfer t = transferOf(fileSize);

        // Very slow: 10 Kbps → BDP very small → should clamp to MIN_CHUNK
        List<TransferChunk> chunks = planner.plan(t, 10_000L, 500);

        assertThat(chunks).isNotEmpty();
        chunks.forEach(c -> assertThat(c.getSizeBytes()).isGreaterThanOrEqualTo(MIN_CHUNK));
    }

    @Test
    void chunkSize_clampedToMax_onFastNetwork() {
        long fileSize = 100L * 1024 * 1024 * 1024; // 100 GB
        Transfer t = transferOf(fileSize);

        // Very fast: 400 Gbps → BDP enormous → should clamp to MAX_CHUNK
        List<TransferChunk> chunks = planner.plan(t, 400_000_000_000L, 100);

        chunks.forEach(c -> assertThat(c.getSizeBytes()).isLessThanOrEqualTo(MAX_CHUNK));
    }

    @Test
    void doesNotExceedMaxChunks() {
        long fileSize = 100L * 1024 * 1024 * 1024; // 100 GB
        Transfer t = transferOf(fileSize);

        // 1 Mbps, 10ms RTT → tiny BDP → many chunks → must be capped
        List<TransferChunk> chunks = planner.plan(t, 1_000_000L, 10);

        assertThat(chunks.size()).isLessThanOrEqualTo(MAX_CHUNKS);
    }
}
