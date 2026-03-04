package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.TransferChunk;

import java.util.List;
import java.util.Optional;

public interface ChunkStore {

    List<TransferChunk> saveAll(List<TransferChunk> chunks);

    TransferChunk save(TransferChunk chunk);

    Optional<TransferChunk> findByTransferIdAndIndex(String transferId, int chunkIndex);
}
