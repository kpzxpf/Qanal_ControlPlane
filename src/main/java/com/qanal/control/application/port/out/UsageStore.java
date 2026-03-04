package com.qanal.control.application.port.out;

import com.qanal.control.domain.model.UsageRecord;

import java.time.OffsetDateTime;

public interface UsageStore {

    UsageRecord save(UsageRecord record);

    long sumBytesForPeriod(String orgId, OffsetDateTime from, OffsetDateTime to);
}
