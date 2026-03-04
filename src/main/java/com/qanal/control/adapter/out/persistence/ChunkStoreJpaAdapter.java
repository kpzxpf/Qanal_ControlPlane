package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.ChunkStore;
import com.qanal.control.domain.model.TransferChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChunkStoreJpaAdapter implements ChunkStore {

    private final TransferChunkJpaRepository repo;

    public ChunkStoreJpaAdapter(TransferChunkJpaRepository repo) {
        this.repo = repo;
    }

    @Override public List<TransferChunk> saveAll(List<TransferChunk> chunks)           { return repo.saveAll(chunks); }
    @Override public TransferChunk save(TransferChunk chunk)                            { return repo.save(chunk); }
    @Override public Optional<TransferChunk> findByTransferIdAndIndex(String tid, int i){ return repo.findByTransferIdAndChunkIndex(tid, i); }
}
