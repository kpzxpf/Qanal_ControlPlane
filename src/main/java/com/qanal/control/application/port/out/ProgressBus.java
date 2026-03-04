package com.qanal.control.application.port.out;

import com.qanal.control.adapter.in.rest.dto.TransferProgressResponse;
import com.qanal.control.domain.model.TransferStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ProgressBus {

    void publish(TransferProgressResponse progress);

    SseEmitter openStream(String transferId, long totalBytes, TransferStatus currentStatus);
}
