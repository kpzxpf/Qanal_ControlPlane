package com.qanal.control.transfer.usecase;

import com.qanal.control.application.port.out.*;
import com.qanal.control.application.usecase.FinalizeTransferUseCaseImpl;
import com.qanal.control.domain.exception.TransferNotFoundException;
import com.qanal.control.domain.model.*;
import com.qanal.control.domain.service.TransferStateMachine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinalizeTransferUseCaseImplTest {

    private TransferStore          transferStore;
    private RelayStore             relayStore;
    private ProgressBus            progressBus;
    private ChunkCompletionCounter chunkCounter;

    private FinalizeTransferUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        transferStore = mock(TransferStore.class);
        relayStore    = mock(RelayStore.class);
        progressBus   = mock(ProgressBus.class);
        chunkCounter  = mock(ChunkCompletionCounter.class);

        useCase = new FinalizeTransferUseCaseImpl(
                transferStore, relayStore, new TransferStateMachine(),
                progressBus, chunkCounter, new SimpleMeterRegistry());
    }

    @Test
    void finalize_checksumMatch_setsCompleted() {
        var transfer = buildTransfer("xfer-1", "aabbccdd");
        when(transferStore.findById("xfer-1")).thenReturn(Optional.of(transfer));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.finalize("xfer-1", "aabbccdd");

        assertThat(result.verified()).isTrue();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCompletedAt()).isNotNull();
        verify(chunkCounter).delete("xfer-1");
        verify(progressBus).publish(any());
    }

    @Test
    void finalize_checksumMismatch_setsFailed() {
        var transfer = buildTransfer("xfer-2", "aabbccdd");
        when(transferStore.findById("xfer-2")).thenReturn(Optional.of(transfer));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.finalize("xfer-2", "WRONG_HASH");

        assertThat(result.verified()).isFalse();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        verify(chunkCounter).delete("xfer-2");
    }

    @Test
    void finalize_nullChecksum_setsFailed() {
        var transfer = buildTransfer("xfer-3", "aabbccdd");
        when(transferStore.findById("xfer-3")).thenReturn(Optional.of(transfer));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.finalize("xfer-3", null);

        assertThat(result.verified()).isFalse();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void finalize_notFound_throws() {
        when(transferStore.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.finalize("missing", "hash"))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    void finalize_withEgressRelay_returnsEgressInfo() {
        var ingress = relay("relay-1", "us-east-1", "ingress.qanal.io", 4433);
        var egress  = relay("relay-2", "eu-west-1", "egress.qanal.io",  4433);

        var transfer = buildTransfer("xfer-4", "checksum42");
        transfer.setAssignedRelay(ingress);
        transfer.setEgressRelay(egress);
        transfer.setEgressDownloadPort(4434);

        when(transferStore.findById("xfer-4")).thenReturn(Optional.of(transfer));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.finalize("xfer-4", "checksum42");

        assertThat(result.verified()).isTrue();
        assertThat(result.egressHost()).isEqualTo("egress.qanal.io");
        assertThat(result.egressPort()).isEqualTo(4433);
        assertThat(result.downloadPort()).isEqualTo(4434);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transfer buildTransfer(String id, String checksum) {
        var relay = relay("relay-1", "us-east-1", "relay.qanal.io", 4433);
        var t     = new Transfer();
        t.setId(id);
        t.setFileSize(1_000_000L);
        t.setFileChecksum(checksum);
        t.setStatus(TransferStatus.COMPLETING);
        t.setTotalChunks(4);
        t.setAssignedRelay(relay);
        return t;
    }

    private RelayNode relay(String id, String region, String host, int port) {
        var r = new RelayNode();
        r.setId(id);
        r.setRegion(region);
        r.setHost(host);
        r.setQuicPort(port);
        r.setCapacityBytes(Long.MAX_VALUE);
        r.setUsedBytes(0);
        return r;
    }
}
