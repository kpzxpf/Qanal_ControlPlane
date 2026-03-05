package com.qanal.control.billing.service;

import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.application.service.ApiKeyService;
import com.qanal.control.domain.model.ApiKey;
import com.qanal.control.domain.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    private ApiKeyStore    apiKeyStore;
    private ApiKeyService  service;

    @BeforeEach
    void setUp() {
        apiKeyStore = mock(ApiKeyStore.class);
        service     = new ApiKeyService(apiKeyStore);
        when(apiKeyStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void generate_returnsRawKeyWithCorrectPrefix() {
        var created = service.generate(org(), "my-key");

        assertThat(created.rawKey()).startsWith("qnl_");
        assertThat(created.rawKey()).hasSize(36); // "qnl_" + 32 hex chars
    }

    @Test
    void generate_storedHashMatchesSha256OfRawKey() throws Exception {
        var created = service.generate(org(), "test");

        String expectedHash = sha256(created.rawKey());
        assertThat(created.entity().getKeyHash()).isEqualTo(expectedHash);
    }

    @Test
    void generate_prefixIsFirst8CharsOfRawKey() {
        var created = service.generate(org(), "test");

        assertThat(created.entity().getKeyPrefix()).isEqualTo(created.rawKey().substring(0, 8));
    }

    @Test
    void generate_keyIsActive() {
        var created = service.generate(org(), "test");
        assertThat(created.entity().isActive()).isTrue();
    }

    @Test
    void generate_twoCallsReturnDifferentKeys() {
        var k1 = service.generate(org(), "k1");
        var k2 = service.generate(org(), "k2");
        assertThat(k1.rawKey()).isNotEqualTo(k2.rawKey());
    }

    @Test
    void listForOrg_delegatesToStore() {
        var key = new ApiKey();
        when(apiKeyStore.findByOrganizationId("org-1")).thenReturn(List.of(key));

        var result = service.listForOrg("org-1");

        assertThat(result).containsExactly(key);
        verify(apiKeyStore).findByOrganizationId("org-1");
    }

    @Test
    void revoke_callsDeactivate() {
        service.revoke("key-id", "org-id");
        verify(apiKeyStore).deactivate("key-id", "org-id");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Organization org() {
        var o = new Organization();
        o.setId("org-1");
        o.setName("Test Corp");
        o.setPlan(Organization.Plan.FREE);
        return o;
    }

    private static String sha256(String input) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
