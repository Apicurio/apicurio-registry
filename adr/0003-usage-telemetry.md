# ADR-0001: Usage Telemetry and API Lifecycle Governance

- **Date:** 2026-05-08
- **Status:** Accepted
- **Issue:** [#7000](https://github.com/Apicurio/apicurio-registry/issues/7000)

## Context

Apicurio Registry stores schemas and API definitions, but has no visibility into whether those schemas are actually being used, by whom, or how stale they are. Operators cannot answer basic governance questions:

- Is anyone still using this schema?
- Which consumers are on which versions?
- Is it safe to deprecate this version?
- Are consumers falling behind on version adoption?

Without this data, schema cleanup is guesswork, deprecation is risky, and version drift goes undetected.

## Decision

Implement server-side usage telemetry that records schema fetch events and exposes governance endpoints.

### Architecture

```
  SerDes Client                           Apicurio Registry
  ┌─────────────┐                        ┌──────────────────────────┐
  │ Kafka/NATS/  │  GET /ids/globalIds/42 │                          │
  │ Pulsar app   │──────────────────────▶ │  UsageTelemetryFilter    │
  │              │  + X-Registry-Client-Id│  (ContainerResponseFilter│
  │              │  + X-Registry-Operation│   records to schema_usage│
  └─────────────┘                        │   table on 200 response) │
                                         │                          │
  Dashboard/API                          │  Query Endpoints         │
  ┌─────────────┐                        │  ┌──────────────────┐    │
  │ GET /admin/  │◀───────────────────── │  │ Direct SQL agg   │    │
  │ usage/...    │  (aggregates directly  │  │ on schema_usage  │    │
  └─────────────┘   from raw events)     │  └──────────────────┘    │
                                         └──────────────────────────┘
```

### How It Works

1. The SerDes SDK adds `X-Registry-Client-Id` and `X-Registry-Operation` headers to existing schema fetch requests (no extra HTTP calls).
2. A JAX-RS `ContainerResponseFilter` (`UsageTelemetryFilter`) intercepts successful responses to `GET /ids/globalIds/{id}` and `GET /ids/contentIds/{id}`, and records a row in the `schema_usage` table (with either `globalId` or `contentId` depending on the fetch path).
3. Query endpoints aggregate directly from the raw events table using SQL GROUP BY. No summary table or materialization step.
4. A scheduled job periodically updates OTel gauge metrics (Active/Stale/Dead counts) and cleans up events older than the retention period.
5. Referenced schemas inherit liveness from the schemas that reference them via a UNION query against the `content_references` table.

### Components

| Component | Purpose |
|-----------|---------|
| `UsageTelemetryFilter` | Records usage events server-side on schema fetch |
| `UsageTelemetryConfig` | Configuration properties (`enabled`, thresholds, retention) |
| `UsageAggregationJob` | Periodic OTel gauge updates and retention cleanup |
| `SqlUsageRepository` | SQL queries for recording and querying usage data |
| `schema_usage` table | Raw usage events (globalId, contentId, clientId, operation, timestamp) |
| `AdminResourceImpl` | REST endpoints for metrics, heatmap, deprecation readiness |
| `RegistryClientFacadeImpl` | Adds telemetry headers to SDK schema fetch requests |
| `DefaultSchemaResolver` | ThreadLocal for propagating SERIALIZE/DESERIALIZE operation |

### API Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /admin/usage/summary` | Global Active/Stale/Dead counts |
| `GET /admin/usage/artifacts/{g}/{a}` | Per-version metrics with client lists |
| `GET /admin/usage/artifacts/{g}/{a}/heatmap` | Consumer version adoption grid with drift alerts |
| `GET /admin/usage/artifacts/{g}/{a}/versions/{v}/deprecation-readiness` | Affected consumers for a version |

### Classification

Schemas are classified based on their most recent fetch timestamp:

- **Active:** fetched within `apicurio.usage.active-threshold-days` (default: 7)
- **Stale:** not fetched within `apicurio.usage.stale-threshold-days` (default: 30)
- **Dead:** not fetched within `apicurio.usage.dead-threshold-days` (default: 90) or never fetched

### Version Lifecycle

A `SUNSET` state was added to the version lifecycle:

```
ENABLED → DEPRECATED → SUNSET → (delete)
    ↕          ↕           ↕
  DISABLED     ↕           ↕
    ←──────────←───────────┘
```

`SUNSET` signals that a migration deadline has passed and the version will be removed. It provides a stronger signal than `DEPRECATED`.

## Alternatives Considered

### Client-side event pipeline (rejected)

The initial implementation had clients POST usage events to a dedicated `POST /admin/usage/events` endpoint via a buffered reporter (`UsageTelemetryReporter`).

**Rejected because:**
- Required a separate HTTP call per flush (unnecessary network overhead)
- Client-side buffering added complexity (flush intervals, buffer sizes, shutdown hooks, dedup window)
- Required 4 client config properties instead of 1
- The client was already making the schema fetch request — telemetry should piggyback on it

### Summary/materialization table (rejected)

A `schema_usage_summary` table was maintained by a scheduled aggregation job that rolled up raw events.

**Rejected because:**
- Data was stale between aggregation runs
- Required an extra DDL table, aggregation SQL, and DB-specific string aggregation overrides (STRING_AGG vs LISTAGG vs GROUP_CONCAT)
- Direct SQL aggregation from the raw table is simpler and always up-to-date
- For typical deployment sizes, the aggregation queries are fast enough

### OpenTelemetry-only approach (rejected)

Export usage data purely as OTel metrics to external dashboards.

**Rejected because:**
- OTel metrics flow to external backends (Prometheus, Jaeger) — cannot be queried internally
- The heatmap, deprecation readiness, and Active/Stale/Dead classification require data stored inside the registry
- OTel is used complementarily for gauge export, not as the primary data store

## Addressed Concerns

The following concerns were identified during design review and addressed in the implementation:

- **Asynchronous writes:** The filter buffers events in-memory and flushes to the database on a background thread every 10 seconds (or when the buffer reaches 1,000 events). Schema fetch requests are never blocked by database writes.
- **Server-side deduplication:** Same client/schema combinations are deduplicated within a 60-second window. A client with a 30-second cache TTL generates at most 1 row per minute per schema instead of 1 per fetch.
- **Data retention:** Events older than `apicurio.usage.retention-days` (default 180 days) are cleaned up by the scheduled aggregation job.
- **Reference liveness:** Referenced schemas inherit Active status from the schemas that reference them via a UNION query against `content_references`.
- **clientId validation:** Restricted to `[a-zA-Z0-9._-]` with a 256-character limit to prevent delimiter collisions and DoS.
- **Cached summary counts:** The `GET /admin/usage/summary` endpoint serves cached counts from the aggregation job, avoiding the expensive UNION query on every API call. Falls back to live query only on the first call before the job has run.

## Known Limitations

### 1. ContentHash and GAV paths not tracked

`GET /ids/globalIds/{id}` and `GET /ids/contentIds/{id}` are tracked. Fetches by contentHash or GAV coordinates are not. ContentHash is rarely used by SerDes clients, and GAV paths are admin/search API calls. Referenced schemas get liveness propagated from their parents via the UNION query.

### 2. SUNSET state backward compatibility

Adding `SUNSET` to the `VersionState` enum may cause issues for existing clients with strict enum validation. Documented in OpenAPI spec with version (3.3.0) and transition requirements. Only appears when explicitly set by an administrator.

### 3. Table growth under extreme throughput

Multiple mitigations in place: server-side dedup (60s window), retention cleanup (default 180 days), async buffered writes. For very high-throughput deployments, reduce `apicurio.usage.retention-days`. Feature can be disabled entirely with zero overhead.

## Scalability Guidance

| Deployment Size | Schemas | Clients | Concerns |
|-----------------|---------|---------|----------|
| Small | < 100 | < 10 | None |
| Medium | 100–1,000 | 10–50 | Works well. Retention keeps table manageable. |
| Large | 1,000+ | 50+ | Reduce retention to 30–90 days. Monitor query performance. |

The feature can be disabled (`apicurio.usage.telemetry.enabled=false`) with zero overhead.

## Consequences

- Operators can now identify dead schemas, stale consumers, and version drift without external tooling
- The `schema_usage` table adds a new storage concern that must be monitored in production
- Client SDKs must be updated to send telemetry headers (backward compatible — headers are optional)
- The `SUNSET` state enriches the version lifecycle but requires SDK updates for full support
