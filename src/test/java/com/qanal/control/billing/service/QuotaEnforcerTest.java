package com.qanal.control.billing.service;

import com.qanal.control.adapter.out.cache.RedisQuotaAdapter;
import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.domain.exception.QuotaExceededException;
import com.qanal.control.domain.model.Organization;
import com.qanal.control.infrastructure.config.QanalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuotaEnforcerTest {

    private UsageStore                      usageStore;
    private StringRedisTemplate             redis;
    private ValueOperations<String, String> valueOps;
    private RedisQuotaAdapter               enforcer;

    private static final long FREE_LIMIT = 100L  * 1024 * 1024 * 1024;       // 100 GB
    private static final long PRO_LIMIT  = 10L   * 1024 * 1024 * 1024 * 1024; // 10 TB

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        usageStore = mock(UsageStore.class);
        redis      = mock(StringRedisTemplate.class);
        valueOps   = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        var props = new QanalProperties(
                new QanalProperties.GrpcServerProps(new QanalProperties.GrpcServerProps.ServerProps(9090)),
                new QanalProperties.TransferProps(24, 100L * 1024 * 1024 * 1024,
                        64L * 1024 * 1024, 256L * 1024 * 1024, 1024, 4, 32),
                new QanalProperties.BillingProps(60),
                new QanalProperties.RateLimitProps(100),
                new QanalProperties.StripeProps("sk_test_x", "whsec_x", "price_x",
                        "https://qanal.io/success", "https://qanal.io/cancel"),
                new QanalProperties.AdminProps("test-secret")
        );
        enforcer = new RedisQuotaAdapter(usageStore, redis, props);
    }

    private Organization orgWithPlan(Organization.Plan plan) {
        var org = new Organization();
        org.setId("org-" + plan.name().toLowerCase());
        org.setName("Test Org");
        org.setPlan(plan);
        return org;
    }

    // ── FREE plan ─────────────────────────────────────────────────────────────

    @Test
    void free_withinQuota_passes() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(usageStore.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(10L * 1024 * 1024 * 1024); // 10 GB used

        assertThatCode(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                5L * 1024 * 1024 * 1024))  // request 5 GB
            .doesNotThrowAnyException();
    }

    @Test
    void free_exceedsQuota_throwsQuotaExceeded() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(usageStore.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(98L * 1024 * 1024 * 1024); // 98 GB already used

        assertThatThrownBy(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                5L * 1024 * 1024 * 1024))  // request 5 GB → total 103 GB > 100 GB
            .isInstanceOf(QuotaExceededException.class)
            .hasMessageContaining("quota exceeded");
    }

    // ── PRO plan ──────────────────────────────────────────────────────────────

    @Test
    void pro_withinQuota_passes() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(usageStore.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(1L * 1024 * 1024 * 1024 * 1024); // 1 TB used

        assertThatCode(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.PRO),
                500L * 1024 * 1024 * 1024))  // 500 GB
            .doesNotThrowAnyException();
    }

    // ── ENTERPRISE plan ───────────────────────────────────────────────────────

    @Test
    void enterprise_alwaysPasses_noDbCall() {
        assertThatCode(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.ENTERPRISE),
                Long.MAX_VALUE / 2))
            .doesNotThrowAnyException();

        verifyNoInteractions(usageStore, valueOps);
    }

    // ── Redis cache ───────────────────────────────────────────────────────────

    @Test
    void cachedUsage_usedWithoutDbCall() {
        long cachedBytes = 50L * 1024 * 1024 * 1024; // 50 GB
        when(valueOps.get(anyString())).thenReturn(String.valueOf(cachedBytes));

        assertThatCode(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                1L * 1024 * 1024 * 1024))  // 1 GB
            .doesNotThrowAnyException();

        verifyNoInteractions(usageStore);
    }

    @Test
    void cacheHit_exceedsQuota_throwsWithoutDbCall() {
        long cachedBytes = 99L * 1024 * 1024 * 1024; // 99 GB
        when(valueOps.get(anyString())).thenReturn(String.valueOf(cachedBytes));

        assertThatThrownBy(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                2L * 1024 * 1024 * 1024))  // 2 GB → total 101 GB > limit
            .isInstanceOf(QuotaExceededException.class);

        verifyNoInteractions(usageStore);
    }
}
