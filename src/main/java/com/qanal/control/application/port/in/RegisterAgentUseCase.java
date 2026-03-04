package com.qanal.control.application.port.in;

import com.qanal.control.domain.model.RelayNode;

public interface RegisterAgentUseCase {

    RelayNode register(String host, int quicPort, String region,
                       long availableBandwidthBps, Double avgRttMs);
}
