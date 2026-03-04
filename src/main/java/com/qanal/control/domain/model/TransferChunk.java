package com.qanal.control.domain.model;

import com.qanal.control.infrastructure.common.UuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Metadata for a single file chunk.
 * Data never flows through Control Plane — only chunk state does.
 */
@Entity
@Table(
        name = "transfer_chunks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"transfer_id", "chunk_index"})
)
@Getter
@Setter
public class TransferChunk {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private long offsetBytes;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 128)
    private String checksum;              // xxHash64, set when chunk completes

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ChunkStatus status = ChunkStatus.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidV7.generate();
    }

    /** Factory — used by ChunkPlanner */
    public static TransferChunk of(Transfer transfer, int index, long offset, long size) {
        var chunk = new TransferChunk();
        chunk.transfer    = transfer;
        chunk.chunkIndex  = index;
        chunk.offsetBytes = offset;
        chunk.sizeBytes   = size;
        return chunk;
    }
}
