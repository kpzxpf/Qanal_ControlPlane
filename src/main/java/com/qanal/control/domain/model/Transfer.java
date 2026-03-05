package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for a file transfer.
 *
 * <p>{@code version} provides optimistic locking — see B6 fix notes.
 * The completedChunks counter is now managed via Redis (ChunkCompletionCounter)
 * and updated exactly once at finalization.
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
public class Transfer {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(length = 1024)
    private String fileName;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 128)
    private String fileChecksum;          // xxHash64 of full file (sender-provided)

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransferStatus status = TransferStatus.INITIATED;

    @Column(nullable = false)
    private int totalChunks;

    @Column(nullable = false)
    private int completedChunks = 0;

    /** Optimistic locking — retained for structural safety. */
    @Version
    @Column(nullable = false)
    private long version = 0;

    @Column(length = 50)
    private String sourceRegion;

    @Column(length = 50)
    private String targetRegion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_relay_id")
    private RelayNode assignedRelay;

    /** Egress relay — where the recipient downloads from. Null if same as ingress. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "egress_relay_id")
    private RelayNode egressRelay;

    /** QUIC port on the egress DataPlane dedicated to recipient downloads. */
    @Column(name = "egress_download_port")
    private Integer egressDownloadPort;

    @Column(nullable = false)
    private long bytesTransferred = 0;

    @Column
    private Double avgThroughput;         // bytes/sec

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime startedAt;

    @Column
    private OffsetDateTime completedAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @OneToMany(
            mappedBy = "transfer",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("chunkIndex ASC")
    private List<TransferChunk> chunks = new ArrayList<>();

    // ── Business helpers ────────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return !status.isTerminal() && OffsetDateTime.now().isAfter(expiresAt);
    }

    public int progressPercent() {
        if (totalChunks == 0) return 0;
        // BUG-9 Fix: use ceiling division so progress reaches 100% exactly when
        // the last chunk is counted, avoiding a stuck-at-99% display.
        return (int) Math.min(100, (100L * completedChunks + totalChunks - 1) / totalChunks);
    }

    public boolean isComplete() {
        return completedChunks >= totalChunks;
    }
}
