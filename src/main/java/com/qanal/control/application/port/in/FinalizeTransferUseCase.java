package com.qanal.control.application.port.in;

public interface FinalizeTransferUseCase {

    boolean finalize(String transferId, String finalChecksum);
}
