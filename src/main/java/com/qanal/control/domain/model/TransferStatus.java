package com.qanal.control.domain.model;

/**
 * Lifecycle states of a {@link Transfer}.
 * Terminal states: COMPLETED, FAILED, CANCELLED, EXPIRED.
 */
public enum TransferStatus {

    /** Transfer record created, waiting for Data Plane assignment. */
    INITIATED,
    /** Relay assigned, waiting for sender to connect. */
    WAITING_SENDER,
    /** Data is actively being transferred. */
    IN_PROGRESS,
    /** Sender paused the transfer. */
    PAUSED,
    /** All chunks received; awaiting final checksum verification. */
    COMPLETING,
    /** Checksum verified, transfer finished successfully. */
    COMPLETED,
    /** Checksum mismatch or unrecoverable error. */
    FAILED,
    /** Cancelled by the user. */
    CANCELLED,
    /** TTL exceeded without completion. */
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
