package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.Organization;

public interface QuotaPort {

    /**
     * Asserts that the organization has enough quota remaining for {@code requestedBytes}.
     *
     * @throws com.qanal.control.domain.exception.QuotaExceededException if quota would be exceeded
     */
    void assertQuotaAvailable(Organization org, long requestedBytes);
}
