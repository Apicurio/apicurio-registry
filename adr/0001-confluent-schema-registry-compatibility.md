# ADR-0001: Confluent Schema Registry Compatibility Layer

- **Status:** Accepted.
- **Decision date:** 2026-05-09.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#7971](https://github.com/Apicurio/apicurio-registry/pull/7971),
  [#7974](https://github.com/Apicurio/apicurio-registry/pull/7974),
  [#7985](https://github.com/Apicurio/apicurio-registry/pull/7985),
  [#11124](https://github.com/Apicurio/apicurio-registry/issues/11124),
  [#11125](https://github.com/Apicurio/apicurio-registry/issues/11125).
- **Scope:** The Confluent Schema Registry-compatible REST API (`/apis/ccompat/v7/`, `/apis/ccompat/v8/`):
  subject and group mapping, ID mapping, deletes, error codes, registration, compatibility checks,
  serialization and mode. It does not
  cover the Confluent-to-Apicurio export utility or the Apicurio serdes.

## Context

Apicurio Registry provides a Confluent Schema Registry-compatible API so that existing Confluent
clients, Kafka Connect, and ksqlDB can use Apicurio as a drop-in replacement. The compatibility layer
exposes the Confluent v7 and v8 REST API at `/apis/ccompat/v7/` and `/apis/ccompat/v8/`.

The [Apicurio Compatibility Harness](https://github.com/Apicurio/apicurio-compatibility-harness)
runs the same requests against both Confluent and Apicurio and compares responses. Before these
changes, 26 of 53 tests passed (49%). After, 52 of 53 pass (98%); see
[Example and verification](#example-and-verification).

## Decision

1. **v8 delegates to v7.** All business logic lives in the v7 implementations; v8 implementations
   are thin wrappers that convert beans and delegate to v7.
2. **`contentId` is the default Confluent schema ID.** Confluent's `id` maps to Apicurio's
   `contentId`; `globalId` is available through legacy ID mode.
3. **The compatibility check endpoint defaults to `BACKWARD`** when no compatibility rule is
   configured at any level.
4. **Avro schemas are compacted on read** to canonical single-line JSON in every ccompat response.
5. **Null suppression is scoped with Jackson mixins** on ccompat bean classes, not set globally.
6. **`ModeUpdateRequest.mode` is a plain string** in the OpenAPI spec, not an enum, so the handler
   validates it and returns Confluent's error.
7. **A subject is an artifact in the `default` group** (the subject is the artifact ID). Groups are
   opt-in: per request with the `X-Registry-GroupId` header, or for every subject with
   `apicurio.ccompat.group-concat.enabled` (`group:artifact`).
8. **Confluent's soft delete is the `DISABLED` version state;** a permanent delete deletes the version
   (or the artifact), and requires a soft delete first, as in Confluent.
9. **A subject's compatibility level is the artifact's `COMPATIBILITY` rule**, so it is the same
   whether set through ccompat or the Apicurio API.

### Why these choices

- **v8 delegates to v7:** v7 and v8 APIs are nearly identical. Duplicating logic would create
  maintenance burden. The delegation pattern ensures behavioral consistency.
- **`contentId` as schema ID:** Confluent assigns the same schema ID when identical content is
  registered under different subjects. Apicurio's `contentId` provides this behavior naturally since
  it's based on content hash. `globalId` would assign different IDs for the same content, breaking
  Confluent client expectations.
- **Default `BACKWARD` on check:** Confluent defaults to BACKWARD globally. Without this, the check
  endpoint would return "compatible" for any schema (since no rule exists to violate), which is
  misleading.
- **Avro compaction:** Confluent returns schemas in compact single-line JSON. The compatibility
  harness compares response bodies, so formatting differences cause test failures.
- **Scoped mixins:** a previous approach set `NON_NULL` globally, which broke the v3 API (labels with
  null values were silently dropped). Mixins scope the behavior to ccompat beans only.
- **Subjects as default-group artifacts:** Confluent has no groups. Mapping subjects to one group
  keeps subject names unchanged; group-concat is for registries that need Apicurio groups.
- **Soft delete as a state:** `DISABLED` already hides a version from lookups and `latest`, and keeps
  it for `?deleted=true`; no second deletion model is needed.
- **Mode as string:** with an enum, JAX-RS returns 400 (Bad Request) for invalid values before the
  handler runs. Confluent returns 422 with a specific error code. Using a plain string allows the
  handler to validate and return the correct Confluent-style error response.

## API model

### API layering

```
/apis/ccompat/v7/*  -->  v7 Resource Implementations (core logic)
/apis/ccompat/v8/*  -->  v8 Resource Implementations (delegate to v7)
```

The v8 layer converts between v7 and v8 bean types, which are generated from separate OpenAPI specs.
Both specs are maintained in
[`api-specifications/ccompat/`](../app/src/main/resources-unfiltered/META-INF/resources/api-specifications/ccompat/).

### Endpoint coverage

| Resource | Endpoints |
|----------|-----------|
| Subjects | CRUD for subjects and versions, schema lookup by content, lookup by version |
| Schemas | Lookup by ID, list subjects/versions by schema ID, list schema types |
| Compatibility | Check schema compatibility against a version or all versions |
| Config | Get/set global and subject-level compatibility level |
| Mode | Get/set global and subject-level read/write mode |
| Contexts | Returns static single context (contexts not supported) |

### ID mapping

Apicurio has two internal ID types: `globalId` (unique per artifact version) and `contentId` (unique
per content hash). Confluent uses a single incrementing `schema ID`. The mapping is controlled by
`apicurio.ccompat.legacy-id-mode.enabled`:

- **`false` (default):** Confluent `id` maps to Apicurio `contentId`. Same schema content across
  subjects returns the same ID. Matches Confluent behavior for most use cases.
- **`true`:** Confluent `id` maps to Apicurio `globalId`. Each registration gets a unique ID
  regardless of content.

### Subjects and groups

| Configuration | Subject `orders-value` | Artifact `orders/order-placed` |
|---|---|---|
| Default | `default/orders-value` | Not a subject, unless the request has `X-Registry-GroupId: orders` |
| `apicurio.ccompat.group-concat.enabled=true` | Invalid: must be `group:artifact` | Subject `orders:order-placed` |

### Deletes

| Request | Effect |
|---|---|
| `DELETE /subjects/{s}/versions/{v}` | Version state `DISABLED`: hidden from lookups and `latest`, listed with `?deleted=true` |
| The same, again | `40406` |
| `DELETE /subjects/{s}/versions/{v}?permanent=true` | Deletes the version; `40407` if it was not soft deleted |
| `DELETE /subjects/{s}` | Every version `DISABLED`; reading the subject returns `40401` |
| `DELETE /subjects/{s}?permanent=true` | Deletes the artifact; `40405` if it was not soft deleted |

### v7 and v8

v8 delegates to v7 (Decision 1), so they return the same data. Known differences: v8 includes an
empty `references` list in schema responses where v7 omits it. Unknown request properties are
ignored on both, whether or not `X-Confluent-Accept-Unknown-Properties` is sent.

### Error handling

Confluent uses numeric error codes in JSON responses. The mapping is in `CCompatExceptionMapperService`:

| Apicurio Exception | Confluent Error Code | HTTP Status |
|---------------------|---------------------|-------------|
| `ArtifactNotFoundException` | 40401 (SUBJECT_NOT_FOUND) | 404 |
| `VersionNotFoundException` | 40402 (VERSION_NOT_FOUND) | 404 |
| `ContentNotFoundException` | 40403 (SCHEMA_NOT_FOUND) | 404 |
| `RuleNotFoundException` | 40408 (SUBJECT_COMPATIBILITY_NOT_CONFIGURED) | 404 |
| `UnprocessableEntityException` | 42201 (INVALID_SCHEMA) | 422 |
| `InvalidCompatibilityLevelException` | 42203 (INVALID_COMPATIBILITY_LEVEL) | 422 |
| `ReferenceExistsException` | 42206 (REFERENCE_EXISTS) | 422 |
| `SubjectSoftDeletedException` | 40404 | 404 |
| `SubjectNotSoftDeletedException` | 40405 | 409 (Confluent: 404) |
| `SchemaSoftDeletedException` | 40406 | 409 (Confluent: 404) |
| `SchemaNotSoftDeletedException` | 40407 | 409 (Confluent: 404) |
| `RuleViolationException` | 409 | 409 |

Error messages are formatted to match Confluent's style (e.g., `"Subject 'foo' not found."`).

### Schema registration

Schema registration uses per-subject locking via a Guava `Cache<GA, ReentrantLock>` to serialize
concurrent writes to the same subject within a JVM. The flow:

1. Acquire per-subject lock
2. Try to find an existing version with matching content (`lookupSchema`)
3. If found and active, return existing ID
4. If not found, create or update the artifact via `createOrUpdateArtifact`
5. Release lock

### Compatibility checking

When a client calls the compatibility check endpoint and no compatibility rule is configured at any
level (artifact, group, global, default), the implementation defaults to `BACKWARD`, matching
Confluent's default behavior.

### JSON serialization

Ccompat responses must omit null fields (e.g., `references` should not appear when empty). This is
handled via Jackson mixins applied to `v7.beans.Schema` and `v8.beans.Schema` classes through
`CCompatJacksonCustomizer`, which adds `@JsonInclude(NON_NULL)` behavior.

Avro schemas are compacted to canonical single-line JSON in ccompat responses via
`ApiConverter.compactIfAvro()`, which parses and re-serializes through Apache Avro's `Schema.Parser`.

### Mode management

Read/write mode is stored as a `DynamicConfigProperty` string (`apicurio.ccompat.mode` for global,
`apicurio.ccompat.mode.subject.<groupId>/<artifactId>` for per-subject). Valid modes: `READWRITE`,
`READONLY`, `READONLY_OVERRIDE`, `IMPORT`. The mode is stored and returned, but **not enforced**:
writes succeed in `READONLY` ([#11124](https://github.com/Apicurio/apicurio-registry/issues/11124)).
To block writes, use the storage read-only mode (`apicurio.storage.read-only.enabled`).

### Behaviour across storage variants

The layer is storage-independent: it calls the same storage API as the v3 REST API.

| Variant | Behaviour |
|---|---|
| SQL | Unique constraints provide the consistency guarantee for concurrent registrations |
| KafkaSQL | Kafka topic ordering provides the consistency guarantee for concurrent registrations |
| GitOps, KubernetesOps | Read-only storage: lookups work, registration and config/mode writes are rejected |

The per-subject lock is per JVM only; see [Limitations](#limitations-and-open-work).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.ccompat.legacy-id-mode.enabled` | `false` | Map Confluent `id` to `globalId` instead of `contentId` |
| `apicurio.ccompat.use-canonical-hash` | `false` | Use the canonical hash instead of the regular content hash |
| `apicurio.ccompat.max-subjects` | `1000` | Maximum number of subjects returned by `/subjects` |
| `apicurio.ccompat.group-concat.enabled` | `false` | Encode the group in the subject name |
| `apicurio.ccompat.group-concat.separator` | `:` | Separator between group and artifact in the subject name |
| `apicurio.ccompat.mode` | none | Dynamic: global mode; per subject `apicurio.ccompat.mode.subject.<groupId>/<artifactId>` |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| API specs | [`ccompat/v7`, `ccompat/v8`](../app/src/main/resources-unfiltered/META-INF/resources/api-specifications/ccompat/) | Confluent-compatible OpenAPI definitions |
| v7 implementation | [`ccompat/rest/v7/impl`](../app/src/main/java/io/apicurio/registry/ccompat/rest/v7/impl/) | Business logic, including `SubjectsResourceImpl`, `ApiConverter`, `CCompatConfig` |
| v8 delegation | [`ccompat/rest/v8/impl`](../app/src/main/java/io/apicurio/registry/ccompat/rest/v8/impl/) | Bean conversion and delegation to v7 |
| Serialization | [`CCompatJacksonCustomizer`](../app/src/main/java/io/apicurio/registry/ccompat/rest/CCompatJacksonCustomizer.java) | Scoped `NON_NULL` mixins |
| Errors | [`CCompatExceptionMapperService`](../app/src/main/java/io/apicurio/registry/services/http/CCompatExceptionMapperService.java) | Exception to Confluent error-code mapping |

## Limitations and open work

1. **Avro compaction on every read (performance).** `ApiConverter.compactIfAvro()` parses and
   re-serializes every Avro schema on every GET request. Avro parsing is not cheap. Under high read
   load, this will degrade performance. The original schema formatting (indentation, key ordering)
   submitted by the user is lost in ccompat responses. *Mitigation options:* move compaction to
   write time (normalize once at registration), cache the compacted form alongside the original
   content, or support compaction only via an explicit `?format=canonical` parameter.
2. **Default BACKWARD inconsistency between check and registration.** The compatibility *check*
   endpoint defaults to BACKWARD when no rule is configured. But schema *registration* does NOT
   enforce BACKWARD; it only applies rules if explicitly configured. A client could check
   compatibility (passes with BACKWARD default), then register an incompatible schema (no
   enforcement). *Mitigation:* make BACKWARD the global default in `RulesProperties` so both code
   paths use it, or remove the hardcoded default from the check endpoint.
3. **Per-JVM subject locks do not protect multi-instance deployments.** The
   `Cache<GA, ReentrantLock>` in `SubjectsResourceImpl` only serializes writes within a single JVM.
   In a multi-instance deployment, two instances can concurrently register schemas for the same
   subject. The storage layer (SQL unique constraints or Kafka topic ordering) provides the actual
   consistency guarantee. *Mitigation:* use database-level advisory locks, or document that the
   application-level lock is a best-effort optimization for single-instance deployments.
4. **Jackson mixins applied to the global ObjectMapper.** `CCompatJacksonCustomizer` applies
   `@JsonInclude(NON_NULL)` mixins to the global ObjectMapper. While the target bean classes are only
   used in ccompat contexts today, the pattern is fragile: if these bean types are ever used in
   non-ccompat responses, null fields will be silently omitted. *Mitigation:* replace with a JAX-RS
   `WriterInterceptor` scoped to `/apis/ccompat/*` paths, or add integration tests that verify the
   mixin scope.
5. **v8 bean conversion is manual and brittle.** Every v8 implementation manually copies fields
   between v7 and v8 bean types. There is no compile-time safety: adding a field to v7 beans without
   updating the v8 converters will silently drop the field. *Mitigation:* share bean classes between
   v7 and v8 (if schemas are identical), or use a compile-time mapping library like MapStruct.
6. **Mode storage lacks type safety.** Mode is stored as a raw string in the `DynamicConfigProperty`
   system. Invalid mode values can be written directly to storage without validation (the
   validation in `validateMode()` only runs on API requests, not direct storage writes). Consider
   promoting mode to a first-class storage entity.
7. **Modes are not enforced** ([#11124](https://github.com/Apicurio/apicurio-registry/issues/11124)).
8. **Error responses that differ from Confluent** ([#11125](https://github.com/Apicurio/apicurio-registry/issues/11125)):
   `40405`, `40406` and `40407` return 409 where Confluent returns 404; the incompatibility message
   includes the Java exception class name; with group-concat, a subject without the separator
   returns 400 with an empty body.
9. **Single remaining harness test failure.** `lookupVersion_unregisteredSchema` fails: Confluent
   returns 405 (Method Not Allowed) when a non-matching schema is POSTed to
   `POST /subjects/{subject}/versions/{version}`. Apicurio returns 404 (Schema Not Found). This
   appears to be a Confluent-specific edge case where 405 may indicate the endpoint doesn't validate
   the body for non-matching schemas.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Separate v7 and v8 implementations | Not selected: near-identical APIs would duplicate logic and drift. The cost of delegation is manual bean conversion (limitation 5). |
| `globalId` as the default schema ID | Kept as opt-in legacy ID mode: it gives the same content different IDs, which breaks Confluent client expectations. |
| No default compatibility level on the check endpoint | Not selected: every schema would be reported compatible when no rule exists. The cost is the check/registration inconsistency (limitation 2). |
| Global `NON_NULL` on the ObjectMapper | Rejected: it silently dropped null-valued labels in the v3 API. |
| `mode` as an OpenAPI enum | Rejected: JAX-RS returns 400 before the handler runs, instead of Confluent's 422 error code. |
| Preserve the submitted Avro formatting | Not selected: harness comparisons need Confluent's compact form. The cost is per-read parsing (limitation 1). |

## Consequences

- **Benefits:** existing Confluent clients, Kafka Connect and ksqlDB work unchanged; harness
  conformance rose from 49% to 98%; one implementation serves both API versions.
- **Costs:** v8 converters must be kept in sync by hand; Avro parsing on every read; check and
  registration can disagree when no rule is configured; per-subject locking is best-effort across
  replicas.

## Example and verification

- **Example:** [`examples/confluent-compatibility`](../examples/confluent-compatibility/):
  `./demo.sh` runs Confluent's console producers and consumers (Avro, Protobuf, JSON Schema) against
  the v7 and v8 APIs, and checks the wire format and IDs, subject and group mapping, references,
  compatibility, soft and permanent deletes, errors, and the v7/v8 differences. CI runs it. The
  `confluent` step of [`examples/kafka-serdes`](../examples/kafka-serdes/) mixes Confluent's and
  Apicurio's Avro serdes, both ways.
- **User documentation:**
  [`assembly-confluent-schema-registry-compatibility.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-confluent-schema-registry-compatibility.adoc).

[Apicurio Compatibility Harness](https://github.com/Apicurio/apicurio-compatibility-harness)
results, before 26/53 (49%), after 52/53 (98%):

| Category | Tests | Pass | Fail |
|----------|-------|------|------|
| Subjects | 23 | 22 | 1 |
| Schemas | 7 | 7 | 0 |
| Compatibility | 3 | 3 | 0 |
| Config | 7 | 7 | 0 |
| Mode | 4 | 4 | 0 |
| Error Codes | 7 | 7 | 0 |
| Spec Examples | 2 | 2 | 0 |

| Concern | Tests to read |
|---|---|
| Canonical-hash lookups | `CCompatCanonicalModeTest` |
| Compatibility configuration | `CCompatCompatibilityConfigTest` |
| Confluent serdes against Apicurio | `ConfluentSerdeTest` |

```bash
./mvnw test -pl app -Dtest='CCompat*Test,ConfluentSerdeTest'
```

## History

| Milestone | Result |
|---|---|
| [#7971](https://github.com/Apicurio/apicurio-registry/pull/7971), [#7974](https://github.com/Apicurio/apicurio-registry/pull/7974), [#7985](https://github.com/Apicurio/apicurio-registry/pull/7985) | Harness conformance raised from 26/53 to 52/53 |
| This ADR reformatted to [the ADR template](TEMPLATE.md) | Structure only; no change to the decisions |
| Subject mapping, deletes and v7/v8 documented | Decisions 7 to 9 recorded; a verified example; mode and error-status gaps filed (#11124, #11125) |
