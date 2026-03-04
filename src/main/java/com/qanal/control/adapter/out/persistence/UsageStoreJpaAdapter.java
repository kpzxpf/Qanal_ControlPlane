package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.domain.model.UsageRecord;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class UsageStoreJpaAdapter implements UsageStore {

    private final UsageRecordJpaRepository repo;

    public UsageStoreJpaAdapter(UsageRecordJpaRepository repo) {
        this.repo = repo;
    }

    @Override public UsageRecord save(UsageRecord r)                                     { return repo.save(r); }
    @Override public long sumBytesForPeriod(String orgId, OffsetDateTime from, OffsetDateTime to) {
        return repo.sumBytesForPeriod(orgId, from, to);
    }
}
