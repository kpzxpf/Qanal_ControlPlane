package com.qanal.control.billing.service;

import com.qanal.control.auth.model.Organization;
import com.qanal.control.billing.repository.UsageRecordRepository;
import com.qanal.control.config.QanalProperties;
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

    private UsageRecordRepository repo;
    private StringRedisTemplate   redis;
    private ValueOperations<String, String> valueOps;
    private QuotaEnforcerImpl     enforcer;

    private static final long FREE_LIMIT       = 100L  * 1024 * 1024 * 1024;  // 100 GB
    private static final long PRO_LIMIT        = 10L   * 1024 * 1024 * 1024 * 1024; // 10 TB

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo      = mock(UsageRecordRepository.class);
        redis     = mock(StringRedisTemplate.class);
        valueOps  = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        var props = new QanalProperties(
                new QanalProperties.GrpcServerProps(new QanalProperties.GrpcServerProps.ServerProps(9090)),
                new QanalProperties.TransferProps(24, 100L * 1024 * 1024 * 1024,
                        1024 * 1024, 512L * 1024 * 1024, 1000, 4, 64),
                new QanalProperties.BillingProps(60),
                new QanalProperties.RateLimitProps(1000)
        );
        enforcer = new QuotaEnforcerImpl(repo, redis, props);
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
        when(repo.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(10L * 1024 * 1024 * 1024); // 10 GB used

        assertThatCode(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                5L * 1024 * 1024 * 1024))  // request 5 GB
            .doesNotThrowAnyException();
    }

    @Test
    void free_exceedsQuota_throwsQuotaExceeded() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(repo.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
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
        when(repo.sumBytesForPeriod(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
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

        verifyNoInteractions(repo, valueOps);
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

        verifyNoInteractions(repo);
    }

    @Test
    void cacheHit_exceedsQuota_throwsWithoutDbCall() {
        long cachedBytes = 99L * 1024 * 1024 * 1024; // 99 GB
        when(valueOps.get(anyString())).thenReturn(String.valueOf(cachedBytes));

        assertThatThrownBy(() -> enforcer.assertQuotaAvailable(
                orgWithPlan(Organization.Plan.FREE),
                2L * 1024 * 1024 * 1024))  // 2 GB → total 101 GB > limit
            .isInstanceOf(QuotaExceededException.class);

        verifyNoInteractions(repo);
    }
}
