package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.RelayNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RelayNodeJpaRepository extends JpaRepository<RelayNode, String> {

    @Query("""
            SELECT n FROM RelayNode n
            WHERE n.status = com.qanal.control.domain.model.RelayStatus.HEALTHY
              AND n.capacityBytes - n.usedBytes >= :requiredBytes
            ORDER BY n.usedBytes ASC
            """)
    List<RelayNode> findHealthyWithCapacity(@Param("requiredBytes") long requiredBytes);

    @Modifying
    @Query("""
            UPDATE RelayNode n
            SET n.status = com.qanal.control.domain.model.RelayStatus.UNHEALTHY
            WHERE n.status = com.qanal.control.domain.model.RelayStatus.HEALTHY
              AND n.lastHeartbeat < :cutoff
            """)
    int markUnhealthyOlderThan(@Param("cutoff") OffsetDateTime cutoff);

    Optional<RelayNode> findByHostAndQuicPort(String host, int quicPort);

    @Modifying
    @Query("UPDATE RelayNode n SET n.usedBytes = n.usedBytes + :bytes WHERE n.id = :id")
    void addUsedBytes(@Param("id") String id, @Param("bytes") long bytes);

    @Modifying
    @Query("UPDATE RelayNode n SET n.usedBytes = GREATEST(0, n.usedBytes - :bytes) WHERE n.id = :id")
    void subtractUsedBytes(@Param("id") String id, @Param("bytes") long bytes);
}
