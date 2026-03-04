package com.qanal.control.adapter.in.security;

import com.qanal.control.application.port.out.RateLimitPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * B3 Fix: Delegates to {@link RateLimitPort} which uses atomic Lua script.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitPort rateLimitPort;

    public RateLimitFilter(RateLimitPort rateLimitPort) {
        this.rateLimitPort = rateLimitPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(ApiKeyAuthFilter.HEADER);
        if (rawKey == null || rawKey.length() < 8) {
            chain.doFilter(request, response);
            return;
        }

        String prefix = rawKey.substring(0, 8);

        if (!rateLimitPort.isAllowed(prefix)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator/");
    }
}
