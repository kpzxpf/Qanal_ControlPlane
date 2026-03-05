-- ═══════════════════════════════════════════════════════════════════════════
--  V2 — Add egress relay routing + Stripe customer link
-- ═══════════════════════════════════════════════════════════════════════════

-- ── transfers: egress relay info ──────────────────────────────────────────
ALTER TABLE transfers
    ADD COLUMN egress_relay_id      VARCHAR(36) REFERENCES relay_nodes(id),
    ADD COLUMN egress_download_port INTEGER;

-- ── organizations: Stripe customer ID ─────────────────────────────────────
ALTER TABLE organizations
    ADD COLUMN stripe_customer_id VARCHAR(64);

CREATE UNIQUE INDEX idx_org_stripe_customer ON organizations(stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;
