package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * API key stored as SHA-256 hash only.
 * The raw key is never persisted — if lost, revoke and reissue.
 */
@Entity
@Table(name = "api_keys")
@Getter
@Setter
public class ApiKey {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** First 8 chars of the key (e.g. {@code qnl_a3b4}). Public, used for lookup. */
    @Column(length = 8, nullable = false, unique = true)
    private String keyPrefix;

    /** SHA-256(fullKey) as lowercase hex. Never expose this over the API. */
    @Column(length = 64, nullable = false)
    private String keyHash;

    @Column
    private String name;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column
    private OffsetDateTime lastUsedAt;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
