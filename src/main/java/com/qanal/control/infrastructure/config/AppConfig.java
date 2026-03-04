package com.qanal.control.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.domain.service.AdaptiveChunkPlanner;
import com.qanal.control.domain.service.RouteSelectorStrategy;
import com.qanal.control.domain.service.TransferStateMachine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@Configuration
@EnableConfigurationProperties(QanalProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * AdaptiveChunkPlanner — no Spring annotations in domain; receives primitives from config.
     */
    @Bean
    public AdaptiveChunkPlanner adaptiveChunkPlanner(QanalProperties props) {
        var t = props.transfer();
        return new AdaptiveChunkPlanner(t.minChunkSizeBytes(), t.maxChunkSizeBytes(), t.maxChunks());
    }

    /**
     * TransferStateMachine — pure domain, no Spring annotations.
     */
    @Bean
    public TransferStateMachine transferStateMachine() {
        return new TransferStateMachine();
    }

    /**
     * LatencyRouteSelector — declared here to stay in infrastructure without Spring annotation in domain.
     */
    @Bean
    public RouteSelectorStrategy routeSelectorStrategy(RelayStore relayStore) {
        return new com.qanal.control.adapter.out.persistence.LatencyRouteSelectorAdapter(relayStore);
    }

    /**
     * B3 Fix: Lua script for atomic rate-limit INCR + EXPIRE.
     */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        var script = new DefaultRedisScript<Long>();
        script.setScriptText("""
                local c = redis.call('INCR', KEYS[1])
                if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
                if c > tonumber(ARGV[1]) then return 0 end
                return 1
                """);
        script.setResultType(Long.class);
        return script;
    }
}
