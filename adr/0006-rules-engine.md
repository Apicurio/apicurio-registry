# ADR-0006: Rules Engine

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-09.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11000](https://github.com/Apicurio/apicurio-registry/issues/11000),
  [#11001](https://github.com/Apicurio/apicurio-registry/issues/11001).
- **Scope:** The content rules (validity, compatibility, integrity) that the registry applies when
  content is written: rule types and levels, which rule applies, when rules run, which versions are
  compared, and the error contract. Not covered: contract rulesets (CEL/JSONata, see
  [ADR-0002](0002-odcs-native-data-contracts.md)), client-side data validation (only its relation to
  rules), and authorization.

## Reading this document

- Start with [the decision](#decision).
- For operators: [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-intro-to-registry-rules.adoc).
- For a running system: the [example](../examples/rules-and-compatibility/), which checks every
  behaviour below with `demo.sh`.
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

A registry is useful only if consumers can trust what it holds: that a schema is well formed, that a
new version will not break the producers and consumers of older ones, and that its references
resolve. These checks depend on the artifact type (Avro, Protobuf, JSON Schema, OpenAPI, agent
cards, ...), must be configurable by scope (a sandbox group needs fewer checks than production
topics), and must give API clients an error they can act on. They must behave the same on every
storage variant, and through the Confluent-compatible API used by Kafka serializers.

## Decision

1. **Three rule types**, each with fixed levels:
   `VALIDITY` (`NONE`, `SYNTAX_ONLY`, `FULL`), `COMPATIBILITY` (`BACKWARD`, `BACKWARD_TRANSITIVE`,
   `FORWARD`, `FORWARD_TRANSITIVE`, `FULL`, `FULL_TRANSITIVE`, `NONE`) and `INTEGRITY` (a
   comma-separated set of `REFS_EXIST`, `ALL_REFS_MAPPED`, `NO_DUPLICATES`, `NO_CIRCULAR_REFERENCES`,
   or `FULL` / `NONE`).
2. **Rules are scoped:** global, group, artifact, plus default global rules from configuration. For
   **each rule type separately**, the first rule found wins: artifact, group, global, default.
   `NONE` at a narrower scope switches that type off there.
3. **Rules check writes, not reads:** they run when content is created (new artifact with a first
   version, new version, `ifExists` paths that create a version, publication of a draft), never on
   stored data. Changing a rule does not revalidate existing versions.
4. **The artifact type owns the semantics:** each type provides a content validator, a compatibility
   checker and reference handling; the engine only selects the rule and the versions to compare.
5. **Compatibility compares with the versions that are not `DISABLED`:** the latest one for
   non-transitive levels, all of them for `*_TRANSITIVE`.
6. **A violation is a client error with structured causes:** HTTP 400 with
   `ruleViolation.ruleType` and `ruleViolation.causes[]` (`description`, `context`, and for
   compatibility the location of each difference). Nothing is stored.
7. **Dry run runs the same rules** and skips only persistence; **drafts skip the rules** until they
   are published.

### Why per-type precedence

Rule types answer independent questions. A team that turns compatibility off for a sandbox group
still wants malformed content rejected; resolving precedence per type lets a group or artifact
override one concern without restating the others. The cost is that the effective rule set of an
artifact is not visible in one place: it must be computed from up to four sources (see
[Limitations](#limitations-and-open-work)).

### Why `DISABLED` and not `DEPRECATED` excludes a version

Deprecated versions are still served and may still have data written with them, so a new version
must stay compatible with them. Disabling a version removes it from normal reads; it is the explicit
way to stop it constraining evolution.

## Rule model

### Which rule applies

[`RulesServiceImpl.applyAllRules`](../app/src/main/java/io/apicurio/registry/rules/RulesServiceImpl.java)
iterates over the rule types and, for each, takes the first configuration found:

| Order | Source | Notes |
|---|---|---|
| 1 | Artifact rule | Only loaded for updates: a new artifact has no artifact rules yet |
| 2 | Group rule | Only when the group exists |
| 3 | Global rule | Stored, managed through `/admin/rules` |
| 4 | Default global rule | `apicurio.rules.global.<type>`; listed by `GET /admin/rules` and overridable there, but cannot be deleted through the API (409) |

The rule is then executed by the executor for its type
([`RuleExecutorFactory`](../app/src/main/java/io/apicurio/registry/rules/RuleExecutorFactory.java)).

### When rules run

| Operation | Rules |
|---|---|
| Create artifact with a first version | Yes (`CREATE`: group, global, default rules) |
| Create artifact without a version | No content, no rules |
| Create version; `ifExists=CREATE_VERSION` | Yes (`UPDATE`) |
| `ifExists=FIND_OR_CREATE_VERSION` matching an existing version | No: the existing version is returned |
| `dryRun=true` | Yes; only persistence is skipped |
| Create a draft (`isDraft: true`) | No, unless `apicurio.rest.draft.production-mode.enabled=true` |
| Change a draft's state (publish, or disable) | Yes, on the draft's content |
| Edit a draft's content (`apicurio.rest.mutability.artifact-version-content.enabled`) | No |
| Other state changes, metadata, labels | No |
| Import | No |
| Confluent-compatible registration | Yes (`CREATE` for a new subject, otherwise `UPDATE`) |
| GitOps / KubernetesOps data load | Yes, with different version selection (see [storage variants](#behaviour-across-storage-variants)) |

### Compatibility

[`CompatibilityChecker.testCompatibility`](../schema-util/common/src/main/java/io/apicurio/registry/rules/compatibility/CompatibilityChecker.java)
receives the level, the existing contents, the proposed content and its resolved references.
[`AbstractCompatibilityChecker`](../schema-util/common/src/main/java/io/apicurio/registry/rules/compatibility/AbstractCompatibilityChecker.java),
which most types extend, defines the levels once:

- non-transitive levels compare with the **last** existing content; `*_TRANSITIVE` with each one;
- `FORWARD` is `BACKWARD` with the arguments swapped; `FULL` is both;
- no existing content always passes.

The existing contents come from `getEnabledArtifactContentIds`: every version **except `DISABLED`**,
ordered by version order, so `DEPRECATED` and `DRAFT` versions are included (see limitation 2).

Checkers are registered per type in
[`StandardArtifactTypeProviderRegistry`](../schema-util/util-provider/src/main/java/io/apicurio/registry/types/provider/StandardArtifactTypeProviderRegistry.java):
Avro (Apache Avro reader/writer compatibility), Protobuf, JSON Schema (Apitomy), OpenAPI, XSD,
Iceberg, the agent types, and configured custom types. Types without a checker (for example AsyncAPI,
GraphQL, WSDL) accept every change. A checker that cannot process a schema raises
`UnprocessableSchemaException` (422).

### Integrity

[`IntegrityRuleExecutor`](../app/src/main/java/io/apicurio/registry/rules/app/integrity/IntegrityRuleExecutor.java):

| Level | Check | `context` |
|---|---|---|
| `ALL_REFS_MAPPED` | Every reference in the content has a mapping (type-specific) | Reference |
| `REFS_EXIST` | Every mapped artifact version exists, and its type is an allowed target | Reference name |
| `NO_DUPLICATES` | No reference name is mapped twice | Reference name |
| `NO_CIRCULAR_REFERENCES` | The reference graph has no cycle | Reference name |
| `FULL` | All of the above | |

### Error contract

| API | Validity violation | Other violation | Body |
|---|---|---|---|
| Core v3 | 400 | 400 | `RuleViolationProblemDetails`: `ruleViolation.ruleType`, `ruleViolation.causes[]` (`description`, `context`, `code`, `direction`, `proposed`, `existing`); top-level `causes` is deprecated |
| Core v3, `apicurio.rest.legacy-error-codes.enabled=true` | 409 | 409 | Same |
| Confluent-compatible | 422, error code 42201 | 409 | Confluent error envelope |

Mapped in [`HttpStatusCodeMap`](../app/src/main/java/io/apicurio/registry/services/http/HttpStatusCodeMap.java)
and [`CoreRegistryExceptionMapperService`](../app/src/main/java/io/apicurio/registry/services/http/CoreRegistryExceptionMapperService.java).

### Behaviour across storage variants

SQL and KafkaSQL apply rules on API writes with the same queries. GitOps and KubernetesOps have no
API writes; they apply rules when they load data, with different version selection.

| Variant | Behaviour |
|---|---|
| SQL | Rules and content IDs read in the same storage; the `DISABLED` filter is in `selectArtifactContentIds` |
| KafkaSQL | Same queries against the local SQL view of the journal |
| GitOps, KubernetesOps | Read-only through the API, so rules cannot be managed there. When data is loaded, [`PollingDataValidator`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/PollingDataValidator.java) checks each artifact's versions against **all preceding versions in the source, whatever their state** (a `DISABLED` version still counts under a transitive rule), and by default checks **only the last version**; the `validatedUpTo` field of the source file sets where checking starts. Failures are reported as load errors |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.rules.global.validity` | not set | Default global validity level |
| `apicurio.rules.global.compatibility` | not set | Default global compatibility level |
| `apicurio.rules.global.integrity` | not set | Default global integrity levels |
| `apicurio.rest.draft.production-mode.enabled` | `false` | Apply rules to drafts when they are created |
| `apicurio.rest.legacy-error-codes.enabled` | `false` | Return 409 instead of 400 for rule violations |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Engine | [`RulesServiceImpl`](../app/src/main/java/io/apicurio/registry/rules/RulesServiceImpl.java) | Precedence, versions to compare, executor dispatch |
| Defaults | [`RulesConfiguration`](../app/src/main/java/io/apicurio/registry/rules/RulesConfiguration.java), [`RulesPropertiesImpl`](../app/src/main/java/io/apicurio/registry/rules/RulesPropertiesImpl.java) | `apicurio.rules.global.*` |
| Executors | [`rules/app`](../app/src/main/java/io/apicurio/registry/rules/app/) | One per rule type |
| Type semantics | [`schema-util`](../schema-util/) | Content validators, compatibility checkers, reference handling per type |
| REST | [`GroupsResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/GroupsResourceImpl.java), [`AdminResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/AdminResourceImpl.java) | When rules run; rule management |
| Errors | [`services/http`](../app/src/main/java/io/apicurio/registry/services/http/) | Status codes and problem details |

## Limitations and open work

1. **Rule configs are not validated when saved** in the core API: an invalid level is stored, and
   every later write to the affected artifacts fails with 500
   ([#11000](https://github.com/Apicurio/apicurio-registry/issues/11000)).
2. **Publishing a draft compares it with itself** under non-transitive levels, because drafts are
   included in the compared versions; transitive levels catch the problem
   ([#11001](https://github.com/Apicurio/apicurio-registry/issues/11001)).
3. **No effective-rules view:** the API returns the rules of one scope; clients compute the
   effective set themselves.
4. **Imported data is not checked**, and changing a rule never revalidates stored versions.
5. **GitOps and KubernetesOps select versions differently** from API writes: all preceding versions
   regardless of state, and by default only the last version is checked.
6. **Types without a compatibility checker** accept any change under every compatibility level,
   without telling the client.
7. **The OpenAPI description still documents 409** in some shared examples, although 400 is the
   default since 3.3.0.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Precedence by scope for all rule types together | Not selected: overriding one concern would require restating the others |
| Exclude `DEPRECATED` versions from compatibility | Not selected: deprecated versions may still have live data; `DISABLED` is the explicit opt-out |
| Revalidate stored versions when a rule changes | Not selected: rules guard evolution, and stored data cannot be changed retroactively; it would make rule changes expensive and could fail halfway |
| 409 for violations | Kept only as a legacy mode: a violation is an invalid request, not a state conflict |
| Check compatibility in the serializer | Not selected: clients would duplicate per-type logic; the registry is the single place where rules are enforced |

## Consequences

- **Benefits:** one engine for every artifact type and API; scoped, independent overrides; a
  structured, machine-readable error; rules that cost nothing on reads.
- **Costs:** the effective rule set is spread over up to four places; per-type semantics live in many
  checkers of uneven depth; the two open issues above.

## Example and verification

- **Example:** [`examples/rules-and-compatibility`](../examples/rules-and-compatibility/):
  `docker compose up -d && ./demo.sh` checks validity, BACKWARD and FORWARD, precedence, dry run,
  transitive checks with deprecated and disabled versions, drafts and integrity; its
  `validation-client` shows client-side data validation. Both run in CI against each pull request's
  images.
- **User documentation:**
  [`assembly-intro-to-registry-rules.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-intro-to-registry-rules.adoc),
  [`assembly-rule-reference.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-rule-reference.adoc),
  [`assembly-registry-compatibility-modes.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-registry-compatibility-modes.adoc).

| Concern | Tests to read |
|---|---|
| Compatibility and validity on writes | `CompatibilityRuleApplicationTest`, `ValidityRuleApplicationTest` (app) |
| Group rules and precedence | `GroupRulesTest`, `AdminResourceTest` (app) |
| Dry run and drafts | `DryRunTest`, `DraftProductionModeTest`, `DraftContentTest` (app) |
| Integrity | `CircularReferenceIntegrityRuleTest`, `IntegrityRuleExecutorCircularReferenceTest` (app) |
| Error contract | `CoreRegistryExceptionMapperServiceTest`, `LegacyErrorCodesModeTest` (app); `RuleViolationCauseTest` (common) |
| Per-type checkers | `AbstractCompatibilityCheckerTest`, `JsonSchemaCompatibilityCheckerTest`, `ProtobufCompatibilityCheckerTest`, `OpenApiCompatibilityCheckerTest`, `XsdCompatibilityCheckerTest` (schema-util) |
| Confluent-compatible API | `CCompatCompatibilityConfigTest`, `CompatibilityResourceTest` (app) |

```bash
./mvnw test -pl app -Dtest='CompatibilityRuleApplicationTest,ValidityRuleApplicationTest,GroupRulesTest,DryRunTest'
```

## History

| Milestone | Result |
|---|---|
| 3.3.0 | Rule violations return 400 instead of 409 by default (`apicurio.rest.legacy-error-codes.enabled` restores 409) |
| This ADR | First written record of the rules engine, with a verified example |
