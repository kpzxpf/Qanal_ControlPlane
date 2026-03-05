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
        RateLimitProps  rateLimit,
        StripeProps     stripe,
        AdminProps      admin
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

    public record StripeProps(
            String secretKey,
            String webhookSecret,
            String proPriceId,          // Stripe Price ID for PRO plan (monthly)
            String successUrl,          // redirect after successful checkout
            String cancelUrl            // redirect if checkout cancelled
    ) {}

    public record AdminProps(String secret) {}
}
