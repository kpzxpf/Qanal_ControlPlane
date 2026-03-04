package com.qanal.control.infrastructure.config;

import com.qanal.control.adapter.in.grpc.TransferGrpcAdapter;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * B5 Fix: Uses {@link SmartLifecycle} instead of {@link org.springframework.context.event.ContextRefreshedEvent}.
 *
 * <p>ContextRefreshedEvent fires on every refresh (including child contexts in tests),
 * potentially starting the gRPC server multiple times.
 * SmartLifecycle guarantees exactly-once start/stop in the correct phase.
 *
 * <p>P3 Fix: maxInboundMessageSize raised from 64 KB to 4 MB.
 */
@Configuration
public class GrpcServerConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    @Value("${qanal.grpc.server.port:9090}")
    private int grpcPort;

    private final TransferGrpcAdapter grpcAdapter;
    private final AtomicBoolean       running = new AtomicBoolean(false);
    private Server server;

    public GrpcServerConfig(TransferGrpcAdapter grpcAdapter) {
        this.grpcAdapter = grpcAdapter;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("gRPC server already running — ignoring duplicate start()");
            return;
        }
        try {
            server = ServerBuilder
                    .forPort(grpcPort)
                    .addService(grpcAdapter)
                    .maxInboundMessageSize(4 * 1024 * 1024)   // P3 Fix: 4 MB
                    .build()
                    .start();
            log.info("gRPC server listening on port {}", grpcPort);
        } catch (IOException e) {
            running.set(false);
            throw new IllegalStateException("Failed to start gRPC server on port " + grpcPort, e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (server != null) {
            log.info("Shutting down gRPC server...");
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            log.info("gRPC server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** Start last (after all repositories/adapters are ready), stop first. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
