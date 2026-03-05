package com.qanal.control.transfer.usecase;

import com.qanal.control.application.port.out.*;
import com.qanal.control.application.usecase.ReportChunkCompletedUseCaseImpl;
import com.qanal.control.domain.model.*;
import com.qanal.control.domain.service.TransferStateMachine;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportChunkCompletedUseCaseImplTest {

    private ChunkStore             chunkStore;
    private TransferStore          transferStore;
    private ChunkCompletionCounter chunkCounter;
    private UsageMeterPort         usageMeter;

    private ReportChunkCompletedUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        chunkStore    = mock(ChunkStore.class);
        transferStore = mock(TransferStore.class);
        chunkCounter  = mock(ChunkCompletionCounter.class);
        usageMeter    = mock(UsageMeterPort.class);

        useCase = new ReportChunkCompletedUseCaseImpl(
                chunkStore, transferStore, chunkCounter, usageMeter,
                new TransferStateMachine());
    }

    @Test
    void report_notLastChunk_doesNotUpdateTransfer() {
        var chunk = pendingChunk("xfer-1", 0);
        when(chunkStore.findByTransferIdAndIndex("xfer-1", 0)).thenReturn(Optional.of(chunk));
        when(chunkStore.save(any())).thenReturn(chunk);
        when(chunkCounter.incrementAndCheck("xfer-1")).thenReturn(false); // not last

        var transfer = transferInProgress("xfer-1");
        when(transferStore.findById("xfer-1")).thenReturn(Optional.of(transfer));

        useCase.report("xfer-1", 0, "hash", 1024L, 1e8, 10L);

        // Transfer row should NOT be updated (only chunk)
        verify(chunkStore).save(chunk);
        verify(transferStore, never()).save(any());
    }

    @Test
    void report_lastChunk_transitionsToCompleting() {
        var chunk = pendingChunk("xfer-2", 3);
        when(chunkStore.findByTransferIdAndIndex("xfer-2", 3)).thenReturn(Optional.of(chunk));
        when(chunkStore.save(any())).thenReturn(chunk);
        when(chunkCounter.incrementAndCheck("xfer-2")).thenReturn(true);  // last chunk!

        var transfer = transferInProgress("xfer-2");
        when(transferStore.findById("xfer-2")).thenReturn(Optional.of(transfer));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.report("xfer-2", 3, "hash", 512L, 2e8, 5L);

        verify(transferStore).save(argThat(t ->
                t.getStatus() == TransferStatus.COMPLETING &&
                t.getCompletedChunks() == t.getTotalChunks() &&
                t.getBytesTransferred() == t.getFileSize()
        ));
    }

    @Test
    void report_duplicateChunk_skipsProcessing() {
        var chunk = completedChunk("xfer-3", 1);
        when(chunkStore.findByTransferIdAndIndex("xfer-3", 1)).thenReturn(Optional.of(chunk));

        useCase.report("xfer-3", 1, "hash", 100L, 1e6, 1L);

        verify(chunkStore, never()).save(any());
        verify(chunkCounter, never()).incrementAndCheck(any());
    }

    @Test
    void report_chunkNotFound_throwsEntityNotFound() {
        when(chunkStore.findByTransferIdAndIndex("xfer-4", 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.report("xfer-4", 99, "hash", 0L, 0, 0L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TransferChunk pendingChunk(String transferId, int index) {
        var t = new Transfer();
        t.setId(transferId);
        var c = new TransferChunk();
        c.setId("chunk-" + index);
        c.setTransfer(t);
        c.setChunkIndex(index);
        c.setStatus(ChunkStatus.PENDING);
        return c;
    }

    private TransferChunk completedChunk(String transferId, int index) {
        var c = pendingChunk(transferId, index);
        c.setStatus(ChunkStatus.COMPLETED);
        return c;
    }

    private Transfer transferInProgress(String id) {
        var org = new Organization();
        org.setId("org-1");
        org.setPlan(Organization.Plan.PRO);

        var t = new Transfer();
        t.setId(id);
        t.setFileSize(4096L);
        t.setTotalChunks(4);
        t.setCompletedChunks(0);
        t.setStatus(TransferStatus.IN_PROGRESS);
        t.setOrganization(org);
        return t;
    }
}
