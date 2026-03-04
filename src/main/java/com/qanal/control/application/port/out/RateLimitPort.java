package com.qanal.control.application.port.out;

public interface RateLimitPort {

    /**
     * @param keyPrefix unique key identifying the rate-limited subject (e.g. API key prefix)
     * @return {@code true} if the request is within limits, {@code false} if it should be rejected
     */
    boolean isAllowed(String keyPrefix);
}
