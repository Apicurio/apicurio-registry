# ADR-0001: Confluent Schema Registry Compatibility Layer

**Status:** Accepted
**Date:** 2026-05-09
**PRs:** #7971, #7974, #7985

---

## Context

Apicurio Registry provides a Confluent Schema Registry-compatible API so that existing Confluent clients, Kafka Connect, and ksqlDB can use Apicurio as a drop-in replacement. The compatibility layer exposes the Confluent v7 and v8 REST API at `/apis/ccompat/v7/` and `/apis/ccompat/v8/`.

The [Apicurio Compatibility Harness](https://github.com/Apicurio/apicurio-compatibility-harness) runs the same requests against both Confluent and Apicurio and compares responses. Before these changes, 26 of 53 tests passed (49%). After, 52 of 53 pass (98%).

---

## Design Overview

### API Layering

```
/apis/ccompat/v7/*  -->  v7 Resource Implementations (core logic)
/apis/ccompat/v8/*  -->  v8 Resource Implementations (delegate to v7)
```

All business logic lives in the v7 implementations. The v8 layer is a thin delegation wrapper that converts between v7 and v8 bean types (generated from separate OpenAPI specs). Both specs are maintained in `app/src/main/resources-unfiltered/META-INF/resources/api-specifications/ccompat/`.

### Endpoint Coverage

| Resource | Endpoints |
|----------|-----------|
| Subjects | CRUD for subjects and versions, schema lookup by content, lookup by version |
| Schemas | Lookup by ID, list subjects/versions by schema ID, list schema types |
| Compatibility | Check schema compatibility against a version or all versions |
| Config | Get/set global and subject-level compatibility level |
| Mode | Get/set global and subject-level read/write mode |
| Contexts | Returns static single context (contexts not supported) |

### ID Mapping

Apicurio has two internal ID types: `globalId` (unique per artifact version) and `contentId` (unique per content hash). Confluent uses a single incrementing `schema ID`.

The mapping is controlled by `apicurio.ccompat.legacy-id-mode.enabled`:
- **`false` (default):** Confluent `id` maps to Apicurio `contentId`. Same schema content across subjects returns the same ID. Matches Confluent behavior for most use cases.
- **`true`:** Confluent `id` maps to Apicurio `globalId`. Each registration gets a unique ID regardless of content.

### Error Handling

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
| `SchemaSoftDeletedException` | 40406 | 404 |
| `SubjectSoftDeletedException` | 40404 | 404 |

Error messages are formatted to match Confluent's style (e.g., `"Subject 'foo' not found."`).

### Schema Registration

Schema registration uses per-subject locking via a Guava `Cache<GA, ReentrantLock>` to serialize concurrent writes to the same subject within a JVM. The flow:

1. Acquire per-subject lock
2. Try to find an existing version with matching content (`lookupSchema`)
3. If found and active, return existing ID
4. If not found, create or update the artifact via `createOrUpdateArtifact`
5. Release lock

### Compatibility Checking

When a client calls the compatibility check endpoint and no compatibility rule is configured at any level (artifact, group, global, default), the implementation defaults to `BACKWARD` -- matching Confluent's default behavior.

### JSON Serialization

Ccompat responses must omit null fields (e.g., `references` should not appear when empty). This is handled via Jackson mixins applied to `v7.beans.Schema` and `v8.beans.Schema` classes through `CCompatJacksonCustomizer`, which adds `@JsonInclude(NON_NULL)` behavior.

Avro schemas are compacted to canonical single-line JSON in ccompat responses via `ApiConverter.compactIfAvro()`, which parses and re-serializes through Apache Avro's `Schema.Parser`.

### Mode Management

Read/write mode is stored as a `DynamicConfigProperty` string (`apicurio.ccompat.mode` for global, `apicurio.ccompat.mode.subject.<groupId>/<artifactId>` for per-subject). Valid modes: `READWRITE`, `READONLY`, `IMPORT`.

---

## Key Decisions

### 1. v8 delegates to v7

**Decision:** v8 implementations are thin wrappers that convert beans and delegate to v7.

**Rationale:** v7 and v8 APIs are nearly identical. Duplicating logic would create maintenance burden. The delegation pattern ensures behavioral consistency.

**Trade-off:** Manual bean conversion between v7 and v8 types is required. Adding a new field to a v7 bean requires updating every converter method in the v8 layer.

### 2. contentId as default Confluent schema ID

**Decision:** Map Confluent's `id` field to Apicurio's `contentId` by default.

**Rationale:** Confluent assigns the same schema ID when identical content is registered under different subjects. Apicurio's `contentId` provides this behavior naturally since it's based on content hash. `globalId` would assign different IDs for the same content, breaking Confluent client expectations.

### 3. Default BACKWARD compatibility for check endpoint

**Decision:** When no compatibility rule exists, the compatibility check endpoint applies BACKWARD by default.

**Rationale:** Confluent defaults to BACKWARD globally. Without this, the check endpoint would return "compatible" for any schema (since no rule exists to violate), which is misleading.

### 4. Avro schema compaction on read

**Decision:** Avro schemas are parsed and re-serialized to compact JSON on every ccompat response.

**Rationale:** Confluent returns schemas in compact single-line JSON. The compatibility harness compares response bodies, so formatting differences cause test failures.

### 5. Jackson mixins for null suppression

**Decision:** Use `ObjectMapper.addMixIn()` to apply `@JsonInclude(NON_NULL)` to specific ccompat bean classes, rather than setting it globally on the ObjectMapper.

**Rationale:** A previous approach set `NON_NULL` globally, which broke the v3 API (labels with null values were silently dropped). Mixins scope the behavior to ccompat beans only.

### 6. OpenAPI spec changes for mode validation

**Decision:** Changed `ModeUpdateRequest.mode` from an enum to a plain string in the OpenAPI spec.

**Rationale:** With an enum, JAX-RS returns 400 (Bad Request) for invalid values before the handler runs. Confluent returns 422 with a specific error code. Using a plain string allows the handler to validate and return the correct Confluent-style error response.

---

## Known Limitations

### 1. Avro compaction on every read (performance)

`ApiConverter.compactIfAvro()` parses and re-serializes every Avro schema on every GET request. Avro parsing is not cheap. Under high read load, this will degrade performance. The original schema formatting (indentation, key ordering) submitted by the user is lost in ccompat responses.

**Mitigation options:**
- Move compaction to write time (normalize once at registration)
- Cache the compacted form alongside the original content
- Support compaction only via an explicit `?format=canonical` parameter

### 2. Default BACKWARD inconsistency between check and registration

The compatibility *check* endpoint defaults to BACKWARD when no rule is configured. But schema *registration* does NOT enforce BACKWARD -- it only applies rules if explicitly configured. A client could check compatibility (passes with BACKWARD default), then register an incompatible schema (no enforcement).

**Mitigation:** Make BACKWARD the global default in `RulesProperties` so both code paths use it, or remove the hardcoded default from the check endpoint.

### 3. Per-JVM subject locks do not protect multi-instance deployments

The `Cache<GA, ReentrantLock>` in `SubjectsResourceImpl` only serializes writes within a single JVM. In a multi-instance deployment, two instances can concurrently register schemas for the same subject. The storage layer (SQL unique constraints or Kafka topic ordering) provides the actual consistency guarantee.

**Mitigation:** Use database-level advisory locks, or document that the application-level lock is a best-effort optimization for single-instance deployments.

### 4. Jackson mixins applied to global ObjectMapper

The `CCompatJacksonCustomizer` applies `@JsonInclude(NON_NULL)` mixins to the global ObjectMapper. While the target bean classes are only used in ccompat contexts today, the pattern is fragile -- if these bean types are ever used in non-ccompat responses, null fields will be silently omitted.

**Mitigation:** Replace with a JAX-RS `WriterInterceptor` scoped to `/apis/ccompat/*` paths, or add integration tests that verify the mixin scope.

### 5. v8 bean conversion is manual and brittle

Every v8 implementation manually copies fields between v7 and v8 bean types. There is no compile-time safety -- adding a field to v7 beans without updating the v8 converters will silently drop the field.

**Mitigation:** Share bean classes between v7 and v8 (if schemas are identical), or use a compile-time mapping library like MapStruct.

### 6. Mode storage lacks type safety

Mode is stored as a raw string in the `DynamicConfigProperty` system. Invalid mode values can be written directly to storage without validation (the validation in `validateMode()` only runs on API requests, not direct storage writes).

### 7. Single remaining harness test failure

`lookupVersion_unregisteredSchema` fails: Confluent returns 405 (Method Not Allowed) when a non-matching schema is POSTed to `POST /subjects/{subject}/versions/{version}`. Apicurio returns 404 (Schema Not Found). This appears to be a Confluent-specific edge case where 405 may indicate the endpoint doesn't validate the body for non-matching schemas.

---

## Compatibility Harness Results

**Before:** 26/53 passing (49%)
**After:** 52/53 passing (98%)

| Category | Tests | Pass | Fail |
|----------|-------|------|------|
| Subjects | 23 | 22 | 1 |
| Schemas | 7 | 7 | 0 |
| Compatibility | 3 | 3 | 0 |
| Config | 7 | 7 | 0 |
| Mode | 4 | 4 | 0 |
| Error Codes | 7 | 7 | 0 |
| Spec Examples | 2 | 2 | 0 |

---

## Future Work

- Resolve Avro compaction performance issue (move to write-time or caching)
- Fix BACKWARD default inconsistency between check and registration
- Evaluate shared bean classes for v7/v8 to eliminate manual converters
- Investigate the `lookupVersion_unregisteredSchema` 405 vs 404 difference
- Add distributed locking for multi-instance deployments if needed
- Consider promoting mode to a first-class storage entity
