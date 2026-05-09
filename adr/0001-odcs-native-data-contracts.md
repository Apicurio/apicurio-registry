# ADR-0001: ODCS-Native Data Contracts

**Date:** 2026-05-08  
**Status:** Accepted  
**Deciders:** Carles Arnal  
**Issue:** [#6192](https://github.com/Apicurio/apicurio-registry/issues/6192)

## Context

Apicurio Registry needs a Data Contracts framework that lets teams define formal agreements about schema structure, ownership, quality, and governance. The framework must integrate with the existing schema registry infrastructure without requiring new storage backends.

The industry has converged on the Open Data Contract Standard (ODCS) v3.1, maintained by the Bitol project under the Linux Foundation. Confluent uses a proprietary contract format. No schema registry natively supports ODCS.

Phase 1 of the Data Contracts framework shipped a foundation: `contract.*` label namespace, contract rule storage (`contract_rules` table), and REST API endpoints for metadata and rulesets. Phase 2-3 builds on this to add ODCS support and field-level tagging.

## Decision

### Adopt ODCS v3.1 as the native contract format

ODCS contracts are submitted as YAML documents and their contents are **projected** onto referenced schema artifacts. The ODCS YAML is the developer-facing format; the existing label/rule infrastructure is the enforcement layer.

### Projection model

When an ODCS contract is submitted:

```
ODCS YAML
    │
    ├─► Stored as ODCS_CONTRACT artifact (source of truth)
    │
    └─► Projected onto each referenced schema artifact:
         ├── info/team/serviceLevel → contract.* artifact labels
         ├── quality.accuracy rules → CEL contract rules (odcs: prefixed)
         ├── quality.freshness/completeness → contract.quality.* labels
         ├── schemas.fields.pii/tags → field-tag.*|* version labels
         └── governance/sla → contract.* labels (stored, not enforced)
```

### Field-level tags as version labels

Field tags use version-level labels with a `|` separator between field path and tag name:

```
field-tag.{fieldPath}|{tagName} = {source}
```

Examples: `field-tag.ssn|PII = INLINE`, `field-tag.customerEmail|EMAIL = EXTERNAL`

The `|` separator prevents ambiguity with dots in field paths (e.g., `profile.address.zipCode`). Tags are either `INLINE` (extracted from schema content by tag extractors) or `EXTERNAL` (projected from ODCS contracts or set via API).

### Tag extractors for Avro, JSON Schema, Protobuf

Each schema format has a `TagExtractor` implementation:

- **Avro:** reads `tags` and `confluent:tags` field properties
- **JSON Schema:** reads `x-tags` and `x-confluent-tags` extensions; handles nested objects, arrays, `allOf`/`oneOf`/`anyOf`, and local `$ref` resolution
- **Protobuf:** reads `@tag:` documentation comments and Confluent `field_meta` options

### Contract-to-schema references

The `OdcsContractReferenceFinder` extracts schema references from the `schemas[].location` field in the ODCS YAML. Schema locations follow the format `groupId/artifactId:version`.

### REST API (OpenAPI-driven)

All endpoints are defined in the OpenAPI spec and generated into the `GroupsResource` interface:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/groups/{g}/contracts` | Submit ODCS YAML, create artifact, project |
| GET | `/groups/{g}/contracts?limit=&offset=` | List contracts (paginated) |
| GET | `/groups/{g}/contracts/{id}` | Retrieve ODCS YAML |
| PUT | `/groups/{g}/contracts/{id}` | Update contract, re-project |
| DELETE | `/groups/{g}/contracts/{id}` | Delete contract |
| GET | `/groups/{g}/artifacts/{a}/contract/export` | Export artifact state as ODCS YAML |

### Concurrency control

The projection engine uses a per-artifact `synchronized` lock (via `ConcurrentHashMap`) to prevent concurrent projections from corrupting labels on the same schema artifact.

### Unknown ODCS fields preservation

`OdcsContract`, `OdcsInfo`, and `OdcsSchema` use `@JsonAnySetter`/`@JsonAnyGetter` to capture ODCS fields not in our model (e.g., `servers`, `terms`, `roles`). These fields are preserved on round-trip through parse → serialize.

## Alternatives Considered

### Alternative 1: Custom contract format (rejected)

Design a bespoke contract format specific to Apicurio.

**Rejected because:**
- No industry adoption — users must learn a new format
- No ecosystem tooling (vs. ODCS which has `datacontract-cli`)
- Confluent already has a proprietary format — Apicurio should differentiate with the standard, not compete with another proprietary one
- The Phase 1 label/rule model is preserved as the internal representation

### Alternative 2: Contract-first with no projection (deferred)

Store the ODCS artifact as the sole source of truth. Evaluate rules dynamically from the contract at enforcement time, without projecting anything onto schema artifacts.

**Deferred because:**
- Requires significant rule engine refactoring to read from contracts instead of artifact labels
- Higher latency at enforcement time (contract lookup on every schema operation)
- Breaks the existing Phase 1 model which is label-based

**Recommendation:** Consider for a future major version where breaking changes are acceptable. This is the cleanest long-term architecture.

### Alternative 3: Dedicated field_tags table (rejected)

Store field tags in a separate SQL table (`field_tags`) with `globalId`, `fieldPath`, `tagName`, `tagSource` columns.

**Rejected because:**
- Requires new storage infrastructure (table, repository, SQL statements, KafkaSQL messages, read-only decorators)
- Labels provide the same functionality with zero new code
- Labels work across all storage variants (SQL, KafkaSQL, GitOps, KubernetesOps) for free

### Alternative 4: Separate Maven module for contracts (partially adopted)

Extract the contract rule execution engine to a separate `contracts-rules` module.

**Partially adopted:** Phase 5 (runtime rules) extracts the rule execution engine to `apicurio-registry-contracts-rules` because it needs to be reusable by the SerDes module. The ODCS parser, projectors, and exporter remain in `app` because they depend on `RegistryStorage`.

## Known Limitations

### L1: Multiple contracts per schema — RESOLVED

~~The projection model assumes a 1:1 relationship between contracts and schema artifacts.~~

**Resolution:** Namespaced projection implemented. All projected labels, rules, and tags are prefixed with the contract ID:
- Labels: `contract.{contractId}.owner` instead of `contract.owner`
- Rules: `odcs:{contractId}:ruleName` instead of `odcs:ruleName`
- Tags: `field-tag.{contractId}:fieldPath|tagName` instead of `field-tag.fieldPath|tagName`

Multiple contracts can now coexist on the same schema artifact without overwriting each other. Each projection only strips/replaces its own namespaced data.

### L2: Multi-node KafkaSQL eventual consistency

The projection engine performs read-modify-write on schema artifact labels. The per-artifact `synchronized` lock prevents corruption on a single JVM, but in multi-node KafkaSQL deployments, concurrent projections from different nodes can race.

**Impact:** Low — contract submissions targeting the same schema from different nodes simultaneously is unlikely in practice.

**Mitigation:** The ODCS contract YAML (stored as an artifact) is the source of truth. Re-submitting the contract corrects the projected state. For strict consistency, use the SQL storage variant.

### L3: Dual storage divergence — PARTIALLY RESOLVED

The ODCS YAML exists as an artifact AND its contents are projected as labels/rules on the schema artifact.

**Resolution:** If projection fails after contract artifact creation, the contract artifact is automatically rolled back (deleted). This prevents the "contract exists but projection is missing" state.

**Remaining risk:** Someone modifying schema artifact labels directly (bypassing the contract) can still cause divergence. The ODCS artifact remains the authoritative source — re-submitting the contract corrects the projected state.

### L4: ODCS model coverage — PARTIALLY RESOLVED

~~The ODCS model covers the core sections but does not model every ODCS v3.1 field.~~

**Resolution:** Added explicit fields for `terms`, `roles`, `servers`, `links`, and `tags` to `OdcsContract`. Combined with `@JsonAnySetter`/`@JsonAnyGetter`, all ODCS v3.1 fields are now preserved on round-trip. Fields not in the model are captured in `additionalProperties`.

### L5: Label key size limits — RESOLVED

~~Field tag labels can produce long keys. No validation is currently applied.~~

**Resolution:** `OdcsTagProjector` now validates label key length against a `MAX_LABEL_KEY_LENGTH` (512 chars). Tags that would exceed this limit are skipped with a warning in the projection result instead of causing storage errors.

## Consequences

### Positive

- Apicurio becomes the first schema registry to natively support ODCS
- Zero new storage tables — reuses existing label and rule infrastructure
- Works across all storage variants (SQL, KafkaSQL, GitOps, KubernetesOps)
- `datacontract-cli` and other ODCS tooling can interoperate via the REST API
- Feature-gated behind `apicurio.contracts.enabled` (experimental) — no risk to existing users
- Existing Phase 1 contract metadata/rules continue to work unchanged

### Negative

- Dual storage (ODCS artifact + projected labels) adds complexity
- 1:1 contract-to-schema limitation constrains multi-team governance
- Multi-node KafkaSQL consistency is eventual, not strict
- ODCS model coverage is partial (core sections only)

### Neutral

- The projection model is evolvable — namespaced projection and contract-first approaches can be adopted incrementally
- Tag extractors are extensible via the `TagExtractor` SPI — new formats can be added without modifying existing code
