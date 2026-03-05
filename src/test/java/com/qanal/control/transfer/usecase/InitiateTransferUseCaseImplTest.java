package com.qanal.control.transfer.usecase;

import com.qanal.control.adapter.in.rest.dto.InitiateTransferRequest;
import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.application.port.out.*;
import com.qanal.control.application.usecase.InitiateTransferUseCaseImpl;
import com.qanal.control.domain.exception.QuotaExceededException;
import com.qanal.control.domain.model.*;
import com.qanal.control.domain.service.AdaptiveChunkPlanner;
import com.qanal.control.domain.service.RouteSelectorStrategy;
import com.qanal.control.infrastructure.config.QanalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InitiateTransferUseCaseImplTest {

    private TransferStore         transferStore;
    private ChunkStore            chunkStore;
    private RelayStore            relayStore;
    private QuotaPort             quotaPort;
    private ChunkCompletionCounter chunkCounter;
    private RouteSelectorStrategy routeSelector;

    private InitiateTransferUseCaseImpl useCase;

    private static final long MIN_CHUNK   = 64L  * 1024 * 1024;
    private static final long MAX_CHUNK   = 256L * 1024 * 1024;
    private static final long MAX_CHUNKS  = 1_024;

    @BeforeEach
    void setUp() {
        transferStore  = mock(TransferStore.class);
        chunkStore     = mock(ChunkStore.class);
        relayStore     = mock(RelayStore.class);
        quotaPort      = mock(QuotaPort.class);
        chunkCounter   = mock(ChunkCompletionCounter.class);
        routeSelector  = mock(RouteSelectorStrategy.class);

        var planner = new AdaptiveChunkPlanner(MIN_CHUNK, MAX_CHUNK, MAX_CHUNKS);
        var props   = new QanalProperties(
                new QanalProperties.GrpcServerProps(new QanalProperties.GrpcServerProps.ServerProps(9090)),
                new QanalProperties.TransferProps(24, 100L * 1024 * 1024 * 1024,
                        MIN_CHUNK, MAX_CHUNK, (int) MAX_CHUNKS, 4, 32),
                new QanalProperties.BillingProps(60),
                new QanalProperties.RateLimitProps(100),
                new QanalProperties.StripeProps("sk_test", "whsec", "price_pro",
                        "https://x/success", "https://x/cancel"),
                new QanalProperties.AdminProps("secret")
        );

        useCase = new InitiateTransferUseCaseImpl(
                transferStore, chunkStore, relayStore,
                planner, routeSelector, quotaPort, chunkCounter, props);
    }

    @Test
    void initiate_happyPath_returnsPlanWithChunks() {
        var relay = relay("relay-1", "us-east-1", "relay.qanal.io", 4433);
        when(routeSelector.select(anyString(), anyString(), anyLong()))
                .thenReturn(Optional.of(relay));
        when(transferStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new InitiateTransferRequest(
                "bigfile.zip", 1L * 1024 * 1024 * 1024,  // 1 GB
                "aabbcc", "us-east-1", "eu-west-1",
                1_000_000_000L, 50);
        var org = orgWithPlan(Organization.Plan.PRO);

        TransferResponse resp = useCase.initiate(req, org);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.totalChunks()).isGreaterThan(0);
        assertThat(resp.relayHost()).isEqualTo("relay.qanal.io");
        assertThat(resp.relayQuicPort()).isEqualTo(4433);

        // chunks should cover the full file
        long covered = resp.chunks().stream().mapToLong(TransferResponse.ChunkDto::sizeBytes).sum();
        assertThat(covered).isEqualTo(1L * 1024 * 1024 * 1024);

        verify(chunkCounter).initialize(any(), eq(resp.totalChunks()));
    }

    @Test
    void initiate_exceedsMaxFileSize_throwsIllegalArgument() {
        var req = new InitiateTransferRequest(
                "huge.bin", Long.MAX_VALUE, "xx", null, null, null, null);
        var org = orgWithPlan(Organization.Plan.ENTERPRISE);

        assertThatThrownBy(() -> useCase.initiate(req, org))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void initiate_quotaExceeded_propagatesException() {
        doThrow(new QuotaExceededException("quota exceeded"))
                .when(quotaPort).assertQuotaAvailable(any(), anyLong());

        var req = new InitiateTransferRequest(
                "file.zip", 1024L, "xx", null, null, null, null);

        assertThatThrownBy(() -> useCase.initiate(req, orgWithPlan(Organization.Plan.FREE)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void initiate_noRelayAvailable_throwsIllegalState() {
        when(routeSelector.select(any(), any(), anyLong())).thenReturn(Optional.empty());
        doNothing().when(quotaPort).assertQuotaAvailable(any(), anyLong());

        var req = new InitiateTransferRequest(
                "file.zip", 1024L, "xx", "us-east-1", "eu-west-1", null, null);

        assertThatThrownBy(() -> useCase.initiate(req, orgWithPlan(Organization.Plan.PRO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No healthy relay");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Organization orgWithPlan(Organization.Plan plan) {
        var org = new Organization();
        org.setId("org-test");
        org.setName("Test Corp");
        org.setPlan(plan);
        return org;
    }

    private RelayNode relay(String id, String region, String host, int port) {
        var r = new RelayNode();
        r.setId(id);
        r.setRegion(region);
        r.setHost(host);
        r.setQuicPort(port);
        r.setStatus(RelayStatus.HEALTHY);
        r.setCapacityBytes(Long.MAX_VALUE);
        r.setUsedBytes(0);
        return r;
    }
}
