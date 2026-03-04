package com.qanal.control.auth.filter;

import com.qanal.control.auth.model.ApiKey;
import com.qanal.control.auth.model.Organization;
import com.qanal.control.auth.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private ApiKeyService    apiKeyService;
    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        filter        = new ApiKeyAuthFilter(apiKeyService);
        SecurityContextHolder.clearContext();
    }

    private ApiKey validApiKey() {
        var org = new Organization();
        org.setId("org-1");
        org.setName("Acme Corp");
        org.setPlan(Organization.Plan.PRO);

        var key = new ApiKey();
        key.setId("key-1");
        key.setOrganization(org);
        key.setActive(true);
        return key;
    }

    @Test
    void validKey_populatesSecurityContext_andContinuesChain() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/v1/transfers");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        request.addHeader("X-API-Key", "qnl_validkey");
        when(apiKeyService.validate("qnl_validkey")).thenReturn(Optional.of(validApiKey()));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(chain.getRequest()).isNotNull(); // chain was called
    }

    @Test
    void invalidKey_returns401_andDoesNotContinueChain() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/api/v1/transfers");
        var response = new MockHttpServletResponse();
        var chain    = mock(jakarta.servlet.FilterChain.class);

        request.addHeader("X-API-Key", "qnl_badkeyXX");
        when(apiKeyService.validate("qnl_badkeyXX")).thenReturn(Optional.empty());

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

        // No X-API-Key header set

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void actuatorPath_isSkipped() throws Exception {
        var request  = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        request.setServletPath("/actuator/health");

        filter.doFilter(request, response, chain);

        verifyNoInteractions(apiKeyService);
        assertThat(chain.getRequest()).isNotNull();
    }
}
