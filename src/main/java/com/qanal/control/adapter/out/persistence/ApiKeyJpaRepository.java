package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.ApiKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKey, String> {

    @EntityGraph(attributePaths = {"organization"})
    @Query("""
            SELECT k FROM ApiKey k
            WHERE k.keyPrefix = :prefix
              AND k.isActive = true
            """)
    Optional<ApiKey> findActiveByPrefix(@Param("prefix") String prefix);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :ts WHERE k.id = :id")
    void updateLastUsed(@Param("id") String id, @Param("ts") OffsetDateTime ts);
}
