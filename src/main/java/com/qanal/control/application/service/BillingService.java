package com.qanal.control.application.service;

import com.qanal.control.application.port.out.OrganizationStore;
import com.qanal.control.domain.model.Organization;
import com.qanal.control.infrastructure.config.QanalProperties;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.model.checkout.Session.LineItem;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integrates with Stripe for subscription billing.
 *
 * <p>Plans:
 * <ul>
 *   <li>FREE — 100 GB/month, no payment required</li>
 *   <li>PRO — $299/month, 10 TB/month, Stripe subscription</li>
 *   <li>ENTERPRISE — manual contract, upgraded via admin endpoint</li>
 * </ul>
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final OrganizationStore orgStore;
    private final QanalProperties   props;

    public BillingService(OrganizationStore orgStore, QanalProperties props) {
        this.orgStore = orgStore;
        this.props    = props;
        Stripe.apiKey = props.stripe().secretKey();
    }

    /**
     * Creates a Stripe Checkout session for upgrading to PRO.
     *
     * @return the Checkout URL to redirect the user to
     */
    public String createCheckoutSession(Organization org) throws StripeException {
        String customerId = ensureStripeCustomer(org);

        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(props.stripe().proPriceId())
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(props.stripe().successUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(props.stripe().cancelUrl())
                .putMetadata("orgId", org.getId())
                .build();

        var session = com.stripe.model.checkout.Session.create(params);
        log.info("Stripe checkout session created for org {} → {}", org.getId(), session.getId());
        return session.getUrl();
    }

    /**
     * Creates a Stripe Customer Portal session for self-service (cancel, update card, etc.).
     *
     * @return the portal URL
     */
    public String createPortalSession(Organization org) throws StripeException {
        String customerId = ensureStripeCustomer(org);

        var params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(props.stripe().successUrl().replace("/success", "/dashboard"))
                .build();

        Session session = Session.create(params);
        log.info("Stripe portal session created for org {}", org.getId());
        return session.getUrl();
    }

    /**
     * Processes a Stripe webhook event.
     * Validates the signature and updates the org's plan accordingly.
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, props.stripe().webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature invalid: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Stripe signature");
        }

        log.info("Stripe webhook: {} [{}]", event.getType(), event.getId());

        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated" -> handleSubscriptionActivated(event);
            case "customer.subscription.deleted"  -> handleSubscriptionCancelled(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void handleSubscriptionActivated(Event event) {
        var sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow();

        String customerId = sub.getCustomer();
        String status     = sub.getStatus(); // active, trialing, past_due, etc.

        Organization org = orgStore.findByStripeCustomerId(customerId)
                .orElse(null);
        if (org == null) {
            log.warn("No org found for Stripe customer {}", customerId);
            return;
        }

        Organization.Plan newPlan = "active".equals(status) || "trialing".equals(status)
                ? Organization.Plan.PRO
                : Organization.Plan.FREE;

        org.setPlan(newPlan);
        orgStore.save(org);
        log.info("Org {} plan updated to {} (subscription status={})", org.getId(), newPlan, status);
    }

    private void handleSubscriptionCancelled(Event event) {
        var sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElseThrow();

        Organization org = orgStore.findByStripeCustomerId(sub.getCustomer())
                .orElse(null);
        if (org == null) return;

        org.setPlan(Organization.Plan.FREE);
        orgStore.save(org);
        log.info("Org {} downgraded to FREE (subscription cancelled)", org.getId());
    }

    /** Returns existing Stripe customer ID or creates a new one. */
    private String ensureStripeCustomer(Organization org) throws StripeException {
        if (org.getStripeCustomerId() != null) {
            return org.getStripeCustomerId();
        }

        var customer = Customer.create(CustomerCreateParams.builder()
                .setName(org.getName())
                .putMetadata("orgId", org.getId())
                .build());

        org.setStripeCustomerId(customer.getId());
        orgStore.save(org);
        log.info("Created Stripe customer {} for org {}", customer.getId(), org.getId());
        return customer.getId();
    }
}
