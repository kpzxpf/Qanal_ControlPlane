# Qanal Control Plane

The orchestration brain of the Qanal platform. Handles authentication, transfer lifecycle management, chunk planning, quota enforcement, billing, and relay routing. Built with Spring Boot 4 on Java 21.

---

## Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Running Locally](#running-locally)
- [Configuration Reference](#configuration-reference)
- [Database Migrations](#database-migrations)
- [REST API](#rest-api)
  - [Authentication](#authentication)
  - [Transfers](#transfers)
  - [Organizations & API Keys](#organizations--api-keys)
  - [Billing](#billing)
  - [Admin](#admin)
- [gRPC Interface](#grpc-interface)
- [Security](#security)
- [Monitoring](#monitoring)
- [Running Tests](#running-tests)
- [Production Deployment](#production-deployment)
- [Design Decisions](#design-decisions)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Control Plane (port 8080 / 9090)          │
│                                                              │
│  REST :8080          gRPC :9090                              │
│  ┌─────────────┐     ┌──────────────┐                        │
│  │ Controllers │     │ gRPC Adapter │◄── DataPlane nodes     │
│  └──────┬──────┘     └──────┬───────┘                        │
│         │                   │                                │
│  ┌──────▼───────────────────▼──────┐                         │
│  │         Use Cases / Services    │                         │
│  └──────┬───────────────────┬──────┘                         │
│         │                   │                                │
│  ┌──────▼──────┐    ┌───────▼──────┐                         │
│  │ PostgreSQL  │    │    Redis      │                         │
│  │ (JPA+Flyway)│    │ (cache/pubsub)│                        │
│  └─────────────┘    └──────────────┘                         │
└──────────────────────────────────────────────────────────────┘
```

The codebase follows **Hexagonal Architecture** (Ports & Adapters):

```
src/main/java/com/qanal/control/
├── domain/
│   ├── model/          # JPA entities: Transfer, TransferChunk, Organization, ApiKey, RelayNode, UsageRecord
│   ├── service/        # TransferStateMachine, AdaptiveChunkPlanner
│   └── exception/      # TransferNotFoundException, InvalidTransferStateException, QuotaExceededException
├── application/
│   ├── port/in/        # 11 use case interfaces (InitiateTransferUseCase, etc.)
│   ├── port/out/       # Output port interfaces (TransferStore, ChunkStore, RelayStore, …)
│   ├── usecase/        # Use case implementations
│   └── service/        # ApiKeyService, BillingService
├── adapter/
│   ├── in/rest/        # TransferController, OrganizationController, AdminController, BillingController
│   ├── in/security/    # ApiKeyAuthFilter, RateLimitFilter, AuthenticatedOrg
│   ├── in/grpc/        # TransferGrpcAdapter
│   ├── out/persistence/# JPA repositories + adapters
│   └── out/cache/      # Redis adapters (rate-limit, API key cache, quota, progress, chunk counter)
└── infrastructure/
    ├── common/         # UuidV7
    ├── config/         # AppConfig, GrpcServerConfig, SecurityConfig, QanalProperties
    └── scheduling/     # TransferExpiryScheduler, RelayHealthScheduler
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Gradle | 8+ (wrapper included) |
| Docker | 24+ (for local PostgreSQL + Redis) |
| Docker Compose | v2+ |

---

## Running Locally

### 1. Start dependencies

```bash
cd ControlPlane
docker compose up -d
# Starts PostgreSQL on :5432 and Redis on :6379 with no password (dev mode)
```

### 2. Run the application

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The dev profile enables:
- SQL logging (Hibernate `format_sql`, `generate_statistics`)
- DEBUG log level for all `com.qanal` packages
- `flyway.baseline-on-migrate=true` for existing schemas

### 3. Verify startup

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}
```

### 4. Create a test organization

```bash
curl -X POST http://localhost:8080/api/v1/admin/organizations \
  -H "X-Admin-Secret: change-me-in-production" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Org", "plan": "FREE"}'
```

Response (save the `apiKey` — it is shown only once):
```json
{
  "orgId": "019500ab-xxxx-7xxx-xxxx-xxxxxxxxxxxx",
  "name": "Test Org",
  "plan": "FREE",
  "createdAt": "2026-03-05T12:00:00Z",
  "apiKey": "qnl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "apiKeyPrefix": "qnl_a1b2"
}
```

---

## Configuration Reference

Configuration is split across `application.yml` (base) and `application-dev.yml` (dev overrides). All sensitive values are injected via environment variables.

### Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `DB_HOST` | `localhost` | Prod | PostgreSQL host |
| `DB_PORT` | `5432` | No | PostgreSQL port |
| `DB_NAME` | `qanal` | No | Database name |
| `DB_USER` | `qanal` | No | Database user |
| `DB_PASSWORD` | `qanal` | **Prod** | Database password |
| `REDIS_HOST` | `localhost` | Prod | Redis host |
| `REDIS_PORT` | `6379` | No | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | **Prod** | Redis password |
| `SERVER_PORT` | `8080` | No | HTTP port |
| `GRPC_PORT` | `9090` | No | gRPC port |
| `AGENT_REGION` | `local` | No | Region label for metrics |
| `QANAL_ADMIN_SECRET` | `change-me-in-production` | **Prod** | Admin endpoint secret |
| `STRIPE_SECRET_KEY` | `sk_test_placeholder` | **Prod** | Stripe API secret |
| `STRIPE_WEBHOOK_SECRET` | `whsec_placeholder` | **Prod** | Stripe webhook signing secret |
| `STRIPE_PRO_PRICE_ID` | `price_placeholder` | **Prod** | Stripe Price ID for PRO plan |
| `STRIPE_SUCCESS_URL` | `https://qanal.io/billing/success` | **Prod** | Post-checkout redirect |
| `STRIPE_CANCEL_URL` | `https://qanal.io/billing/cancel` | **Prod** | Checkout cancel redirect |

### Transfer Tuning (`qanal.transfer.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `default-expiry-hours` | `24` | Hours before an unfinished transfer expires |
| `max-file-size-bytes` | `109951162777600` | 100 TB |
| `min-chunk-size-bytes` | `67108864` | 64 MB |
| `max-chunk-size-bytes` | `268435456` | 256 MB |
| `max-chunks` | `1024` | Max chunks per transfer |
| `min-parallel-streams` | `4` | Min QUIC streams the DataPlane uses |
| `max-parallel-streams` | `32` | Max QUIC streams the DataPlane uses |

### Rate Limiting (`qanal.rate-limit.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `requests-per-minute` | `100` | Per API key, enforced via Redis Lua script |

---

## Database Migrations

Managed by **Flyway**. Migrations run automatically at startup.

```
src/main/resources/db/migration/
├── V1__initial_schema.sql        # organizations, api_keys, relay_nodes, transfers, transfer_chunks, usage_records
└── V2__add_egress_relay_and_stripe.sql  # egress_relay_id, egress_download_port, stripe_customer_id
```

### Schema Overview

| Table | Purpose |
|-------|---------|
| `organizations` | Tenants. Each has a `plan` (FREE/PRO/ENTERPRISE) and optional `stripe_customer_id` |
| `api_keys` | Hashed (SHA-256) API keys per org. Raw key shown only at creation |
| `relay_nodes` | DataPlane nodes registered via heartbeat. Tracks capacity, region, latency |
| `transfers` | Transfer lifecycle. Versioned for optimistic locking |
| `transfer_chunks` | Individual 64–256 MB chunks within a transfer |
| `usage_records` | Append-only log of bytes transferred per org per transfer |

All primary keys are **UUID v7** strings (time-sortable, B-tree friendly).

---

## REST API

### Authentication

All endpoints except `/api/v1/admin/*` and `/api/v1/billing/webhook` require:

```
X-API-Key: qnl_your_key_here
```

The key is looked up via SHA-256 hash with a **5-minute Redis cache** to avoid DB hits on every request.

### Error Format

All errors return a consistent JSON body:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Transfer not found: 019500ab-...",
  "timestamp": "2026-03-05T12:00:00Z"
}
```

---

### Transfers

#### `POST /api/v1/transfers` — Initiate transfer

Request:
```json
{
  "fileName": "dataset.tar.gz",
  "fileSize": 107374182400,
  "fileChecksum": "a1b2c3d4e5f6...",
  "sourceRegion": "us-east-1",
  "targetRegion": "eu-west-1",
  "estimatedBandwidthBps": 10000000000,
  "estimatedRttMs": 80
}
```

Response `201 Created`:
```json
{
  "id": "019500ab-1234-7abc-def0-123456789abc",
  "status": "INITIATED",
  "fileName": "dataset.tar.gz",
  "fileSize": 107374182400,
  "totalChunks": 400,
  "completedChunks": 0,
  "progressPercent": 0,
  "bytesTransferred": 0,
  "avgThroughputBps": null,
  "relayHost": "relay.us-east-1.qanal.io",
  "relayQuicPort": 4433,
  "egressRelayHost": "relay.eu-west-1.qanal.io",
  "egressDownloadPort": 4434,
  "createdAt": "2026-03-05T12:00:00Z",
  "expiresAt": "2026-03-06T12:00:00Z",
  "completedAt": null,
  "chunks": [
    { "chunkIndex": 0, "offsetBytes": 0, "sizeBytes": 268435456 },
    { "chunkIndex": 1, "offsetBytes": 268435456, "sizeBytes": 268435456 }
  ]
}
```

The `chunks` array is **only present on the creation response**. Subsequent GETs return `null` for `chunks`.

#### `GET /api/v1/transfers` — List transfers

Paginated. Query params: `page` (0-based), `size` (default 20), `sort`.

```bash
GET /api/v1/transfers?page=0&size=20&sort=createdAt,desc
```

#### `GET /api/v1/transfers/{id}` — Get transfer status

Returns the same shape as above, `chunks` is `null`.

#### `GET /api/v1/transfers/{id}/progress` — SSE progress stream

`Content-Type: text/event-stream`. Emits events every ~500 ms:

```
data: {"status":"IN_PROGRESS","progressPercent":42,"bytesTransferred":45097156608,"currentThroughputBps":8589934592}
data: {"status":"COMPLETED","progressPercent":100,"bytesTransferred":107374182400,"currentThroughputBps":0}
```

The stream closes automatically when the transfer reaches a terminal state (COMPLETED, FAILED, CANCELLED, EXPIRED).

#### `POST /api/v1/transfers/{id}/pause`

Transitions `IN_PROGRESS` → `PAUSED`. Returns updated transfer.

#### `POST /api/v1/transfers/{id}/resume`

Transitions `PAUSED` → `IN_PROGRESS`. Returns updated transfer.

#### `POST /api/v1/transfers/{id}/cancel`

Transitions any non-terminal state → `CANCELLED`. Returns updated transfer.

### Transfer State Machine

```
INITIATED → WAITING_SENDER → IN_PROGRESS → COMPLETING → COMPLETED
                                  │
                                  ├──→ PAUSED → IN_PROGRESS
                                  ├──→ CANCELLED
                                  └──→ FAILED
INITIATED / WAITING_SENDER / IN_PROGRESS / PAUSED ──→ EXPIRED (scheduler)
```

---

### Organizations & API Keys

#### `GET /api/v1/organizations/me` — Current org

```json
{
  "id": "019500ab-...",
  "name": "Acme Corp",
  "plan": "FREE",
  "planQuotaBytes": 107374182400,
  "bytesUsedThisMonth": 5368709120,
  "createdAt": "2026-01-01T00:00:00Z"
}
```

#### `POST /api/v1/organizations/me/api-keys` — Create API key

Request:
```json
{ "name": "CI/CD pipeline" }
```

Response `201 Created`:
```json
{
  "id": "019500ab-...",
  "prefix": "qnl_a1b2",
  "name": "CI/CD pipeline",
  "key": "qnl_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "createdAt": "2026-03-05T12:00:00Z"
}
```

**The raw `key` is shown exactly once. Store it immediately.**

#### `GET /api/v1/organizations/me/api-keys` — List keys

Returns all keys for the org. The raw key is **never** returned here — only prefix, name, active status, and last-used timestamp.

#### `DELETE /api/v1/organizations/me/api-keys/{id}` — Revoke key

`204 No Content`. Idempotent. The key is deactivated and the Redis cache entry is evicted immediately.

---

### Billing

#### `POST /api/v1/billing/checkout` — Start PRO upgrade

```json
{ "url": "https://checkout.stripe.com/pay/cs_test_..." }
```

Open this URL in a browser. After payment, Stripe calls the webhook automatically.

Not available if already on PRO or ENTERPRISE plan.

#### `POST /api/v1/billing/portal` — Manage subscription

```json
{ "url": "https://billing.stripe.com/session/..." }
```

Self-service portal: cancel, update payment method, view invoices.

Requires an existing Stripe subscription (`stripe_customer_id` must be set).

#### `POST /api/v1/billing/webhook` — Stripe webhook (no auth header)

Signature-verified via `Stripe-Signature` header and `STRIPE_WEBHOOK_SECRET`.

Handled events:
- `customer.subscription.created` → plan set to PRO
- `customer.subscription.updated` → plan updated
- `customer.subscription.deleted` → plan downgraded to FREE

**Stripe Dashboard setup:**
1. Webhooks → Add endpoint → `https://yourdomain.com/api/v1/billing/webhook`
2. Select events: `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted`
3. Copy signing secret → `STRIPE_WEBHOOK_SECRET`

---

### Admin

Protected by `X-Admin-Secret` header. Keep this endpoint behind a VPN or firewall in production.

#### `POST /api/v1/admin/organizations` — Create organization

Request:
```json
{
  "name": "Acme Corp",
  "plan": "FREE"
}
```

`plan` is optional, defaults to `FREE`. Valid values: `FREE`, `PRO`, `ENTERPRISE`.

Response `201 Created`:
```json
{
  "orgId": "019500ab-...",
  "name": "Acme Corp",
  "plan": "FREE",
  "createdAt": "2026-03-05T12:00:00Z",
  "apiKey": "qnl_a1b2c3d4...",
  "apiKeyPrefix": "qnl_a1b2"
}
```

Creates the organization and generates the first API key atomically. **Save the `apiKey`.**

---

## gRPC Interface

Port `9090`. Used exclusively by DataPlane nodes.

Proto file: `src/main/proto/transfer_report.proto`

| RPC | Direction | Description |
|-----|-----------|-------------|
| `RegisterAgent` | DataPlane → CP | Announces a new DataPlane node (host, port, capacity) |
| `SendHeartbeat` | DataPlane → CP | Reports CPU, memory, active transfers every 5 s |
| `ReportChunkCompleted` | DataPlane → CP | Marks a chunk as done; triggers finalization when all chunks are done |
| `FinalizeTransfer` | DataPlane → CP | Confirms full file assembled + checksum verified |

The gRPC server is managed via `SmartLifecycle` to integrate cleanly with Spring's application context lifecycle (starts after all beans are ready, shuts down before the context closes).

Max inbound message size: **4 MB**.

---

## Security

| Layer | Mechanism |
|-------|-----------|
| API auth | `X-API-Key` header → SHA-256 hash → Redis-cached DB lookup (5-min TTL) |
| Rate limiting | Redis Lua script: atomic INCR + EXPIRE per API key, 100 req/min |
| Admin endpoints | Static `X-Admin-Secret` header |
| Stripe webhooks | `Stripe-Signature` HMAC verification |
| Quota enforcement | Redis-cached monthly usage (1-min TTL), evicted on new usage records |
| Passwords in DB | API keys stored as SHA-256 hex; raw keys never persisted |

### Spring Security

Default form login is disabled. All security is handled by two servlet filters wired before Spring Security:

- **`ApiKeyAuthFilter`** — extracts `X-API-Key`, resolves org via cache/DB, populates `SecurityContext`
- **`RateLimitFilter`** — checks Redis INCR counter, returns `429` with `Retry-After` header if exceeded

Public paths (no auth required): `/actuator/**`, `/api/v1/billing/webhook`

---

## Monitoring

### Health

```
GET /actuator/health
```

Shows status of PostgreSQL and Redis connections, disk space, and JVM.

### Metrics

```
GET /actuator/prometheus
```

Exposes all Spring Boot metrics + custom Qanal counters in Prometheus format. Tag `application=qanal-control-plane`, `region=<AGENT_REGION>`.

### Logs

Structured via SLF4J / Logback. In dev mode all `com.qanal` packages log at DEBUG level.

---

## Running Tests

```bash
# Unit tests (no external dependencies)
./gradlew test

# Integration tests (requires Docker — uses Testcontainers for PostgreSQL + Redis)
./gradlew test --tests "*Integration*"

# Full test suite
./gradlew check
```

---

## Production Deployment

### Docker (recommended)

The `Dockerfile` performs a two-stage build:

```
Stage 1 (builder): eclipse-temurin:21-jdk-alpine → ./gradlew bootJar
Stage 2 (runtime): eclipse-temurin:21-jre-alpine  → runs app.jar
```

Runtime flags: `-XX:+UseZGC -XX:MaxRAMPercentage=75`

Memory limit in `docker-compose.prod.yml`: **1 GB**

### Full stack

```bash
# From repo root
cp .env.example .env
# Fill in all values in .env

docker compose -f docker-compose.prod.yml up -d

# Verify
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8080/actuator/health
```

### Scaling

The Control Plane is stateless (all state in PostgreSQL + Redis) — run multiple replicas behind a load balancer. Ensure all replicas share the same PostgreSQL and Redis instances.

gRPC port must be exposed from each replica for DataPlane nodes to connect.

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| UUID v7 primary keys | Time-sortable → sequential B-tree inserts, no hot-page contention |
| Hexagonal architecture | Domain logic is fully isolated from Spring, JPA, and Redis |
| Redis chunk counter (INCR) | Replaces optimistic-lock storm when 32+ streams complete simultaneously |
| Redis API key cache (5-min TTL) | Eliminates DB round-trip on every request; evicted immediately on revoke |
| Virtual threads (`spring.threads.virtual.enabled=true`) | Tomcat threads park instead of blocking; 200 threads handle thousands of concurrent SSE streams |
| Flyway `validate-on-migrate=true` | Prevents schema drift between environments |
| Atomic Lua rate-limit script | Single Redis round-trip: INCR + conditional EXPIRE, no TOCTOU race |
| `SmartLifecycle` gRPC server | gRPC starts after Spring context is fully ready; shuts down before beans are destroyed |
