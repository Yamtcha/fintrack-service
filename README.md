# fintrack-api

API ingestion layer for the FinTrack financial transaction aggregation platform.

Receives raw transaction data from external source systems, authenticates them via API keys, validates and translates payloads to a canonical format, then publishes events to RabbitMQ for downstream processing. All ingestion is **asynchronous** — every valid batch request returns `202 Accepted` immediately.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Security Model](#security-model)
- [Adapter Layer](#adapter-layer)
- [Ingestion Flow](#ingestion-flow)
- [Message Publishing](#message-publishing)
- [Database Migrations](#database-migrations)
- [Running Tests](#running-tests)
- [Dependencies](#dependencies)

---

## Architecture Overview

```
External Source System
        │
        │  POST /v1/sources/transactions
        │  Authorization: Bearer sk_live_...
        ▼
┌─────────────────────────────────────────────┐
│              fintrack-api                   │
│                                             │
│  ApiKeyAuthFilter ──► SecurityContext       │
│         │                                  │
│         ▼                                  │
│  IngestionController                        │
│         │                                  │
│         ▼                                  │
│  IngestionService                           │
│    ├── Idempotency check (SyncJob)          │
│    ├── Return 202 immediately               │
│    └── @Async processAsync()               │
│           │                                │
│           ▼                                │
│    AdapterRegistry ──► TransactionAdapter  │
│           │                                │
│           ▼                                │
│    PublisherService                         │
└─────────────┬───────────────────────────────┘
              │
              ▼
     RabbitMQ (fintrack.transactions)
              │
     ┌────────┼────────┐
     ▼        ▼        ▼
 spending  portfolio  debt
```

---

## Project Structure

```
fintrack-api/
├── pom.xml
├── src/                                      ← Java source root
│   └── com/fintrack/api/
│       ├── FintrackApiApplication.java
│       ├── adapter/
│       │   ├── TransactionAdapter.java
│       │   ├── AdapterRegistry.java
│       │   ├── CreditAdapter.java
│       │   ├── DebitAdapter.java
│       │   ├── LoansAdapter.java
│       │   └── InvestmentsAdapter.java
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── RabbitMQConfig.java
│       │   ├── RedisConfig.java
│       │   └── OpenApiConfig.java
│       ├── controller/
│       │   ├── SourceController.java
│       │   └── IngestionController.java
│       ├── domain/
│       │   ├── entity/
│       │   │   ├── Source.java
│       │   │   ├── ApiKey.java
│       │   │   └── SyncJob.java
│       │   └── repository/
│       │       ├── SourceRepository.java
│       │       ├── ApiKeyRepository.java
│       │       └── SyncJobRepository.java
│       ├── dto/
│       │   ├── request/
│       │   │   ├── SourceRegistrationRequest.java
│       │   │   ├── TransactionIngestionRequest.java
│       │   │   └── RawTransactionDto.java
│       │   └── response/
│       │       ├── SourceRegistrationResponse.java
│       │       ├── IngestionResponse.java
│       │       └── SourceStatusResponse.java
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       ├── security/
│       │   ├── ApiKeyAuthFilter.java
│       │   └── SourceIdentity.java
│       └── service/
│           ├── SourceService.java
│           ├── IngestionService.java
│           ├── ApiKeyService.java
│           └── PublisherService.java
├── resources/                                ← main resources root
│   ├── application.yml
│   └── db/migration/
│       ├── V1__create_sources_table.sql
│       ├── V2__create_api_keys_table.sql
│       └── V3__create_sync_jobs_table.sql
└── test/                                     ← test source root
    └── com/fintrack/api/
        ├── controller/
        │   ├── SourceControllerTest.java
        │   └── IngestionControllerTest.java
        └── adapter/
            ├── CreditAdapterTest.java
            ├── DebitAdapterTest.java
            ├── LoansAdapterTest.java
            └── InvestmentsAdapterTest.java
```

The non-standard source layout is configured in `pom.xml`:

```xml
<build>
    <sourceDirectory>src</sourceDirectory>
    <testSourceDirectory>test</testSourceDirectory>
    <resources>
        <resource><directory>resources</directory></resource>
    </resources>
</build>
```

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| PostgreSQL | 15+ |
| RabbitMQ | 3.12+ |
| Redis | 7+ |
| fintrack-common | 1.0.0-SNAPSHOT (local Nexus) |

---

## Getting Started

### 1. Build fintrack-common

`fintrack-common` must be available in your local Nexus or Maven local repository before building this module.

```bash
cd fintrack-common
mvn clean install
```

### 2. Start infrastructure

```bash
# PostgreSQL
docker run -d --name fintrack-postgres \
  -e POSTGRES_DB=fintrack \
  -e POSTGRES_USER=fintrack \
  -e POSTGRES_PASSWORD=fintrack \
  -p 5432:5432 postgres:15

# RabbitMQ
docker run -d --name fintrack-rabbit \
  -e RABBITMQ_DEFAULT_USER=fintrack \
  -e RABBITMQ_DEFAULT_PASS=fintrack \
  -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# Redis
docker run -d --name fintrack-redis \
  -p 6379:6379 redis:7
```

### 3. Build and run

```bash
mvn clean package -DskipTests
java -jar target/fintrack-api-1.0.0-SNAPSHOT.jar
```

The application starts on **port 8080** by default.

### 4. Explore the API

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Configuration

All settings live in `resources/application.yml`.

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/fintrack` | PostgreSQL connection |
| `spring.rabbitmq.host` | `localhost` | RabbitMQ host |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.threads.virtual.enabled` | `true` | Java 21 virtual threads |
| `fintrack.ingestion.batch-size-limit` | `1000` | Max transactions per batch |
| `fintrack.ingestion.dedup-ttl-hours` | `24` | Fingerprint TTL in Redis |

---

## API Reference

### Source Management — `/v1/sources`

#### Register a source
```
POST /v1/sources/register
```
No authentication required. Returns a one-time API key — store it securely, it is never shown again.

**Request**
```json
{
  "name": "Chase Checking",
  "sourceType": "DEBIT",
  "config": {
    "currency": "USD",
    "timezone": "America/New_York",
    "accountIdentifier": "****4321"
  }
}
```

**Response `201 Created`**
```json
{
  "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sourceType": "DEBIT",
  "apiKey": "sk_live_abc123...",
  "status": "ACTIVE",
  "registeredAt": "2026-05-13T09:00:00Z"
}
```

Supported `sourceType` values: `DEBIT`, `CREDIT`, `LOANS`, `INVESTMENTS`

---

#### Get source details
```
GET /v1/sources/{sourceId}
Authorization: Bearer sk_live_...
```
Response `200 OK` — source record (without API key).

---

#### Get source sync status
```
GET /v1/sources/{sourceId}/status
Authorization: Bearer sk_live_...
```

**Response `200 OK`**
```json
{
  "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "ACTIVE",
  "lastSync": "2026-05-13T08:00:00Z",
  "totalBatchesProcessed": 42,
  "totalTransactionsProcessed": 10420
}
```

---

#### Deactivate a source
```
DELETE /v1/sources/{sourceId}
Authorization: Bearer sk_live_...
```
Soft delete — sets status to `INACTIVE`. Response `204 No Content`.

---

### Transaction Ingestion — `/v1/sources`

#### Submit a transaction batch
```
POST /v1/sources/transactions
Authorization: Bearer sk_live_...
```

**Request**
```json
{
  "batchId": "BATCH-001",
  "transactions": [
    {
      "externalId": "TXN-001",
      "amount": 4999,
      "currency": "USD",
      "description": "WOOLWORTHS SANDTON",
      "transactedAt": "2026-05-13T10:22:00Z",
      "metadata": {}
    }
  ]
}
```

- `batchId` is an **idempotency key** — re-submitting the same `batchId` returns the existing job without reprocessing.
- `amount` is always in **minor units** (cents, pence, etc.).
- Maximum batch size: **1000 transactions**.

**Response `202 Accepted`**
```json
{
  "batchId": "BATCH-001",
  "jobId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "PENDING",
  "totalReceived": 1,
  "message": "Batch accepted for processing"
}
```

---

#### Get batch sync status
```
GET /v1/sources/sync/status/{batchId}
Authorization: Bearer sk_live_...
```
Response `200 OK` — `SyncJob` record with current processing status.

`status` lifecycle: `PENDING` → `PROCESSING` → `COMPLETED` | `PARTIAL_FAILURE` | `FAILED`

---

#### Get sync history
```
GET /v1/sources/sync/history?page=0&size=20&status=COMPLETED
Authorization: Bearer sk_live_...
```

| Query param | Default | Description |
|---|---|---|
| `page` | `0` | Page number (zero-based) |
| `size` | `20` | Page size |
| `status` | *(none)* | Optional filter by job status |

---

### Error Responses

```json
{
  "status": 404,
  "code": "SOURCE_NOT_FOUND",
  "message": "Source not found: 3fa85f64-...",
  "timestamp": "2026-05-13T09:15:00Z"
}
```

| HTTP Status | Code | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean validation failure |
| 400 | `INVALID_SOURCE_TYPE` | No adapter for the given source type |
| 401 | `UNAUTHORIZED` | Missing or invalid API key |
| 403 | `FORBIDDEN` | Authenticated but insufficient scope |
| 404 | `SOURCE_NOT_FOUND` | Source ID does not exist |
| 409 | `DUPLICATE_TRANSACTION` | Duplicate fingerprint detected |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## Security Model

Authentication is **API key-based**. Keys are issued per source at registration time.

### Key format
```
sk_live_<43-char base64url random>
```

### How it works

1. Client sends `Authorization: Bearer sk_live_...` on every request.
2. `ApiKeyAuthFilter` extracts the first 8 characters as a **lookup prefix**.
3. The full key is hashed with **SHA-256** and compared against the stored hash — the plaintext key is never persisted.
4. On success, a `SourceIdentity` is placed in the `SecurityContextHolder`.
5. `ApiKey.lastUsedAt` is updated on every authenticated request.

### Public endpoints (no auth required)

| Endpoint | Reason |
|---|---|
| `POST /v1/sources/register` | Bootstrap — no key exists yet |
| `GET /actuator/health` | Infrastructure probes |
| `GET /swagger-ui/**` | Developer tooling |
| `GET /v3/api-docs/**` | OpenAPI spec |

---

## Adapter Layer

Each source type has a dedicated adapter that translates a `RawTransactionDto` into a `CanonicalTransaction`.

| Adapter | Source Type | Transaction Class |
|---|---|---|
| `DebitAdapter` | `DEBIT` | Always `PAYMENT` |
| `CreditAdapter` | `CREDIT` | `CHARGE` (default) or `DEBT_PAYMENT` (if `metadata.kind == "DEBT_PAYMENT"`) |
| `LoansAdapter` | `LOANS` | Always `DEBT_PAYMENT` |
| `InvestmentsAdapter` | `INVESTMENTS` | Always `TRADE` |

Every adapter sets all `CanonicalTransaction` fields, generates a fingerprint via `FingerprintGenerator`, sets `ingestedAt`, and preserves the raw `metadata` map.

`AdapterRegistry` is built at startup from all `TransactionAdapter` beans. Requesting an unknown source type throws `InvalidSourceTypeException`.

### Adding a new adapter

1. Create a `@Component` that implements `TransactionAdapter`.
2. Return the correct `SourceType` from `supports()`.
3. `AdapterRegistry` picks it up automatically — no further wiring needed.

---

## Ingestion Flow

```
ingest(request)
  │
  ├── Resolve SourceIdentity from SecurityContextHolder
  ├── Check batchId idempotency ──► if exists, return existing job (no reprocessing)
  ├── Create SyncJob (status=PENDING)
  ├── Return IngestionResponse 202 ◄── caller gets response here
  │
  └── @Async processAsync()
        ├── Update status → PROCESSING
        ├── For each transaction:
        │     ├── adapter.adapt(raw, identity) → CanonicalTransaction
        │     ├── publisherService.publish(canonical)
        │     └── on error: increment totalFailed, log, continue
        └── Final status:
              totalFailed == 0                      → COMPLETED
              totalFailed > 0 && totalProcessed > 0 → PARTIAL_FAILURE
              totalFailed == totalReceived          → FAILED
```

---

## Message Publishing

Published to the `fintrack.transactions` **topic exchange**.

| Transaction Class | Routing Key | Target Queue(s) |
|---|---|---|
| `PAYMENT` | `transaction.PAYMENT` | `fintrack.spending` |
| `CHARGE` | `transaction.CHARGE` | `fintrack.spending`, `fintrack.debt` |
| `DEBT_PAYMENT` | `transaction.DEBT_PAYMENT` | `fintrack.debt` |
| `TRADE` | `transaction.TRADE` | `fintrack.portfolio` |

Failed messages are routed to `fintrack.dead-letter` via the `fintrack.dlx` direct exchange. All queues and exchanges are **durable**.

Each message is a `TransactionIngestedEvent` serialised as JSON using `Jackson2JsonMessageConverter`.

---

## Database Migrations

Managed by **Flyway**, applied automatically on startup.

| Migration | Table | Description |
|---|---|---|
| `V1__create_sources_table.sql` | `sources` | Registered source systems |
| `V2__create_api_keys_table.sql` | `api_keys` | Hashed API keys scoped to a source |
| `V3__create_sync_jobs_table.sql` | `sync_jobs` | Per-batch ingestion job tracking |

`spring.jpa.hibernate.ddl-auto` is set to `validate` — Hibernate validates the schema against entities but never modifies it.

---

## Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=CreditAdapterTest

# Adapter layer only
mvn test -Dtest="com.fintrack.api.adapter.*"
```

Tests use `@WebMvcTest` for controller slices and Mockito for unit tests — no running infrastructure required.

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST controllers |
| `spring-boot-starter-validation` | Bean validation (`@Valid`) |
| `spring-boot-starter-data-jpa` | JPA / Hibernate |
| `spring-boot-starter-amqp` | RabbitMQ |
| `spring-boot-starter-data-redis` | Redis for deduplication |
| `spring-boot-starter-security` | API key filter chain |
| `spring-boot-starter-actuator` | Health and metrics endpoints |
| `fintrack-common:1.0.0-SNAPSHOT` | Shared enums, models, exceptions |
| `postgresql` | JDBC driver (runtime) |
| `flyway-core` | Schema migrations |
| `lombok` | Boilerplate reduction |
| `mapstruct` | Compile-time mapping (available for extension) |
| `jackson-datatype-jsr310` | Java time serialisation |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI |
