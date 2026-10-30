# ADR-0003: Usage Telemetry and API Lifecycle Governance

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
5. Referenced schemas inherit liveness from the schemas that directly reference them: the summary statement joins `content_references` to the referenced versions, combines that result with the directly fetched versions using `UNION ALL`, and keeps the latest timestamp per version.

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
- **Stale:** last fetched more than the active threshold ago, but within `apicurio.usage.stale-threshold-days` (default: 30)
- **Dead:** last fetched more than the stale threshold ago

Only versions with at least one recorded event, or referenced by a version that has one, are classified. A version with no event and no fetched referrer, for example one that was never fetched or whose events were all removed by retention, has no classification and is not counted in the summary. A never-fetched version that a fetched version references is counted in the summary (references inherit liveness there); the per-artifact endpoints never list a version without an event of its own. `apicurio.usage.dead-threshold-days` (default: 90) is deprecated since 3.4.0, scheduled for removal in 4.0.0, and has no effect: it is accepted, and `DeprecatedPropertiesCheck` warns when it is set. See the GA checklist.

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

- **Asynchronous writes:** The filter buffers events in-memory and flushes to the database on a background thread every 10 seconds (a flush writes at most 2,000 events; the rest wait for the next flush). Schema fetch requests are never blocked by database writes.
- **Server-side deduplication:** Fetches of the same schema identifier by the same client are deduplicated within a 60-second window, so a client with a 30-second cache TTL generates at most 1 row per minute per identifier instead of 1 per fetch. The key is the `globalId` or the `contentId` of the request plus the client, so the same schema fetched both ways yields two events. The map is per instance, and `addEvent` drops every event while it holds 10,000 entries; see gap 19 for why it may not empty again.
- **Data retention:** Events older than `apicurio.usage.retention-days` (default 180 days) are cleaned up by the scheduled aggregation job.
- **Reference liveness:** Referenced schemas inherit Active status from the schemas that reference them. The summary statement joins `content_references`, merges the result with the direct fetches with `UNION ALL` and keeps the latest timestamp per version.
- **clientId validation:** Restricted to `[a-zA-Z0-9._-]` with a 256-character limit to prevent delimiter collisions and DoS.
- **Cached summary counts:** The `GET /admin/usage/summary` endpoint serves cached counts from the aggregation job, avoiding the expensive summary statement on every API call. Falls back to live query only on the first call before the job has run.

## Known Limitations

### 1. ContentHash and GAV paths not tracked

`GET /ids/globalIds/{id}` and `GET /ids/contentIds/{id}` are tracked. Fetches by contentHash or GAV coordinates are not. ContentHash is rarely used by SerDes clients, and GAV paths are admin/search API calls. Referenced schemas get liveness propagated from their parents by the summary statement.

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

## GA Checklist

Usage telemetry stays experimental: `apicurio.usage.telemetry.enabled` keeps `@Info(experimental = true)` and needs `apicurio.features.experimental.enabled=true`. This checklist records every gap found while preparing the promotion to general availability and whether it is closed. "Verified" says how: a test that ran, a statement run on a real database, or reading the code only.

General availability is deferred, and the reason is gap 14. On KafkaSQL every usage event is one journal record of about 446.5 bytes, retention deletes SQL rows but never shrinks the journal, scheduled snapshots are off by default, and a snapshot restore could not be demonstrated in the measurement below. A KafkaSQL deployment therefore has no bound on journal growth, and every restart replays every usage record ever written. Promotion requires two things: a snapshot restore that works and has a test, and a strategy that bounds the journal growth caused by usage events. Every other gap below is closed or documented.

### Storage parity

| Storage | Recording | Metrics, summary, heatmap, readiness | Retention | Verified |
|---------|-----------|--------------------------------------|-----------|----------|
| H2 | Yes | Yes | Yes | `DefaultUsageStorageTest`, `UsageAggregationJobTest` ran |
| KafkaSQL | Yes | Yes | Yes | `KafkaSqlUsageStorageTest` ran against a Kafka container |
| PostgreSQL | Yes | Yes | Yes | `PostgresqlUsageStorageTest` ran on embedded PostgreSQL (x64 binaries under Rosetta); the statements also ran on PostgreSQL 16 |
| MySQL | Yes | Yes | Yes | `MysqlUsageStorageTest` ran against a MySQL 8 container; the statements also ran on MySQL 8.4 with `ONLY_FULL_GROUP_BY` |
| SQL Server | Yes | Yes | Yes | `MssqlUsageStorageTest` ran in CI on Linux against a SQL Server container (4 tests passed in the `app-sql` shard of Verify run 40010792021 on c7c88d95e); it is disabled on macOS. The statements use no database-specific function after this change |
| GitOps, KubernetesOps | Dropped on purpose | Empty results | No-op | Unit test; read-only storages record nothing |

### Gaps

| # | Gap | Status |
|---|-----|--------|
| 1 | The buffer flush loses one event per cycle when more than 2000 events are queued (#10611) | Open. Fixed by pull request #10632. A failing write still discards the rest of its batch, and the queue has no size bound |
| 2 | KafkaSQL could not deserialize `RecordUsageEvent1Message` and `DeleteOldUsageEvents1Message`, so it recorded nothing and never ran retention (#9532) | Closed. Both messages are registered, `KafkaSqlMessageIndexTest` fails when any message class is missing, and `KafkaSqlUsageStorageTest` timed out without the fix and passes with it |
| 3 | Read-only storages threw `UnreachableCodeException` on every flush (#9580). The retention run never reached it, because `UsageAggregationJob` skips `deleteOldUsageEvents` when `isReadOnly()` is true | Closed in the storage classes. The decorator is unchanged on purpose: failing the write would turn every background flush into an error, and `ReadOnlyRegistryStorageTest` expects usage writes to pass through |
| 4 | The per-artifact metrics statement selected a column that is not in its `GROUP BY` and fails on PostgreSQL and on MySQL with `ONLY_FULL_GROUP_BY`; H2 accepted it | Closed. One row per version and client, folded in `SqlUsageRepository` |
| 5 | The same statement used `STRING_AGG(DISTINCT ...)`, which SQL Server does not support, and MySQL cut the client list at 1024 bytes | Closed by the same change. `MssqlUsageStorageTest` passed in CI on c7c88d95e |
| 6 | The summary counted a version twice when it was fetched directly and also referenced by a fresher schema | Closed. One row per version, the latest timestamp wins |
| 7 | `apicurio.usage.dead-threshold-days` is never read. Dead means older than the stale threshold, not 90 days. A version with no event and no fetched referrer is not counted in the summary | Property closed by deprecation (maintainer decision): registered in `DeprecatedPropertiesCheck` since 3.4.0 with removal in 4.0.0, no default changed, the unused getters removed. The summary exclusion stays as documented |
| 8 | MySQL compares client identifiers case-insensitively, and SQL Server probably does as well because its column has no explicit collation (not checked); PostgreSQL does not | Open, untracked but recorded here, documented. A fix needs a column collation change and a schema upgrade |
| 9 | The heatmap lists versions in the order of its result rows, which are sorted by client, so the list is not in version order | Open. The code is being changed by pull request #10671 |
| 10 | `apicurio.usage.flush.every` had no declaration and showed as type `unknown` in the generated configuration reference | Closed. Declared with the existing default of 10 seconds |
| 11 | Nothing proved that the application starts with telemetry on and the experimental gate off | Not applicable while the feature is experimental: startup with telemetry on and the gate off fails with the standard experimental-features error, and the test profiles set the gate. Re-open for GA |
| 12 | No test denied a non-admin caller access to the usage endpoints | Closed. `UsageAdminAuthTest` checks four endpoints for an admin, a developer and a read-only user |
| 13 | No test counted rows before and after retention or the aggregation job | Closed. `UsageAggregationJobTest` and `AbstractUsageStorageTest` |
| 14 | On KafkaSQL every usage event is a journal record, and the journal topic keeps records forever by default | Open and blocking GA. Measured, see the section below. The feature stays experimental until the journal growth is bounded and a snapshot restore is shown to work |
| 15 | `deleteAllUserData` leaves `schema_usage` rows of deleted versions until retention removes them | Open, untracked but recorded here, low impact. The rows match no version, so no endpoint shows them |
| 16 | The OpenAPI document declares `404` for the artifact metrics endpoint but the implementation returns an empty list for an unknown artifact | Open, untracked but recorded here, cosmetic |
| 17 | A fetch by `contentId` counts for every version that has that content, in any artifact or group, because the event stores only the content id. Identical content registered in several artifacts shares its fetches, which can raise Active counts and make deprecation readiness more conservative | Open, untracked but recorded here, documented |
| 18 | Reference liveness follows one level only: a version inherits the fetches of the schemas that directly reference it, not of schemas that reference those in turn | Open, untracked but recorded here, documented |
| 19 | `UsageTelemetryBuffer.flush()` returns before it prunes the dedup map when no event is buffered, and a full map drops every event, so by reading the code an instance whose map reaches 10,000 keys stops recording until it restarts (not reproduced by a test) | Open, untracked but recorded here. Belongs with #10611 (pull request #10632), which changes the same method |
| 20 | During a rolling upgrade on KafkaSQL, nodes that run a release without the two usage messages in `KafkaSqlMessageIndex` log an error for every usage record they read | Open, documented, no fix planned: it lasts for the rollout window |

### Gap 14: KafkaSQL journal growth, measured

Method. A throwaway test class (not in the repository) ran in the real `KafkasqlTestProfile`: one Testcontainers Kafka broker (image `quay.io/strimzi-test-container/test-container:0.117.0-kafka-4.3.1`, journal topic auto-created with the broker default partition count, not checked), the application's own `KafkaSqlRegistryStorage`, and 50 artifacts. It called `recordUsageEvent` 110,000 times with 100 random 36-character client ids, alternating `SERIALIZE` events by `globalId` and `DESERIALIZE` events by `contentId`, with the real ids (1 to 50), waited until the SQL rows matched, and read the topic size with `Admin.describeLogDirs`. Replay was timed by starting a fresh copy of the packaged application (`quarkus-run.jar`, `-Xmx2g`, a new consumer group) against the same broker and reading its own "bootstrapped in N ms" log line. Machine: Apple M3 Pro, 12 cores, 36 GB, Darwin 25.6.0, Java 21.0.10 (Temurin), podman machine with 4 CPUs and 8 GiB (applehv), broker inside it. Four runs: two with 10,000, 50,000 and 100,000 events (plus 10,000 after a snapshot) and two with 10,000 events, all giving 446.5 to 446.7 bytes per record. Events were sent in bursts, so these numbers say nothing about how producer batching at production rates changes the framing share.

Measured.

| Quantity | Value |
|----------|-------|
| Journal bytes per usage event | 446.5 bytes of log (10,000 events: 4,465,158 bytes; 40,000: 17,860,632; 50,000: 22,325,729) |
| Record payload | average 112 bytes key, 253.8 bytes value (JSON), 65 bytes headers `req` and `mt`; 430.8 bytes in total, read back with a consumer |
| Framing | 15.7 bytes per record, the difference between log size and payload, so record and batch headers together |
| Event content inside the 253-byte value | 133 bytes; the other 119 bytes are a copy of the message key that the serializer writes into the value (measured on one serialized sample) |
| Journal before any event | 51 records, 30,965 bytes |
| Fixed cost of a cold start without events | About 13.7 s from the start of the KafkaSQL bootstrap on this machine (about 10.4 s before the consumer subscribes, plus 3.2 to 3.8 s of replay phase for the 51 records), and about 20 s from process launch (the clock starts just before the packaged application is launched and stops at the "bootstrapped" log line). The logged "bootstrapped in" time already contains the replay phase |
| Replay of usage events, no snapshot | 0.17 to 0.18 s per 10,000 records (two least-squares fits: 0.18 s over 22 timings, 0.173 s over 40 timings, R squared 0.96 in both): the replay phase (first "Subscribing to" line to the "bootstrapped" line) took 5.2 to 5.3 s after 100,000 events and 3.2 to 3.8 s with none |
| Retention | 110,000 SQL rows deleted by `deleteOldUsageEvents`; journal end offset 110,059 before and 110,060 after (one delete record, +416 bytes); begin offset 0 before and after; log size 49,149,819 before and 49,150,235 after |

Consequences. Retention removes SQL rows and never shrinks the journal. A restart replays every usage record ever written, including those already deleted by retention (a replay after retention reached 0 rows in the same 5 s). The journal topic has `retention.ms=-1` and `retention.bytes=-1` (`KafkaSqlConfiguration`), so only a snapshot, or a manual cleanup after a backup, bounds it. Every node runs the retention job (`UsageAggregationJob` skips only read-only storages), so a cluster of N nodes adds N delete records per run and every node applies all of them. Journals written by releases that had telemetry on before the two usage messages were registered already hold usage records that were skipped at the time. After this change the first start replays and applies all of them, including events older than the retention period, so the first start after the upgrade is slower and `schema_usage` is filled with old rows until the next retention run deletes them.

Projections, not measurements. They multiply 446.5 bytes and 0.018 ms per record by one event per client and schema pair per minute, 1,440 a day, for 180 days, the retention default. They assume one instance, constant traffic and the measured per-record cost holding at scale. The dedup map of one instance holds at most 10,000 keys (`UsageTelemetryBuffer.MAX_DEDUP_MAP_SIZE`) and drops events while it is full, so a single instance cannot reach the ceiling above 10,000 pairs; the second row of each profile applies that limit. Replicas multiply the figure by their number.

| Profile (clients x schemas) | Pairs | Events a day | Journal a day | Journal in 180 days | Cold start without a snapshot after 180 days |
|-----------------------------|------:|-------------:|--------------:|--------------------:|---------------------------------------------:|
| 10 x 50 | 500 | 720,000 | 0.32 GB | 58 GB | about 0.7 h |
| 100 x 500 | 50,000 | 72,000,000 at the ceiling; 14,400,000 with the 10,000-key cap | 32 GB; 6.4 GB | 5.8 TB; 1.2 TB | about 66 h; 13 h |
| 1,000 x 2,000 | 2,000,000 | 2.88 billion at the ceiling; 14,400,000 with the cap | 1,286 GB; 6.4 GB | 231 TB; 1.2 TB | about 2,650 h; 13 h |

Snapshot effect: could not be exercised. A snapshot was created and published (`triggerSnapshotCreation`, 0.1 to 0.3 s for 10,000 to 100,000 events, file 74 KB to 627 KB gzip; one extra journal record of 532 bytes; the snapshots topic held the record and the file existed). Replays of that journal with the snapshot never logged "Found N total snapshots" or "Restoring snapshot" and consumed all 10,055 records, as a replay without snapshots does, so the saving cannot be stated. A plain consumer that copies the wait loop of `consumeSnapshotsTopic` (`KafkaSqlRegistryStorage.consumeSnapshotsTopic`) returned the snapshot record from the polls that wait for partition assignment in three of four tries, and that loop does not collect what those polls return. Whether this explains the missing restore is a hypothesis, not a confirmed defect, and no application code was changed. The default snapshot interval is 86,400 s, and scheduled snapshots are off unless `apicurio.kafkasql.snapshot.scheduled.enabled` is true (`KafkaSqlSnapshotScheduler`).
