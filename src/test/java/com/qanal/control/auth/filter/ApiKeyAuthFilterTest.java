package com.qanal.control.auth.filter;

import com.qanal.control.adapter.in.security.ApiKeyAuthFilter;
import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.domain.model.ApiKey;
import com.qanal.control.domain.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private ApiKeyStore      apiKeyStore;
    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        apiKeyStore = mock(ApiKeyStore.class);
        filter      = new ApiKeyAuthFilter(apiKeyStore);
        SecurityContextHolder.clearContext();
    }

    /**
     * Creates an ApiKey whose hash matches SHA-256("qnl_validkey").
     * SHA-256("qnl_validkey") = pre-computed hex below.
     */
    private ApiKey validApiKey() {
        var org = new Organization();
        org.setId("org-1");
        org.setName("Acme Corp");
        org.setPlan(Organization.Plan.PRO);

        var key = new ApiKey();
        key.setId("key-1");
        key.setOrganization(org);
        key.setKeyPrefix("qnl_vali");
        // SHA-256 of "qnl_validkey"
        key.setKeyHash(sha256("qnl_validkey"));
        key.setActive(true);
        return key;
    }

    @Test
    void validKey_populatesSecurityContext_andContinuesChain() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/v1/transfers");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        request.addHeader("X-API-Key", "qnl_validkey");
        when(apiKeyStore.findActiveByPrefix("qnl_vali")).thenReturn(Optional.of(validApiKey()));
        doNothing().when(apiKeyStore).updateLastUsed(anyString(), any(OffsetDateTime.class));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidKey_returns401_andDoesNotContinueChain() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/v1/transfers");
        var response = new MockHttpServletResponse();
        var chain    = mock(jakarta.servlet.FilterChain.class);

        request.addHeader("X-API-Key", "qnl_badkeyXX");
        when(apiKeyStore.findActiveByPrefix("qnl_badk")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid API key");
        assertThat(response.getContentType()).isEqualTo("application/json");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void missingKey_continuesChain_withNoAuthentication() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/v1/transfers");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(apiKeyStore);
    }

    @Test
    void actuatorPath_isSkipped() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        request.setServletPath("/actuator/health");

        filter.doFilter(request, response, chain);

        verifyNoInteractions(apiKeyStore);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
