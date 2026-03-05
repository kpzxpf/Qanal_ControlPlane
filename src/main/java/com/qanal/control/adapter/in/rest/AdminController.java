package com.qanal.control.adapter.in.rest;

import com.qanal.control.application.port.out.OrganizationStore;
import com.qanal.control.application.service.ApiKeyService;
import com.qanal.control.domain.model.Organization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/**
 * Internal admin endpoint for onboarding new B2B customers.
 *
 * <p>Protected by a static secret via {@code X-Admin-Secret} header.
 * Never expose this endpoint publicly without additional network-level protection.
 *
 * <p>Usage: ops team calls {@code POST /api/v1/admin/organizations} to create
 * an organization and receive the first API key in one step.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final OrganizationStore orgStore;
    private final ApiKeyService     apiKeyService;
    private final String            adminSecret;

    public AdminController(OrganizationStore orgStore,
                           ApiKeyService apiKeyService,
                           @Value("${qanal.admin.secret}") String adminSecret) {
        this.orgStore     = orgStore;
        this.apiKeyService = apiKeyService;
        this.adminSecret  = adminSecret;
    }

    /**
     * POST /api/v1/admin/organizations
     *
     * <p>Creates an organization + initial API key in one atomic call.
     * Returns the raw API key — it will NOT be shown again.
     */
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrgResponse createOrganization(
            @RequestHeader("X-Admin-Secret") String secret,
            @Valid @RequestBody CreateOrgRequest req) {

        if (!adminSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret");
        }

        // 1 — Create organization
        var org = new Organization();
        org.setName(req.name());
        org.setPlan(req.plan() != null ? req.plan() : Organization.Plan.FREE);
        Organization saved = orgStore.save(org);

        // 2 — Generate initial API key
        var created = apiKeyService.generate(saved, "default");

        return new CreateOrgResponse(
                saved.getId(),
                saved.getName(),
                saved.getPlan().name(),
                saved.getCreatedAt(),
                created.rawKey(),           // shown ONCE — customer must save this
                created.entity().getKeyPrefix()
        );
    }

    /**
     * POST /api/v1/admin/organizations/{orgId}/upgrade
     *
     * <p>Manually upgrades an organization's plan (e.g. after ENTERPRISE contract signed).
     */
    @PostMapping("/organizations/{orgId}/upgrade")
    public UpgradeResponse upgradePlan(
            @RequestHeader("X-Admin-Secret") String secret,
            @PathVariable String orgId,
            @Valid @RequestBody UpgradeRequest req) {

        if (!adminSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret");
        }

        // We use the JPA repo indirectly via OrganizationStore for simplicity.
        // A dedicated use case would be cleaner at scale.
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Use Stripe webhook to upgrade plans automatically, " +
                "or extend this endpoint with a findById + update.");
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record CreateOrgRequest(
            @NotBlank @Size(max = 255) String name,
            Organization.Plan plan   // optional — defaults to FREE
    ) {}

    public record CreateOrgResponse(
            String         orgId,
            String         name,
            String         plan,
            OffsetDateTime createdAt,
            String         apiKey,      // raw key — store immediately, never shown again
            String         apiKeyPrefix // for identification
    ) {}

    public record UpgradeRequest(
            @NotBlank Organization.Plan plan
    ) {}

    public record UpgradeResponse(String orgId, String plan) {}
}
