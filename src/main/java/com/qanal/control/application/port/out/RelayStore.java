package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.RelayNode;

import java.util.Optional;

public interface RelayStore {

    RelayNode save(RelayNode relay);

    Optional<RelayNode> findById(String id);

    Optional<RelayNode> findByHostAndPort(String host, int port);

    java.util.List<RelayNode> findHealthyWithCapacity(long requiredBytes);

    void addUsedBytes(String relayId, long bytes);

    void subtractUsedBytes(String relayId, long bytes);
}
