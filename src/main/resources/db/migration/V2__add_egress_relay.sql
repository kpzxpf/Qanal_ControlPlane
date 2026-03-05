-- ═══════════════════════════════════════════════════════════════════════════
--  V2: P2P relay support — each transfer now has an ingress relay (sender side)
--      and an optional egress relay (receiver side).
--
--  assigned_relay_id  = ingress  (where sender uploads to)
--  egress_relay_id    = egress   (where receiver downloads from)
--
--  Also stores the download port on the egress DataPlane so the recipient
--  CLI knows exactly where to connect for the download.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE transfers
    ADD COLUMN egress_relay_id   VARCHAR(36) REFERENCES relay_nodes(id),
    ADD COLUMN egress_download_port INTEGER;

CREATE INDEX idx_transfers_egress ON transfers(egress_relay_id)
    WHERE egress_relay_id IS NOT NULL;
