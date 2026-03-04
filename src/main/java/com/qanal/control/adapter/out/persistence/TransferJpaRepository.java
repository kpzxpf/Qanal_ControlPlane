package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransferJpaRepository extends JpaRepository<Transfer, String> {

    @Query("SELECT t FROM Transfer t WHERE t.organization.id = :orgId ORDER BY t.createdAt DESC")
    Page<Transfer> findByOrganizationId(@Param("orgId") String orgId, Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.id = :id AND t.organization.id = :orgId")
    Optional<Transfer> findByIdAndOrganizationId(@Param("id") String id, @Param("orgId") String orgId);

    @Query("SELECT t FROM Transfer t WHERE t.status IN :activeStatuses AND t.expiresAt < :now")
    List<Transfer> findExpired(
            @Param("activeStatuses") List<TransferStatus> activeStatuses,
            @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Transfer t SET t.status = com.qanal.control.domain.model.TransferStatus.EXPIRED WHERE t.id IN :ids")
    int bulkExpire(@Param("ids") List<String> ids);
}
