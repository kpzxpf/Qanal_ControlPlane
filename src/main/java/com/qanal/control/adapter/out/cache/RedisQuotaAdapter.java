package com.qanal.control.adapter.out.cache;

import com.qanal.control.application.port.out.QuotaPort;
import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.domain.exception.QuotaExceededException;
import com.qanal.control.domain.model.Organization;
import com.qanal.control.infrastructure.config.QanalProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

/**
 * Quota limits by plan (bytes/month).
 * Cached in Redis for configurable TTL to avoid per-request DB aggregations.
 */
@Component
public class RedisQuotaAdapter implements QuotaPort {

    private static final Map<Organization.Plan, Long> PLAN_LIMITS = Map.of(
            Organization.Plan.FREE,       100L  * 1024 * 1024 * 1024,
            Organization.Plan.PRO,        10L   * 1024 * 1024 * 1024 * 1024,
            Organization.Plan.ENTERPRISE, Long.MAX_VALUE
    );

    private final UsageStore          usageStore;
    private final StringRedisTemplate redis;
    private final long                cacheTtlSeconds;

    public RedisQuotaAdapter(UsageStore usageStore,
                              StringRedisTemplate redis,
                              QanalProperties props) {
        this.usageStore      = usageStore;
        this.redis           = redis;
        this.cacheTtlSeconds = props.billing().quotaCacheTtlSeconds();
    }

    @Override
    public void assertQuotaAvailable(Organization org, long requestedBytes) {
        long limit = PLAN_LIMITS.getOrDefault(org.getPlan(), 0L);
        if (limit == Long.MAX_VALUE) return;

        long used = getUsedBytes(org.getId());
        if (used + requestedBytes > limit) {
            throw new QuotaExceededException(
                    "Monthly quota exceeded for organisation %s (plan=%s, used=%d, limit=%d)"
                            .formatted(org.getId(), org.getPlan(), used, limit));
        }
    }

    private long getUsedBytes(String orgId) {
        String cacheKey = "quota:" + orgId;
        String cached   = redis.opsForValue().get(cacheKey);
        if (cached != null) return Long.parseLong(cached);

        OffsetDateTime now   = OffsetDateTime.now();
        OffsetDateTime start = now.with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end   = now.with(TemporalAdjusters.firstDayOfNextMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        long bytes = usageStore.sumBytesForPeriod(orgId, start, end);
        redis.opsForValue().set(cacheKey, String.valueOf(bytes), Duration.ofSeconds(cacheTtlSeconds));
        return bytes;
    }
}
