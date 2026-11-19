# ADR-0026: Iceberg and Flink catalogs

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** epic [#6993](https://github.com/Apicurio/apicurio-registry/issues/6993); open work in
  [#7261](https://github.com/Apicurio/apicurio-registry/issues/7261).
- **Scope:** The registry as an Apache Iceberg REST catalog (server side, in `app`) and the Apache
  Flink catalog that reads registry schemas (client side, in `utils/flink`). Not covered: how
  authorization decisions are made ([ADR-0005](0005-per-resource-authorization.md)), the metrics
  pipeline ([ADR-0021](0021-observability.md)), the storage variants themselves
  ([ADR-0010](0010-storage-architecture.md)), and the general data model
  ([ADR-0024](0024-data-model.md)).

## Reading this document

- Start with [the decision](#decision).
- For deployment: [configuration](#configuration) and the user documentation for
  [Iceberg](../docs/modules/ROOT/pages/getting-started/assembly-using-iceberg-catalog.adoc) and
  [Flink](../docs/modules/ROOT/pages/getting-started/assembly-using-flink-catalog.adoc).
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

Lakehouse engines (Spark, Trino, Flink, DuckDB) need a catalog that tracks table metadata, and
streaming jobs need table schemas that match the schemas their producers register. The registry
already stores versioned, access-controlled content in groups and artifacts on every storage
variant, so it can hold Iceberg table and view metadata without a second metadata store. Iceberg
defines a standard REST catalog protocol with optimistic commits; Flink defines a pluggable
`Catalog` SPI. Any solution must keep core behaviour unchanged when the feature is off, respect
per-resource authorization, and work on the storage variants the registry supports.

## Decision

1. **The registry implements the Iceberg REST catalog API under `/apis/iceberg/v1`.** The JAX-RS
   interfaces are generated from a vendored OpenAPI document
   ([`iceberg/v1/openapi.json`](../app/src/main/resources-unfiltered/META-INF/resources/api-specifications/iceberg/v1/openapi.json));
   `IcebergApiResourceImpl` implements them on top of `RegistryStorage`.
2. **It is opt-in and experimental.** `apicurio.iceberg.enabled` defaults to `false` and is marked
   `experimental = true`; every endpoint calls `requireIcebergEnabled()` and returns 404 when off.
   Enabling it without `apicurio.features.experimental.enabled=true` stops the application at
   startup (`ExperimentalFeaturesConfig`).
3. **Iceberg objects are ordinary registry objects.** Namespaces are groups, tables and views are
   artifacts of type `ICEBERG_TABLE` and `ICEBERG_VIEW`, and each commit is a new artifact version
   whose content is the full metadata JSON (see [mapping](#mapping)).
4. **Commits are optimistic.** A commit records the version order of the latest version, validates
   the client's requirements against that metadata, applies the updates in memory and writes with
   `createArtifactVersionIfLatest(..., expectedBaseVersionOrder, artifactMetaData)`. The new version
   and any label update are one storage write; a concurrent commit fails with
   `CommitFailedException`, returned as 409 `CommitFailedException`, and the client retries.
5. **Authorization reuses the per-resource model.** Path-addressed endpoints use the
   `IcebergNamespace` and `IcebergTable` styles of `@Authorized`; endpoints whose target is in the
   body (create namespace, create table or view, rename) check it with `ResourceAccessGuard`.
6. **Errors use Iceberg's error shape on the Iceberg surface.** `ApiSurfaceResolver` routes
   `/apis/iceberg` requests to `IcebergExceptionMapperService`, which returns
   `{"error": {code, type, message}}` with Iceberg exception names.
7. **The Flink catalog is a read-only client library.** `utils/flink`
   (`apicurio-registry-utils-flink`) registers `ApicurioCatalogFactory` (identifier `apicurio`) via
   the Flink `Factory` SPI. It uses the Java SDK, maps groups to databases and artifacts to tables,
   converts Avro and JSON Schema content to Flink types, and rejects every DDL operation.

## Mapping

| Iceberg | Registry | Notes |
|---|---|---|
| Catalog prefix (`{prefix}`) | None | Advertised by `GET /config` (`apicurio.iceberg.default-prefix`); not used to select data or in authorization |
| Namespace `["sales","eu"]` | Group `sales.eu` | Levels joined with `.` (`IcebergNamespaces`); in the URL, levels are separated by U+0000. Listing splits group IDs on `.` |
| Namespace properties | Group labels | `comment` also sets the group description |
| Table / view | Artifact `ICEBERG_TABLE` / `ICEBERG_VIEW` | Artifact ID is the table or view name |
| Table / view metadata | Content of the latest version | Full `TableMetadata` / `ViewMetadata` JSON |
| Commit (`POST .../tables/{table}`), replace view | New artifact version | Version history is the commit history |
| `table-uuid`, `view-uuid`, `location`, create-time properties | Artifact labels | `uuid` and `location` labels are refreshed on commit; later property changes live only in the metadata |

Default locations are `<warehouse>/<group with . → />/<name>`, with `/warehouse` when
`apicurio.iceberg.warehouse` is unset. `metadata-location` in responses is
`<location>/metadata/v1.metadata.json`; the registry stores metadata as content, not as files, and
does not manage data files.

### Endpoints

| Path under `/apis/iceberg/v1` | Methods | Level |
|---|---|---|
| `/config` | GET | Read |
| `/{prefix}/namespaces` | GET, POST | Read (results filtered by `ISearchAuthorizer`); Write on the new group |
| `/{prefix}/namespaces/{ns}` | GET, HEAD, DELETE | Read; DELETE needs Admin and an empty group |
| `/{prefix}/namespaces/{ns}/properties` | POST | Write |
| `/{prefix}/namespaces/{ns}/tables`, `.../views` | GET, POST | Read; Write on the namespace and the new artifact |
| `/{prefix}/namespaces/{ns}/tables/{t}`, `.../views/{v}` | GET, HEAD, POST, DELETE | Read; POST (commit / replace) Write; DELETE Admin |
| `/{prefix}/tables/rename` | POST | Write on the source and destination artifacts, and per-resource Write on the destination group |
| `/{prefix}/views/rename` | POST | Write on the source and destination artifacts |

### Commits

Table requirements supported by `TableRequirementValidator`: `assert-create`, `assert-table-uuid`,
`assert-ref-snapshot-id`, `assert-last-assigned-field-id`, `assert-current-schema-id`,
`assert-last-assigned-partition-id`, `assert-default-spec-id`, `assert-default-sort-order-id`. Table
updates in `TableUpdateApplicator`: `assign-uuid`, `upgrade-format-version`, `add-schema`,
`set-current-schema`, `add-spec`, `set-default-spec`, `add-sort-order`, `set-default-sort-order`,
`add-snapshot`, `set-snapshot-ref`, `remove-snapshots`, `remove-snapshot-ref`, `set-location`,
`set-properties`, `remove-properties`. Views support `assert-create` and `assert-view-uuid`
(`ViewRequirementValidator`, `ViewUpdateApplicator`). An unknown requirement or action is a 400; a
failed requirement or a lost race is a 409.

| Failure | Mapped to |
|---|---|
| Group / artifact / version not found | 404 `NoSuchNamespaceException` / `NoSuchTableException` |
| Group or artifact exists | 409 `AlreadyExistsException` |
| Non-empty namespace on drop | 409 `NamespaceNotEmptyException` |
| `CommitFailedException`, `VersionAlreadyExistsException` | 409 `CommitFailedException`; increments `iceberg.commit.conflicts` |
| `ForbiddenException` | 403 with a generic message |
| `IllegalArgumentException` | 400 `BadRequestException` |

### Behaviour across storage variants

| Variant | Behaviour |
|---|---|
| SQL | Supported. The version-order check and label update run in one transaction (`AbstractSqlRegistryStorage`). Integration tests run on H2 |
| KafkaSQL | Supported. The conditional write is a journal message (`CreateArtifactVersionIfLatest11Message`); a replayed losing commit fails again and is discarded |
| GitOps, KubernetesOps | Reads only: the read-only storage rejects writes (`AbstractReadOnlyRegistryStorage`) |

## Flink catalog

| Flink | Registry | Behaviour |
|---|---|---|
| Database | Group | `listDatabases`, `getDatabase`, `databaseExists`; create, drop, alter throw |
| Table | Artifact | `getTable` reads `branch=latest` content; columns from `AvroFlinkTypeConverter` or `JsonSchemaFlinkTypeConverter`; other types throw `Unsupported type` |
| Views, functions, partitions, statistics | None | Empty lists or `CatalogException` |

`getTable` results are cached per `database/table` in a bounded LRU map (`cache.max.size`, default
1000) for `cache.ttl.ms` (default 300000). Table options carry `connector=apicurio-registry`,
`registry.url`, `group.id` and `artifact.id`. The `state` package (`StateSchemaService`,
`SavepointValidator`, `SchemaCompatibilityChecker`) registers Flink operator state schemas in a
`<group>-state` group and checks savepoint compatibility; it is a library API, not part of the
catalog SPI.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.iceberg.enabled` | `false` | Enables the Iceberg REST catalog (experimental) |
| `apicurio.features.experimental.enabled` | `false` | Required to enable the above |
| `apicurio.iceberg.warehouse` | unset | Default warehouse, advertised in `/config`; `/warehouse` is used for locations when unset |
| `apicurio.iceberg.default-prefix` | `default` | Prefix advertised in `/config` |
| `apicurio.metrics.iceberg.enabled` | `true` | Iceberg metrics |

Flink options (`ApicurioCatalogOptions`): `registry.url` (required), `registry.auth.type`
(`none`, `basic`, `oauth2`), `registry.auth.username`, `registry.auth.password`,
`registry.auth.token-endpoint`, `registry.auth.client-id`, `registry.auth.client-secret`,
`cache.ttl.ms`, `cache.max.size`, `default-database` (`default`).

## Metrics

`IcebergMetricsService` records via Micrometer and OpenTelemetry
([ADR-0021](0021-observability.md)): `iceberg.namespace.operations`, `iceberg.table.operations`,
`iceberg.view.operations` (`operation`, `result`), `iceberg.commit.duration` timer and
`iceberg.commit.conflicts` (`entity_type`), and `iceberg.errors` (`error_type`). Conflicts and errors
are counted in the exception mapper, so they cover every Iceberg endpoint.

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| API contract | [`iceberg/v1/openapi.json`](../app/src/main/resources-unfiltered/META-INF/resources/api-specifications/iceberg/v1/openapi.json) | Generated `io.apicurio.registry.iceberg.rest.v1` interfaces (`app/pom.xml`) |
| Endpoints | [`IcebergApiResourceImpl`](../app/src/main/java/io/apicurio/registry/iceberg/rest/v1/impl/IcebergApiResourceImpl.java) | Gate, mapping, authorization, commits |
| Config | [`IcebergConfig`](../app/src/main/java/io/apicurio/registry/iceberg/rest/v1/impl/IcebergConfig.java), [`ExperimentalFeaturesConfig`](../app/src/main/java/io/apicurio/registry/config/ExperimentalFeaturesConfig.java) | Toggle and experimental gate |
| Namespaces | [`IcebergNamespaces`](../app/src/main/java/io/apicurio/registry/iceberg/rest/v1/impl/IcebergNamespaces.java) | Namespace ↔ group, shared with access controllers |
| Commits | [`commit/`](../app/src/main/java/io/apicurio/registry/iceberg/rest/v1/impl/commit/) | Requirement validation and update application |
| Storage | [`RegistryStorage.createArtifactVersionIfLatest`](../app/src/main/java/io/apicurio/registry/storage/RegistryStorage.java) | Conditional, atomic version write |
| Auth | [`AuthorizedStyle`](../app/src/main/java/io/apicurio/registry/auth/AuthorizedStyle.java), [`AbstractAccessController`](../app/src/main/java/io/apicurio/registry/auth/AbstractAccessController.java), [`ResourceAccessGuard`](../app/src/main/java/io/apicurio/registry/auth/ResourceAccessGuard.java) | Resource resolution for Iceberg paths and bodies |
| Errors | [`IcebergExceptionMapperService`](../app/src/main/java/io/apicurio/registry/services/http/IcebergExceptionMapperService.java), [`ApiSurfaceResolver`](../app/src/main/java/io/apicurio/registry/rest/ApiSurfaceResolver.java) | Iceberg error shape |
| Metrics | [`IcebergMetricsService`](../app/src/main/java/io/apicurio/registry/iceberg/metrics/IcebergMetricsService.java) | Counters and timers |
| Artifact types | [`schema-util/iceberg`](../schema-util/iceberg/src/main/java/io/apicurio/registry/iceberg/content/) | Accepters, validator, compatibility, search extraction |
| Flink | [`ApicurioCatalog`](../utils/flink/src/main/java/io/apicurio/registry/flink/ApicurioCatalog.java), [`ApicurioCatalogFactory`](../utils/flink/src/main/java/io/apicurio/registry/flink/ApicurioCatalogFactory.java), [`ApicurioCatalogOptions`](../utils/flink/src/main/java/io/apicurio/registry/flink/ApicurioCatalogOptions.java) | Flink catalog SPI |
| Flink state | [`state/`](../utils/flink/src/main/java/io/apicurio/registry/flink/state/) | State schema registration and savepoint checks |

## Limitations and open work

1. Only single-table commits; no multi-table transactions
   ([#7670](https://github.com/Apicurio/apicurio-registry/issues/7670)).
2. No credential vending, scan planning, or catalog-level OAuth2 endpoint
   ([#7669](https://github.com/Apicurio/apicurio-registry/issues/7669),
   [#7668](https://github.com/Apicurio/apicurio-registry/issues/7668),
   [#7667](https://github.com/Apicurio/apicurio-registry/issues/7667)); the registry's own
   authentication applies.
3. Rename is create-then-delete, not atomic; a failed delete leaves the source for manual cleanup. View rename does not check destination-group grants the way table rename does.
4. Listing uses offset page tokens; keyset pagination is tracked in
   [#11105](https://github.com/Apicurio/apicurio-registry/issues/11105).
5. The prefix is not a catalog boundary, and group IDs containing `.` read back as multi-level
   namespaces.
6. Exceptions without a specific mapping become 500
   ([#10147](https://github.com/Apicurio/apicurio-registry/issues/10147)).
7. Engine compatibility is tested only with the Iceberg Java client
   ([#7262](https://github.com/Apicurio/apicurio-registry/issues/7262)).
8. Table and view endpoints do not check the artifact type, properties set by a commit are not
   reflected in the artifact labels, rename does not carry labels or history over, `purgeRequested`
   is ignored, and `metadata-location` does not change between commits ([#11354](https://github.com/Apicurio/apicurio-registry/issues/11354)).
9. The Flink catalog is read-only, supports Avro and JSON Schema only, reads the SDK's first page of
   groups and artifacts, has no TLS options, and ships without a shaded JAR
   ([#8285](https://github.com/Apicurio/apicurio-registry/issues/8285), [#11355](https://github.com/Apicurio/apicurio-registry/issues/11355)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| A separate catalog service or store | Not selected: reusing groups and artifacts gives versioning, authorization, search and every storage variant for free, at the cost of mapping Iceberg concepts onto them |
| Storing metadata files in the warehouse (pointer-swap catalog) | Not selected: the registry keeps the metadata as version content, so it needs no object-store access; engines get a synthetic `metadata-location` |
| Pessimistic locking for commits | Not selected: the Iceberg protocol expects optimistic commits with 409 and client retry |
| A writable Flink catalog | Not selected: schemas are registered by producers and governed by rules; the catalog only exposes them |

## Consequences

- **Benefits:** Iceberg engines and Flink jobs see the same governed metadata; commit history is
  version history; no new storage, no new auth model; off by default.
- **Costs:** a second REST surface with its own error shape and spec to keep in step; Iceberg
  semantics (snapshots, refs, schema IDs) reimplemented in `commit/`; the Flink module tracks Flink
  releases (`flink.version` 1.20.5).

## Example and verification

- **Example:** none in `examples/` exercises either catalog.
- **User documentation:**
  [`assembly-using-iceberg-catalog.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-using-iceberg-catalog.adoc)
  (including Spark, Trino, DuckDB and ClickHouse configuration),
  [`assembly-using-flink-catalog.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-using-flink-catalog.adoc).

| Concern | Tests to read |
|---|---|
| Endpoints, mapping, commits | `IcebergApiTest`, `TableRequirementValidatorTest`, `TableUpdateApplicatorTest`, `IcebergSchemaUtilTest` |
| Opt-in gate | `IcebergFeatureGateTest` |
| Metrics | `IcebergMetricsTest`, `OTelMetricsProviderTest` |
| Authorization | `GrantsAuthorizationTest`, `GrantsIndirectAccessTest`, `SearchPermissionsTest` |
| Error routing | `ApiSurfaceResolverTest`, `RegistryExceptionMapperTest` |
| Search extraction | `IcebergStructuredContentExtractorTest` |
| Real Iceberg client | `IcebergCatalogIT` (group `iceberg`, h2 job in `verify-integration-tests.yaml`) |
| Flink | `AvroFlinkTypeConverterTest`, `JsonSchemaFlinkTypeConverterTest`, `StateSchemaServiceTest`, `SavepointValidatorTest`, `SchemaCompatibilityCheckerTest`, `ApicurioCatalogIT` (runs only with `REGISTRY_URL` set) |

```bash
./mvnw test -pl app -Dtest='Iceberg*Test,Table*Test'
./mvnw test -pl utils/flink
```

## History

| Milestone | Result |
|---|---|
| [#7105](https://github.com/Apicurio/apicurio-registry/pull/7105) | Flink catalog |
| [#7258](https://github.com/Apicurio/apicurio-registry/pull/7258) | Iceberg REST catalog, phase 1 |
| [#7476](https://github.com/Apicurio/apicurio-registry/pull/7476) | Table commits |
| [#7579](https://github.com/Apicurio/apicurio-registry/pull/7579) | Iceberg integration tests |
| [#7637](https://github.com/Apicurio/apicurio-registry/pull/7637) | Views |
| This ADR | First written record |
