package com.qanal.control.application.port.in;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;

public interface ResumeTransferUseCase {

    TransferResponse resume(String transferId, String orgId);
}
