package com.qanal.control.adapter.out.cache;

import com.qanal.control.application.port.out.ChunkCompletionCounter;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 */
@Component
public class RedisChunkCounterAdapter implements ChunkCompletionCounter {

    private static final Duration TTL = Duration.ofHours(24);

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
        Long done  = redis.opsForValue().increment(doneKey(transferId));
        String tot = redis.opsForValue().get(totalKey(transferId));
        if (done == null || tot == null) return false;
        return done >= Long.parseLong(tot);
    }

    @Override
    public void delete(String transferId) {
        redis.delete(List.of(totalKey(transferId), doneKey(transferId)));
    }

    private static String totalKey(String tid) { return "xfer:" + tid + ":total"; }
    private static String doneKey(String tid)  { return "xfer:" + tid + ":done"; }
}
