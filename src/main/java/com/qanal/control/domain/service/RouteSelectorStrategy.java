package com.qanal.control.domain.service;

import com.qanal.control.domain.model.RelayNode;

import java.util.Optional;

/**
 * Strategy for selecting the optimal relay node for a transfer.
 */
public interface RouteSelectorStrategy {

    Optional<RelayNode> select(String sourceRegion, String targetRegion, long requiredBytes);
}
