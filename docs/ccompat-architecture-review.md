# Confluent Compatibility Layer — Architecture Review

**Date:** 2026-05-09
**Scope:** PRs #7971, #7974, #7985 (Confluent Schema Registry compatibility improvements)
**Harness results:** 52/53 tests passing (up from 26/53)

---

## Critical Issues

### 1. Avro schema compaction on every GET request

`ApiConverter.compactIfAvro()` parses and re-serializes every Avro schema via `new Schema.Parser().parse(content).toString()` on every read response.

**Problems:**
- **Performance:** Avro parsing is expensive. This runs on every GET for schema content — will degrade under load.
- **Loss of fidelity:** The original schema the user submitted (formatting, key ordering, whitespace) is lost. Clients may not recognize their own schema.
- **Type confusion:** If `artifactType` is null (possible in `convert(ContentHandle, String, List)`), the check is skipped. But non-Avro schemas that happen to be valid Avro JSON could be silently transformed.
- **Error swallowing:** Parse failures are caught and the original content returned, masking invalid schemas in storage.

**How Confluent works:** Schemas are stored as-submitted and returned as-is. Confluent does not compact on read.

**Recommendation:** Remove compaction from `ApiConverter`. If normalization is needed for deduplication, do it once at registration time (write path), not on every read. Alternatively, support it only via an explicit `?format=canonical` query parameter.

**Files:** `app/src/main/java/io/apicurio/registry/ccompat/rest/v7/impl/ApiConverter.java` (lines 69-79)

---

### 2. Default BACKWARD compatibility inconsistency

When no compatibility rule is configured at any level (artifact, group, global), two code paths behave differently:

| Path | Behavior | File |
|------|----------|------|
| Compatibility check (`/compatibility/subjects/...`) | Defaults to BACKWARD | `CompatibilityResourceImpl.java` lines 97-102 |
| Schema registration (`POST /subjects/{subject}/versions`) | No compatibility enforcement | `SubjectsResourceImpl.registerNewSchemaVersion()` |

**Impact:** The check endpoint tells clients "this schema is backward-compatible" but registration doesn't actually enforce backward compatibility. A client could:
1. Check compatibility → passes (BACKWARD enforced)
2. Register schema → succeeds even if incompatible (no rule enforced)
3. Consumer fails at deserialization time

**How Confluent works:** BACKWARD is the global default. Both check and registration enforce it unless explicitly changed.

**Recommendation:** Either:
- Make BACKWARD the global default in `RulesProperties.getDefaultGlobalRuleConfiguration()` so both paths use it consistently
- Or remove the hardcoded default from the check endpoint and return 404 (`SUBJECT_COMPATIBILITY_NOT_CONFIGURED`) when no rule exists

**Files:** `CompatibilityResourceImpl.java`, `SubjectsResourceImpl.java`

---

## Major Issues

### 3. Jackson mixin applied to global ObjectMapper

`CCompatJacksonCustomizer` applies `@JsonInclude(NON_NULL)` to specific ccompat bean classes via the global `ObjectMapper`:

```java
mapper.addMixIn(io.apicurio.registry.ccompat.rest.v7.beans.Schema.class, NonNullMixin.class);
```

**Risk:** The mixin affects serialization of these bean types everywhere in the application, not just ccompat endpoints. Currently safe because these beans are only used in ccompat contexts, but fragile:
- If someone adds a field of type `v7.beans.Schema` to a v3 DTO, it inherits `NON_NULL` silently
- If beans are refactored/moved, the mixin stops working with no compile error
- Subclasses are not covered unless explicitly registered

**Better approach:** A JAX-RS `WriterInterceptor` scoped to `/apis/ccompat/*` paths, or a custom `ObjectMapper` per ccompat endpoints.

**Files:** `app/src/main/java/io/apicurio/registry/ccompat/rest/CCompatJacksonCustomizer.java`

---

### 4. v8 delegation with manual bean conversion

v8 implementations delegate to v7 and manually convert between bean types field by field:

```java
v7Request.setSchema(data.getSchema());
v7Request.setSchemaType(data.getSchemaType());
// ... every field copied manually ...
```

There are 7+ converter call sites in `SubjectsResourceImpl` alone, plus more in `ConfigResourceImpl`. Adding a new field to v7 beans requires updating every converter — easy to miss, no compile-time safety.

**Alternatives:**
- Share bean classes between v7 and v8 (if schemas are identical)
- Use MapStruct for type-safe compile-time mapping
- Accept code duplication in v8 (cleaner than brittle converters)

**Files:** `app/src/main/java/io/apicurio/registry/ccompat/rest/v8/impl/SubjectsResourceImpl.java`, `ConfigResourceImpl.java`

---

### 5. Subject locks are per-JVM only

`SubjectsResourceImpl` uses a Guava `Cache<GA, Lock>` for per-subject locking during schema registration. This only protects against races within a single JVM instance.

In multi-instance deployments (common in production), two instances can concurrently register schemas for the same subject, bypassing the lock entirely. The storage layer may handle this via optimistic locking or unique constraints, but the application-level lock provides a false sense of safety.

**Note:** This is a pre-existing issue, not introduced by these PRs.

**Options:**
- Database-level advisory locks (for SQL storage)
- Document single-writer requirement (like Confluent's architecture)
- Remove the lock if storage already handles concurrency

**Files:** `SubjectsResourceImpl.java` lines 72-76

---

## Minor Issues

### 6. Error messages are hardcoded strings

Error messages in `CCompatExceptionMapperService.toConfluentMessage()` are built via string concatenation. No constants, no message bundle. Acceptable for a Confluent compatibility layer (English-only, well-defined messages), but not best practice.

### 7. Mode stored as DynamicConfigProperty

Mode is stored as a raw key-value string (`apicurio.ccompat.mode`). No type safety at the storage level — invalid mode strings can be written directly to storage without validation. Acceptable for now, but should be promoted to a first-class storage entity if mode management grows in complexity.

### 8. lookupSchemaByVersion does unnecessary work

The endpoint calls `lookupSchema()` (full content-based search across all versions) to verify the posted schema matches the requested version. It would be more efficient to fetch the version's schema directly and compare content/hashes, since the version is already known.

---

## What's Done Well

- **Error handling structure:** Custom ccompat exceptions map cleanly to Confluent error codes via `CONFLUENT_CODE_MAP`. Adding new error types is straightforward.
- **Format transformation isolation:** `SchemaFormatService` cleanly separates format concerns from resource logic.
- **Authorization consistency:** All endpoints have proper `@Authorized` annotations.
- **v8 delegation avoids logic duplication:** Despite the converter brittleness, the pattern ensures v7 and v8 behavior stays in sync.
- **Compatibility harness integration:** Testing against a real Confluent instance via the compatibility harness is the right approach for validating compatibility.

---

## Action Items

| Priority | Issue | Action |
|----------|-------|--------|
| Critical | Avro compaction on read | Move to write-time or remove; add `?format=canonical` if needed |
| Critical | BACKWARD default inconsistency | Make default consistent between check and registration |
| Major | Jackson mixin scope | Scope to ccompat paths or document/test current behavior |
| Major | v8 converter brittleness | Evaluate shared beans or MapStruct |
| Low | Per-JVM locks | Document limitation or implement distributed locking |
| Low | Hardcoded error messages | Extract to constants |
