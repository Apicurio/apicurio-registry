# ADR-0002: ODCS-Native Data Contracts

- **Status:** Accepted; rewritten 2026-11-06 to describe the current design.
- **Decision date:** 2026-05-08.
- **Author:** Carles Arnal.
- **Tracking:** [#6192](https://github.com/Apicurio/apicurio-registry/issues/6192).
- **Scope:** How Registry stores data contracts, attaches them to schemas, and evaluates their rules.
  For usage, see the [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-data-contracts.adoc)
  and the [example and guide](../examples/odcs-data-contracts/GUIDE.md).

## Context

A data contract is an agreement about a dataset beyond its schema: who owns it, how sensitive its
fields are, what a valid record looks like, and what consumers can expect. Teams need to declare
contracts next to the schemas they govern, and producers need the contract's rules enforced before
data is published.

The industry has converged on the [Open Data Contract Standard](https://bitol-io.github.io/open-data-contract-standard/)
(ODCS) v3.1, maintained by the Bitol project under the Linux Foundation. Confluent uses a
proprietary format. Registry already had what enforcement needs: labels on artifacts and versions,
which every storage variant persists and search can filter on, and serializers that fetch metadata
from the registry. The design question was how to accept a standard document without building a
second metadata store beside those.

## Decision

1. **ODCS v3.1 YAML is the contract format.** A submitted contract is stored unchanged as an
   artifact of type `ODCS_CONTRACT`. That artifact is the source of truth, and it is versioned like
   any other artifact.
2. **The contract is projected onto the schemas it references.** Each `schemas[]` entry names a
   schema artifact. The projection writes the contract's facts onto it as artifact labels, its
   field metadata as version labels (field tags), and its quality rules as contract rules. Registry
   then works with those, and never re-reads the YAML at enforcement time.
3. **Everything projected is namespaced by contract ID**, so several contracts can govern one
   schema, and re-projecting one contract replaces only its own data.
4. **Contract rules are a separate rule system** from Registry's artifact rules (validity,
   compatibility). They validate and transform *data records*, not schema content, at three
   scopes (global, artifact, version), and are evaluated by an engine that has no storage
   dependency, so the server and the serializers run the same code.

## Design

### Overview

```
           ODCS YAML (POST/PUT /groups/{g}/contracts)
                         │
          ┌──────────────┴──────────────────────────┐
          ▼                                         ▼
  ODCS_CONTRACT artifact               projection onto each schemas[].location
  (source of truth, one                  ├─ artifact labels   contract.{id}.*
   version per info.version)             ├─ version labels    field-tag.{id}:{field}|{tag}
                                         └─ contract rules    odcs:{id}:{rule} (artifact scope)
                                                       │
            contract rules (global / artifact / version, contract_rules table)
                                                       │
                       RuleExecutionEngine (contracts-rules module: CEL, JSONata)
                ┌──────────────────────┼─────────────────────────┐
                ▼                      ▼                         ▼
       POST .../contract/execute   POST .../contract/migrate   Kafka serializer/deserializer
       (server, all scopes)        (server, version scope)     (client, artifact scope)
```

### The contract artifact

`POST /groups/{g}/contracts` parses the YAML (`OdcsParser`) and creates an `ODCS_CONTRACT`
artifact. Its ID is the contract `id`, falling back to a sanitized `info.title` and then to a
generated ID; its version is `info.version`. Submitting a new `info.version` of an existing
contract adds a version; `PUT /groups/{g}/contracts/{id}` does the same for an explicit ID.
Re-submitting an existing version fails with 409.

The `ODCS_CONTRACT` type is a regular artifact type: it has a content accepter, validator,
canonicalizer, and a reference finder (`OdcsContractReferenceFinder`) that reports each
`schemas[].location` as an external reference. The ODCS model (`OdcsContract` and its parts) types
the sections the projection reads and keeps every other field, so the stored YAML round-trips.

### Projection

`OdcsProjectionEngine` resolves each `schemas[].location` (`[groupId/]artifactId[:version]`; the
group defaults to the contract's group, the version to the latest) and runs three projectors on the
target. A location that does not resolve becomes a warning in the response, not an error.

| Projector | Source in the ODCS document | Written to |
|---|---|---|
| `OdcsLabelProjector` | `info.status` (draft → `DRAFT`, active → `STABLE`, deprecated/retired → `DEPRECATED`), `info.dataClassification`, `info.version`, `team.name`, `team.domain`, `team.contact`, `serviceLevel.availability`, `serviceLevel.latency.p50/p99`, `quality.freshness.maxStaleness`, `quality.completeness[]`, and the contract ID | Artifact labels `contract.{id}.status`, `.classification`, `.version`, `.owner.team`, `.owner.domain`, `.support.contact`, `.sla.*`, `.quality.*`, `.id` |
| `OdcsTagProjector` | Each field's `pii: true` (tag `PII`), `classification` (tag `CLASSIFICATION:<value>`) and `tags[]` | Labels on the target version: `field-tag.{id}:{fieldPath}\|{tag}` = `EXTERNAL` |
| `OdcsRuleProjector` | Each `quality.accuracy[]` entry | An artifact-level contract rule `odcs:{id}:{name}`: CONDITION, CEL, mode WRITE. `threshold` ≥ 1.0 gives `onFailure: ERROR`, ≥ 0.95 `DLQ`, otherwise `NONE` |

Labels are written with `mergeArtifactLabels` / `mergeVersionLabels`, which replace every label
under a prefix (`contract.{id}.`, `field-tag.{id}:`) and keep all others, as one storage operation.
In KafkaSQL these are journal messages (`MergeArtifactLabels4Message`, `MergeVersionLabels5Message`),
so all replicas apply them in the same order. Rule projection replaces the rules named
`odcs:{id}:*` and keeps the others. The `contract.` label prefix is reserved for this feature
(`ContractLabelOwner`), following the [labels convention](../.claude/rules/labels.md).

The field-tag key uses `|` before the tag name because field paths contain dots
(`address.zipCode`). The `INLINE` tag source and the `TagExtractor` implementations (Avro `tags`,
JSON Schema `x-tags`, Protobuf `@tag:` comments, and their Confluent equivalents) exist for tags
declared in schema content, but are not yet called when a version is created (see
[Limitations](#limitations-and-open-work)).

`GET .../contract/export` (`OdcsExporter`) goes the other way: it rebuilds an ODCS document from
the labels, rules and field tags on one artifact. It is a view of the projection, not of the
submitted YAML.

### Contract state that is not in the YAML

Some contract state is changed through the API rather than by submitting YAML. It is stored in the
same `contract.{id}.*` artifact labels, so the metadata view, search and export see it:

- **Status** (`POST .../contract/status`): `DRAFT → STABLE → DEPRECATED`, never backwards
  (`ContractMetadataValidator`). Transitions also set `lifecycle.stable-date` or
  `lifecycle.deprecated-date`.
- **Promotion stage** (`POST .../contract/promote`, `PromotionService`): `DEV → STAGE → PROD`,
  forward and one step at a time once a stage is set; PROD requires status `STABLE`.
- **Compatibility group** (`PUT .../contract/compatibility-group`): a label only.
- **Metadata** (`PUT .../contract/metadata`): replaces the contract's labels with the given fields,
  without transition checks.

`GET .../contract/metadata` maps the labels to a DTO (`ContractMetadataMapper`); it finds the
contract through the `contract.{id}.id` label and falls back to un-namespaced `contract.*` labels
for metadata set before any ODCS contract. `GET .../contract/quality?contractId=`
(`QualityScoreCalculator`) derives an informational score from the same labels: completeness
(30%), compliance (40%) and stability (30%).

### Contract rules

A contract rule (`ContractRule`; `ContractRuleDto` in storage) has a `name`, a `kind` (`CONDITION`
or `TRANSFORM`), a `type` (`CEL` or `JSONATA`), a `mode` (`WRITE`, `READ`, `WRITEREAD`, `UPGRADE`,
`DOWNGRADE`), an expression, `params`, `tags`, `onSuccess`/`onFailure` actions (`NONE`, `ERROR`,
`DLQ`) and `disabled`. A ruleset holds `domainRules` (checks and transforms on records) and
`migrationRules` (transforms between versions).

Rulesets exist at three scopes, all in the `contract_rules` table (DB upgrade 103): **global**
(`/admin/contracts/ruleset`, stored under a sentinel group and artifact), **artifact**
(`.../contract/ruleset`) and **version** (`.../versions/{v}/contract/ruleset`). They are separate
from Registry's artifact rules, which validate schema content; contract rules never run when a
schema is registered. Ruleset changes, metadata changes and status changes publish
`CONTRACT_RULESET_CONFIGURED`, `CONTRACT_METADATA_UPDATED` and `CONTRACT_STATUS_CHANGED` registry
events.

### Rule execution

The `contracts-rules` module (`apicurio-registry-contracts-rules`) evaluates rules without any
storage or Quarkus dependency, so `app` and `serde-common` share it. `RuleExecutionEngine`:

1. drops disabled rules and rules whose mode does not match (`WRITEREAD` and an unset mode match
   any mode), and sorts the rest by order;
2. evaluates each rule. CEL (`org.projectnessie.cel:cel-standalone`) exposes each top-level field
   of the record as a variable; JSONata (`com.dashjoin:jsonata`) receives the whole record. A
   CONDITION passes only if the result is `true`; a compile or evaluation error is a failure.
   Compiled expressions are cached (1000 per evaluator);
3. on a failure, records a violation, and stops if `onFailure` is `ERROR`;
4. feeds each TRANSFORM's output map to the next rule.

The result (`passed`, `violations`, `transformedRecord`, counts) is the same everywhere. Unknown
rule types are skipped with a warning.

There are three callers, and they see different rules:

| Caller | Rules evaluated |
|---|---|
| `POST .../versions/{v}/contract/execute` (`RuleExecutionService`) | `domainRules` of global, artifact and version scopes, merged by name, the narrowest scope winning |
| `POST .../contract/migrate` (`MigrationRuleService`, `MigrationExecutor`) | For each step on the version path, the `migrationRules` of the step's target version, in mode `UPGRADE` (or `DOWNGRADE`, walking backwards) |
| Serializers and deserializers (`AbstractSerializer`, `AbstractDeserializer`) | The artifact-scope ruleset only, fetched with the SDK and cached (`contract-rules.cache.ttl.seconds`, default 60) |

The serializers are opt-in (`apicurio.registry.serde.contract-rules.enabled`, default `false`).
They run `WRITE` rules after resolving the schema and before writing, and `READ` rules after
reading. With `contract-rules.fail-on-error` (default `true`) any failed rule throws; otherwise it
is logged. Records are converted to maps by parsing their JSON `toString()`.

Running the rules in the client keeps the serializer's hot path free of a registry call per record,
at the cost of a rules cache that can be up to one TTL out of date.

### Search and audit

`GET /search/contracts` searches artifacts by their contract labels (`status`, `ownerTeam`,
`compatibilityGroup`), matching both namespaced (`contract.{id}.status`) and un-namespaced keys.

`ContractAuditService` writes status changes (`STATUS_CHANGED`) and metadata updates
(`METADATA_UPDATED`) to the `contract_audit_log` table (DB upgrade 106; a KafkaSQL journal message
in KafkaSQL), read with `GET .../contract/audit`.

### Behaviour across storage variants

Contracts are labels plus two tables, so they need no contract-specific storage code beyond the
tables and their KafkaSQL journal messages.

| Variant | Behaviour |
|---|---|
| SQL (H2, PostgreSQL, MySQL, SQL Server) | Full support. `contract_rules` (upgrade 103) and `contract_audit_log` (upgrade 106) exist in all four dialects. |
| KafkaSQL | Full support. Label merges, rulesets and audit entries are journal messages, applied in order on every replica. |
| GitOps | Read-only: `ODCS_CONTRACT` artifacts can be served, but submitting, projecting, and changing contract state or rules fail as any write does. |
| KubernetesOps | Same as GitOps. |

### Authorization

There is no feature flag; contracts are used by submitting one. All endpoints are `@Authorized`:
reads, execute and migrate need read access; submitting contracts and changing contract state or
rulesets need write access to the artifact (submission also to the group); changing the global
ruleset needs the admin role.

## Configuration

The server has no contract-specific configuration. The serializers and deserializers have:

| Property | Default | Meaning |
|---|---|---|
| `apicurio.registry.serde.contract-rules.enabled` | `false` | Evaluate the artifact's contract rules on write (serializer) and read (deserializer) |
| `apicurio.registry.serde.contract-rules.fail-on-error` | `true` | Throw on any failed rule; when `false`, log and continue |
| `apicurio.registry.serde.contract-rules.cache.ttl.seconds` | `60` | How long a fetched ruleset is cached |
| `apicurio.registry.serde.contract-rules.migration.enabled` | `false` | Run migration rules in the deserializer (the result is currently discarded) |
| `apicurio.registry.serde.contract-rules.migration.target-version` | none | Target version for deserializer migration |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| REST | [`GroupsResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/GroupsResourceImpl.java), [`AdminResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/AdminResourceImpl.java), [`SearchResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/SearchResourceImpl.java) | Contract, state, ruleset, execute, migrate and audit endpoints; global ruleset; contract search |
| ODCS | [`contracts/odcs/`](../app/src/main/java/io/apicurio/registry/contracts/odcs/) | Model, parser, location parsing, the three projectors, export |
| Contract state | [`ContractLabels`](../app/src/main/java/io/apicurio/registry/contracts/ContractLabels.java), [`ContractMetadataMapper`](../app/src/main/java/io/apicurio/registry/contracts/ContractMetadataMapper.java), [`ContractMetadataValidator`](../app/src/main/java/io/apicurio/registry/contracts/ContractMetadataValidator.java), [`ContractLabelOwner`](../app/src/main/java/io/apicurio/registry/contracts/ContractLabelOwner.java) | Label keys, metadata mapping, status transitions, reserved prefix |
| Lifecycle | [`promotion/`](../app/src/main/java/io/apicurio/registry/contracts/promotion/), [`quality/`](../app/src/main/java/io/apicurio/registry/contracts/quality/), [`compatibility/`](../app/src/main/java/io/apicurio/registry/contracts/compatibility/) | Promotion stages, quality score, compatibility group label |
| Rule loading | [`RuleExecutionService`](../app/src/main/java/io/apicurio/registry/contracts/rules/RuleExecutionService.java), [`MigrationRuleService`](../app/src/main/java/io/apicurio/registry/contracts/migration/MigrationRuleService.java) | Merge scopes and call the engine; build migration paths |
| Rule engine | [`contracts-rules`](../contracts-rules/src/main/java/io/apicurio/registry/contracts/rules/) | Storage-free CEL and JSONata evaluation |
| Audit | [`ContractAuditService`](../app/src/main/java/io/apicurio/registry/contracts/audit/ContractAuditService.java), [`SqlContractAuditRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlContractAuditRepository.java) | Audit entries |
| Storage | [`RegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/RegistryStorage.java) (`mergeArtifactLabels`, `mergeVersionLabels`, contract rulesets), SQL DDLs | Prefix-scoped label merges; `contract_rules`, `contract_audit_log` |
| Artifact type | [`OdcsContractReferenceFinder`](../schema-util/common/src/main/java/io/apicurio/registry/content/refs/OdcsContractReferenceFinder.java) and the other `OdcsContract*` classes in `schema-util/common` | Accepter, validator, reference finder, dereferencer |
| Tag extractors (not wired) | [`contracts/tags/`](../app/src/main/java/io/apicurio/registry/contracts/tags/) | Inline tag extraction for Avro, JSON Schema, Protobuf |
| Serializers | [`AbstractSerializer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/AbstractSerializer.java), [`AbstractDeserializer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/AbstractDeserializer.java), [`SerdeConfig`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/config/SerdeConfig.java) | Client-side rule evaluation |
| UI | [`ArtifactContractTabContent`](../ui/ui-app/src/app/pages/artifact/components/tabs/ArtifactContractTabContent.tsx), [`GlobalContractRulesPage`](../ui/ui-app/src/app/pages/contractRules/GlobalContractRulesPage.tsx) | Contract tab; global contract rules page |

## Limitations and open work

Projection lifecycle:

- **Deleting a contract leaves its projection.** `DELETE /groups/{g}/contracts/{id}` deletes the
  `ODCS_CONTRACT` artifact only; labels, `odcs:{id}:*` rules (still enforced) and field tags stay.
- **Re-projection overwrites API-set state.** Updating a contract replaces every `contract.{id}.*`
  label, so status, stage, compatibility group and lifecycle dates set through the API are lost.
  `PUT .../contract/metadata` similarly replaces them all, including the `id` label that links the
  artifact to its contract.
- **Re-projection does not clean up.** A schema dropped from `schemas[]` keeps its projection, and
  a contract with no `quality.accuracy` (or no field tags) keeps the old rules (or tags).
- **Rollback is narrow.** A failed projection deletes the whole contract artifact, but per-schema
  failures are reported as warnings, so partial projections are possible.
- **Label keys are lower-cased** by SQL storage, so field paths and tag names lose their case, and
  the export reports `pii` as a tag rather than `pii: true`. The tag projector checks keys against
  512 characters, but storage rejects keys over 256, which drops all of that schema's tags.

Features that are partly wired:

- **Inline tags are not extracted.** The `TagExtractor` implementations are unused.
- **The compatibility group is not used** by compatibility checking.
- **`GovernanceRuleExecutor`** (completeness levels NONE/BASIC/FULL) is not called.
- **The audit log** records only status changes and metadata updates, and its entries outlive the
  artifact.
- **Promotion has no concurrency control**: two concurrent promotions can both succeed.

Serializers:

- They evaluate artifact-scope rules only, not global or version rules, so the same record can
  pass in a producer and fail `.../contract/execute`, or the reverse.
- TRANSFORM output and deserializer migration results are discarded, and nothing implements `DLQ`
  routing; `onFailure` other than `ERROR` only changes whether evaluation continues.
- The exception message prints violations without their text (`RuleViolation` has no `toString`).

Other:

- `cel-standalone` is pinned to 0.8.0 in `contracts-rules/pom.xml`, while the root
  `cel-standalone.version` property still says 0.6.0 and is unused.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| A custom contract format | Rejected. No ecosystem tooling (such as `datacontract-cli`), another format to learn, and it would compete with Confluent's proprietary format instead of differentiating with a standard. |
| Contract-first evaluation, without projection | Deferred. Reading rules from the contract at enforcement time is the cleanest model: no second copy, nothing to drift or clean up, and most limitations above disappear. It needs the rule engine and serializers to resolve contracts on every lookup, and replaces the label-based metadata existing clients use. Worth reconsidering in a major version. |
| A dedicated `field_tags` table | Rejected. Version labels give the same lookups with no new table, repository, KafkaSQL messages or read-only decorator code. |
| The ODCS code in its own module | Partly adopted. Only rule evaluation moved to `contracts-rules`, because serializers need it. Parsing, projection and export depend on `RegistryStorage` and stay in `app`. |

## Consequences

- **Benefits:** Registry accepts a standard contract format, and ODCS tooling can submit and fetch
  contracts through the REST API. Contracts reuse labels, search, events and the storage journal;
  the only new tables are `contract_rules` and `contract_audit_log`. The same rule engine runs in
  the server and in serializers, so a rule behaves the same in both, as long as both evaluate the
  same rules.
- **Costs:** Two copies of the contract exist: the YAML and its projection. They can drift,
  through direct label edits, API-set state, or deletion. The YAML is authoritative and
  re-submitting it repairs the projection, but not API-set state (see
  [Limitations](#limitations-and-open-work)). Two rule systems (artifact rules for schema content,
  contract rules for records) share vocabulary but not code, which users must learn to tell apart.

## Example and verification

- **Example:** [`examples/odcs-data-contracts`](../examples/odcs-data-contracts/):
  `docker compose up -d && ./demo.sh` checks the contract artifact, the projected labels, rule and
  field tags, and rule execution on a valid and an invalid record. Its
  [guide](../examples/odcs-data-contracts/GUIDE.md) walks through every behaviour in this ADR,
  including the Kafka serializer.
- **User documentation:** [`assembly-data-contracts.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-data-contracts.adoc).


| Concern | Tests to read |
|---|---|
| Submit, update, list, project, export | `app/.../noprofile/rest/v3/OdcsContractResourceTest`, `integration-tests/.../OdcsContractIT` |
| Execute, migrate, audit, search, global ruleset | `app/.../noprofile/rest/v3/DataContractsResourceTest` |
| Rule engine, CEL, JSONata | `contracts-rules/src/test/.../RuleExecutionEngineTest`, `CelExpressionEvaluatorTest`, `JsonataRuleExecutorTest` |
| Metadata, status transitions, promotion, quality, compatibility group | `app/.../contracts/ContractMetadataMapperTest`, `ContractMetadataValidatorTest`, `PromotionServiceTest`, `QualityScoreCalculatorTest`, `CompatibilityGroupServiceTest` |
| KafkaSQL journaling | `app/.../storage/impl/kafkasql/KafkaSqlContract*Test`, `KafkaSqlGlobalContractRulesetJournalTest` |
| Serializers | `app/.../noprofile/serde/ContractRuleSerdeTest`, `serde-common/.../ContractRuleLocalEvaluationTest` |

```bash
./mvnw test -pl app -Dtest='OdcsContractResourceTest,DataContractsResourceTest'
./mvnw test -pl contracts-rules
```

## History

The feature was delivered in phases; the design above is their current combined state.

| Phase | Delivered |
|---|---|
| 1 | `contract.*` labels, contract rules (`contract_rules` table), metadata and ruleset endpoints |
| 2–3 | ODCS submission, projection, field tags, tag extractors, export |
| 4 | Status transitions, promotion stages, quality score |
| 5 | `contracts-rules` module, CEL execution endpoint, global/artifact/version merge |
| 6 | JSONata, migration rules, compatibility groups |
| 7 | Contract events, audit log, contract search, serializer integration |
| 8 | Global rulesets, UI contract tab and global contract rules page |

Earlier revisions of this ADR also recorded, and resolved: un-namespaced projection, which allowed
only one contract per schema (fixed by namespacing with the contract ID); read-modify-write label
updates, inconsistent across KafkaSQL replicas (fixed by the merge operations); and serializers
calling a server endpoint over raw HTTP (they now fetch rulesets with the SDK and evaluate
locally).
