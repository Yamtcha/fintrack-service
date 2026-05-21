# fintrack-api

The ingestion layer for the FinTrack platform. It accepts batches of financial transactions from registered source systems, validates and normalises them, then publishes events asynchronously to RabbitMQ for downstream processing.

Every valid batch returns `202 Accepted` immediately — all heavy lifting happens off the request thread.

---

## How it works

```
Client
  │
  │  X-API-Key: <key>
  │  POST /v1/ingestion/transactions
  ▼
ApiKeyAuthFilter ──► validates key ──► sets SecurityContext
  │
  ▼
IngestionController
  │
  ▼
IngestionService
  ├── idempotency check (SyncJob lookup)
  ├── creates SyncJob (PENDING)
  ├── returns 202 ◄── client gets response here
  │
  └── @Async BatchProcessorService
        ├── adapter.adapt(raw, identity) → canonical Transaction
        ├── PublisherService.publish() → RabbitMQ
        └── updates SyncJob (COMPLETED / PARTIAL_FAILURE / FAILED)
```

---

## Source Registration

Before submitting transactions, a source must be registered. This is the only endpoint that does not require an API key.

```
POST /v1/sources/register

{
  "name": "Chase Checking",
  "sourceType": "DEBIT"
}
```

**Response `201 Created`**
```json
{
  "sourceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sourceType": "DEBIT",
  "status": "ACTIVE",
  "registeredAt": "2026-05-21T09:00:00Z",
  "apiKey": "<hmac-signed-key>"
}
```

Store the returned `apiKey` — it is not stored in plaintext and cannot be retrieved again.

Supported source types: `DEBIT`, `CREDIT`, `LOANS`, `INVESTMENTS`

---

## Authentication

All endpoints except `/v1/sources/register` and `/actuator/health` require the `X-API-Key` header.

```
X-API-Key: <your-api-key>
```

The filter (`ApiKeyAuthFilter`) accepts two key types:

| Key type | How it's validated | Principal set |
|---|---|---|
| Source key (from registration) | HMAC-verified, decodes `sourceId` + `sourceType` | `SourceIdentity` |
| Master key (`API_KEY` env var) | Direct string match | `"api-client"` |

Requests with a missing or invalid key receive `401 Unauthorized`.

---

## Transaction Ingestion

```
POST /v1/ingestion/transactions
X-API-Key: <your-api-key>
Idempotency-Key: <optional>

{
  "batchId": "BATCH-2026-001",
  "transactions": [
    {
      "externalId": "TXN-001",
      "amount": 4999,
      "currency": "ZAR",
      "merchant": "WOOLWORTHS",
      "description": "WOOLWORTHS SANDTON",
      "transactedAt": "2026-05-21T10:30:00Z",
      "metadata": {}
    }
  ]
}
```

- `amount` is in **minor units** (cents)
- Maximum batch size: **1000 transactions**
- Re-submitting the same `batchId` from the same source returns the existing job without reprocessing
- The optional `Idempotency-Key` header provides an additional deduplication layer for retried HTTP calls

**Response `202 Accepted`**
```json
{
  "batchId": "BATCH-2026-001",
  "jobId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "PENDING",
  "totalReceived": 1,
  "message": "Batch accepted for processing"
}
```

---

## Polling Batch Status

```
GET /v1/ingestion/sync/status/{batchId}
X-API-Key: <your-api-key>
```

Status lifecycle: `PENDING` → `PROCESSING` → `COMPLETED` | `PARTIAL_FAILURE` | `FAILED`

---

## Adapter Layer

Each source type has a dedicated adapter that normalises raw transactions into the canonical `Transaction` model:

| Source type | Adapter | Transaction class |
|---|---|---|
| `DEBIT` | `DebitAdapter` | Always `PAYMENT` |
| `CREDIT` | `CreditAdapter` | `CHARGE` by default; `DEBT_PAYMENT` if `metadata.kind == "DEBT_PAYMENT"` |
| `LOANS` | `LoansAdapter` | Always `DEBT_PAYMENT` |
| `INVESTMENTS` | `InvestmentsAdapter` | Always `TRADE` |

Enabled adapters are controlled in `application.yml`:

```yaml
fintrack:
  ingestion:
    adapters:
      enabled:
        - CREDIT
        - DEBIT
        - LOANS
        - INVESTMENTS
```

---

## Message Publishing

Normalised transactions are published to the `fintrack.transactions` topic exchange as `TransactionIngestedEvent` (JSON). The routing key is `transaction.TRANSACTION`.

Failed publishes are retried up to **3 times** with exponential backoff (200ms → 400ms → 800ms). If all attempts fail the transaction is counted as failed and the job status reflects it.

---

## Error Responses

```json
{
  "code": "SOURCE_NOT_FOUND",
  "message": "Source not found: 3fa85f64-...",
  "requestId": "a1b2c3d4-...",
  "timestamp": "2026-05-21T09:15:00Z"
}
```

| Status | Code | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean validation failure |
| 400 | `INVALID_SOURCE_TYPE` | No adapter registered for the source type |
| 401 | `UNAUTHORIZED` | Missing or invalid `X-API-Key` |
| 404 | `SOURCE_NOT_FOUND` | Source ID does not exist |
| 409 | `DUPLICATE_TRANSACTION` | Duplicate transaction fingerprint detected |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## Environment Variables

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL — e.g. `jdbc:postgresql://localhost:5432/fintrack` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `API_KEY` | Shared secret used to sign source keys and as the master key |
| `RABBITMQ_HOST` | RabbitMQ hostname |
| `RABBITMQ_USERNAME` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | RabbitMQ password |
| `GITHUB_USERNAME` | GitHub username — used by Maven to pull `fintrack-commons` from GitHub Packages |
| `GITHUB_TOKEN` | GitHub Personal Access Token with `read:packages` scope |

RabbitMQ connects on port `5671` with SSL enabled.

---

## Database

Migrations are managed by **Flyway**, applied automatically on startup. `ddl-auto` is set to `validate` — Hibernate never modifies the schema.

| Migration | Description |
|---|---|
| `V1` | `sources` table |
| `V3` | `sync_jobs` table |
| `V5` | Adds `idempotency_key` column to `sync_jobs` |

---

## Running with Docker

```bash
docker build -t fintrack-api .

docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/fintrack \
  -e DB_USERNAME=fintrack \
  -e DB_PASSWORD=secret \
  -e API_KEY=your-secret \
  -e RABBITMQ_HOST=host.docker.internal \
  -e RABBITMQ_USERNAME=guest \
  -e RABBITMQ_PASSWORD=guest \
  fintrack-api
```

---

## Running Tests

```bash
mvn test
```

Tests use `@WebMvcTest` for controller slices and plain Mockito for unit tests — no running infrastructure required.

---

## Observability

Actuator endpoints: `health`, `info`, `metrics`, `prometheus`

Key metrics:

| Metric | Tags |
|---|---|
| `fintrack.ingestion.batches` | `source_type`, `outcome` |
| `fintrack.ingestion.transactions` | `source_type`, `result` |
| `fintrack.ingestion.batch.duration` | `source_type`, `status` |
| `fintrack.publish.transactions` | `result` |

Swagger UI: `/swagger-ui.html`
