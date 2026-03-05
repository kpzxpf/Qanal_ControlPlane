package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.ApiKey;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiKeyStore {

    Optional<ApiKey> findActiveByPrefix(String prefix);

    void updateLastUsed(String id, OffsetDateTime ts);

    ApiKey save(ApiKey key);

    List<ApiKey> findByOrganizationId(String orgId);

    /** Sets isActive=false. Idempotent. */
    void deactivate(String keyId, String orgId);
}
