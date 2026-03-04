package com.qanal.control.application.port.in;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;

public interface PauseTransferUseCase {

    TransferResponse pause(String transferId, String orgId);
}
