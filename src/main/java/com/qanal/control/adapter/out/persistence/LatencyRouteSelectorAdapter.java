package com.qanal.control.adapter.out.persistence;

import com.qanal.control.application.port.out.RelayStore;
import com.qanal.control.domain.model.RelayNode;
import com.qanal.control.domain.service.RouteSelectorStrategy;

import java.util.Comparator;
import java.util.Optional;

/**
 * Selects the relay with:
 * <ol>
 *   <li>Region match (source or target) — preferred</li>
 *   <li>Minimum {@code avgRttMs} among eligible healthy nodes</li>
 *   <li>Minimum {@code usedBytes} as tie-breaker</li>
 * </ol>
 */
public class LatencyRouteSelectorAdapter implements RouteSelectorStrategy {

    private final RelayStore relayStore;

    public LatencyRouteSelectorAdapter(RelayStore relayStore) {
        this.relayStore = relayStore;
    }

    @Override
    public Optional<RelayNode> select(String sourceRegion, String targetRegion, long requiredBytes) {
        var candidates = relayStore.findHealthyWithCapacity(requiredBytes);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((RelayNode n) -> regionScore(n, sourceRegion, targetRegion))
                        .thenComparingDouble(n -> n.getAvgRttMs() != null ? n.getAvgRttMs() : Double.MAX_VALUE)
                        .thenComparingLong(RelayNode::getUsedBytes))
                .findFirst();
    }

    private int regionScore(RelayNode node, String source, String target) {
        if (node.getRegion().equals(source) || node.getRegion().equals(target)) return 0;
        return 1;
    }
}
