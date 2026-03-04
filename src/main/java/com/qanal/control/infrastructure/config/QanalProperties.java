package com.qanal.control.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code qanal.*} configuration namespace.
 */
@ConfigurationProperties(prefix = "qanal")
public record QanalProperties(
        GrpcServerProps grpc,
        TransferProps   transfer,
        BillingProps    billing,
        RateLimitProps  rateLimit
) {

    public record GrpcServerProps(ServerProps server) {
        public record ServerProps(int port) {}
    }

    public record TransferProps(
            int  defaultExpiryHours,
            long maxFileSizeBytes,
            long minChunkSizeBytes,
            long maxChunkSizeBytes,
            int  maxChunks,
            int  minParallelStreams,
            int  maxParallelStreams
    ) {}

    public record BillingProps(long quotaCacheTtlSeconds) {}

    public record RateLimitProps(int requestsPerMinute) {}
}
