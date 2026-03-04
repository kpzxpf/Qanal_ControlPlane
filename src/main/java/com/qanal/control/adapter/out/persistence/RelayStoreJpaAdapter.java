package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.domain.model.RelayNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class RelayStoreJpaAdapter implements RelayStore {

    private final RelayNodeJpaRepository repo;

    public RelayStoreJpaAdapter(RelayNodeJpaRepository repo) {
        this.repo = repo;
    }

    @Override public RelayNode save(RelayNode n)                                    { return repo.save(n); }
    @Override public Optional<RelayNode> findById(String id)                        { return repo.findById(id); }
    @Override public Optional<RelayNode> findByHostAndPort(String h, int p)         { return repo.findByHostAndQuicPort(h, p); }
    @Override public List<RelayNode> findHealthyWithCapacity(long req)              { return repo.findHealthyWithCapacity(req); }
    @Override @Transactional public void addUsedBytes(String id, long b)            { repo.addUsedBytes(id, b); }
    @Override @Transactional public void subtractUsedBytes(String id, long b)       { repo.subtractUsedBytes(id, b); }
}
