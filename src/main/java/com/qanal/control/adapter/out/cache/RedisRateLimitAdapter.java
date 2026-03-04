package com.qanal.control.adapter.out.cache;

import com.qanal.control.application.port.out.RateLimitPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * B3 Fix: Atomic INCR + EXPIRE via Lua script.
 *
 * <p>The old implementation did INCR then EXPIRE as two separate commands —
 * if the process died between them the key would never expire (phantom key).
 * The Lua script executes atomically on the Redis server.
 */
@Component
public class RedisRateLimitAdapter implements RateLimitPort {

    private static final String KEY_PREFIX = "rate:";

    private final StringRedisTemplate    redis;
    private final RedisScript<Long>      rateLimitScript;
    private final int                    requestsPerMinute;

    public RedisRateLimitAdapter(StringRedisTemplate redis,
                                  RedisScript<Long> rateLimitScript,
                                  com.qanal.control.infrastructure.config.QanalProperties props) {
        this.redis             = redis;
        this.rateLimitScript   = rateLimitScript;
        this.requestsPerMinute = props.rateLimit().requestsPerMinute();
    }

    @Override
    public boolean isAllowed(String keyPrefix) {
        long   bucket   = Instant.now().getEpochSecond() / 60;
        String redisKey = KEY_PREFIX + keyPrefix + ":" + bucket;

        Long result = redis.execute(
                rateLimitScript,
                List.of(redisKey),
                String.valueOf(requestsPerMinute),
                "120"   // TTL in seconds (2 min grace)
        );

        return result != null && result == 1L;
    }
}
