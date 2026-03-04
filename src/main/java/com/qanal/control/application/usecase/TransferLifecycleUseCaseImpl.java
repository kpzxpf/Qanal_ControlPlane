package com.qanal.control.application.usecase;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.application.port.in.CancelTransferUseCase;
import com.qanal.control.application.port.in.PauseTransferUseCase;
import com.qanal.control.application.port.in.ResumeTransferUseCase;
import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.exception.TransferNotFoundException;
import com.qanal.control.domain.model.Transfer;
import com.qanal.control.domain.model.TransferStatus;
import com.qanal.control.domain.service.TransferStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TransferLifecycleUseCaseImpl
        implements PauseTransferUseCase, ResumeTransferUseCase, CancelTransferUseCase {

    private final TransferStore        transferStore;
    private final RelayStore           relayStore;
    private final TransferStateMachine stateMachine;

    public TransferLifecycleUseCaseImpl(TransferStore transferStore,
                                        RelayStore relayStore,
                                        TransferStateMachine stateMachine) {
        this.transferStore = transferStore;
        this.relayStore    = relayStore;
        this.stateMachine  = stateMachine;
    }

    @Override
    @Transactional
    public TransferResponse pause(String transferId, String orgId) {
        var t = require(transferId, orgId);
        t.setStatus(stateMachine.transition(t.getStatus(), TransferStatus.PAUSED));
        return QueryTransferUseCaseImpl.toResponse(transferStore.save(t), null);
    }

    @Override
    @Transactional
    public TransferResponse resume(String transferId, String orgId) {
        var t = require(transferId, orgId);
        t.setStatus(stateMachine.transition(t.getStatus(), TransferStatus.IN_PROGRESS));
        return QueryTransferUseCaseImpl.toResponse(transferStore.save(t), null);
    }

    @Override
    @Transactional
    public TransferResponse cancel(String transferId, String orgId) {
        var t = require(transferId, orgId);
        t.setStatus(stateMachine.transition(t.getStatus(), TransferStatus.CANCELLED));
        t.setCompletedAt(OffsetDateTime.now());
        freeRelayCapacity(t);
        return QueryTransferUseCaseImpl.toResponse(transferStore.save(t), null);
    }

    private Transfer require(String transferId, String orgId) {
        return transferStore.findByIdAndOrgId(transferId, orgId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    private void freeRelayCapacity(Transfer t) {
        if (t.getAssignedRelay() != null) {
            relayStore.subtractUsedBytes(t.getAssignedRelay().getId(), t.getFileSize());
        }
    }
}
