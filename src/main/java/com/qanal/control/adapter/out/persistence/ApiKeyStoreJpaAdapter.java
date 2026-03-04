package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.domain.model.ApiKey;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
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
    public void updateLastUsed(String id, OffsetDateTime ts) {
        repo.updateLastUsed(id, ts);
    }
}
