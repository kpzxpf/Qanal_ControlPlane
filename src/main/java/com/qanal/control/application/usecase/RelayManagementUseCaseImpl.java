package com.qanal.control.application.usecase;

import com.qanal.control.application.port.in.HeartbeatUseCase;
import com.qanal.control.application.port.in.RegisterAgentUseCase;
import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.domain.model.RelayNode;
import com.qanal.control.domain.model.RelayStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class RelayManagementUseCaseImpl implements RegisterAgentUseCase, HeartbeatUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayManagementUseCaseImpl.class);

    private final RelayStore relayStore;

    public RelayManagementUseCaseImpl(RelayStore relayStore) {
        this.relayStore = relayStore;
    }

    @Override
    @Transactional
    public RelayNode register(String host, int quicPort, String region,
                               long availableBandwidthBps, Double avgRttMs) {
        return relayStore.findByHostAndPort(host, quicPort)
                .map(existing -> {
                    existing.setRegion(region);
                    existing.setCapacityBytes(availableBandwidthBps);
                    existing.setAvgRttMs(avgRttMs);
                    existing.setStatus(RelayStatus.HEALTHY);
                    existing.setLastHeartbeat(OffsetDateTime.now());
                    return relayStore.save(existing);
                })
                .orElseGet(() -> {
                    var node = new RelayNode();
                    node.setHost(host);
                    node.setQuicPort(quicPort);
                    node.setRegion(region);
                    node.setCapacityBytes(availableBandwidthBps);
                    node.setAvgRttMs(avgRttMs);
                    node.setLastHeartbeat(OffsetDateTime.now());
                    log.info("Registered new relay node {}:{} in region {}", host, quicPort, region);
                    return relayStore.save(node);
                });
    }

    @Override
    @Transactional
    public void heartbeat(String nodeId, long bytesInFlight) {
        relayStore.findById(nodeId).ifPresentOrElse(node -> {
            node.setLastHeartbeat(OffsetDateTime.now());
            node.setUsedBytes(bytesInFlight);
            if (node.getStatus() == RelayStatus.UNHEALTHY) {
                node.setStatus(RelayStatus.HEALTHY);
                log.info("Relay node {} recovered and is now HEALTHY", nodeId);
            }
            relayStore.save(node);
        }, () -> log.warn("Heartbeat from unknown relay node {}", nodeId));
    }
}
