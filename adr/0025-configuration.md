# ADR-0025: Configuration

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#10142](https://github.com/Apicurio/apicurio-registry/issues/10142).
- **Scope:** How the registry server reads its configuration, how properties are declared and
  documented, and how some of them change at runtime through the admin API and storage. Not covered:
  what individual settings do (guardrails and read-only mode are in
  [ADR-0017](0017-operational-guardrails.md)), and how the operator builds the Deployment
  ([ADR-0019](0019-operator.md)).

## Reading this document

- Start with [the decision](#decision).
- For deployment, see [configuration](#configuration) and the
  [configuration reference](../docs/modules/ROOT/pages/getting-started/assembly-config-reference.adoc).
- For a running system, see the [example](../examples/operational-guardrails/).
- For code changes, see the [source map](#source-map) and [verification](#example-and-verification).

## Context

The registry is a Quarkus application deployed as a container, by hand, with Compose, or by the
operator, often as several replicas sharing one storage. Most settings (storage kind, authentication,
TLS) are fixed for the life of a process and come from the environment. A few are toggles an
administrator needs to flip without a restart: read-only mode during a migration, anonymous read
access, deletion switches, the log level. Those must reach every replica, survive restarts, and be
limited to properties that are safe to change. Several hundred properties also need a reference that
stays in step with the code.

## Decision

1. **MicroProfile Config is the only configuration mechanism.** Properties are read with
   `@ConfigProperty` (SmallRye Config in Quarkus) from the usual sources: `application.properties`,
   environment variables (`APICURIO_STORAGE_KIND` for `apicurio.storage.kind`), system properties.
   There is no registry-specific config file format.
2. **Registry properties are `apicurio.*` and documented by `@Info`.** Names follow
   `apicurio.<category>.<feature>.<aspect>` with type-revealing suffixes (`.enabled`, `.url`,
   `.seconds`); defaults go in `@ConfigProperty(defaultValue=)`, and `application.properties` is kept
   for Quarkus overrides, `${...}` interpolation and `.dynamic.allow` entries. Every `@ConfigProperty`
   in `app` carries `@Info` (`category`, `description`, `availableSince`, `experimental`). The
   conventions are in [`.claude/rules/config-properties.md`](../.claude/rules/config-properties.md).
3. **The reference is generated.** `GenerateAllConfigPartial` (module
   `apicurio-registry-config-generator`) reads the Jandex index of the `app` JAR, collects every
   `@ConfigProperty` with `@Info`, adds defaults from `application.properties`, and writes
   [`ref-registry-all-configs.adoc`](../docs/modules/ROOT/partials/getting-started/ref-registry-all-configs.adoc),
   grouped by category, which the configuration reference includes. Dynamic properties have
   `[dynamic]` after their type; experimental ones are marked *(experimental)*.
4. **Dynamic properties are `@Dynamic Supplier<T>` fields.** A field annotated `@Dynamic(label,
   description, requires)` and `@ConfigProperty` with a `Supplier<T>` type is produced by
   `DynamicConfigProducer`, which reads the value from `Config` on every `get()`. At build time
   `ConfigIndexProcessor` collects them into `DynamicConfigPropertyList`; a dynamic property without a
   `defaultValue`, or not a `Supplier`, fails the build.
5. **Stored values win.** `DynamicConfigSource` is a config source with ordinal 450, above system
   properties (400) and environment variables (300). For an indexed dynamic property it returns the
   value from storage (`DynamicConfigStorage.getConfigProperty`) once storage is ready; otherwise it
   returns nothing and the lower sources apply. Deleting the stored value restores the deployed one.
   One exception: `FileBasedSecretsInterceptor` resolves a nonempty `<name>.file` before any source,
   so it also wins over a stored value.
6. **A dynamic property must be allowed.** `DynamicConfigPropertyIndexImpl` accepts a property only if
   `<name>.dynamic.allow=true` and every `requires` entry (`name=value`, or `name` for "is set") holds.
   `application.properties` sets each `.dynamic.allow` to `${apicurio.config.dynamic.allow-all}`
   (default `true`), so `allow-all=false` disables runtime changes unless a property's own
   `.dynamic.allow` is set explicitly. Acceptance is evaluated on each call, against the full
   configuration. It gates the admin API only: a value already stored for a property that is no longer
   allowed is still read by `DynamicConfigSource`.
7. **The admin API changes them.** `/admin/config/properties` (v3 and v2, `AdminResourceImpl`) lists
   accepted properties, reads, sets (`PUT`) and resets (`DELETE`) one. List and read return the value
   read from storage directly (not through the cache), or the configured value when nothing is stored,
   so they can differ from what this replica is using (cache, `.file`). The operations are at the
   admin authorization level, enforced as described in [ADR-0009](0009-authentication.md); v3 writes
   are audited. An unknown or not-accepted name is 404; a value that does not parse as the property's
   type (`Boolean`, `Integer`, `Long`) is rejected. v3 only: `apicurio.log.level` has its own
   validator, and setting an experimental toggle to `true` without
   `apicurio.features.experimental.enabled` is 409.
8. **Storage is the shared state, with a cache.** Values live in the `config` table (SQL, with a
   `modifiedOn` timestamp) or travel through the KafkaSQL journal (`SetConfigProperty1Message`,
   `DeleteConfigProperty1Message`) into each replica's local database.
   `RegistryStorageConfigCache`, a storage decorator, caches lookups (including misses), clears the
   cache on a local set or delete, and every `apicurio.config.refresh.every` asks storage for rows
   modified since the last refresh and clears the whole cache if there are any. The first run only
   records the baseline time.
9. **Startup guards.** `ExperimentalFeaturesConfig` runs before storage starts and refuses to start if
   an `@Info(experimental = true)` boolean, or `apicurio.storage.kind=gitops|kubernetesops`, is on
   without the gate. `DeprecatedPropertiesCheck` warns on deprecated names and fails on removed ones.
   `FileBasedSecretsInterceptor` resolves `<name>.file` to the trimmed content of that file, for
   mounted secrets.
10. **The operator passes environment variables.** Its features translate the CR into `APICURIO_*`
    and `QUARKUS_*` variables, and `spec.app.env` passes anything else
    ([ADR-0019](0019-operator.md)). Dynamic values are not part of the CR.

### Why storage and not a config map

A value in the registry's own storage is shared by every replica that shares the storage, survives
restarts when the storage is persistent (not the default in-memory H2), and needs no access to the
platform. It is not part of a registry export: runtime settings must be captured and restored
separately. The cost is
that a change is seen by other replicas only when their cache refreshes.

## Behaviour across storage variants

| Variant | Behaviour |
|---|---|
| SQL | Rows in `config`; other replicas see a set on their next refresh, not a delete (limitation 1) |
| KafkaSQL | Set and delete go through the journal and each replica applies both to its local database; its cache sees a set on the next refresh, not a delete (limitation 1) |
| GitOps, KubernetesOps | Read-only: the admin API write is refused; properties come from the data source (`AbstractPollingDataSourceManager`) |

In read-only mode (`apicurio.storage.read-only.enabled`, [ADR-0017](0017-operational-guardrails.md)),
`ReadOnlyRegistryStorageDecorator` refuses every config write except to that property itself, so the
mode can be switched off again.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.config.dynamic.allow-all` | `true` (`application.properties`) | Default of every `<name>.dynamic.allow` |
| `<name>.dynamic.allow` | `${apicurio.config.dynamic.allow-all}` | Whether the admin API may change that property; overrides `allow-all` |
| `apicurio.config.cache.enabled` | `true` | Cache stored values; refresh on schedule |
| `apicurio.config.refresh.every` | `1m` (`application.properties`) | How often a replica checks for changed values |
| `apicurio.features.experimental.enabled` | `false` | Gate for experimental properties |

The dynamic properties themselves are listed in the reference with `[dynamic]`, and on a running
instance by `GET /apis/registry/v3/admin/config/properties`.

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Annotations | [`Info`](../config-index/definitions/src/main/java/io/apicurio/common/apps/config/Info.java), [`Dynamic`](../config-index/definitions/src/main/java/io/apicurio/common/apps/config/Dynamic.java), [`ConfigPropertyCategory`](../config-index/definitions/src/main/java/io/apicurio/common/apps/config/ConfigPropertyCategory.java) | Declaring and documenting properties |
| Build | [`ConfigIndexProcessor`](../config-index/deployment/src/main/java/apicurio/common/app/components/config/index/deployment/ConfigIndexProcessor.java) | Index of dynamic and experimental properties |
| Dynamic config | [`DynamicConfigSource`](../app/src/main/java/io/apicurio/registry/config/config/impl/DynamicConfigSource.java), [`DynamicConfigPropertyIndexImpl`](../app/src/main/java/io/apicurio/registry/config/config/impl/DynamicConfigPropertyIndexImpl.java), [`DynamicConfigProducer`](../app/src/main/java/io/apicurio/registry/config/config/impl/DynamicConfigProducer.java), [`DynamicConfigStartup`](../app/src/main/java/io/apicurio/registry/config/config/impl/DynamicConfigStartup.java) | Stored values as a config source; acceptance; `Supplier` injection |
| Cache | [`RegistryStorageConfigCache`](../app/src/main/java/io/apicurio/registry/config/RegistryStorageConfigCache.java) | Caching and scheduled refresh |
| Storage | [`SqlConfigRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlConfigRepository.java), [`KafkaSqlRegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/impl/kafkasql/KafkaSqlRegistryStorage.java), [`ReadOnlyRegistryStorageDecorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/ReadOnlyRegistryStorageDecorator.java) | Persistence, journal, read-only exception |
| REST | [`AdminResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/AdminResourceImpl.java) (v3, v2) | `/admin/config/properties` |
| Startup | [`ExperimentalFeaturesConfig`](../app/src/main/java/io/apicurio/registry/config/ExperimentalFeaturesConfig.java), [`DeprecatedPropertiesCheck`](../app/src/main/java/io/apicurio/registry/config/DeprecatedPropertiesCheck.java), [`FileBasedSecretsInterceptor`](../app/src/main/java/io/apicurio/registry/config/FileBasedSecretsInterceptor.java) | Gates, deprecations, secrets from files |
| Docs | [`GenerateAllConfigPartial`](../docs/config-generator/src/main/java/io/apicurio/registry/docs/GenerateAllConfigPartial.java) | Generated reference |

## Limitations and open work

1. Resetting a property (`DELETE`) clears the cache only on the replica that served it; the refresh
   finds changed rows, not deleted ones, so other replicas keep the old value until their cache is
   next cleared (a restart, or any other detected config change)
   ([#11308](https://github.com/Apicurio/apicurio-registry/issues/11308)). A workaround is to set the
   wanted value explicitly instead of resetting; that leaves a stored override, not the deployed value.
2. Propagation is eventual: normally within `apicurio.config.refresh.every` (1 minute by default).
   A change to an already cached value made before a replica's first refresh is missed until its
   cache is next cleared.
3. Runtime settings are not included in a registry export; they persist across restarts only on
   persistent storage.
4. `@Dynamic.requires` and `@Info.dependsOn` are separate mechanisms; `dependsOn` and `seeAlso` are
   not used by the generator or at runtime
   ([#10162](https://github.com/Apicurio/apicurio-registry/issues/10162), epic
   [#10142](https://github.com/Apicurio/apicurio-registry/issues/10142)).
5. A `@Dynamic` property is settable only if it also has a `.dynamic.allow` entry; nothing checks
   that the two lists agree ([#11359](https://github.com/Apicurio/apicurio-registry/issues/11359)).
6. Value validation is limited to parsing the type (v3 validates `apicurio.log.level`); ranges and
   enumerations are not checked.
7. Properties found only in `application.properties` (including the `.dynamic.allow` entries) appear
   in the reference under an `unknown` category ([#11359](https://github.com/Apicurio/apicurio-registry/issues/11359)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Restart to change any setting | Kept for most properties; not acceptable for operational toggles such as read-only mode |
| A ConfigMap or file watched by each replica | Not used: depends on the platform and is not shared through the registry's storage |
| Making every property dynamic | Not done: many properties are read once at startup (storage, auth, TLS); each dynamic property is opt-in through `@Dynamic` and an allow entry |
| Pushing changes to replicas (events) | Not used: a periodic query on the shared storage needs no extra channel; the cost is the refresh delay |
| A hand-written configuration reference | Replaced by generation from the annotations, so the reference follows the code |

## Consequences

- **Benefits:** one mechanism for every source; operational toggles change without a restart and
  survive restarts on persistent storage; one property sets the default for which properties may
  change; the reference follows the code.
- **Costs:** two annotations per property and an extra allow entry for dynamic ones; changes reach
  other replicas with a delay, and resets not at all (#11308); a stored value silently overrides the
  deployed environment until it is reset; runtime settings are not exported.

## Example and verification

- **Example:** [`examples/operational-guardrails`](../examples/operational-guardrails/):
  `docker compose up -d && ./demo.sh` switches deletion and read-only mode on one of two replicas
  through `/admin/config/properties` and checks that the other replica follows
  (`APICURIO_CONFIG_REFRESH_EVERY=5s`). [`examples/authentication`](../examples/authentication/)
  switches anonymous read access on and resets it at runtime;
  [`examples/storage-variants`](../examples/storage-variants/) toggles read-only mode on every variant
  and across KafkaSQL replicas.
- **User documentation:**
  [`assembly-configuring-the-registry.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc)
  (loading values from files, operational guardrails, changing the log level at runtime) and
  [`assembly-config-reference.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-config-reference.adoc).

| Concern | Tests to read |
|---|---|
| Admin config API | `AdminResourceTest` (`rbac`), `ExperimentalDynamicConfigTest`, `ExperimentalDynamicConfigGateEnabledTest` |
| Cache and refresh | `RegistryStorageConfigCacheTest` |
| Read-only exception | `ReadOnlyRegistryStorageTest` |
| Startup guards | `ExperimentalFeaturesConfigTest`, `ExperimentalFeaturesConfigStartupTest`, `DeprecatedPropertiesCheckTest`, `DeprecatedPropertiesDefaultSilenceTest` |
| Secrets from files | `FileBasedSecretsInterceptorTest` |

```bash
./mvnw test -pl app -Dtest='RegistryStorageConfigCacheTest,ExperimentalDynamicConfig*Test,ExperimentalFeaturesConfig*Test,DeprecatedProperties*Test,FileBasedSecretsInterceptorTest'
./mvnw clean install -pl :apicurio-registry-config-generator -am -DskipTests
```

## History

| Milestone | Result |
|---|---|
| [#5530](https://github.com/Apicurio/apicurio-registry/pull/5530) | Config index and dynamic config moved into this repository from the common components |
| [#6790](https://github.com/Apicurio/apicurio-registry/pull/6790) | Config documentation generator fixed |
| [#9850](https://github.com/Apicurio/apicurio-registry/pull/9850) | Atomic loads in the config cache |
| This ADR | First written record |
