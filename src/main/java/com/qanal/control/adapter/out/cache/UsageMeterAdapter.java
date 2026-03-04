package com.qanal.control.adapter.out.cache;

import com.qanal.control.application.port.out.UsageMeterPort;
import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.domain.model.Organization;
import com.qanal.control.domain.model.UsageRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * B8 Fix: Evicts quota cache after recording usage.
 *
 * <p>Old implementation wrote UsageRecord but never invalidated quota:{orgId},
 * so the cached quota value remained stale until TTL expired.
 */
@Component
public class UsageMeterAdapter implements UsageMeterPort {

    private final UsageStore          usageStore;
    private final StringRedisTemplate redis;

    public UsageMeterAdapter(UsageStore usageStore, StringRedisTemplate redis) {
        this.usageStore = usageStore;
        this.redis      = redis;
    }

    @Override
    @Retryable(retryFor = Exception.class, maxAttempts = 3,
               backoff = @Backoff(delay = 200, multiplier = 2))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Organization org, String transferId, long bytes) {
        usageStore.save(UsageRecord.of(org, transferId, bytes));
        // B8 Fix: invalidate quota cache so next check reads fresh value from DB
        redis.delete("quota:" + org.getId());
    }
}
