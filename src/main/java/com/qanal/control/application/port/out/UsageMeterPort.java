package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.Organization;

public interface UsageMeterPort {

    void record(Organization org, String transferId, long bytes);
}
