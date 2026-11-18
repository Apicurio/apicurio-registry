# ADR-0010: Storage Architecture

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-12.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11079](https://github.com/Apicurio/apicurio-registry/issues/11079),
  [#11080](https://github.com/Apicurio/apicurio-registry/issues/11080).
- **Scope:** How the registry stores its data: the storage variants, how one is selected, the
  decorator chain around it, and what each variant guarantees (consistency, replicas, upgrades,
  read-only behaviour, events). Search indexing, events and caching details have their own records;
  this one covers how they attach to storage.

## Reading this document

- Start with [the decision](#decision).
- For choosing and deploying a variant: [the variants](#variants), [configuration](#configuration),
  and the storage support matrix in [DEVELOPING.md](../DEVELOPING.md).
- For a running system: the [example](../examples/storage-variants/), which runs the same checks on
  every variant, then each variant's own behaviour.
- For code changes: the [source map](#source-map). Every storage-touching change must work on every
  variant (see [CONTRIBUTING.md](../CONTRIBUTING.md)).

## Context

The registry is deployed by teams with different infrastructure and different ideas of where truth
lives: a relational database run by a DBA team; Kafka as the backbone, with no database at all;
schemas reviewed and merged like code; everything declared as Kubernetes objects. Asking each of
them to adopt one storage is a barrier, and maintaining separate products is unaffordable. The REST
API, rules, search, authorization and the console must behave the same regardless.

## Decision

1. **One `RegistryStorage` interface, several implementations,** selected at startup by
   `apicurio.storage.kind` (`sql`, `kafkasql`, `gitops`, `kubernetesops`) in one image. Everything
   above storage is shared.
2. **SQL is the core implementation.** H2, PostgreSQL, MySQL and SQL Server share one implementation
   with per-dialect statements and DDL. The other variants reuse it: KafkaSQL and the polling
   variants keep their state in an SQL database (usually in-memory H2) and differ only in how it is
   fed.
3. **KafkaSQL is a replicated log:** writes are published to a journal topic; every replica applies
   the whole topic, in order, to its own database.
4. **GitOps and KubernetesOps are read-only projections** of an external source: polled, validated
   as a whole into a standby database, and switched atomically; writes through the API are refused.
5. **Cross-cutting behaviour is a chain of decorators** around the selected storage, ordered by
   constants: content cache, read-only, feature-label preservation, limits, configuration cache,
   search indexing.

### Why reuse SQL everywhere

One query layer, one schema and one set of tests serve every variant; KafkaSQL and the polling
variants only add how data arrives. Behaviour that depends on queries (search, authorization
filters, reference resolution) cannot diverge.

### Why read-only projections for GitOps and KubernetesOps

Two writable sources of truth would conflict. When git or Kubernetes is the source of truth, the
registry serves it, validates it (rules apply to the whole data set before it is published), and
refuses writes, so every change goes through the source's own review.

## Variants

### SQL

- `apicurio.storage.sql.kind`: `h2` (default, in memory), `postgresql`, `mysql`, `mssql`;
  connection through `apicurio.datasource.*`.
- The schema is created and upgraded at startup when `apicurio.sql.init=true` (default); otherwise
  startup fails on an old schema. The version is the `db_version` property in table `apicurio`
  (113). Upgrading from a 2.x database is refused.
- Several replicas on one database are supported: the database is the only state, and schema
  initialization takes a database lock. Consistency is the database's.

### KafkaSQL

- Writes are published to `apicurio.kafkasql.topic` (`kafkasql-journal`); a write returns once this
  replica has consumed and applied its own message (`apicurio.kafkasql.coordinator.response-timeout`,
  30 s). Each replica therefore reads its own writes; other replicas are eventually consistent.
- Every start replays the topic (a random consumer group each time), optionally from a snapshot
  (`kafkasql-snapshots`); readiness waits for it.
- The journal must never lose a message: the registry creates it with `cleanup.policy=delete` and
  `retention.ms=-1`, and refuses a compacted topic or a finite retention.
- The local database is `apicurio.storage.sql.kind` (H2 in memory by default).

### GitOps and KubernetesOps

- Data is a set of files with a `$type` (`registry-v0`, `group-v0`, `artifact-v0`, `content-v0`);
  a file is loaded by registries whose `apicurio.polling-storage.id` is in its `registryIds`.
- GitOps reads a local checkout (`apicurio.gitops.workspace`, one or several repositories) and never
  fetches; a sidecar or a volume keeps it current. KubernetesOps lists labelled ConfigMaps in a
  namespace, and watches them.
- Each change is debounced (`debounce.quiet-period`, `max-wait-period`), loaded into the inactive of
  two databases (blue/green), validated with the registry's rules, and published by switching. A
  failed load keeps the last good data and is reported (`GET /admin/gitops/status`).
- IDs are deterministic (SHA-256 of content, and of coordinates), so replicas and restarts agree.
- Both are experimental: they need `apicurio.features.experimental.enabled`.

### Behaviour across storage variants

| | SQL | KafkaSQL | GitOps / KubernetesOps |
|---|---|---|---|
| Writes through the API | Yes | Yes | No: 409 |
| Several replicas | Yes, strongly consistent | Yes, eventually consistent | Yes, each loads the source |
| State on restart | Kept in the database | Rebuilt from the topic | Reloaded from the source |
| IDs | Sequences | Sequences, via the journal | Deterministic |
| Registry events | PostgreSQL and SQL Server (outbox) | Yes | No |
| Read-only mode | `apicurio.storage.read-only.enabled` (dynamic): writes return 409 | Same, propagated through the journal | Always |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.storage.kind` | `sql` | Variant |
| `apicurio.storage.sql.kind` | `h2` | SQL dialect (also KafkaSQL's local database) |
| `apicurio.datasource.url`, `.username`, `.password` | in-memory H2 | Database connection |
| `apicurio.sql.init` | `true` | Create and upgrade the schema at startup |
| `apicurio.kafkasql.bootstrap.servers` | `localhost:9092` | Kafka |
| `apicurio.kafkasql.topic` | `kafkasql-journal` | Journal |
| `apicurio.kafkasql.snapshot.every.seconds` | `86400s` | Snapshot period, with `snapshot.scheduled.enabled` |
| `apicurio.polling-storage.id` | `main` | Which files a GitOps/KubernetesOps registry loads |
| `apicurio.polling-storage.poll-period` | `PT10S` | Polling period |
| `apicurio.polling-storage.debounce.quiet-period` | `PT3S` | Wait for changes to settle |
| `apicurio.gitops.workspace` | `/repos` | GitOps checkouts |
| `apicurio.kubernetesops.namespace` | the client's namespace, or `default` | KubernetesOps namespace |
| `apicurio.storage.read-only.enabled` | `false` | Read-only mode (dynamic) |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Selection, decorators | [`RegistryStorageProducer`](../app/src/main/java/io/apicurio/registry/storage/RegistryStorageProducer.java), [`decorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/) | Variant, decorator chain and order |
| SQL | [`impl/sql`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/) | Implementation, per-dialect statements, DDL and upgrades |
| KafkaSQL | [`impl/kafkasql`](../app/src/main/java/io/apicurio/registry/storage/impl/kafkasql/) | Journal, coordinator, snapshots |
| Polling variants | [`impl/polling`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/), [`impl/gitops`](../app/src/main/java/io/apicurio/registry/storage/impl/gitops/), [`impl/kubernetesops`](../app/src/main/java/io/apicurio/registry/storage/impl/kubernetesops/) | Data files, debounce, blue/green, deterministic IDs, sources |
| Topic administration | [`KafkaAdminUtil`](../app/src/main/java/io/apicurio/registry/storage/impl/util/KafkaAdminUtil.java) | Topic creation and verification |

## Limitations and open work

1. KafkaSQL replicas starting together can fail creating the topics
   ([#11079](https://github.com/Apicurio/apicurio-registry/issues/11079)).
2. KubernetesOps sees a change on every poll on a live cluster, so updates wait for the debounce
   maximum and data is reloaded continuously
   ([#11080](https://github.com/Apicurio/apicurio-registry/issues/11080)).
3. KafkaSQL replicas are eventually consistent: a client may not read its write on another replica.
4. Every KafkaSQL start replays the journal; startup time grows with it unless snapshots are used.
5. GitOps and KubernetesOps are experimental; KubernetesOps has no status endpoint.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| A different store per variant, without SQL underneath | Not selected: queries, search filters and authorization would diverge per variant |
| KafkaSQL with a compacted topic | Rejected: compaction would lose history that the replay needs |
| Writable GitOps (commit on API writes) | Not selected: two sources of truth; changes would bypass review |
| A single storage for everyone | Not selected: a barrier for teams without that infrastructure |

## Consequences

- **Benefits:** one image and one API for every deployment; storage chosen by configuration; one
  query layer and one set of semantics.
- **Costs:** every storage-touching feature must be verified on every variant; the read-only
  variants cannot support write-only features; KafkaSQL trades consistency and startup time for
  having no database.

## Example and verification

- **Example:** [`examples/storage-variants`](../examples/storage-variants/): `./demo.sh` runs the
  same checks (rules, search, read-only mode, restart) on PostgreSQL with two replicas, MySQL, SQL
  Server, and KafkaSQL with two replicas, then GitOps (commits, a broken commit reported and not
  applied, deterministic IDs) and KubernetesOps (ConfigMap updates). CI runs one job per variant.
- **User documentation:** the installation, GitOps and KubernetesOps storage pages
  ([source](../docs/modules/ROOT/pages/getting-started/)).

| Concern | Tests to read |
|---|---|
| Per-dialect SQL | `*PostgresqlTest`, `*MysqlTest`, `*MssqlTest` (app) |
| KafkaSQL | `KafkaSqlRegistryStorageTest`, `KafkaSqlCoordinatorTest`, `KafkaSqlSnapshotTest`, `KafkaAdminUtilTest` (app) |
| GitOps | `GitOpsSmokeTest`, `GitOpsMultiRepoTest`, `GitOpsStatusTest`, `GitOpsValidateTest` (app) |
| KubernetesOps | `KubernetesOpsSmokeTest`, `KubernetesWatchTest` (app) |
| Polling, IDs | `AbstractPollingRegistryStorageTest`, `BlueGreenStorageIsolationTest`, `DeterministicIdGeneratorTest`, `DebouncerTest` (app) |
| Decorators | `ReadOnlyRegistryStorageTest`, `ContentCacheDecoratorTest`, `RegistryStorageProxyFactoryTest` (app) |

## History

| Milestone | Result |
|---|---|
| [#3902](https://github.com/Apicurio/apicurio-registry/pull/3902) (3.0) | Storage variants simplified to SQL, KafkaSQL and GitOps, built on the SQL implementation |
| [#7400](https://github.com/Apicurio/apicurio-registry/pull/7400) (3.2) | KubernetesOps storage |
| This ADR | First written record, with a verified example |
