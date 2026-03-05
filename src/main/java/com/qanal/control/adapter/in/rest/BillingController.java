package com.qanal.control.adapter.in.rest;

import com.qanal.control.adapter.in.security.AuthenticatedOrg;
import com.qanal.control.application.service.BillingService;
import com.qanal.control.domain.model.Organization;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints for Stripe billing integration.
 *
 * <p>Flow:
 * <ol>
 *   <li>CLI calls {@code POST /api/v1/billing/checkout} → gets Stripe URL → user opens it in browser</li>
 *   <li>User completes payment on Stripe</li>
 *   <li>Stripe calls {@code POST /api/v1/billing/webhook} → plan upgraded in DB</li>
 *   <li>User can manage subscription via {@code POST /api/v1/billing/portal}</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * POST /api/v1/billing/checkout
     *
     * <p>Creates a Stripe Checkout session for the PRO plan.
     * Returns a URL the user must open in their browser.
     */
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthenticatedOrg principal) {
        Organization org = principal.organization();
        if (org.getPlan() == Organization.Plan.ENTERPRISE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enterprise plan is managed manually. Contact sales@qanal.io.");
        }
        if (org.getPlan() == Organization.Plan.PRO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Already on PRO plan. Use /billing/portal to manage your subscription.");
        }
        try {
            String url = billingService.createCheckoutSession(org);
            return new CheckoutResponse(url);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Stripe error: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/billing/portal
     *
     * <p>Creates a Stripe Customer Portal session for self-service
     * (cancel subscription, update payment method, view invoices).
     */
    @PostMapping("/portal")
    public CheckoutResponse portal(@AuthenticationPrincipal AuthenticatedOrg principal) {
        Organization org = principal.organization();
        if (org.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Stripe subscription found. Upgrade to PRO first via /billing/checkout.");
        }
        try {
            String url = billingService.createPortalSession(org);
            return new CheckoutResponse(url);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Stripe error: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/billing/webhook
     *
     * <p>Stripe webhook endpoint. Must be configured in Stripe Dashboard.
     * Events handled: customer.subscription.created/updated/deleted.
     */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {

        try {
            billingService.handleWebhook(payload, sigHeader);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record CheckoutResponse(String url) {}
}
