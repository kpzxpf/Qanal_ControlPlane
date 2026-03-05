package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.domain.model.ApiKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyStoreJpaAdapter implements ApiKeyStore {

    private final ApiKeyJpaRepository repo;

    public ApiKeyStoreJpaAdapter(ApiKeyJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<ApiKey> findActiveByPrefix(String prefix) {
        return repo.findActiveByPrefix(prefix);
    }

    @Override
    @Transactional
    public void updateLastUsed(String id, OffsetDateTime ts) {
        repo.updateLastUsed(id, ts);
    }

    @Override
    @Transactional
    public ApiKey save(ApiKey key) {
        return repo.save(key);
    }

    @Override
    public List<ApiKey> findByOrganizationId(String orgId) {
        return repo.findByOrganizationId(orgId);
    }

    @Override
    @Transactional
    public void deactivate(String keyId, String orgId) {
        repo.deactivateByIdAndOrgId(keyId, orgId);
    }
}
