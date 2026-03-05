package com.qanal.control.adapter.out.cache;

import com.qanal.control.application.port.out.ChunkCompletionCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * B6 Fix: Redis-based atomic chunk completion counter.
 *
 * <p>Keys:
 * <ul>
 *   <li>{@code xfer:{id}:total} — total expected chunks</li>
 *   <li>{@code xfer:{id}:done}  — completed chunks so far</li>
 * </ul>
 * Both keys expire after 24 h to prevent orphan keys on abnormal termination.
 *
 * <p>BUG-5 Fix: {@code incrementAndCheck} now uses a single Lua script so that
 * INCR and GET are executed atomically. The previous two-command implementation
 * had a window where the {@code total} key could expire between the two calls,
 * causing the counter to never report completion.
 */
@Component
public class RedisChunkCounterAdapter implements ChunkCompletionCounter {

    private static final Logger   log = LoggerFactory.getLogger(RedisChunkCounterAdapter.class);
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Lua script: atomically increments the done-counter and compares it to the total.
     * Returns:
     * <ul>
     *   <li> 1 — all chunks done ({@code done >= total})</li>
     *   <li> 0 — not done yet</li>
     *   <li>-1 — keys missing (expired or never initialized)</li>
     * </ul>
     * KEYS[1] = doneKey, KEYS[2] = totalKey
     */
    private static final RedisScript<Long> INCREMENT_AND_CHECK = RedisScript.of("""
            local done = redis.call('INCR', KEYS[1])
            local tot  = redis.call('GET',  KEYS[2])
            if not tot then return -1 end
            if tonumber(done) >= tonumber(tot) then return 1 else return 0 end
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisChunkCounterAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void initialize(String transferId, int totalChunks) {
        redis.opsForValue().set(totalKey(transferId), String.valueOf(totalChunks), TTL);
        redis.opsForValue().set(doneKey(transferId),  "0",                         TTL);
    }

    @Override
    public boolean incrementAndCheck(String transferId) {
        Long result = redis.execute(
                INCREMENT_AND_CHECK,
                List.of(doneKey(transferId), totalKey(transferId)));
        if (result == null || result < 0) {
            log.warn("Redis chunk counter keys missing for transfer {} — keys may have expired", transferId);
            return false;
        }
        return result > 0;
    }

    @Override
    public void delete(String transferId) {
        redis.delete(List.of(totalKey(transferId), doneKey(transferId)));
    }

    private static String totalKey(String tid) { return "xfer:" + tid + ":total"; }
    private static String doneKey(String tid)  { return "xfer:" + tid + ":done"; }
}
