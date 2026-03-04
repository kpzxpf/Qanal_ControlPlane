package com.qanal.control.domain.exception;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(String transferId) {
        super("Transfer not found: " + transferId);
    }

    public TransferNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
