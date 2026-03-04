package com.qanal.control.application.port.in;

import com.qanal.control.adapter.in.rest.dto.TransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QueryTransferUseCase {

    Page<TransferResponse> list(String orgId, Pageable pageable);

    TransferResponse get(String transferId, String orgId);
}
