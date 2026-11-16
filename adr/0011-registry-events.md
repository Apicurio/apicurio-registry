# ADR-0011: Registry Events

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-12.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11097](https://github.com/Apicurio/apicurio-registry/issues/11097),
  [#9324](https://github.com/Apicurio/apicurio-registry/issues/9324),
  [#8426](https://github.com/Apicurio/apicurio-registry/issues/8426).
- **Scope:** The events the registry publishes when its data changes: the event model, how they reach
  Kafka on each storage variant, and what is guaranteed. Not covered: usage telemetry
  ([ADR-0003](0003-usage-telemetry.md)) and audit logging.

## Context

Systems around a registry need to react to it: invalidate caches when a schema changes, trigger
pipelines on a new version, alert on deprecations, keep an audit trail. Polling the API does not scale
and misses intermediate states. The registry has several storage variants, and an event that is
published for a change that was rolled back, or lost for a change that was committed, is worse than
none: the guarantees must be explicit for each.

## Decision

1. **One event per change, published to Kafka.** Sixteen types (`StorageEventType`): group, artifact
   and version created, updated and deleted; rules configured; version state changed; contract
   ruleset, metadata and status changes. Each is a flat JSON object with a unique `id`, its
   `eventType` and the change's fields, keyed by the changed coordinates (`group`, `group-artifact`,
   `group-artifact-version`, or the rule type).
2. **Events come from storage, not from REST:** the storage implementation fires them where the change
   is made, so the API used does not matter, and dry runs and rejected changes produce none. It is
   **not a complete change feed**: bulk artifact deletion (`DELETE /groups/{g}/artifacts`) and
   imports produce no events ([#11109](https://github.com/Apicurio/apicurio-registry/issues/11109)).
3. **SQL storage uses the transactional outbox:** each event is inserted into the `outbox` table in the
   same transaction as the change (except contract rulesets, see below), and deleted again; an external CDC connector (Debezium's outbox
   event router) publishes it from the database log. PostgreSQL and SQL Server only.
4. **KafkaSQL publishes directly** to `apicurio.events.kafka.topic` (`registry-events`, one partition),
   from the replica that handled the request, after the change is applied.
5. **GitOps and KubernetesOps produce no events.**
6. **Kafka is the only transport;** there are no HTTP webhooks.

### Why an outbox on SQL storage

Publishing to Kafka from the request cannot be atomic with the database commit: a crash between the
two loses an event or publishes one for a change that never happened. Writing the event in the same
transaction, and letting CDC publish it from the log, makes events exactly as durable as the data,
without a polling publisher in the registry. The cost is running Debezium.

### Why KafkaSQL publishes directly

KafkaSQL already depends on Kafka, and its writes are themselves Kafka messages; a second connector
would add little. The trade-off is that the event is published after the change is applied, not with
it.

## Model

### Guarantees

| | SQL (PostgreSQL, SQL Server) | KafkaSQL | GitOps, KubernetesOps, H2, MySQL |
|---|---|---|---|
| Published | If and only if the change commits; contract-ruleset events are written after the change's transaction, so a failure in between loses them ([#11109](https://github.com/Apicurio/apicurio-registry/issues/11109)) | After the change is applied; lost if publishing fails | Never |
| Delivery | At least once (CDC resumes from the log) | At most once | |
| Order | Commit order | One partition: total order | |
| Topic | `outbox.event.registry-events` (Debezium's default for the outbox router) | `registry-events` | |
| If Kafka is down | Changes succeed; events follow | The storage itself is unavailable | |

Consumers should deduplicate by event `id`.

### Format

The payload is published as is (no envelope, no `ce_` headers). `CloudEventConverter` and
`CloudEventDto` define CloudEvents 1.0 type names (`io.apicurio.registry.<what>.<change>`) but no
code path publishes them yet; a second, unused `CloudEventType` enum disagrees with those names
([#9324](https://github.com/Apicurio/apicurio-registry/issues/9324)). Publishing CloudEvents, and
HTTP delivery, are open ([#8426](https://github.com/Apicurio/apicurio-registry/issues/8426)).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.events.kafka.topic` | `registry-events` | KafkaSQL events topic; on SQL storage, the outbox `aggregatetype` (so the Debezium topic `outbox.event.registry-events`) |
| `apicurio.events.kafka.topic.*` | `cleanup.policy=delete`, `retention.ms=-1` | KafkaSQL events topic properties; one partition is forced |

Events cannot be switched off: on SQL storage, simply do not run the connector.

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Event types | [`StorageEventType`](../app/src/main/java/io/apicurio/registry/storage/StorageEventType.java), [`events`](../app/src/main/java/io/apicurio/registry/events/) | One class per type, with its payload |
| SQL outbox | [`SqlEventsProcessor`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/SqlEventsProcessor.java), `SqlEventRepository`, `outbox` in the DDL | Insert and delete in the change's transaction |
| KafkaSQL | `KafkaSqlEventsProcessor`, `KafkaSqlRegistryStorage` | Direct publishing |
| CloudEvents (unused) | [`CloudEventConverter`](../app/src/main/java/io/apicurio/registry/events/dto/CloudEventConverter.java), `CloudEventDto` | Type names and envelope |

## Limitations and open work

1. On SQL storage, `ARTIFACT_VERSION_CREATED` precedes `ARTIFACT_CREATED` for an artifact created with
   its first version ([#11097](https://github.com/Apicurio/apicurio-registry/issues/11097)).
2. KafkaSQL events are not transactional: a failed publish loses the event.
3. No CloudEvents, no HTTP delivery ([#8426](https://github.com/Apicurio/apicurio-registry/issues/8426), [#9324](https://github.com/Apicurio/apicurio-registry/issues/9324)).
4. MySQL has an outbox table that is never written; H2, MySQL, GitOps and KubernetesOps produce no
   events.
5. Events carry coordinates and metadata, not content; consumers fetch content from the registry.
6. Not every change produces an event: bulk artifact deletion and imports do not, and on SQL storage
   contract-ruleset events are not transactional ([#11109](https://github.com/Apicurio/apicurio-registry/issues/11109)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Publish to Kafka from the request on SQL storage | Not selected: not atomic with the commit |
| A polling outbox publisher inside the registry | Not selected: CDC reads the log without extra load or a scheduler |
| HTTP webhooks | Not yet: open as [#8426](https://github.com/Apicurio/apicurio-registry/issues/8426) |
| Raw CDC of the registry's tables | Not selected: couples consumers to the schema; the outbox is a stable contract |

## Consequences

- **Benefits:** events as durable as the data on SQL storage; a stable, keyed format; independent of the API used.
- **Costs:** SQL storage needs Debezium; KafkaSQL's guarantees are weaker; consumers must deduplicate.

## Example and verification

- **Example:** [`examples/registry-events`](../examples/registry-events/): `./demo.sh` makes a fixed
  sequence of changes (including a dry run and a rejected change) and checks the exact events, keys
  and payloads on PostgreSQL with Debezium (including delivery after a Kafka Connect outage) and on
  KafkaSQL with two replicas. CI runs both.
- **User documentation:**
  [`assembly-registry-events.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-registry-events.adoc).

| Concern | Tests to read |
|---|---|
| Events end to end, SQL with Debezium | `RegistryEventsTest` (app) |
| KafkaSQL | `KafkaSqlEventsTest`, `KafkaSqlContractEventsTest` (app) |
| Outbox repository | `SqlEventRepositoryTest` (app) |
| CloudEvents envelope | `CloudEventConverterTest` (app), `CloudEventDtoTest` (common) |

## History

| Milestone | Result |
|---|---|
| DB version 101 | `outbox` table |
| This ADR | First written record, with a verified example |
