package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.ApiKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKey, String> {

    @EntityGraph(attributePaths = {"organization"})
    @Query("""
            SELECT k FROM ApiKey k
            WHERE k.keyPrefix = :prefix
              AND k.isActive = true
            """)
    Optional<ApiKey> findActiveByPrefix(@Param("prefix") String prefix);

    @Query("SELECT k FROM ApiKey k WHERE k.organization.id = :orgId ORDER BY k.createdAt DESC")
    List<ApiKey> findByOrganizationId(@Param("orgId") String orgId);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :ts WHERE k.id = :id")
    void updateLastUsed(@Param("id") String id, @Param("ts") OffsetDateTime ts);

    @Modifying
    @Query("UPDATE ApiKey k SET k.isActive = false WHERE k.id = :keyId AND k.organization.id = :orgId")
    void deactivateByIdAndOrgId(@Param("keyId") String keyId, @Param("orgId") String orgId);
}
