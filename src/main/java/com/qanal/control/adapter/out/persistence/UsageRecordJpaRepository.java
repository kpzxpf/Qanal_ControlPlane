package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface UsageRecordJpaRepository extends JpaRepository<UsageRecord, String> {

    @Query("""
            SELECT COALESCE(SUM(u.bytesTransferred), 0)
            FROM UsageRecord u
            WHERE u.organization.id = :orgId
              AND u.recordedAt >= :from
              AND u.recordedAt < :to
            """)
    long sumBytesForPeriod(
            @Param("orgId") String orgId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
