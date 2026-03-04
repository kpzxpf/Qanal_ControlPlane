package com.qanal.control.adapter.in.rest;

import com.qanal.control.adapter.in.security.AuthenticatedOrg;
import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.domain.model.Organization;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final UsageStore usageStore;

    public OrganizationController(UsageStore usageStore) {
        this.usageStore = usageStore;
    }

    @GetMapping("/me")
    public OrgResponse me(@AuthenticationPrincipal AuthenticatedOrg principal) {
        Organization org = principal.organization();
        OffsetDateTime now   = OffsetDateTime.now();
        OffsetDateTime start = now.with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime end   = now.with(TemporalAdjusters.firstDayOfNextMonth())
                .toLocalDate().atStartOfDay().atOffset(now.getOffset());
        long used = usageStore.sumBytesForPeriod(org.getId(), start, end);
        return new OrgResponse(org.getId(), org.getName(), org.getPlan().name(), used, org.getCreatedAt());
    }

    public record OrgResponse(
            String         id,
            String         name,
            String         plan,
            long           bytesUsedThisMonth,
            OffsetDateTime createdAt
    ) {}
}
