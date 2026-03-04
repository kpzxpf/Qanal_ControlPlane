package com.qanal.control.domain.service;

import com.qanal.control.domain.exception.InvalidTransferStateException;
import com.qanal.control.domain.model.TransferStatus;

import java.util.Map;
import java.util.Set;

/**
 * Guards all state transitions.
 *
 * <p>Calling {@link #transition(TransferStatus, TransferStatus)} throws
 * {@link InvalidTransferStateException} on invalid transitions — prevents
 * race-condition bugs from silently corrupting state.
 *
 * <p>No Spring annotations — registered as @Bean in AppConfig.
 */
public class TransferStateMachine {

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED =
            Map.of(
                    TransferStatus.INITIATED,      Set.of(TransferStatus.WAITING_SENDER, TransferStatus.CANCELLED, TransferStatus.EXPIRED),
                    TransferStatus.WAITING_SENDER, Set.of(TransferStatus.IN_PROGRESS,   TransferStatus.CANCELLED, TransferStatus.EXPIRED),
                    TransferStatus.IN_PROGRESS,    Set.of(TransferStatus.PAUSED,        TransferStatus.COMPLETING, TransferStatus.FAILED, TransferStatus.CANCELLED, TransferStatus.EXPIRED),
                    TransferStatus.PAUSED,         Set.of(TransferStatus.IN_PROGRESS,   TransferStatus.CANCELLED, TransferStatus.EXPIRED),
                    TransferStatus.COMPLETING,     Set.of(TransferStatus.COMPLETED,     TransferStatus.FAILED),
                    TransferStatus.COMPLETED,      Set.of(),
                    TransferStatus.FAILED,         Set.of(),
                    TransferStatus.CANCELLED,      Set.of(),
                    TransferStatus.EXPIRED,        Set.of()
            );

    /**
     * Validates and returns the new status.
     *
     * @throws InvalidTransferStateException if the transition is not permitted
     */
    public TransferStatus transition(TransferStatus from, TransferStatus to) {
        Set<TransferStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidTransferStateException(
                    "Invalid transition: %s → %s".formatted(from, to));
        }
        return to;
    }

    public boolean canTransition(TransferStatus from, TransferStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
