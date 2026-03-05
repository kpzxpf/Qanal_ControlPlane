package com.qanal.control.application.service;

import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.domain.model.ApiKey;
import com.qanal.control.domain.model.Organization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates and manages API keys.
 *
 * <p>Key format: {@code qnl_<32-hex-chars>} (36 chars total).
 * Only the raw key is returned at creation; only the SHA-256 hash is stored.
 */
@Service
public class ApiKeyService {

    private static final SecureRandom RNG = new SecureRandom();

    private final ApiKeyStore apiKeyStore;

    public ApiKeyService(ApiKeyStore apiKeyStore) {
        this.apiKeyStore = apiKeyStore;
    }

    /**
     * Generates a new API key for the given organization.
     *
     * @return {@link CreatedKey} containing the raw key (show once!) and the persisted entity.
     */
    @Transactional
    public CreatedKey generate(Organization org, String keyName) {
        String rawKey = generateRawKey();
        String prefix = rawKey.substring(0, 8);   // "qnl_XXXX"
        String hash   = sha256Hex(rawKey);

        var entity = new ApiKey();
        entity.setOrganization(org);
        entity.setKeyPrefix(prefix);
        entity.setKeyHash(hash);
        entity.setName(keyName);
        entity.setActive(true);

        ApiKey saved = apiKeyStore.save(entity);
        return new CreatedKey(rawKey, saved);
    }

    public List<ApiKey> listForOrg(String orgId) {
        return apiKeyStore.findByOrganizationId(orgId);
    }

    @Transactional
    public void revoke(String keyId, String orgId) {
        apiKeyStore.deactivate(keyId, orgId);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static String generateRawKey() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        return "qnl_" + HexFormat.of().formatHex(bytes); // "qnl_" + 32 hex chars = 36 chars
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CreatedKey(String rawKey, ApiKey entity) {}
}
