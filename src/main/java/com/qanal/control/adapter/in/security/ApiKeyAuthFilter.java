package com.qanal.control.adapter.in.security;

import com.qanal.control.application.port.out.ApiKeyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * B7 Fix: Injects {@link ApiKeyStore} (which is decorated by Redis cache)
 * instead of going directly to DB on every request.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private final ApiKeyStore apiKeyStore;

    public ApiKeyAuthFilter(ApiKeyStore apiKeyStore) {
        this.apiKeyStore = apiKeyStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER);

        if (rawKey != null) {
            var validated = validate(rawKey);
            if (validated != null) {
                var principal = new AuthenticatedOrg(validated.getOrganization());
                var auth      = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid API key\"}");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private com.qanal.control.domain.model.ApiKey validate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("qnl_") || rawKey.length() < 12) {
            return null;
        }
        String prefix = rawKey.substring(0, 8);
        String hash   = sha256Hex(rawKey);

        return apiKeyStore.findActiveByPrefix(prefix)
                .filter(key -> MessageDigest.isEqual(
                        hash.getBytes(StandardCharsets.UTF_8),
                        key.getKeyHash().getBytes(StandardCharsets.UTF_8)
                ))
                .map(key -> {
                    apiKeyStore.updateLastUsed(key.getId(), java.time.OffsetDateTime.now());
                    return key;
                })
                .orElse(null);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator/");
    }
}
