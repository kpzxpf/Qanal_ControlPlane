package com.qanal.control.application.usecase;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.application.port.in.QueryTransferUseCase;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.exception.TransferNotFoundException;
import com.qanal.control.domain.model.RelayNode;
import com.qanal.control.domain.model.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryTransferUseCaseImpl implements QueryTransferUseCase {

    private final TransferStore transferStore;

    public QueryTransferUseCaseImpl(TransferStore transferStore) {
        this.transferStore = transferStore;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransferResponse> list(String orgId, Pageable pageable) {
        return transferStore.findByOrgId(orgId, pageable)
                .map(t -> toResponse(t, null));
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse get(String transferId, String orgId) {
        var t = transferStore.findByIdAndOrgId(transferId, orgId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        return toResponse(t, null);
    }

    static TransferResponse toResponse(Transfer t, java.util.List<com.qanal.control.domain.model.TransferChunk> chunks) {
        var relay = t.getAssignedRelay();
        java.util.List<TransferResponse.ChunkDto> chunkDtos = chunks == null ? null :
                chunks.stream()
                        .map(c -> new TransferResponse.ChunkDto(
                                c.getChunkIndex(), c.getOffsetBytes(), c.getSizeBytes()))
                        .toList();

        return new TransferResponse(
                t.getId(),
                t.getStatus(),
                t.getFileName(),
                t.getFileSize(),
                t.getTotalChunks(),
                t.getCompletedChunks(),
                t.progressPercent(),
                t.getBytesTransferred(),
                t.getAvgThroughput(),
                relay != null ? relay.getHost() : null,
                relay != null ? relay.getQuicPort() : 0,
                t.getCreatedAt(),
                t.getExpiresAt(),
                t.getCompletedAt(),
                chunkDtos
        );
    }
}
