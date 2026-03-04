package com.qanal.control.application.port.in;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;

public interface CancelTransferUseCase {

    TransferResponse cancel(String transferId, String orgId);
}
