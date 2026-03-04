package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Plan plan = Plan.FREE;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public enum Plan {
        FREE, PRO, ENTERPRISE
    }
}
