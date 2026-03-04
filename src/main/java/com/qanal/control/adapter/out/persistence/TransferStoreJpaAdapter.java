package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class TransferStoreJpaAdapter implements TransferStore {

    private final TransferJpaRepository repo;

    public TransferStoreJpaAdapter(TransferJpaRepository repo) {
        this.repo = repo;
    }

    @Override public Transfer save(Transfer t)                                              { return repo.save(t); }
    @Override public Optional<Transfer> findById(String id)                                { return repo.findById(id); }
    @Override public Optional<Transfer> findByIdAndOrgId(String id, String orgId)         { return repo.findByIdAndOrganizationId(id, orgId); }
    @Override public Page<Transfer> findByOrgId(String orgId, Pageable pageable)          { return repo.findByOrganizationId(orgId, pageable); }
    @Override public List<Transfer> findExpired(List<TransferStatus> s, OffsetDateTime n) { return repo.findExpired(s, n); }
    @Override public int bulkExpire(List<String> ids)                                      { return repo.bulkExpire(ids); }
}
