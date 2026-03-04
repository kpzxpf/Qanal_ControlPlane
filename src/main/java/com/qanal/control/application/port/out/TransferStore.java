package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransferStore {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(String id);

    Optional<Transfer> findByIdAndOrgId(String id, String orgId);

    Page<Transfer> findByOrgId(String orgId, Pageable pageable);

    List<Transfer> findExpired(List<TransferStatus> activeStatuses, OffsetDateTime now);

    int bulkExpire(List<String> ids);
}
