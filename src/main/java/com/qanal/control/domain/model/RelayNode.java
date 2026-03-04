package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "relay_nodes")
@Getter
@Setter
public class RelayNode {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int quicPort;

    @Column(nullable = false)
    private long capacityBytes;

    @Column(nullable = false)
    private long usedBytes = 0;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RelayStatus status = RelayStatus.HEALTHY;

    @Column
    private Double avgRttMs;

    @Column
    private OffsetDateTime lastHeartbeat;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public long availableBytes() {
        return Math.max(0, capacityBytes - usedBytes);
    }
}
