# ADR-0024: Data model

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** the coordinate types in [`common/.../model`](../common/src/main/java/io/apicurio/registry/model/),
  [`RegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/RegistryStorage.java) and its
  DTOs; open work in [#10513](https://github.com/Apicurio/apicurio-registry/issues/10513),
  [#10164](https://github.com/Apicurio/apicurio-registry/issues/10164) and
  [#9326](https://github.com/Apicurio/apicurio-registry/issues/9326).
- **Scope:** The registry's core model: groups, artifacts and versions; how identifiers and hashes
  are assigned; version numbering; version states; drafts; branches and version expressions; labels
  and ownership fields; the default group. Not covered: how each storage variant persists the model
  ([ADR-0010](0010-storage-architecture.md)), artifact types ([ADR-0013](0013-custom-artifact-types.md)),
  references between versions ([ADR-0008](0008-artifact-references.md)), rules
  ([ADR-0006](0006-rules-engine.md)), and the deletion switches
  ([ADR-0017](0017-operational-guardrails.md)).

## Reading this document

- Start with [the decision](#decision).
- For API users: [identifiers](#identifiers), [version states](#version-states-and-drafts) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-registry-concepts-glossary.adoc).
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

A registry stores many revisions of many documents (schemas, API definitions, agent cards), and its
clients address them in two different ways. Humans and build tools use names: a group, an artifact,
a version. Serializers on the wire need a short number that identifies exact bytes, and must be able
to find the same number again for the same schema. Both kinds of address must stay valid across
storage variants (SQL, KafkaSQL, GitOps, KubernetesOps), across export and import, and while the
registry runs as several replicas. Some users also want to iterate on a version before publishing it,
or follow a moving pointer (the newest version) instead of a fixed one.

## Decision

1. **Three levels: group, artifact, version.** A group holds artifacts; an artifact has one artifact
   type ([ADR-0013](0013-custom-artifact-types.md)) and an ordered list of versions; a version points
   to one content row. Group and artifact IDs are 1–512 characters (`GroupId`, `ArtifactId`); the v3
   API also rejects `%` and non-ASCII characters in them (`ArtifactIdValidator`). Version and branch
   IDs are 1–256 characters of `[a-zA-Z0-9._\-+]` (`VersionId`, `BranchId`), so they never contain
   `=` and can not be confused with a version expression.
2. **Content is stored once and shared.** Versions with byte-identical content and references point
   to the same content row, identified by a `contentId` and two SHA-256 hashes: `contentHash` (of the
   raw bytes plus the serialized references) and `canonicalHash` (of the type's canonical form plus
   references).
3. **`globalId` identifies a version; `contentId` identifies content.** Both are longs from
   registry-wide sequences, allocated by storage, never by the client (except on import and in
   GitOps/KubernetesOps files).
4. **The default group has no stored name.** The API calls it `default`; `GroupId` maps `null` and
   `default` (case-insensitively) to the default group, the storage API receives `null`, and the SQL
   layer stores the sentinel `__$GROUPID$__` (`RegistryContentUtils.normalizeGroupId`). No group
   metadata row is created for it.
5. **Groups are created implicitly.** Creating an artifact in a group that does not exist creates the
   group, unless `apicurio.storage.enable-automatic-group-creation=false`, in which case it fails with
   `GroupNotFoundException`.
6. **Version identifiers are explicit or generated.** The client may name a version. If it does not,
   the version is `1` for the first version and otherwise the artifact's next `versionOrder`
   (`MAX(versionOrder) + 1`) as a string. `versionOrder` always records creation order, whatever the
   names are.
7. **Versions have a state; drafts are the only mutable versions.** Content of a non-draft version
   never changes. A `DRAFT` version's content can be replaced only when
   `apicurio.rest.mutability.artifact-version-content.enabled=true`.
8. **Branches are ordered lists of versions, and the version path segment is an expression.**
   Wherever the v3 API takes a version, it accepts either a version ID or `branch=<branchId>`, which
   resolves to the newest version on that branch (`VersionExpressionParser`). The system branches
   are maintained by the registry and can not be edited by users.

## Identifiers

| Identifier | Scope | Assigned by | Notes |
|---|---|---|---|
| `groupId`, `artifactId` | Registry / group | Client | A missing `artifactId` on create becomes a random UUID (`ArtifactIdGeneratorImpl`) |
| `version` | Artifact | Client, or generated (decision 6) | Unique within the artifact |
| `versionOrder` | Artifact | Storage | 1, 2, 3… in creation order |
| `globalId` | Registry | `globalId` sequence | One per version, never reused for another version |
| `contentId` | Registry | `contentId` sequence | One per distinct (content, references); shared by versions |
| `contentHash` | Registry | SHA-256 of bytes (+ references) | Unique; used for lookup and deduplication |
| `canonicalHash` | Registry | SHA-256 of canonical form (+ references) | Used by `canonical=true` lookups, including `ifExists=FIND_OR_CREATE_VERSION` |

A `contentId` is allocated on every content write, even when the content already exists, so the
sequence has gaps (`SqlContentRepository.insertContentIfAbsentRaw`). Draft content, unless
`apicurio.rest.draft.production-mode.enabled=true`, gets random `draft:<uuid>` hashes so it can not be
found by hash and is never shared; on publishing, the registry computes the real hashes and points
the version at the matching content row (`AbstractSqlRegistryStorage.updateArtifactVersionState`).

## Version states and drafts

| State | Meaning | On `branch=latest`? |
|---|---|---|
| `ENABLED` | Normal, published | Yes |
| `DEPRECATED` | Usable; signals consumers to move on | Yes |
| `DISABLED` | Kept, but not served as `branch=latest` content and not used as a rule baseline | Skipped |
| `SUNSET` | Migration deadline passed (usage telemetry, [ADR-0003](0003-usage-telemetry.md)) | Skipped |
| `DRAFT` | Created with `isDraft: true`; content may change | Never added to `latest` |

- A version is created `ENABLED`, or `DRAFT` when `isDraft` is set. Drafts skip rules and write hooks
  unless draft production mode is on.
- `PUT .../versions/{version}/state` moves a version to any state except `DRAFT`; moving to `DRAFT` is
  a 400. No other transition is refused by the REST layer or SQL storage today (`SunsetStateTest`
  moves `ENABLED` straight to `SUNSET`).
- Leaving `DRAFT` runs the artifact's rules against the draft and the write hooks, then adds the
  version to `latest` (and the SemVer branches), and removes it from `drafts`. A rule violation
  keeps it a draft.
- Lookups by `branch=latest` use `RetrievalBehavior.SKIP_DISABLED_LATEST` (`ENABLED`, `DEPRECATED`,
  `DRAFT`), so a disabled or sunset tip falls through to the newest earlier allowed version.
- Lookups by `globalId` or `contentId` do not serve drafts unless draft production mode is on
  (`IdsResourceImpl`).

## Branches

| Branch | Kind | Contents |
|---|---|---|
| `latest` | System | Every non-draft version, in creation order |
| `drafts` | System | Draft versions until they are published |
| `<major>.x`, `<major>.<minor>.x` | System | When `apicurio.semver.branching.enabled=true`; versions parsed (or, with `apicurio.semver.branching.coerce`, coerced) as SemVer |
| Any other ID | User | Created by the client, or with `branches` on version create; append, replace, delete |

A system branch rejects metadata updates, appends, replacement and deletion with `NotAllowedException`
(`SqlBranchRepository`). `apicurio.semver.validation.enabled=true` refuses versions that are not
SemVer.

## Metadata, labels and ownership

| Level | Editable | System fields |
|---|---|---|
| Group | description, labels, owner | `createdOn`, `modifiedBy`, `modifiedOn` |
| Artifact | name, description, labels, owner | `artifactType`, `createdOn`, `modifiedBy`, `modifiedOn` |
| Version | name, description, labels, state | `globalId`, `contentId`, `versionOrder`, `owner`, `createdOn`, `modifiedBy`, `modifiedOn` |
| Branch | description (user branches) | `systemDefined`, `owner`, `createdOn`, `modifiedBy`, `modifiedOn` |

- `owner` is the authenticated principal that created the resource; there is no separate
  `createdBy`. Changing an artifact or group owner goes through `OwnershipTransferAuthorizer`
  ([ADR-0005](0005-per-resource-authorization.md)).
- Labels are a string map. They are kept as given on the resource, and copied, lowercased and
  truncated (key 256, value 512 characters), into label tables used by search.
- Deleting groups, artifacts and versions is off by default (`apicurio.rest.deletion.*`,
  [ADR-0017](0017-operational-guardrails.md)).

### Behaviour across storage variants

| Variant | Behaviour |
|---|---|
| SQL | Sequences in the database (an in-process counter on H2) |
| KafkaSQL | Each ID is requested through a journal message, so every replica gets the same value |
| GitOps, KubernetesOps | Read-only. `globalId`/`contentId` come from the files, or, with `apicurio.polling-storage.deterministic-ids-enabled=true` (default), from SHA-256 of the coordinates / content, in the upper half of the positive `long` range so explicit IDs below it do not collide |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.rest.mutability.artifact-version-content.enabled` | `false` | Allow replacing a `DRAFT` version's content |
| `apicurio.rest.draft.production-mode.enabled` | `false` | Drafts get real hashes, are found by ID and are checked by rules |
| `apicurio.storage.enable-automatic-group-creation` | `true` | Create missing groups on artifact create |
| `apicurio.semver.validation.enabled` | `false` | Refuse non-SemVer versions |
| `apicurio.semver.branching.enabled` | `false` | Maintain `A.x` and `A.B.x` branches |
| `apicurio.semver.branching.coerce` | `false` | Coerce near-SemVer versions for branching |
| `apicurio.polling-storage.deterministic-ids-enabled` | `true` | Derive IDs in GitOps/KubernetesOps when files omit them |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Coordinates | [`GroupId`](../common/src/main/java/io/apicurio/registry/model/GroupId.java), [`GA`](../common/src/main/java/io/apicurio/registry/model/GA.java), [`GAV`](../common/src/main/java/io/apicurio/registry/model/GAV.java), [`VersionId`](../common/src/main/java/io/apicurio/registry/model/VersionId.java), [`BranchId`](../common/src/main/java/io/apicurio/registry/model/BranchId.java) | Validation, default group mapping, system branch names |
| Expressions | [`VersionExpressionParser`](../common/src/main/java/io/apicurio/registry/model/VersionExpressionParser.java) | `version` or `branch=<id>` |
| Storage contract | [`RegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/RegistryStorage.java), [`dto/`](../app/src/main/java/io/apicurio/registry/storage/dto/) | Operations, `RetrievalBehavior`, metadata DTOs |
| REST v3 | [`GroupsResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/GroupsResourceImpl.java), [`IdsResourceImpl`](../app/src/main/java/io/apicurio/registry/rest/v3/impl/IdsResourceImpl.java), [`RestConfig`](../app/src/main/java/io/apicurio/registry/rest/RestConfig.java) | Create, state changes, draft publishing, mutability and deletion switches |
| Versions and IDs | [`SqlVersionRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlVersionRepository.java), [`SqlSequenceRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlSequenceRepository.java), [`CommonSqlStatements`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/CommonSqlStatements.java) | `globalId`, `versionOrder`, generated versions, system branches on create |
| Content | [`SqlContentRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlContentRepository.java), [`RegistryStorageContentUtils`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/RegistryStorageContentUtils.java) | Hashes, deduplication, draft hashes |
| Branches | [`SqlBranchRepository`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlBranchRepository.java), [`SemVerConfigProperties`](../app/src/main/java/io/apicurio/registry/semver/SemVerConfigProperties.java) | Branch tips, system-branch protection, SemVer branches |
| Groups | [`AbstractSqlRegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/AbstractSqlRegistryStorage.java), [`RegistryContentUtils`](../app/src/main/java/io/apicurio/registry/storage/impl/sql/RegistryContentUtils.java) | Implicit groups, default group sentinel, draft publishing |
| Other variants | [`KafkaSqlRegistryStorage`](../app/src/main/java/io/apicurio/registry/storage/impl/kafkasql/KafkaSqlRegistryStorage.java), [`DeterministicIdGenerator`](../app/src/main/java/io/apicurio/registry/storage/impl/polling/DeterministicIdGenerator.java) | Journaled IDs, deterministic IDs |

## Limitations and open work

1. Allowed state transitions are not enforced: any state except `DRAFT` can be set from any state.
   [`VersionStateExt`](../app/src/main/java/io/apicurio/registry/storage/VersionStateExt.java) holds a
   transition table (and a "deprecated" warning log) but nothing calls it, and the OpenAPI description of `SUNSET` ("requires
   transitioning through DEPRECATED first") does not match the behaviour ([#11360](https://github.com/Apicurio/apicurio-registry/issues/11360)).
2. Other drafts still count among the versions a draft or new version is checked against by
   compatibility rules ([#10513](https://github.com/Apicurio/apicurio-registry/issues/10513)).
3. Fetching a disabled version's content reports a missing version rather than a disabled one
   ([#10164](https://github.com/Apicurio/apicurio-registry/issues/10164)).
4. Replacing a draft's content does not update the search index until the draft is published
   ([#9326](https://github.com/Apicurio/apicurio-registry/issues/9326)).
5. The default group is stored as a sentinel string, and group IDs are compared as given except for
   `default`, which is matched case-insensitively by `GroupId` but case-sensitively by
   `RegistryContentUtils.normalizeGroupId` ([#11360](https://github.com/Apicurio/apicurio-registry/issues/11360)).

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| A flat namespace (subject only, as in Confluent) | Not selected: groups carry rules, ownership and authorization scopes; the Confluent-compatible API maps subjects onto groups and artifacts ([ADR-0001](0001-confluent-schema-registry-compatibility.md)) |
| Content-derived IDs everywhere | Used only for read-only GitOps/KubernetesOps; sequences give short, dense IDs for the wire format at the cost of coordination (KafkaSQL journal) |
| One ID per version for content too | Not selected: shared `contentId` lets serdes cache by content and deduplicates storage |
| Mutable versions | Only drafts, behind a switch: published content stays immutable so consumers that cached it by ID stay correct |
| A fixed `latest` pointer per artifact | Generalised to branches, so the same expression mechanism serves `latest`, SemVer lines and user streams |

## Consequences

- **Benefits:** one addressing scheme for names, wire IDs and moving pointers; content stored once;
  immutable published versions; drafts for review workflows; IDs consistent across replicas and
  reloads.
- **Costs:** `contentId` sequences have gaps; the default group needs mapping in every layer; branch
  tips depend on state filters that differ per call site; transition rules exist only as dead code.

## Example and verification

- **Example:** no example is dedicated to the data model.
  [`examples/rules-and-compatibility`](../examples/rules-and-compatibility/) exercises
  `DEPRECATED`, `DISABLED` and a draft that stays a draft after a rule violation;
  [`examples/usage-telemetry-demo`](../examples/usage-telemetry-demo/) moves a version to `SUNSET`;
  [`examples/http-caching`](../examples/http-caching/) reads `branch=latest`.
- **User documentation:**
  [`assembly-registry-concepts-glossary.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-registry-concepts-glossary.adoc),
  [`assembly-managing-registry-artifacts-api.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-managing-registry-artifacts-api.adoc),
  [`assembly-schema-lifecycle-best-practices.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-schema-lifecycle-best-practices.adoc).

| Concern | Tests to read |
|---|---|
| Groups, artifacts, `ifExists`, dry run | `GroupsResourceTest`, `GroupMetaDataTest`, `IfExistsTest`, `DryRunTest` |
| States | `VersionStateTest`, `SunsetStateTest` |
| Drafts | `DraftContentTest`, `DraftProductionModeTest`, `DraftsBranchDdlUpgradeTest` |
| Branches and expressions | `BranchesTest`, `SemVerBranchesTest`, `VersionExpressionParserTest` |
| Ownership and modification | `VersionModifiedByOnTest`, `OwnershipTransferAuthorizerTest` |
| Deterministic IDs | `DeterministicIdGeneratorTest` |

```bash
./mvnw test -pl app -Dtest='GroupsResourceTest,VersionStateTest,SunsetStateTest,DraftContentTest,BranchesTest,SemVerBranchesTest'
./mvnw test -pl common -Dtest=VersionExpressionParserTest
```
