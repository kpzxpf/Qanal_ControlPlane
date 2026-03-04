package com.qanal.control.application.port.in;

import com.qanal.control.adapter.in.rest.dto.InitiateTransferRequest;
import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import com.qanal.control.domain.model.Organization;

public interface InitiateTransferUseCase {

    TransferResponse initiate(InitiateTransferRequest request, Organization org);
}
