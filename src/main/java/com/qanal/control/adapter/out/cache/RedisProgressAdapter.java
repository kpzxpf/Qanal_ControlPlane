package com.qanal.control.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qanal.control.adapter.in.rest.dto.TransferProgressResponse;
import com.qanal.control.application.port.out.ProgressBus;
import com.qanal.control.domain.model.TransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Redis pub/sub implementation of ProgressBus.
 *
 * <p>Write path: serialise progress to JSON → publish to {@code progress:{transferId}}.
 * Read path: subscribe on virtual thread → relay to SSE emitter.
 */
@Component
public class RedisProgressAdapter implements ProgressBus {

    private static final Logger   log         = LoggerFactory.getLogger(RedisProgressAdapter.class);
    private static final String   CHANNEL_FMT = "progress:%s";
    private static final Duration SSE_TIMEOUT = Duration.ofHours(1);

    private final StringRedisTemplate    redis;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper           mapper;

    public RedisProgressAdapter(StringRedisTemplate redis,
                                 RedisConnectionFactory connectionFactory,
                                 ObjectMapper mapper) {
        this.redis             = redis;
        this.connectionFactory = connectionFactory;
        this.mapper            = mapper;
    }

    @Override
    public void publish(TransferProgressResponse progress) {
        try {
            String json    = mapper.writeValueAsString(progress);
            String channel = CHANNEL_FMT.formatted(progress.transferId());
            redis.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise progress update for transfer {}", progress.transferId(), e);
        }
    }

    @Override
    public SseEmitter openStream(String transferId, long totalBytes, TransferStatus currentStatus) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());

        if (currentStatus.isTerminal()) {
            emitter.complete();
            return emitter;
        }

        String channel = CHANNEL_FMT.formatted(transferId);
        Thread.ofVirtual().name("sse-" + transferId).start(() ->
                subscribeAndRelay(emitter, channel, transferId)
        );
        return emitter;
    }

    private void subscribeAndRelay(SseEmitter emitter, String channel, String transferId) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        container.start();

        CompletableFuture<Void> done = new CompletableFuture<>();
        emitter.onCompletion(() -> done.complete(null));
        emitter.onTimeout(() -> done.complete(null));
        emitter.onError(done::completeExceptionally);

        try {
            container.addMessageListener((message, pattern) -> {
                try {
                    String json     = new String(message.getBody());
                    var    progress = mapper.readValue(json, TransferProgressResponse.class);
                    emitter.send(SseEmitter.event().name("progress").data(progress));

                    if (progress.status().isTerminal()) {
                        emitter.complete();
                    }
                } catch (IOException e) {
                    log.debug("SSE relay error for transfer {}: {}", transferId, e.getMessage());
                    emitter.completeWithError(e);
                }
            }, new PatternTopic(channel));

            done.get();

        } catch (Exception e) {
            log.debug("SSE subscription ended for transfer {}", transferId);
        } finally {
            container.stop();
            try { container.destroy(); } catch (Exception ex) {
                log.debug("Error destroying Redis listener container for {}", transferId, ex);
            }
        }
    }
}
