package com.qanal.control.application.port.in;

public interface HeartbeatUseCase {

    void heartbeat(String nodeId, long bytesInFlight);
}
