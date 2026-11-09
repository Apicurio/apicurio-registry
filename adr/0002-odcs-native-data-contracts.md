# ADR-0002: ODCS-Native Data Contracts

- **Status:** Accepted; rewritten 2026-11-06 to describe the current design, including the fixes
  for [#10958](https://github.com/Apicurio/apicurio-registry/issues/10958).
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
| `OdcsLabelProjector` | `info.status` (draft → `DRAFT`, active → `STABLE`, deprecated/retired → `DEPRECATED`), `info.dataClassification`, `info.version`, `team.name`, `team.domain`, `team.contact`, `serviceLevel.availability`, `serviceLevel.latency.p50/p99`, `quality.freshness.maxStaleness`, `quality.completeness[]`, and the contract ID | Artifact labels `contract.{id}.status`, `.classification`, `.version`, `.owner.team`, `.owner.domain`, `.support.contact`, `.sla.*`, `.quality.*`, `.id`; a status that becomes `STABLE` or `DEPRECATED` also gets its `lifecycle.*-date` |
| `OdcsTagProjector` | Each field's `pii: true` (tag `PII`), `classification` (tag `CLASSIFICATION:<value>`) and `tags[]` | Labels on the target version: `field-tag.{id}:{fieldPath}\|{tag}` = `EXTERNAL`. A key longer than 256 characters, the limit of every storage variant, is skipped with a warning |
| `OdcsRuleProjector` | Each `quality.accuracy[]` entry | An artifact-level contract rule `odcs:{id}:{name}`: CONDITION, CEL, mode WRITE. `threshold` ≥ 1.0 gives `onFailure: ERROR`, ≥ 0.95 `DLQ`, otherwise `NONE` |

Labels are written with `mergeArtifactLabels` / `mergeVersionLabels`, which replace every label
under a prefix (`contract.{id}.`, `field-tag.{id}:`) and keep all others, as one storage operation
that locks the artifact or version row. Keys keep their case; only the search index is
lower-cased, as for any label.
In KafkaSQL these are journal messages (`MergeArtifactLabels4Message`, `MergeVersionLabels5Message`),
so all replicas apply them in the same order. Rule projection replaces the rules named
`odcs:{id}:*` and keeps the others. The `contract.` label prefix is reserved for this feature
(`ContractLabelOwner`), following the [labels convention](../.claude/rules/labels.md).

The field-tag key uses `|` before the tag name because field paths contain dots
(`address.zipCode`). Tags can also be declared in schema content: Avro `tags`, JSON Schema `x-tags`,
Protobuf `@tag:` comments, and their Confluent equivalents. When a version is created through the
v3 REST API, `InlineFieldTagService` reads them with the `TagExtractor` for the artifact type (for
Avro, from the JSON when the schema references named types defined elsewhere) and
adds `field-tag.{fieldPath}|{tag}` = `INLINE` to the version's labels; labels submitted with the
version win over extracted ones with the same key.

### Projection lifecycle

A contract is projected again every time a version of it is submitted, and the projection follows
the contract to the end:

- **Re-projection replaces what the YAML provides, and keeps the rest.** The projectors replace
  the contract's labels, rules and tags with the new version's. State set through the API (stage,
  compatibility group, lifecycle dates) is carried over, and the YAML's status is applied only if
  the lifecycle allows moving to it from the current status; otherwise the current status stays
  and the response carries a warning. A version without accuracy rules or field tags removes the
  ones an earlier version projected.
- **Schemas a contract stops governing are cleaned up.** Before projecting, the previous version
  of the contract is read; schemas it targeted and the new version does not lose the contract's
  labels, rules and field tags (on every version).
- **Deleting a contract removes its projection** from every schema any of its versions targeted,
  before the `ODCS_CONTRACT` artifact is deleted, so a failure leaves a contract that can be
  deleted again.
- **A failed projection is rolled back.** Missing schema artifacts, invalid locations and missing
  versions are warnings. Before storing the new version, the contract's projection on every
  affected schema is captured (labels, the artifact's contract ruleset, field tags on each
  version). Any other failure deletes the new contract version (or the new contract artifact),
  puts every captured projection back as it was, and returns 400 with a fixed message; the cause
  is only logged.

Each projection and removal is recorded in the schema artifact's audit log (`CONTRACT_PROJECTED`,
`CONTRACT_REMOVED`). The "previous version" of a contract is its `latest` branch tip, not the last
entry of the version list, whose order is not defined.

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
  forward and one step at a time, starting at DEV; PROD requires status `STABLE`.
- **Compatibility group** (`PUT .../contract/compatibility-group`): a label only.
- **Metadata** (`PUT .../contract/metadata`): replaces the descriptive fields (owner, domain,
  contact, classification, compatibility group) and keeps every other contract label, so the
  artifact stays linked to its ODCS contract. Status and stage change only when given, by the same
  rules as the status and promote endpoints; omitting them keeps them.

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
| Serializers and deserializers (`AbstractSerializer`, `AbstractDeserializer`, `ContractRuleLoader`) | `domainRules` of global, artifact and version scopes, merged as by the execute endpoint, fetched with the SDK and cached (`contract-rules.cache.ttl.seconds`, default 60), empty rulesets included; for migration, as the migrate endpoint |

The serializers are opt-in (`apicurio.registry.serde.contract-rules.enabled`, default `false`).
They run `WRITE` rules after resolving the schema and before writing, and `READ` rules after
reading. With `contract-rules.fail-on-error` (default `true`) any failed rule throws; otherwise it
is logged. Records are converted to maps by parsing their JSON `toString()`. The output of
TRANSFORM rules and of deserializer migrations is not applied to the record (see
[Limitations](#limitations-and-open-work)).

Running the rules in the client keeps the serializer's hot path free of a registry call per record,
at the cost of a rules cache that can be up to one TTL out of date. A ruleset that cannot be
fetched (timeout, 401, 5xx) is never cached as "no rules": the failure propagates, so with
`fail-on-error` the record is rejected, and the next record fetches again. Rules keep their order
within their own scope, so rules of different scopes interleave as on the server.

### Search and audit

`GET /search/contracts` searches artifacts by their contract labels (`status`, `ownerTeam`,
`compatibilityGroup`), matching both namespaced (`contract.{id}.status`) and un-namespaced keys.

`ContractAuditService` writes every change to an artifact's contract state to the
`contract_audit_log` table (DB upgrade 106; a KafkaSQL journal message in KafkaSQL), read with
`GET .../contract/audit`: `CONTRACT_PROJECTED`, `CONTRACT_REMOVED`, `STATUS_CHANGED`,
`METADATA_UPDATED`, `PROMOTED`, `COMPATIBILITY_GROUP_SET`, and `RULESET_CONFIGURED` /
`RULESET_DELETED` for artifact and version rulesets. Global ruleset changes belong to no artifact
and are only in the REST audit log. Deleting an artifact, or all artifacts of a group, deletes its
audit entries and its artifact-level contract rules (version-level rules cascade with the
versions).

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
| Inline tags | [`contracts/tags/`](../app/src/main/java/io/apicurio/registry/contracts/tags/) | `InlineFieldTagService` and the tag extractors for Avro, JSON Schema, Protobuf |
| Serializers | [`ContractRuleLoader`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/ContractRuleLoader.java), [`AbstractSerializer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/AbstractSerializer.java), [`AbstractDeserializer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/AbstractDeserializer.java), [`SerdeConfig`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/config/SerdeConfig.java) | Client-side rule evaluation |
| UI | [`ArtifactContractTabContent`](../ui/ui-app/src/app/pages/artifact/components/tabs/ArtifactContractTabContent.tsx), [`GlobalContractRulesPage`](../ui/ui-app/src/app/pages/contractRules/GlobalContractRulesPage.tsx) | Contract tab; global contract rules page |

## Limitations and open work

1. **Serializers do not apply TRANSFORM output**, and nothing implements `DLQ` routing: `onFailure`
   other than `ERROR` only changes whether evaluation continues
   ([#10968](https://github.com/Apicurio/apicurio-registry/issues/10968)).
2. **The compatibility group is not used** by compatibility checking; it is a label that can be
   searched ([#10969](https://github.com/Apicurio/apicurio-registry/issues/10969)).
3. **`GovernanceRuleExecutor`** (completeness levels NONE/BASIC/FULL) is not called
   ([#10970](https://github.com/Apicurio/apicurio-registry/issues/10970)).
4. **Lifecycle checks are not atomic.** Promotion and status changes validate the current label,
   then write the new one; two concurrent requests can both pass
   ([#10971](https://github.com/Apicurio/apicurio-registry/issues/10971)).
5. **Inline tags are read only on the v3 REST create paths.** Versions created through the
   Confluent-compatible API, and draft content updates, get no `INLINE` tags.
6. **The export covers one artifact.** It rebuilds the contract from that artifact's projection
   and its latest version's tags, not the submitted multi-schema YAML.
7. **Direct label edits can diverge from the YAML.** The ODCS artifact is authoritative;
   re-submitting it repairs what it projects.

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
- **Costs:** Two copies of the contract exist: the YAML and its projection, which the projection
  lifecycle has to keep in step on every submission, update and deletion. Direct label edits can
  still make them drift; the YAML is authoritative and re-submitting it repairs the projection. Two rule systems (artifact rules for schema content,
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
| Projection lifecycle, inline tags, audit, cleanup on deletion | `app/.../noprofile/rest/v3/OdcsContractLifecycleTest` |
| Execute, migrate, audit, search, global ruleset | `app/.../noprofile/rest/v3/DataContractsResourceTest` |
| Rule engine, CEL, JSONata | `contracts-rules/src/test/.../RuleExecutionEngineTest`, `CelExpressionEvaluatorTest`, `JsonataRuleExecutorTest` |
| Metadata, status transitions, promotion, quality, compatibility group | `app/.../contracts/ContractMetadataMapperTest`, `ContractMetadataValidatorTest`, `PromotionServiceTest`, `QualityScoreCalculatorTest`, `CompatibilityGroupServiceTest` |
| KafkaSQL journaling | `app/.../storage/impl/kafkasql/KafkaSqlContract*Test`, `KafkaSqlGlobalContractRulesetJournalTest` |
| Serializers | `app/.../noprofile/serde/ContractRuleSerdeTest`, `serde-common/.../ContractRuleLocalEvaluationTest`, `ContractRuleLoaderTest` |

```bash
./mvnw test -pl app -Dtest='OdcsContractResourceTest,OdcsContractLifecycleTest,DataContractsResourceTest,ContractRuleSerdeTest'
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
| [#10958](https://github.com/Apicurio/apicurio-registry/issues/10958) | Projection lifecycle (deletion, re-projection that keeps API-set state, cleanup, rollback), case-preserving label merges, inline tag extraction, serializers evaluating all rule scopes, complete audit log, deletion of contract rules and audit with the artifact |

Earlier revisions of this ADR also recorded, and resolved: un-namespaced projection, which allowed
only one contract per schema (fixed by namespacing with the contract ID); read-modify-write label
updates, inconsistent across KafkaSQL replicas (fixed by the merge operations); and serializers
calling a server endpoint over raw HTTP (they now fetch rulesets with the SDK and evaluate
locally).
