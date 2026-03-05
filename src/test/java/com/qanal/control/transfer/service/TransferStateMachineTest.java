package com.qanal.control.transfer.service;

import com.qanal.control.domain.exception.InvalidTransferStateException;
import com.qanal.control.domain.model.TransferStatus;
import com.qanal.control.domain.service.TransferStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferStateMachineTest {

    private final TransferStateMachine machine = new TransferStateMachine();

    // ── Valid transitions ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "INITIATED,       WAITING_SENDER",
        "INITIATED,       CANCELLED",
        "INITIATED,       EXPIRED",
        "WAITING_SENDER,  IN_PROGRESS",
        "WAITING_SENDER,  CANCELLED",
        "WAITING_SENDER,  EXPIRED",
        "IN_PROGRESS,     PAUSED",
        "IN_PROGRESS,     COMPLETING",
        "IN_PROGRESS,     FAILED",
        "IN_PROGRESS,     CANCELLED",
        "IN_PROGRESS,     EXPIRED",
        "PAUSED,          IN_PROGRESS",
        "PAUSED,          CANCELLED",
        "PAUSED,          EXPIRED",
        "COMPLETING,      COMPLETED",
        "COMPLETING,      FAILED",
    })
    void validTransitions(TransferStatus from, TransferStatus to) {
        TransferStatus result = machine.transition(from, to);
        assertThat(result).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "INITIATED,       WAITING_SENDER",
        "IN_PROGRESS,     PAUSED",
        "PAUSED,          IN_PROGRESS",
        "COMPLETING,      COMPLETED",
    })
    void canTransition_returnsTrue(TransferStatus from, TransferStatus to) {
        assertThat(machine.canTransition(from, to)).isTrue();
    }

    // ── Invalid transitions ───────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1} must throw")
    @CsvSource({
        "INITIATED,   COMPLETED",
        "INITIATED,   IN_PROGRESS",
        "COMPLETED,   CANCELLED",
        "COMPLETED,   FAILED",
        "FAILED,      COMPLETED",
        "CANCELLED,   IN_PROGRESS",
        "EXPIRED,     IN_PROGRESS",
        "COMPLETING,  PAUSED",
        "COMPLETING,  CANCELLED",
    })
    void invalidTransition_throwsIllegalState(TransferStatus from, TransferStatus to) {
        assertThatThrownBy(() -> machine.transition(from, to))
                .isInstanceOf(InvalidTransferStateException.class)
                .hasMessageContaining("Invalid transition");
    }

    @Test
    void canTransition_returnsFalse_forInvalidMove() {
        assertThat(machine.canTransition(TransferStatus.COMPLETED, TransferStatus.FAILED)).isFalse();
    }
}
