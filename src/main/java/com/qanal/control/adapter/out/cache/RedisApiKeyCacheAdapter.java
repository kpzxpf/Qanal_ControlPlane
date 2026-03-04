package com.qanal.control.adapter.out.cache;

import com.qanal.control.adapter.out.persistence.ApiKeyStoreJpaAdapter;
import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.domain.model.ApiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * B7 Fix: Redis cache-aside decorator over JPA adapter.
 *
 * <p>Cache key: {@code apikey:{prefix}}, TTL 5 minutes.
 * On cache miss → load from DB → cache result.
 * On updateLastUsed → does NOT evict (timestamp update is non-critical).
 */
@Component
@Primary
public class RedisApiKeyCacheAdapter implements ApiKeyStore {

    private static final Logger   log      = LoggerFactory.getLogger(RedisApiKeyCacheAdapter.class);
    private static final Duration TTL      = Duration.ofMinutes(5);
    private static final String   KEY_FMT  = "apikey:%s";

    private final ApiKeyStoreJpaAdapter delegate;
    private final StringRedisTemplate   redis;
    private final ObjectMapper          mapper;

    public RedisApiKeyCacheAdapter(ApiKeyStoreJpaAdapter delegate,
                                    StringRedisTemplate redis,
                                    ObjectMapper mapper) {
        this.delegate = delegate;
        this.redis    = redis;
        this.mapper   = mapper;
    }

    @Override
    public Optional<ApiKey> findActiveByPrefix(String prefix) {
        String cacheKey = KEY_FMT.formatted(prefix);
        String cached   = redis.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return Optional.of(mapper.readValue(cached, ApiKey.class));
            } catch (IOException e) {
                log.warn("Failed to deserialise cached ApiKey for prefix {}", prefix, e);
                redis.delete(cacheKey);
            }
        }

        Optional<ApiKey> result = delegate.findActiveByPrefix(prefix);
        result.ifPresent(key -> {
            try {
                redis.opsForValue().set(cacheKey, mapper.writeValueAsString(key), TTL);
            } catch (IOException e) {
                log.warn("Failed to cache ApiKey for prefix {}", prefix, e);
            }
        });

        return result;
    }

    @Override
    public void updateLastUsed(String id, OffsetDateTime ts) {
        delegate.updateLastUsed(id, ts);
        // We don't evict cache on last-used update — it's non-critical metadata
    }
}
