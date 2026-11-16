# ADR-0005: Per-Resource Authorization for Apicurio Registry

- **Status:** Accepted; implementation guide updated 2026-11-05.
- **Original decision date:** 2026-05-09.
- **Author:** Carles Arnal.
- **Tracking:** [#7724](https://github.com/Apicurio/apicurio-registry/issues/7724),
  [enterprise authorization epic #8212](https://github.com/Apicurio/apicurio-registry/issues/8212).
- **Scope:** Server-side authorization of Registry groups, artifacts, and content reached through them.
  Authentication establishes identity; this design decides what that identity may access.

## Reading this document

This is a description of the current implementation, with historical decisions collected at the end.
The original JSON grants design is superseded: **use the Kroxylicious ACL rules format and the
`rules.*` properties documented here**. Java classes retaining the `Grants` prefix implement the
current ACL-based design; the prefix does not imply a JSON format.

The references below point to source files in this checkout, so they follow the code rather than
fixed line numbers. Delivered milestones are listed under [History](#history).

- Start with [the decision](#decision) and [the access model](#access-model).
- To trace a request, follow [point access](#point-access) or [search](#search-and-pagination).
- For deployment, use [configuration](#configuration-and-reload) and the
  [operator guide](../docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc).
- For code changes, use [the source map](#source-map) and [verification map](#verification-map).
- Read [limitations](#limitations-and-open-work) before inferring guarantees about concurrent
  requests or multiple replicas.

## Context

Registry already has global role-based access control (RBAC) and owner-only authorization (OBAC).
RBAC alone cannot express team-specific visibility. OBAC limits writes but does not hide another
team's artifacts from readers. A shared Registry therefore needs a resource-level decision as well
as the existing identity and role checks.

The difficult part is search: filtering a fetched page after authorization can produce short pages,
incorrect totals, and offsets that refer to the wrong result set. A point-decision API alone does
not solve this. The authorization restriction must participate in the query that selects and counts
the page. This does not require every future policy engine to use SQL internally; it requires a
queryable scope consistent with that engine's decisions.

## Decision

1. Use the **Kroxylicious `Authorizer` API** for decisions about operations on named resources.
2. Read **Kroxylicious ACL rules**, but evaluate the parsed rules in Apicurio's `AccessRules`.
3. Publish **`ResourceScopeProvider`** and its TCK as a companion contract for query pre-filtering.
4. Keep identity integration, RBAC, OBAC, owner/admin bypasses, resource resolution, and audit/metrics
   in Registry. An ACL allow is not, by itself, permission to execute an endpoint.
5. Push supported scopes into storage queries before pagination. When a scope cannot be translated,
   fail closed rather than discard its denied selectors.

The root [POM](../pom.xml) currently pins Kroxylicious **0.25.1**. The
[proposal](https://github.com/kroxylicious/design/blob/main/proposals/009-authorizer.md) describes
the shared direction; the pinned parser and Registry tests establish the supported behavior.

### Why Registry has its own evaluator

[AccessRules](../authz/src/main/java/io/apicurio/authz/AccessRules.java) uses Kroxylicious's
`AclRulesLexer`/`AclRulesParser`, validates imported types and operations, and creates both decisions
and scopes from one parsed rule set. It does not delegate production decisions to `AclAuthorizer`.

The implementation records two reasons for this choice: class resolution in
`AclAuthorizerService` conflicts with Quarkus test/dev class loaders, and upstream 0.25.1 has
prefix-matching defects. `AccessRulesTest` compares compatible behavior and tests Registry's
regressions. Upgrading the dependency does not automatically remove the custom evaluator; a switch
requires checking class loading, matching, operation implication, and scope conformance again.

## Access model

### Subjects and roles

`GrantsAccessController.currentSubject()` converts the current Quarkus `SecurityIdentity` into:

- an authenticated caller: one Kroxylicious `User` containing the principal name, plus an Apicurio
  `Role` for each entry in `SecurityIdentity.getRoles()`;
- an anonymous caller: only `Role("$anonymous")`, with no `User` principal.

Rules match the **exact principal class** and name: a user named `ops` is not a role named `ops`.
`User` defaults to `io.kroxylicious.proxy.authentication.User`; `Role` is explicitly imported as
`io.apicurio.authz.Role`. Do not infer ACL role principals from RBAC decisions: RBAC can use the
token, application, header, or Kubernetes role provider, while ACL subjects are built from the
security identity's roles. Check both mappings when configuring a non-token role source.

### Resources and names

The current Registry initializer supports two resource types, the `Artifact` and `Group` enums in
`authz`. A version, branch, or comment is normally authorized as its **artifact**, not as an
independent resource. Content IDs/hashes are an indirect resource resolved through their users.

[AuthorizationNames](../app/src/main/java/io/apicurio/registry/storage/dto/AuthorizationNames.java)
defines the naming convention:

| Resource | Name passed to the authorizer | Example |
|---|---|---|
| Group | Group ID; default group is `default` | `team-a` |
| Artifact | `escape(groupId) + "/" + artifactId` | `team-a/order` |
| Artifact in default group | `default/` followed by artifact ID | `default/order` |

Only the **group component of an artifact name** escapes `%` as `%25` and `/` as `%2F`.
Group resource names and artifact IDs are not escaped. Thus group `team-a/sub`, artifact `x`
becomes `team-a%2Fsub/x`; group `team-a`, artifact `sub/x` becomes `team-a/sub/x`.
This is an authorization naming scheme, not URL encoding.

Names are case-sensitive. On a database that resolves IDs case-insensitively, point access first
resolves an existing resource to its **stored spelling**. A request for `TEAM-A/VICTIM` must not
escape a deny on stored `team-a/victim`. Missing artifacts retain their requested artifact ID but
use the existing group's stored spelling when available. Default-group representations normalize
to the same authorization name.

### Operations and denies

Both enums have `READ`, `WRITE`, and `ADMIN`. Operation expansion is the same for **allow and deny**:

| Operation written in a rule | Operations covered |
|---|---|
| `READ` | `READ` |
| `WRITE` | `WRITE`, `READ` |
| `ADMIN` or `*` | `ADMIN`, `WRITE`, `READ` |

A matching deny takes precedence over matching allows; without an allow, access is denied.
This precedence is within ACL evaluation. An owner/admin bypass can skip ACL evaluation entirely.

**A deny is not the inverse of an allow hierarchy.** Denying `READ` does not deny `WRITE` or
`ADMIN`. Denying `WRITE` denies reads too, but does not deny `ADMIN`. To exclude a resource from all
operations, deny `*`; to grant read-only access, allow `READ` without a matching broader allow.
For example, with an allow of `ADMIN` on a resource:

| Additional matching deny | READ | WRITE | ADMIN |
|---|---|---|---|
| None | allow | allow | allow |
| `READ` | deny | allow | allow |
| `WRITE` | deny | deny | allow |
| `*` | deny | deny | deny |

Endpoint annotations choose the operation:

| `AuthorizedLevel` | ACL operation | Other relevant behavior |
|---|---|---|
| `Read` | `READ` | Anonymous/authenticated read settings can bypass the role check |
| `Write` | `WRITE` | Includes ordinary core metadata changes and deletes; OBAC can restrict it |
| `Admin` | `ADMIN` | Used for example by Iceberg namespace/table/view drops |
| `AdminOrOwner` | `ADMIN` | RBAC permits an administrator or the owner; not the same as `Admin` |
| `None` | No ACL check | Unprotected level; distinct from resource **style** `None` |

`RoleBasedAccessController` may downgrade its own `Write` check to `Read` for an annotated dry run.
That does not rewrite the level used by OBAC or the grants controller. Do not assume that a dry run
universally requires only read permission.

### A complete rules example

```text
from io.apicurio.authz import Artifact, Group, Role;

deny User with name = "bob" to * Artifact with name = "team-a/secret";
allow Role with name = "team-a-dev" to WRITE Group with name = "team-a";
allow Role with name = "team-a-dev" to WRITE Artifact with name like "team-a/*";
allow Role with name = "$anonymous" to READ Group with name = "public";
allow Role with name = "$anonymous" to READ Artifact with name like "public/*";
otherwise deny;
```

The parser requires denies before allows and a final `otherwise deny;`. Use `//` comments.
Selectors are equality (`=`), a set (`in`), a trailing-wildcard prefix (`like "team-a/*"`), all
names (`with name *`), or a full RE2 match (`matching /regex/`, resources only).
`like` is not a general glob or a SQL LIKE expression.

For this example, suppose Bob has the `team-a-dev` identity role and the RBAC developer role,
OBAC is disabled, and Bob does not own `team-a/secret`. He may read/write other artifacts in
`team-a`, and create new names there, but cannot access `secret`. If Bob owns `secret` and owner
bypass is enabled, the ownership exception overrides that ACL deny. Disable owner bypass if ACL
denies must apply to owners too. Anonymous reads require the anonymous-read setting in addition
to the two explicit rules.

Group and artifact rules are independent: a group grant does not grant its artifacts, and a group
deny does not hide them from global artifact search. Listing a group's artifacts checks the group
first and filters the artifacts second. It is therefore possible to read an artifact directly but
be unable to list its group.

## Point access

The authoritative entry point is
[AuthorizedInterceptor.authorizeMethod](../app/src/main/java/io/apicurio/registry/auth/AuthorizedInterceptor.java).
The following order matters:

1. Role-mapping API operations require application-managed RBAC, even before the general bypasses.
2. A non-anonymous request with a proxy-header credential bypasses local authorization when trusted
   proxy authorization is enabled. A normal OIDC/basic request does not get this bypass merely
   because the option is enabled.
3. If `AuthConfig.isAuthenticationEnabled()` is false, proceed without authorization. Enabling the
   resource-authorization option alone does not enable authentication.
4. An anonymous caller may use level `None`. For level `Read`, anonymous reads must be enabled and,
   when grants are enabled, the grants check must pass. Other anonymous access is rejected with 401.
5. Authenticated level `None` requests proceed; a configured admin override also proceeds.
6. Authenticated-read access skips the RBAC read check, but still applies grants. Otherwise, when
   RBAC is enabled, the role provider checks the level.
7. OBAC, when enabled, restricts writes; RBAC administrators are exempt. Groups are owner-restricted
   only when `owner-only-authorization.limit-group-access` is enabled.
8. When grants are enabled, RBAC administrators bypass them. Otherwise the strict owner bypass or
   an ACL allow must permit the addressed resource.

With default RBAC role names, read-only users can read, developers can read/write, and administrators
can read/write/administer. These checks apply **only when RBAC is enabled**. RBAC is optional;
there is no requirement to enable it in order to use resource rules. Conversely, with RBAC disabled,
`sr-admin` is not automatically a grants bypass unless a separate admin override applies.

### Ownership is two different checks

- **OBAC** (`isOwnerOf`): a write restriction. It is intentionally lenient for missing resources
  and resources with no owner; the endpoint decides whether the operation is valid.
- **Owner bypass** (`isStrictOwner`): an exception to ACL restrictions. It requires an existing
  resource whose recorded owner is the authenticated caller. It is independent of OBAC and can
  be disabled. It does not override an earlier RBAC denial.

For versions these checks use the artifact owner, not the version creator. Ownership changes in
core v3 additionally use
[OwnershipTransferAuthorizer](../app/src/main/java/io/apicurio/registry/auth/OwnershipTransferAuthorizer.java)
inside the metadata-update endpoint. A `write` permission flag is not sufficient evidence that
an ownership transfer will succeed.

### Resolving the target

[AbstractAccessController.resolveResource](../app/src/main/java/io/apicurio/registry/auth/AbstractAccessController.java)
is shared by OBAC and grants:

| `AuthorizedStyle` | Resource resolution |
|---|---|
| `GroupAndArtifact` | Artifact from group/artifact parameters |
| `GroupOnly` | Group parameter |
| `ArtifactOnly` | Confluent subject plus group header or configured group-concat convention |
| `GlobalId` | Coordinates of the global ID, then its artifact |
| `ContentId`, `ContentHash` | Artifacts using the content |
| `CCompatSchemaId` | Content ID normally; global ID in legacy-ID mode |
| `QualifiedArtifactName` | MCP server name split into namespace/group and artifact |
| `IcebergNamespace`, `IcebergTable` | Namespace/group and optional table/artifact; not catalog prefix |
| `None` | No single resource resolved; role/authentication checks still apply |

Content access uses **any-of** semantics: one readable artifact using the content (or one eligible
owner bypass) is sufficient. Unknown IDs resolve to content with no users, so a restricted caller
cannot obtain an accidental bypass. An administrator can reach the endpoint's own 404 instead.

`GrantsAccessController.isAuthorized` permits style `None` without a single-resource ACL check.
Body-addressed targets therefore need an explicit
[ResourceAccessGuard](../app/src/main/java/io/apicurio/registry/auth/ResourceAccessGuard.java) check;
list/search endpoints need filtering; unfilterable registry-wide reports use
`requireUnrestrictedRead`. **`requireAccess` adds OBAC/grants, not authentication and RBAC**:
the endpoint still needs its annotation or an equivalent outer check.

### Following a concrete request

For an authenticated, non-admin `GET /apis/registry/v3/ids/globalIds/{id}`:

1. `IdsResourceImpl` declares style `GlobalId`, level `Read`; the interceptor applies its gates.
2. Resource resolution uses `AuthorizationLookups.coordinates` to identify the artifact the global
   ID names, reading storage once per request.
3. The owner bypass, if enabled, looks up the artifact's current-request metadata. Otherwise
   `isAllowed(Read, resource)` canonicalizes the name and evaluates `Artifact.READ` for the caller.
4. After authorization, the endpoint loads content and applies its own existence/state checks.
   Resolving the artifact does not by itself make a deleted or disabled version readable.
5. If dereferencing is requested, the reference loader checks each reached artifact separately
   from the root's permission. Access to the root never implies access to its dependencies.

For `GET /apis/registry/v3/search/artifacts`, there is no single artifact in the request to
authorize. The outer read gate runs, then the scope limits the query and its count. Optional
permission reporting evaluates operations on the selected page; it does not replace filtering.

### Multiple resources and generated writes

- Creating an artifact requires a group write and a write on the new artifact name. The name does
  not exist yet, so it cannot acquire an owner bypass before authorization.
- Existing-artifact `ifExists` paths must check the existing artifact, not rely on the group's
  permission. Deleting a group/all its artifacts checks the affected artifacts too.
- References included in dereferencing, canonicalization, Protobuf export, and Confluent resolved
  content use **all-of** semantics: every reached artifact must be readable. This differs from
  the any-of rule for shared content IDs.
- Submitted references, including those of drafts, are checked before write-hook planning.
  Generated targets have their own write checks through `requireGeneratedArtifactWrite`.

Embedded-schema extraction now uses
[ContentPlan](../app/src/main/java/io/apicurio/registry/extensions/ContentPlan.java): predict the
rewrite and reference coordinates, expose planned content to rules, run rules and `beforePublish`,
then commit only when not a dry run. `EmbeddedSchemaService` uses
`createArtifactVersionIfLatest` to reject a coordinate race and compensates earlier field writes
when a later field commit fails. This fixes the rejection-before-commit orphan case from #10603.
It is **not a general transaction over arbitrary third-party hooks and the parent write**; the
default `planContent` delegates to legacy `prepareContent`, which cannot make a legacy writing
hook side-effect-free. See
[ArtifactVersionWriteHook](../app/src/main/java/io/apicurio/registry/extensions/ArtifactVersionWriteHook.java).

### Reference batching (#10883)

`GrantsAccessController.isAllowedForAll`, added in #10883, does the following: canonicalize names,
deduplicate actions by stored resource, call `authorize()` once, and record the resulting decisions.
`ResourceAccessGuard` removes strict-owner bypasses before submitting the batch.

[AuthorizingReferenceLoader](../app/src/main/java/io/apicurio/registry/references/AuthorizingReferenceLoader.java)
extends the existing loader shape with an `authorize(collection)` operation. Loading a reference
individually still authorizes it. A guard-created loader remembers successful authorizations for
its own lifetime, keyed by group/artifact as supplied (not version); it is not a cross-request cache.

| Traversal | Batch boundary |
|---|---|
| `ReferenceResolution` / `ReferenceGraphWalker` | Distinct target coordinates at each breadth-first level |
| `RegistryContentUtils.resolveReferencesWithContext` | Siblings in a document before depth-first loading; invalid sibling lists retain the previous per-item error path |
| `ProtobufExporter.collectReferences` | Unvisited imports of each file before depth-first loading |
| `HookPlan.loader` | Preserves batching through `AuthorizingReferenceLoader.preferring`; planned content is authorized but need not be read from storage |

There can be multiple walks in a request (for example submitted-reference checks followed by rule
resolution). The guarantee is **one authorizer call per nonempty batch needing ACL evaluation**,
not one per request. Already-authorized references and bypasses can need zero calls. Metadata
lookups are still per distinct lookup key and shared by the request lookup layer.

On denial, content in that batch is not loaded. Earlier levels may already have been loaded to
discover it; authorization itself can read metadata. Do not interpret this as "no reads occurred."
A denial now takes precedence over a missing sibling that the old loader would have visited first.
Non-authorization loader errors retain the existing traversal-specific behavior (some are logged
and skipped); a `ForbiddenException` must propagate. Protobuf export collects references before
streaming so it can still return an error response rather than a partially written ZIP.

## Search and pagination

The paginated search path is:

```text
Endpoint authentication / read-role gate
  -> ISearchAuthorizer (SearchAuthorizerProducer)
  -> GrantsSearchFilter: scope for Artifact.READ or Group.READ
  -> AuthorizationFilter, combined with the user's normal search criteria
  -> storage query: authorization restriction before count, offset, limit
  -> result conversion, then optional v3 permissions decoration
```

[ResourceScopeProvider](../scope-api/src/main/java/io/apicurio/scope/ResourceScopeProvider.java)
returns `Unrestricted`, `Forbidden`, or `Scoped` (allow-all plus allowed/denied exact names,
prefixes, and RE2 patterns). At the contract level, `scope.matches(name)` must agree with the
authorizer's decision for that subject, operation, and policy version. The TCK tests this property.

For scopes the store can translate, selection is conceptually:

```text
normal search criteria
AND ((ACL allow matches AND no ACL deny matches) OR eligible owner matches)
```

The owner term is outside the ACL deny term deliberately: it reproduces owner bypass. Version
search uses artifact ownership. Unrestricted callers/scopes require no ACL predicate.

### Translation and boundaries of the invariant

- **SQL:** `SqlAuthorizationFilter` translates names via `AuthorizationNames` for group, artifact,
  and version queries. It binds values and escapes LIKE metacharacters. MySQL uses binary
  comparisons; SQL Server uses a binary collation. Equality/prefix conditions also include an
  ordinary index-usable comparison on these dialects. H2/PostgreSQL are case-sensitive by default.
  The SQL query layer is reused by the storage variants; KafkaSQL uses its local SQL view.
- **Elasticsearch:** authorization uses term/prefix queries over names. The
  `ElasticsearchSearchDecorator` resolves owner matches from primary storage rather than relying
  on stale indexed ownership. It supplies **at most 1000 owned artifact names** and logs when the
  cap is reached. Indexed data can lag primary storage. Unsupported combinations of content or
  structure filters with hash filters return 400 rather than partially evaluating the query.
- **Regex scopes:** the point evaluator supports full RE2 matches, but SQL/Elasticsearch search
  does not translate them. For a restricted caller whose relevant scope contains patterns,
  `GrantsSearchFilter` selects only eligible owned resources, or none without owner bypass.
  It does not simply ignore the regex rule and retain other ACL allows.

Thus policy-scope equivalence is an API invariant, but **exact search/point parity has explicit
implementation limits**: pattern scopes, the indexed owner cap, index freshness, and concurrent
policy/data changes. Counts describe the query's selected result set (when the endpoint requests
a count); no promise is made that every page is full, or that separate requests share a snapshot.

Not every list uses a paginated search: branch-version lists are gated on the parent artifact;
inbound-reference/content-user lists use per-item visibility checks. The outbound reference graph
can retain reference coordinates while suppressing an unreadable target's metadata. Raw stored
content can itself contain references. This design does not conceal every occurrence of a hidden
artifact's name; it prevents unauthorized content/metadata access through protected paths.

## Permissions reported in v3 results

[SearchResultPermissions](../app/src/main/java/io/apicurio/registry/rest/v3/impl/SearchResultPermissions.java)
adds an optional object to `SearchedArtifact`, `SearchedVersion`, and `SearchedGroup`:

```json
{
  "permissions": {"read": true, "write": false, "admin": false}
}
```

It is present when resource-based authorization is enabled, and omitted when disabled. It is
v3-only and does not change result membership. It covers artifact/group/version searches,
content searches, contract search returning searched artifacts, and corresponding group/artifact/
version/branch-version lists.

`ResourcePermissionsEvaluator` applies the caller-wide gates and per-resource owner rules, with
at most one quiet `allowedActions` call per page. Empty/bypassed pages need no authorizer call.
Artifact/group owners come from the result data; versions use the artifact metadata through
`AuthorizationLookups`. Owner lookups are skipped when ownership cannot affect the answer.

The flags describe authorization **at those levels**, not guaranteed endpoint success:

- `write` is not "can perform every mutation": ownership transfer, state, mutability settings,
  validation rules, referenced artifacts, and group-wide deletes can impose additional checks.
- `admin` means `AuthorizedLevel.Admin`, not `AdminOrOwner`, not "can delete a core artifact",
  and not "can transfer ownership". Core deletes generally require `Write`; Iceberg drops use
  `Admin`.
- An owning developer with RBAC enabled can have `{read:true, write:true, admin:false}`.
  With RBAC disabled, an owner bypass can also allow `Admin`; there is no unconditional rule that
  every non-administrator must receive `admin:false`.
- Policy or ownership can change after the result is returned. The flags never authorize a later
  request, and the UI must still handle authorization failures.

## Configuration and reload

These are the defaults in `GrantsAccessControllerConfig`:

| Property | Default | Meaning |
|---|---|---|
| `apicurio.auth.resource-based-authorization.enabled` | `false` | Enable rules integration; does not enable authentication |
| `apicurio.auth.resource-based-authorization.rules.path` | none | Required rules file when enabled |
| `apicurio.auth.resource-based-authorization.rules.reload-enabled` | `true` | Register the polling job |
| `apicurio.auth.resource-based-authorization.rules.reload-every` | `5s` | Poll interval |
| `apicurio.auth.resource-based-authorization.owner-bypass.enabled` | `true` | Let eligible owners bypass ACL rules |

Related settings, defined in `AuthConfig`, are independent:

| Property | Default |
|---|---|
| `apicurio.auth.role-based-authorization` | `false` |
| `apicurio.auth.owner-only-authorization` | `false` |
| `apicurio.auth.owner-only-authorization.limit-group-access` | `false` |
| `apicurio.auth.anonymous-read-access.enabled` | `false` |
| `apicurio.auth.authenticated-read-access.enabled` | `false` |
| `apicurio.auth.role-source` | `token` |
| `apicurio.auth.admin-override.enabled` | `false` |
| `apicurio.authn.proxy-header.trust-proxy-authorization` | `false` |

Authentication is configured separately (OIDC, basic, proxy header, Kubernetes, or form as recognized
by `AuthConfig`). When rules are enabled without authentication, startup logs a warning but the
authorization bypass remains. The old `grants.path`/`grants.reload-*` keys and JSON
`config.admin_roles` are not part of this configuration. A role allowed all ACL operations remains
subject to enabled RBAC/OBAC; it is not equivalent to an admin override.

### File lifecycle

`GrantsAccessControllerInitializer` eagerly loads the file and supports only `Artifact` and `Group`.
Missing paths/files, invalid syntax/operations/types, and rules for unsupported resource types fail
startup. It registers the `apicurio-access-rules-reload` scheduler job only when both feature and
reload are enabled, with concurrent executions skipped.

`AccessRulesAuthorizer.checkForChanges()` compares file modification time, not a content hash.
A valid changed file replaces the decision/scope pair together via a volatile `Rules` reference.
A rejected changed version keeps the last good rules and records its timestamp, avoiding retries
of the same invalid version. Fixing it must also change the timestamp. A missing/non-regular file
on reload leaves the current rules active; that path returns without logging a reload error.

Reload is process-local. Each replica must receive the file update and poll it. A request making
several authorization/scope calls is not pinned to one policy version, although an individual
`authorize()` batch uses a single rule set. Publishing an update is not an instantaneous,
cluster-wide revocation barrier. Mount ConfigMaps as directories rather than `subPath` mounts
when relying on updates; see the operator guide for deployment details.

### Parser and sharing constraints

The format is shared; arbitrary mixed-system files are not automatically portable. Registry rejects
rules for resource types it does not enforce, even if their classes exist. A used imported class
must be resolvable and assignable to the expected principal/resource API type. Other systems must
also use compatible principal classes; matching a similarly named class is insufficient.

With the pinned grammar, use `Role("$anonymous")` rules instead of the proposal's `anonymous User`
syntax. Block comments are not supported as comments. Literal processing removes escaping
backslashes, including in regexes: `/a\.b/` becomes `a.b`. Avoid inferring regex semantics from
another language's string escaping; test the exact file against the pinned parser.

## Caches and consistency

### Request-local metadata

[AuthorizationLookups](../app/src/main/java/io/apicurio/registry/auth/AuthorizationLookups.java)
is an application-scoped facade over request-scoped `RequestAuthorizationLookups`. It memoizes
artifact/group/version metadata and content users within the request, including missing resources.
Without an active request context, those lookups go directly to storage. Keys preserve requested
spelling except for default-group normalization; different case spellings can cause extra reads.

The first result is reused for that request, not refreshed after each check. A missing resource
stays missing even if the same request subsequently creates it. Authorize before writing; do not
reuse these lookups to establish the ownership of something just created. This memoization is not
a transaction or a concurrency lock.

### Global IDs are resolved in every request

A global ID's coordinates are remembered for the current request only, like every other lookup.
They are deliberately **not cached across requests**. Ordinary sequence allocation never reuses a
deleted global ID, but a data import that preserves global IDs (the default) can rebind an ID to a
different artifact. A cross-request cache would then authorize a request against the artifact the ID
named before, while the endpoint serves the artifact it names now; a cache local to one process also
cannot be invalidated on other replicas. Release 3.4.0 development briefly had such a cache
(#10849); it was removed before release, together with its `apicurio.storage.global-id-cache.*`
properties. `GrantsAuthorizationTest.aGlobalIdRebindByImportIsAuthorizedAgainstTheNewArtifact` and
`AuthorizationLookupsTest` cover this.

Any future cross-request metadata cache (#10600) must be safe across replicas and imports: it must
never make an authorization decision from data a later request could see differently.
Decision caching is not implemented.

## Observability and failure diagnosis

`GrantsAccessController.record` records ACL decisions through `OTelMetricsProvider` and logs denials
at INFO to `io.apicurio.registry.audit.authz`:

```text
authz.denied user="bob" operation="read" resource_type="artifact" resource="team-a/secret"
```

The `apicurio.authz.decisions` counter uses attributes `apicurio.registry.result` (`allow`/`deny`),
`apicurio.registry.entity_type` (`artifact`/`group`/`content`), and `apicurio.registry.operation`
(lowercased level name, including `adminorowner` where used). Names are sanitized for logging.

These are **ACL-decision counts, not HTTP-request counts**. Owner/admin bypasses and earlier
RBAC/OBAC rejections need not produce an ACL decision. Shared-content checks record an aggregate
content decision; permission reporting uses quiet `allowedActions` and records none. In #10883,
a batch records all evaluated canonical actions, even if one denies; an old per-item walk could
stop at the first denial. Deduplication and loader memoization also change counts. Preserving
observability does not mean preserving the exact number of events per request.

An authenticated point denial normally returns 403 with
`User is not authorized to perform the requested operation.` Anonymous point denials in the
interceptor return 401. Indirect checks can throw `ForbiddenException` after the outer gate,
including on an anonymous read path. API families have their own error envelopes; internal
resource names in audit logs must not become stack traces or internal errors in client responses.

| Symptom | First checks |
|---|---|
| Matching allow, but 403 | RBAC role/provider, OBAC, operation mapping, stored resource name, explicit denies, then referenced/generated targets |
| Deny rule, but access succeeds | Authentication actually enabled? Trusted proxy/admin bypass? Resource owner bypass? Is the denied operation the one required? |
| Direct read succeeds, but search omits it | Group gate versus artifact grant; regex scope fallback; indexed owner cap/freshness; ordinary search criteria |
| `write:true`, but mutation fails | Ownership-transfer check, descendant/group checks, referenced/generated artifacts, mutability/state/rules, or changes since search |
| No `authz.denied` for a rejected request | It may have failed before ACL evaluation, or used a different guard/error path |
| Rules edit has no effect | Feature/reload enabled, correct file per replica, modification time changed, valid file, ConfigMap projection updated |

## Source map

Paths below are repository-relative; within each directory the listed names are Java classes.

| Layer | Source | Responsibility |
|---|---|---|
| Shared contract | [scope-api](../scope-api/src/main/java/io/apicurio/scope/) | `ResourceScopeProvider`, sealed `ResourceScope`; companion to Kroxylicious `Authorizer` |
| Conformance | [scope-api-tck](../scope-api-tck/) | Provider/decision equivalence tests |
| Rules engine | [authz](../authz/src/main/java/io/apicurio/authz/) | `AccessRules`, `AccessRulesAuthorizer`, `Artifact`, `Group`, `Role` |
| Identity/outer gates | [auth](../app/src/main/java/io/apicurio/registry/auth/) | `AuthorizedInterceptor`, `AuthConfig`, `AdminOverride`, role providers, `OwnerBasedAccessController` |
| Resource resolution | [AbstractAccessController](../app/src/main/java/io/apicurio/registry/auth/AbstractAccessController.java) | Annotation styles, canonical resource lookup inputs, strict/lenient ownership |
| Integration | [grants](../app/src/main/java/io/apicurio/registry/auth/grants/) | Controller, startup/reload, `GrantsSearchFilter` |
| Explicit checks | [ResourceAccessGuard](../app/src/main/java/io/apicurio/registry/auth/ResourceAccessGuard.java) | Body targets, indirect reads, generated targets, bulk deletes |
| Query restriction | [storage DTOs](../app/src/main/java/io/apicurio/registry/storage/dto/) | `AuthorizationFilter`, `AuthorizationNames` |
| SQL translation | [SqlAuthorizationFilter](../app/src/main/java/io/apicurio/registry/storage/impl/sql/repositories/SqlAuthorizationFilter.java) | Bound, dialect-aware predicates |
| Index translation | [ElasticsearchSearchService](../app/src/main/java/io/apicurio/registry/storage/impl/search/ElasticsearchSearchService.java), [decorator](../app/src/main/java/io/apicurio/registry/storage/decorator/ElasticsearchSearchDecorator.java) | Indexed query restrictions and live ownership names |
| Permission reporting | [ResourcePermissionsEvaluator](../app/src/main/java/io/apicurio/registry/auth/ResourcePermissionsEvaluator.java), [SearchResultPermissions](../app/src/main/java/io/apicurio/registry/rest/v3/impl/SearchResultPermissions.java) | Quiet page-level evaluation and v3 DTO decoration |
| Reference traversal | [references](../app/src/main/java/io/apicurio/registry/references/), [RegistryContentUtils](../app/src/main/java/io/apicurio/registry/storage/impl/sql/RegistryContentUtils.java), [ProtobufExporter](../app/src/main/java/io/apicurio/registry/rest/v3/impl/shared/ProtobufExporter.java) | Graph walking, contextual rewriting, export; batching additions in #10883 |
| Write planning | [GroupsResourceImpl](../app/src/main/java/io/apicurio/registry/rest/v3/impl/GroupsResourceImpl.java), [EmbeddedSchemaService](../app/src/main/java/io/apicurio/registry/agents/services/EmbeddedSchemaService.java) | Plan/check/commit ordering for generated schemas |

Published module coordinates are `io.apicurio:apicurio-authz-core`,
`io.apicurio:apicurio-scope-api`, and `io.apicurio:apicurio-scope-api-tck`, versioned with Registry.
`authz` depends on the Kroxylicious authorizer API/ACL parser, scope API, ANTLR, RE2/J, and SLF4J;
it is not the retired Jackson-based JSON grants implementation.

## Verification map

Tests are executable examples of the boundaries, not proof that every combination or race is safe.

| Concern | Tests to read |
|---|---|
| Grammar, implications, denies, principal matching | `authz`: `AccessRulesTest` |
| File startup/reload and unsupported types | `authz`: `AccessRulesAuthorizerTest` |
| Scope/decision contract | `authz`: `AccessRulesConformanceTest`; `scope-api-tck` |
| Stored-name/case behavior and batches | `app`: `GrantsAccessControllerTest`, `AuthorizationNamesTest` |
| Query translation and dialect behavior | `SqlAuthorizationFilterTest`, `AuthorizationFilterStorageTest` and PostgreSQL/MySQL/MSSQL subclasses; `ElasticsearchAuthorizationQueryTest` |
| End-to-end ACL integration | `GrantsAuthorizationTest`, `GrantsInteractionsTest`, `GrantsArtifactScopeTest`, `GrantsIndirectAccessTest` |
| Filter fallback and bypasses | `GrantsSearchFilterTest`, `AuthorizedInterceptorReadParityTest` |
| Reported flags versus direct access | `SearchPermissionsTest`, `ResourcePermissionsEvaluatorTest`, `SearchResultPermissionsTest`; omission check in `SearchArtifactsTest` |
| Lookup behavior, global ID rebinding | `AuthorizationLookupsTest`, `GrantsAuthorizationTest` |
| Generated-schema rejection/races | `EmbeddedSchemaExtractionRejectionTest`, `EmbeddedSchemaServiceTest` and their storage-specific subclasses |
| Batching additions in #10883 | `ResourceAccessGuardBatchTest`, `AuthorizingReferenceLoaderTest`, `ReferenceResolutionTest`, `RegistryContentUtilsTest`, `ProtobufExporterTest` |

From the repository root, after building the dependencies as described in
[DEVELOPING.md](../DEVELOPING.md), useful focused runs are:

```bash
./mvnw test -pl authz -am -Dtest=AccessRulesTest,AccessRulesAuthorizerTest,AccessRulesConformanceTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw test -pl app -Dtest=GrantsAccessControllerTest,GrantsAuthorizationTest,GrantsInteractionsTest,GrantsIndirectAccessTest,SearchPermissionsTest
```

The [per-resource authorization example](../examples/per-resource-authorization/) supplies
`access.rules` and `demo.sh` for a running-system walkthrough. It demonstrates point decisions,
search filtering, anonymous reads, and denied creation; consult its README for ports and setup.

When adding an endpoint, trace its annotation level/style, any body or indirectly accessed targets,
search/filter strategy, and response fields. Add both allowed and denied cases, with state checks
after rejected writes. For batching, count actual `authorize()` calls as well as testing outcomes.
For storage changes, exercise supported dialects and search backends. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for the project's verification requirements.

## Limitations and open work

1. **Concurrent import:** authorization and the operation run against live storage, but not in one
   transaction; an import running concurrently with a request can change what the request reads.
2. **No general request snapshot:** metadata memoization and atomic rule-set publication do not
   pin policy, identity roles, ownership, and data to one transaction across a request.
3. **Search expressiveness:** regex scope fallback, indexed ownership cap/freshness, and supported
   filter combinations constrain exact parity with direct access.
4. **File-based operation:** each replica receives/polls its own file. Large selector sets produce
   larger query predicates and operational editing overhead. There is no established hard
   "500-grant ceiling" or guaranteed microsecond latency; benchmark representative rules and data.
5. **External authorizers are future integration work:** the current initializer/controller uses
   concrete `AccessRulesAuthorizer`, not a pluggable arbitrary remote `Authorizer`. A replacement
   must also solve scope production, failure behavior, identity mapping, lifecycle, and latency.
   Batching is preparation, not delivery of OPA/OpenFGA support.
6. **Additional caches and UI behavior:** #10600 tracks further work; v3 permission fields are
   delivered but do not by themselves implement UI action visibility. They are advisory.
7. **No universal multi-artifact transaction:** write planning removes early generated writes
   from the built-in extraction path, but does not make arbitrary extension hooks transactional.

## Alternatives and consequences

| Alternative | Decision and trade-off |
|---|---|
| OPA/WASM or an external point-decision service | Not the current implementation. Arbitrary policies need an additional search-scope strategy; evaluating decisions alone does not preserve paginated queries. Future integrations remain possible. |
| Keycloak authorization services/UMA | Not selected: resource synchronization and queryable visibility would add coupling and lifecycle complexity. Authentication can still use Keycloak. |
| Proxy-only authorization | Insufficient to implement resource-filtered result sets inside Registry; trusted-proxy bypass is an explicit deployment trust choice, not search filtering. |
| Zanzibar/OpenFGA | Not selected for the initial flat named-resource rules. Relationship-based authorization is a separate tracked integration, not inherently impossible. |
| Database ACL tables and joins | Deferred: queryable and potentially useful at scale, but requires schema, management API, migrations, and synchronization decisions. |
| Over-fetch/post-filter pages | Not selected for exact paginated search. Correct totals would require evaluating the whole matching population; simply filtering one page is incorrect. |
| Upstream `AclAuthorizer` implementation | Shared API and grammar adopted; production evaluator deferred for the implementation-specific reasons above. |

Benefits are query-level filtering, consistent resource naming across API families, reusable
contracts, no remote policy service required today, and reload without restart. Costs are the
parser dependency, maintenance of a custom evaluator, explicit handling of all indirect access
paths, query complexity from selectors, and distributed consistency concerns. These trade-offs
are more useful design boundaries than an assumed fixed rule count or latency claim.

## History

| Milestone | Result |
|---|---|
| [#7829](https://github.com/Apicurio/apicurio-registry/pull/7829) | Initial per-resource authorization and query-filter design; JSON grants were a development format |
| [#10746](https://github.com/Apicurio/apicurio-registry/pull/10746), amendment 1 (2026-10-27) | Replaced JSON grants with Kroxylicious ACL rules before their first release; retired `GrantsAuthorizer`, `GrantsData`, `Grant`, `SearchFilterData`, and `RolePrincipal` |
| [#10753](https://github.com/Apicurio/apicurio-registry/pull/10753), amendment 2 (2026-10-28) | Published resource-scope API/TCK and integrated the shared contracts |
| [#10807](https://github.com/Apicurio/apicurio-registry/pull/10807) | Plan/check/commit for embedded schemas; resolves the early-write rejection case in #10603 |
| [#10849](https://github.com/Apicurio/apicurio-registry/pull/10849) | Cross-request global-ID coordinate cache; removed before release, because an ID-preserving import can rebind a global ID (see "Global IDs are resolved in every request") |
| [#10858](https://github.com/Apicurio/apicurio-registry/pull/10858) | v3 search/list permission reporting for #10599 |
| [#10882](https://github.com/Apicurio/apicurio-registry/pull/10882) | Renumbered this ADR from `001` to `0005`, avoiding a duplicate ADR 1 |
| [#10883](https://github.com/Apicurio/apicurio-registry/pull/10883) | Batches reference authorization: one authorizer call per level or sibling set |

Earlier versions of this document are retained in Git history. They explain the development JSON
format and initial alternatives; they must not be used as current configuration instructions.
