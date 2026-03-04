package com.qanal.control.application.port.in;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface StreamProgressUseCase {

    SseEmitter stream(String transferId, String orgId);
}
