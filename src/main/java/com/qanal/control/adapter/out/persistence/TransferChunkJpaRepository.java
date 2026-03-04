package com.qanal.control.adapter.out.persistence;

import com.qanal.control.domain.model.TransferChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferChunkJpaRepository extends JpaRepository<TransferChunk, String> {

    Optional<TransferChunk> findByTransferIdAndChunkIndex(String transferId, int chunkIndex);
}
