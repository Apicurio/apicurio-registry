# ADR-0017: Operational Guardrails

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-17.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11218](https://github.com/Apicurio/apicurio-registry/issues/11218),
  [#11219](https://github.com/Apicurio/apicurio-registry/issues/11219).
- **Scope:** The settings that bound what clients can do, independently of who they are: usage
  limits, disabled API paths, the switches for destructive and mutating operations, and read-only
  mode. Not covered: authentication and authorization ([ADR-0009](0009-authentication.md),
  [ADR-0005](0005-per-resource-authorization.md)) and the read-only storage variants
  ([ADR-0010](0010-storage-architecture.md)).

## Context

A registry is shared infrastructure. Without bounds, one client can register megabyte schemas or
thousands of versions, an API the organization does not want can be used because it exists,
destructive operations are one call away, and there is no way to stop writes during a migration or
restore without stopping the service. Authorization answers *who* may do something; these guardrails
answer *what* anyone may do, and must apply to every API and storage variant.

## Decision

1. **Limits are a storage decorator.** `RegistryStorageLimitsEnforcer` checks creates and metadata
   updates against `apicurio.limits.config.*` before delegating, so every API (v3, v2, Confluent,
   MCP) is bound by them. A refusal is `409` with a message that names the limit. Every limit
   defaults to `-1` (none); limits are read at startup.
2. **Disabled APIs are a servlet filter.** `apicurio.disable.apis` is a list of regular expressions
   matched against the request path by `RegistryApplicationServletFilter`; a match is `404`, as if
   the path did not exist. A servlet filter (not JAX-RS) so it can also turn off non-JAX-RS paths
   such as the web UI.
3. **Destructive and mutating operations are opt-in and dynamic.** Deleting groups, artifacts and
   versions (`apicurio.rest.deletion.*`, `405` when off), editing version content
   (`apicurio.rest.mutability.artifact-version-content.enabled`) and draft production mode are off by
   default and are runtime settings.
4. **Read-only mode is a storage decorator and dynamic.** `apicurio.storage.read-only.enabled`
   (`ReadOnlyRegistryStorageDecorator`) refuses every write with `409` on every API, keeps reads, and
   tells the console (`readOnly`, `readOnlyReason: storage`). Configuration writes are not storage
   writes, so it can be switched off through the API.
5. **Runtime settings live in storage.** A runtime setting (`@Dynamic`) is changed with
   `PUT /admin/config/properties/{name}`, stored with the data, and re-read by every replica every
   `apicurio.config.refresh.every` (1 minute).

### Why decorators

Every write path, from every API, goes through the storage interface, so a decorator cannot be
bypassed by a new endpoint. The same chain carries events, search indexing and caching
([ADR-0011](0011-registry-events.md), [ADR-0015](0015-search-and-elasticsearch.md)).

### Why runtime settings for read-only and deletion

They are operational decisions taken during an incident or a migration, when a restart is the last
thing wanted, and they must reach every replica without redeploying.

## Model

| Guardrail | Applies to | Refusal | Changed |
|---|---|---|---|
| Size and length limits (schema size, name, description, labels, label size, properties) | The request | 409 | Restart |
| Count limits (`max-artifacts`, `max-total-schemas`, `max-versions-per-artifact`) | Counters per replica, refreshed from storage every 30 s | 409 | Restart |
| Disabled APIs | The request path | 404 | Restart |
| Deletion switches | Delete endpoints | 405 | Runtime |
| Read-only mode | Every storage write | 409 | Runtime |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.limits.config.max-artifacts`, `max-total-schemas`, `max-versions-per-artifact` | `-1` | Count limits |
| `apicurio.limits.config.max-schema-size.bytes` | `-1` | Content size |
| `apicurio.limits.config.max-artifact-labels`, `max-label-size.bytes`, `max-property-key-size.bytes`, `max-property-value-size.bytes`, `max-artifact-properties` | `-1` | Label limits |
| `apicurio.limits.config.max-name-length`, `max-description-length` | `-1` | Metadata limits |
| `apicurio.limits.config.max-requests-per-second` | `-1` | Accepted, not enforced ([#11219](https://github.com/Apicurio/apicurio-registry/issues/11219)) |
| `apicurio.storage.metrics.cache.check-period.ms` | `30000` | How often count limits are re-read from storage |
| `apicurio.disable.apis` | none | Path regular expressions answered with 404 |
| `apicurio.rest.deletion.group.enabled`, `.artifact.enabled`, `.artifact-version.enabled` | `false` | Deletes (runtime) |
| `apicurio.rest.mutability.artifact-version-content.enabled` | `false` | Editing draft content (runtime) |
| `apicurio.storage.read-only.enabled` | `false` | Read-only mode (runtime) |
| `apicurio.config.refresh.every` | `1m` | How often replicas re-read runtime settings |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Limits | [`limits`](../app/src/main/java/io/apicurio/registry/limits/) | Configuration, checks, enforcer decorator |
| Counters | [`StorageMetricsStore`](../app/src/main/java/io/apicurio/registry/storage/metrics/StorageMetricsStore.java) | Per-replica artifact and version counts |
| Disabled APIs | [`DisabledApisMatcherService`](../app/src/main/java/io/apicurio/registry/services/DisabledApisMatcherService.java), [`RegistryApplicationServletFilter`](../app/src/main/java/io/apicurio/registry/rest/RegistryApplicationServletFilter.java) | Matching and the 404 |
| Read-only | [`ReadOnlyRegistryStorageDecorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/ReadOnlyRegistryStorageDecorator.java) | Refusing writes |
| Runtime settings | [`config`](../app/src/main/java/io/apicurio/registry/config/) | Storage-backed dynamic properties and their refresh |

## Limitations and open work

1. Count limits are per replica, and check-then-act: a cluster can hold up to N times the limit
   ([#11218](https://github.com/Apicurio/apicurio-registry/issues/11218)).
2. `max-requests-per-second` does nothing
   ([#11219](https://github.com/Apicurio/apicurio-registry/issues/11219)).
3. Limits are global: there are no per-group or per-team quotas.
4. Runtime settings reach other replicas only after the refresh period.
5. The console's own read-only setting (`REGISTRY_FEATURE_READ_ONLY`) only hides actions; it is not a
   guardrail.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Limits in the REST layer | Not selected: every API would need them; the decorator covers all |
| Distributed counters (database counts per write) | Open (#11218): exact, but a count query per write |
| Rate limiting in the registry | Not implemented; gateways do it better (#11219) |
| Read-only by redeploying with a property | Not selected: a restart during an incident; runtime settings reach every replica |
| Disabling APIs by authorization | Not selected: an API nobody may use is simpler to remove, and 404 reveals nothing |

## Consequences

- **Benefits:** bounds that hold for every API and storage variant; destructive operations off by
  default; writes frozen in seconds, without a restart.
- **Costs:** count limits are approximate in a cluster; limits and disabled APIs need a restart.

## Example and verification

- **Example:** [`examples/operational-guardrails`](../examples/operational-guardrails/): `./demo.sh`
  runs two replicas on PostgreSQL and checks every limit and its message, disabled APIs, the deletion
  switch and read-only mode changed at runtime and followed by the other replica, and the per-replica
  count limit. CI runs it.
- **User documentation:**
  [`assembly-configuring-the-registry.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc),
  *Operational guardrails*.

| Concern | Tests to read |
|---|---|
| Limits | `LimitsTest`, `LabelLimitsTest`, `RegistryLimitsServiceUnitTest`, `RegistryStorageLimitsEnforcerTest` (app) |
| Disabled APIs | `DisableApisFlagsTest` (app) |
| Read-only mode | `ReadOnlyRegistryStorageTest` (app) |

## History

| Milestone | Result |
|---|---|
| 2.x | Limits, disabled APIs |
| 3.x | Read-only mode and the deletion switches as runtime settings |
| This ADR | First written record, with a verified example |
