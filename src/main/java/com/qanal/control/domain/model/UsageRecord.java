package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "usage_records")
@Getter
@Setter
public class UsageRecord {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** The transfer that generated this usage (nullable — for future manual entries). */
    @Column(name = "transfer_id", length = 36)
    private String transferId;

    @Column(nullable = false)
    private long bytesTransferred;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public static UsageRecord of(Organization org, String transferId, long bytes) {
        var r = new UsageRecord();
        r.organization     = org;
        r.transferId       = transferId;
        r.bytesTransferred = bytes;
        return r;
    }
}
