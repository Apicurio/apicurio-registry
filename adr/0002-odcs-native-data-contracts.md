# ADR-0002: ODCS-Native Data Contracts

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
| GET | `/groups/{g}/artifacts/{a}/contract/metadata` | Get contract metadata (from labels) |
| PUT | `/groups/{g}/artifacts/{a}/contract/metadata` | Update contract metadata (stored as labels) |
| POST | `/groups/{g}/artifacts/{a}/contract/status` | Transition lifecycle status (DRAFT→STABLE→DEPRECATED) |
| GET | `/groups/{g}/artifacts/{a}/contract/ruleset` | Get artifact-level contract ruleset |
| PUT | `/groups/{g}/artifacts/{a}/contract/ruleset` | Set artifact-level contract ruleset |
| DELETE | `/groups/{g}/artifacts/{a}/contract/ruleset` | Delete artifact-level contract ruleset |
| GET | `/groups/{g}/artifacts/{a}/contract/export` | Export artifact state as ODCS YAML |
| POST | `/groups/{g}/artifacts/{a}/contract/promote` | Promote contract stage (DEV→STAGE→PROD) |
| GET | `/groups/{g}/artifacts/{a}/contract/quality?contractId=` | Get quality score |
| POST | `/groups/{g}/artifacts/{a}/versions/{v}/contract/execute` | Execute contract rules against a data record |
| POST | `/groups/{g}/artifacts/{a}/contract/migrate` | Migrate a record between versions using migration rules |
| GET | `/groups/{g}/artifacts/{a}/contract/audit` | Get contract audit log entries |
| GET | `/groups/{g}/artifacts/{a}/contract/compatibility-group` | Get compatibility group |
| PUT | `/groups/{g}/artifacts/{a}/contract/compatibility-group` | Set compatibility group |
| GET | `/search/contracts` | Search contracts by status, ownerTeam, compatibilityGroup |
| GET | `/admin/contracts/ruleset` | Get global contract ruleset |
| PUT | `/admin/contracts/ruleset` | Set global contract ruleset |
| DELETE | `/admin/contracts/ruleset` | Delete global contract ruleset |

### Governance (Phase 4)

`GovernanceRuleExecutor` checks contract metadata completeness using namespaced labels. Three levels: NONE, BASIC (owner required, deprecated blocked), FULL (+ classification, contact, PROD/STABLE). `PromotionService` validates DEV→STAGE→PROD transitions with per-artifact locking. `QualityScoreCalculator` computes weighted scores (completeness 30%, compliance 40%, stability 30%).

### Runtime rule execution (Phase 5)

The `contracts-rules` Maven module (`apicurio-registry-contracts-rules`) provides a storage-free rule execution engine reusable by both `app` and SerDes modules. Uses real CEL via `org.projectnessie.cel:cel-standalone:0.6.0` with AST caching and dynamic variable declaration. The `RuleExecutionEngine` filters rules by mode, sorts by order, executes CONDITION rules (ERROR stops, DLQ continues), and chains TRANSFORM outputs.

`RuleExecutionService` in `app` loads rules from storage, maps `ContractRuleDto` → `RuleDefinition` (string-based types to avoid enum duplication), and delegates to the engine. Global contract rules (stored via `AdminResource`) are merged with artifact-level and version-level rules at execution time, with version rules taking precedence over artifact rules, which take precedence over global rules (matched by rule name).

### JSONata transform engine (Phase 6)

The `contracts-rules` module includes a JSONata rule executor (`JsonataRuleExecutor`) using `com.dashjoin:jsonata:0.9.9`. JSONata expressions support both CONDITION and TRANSFORM kinds:

- **CONDITION:** evaluates the expression and checks for a truthy result
- **TRANSFORM:** evaluates the expression and returns the transformed data as the new record

`JsonataExpressionEvaluator` provides LRU caching (capacity 1000) of parsed JSONata expressions. Expressions are compiled once and reused.

### Migration rule orchestration (Phase 6)

`MigrationRuleService` computes version-to-version migration paths by chaining UPGRADE or DOWNGRADE migration rules. Given a source version and target version:

1. Determines direction (upgrade or downgrade) by comparing version ordering
2. Loads migration rules for each intermediate version
3. Chains transforms sequentially: v1→v2→v3 applies v2's UPGRADE rules then v3's UPGRADE rules
4. Returns the transformed record or reports violations

The migration endpoint (`POST /groups/{g}/artifacts/{a}/contract/migrate`) accepts a source version, target version, and record, returning the transformed result.

### Compatibility group support (Phase 6)

`CompatibilityGroupService` manages compatibility groups via artifact labels (`contract.{id}.compatibility.group`). Compatibility groups partition the version history for compatibility checking — versions in different groups are not compared against each other. Label writes use exact key as the merge prefix to avoid wiping other contract labels.

### Contract events (Phase 7)

Three `OutboxEvent` implementations publish contract lifecycle events:

- `ContractRulesetConfigured` — fired on ruleset CRUD operations
- `ContractMetadataUpdated` — fired on metadata changes
- `ContractStatusChanged` — fired on lifecycle transitions

Events are published via the existing `OutboxEvent` infrastructure (Debezium outbox pattern in KafkaSQL, in-process in SQL mode). The `StorageEventType` enum includes `CONTRACT_RULESET_CONFIGURED`, `CONTRACT_METADATA_UPDATED`, and `CONTRACT_STATUS_CHANGED`.

### Contract audit log (Phase 7)

`ContractAuditService` records contract operations in a `contract_audit_log` SQL table (DB upgrade 106). Each entry includes: `groupId`, `artifactId`, `version` (nullable), `action` (e.g., `METADATA_UPDATED`, `RULESET_CONFIGURED`, `STATUS_TRANSITION`), `principal`, `details` (human-readable description), and `createdOn` timestamp. Indexed on `(groupId, artifactId)` and `(createdOn)`.

`SqlContractAuditRepository` provides JDBC persistence. The REST endpoint `GET /groups/{g}/artifacts/{a}/contract/audit` returns audit entries as a JSON array.

### Contract search (Phase 7)

`SearchResource` exposes `GET /search/contracts` with query parameters: `status`, `ownerTeam`, `compatibilityGroup`, `limit`, `offset`, `orderby`, `order`. Searches across artifact labels matching the `contract.*` namespace using prefix-based label filters.

### Java SerDes rule integration (Phase 7)

`AbstractSerializer` and `AbstractDeserializer` integrate contract rule execution into the normal Kafka serialize/deserialize path:

- **Serializer:** after schema resolution and before serialization, calls `executeContractRules` with mode `WRITE` if `apicurio.registry.serde.contract-rules.enabled=true`
- **Deserializer:** after deserialization, calls `executeContractRules` with mode `READ`
- **Failure handling:** controlled by `apicurio.registry.serde.contract-rules.fail-on-error` (default `true`). When `false`, violations are logged but serialization proceeds (for DLQ routing by the application)
- **Record conversion:** `dataToMap()` converts GenericRecord/SpecificRecord to `Map<String, Object>` via JSON parsing of `toString()` output

`RegistryClientFacadeImpl.executeContractRules` calls the server-side rule execution endpoint via `java.net.http.HttpClient` (not the Kiota SDK, which doesn't support the execute endpoint's dynamic request/response shape). The `baseUrl` is extracted from the `SerdeConfig.REGISTRY_URL` property.

Contract rules are disabled by default (`apicurio.registry.serde.contract-rules.enabled` defaults to `false`). Users opt in explicitly.

### Global contract rules (Phase 8)

Global contract rulesets apply to all artifacts unless overridden. CRUD via `AdminResource`:

- `GET /admin/contracts/ruleset` — get the global ruleset
- `PUT /admin/contracts/ruleset` — set the global ruleset
- `DELETE /admin/contracts/ruleset` — delete the global ruleset

Global rules are stored as JSON in the `properties` table with key `apicurio.contracts.global.ruleset`. At execution time, `RuleExecutionService` merges global → artifact → version rules, with more specific scopes overriding global rules by name.

### UI contract management (Phase 8)

The artifact detail page includes a "Contract" tab (`ArtifactContractTabContent.tsx`) showing:

- **Metadata panel:** owner team, domain, classification, status badge, support contact
- **Quality scores:** overall/completeness/compliance/stability with color-coded progress bars
- **Rules table:** domain and migration rules with name, kind, type, mode, expression, and failure action
- **ODCS editor:** view/edit the ODCS YAML source with submit capability
- **Audit log:** chronological list of contract operations
- **Status transition modal:** change lifecycle status (DRAFT→STABLE→DEPRECATED)
- **Promotion modal:** promote contract stage (DEV→STAGE→PROD)

Uses PatternFly React components. `useContractsService.ts` provides the API client layer.

### Concurrency control

Label writes use atomic `mergeArtifactLabels`/`mergeVersionLabels` operations scoped to a prefix. In KafkaSQL mode, these operations are routed through the Kafka journal like all other writes, ensuring multi-node consistency.

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

### L2: Multi-node KafkaSQL consistency — RESOLVED

~~The projection engine performs read-modify-write on schema artifact labels, causing eventual consistency in KafkaSQL.~~

**Resolution:** `mergeArtifactLabels` and `mergeVersionLabels` now have dedicated KafkaSQL messages (`MergeArtifactLabels4Message`, `MergeVersionLabels5Message`). All label writes go through the Kafka journal, ensuring the same consistency guarantees as other storage operations.

### L3: Dual storage divergence — PARTIALLY RESOLVED

The ODCS YAML exists as an artifact AND its contents are projected as labels/rules on the schema artifact.

**Resolution:** If projection fails after contract artifact creation, the contract artifact is automatically rolled back (deleted). This prevents the "contract exists but projection is missing" state.

**Remaining risk:** Someone modifying schema artifact labels directly (bypassing the contract) can still cause divergence. The ODCS artifact remains the authoritative source — re-submitting the contract corrects the projected state.

### L4: ODCS model coverage — RESOLVED

~~The ODCS model covers the core sections but does not model every ODCS v3.1 field.~~

**Resolution:** All ODCS v3.1 top-level fields are now explicitly modeled in `OdcsContract`: `customProperties`, `support`, `price`, `slaProperties`, `authoritativeDefinitions`, `tenant`, `dataProduct`, `contractCreatedTs`, `domain`, `version`, `status`, `description`, `schema` (ODCS v3.1 alternate to `schemas`). `@JsonAnySetter`/`@JsonAnyGetter` is retained only as a safety net for future ODCS spec versions (v3.2+). Sub-objects (`OdcsTeam`, `OdcsFieldMetadata`) also preserve unknown fields via `@JsonAnySetter` instead of silently dropping them.

### L5: Label key size limits — RESOLVED

~~Field tag labels can produce long keys. No validation is currently applied.~~

**Resolution:** `OdcsTagProjector` validates label key length against `MAX_LABEL_KEY_LENGTH` (512 chars). Tags that would exceed this limit are skipped with a warning in the projection result. The SQL storage layer enforces a 256-char limit on label keys via `VARCHAR(512)` columns (the index prefix limit is more restrictive on MySQL). Both layers reject instead of truncating.

### L6: CEL library ABI incompatibility with Confluent SerDes — OPEN

`cel-standalone:0.6.0` relocates protobuf types into its own package namespace, which is incompatible with Confluent's `kafka-schema-rules` library (used for Confluent SerDes CEL rule execution). Both libraries cannot coexist in the same classpath.

**Impact:** Confluent SerDes tests that use CEL rules fail at runtime with `ClassNotFoundException` for protobuf types.

**Mitigation options:**
- Migrate from `cel-standalone` to the non-relocated `cel-tools` + `cel-core` modules (requires managing protobuf dependency alignment)
- Use Confluent's CEL evaluator for ccompat rule execution paths
- Keep CEL implementations separate: Apicurio's CEL for native contracts, Confluent's for ccompat (Phase 9)

### L7: SerDes contract rule execution uses raw HTTP — RESOLVED

~~`RegistryClientFacadeImpl.executeContractRules` uses `java.net.http.HttpClient` directly instead of the Kiota-generated SDK client.~~

**Resolution:** Replaced raw HTTP with Kiota SDK calls. The SDK's `ExecutePostRequestBody`/`ExecutePostResponse` types use `AdditionalDataHolder` for the dynamic `record` and `violations` fields, which maps directly to `Map<String, Object>`. SerDes contract rule execution now benefits from the SDK's authentication, retry, and serialization infrastructure.

## Consequences

### Positive

- Apicurio becomes the first schema registry to natively support ODCS
- Zero new storage tables for core contracts — reuses existing label and rule infrastructure (one table added for audit log only)
- Works across all storage variants (SQL, KafkaSQL, GitOps, KubernetesOps)
- `datacontract-cli` and other ODCS tooling can interoperate via the REST API
- Always available — contracts are opt-in by nature (submitting a contract is an explicit action), so no feature gate is needed
- Existing Phase 1 contract metadata/rules continue to work unchanged
- Full SerDes integration — contract rules are enforced during normal Kafka serialize/deserialize operations via a simple configuration property
- End-to-end lifecycle — from ODCS YAML submission to CEL rule enforcement to schema migration transforms
- UI management — contracts can be viewed, edited, promoted, and monitored from the registry UI

### Negative

- Dual storage (ODCS artifact + projected labels) adds complexity — justified for versioning, export, and re-projection
- CEL library conflict prevents coexistence with Confluent's `kafka-schema-rules`

### Neutral

- The projection model is evolvable — namespaced projection and contract-first approaches can be adopted incrementally
- Tag extractors are extensible via the `TagExtractor` SPI — new formats can be added without modifying existing code
- JSONata transforms complement CEL — CEL for validation (CONDITION), JSONata for data transformation (TRANSFORM)
- Global rules provide a baseline that can be overridden at artifact or version level
