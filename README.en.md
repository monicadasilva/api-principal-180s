# API Principal 180 Insurance

> [🇧🇷 Leia em Português](README.md)

REST API that mediates communication between partners and the insurer's API. The flow is: partner registers → gets a quote → generates a policy.

## Stack

- **Clojure 1.12** · Ring + Reitit + Malli · Integrant
- **Database:** PostgreSQL 16 · next.jdbc · HoneySQL · HikariCP (default pool 10)
- **HTTP client:** hato (with retry and exponential backoff)
- **Auth insurer:** Insurer JWT memoized via `core.memoize` (TTL 55 min)
- **Logging:** Telemere with structured JSON sink (one line per event, ready for aggregators)
- **Migrations:** ragtime

## Requirements

- Docker and Docker Compose

## Configuration

```bash
cp .env.example .env
```

| Variable             | Description               | Example                                                                   |
| -------------------- | ------------------------- | ------------------------------------------------------------------------- |
| `HTTP_PORT`          | API Principal port        | `3000`                                                                    |
| `DATABASE_URL`       | PostgreSQL connection URL | `jdbc:postgresql://db:5432/api_principal?user=postgres&password=postgres` |
| `INSURANCE_BASE_URL` | Insurer API base URL      | `http://insurance-api:5000`                                               |
| `INSURANCE_API_KEY`  | Insurer API access key    | ``                                                         |

## How to run

**Development** (hot-reload via volume):

```bash
docker compose --profile dev up
```

**Production** (compiled uber JAR):

```bash
docker compose --profile prod up --build
```

The API will be available at `http://localhost:3000`.

## Migrations

Migrations run automatically at startup via ragtime. Files live in `resources/migrations/` following the `NNN-description.up.sql` naming convention.

To add a new migration, create the next file in sequence — the system applies only pending ones. Schema rollbacks are done via a new forward migration, not down files.

## How to test

Integration tests require PostgreSQL running:

```bash
docker compose up db -d
clojure -M:test -m cognitect.test-runner -d test
```

## Authentication

`POST /partners` is public. All other routes require:

```
Authorization: Bearer <api_key>
```

The `api_key` is returned **once** in the `POST /partners` response body. Store it securely - there is no endpoint to retrieve it again.

> The server stores only the **SHA-256** of the `api_key` (column `api_key_hash`). A database dump does not compromise partner tokens: authentication compares the hash of the received token against the stored hash.

## Partner Journey

### 1. Register

```http
POST /partners
Content-Type: application/json

{ "name": "Acme Broker", "cnpj": "12.345.678/0001-95" }
```

```json
{
  "id": "partner-uuid",
  "name": "Acme Broker",
  "cnpj": "12345678000195",
  "api_key": "key-uuid"
}
```

> Store both `id` and `api_key`. The `api_key` will not be shown again.

### 2. Quote

```http
POST /partners/partner-uuid/quotes
Authorization: Bearer key-uuid
Content-Type: application/json

{ "age": 30, "sex": "f" }
```

```json
{
  "id": "quote-uuid",
  "age": 30,
  "gender": "F",
  "price": "500.0",
  "expire_at": "2025-12-31"
}
```

### 3. Policy

```http
POST /partners/partner-uuid/policies
Authorization: Bearer key-uuid
Content-Type: application/json

{
  "quotation_id":  "quote-uuid",
  "name":          "John Smith",
  "sex":           "f",
  "date_of_birth": "1996-03-15"
}
```

### 4. Retrieve

```http
GET /partners/partner-uuid/policies/policy-uuid
Authorization: Bearer key-uuid
```

## Operations

### Healthcheck

```http
GET /health
```

Returns `200 {"db": "up"}` when the pool can execute `SELECT 1`, or `503 {"db": "down"}` on failure. Does not check the insurer — hitting `/api/auth` on every probe would add load and latency for no real signal; upstream health surfaces indirectly via the `502/504` responses on real endpoints.

### Graceful shutdown

Jetty is configured with `setStopTimeout(30_000)`: on SIGTERM it stops accepting new connections and waits up to 30 s for in-flight requests to drain before `halt!` propagates to the remaining components (worker → pool).

### Logs

Output is **JSON line-per-event** on stdout, with fields `level`, `time`, `ns`, `msg`, optionally `id`, `data`, `error.{type,message}`. Ready for ingestion by Loki/Datadog/Cloudwatch with no custom parser.

## Endpoints

### `POST /partners`

Registers a partner. Public route.

```json
{ "name": "Acme", "cnpj": "12.345.678/0001-95" }
```

**Responses:** `201` created (includes `api_key`) · `400` invalid format · `409` duplicate CNPJ · `422` invalid check digits

---

### `POST /partners/:partner-id/quotes`

Requests a quote from the insurer and persists it locally.

```json
{ "age": 30, "sex": "f" }
```

`sex`: `m | M | f | F | n | N`

**Responses:** `201` created · `400` validation · `401` unauthorized

---

### `POST /partners/:partner-id/policies`

Creates a policy from a quote. Validates business rules before calling the insurer.

```json
{
  "quotation_id": "uuid",
  "name": "John Smith",
  "sex": "f",
  "date_of_birth": "1996-03-15"
}
```

**Responses:** `200` created · `400` HTTP validation · `401` unauthorized · `404` quote not found or does not belong to partner · `422` quote expired · `422` sex mismatch · `422` age incompatible with date of birth (±1 year)

---

### `GET /partners/:partner-id/policies/:policy-id`

Fetches a policy from the insurer. If it does not belong to the authenticated partner, returns `404`.

**Responses:** `200` found · `401` unauthorized · `404` not found

## Architecture

The chosen pattern is **functional Ports & Adapters** - without `defprotocol`. Ports are implicit function signatures; adapters are function maps built by higher-order functions and injected via Integrant at startup.

Each layer has a clear, non-leaking responsibility:

- **domain** - pure business rules, no I/O. Functions that take data and return data or booleans.
- **use_cases** - orchestration: receives ports (repos, insurer) as arguments and coordinates domain + side effects.
- **adapters/inbound** - translates HTTP → use case → HTTP. Contains no business logic.
- **adapters/outbound** - implements ports: DB via next.jdbc/HoneySQL, insurer via hato.
- **infrastructure** - assembles and wires everything via Integrant; no domain or transport logic.

```
src/api_principal/
├── core/
│   ├── domain/          # Pure business rules (no side effects)
│   │   ├── partner.clj  # valid-cnpj?, build
│   │   ├── quote.clj    # build
│   │   └── policy.clj   # owned-by?, quote-expired?, age-matches-dob?, sex-matches?
│   └── use_cases/       # Orchestration: domain + ports
│       ├── create_partner.clj
│       ├── create_quote.clj
│       ├── create_policy.clj
│       └── fetch_policy.clj
├── adapters/
│   ├── inbound/http/    # Ring handlers, Reitit/Malli routes, middleware
│   └── outbound/
│       ├── db/          # next.jdbc + HoneySQL (partner, quote, policy repos)
│       └── insurer/     # hato: auth.clj (token + memoize) · client.clj (retry/backoff)
└── infrastructure/      # Integrant: db pool, http client, server, adapters, logging
```

### Data Model

| Table      | Main columns                                    | Responsibility                         |
| ---------- | ----------------------------------------------- | -------------------------------------- |
| `partners` | `id, name, cnpj, api_key_hash, created_at`      | Entity exclusive to API Principal      |
| `quotes`   | `id, partner_id, age, gender, price, expire_at` | Persisted locally (insurer has no GET) |
| `policies` | `id, partner_id, created_at`                    | Local link; insurer is source of truth |

### Resilience in communication with the insurer

Every call goes through an `attempt` layer that catches specific exceptions and translates them into HTTP-shaped responses with a structured `:body`. The client never receives `:status` without `:body`:

| Scenario                                | Status   | Body code              | Retry?  |
|-----------------------------------------|----------|------------------------|---------|
| `200/4xx` from insurer                  | pass     | pass                   | no      |
| `5xx` from insurer                      | pass     | pass (or normalized)   | yes     |
| `UnknownHostException` (DNS)            | 502      | `insurer_unreachable`  | yes     |
| `ConnectException`                      | 502      | `insurer_unreachable`  | yes     |
| `HttpTimeoutException`                  | 504      | `insurer_timeout`      | yes     |
| `/api/auth` (token endpoint) failure    | 502      | `insurer_auth_failed`  | no      |
| Generic `Exception`                     | 502      | `insurer_error`        | no      |
| `5xx` response with empty body          | pass     | normalized             | yes     |

- **Transient retry:** `500/502/504` with exponential backoff (2 attempts, 200 ms base)
- **Expired token (401):** invalidates the JWT cache (`memo-clear!`) and retries once with a fresh token
- **Auth failed (`fetch-token!` threw):** converted to `502 insurer_auth_failed`, no retry — repeating would just fail the same way

The retry-on-5xx assumption holds because **inputs are already validated by Malli at the HTTP edge**, so a 5xx from the insurer can only be infrastructure instability, not a malformed payload. If the insurer ever starts returning 5xx for semantic validation, retry wastes two attempts but causes no harm.

### Local ↔ insurer consistency

Policy creation is a distributed operation: the insurer creates the record and the service persists the `(policy_id, partner_id)` link locally. If the Postgres `INSERT` fails after the insurer's `200`, we would have a policy existing remotely and no local record — every future `GET /policies/:id` would return `404` due to ownership failure.

To prevent this silent inconsistency:

1. The `INSERT` on the `policies` table is wrapped in a `try`.
2. On failure, the `(policy_id, partner_id)` tuple is enqueued in the `pending_policy_saves` table with the original error.
3. A background worker (Integrant component `:worker/policy-retry`, 30 s interval) reads pending rows, retries the `INSERT`, and deletes them on success. On failure, it updates `attempts` and `last_error` for diagnostics.
4. If the enqueue itself fails (DB completely down), a critical log (`::policy-persistence-lost`) is emitted with all data — last line of defense for manual reconciliation.

The original request always returns `200` to the client when the insurer confirms creation, even if the local save was deferred. The policy is fully usable (local queries via `/policies/:id` work as soon as the worker drains the queue).

> Known limitation: in a multi-instance deployment, two worker instances may race on the same row. The fix is wrapping the `SELECT` in `FOR UPDATE SKIP LOCKED`. Not implemented because it's single-instance today.

## Technical decisions

### Ports & Adapters without `defprotocol`

`defprotocol` creates nominal coupling: it requires defining a concrete type that implements the protocol, adding unnecessary boilerplate for a service with few implementations per port. Function maps (`{:save-partner! fn, :find-partner fn}`) are equally substitutable in tests - just swap the map - and more idiomatic in Clojure, where first-class functions eliminate the need for polymorphic dispatch at this level.

### Integrant

Component and Mount are common alternatives. Integrant was chosen because the dependency graph is declared in EDN (`resources/system.edn`), separating configuration from code. This allows swapping adapters at test time without modifying production namespaces, and makes the entire system readable in a single configuration file.

### Reitit + Malli

Compojure is simpler but lacks native type coercion. Reitit integrates with Malli to validate and coerce input and output parameters at the HTTP boundary, eliminating manual conversions in handlers. Malli was preferred over `clojure.spec` for its native JSON Schema support and human-readable error messages ready for the client.

### next.jdbc + HoneySQL

next.jdbc is the modern JDBC wrapper recommended by the Clojure community (replaces `clojure.java.jdbc`). HoneySQL represents queries as Clojure maps - testable, composable, and immune to SQL injection by construction. Raw SQL strings are simple but fragile under refactoring; an ORM would be unnecessary complexity for three tables.

### hato

hato is a thin wrapper over Java 11's native `java.net.http.HttpClient`, with no Apache HTTPClient dependency. The exponential backoff retry and token invalidation are implemented in the application layer (`client.clj`), keeping explicit control over behavior rather than relying on opaque HTTP client configuration.

### core.memoize/ttl

The insurer requires JWT authentication with expiration. Fetching a new token on every request would add latency and could hit rate limits. `core.memoize/ttl` caches the token with a 55-minute TTL - 5 minutes below the token's expiry - ensuring an expired token is never sent. On an unexpected 401 (early rotation), `memo-clear!` invalidates the cache and forces immediate re-authentication.

### Telemere

Telemere is the successor to Timbre (same author, taoensso). It has native structured logging support and is compatible with OpenTelemetry, allowing evolution toward distributed observability without a library swap. Timbre was discarded as it is considered legacy by its own author.
