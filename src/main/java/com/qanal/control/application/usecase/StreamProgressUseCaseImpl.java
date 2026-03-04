package com.qanal.control.application.usecase;

import com.qanal.control.application.port.in.StreamProgressUseCase;
import com.qanal.control.application.port.out.ProgressBus;
import com.qanal.control.application.port.out.TransferStore;
import com.qanal.control.domain.exception.TransferNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StreamProgressUseCaseImpl implements StreamProgressUseCase {

    private final TransferStore transferStore;
    private final ProgressBus   progressBus;

    public StreamProgressUseCaseImpl(TransferStore transferStore, ProgressBus progressBus) {
        this.transferStore = transferStore;
        this.progressBus   = progressBus;
    }

    @Override
    public SseEmitter stream(String transferId, String orgId) {
        var t = transferStore.findByIdAndOrgId(transferId, orgId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        return progressBus.openStream(transferId, t.getFileSize(), t.getStatus());
    }
}
