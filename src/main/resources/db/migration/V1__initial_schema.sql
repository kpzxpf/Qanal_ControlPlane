-- ═══════════════════════════════════════════════════════════════════════════
--  Qanal — initial schema
--  All PKs use VARCHAR(36) storing UUID v7 strings (time-sortable, B-tree friendly).
-- ═══════════════════════════════════════════════════════════════════════════

-- ── organizations ─────────────────────────────────────────────────────────
CREATE TABLE organizations (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    plan       VARCHAR(50)  NOT NULL DEFAULT 'free',   -- free | pro | enterprise
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── api_keys ──────────────────────────────────────────────────────────────
CREATE TABLE api_keys (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id),
    key_prefix      VARCHAR(8)   NOT NULL UNIQUE,   -- qnl_xxxx  (public identifier)
    key_hash        VARCHAR(64)  NOT NULL,           -- SHA-256 of full key (hex)
    name            VARCHAR(255),
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_keys_org ON api_keys(organization_id);
CREATE INDEX idx_api_keys_active ON api_keys(is_active) WHERE is_active = true;

-- ── relay_nodes ───────────────────────────────────────────────────────────
CREATE TABLE relay_nodes (
    id              VARCHAR(36)   PRIMARY KEY,
    region          VARCHAR(50)   NOT NULL,
    host            VARCHAR(255)  NOT NULL,
    quic_port       INTEGER       NOT NULL,
    capacity_bytes  BIGINT        NOT NULL,
    used_bytes      BIGINT        NOT NULL DEFAULT 0,
    status          VARCHAR(50)   NOT NULL DEFAULT 'HEALTHY',   -- HEALTHY | UNHEALTHY | DRAINING
    avg_rtt_ms      DOUBLE PRECISION,
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_relay_nodes_status ON relay_nodes(status);
CREATE INDEX idx_relay_nodes_region ON relay_nodes(region, status);

-- ── transfers ─────────────────────────────────────────────────────────────
CREATE TABLE transfers (
    id                 VARCHAR(36)       PRIMARY KEY,
    organization_id    VARCHAR(36)       NOT NULL REFERENCES organizations(id),
    file_name          VARCHAR(1024),
    file_size          BIGINT            NOT NULL,
    file_checksum      VARCHAR(128),                    -- xxHash64 of full file (sender-provided)
    status             VARCHAR(50)       NOT NULL,      -- see TransferStatus enum
    total_chunks       INTEGER           NOT NULL,
    completed_chunks   INTEGER           NOT NULL DEFAULT 0,
    version            BIGINT            NOT NULL DEFAULT 0,   -- optimistic locking
    source_region      VARCHAR(50),
    target_region      VARCHAR(50),
    assigned_relay_id  VARCHAR(36)       REFERENCES relay_nodes(id),
    bytes_transferred  BIGINT            NOT NULL DEFAULT 0,
    avg_throughput     DOUBLE PRECISION,               -- bytes/sec
    created_at         TIMESTAMPTZ       NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ       NOT NULL
);

-- Fast lookup of active transfers (only a fraction of the total table)
CREATE INDEX idx_transfers_org     ON transfers(organization_id);
CREATE INDEX idx_transfers_status  ON transfers(status);
CREATE INDEX idx_transfers_active  ON transfers(status, expires_at)
    WHERE status IN ('INITIATED','WAITING_SENDER','IN_PROGRESS','PAUSED','COMPLETING');
CREATE INDEX idx_transfers_expires ON transfers(expires_at)
    WHERE status NOT IN ('COMPLETED','FAILED','CANCELLED','EXPIRED');

-- ── transfer_chunks ───────────────────────────────────────────────────────
CREATE TABLE transfer_chunks (
    id           VARCHAR(36)  PRIMARY KEY,
    transfer_id  VARCHAR(36)  NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    chunk_index  INTEGER      NOT NULL,
    offset_bytes BIGINT       NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    checksum     VARCHAR(128),                          -- xxHash64 of chunk
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',  -- PENDING | TRANSFERRING | COMPLETED | FAILED
    retry_count  INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (transfer_id, chunk_index)
);

CREATE INDEX idx_chunks_transfer ON transfer_chunks(transfer_id);
CREATE INDEX idx_chunks_pending  ON transfer_chunks(transfer_id, status)
    WHERE status IN ('PENDING', 'TRANSFERRING', 'FAILED');

-- ── usage_records ─────────────────────────────────────────────────────────
CREATE TABLE usage_records (
    id               VARCHAR(36)  PRIMARY KEY,
    organization_id  VARCHAR(36)  NOT NULL REFERENCES organizations(id),
    transfer_id      VARCHAR(36)  REFERENCES transfers(id),
    bytes_transferred BIGINT      NOT NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_org_month ON usage_records(organization_id, recorded_at);
