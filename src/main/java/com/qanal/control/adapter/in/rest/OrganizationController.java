package com.qanal.control.adapter.in.rest;

import com.qanal.control.adapter.in.security.AuthenticatedOrg;
import com.qanal.control.application.port.out.UsageStore;
import com.qanal.control.application.service.ApiKeyService;
import com.qanal.control.domain.model.ApiKey;
import com.qanal.control.domain.model.Organization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final UsageStore    usageStore;
    private final ApiKeyService apiKeyService;

    public OrganizationController(UsageStore usageStore, ApiKeyService apiKeyService) {
        this.usageStore    = usageStore;
        this.apiKeyService = apiKeyService;
    }

    // ── Organization info ────────────────────────────────────────────────────

    @GetMapping("/me")
    public OrgResponse me(@AuthenticationPrincipal AuthenticatedOrg principal) {
        Organization org = principal.organization();
        OffsetDateTime now   = OffsetDateTime.now();
        OffsetDateTime start = now.with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime end   = now.with(TemporalAdjusters.firstDayOfNextMonth())
                .toLocalDate().atStartOfDay().atOffset(now.getOffset());
        long used = usageStore.sumBytesForPeriod(org.getId(), start, end);
        return new OrgResponse(org.getId(), org.getName(), org.getPlan().name(),
                planQuotaBytes(org.getPlan()), used, org.getCreatedAt());
    }

    // ── API key management ───────────────────────────────────────────────────

    /**
     * POST /api/v1/organizations/me/api-keys
     * Creates a new API key. The raw key is returned ONCE and never stored.
     */
    @PostMapping("/me/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateKeyResponse createApiKey(
            @Valid @RequestBody CreateKeyRequest req,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        var created = apiKeyService.generate(principal.organization(), req.name());
        return new CreateKeyResponse(
                created.entity().getId(),
                created.entity().getKeyPrefix(),
                created.entity().getName(),
                created.rawKey(),   // shown ONCE
                created.entity().getCreatedAt()
        );
    }

    /**
     * GET /api/v1/organizations/me/api-keys
     * Lists all API keys (raw key NOT included).
     */
    @GetMapping("/me/api-keys")
    public List<ApiKeyResponse> listApiKeys(@AuthenticationPrincipal AuthenticatedOrg principal) {
        return apiKeyService.listForOrg(principal.organization().getId())
                .stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(), k.getKeyPrefix(), k.getName(),
                        k.isActive(), k.getLastUsedAt(), k.getCreatedAt()))
                .toList();
    }

    /**
     * DELETE /api/v1/organizations/me/api-keys/{id}
     * Revokes (deactivates) an API key. Idempotent.
     */
    @DeleteMapping("/me/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedOrg principal) {

        apiKeyService.revoke(id, principal.organization().getId());
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record OrgResponse(
            String         id,
            String         name,
            String         plan,
            long           planQuotaBytes,
            long           bytesUsedThisMonth,
            OffsetDateTime createdAt
    ) {}

    public record CreateKeyRequest(
            @NotBlank @Size(max = 128) String name
    ) {}

    public record CreateKeyResponse(
            String         id,
            String         prefix,
            String         name,
            String         key,       // raw key — show once, not stored
            OffsetDateTime createdAt
    ) {}

    public record ApiKeyResponse(
            String         id,
            String         prefix,
            String         name,
            boolean        active,
            OffsetDateTime lastUsedAt,
            OffsetDateTime createdAt
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long planQuotaBytes(Organization.Plan plan) {
        return switch (plan) {
            case FREE       -> 100L  * 1024 * 1024 * 1024;
            case PRO        -> 10L   * 1024 * 1024 * 1024 * 1024;
            case ENTERPRISE -> Long.MAX_VALUE;
        };
    }
}
