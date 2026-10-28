# ADR-001: Per-Resource Authorization for Apicurio Registry

- **Status:** Accepted
- **Date:** 2026-05-09
- **Authors:** Carles Arnal
- **PR:** [#7829](https://github.com/Apicurio/apicurio-registry/pull/7829)
- **Issue:** [#7724](https://github.com/Apicurio/apicurio-registry/issues/7724)
- **Amended:** 2026-10-27: Registry adopts the Kroxylicious ACL rules format
  ([proposal 009](https://github.com/kroxylicious/design/blob/main/proposals/009-authorizer.md))
  in place of the JSON grants format. See "Amendment 1" below.

## Context

Apicurio Registry supports two authorization models:

- **RBAC** — global roles (`sr-readonly`, `sr-developer`, `sr-admin`) that apply to all resources equally
- **OBAC** — owner-based access control, where only the artifact creator can modify it

Neither supports per-resource visibility or access rules. A developer with `sr-developer` can read and write every artifact in every group. There is no way to express "Alice can write to team-a artifacts but not team-b artifacts," and no way to prevent team-b's schemas from appearing in team-a's search results.

This is a fundamental limitation for multi-tenant deployments where teams share a Registry instance but need isolation between their schemas.

## Decision

We implement per-resource authorization using **a JSON grants file with an in-process Java evaluator that translates grants into SQL query predicates** for search/list filtering.

### Why this approach

The hard problem is not point-access authorization (can this user read this artifact?) — any authorization engine solves that. The hard problem is **search/list filtering with correct pagination**.

When a user calls `GET /search/artifacts?limit=20`, Registry needs to return exactly 20 authorized results with a correct total count. This constrains the entire design. External engines (OPA, Zanzibar, Keycloak UMA) are outside the database query path — they can only make allow/deny decisions after results are fetched, not before. Post-filtering breaks pagination: total counts are wrong, page sizes are unpredictable, and performance degrades with deny rate.

The only approach that gives correct pagination is **translating authorization rules into SQL `WHERE` clauses before the query hits the database**. This requires the authorization data to be in-process, in a format that maps to SQL predicates.

## Amendment 1 (2026-10-27): adopt the Kroxylicious ACL rules format

**Decision.** The access rules file is shared with Kroxylicious and StreamsHub Console, and its
format is the Kroxylicious ACL rules language defined by
[proposal 009](https://github.com/kroxylicious/design/blob/main/proposals/009-authorizer.md) and
implemented by `AclAuthorizer` (`io.kroxylicious:kroxylicious-authorizer-acl`). Registry replaces
its JSON grants format with it before 3.4.0. The JSON format was never released (the latest
release is 3.3.3), so no migration is needed. This reverses the earlier rejection of `AclAuthorizer`
(see "Kroxylicious Authorizer with ACL DSL" below). Issue
[#10602](https://github.com/Apicurio/apicurio-registry/issues/10602), implemented in
[#10746](https://github.com/Apicurio/apicurio-registry/pull/10746).

The sections below that describe `GrantsAuthorizer`, `GrantsData` and the JSON grants file
describe the original design, which #10746 replaces.

**Example:**

```
from io.apicurio.authz import Artifact, Group, Role;

deny User with name = "bob" to * Artifact with name = "team-a/secret";
allow Role with name = "team-a-dev" to WRITE Group with name = "team-a";
allow Role with name = "team-a-dev" to WRITE Artifact with name like "team-a/*";
allow Role with name = "ops" to * Artifact with name *;
otherwise deny;
```

**Mapping from the JSON grants format.**

| JSON grants | ACL rules |
|---|---|
| `principal` / `principal_role` | `User with name = …` / `Role with name = …`. `User` is Kroxylicious's own `io.kroxylicious.proxy.authentication.User`, the default for `User` in rules files, so Kroxylicious rules work unchanged; `Role` is `io.apicurio.authz.Role` |
| `operation`: read, write, admin | `READ`, `WRITE`, `ADMIN` of the resource type enum |
| `resource_type`: artifact, group | `io.apicurio.authz.Artifact`, `io.apicurio.authz.Group`: top-level `ResourceType` enums (nested classes can't be imported) |
| `exact` / `prefix` pattern | `with name = "…"` / `with name like "…*"`, plus `in {…}`, `*` and `matching /regex/` (RE2, full match) |
| `deny: true` | `deny` rules, which must come before `allow` rules; the first matching rule applies |
| `config.admin_roles` | explicit rules, e.g. `allow Role with name = "ops" to * Artifact with name *;` |
| deny by default | explicit, mandatory `otherwise deny;` at the end of the file |
| `grants.path`, `grants.reload-*` config | `rules.path`, `rules.reload-every`, `rules.reload-enabled` |

Registry keeps what is outside the file: RBAC (evaluated first), the owner bypass, admin override,
audit logging (the `Authorizer` does not audit) and hot reload (the authorizer is rebuilt when the
file changes; an invalid file keeps the previous rules).

**Semantics that differ from the JSON grants** (verified against `AclAuthorizer` 0.25.1):
- A `deny` also denies the operations its operation implies: denying `WRITE` also denies `READ`. A
  resource can't be made read-only by denying `WRITE`; instead, allow only `READ`.
- A `deny` does not deny operations that imply it: denying `READ` leaves `WRITE` allowed, and
  denying `WRITE` leaves `ADMIN` allowed, if an `allow` matches. To hide a resource completely, deny
  `*` (or `ADMIN`).
- Names are opaque strings. Registry's `group/artifact` naming, with `%` and `/` escaped in the group
  ID, is a Registry convention, not part of the format.

**How Registry reads and evaluates the file.** Registry uses the format, not (yet) the Kroxylicious
evaluator (`io.apicurio.authz.AccessRules`):
- **Reading.** `AclAuthorizerService` resolves imported classes with `Class.forName` in its own class
  loader. Under Quarkus test and dev class loading, that finds a second copy of Registry's resource
  types, which is not a `ResourceType` in its view, so it rejects every Registry rules file. Registry
  parses the file with the Kroxylicious parser (`AclRulesParser`), resolves classes in its own class
  loader, and validates as `AclAuthorizerService` does. Tests check that the same files are accepted
  and rejected.
- **Evaluating.** `AclAuthorizer` 0.25.1 mis-evaluates prefixes. A principal `like` rule also matches
  principals of other classes with that name prefix, so `allow Role with name like "reader-*"`
  allows a `User` named `reader-bob`. Of several overlapping resource prefixes, only the
  lexicographically closest is checked, so an allow can be lost and **a deny bypassed**. Registry
  therefore evaluates the parsed rules itself with the semantics above, and returns to `AclAuthorizer`
  once these are fixed upstream. Tests check that decisions otherwise agree with `AclAuthorizer`, and
  pin the upstream bugs so a fix is noticed.

**Search.** Search pre-filtering needs the scope of the rules, not just point decisions. Point
decisions and the scope come from the same parse and the same matching, so search selects exactly
what point access allows. A test checks this invariant, including on overlapping and cross-class
prefixes. The scope moves to the shared resource scope API
([#10601](https://github.com/Apicurio/apicurio-registry/issues/10601)) once an upstream evaluator
implements it. `=` and `in` map to exact names, `like` to prefixes, `*` to allow-all
and `matching` to patterns. SQL and Elasticsearch can't evaluate RE2 patterns, so a grant-restricted
search whose scope contains patterns fails closed. Use `=`, `in` or `like` for Registry resources
where search matters.

**Open points with Kroxylicious** (found while checking 0.25.1 against proposal 009):
- `anonymous User` is in the proposal but not in the 0.25.1 grammar. Until it ships, Registry
  gives unauthenticated callers a `Role` principal named `$anonymous`.
- A rules file only loads if every imported principal and resource type class is on the classpath.
  One file covering Registry and Kroxylicious resources can't load in either, so either each system
  has its own file in the shared format, or loading must tolerate other systems' types.
- Principal classes are imported by fully qualified name, so a file shared across systems needs
  shared principal classes (e.g. `User` and `Role` in `kroxylicious-identity-api`).
- `kroxylicious-authorizer-acl` depends on the whole `kroxylicious-api` (including native compression
  libraries) only for the `@Plugin` annotation.
- `AclAuthorizer.authorize()` still takes the deprecated `Identity`; the proposal uses `Subject`.
- `AclAuthorizerService` resolves classes in its own class loader, which fails under Quarkus test
  and dev class loading (see "How Registry reads and evaluates the file"). A way to supply a class
  resolver would let Registry use it directly.
- `AclAuthorizer` prefix matching: principal prefixes ignore the principal class, and overlapping
  resource prefixes check only the closest one (an allow is lost, a deny bypassed).
- The parser removes every backslash in a regex literal, so `/a\.b/` is the regex `a.b`.
- The lexer reads a block comment (`/* … */`) as a regex literal, so block comments are syntax
  errors; only `//` comments work.

## Design

### Grants file format

> **Superseded by Amendment 1:** the file format is the Kroxylicious ACL rules format. The JSON
> format below is the format of the current code until #10602 is merged.

A JSON file defines who can access what. Each grant maps a principal (user or IdP role) to an operation on a resource pattern:

```json
{
  "config": {
    "admin_roles": ["sr-admin"]
  },
  "grants": [
    {
      "principal": "alice",
      "operation": "write",
      "resource_type": "artifact",
      "resource_pattern_type": "prefix",
      "resource_pattern": "team-a/"
    },
    {
      "principal_role": "sr-developer",
      "operation": "read",
      "resource_type": "artifact",
      "resource_pattern_type": "prefix",
      "resource_pattern": "public/"
    },
    {
      "principal": "alice",
      "operation": "read",
      "resource_type": "artifact",
      "resource_pattern_type": "exact",
      "resource_pattern": "team-a/secret-schema",
      "deny": true
    }
  ]
}
```

**Grant fields:**

| Field | Required | Description |
|-------|----------|-------------|
| `principal` | One of `principal` or `principal_role` | Username to match |
| `principal_role` | One of `principal` or `principal_role` | IdP role to match |
| `operation` | Yes | `read`, `write`, or `admin` (hierarchy: admin > write > read) |
| `resource_type` | Yes | `artifact` or `group` (extensible for other systems) |
| `resource_pattern_type` | No | `prefix` (startsWith), `exact` or omitted (equals). `resource_pattern: "*"` matches everything regardless of this field. |
| `resource_pattern` | Yes | The pattern to match (`team-a/`, `team-b/public-schema`, `*`) |
| `deny` | No | When `true`, denies the matched access. Deny rules take precedence over allow rules. |

Artifact resource names follow the format `{groupId}/{artifactId}`. Group resource names are just the group ID.

### Authorization flow

```
Request → Authentication (Keycloak/IdP)
        → Admin override                       (bypasses everything below)
        → RBAC                                 (coarse-grained role check; skipped for reads under
                                                anonymous/authenticated read access)
        → OBAC                                 (when enabled)
        → Per-resource grants                  (RBAC admins and, by default, owners bypass)
```

Key interactions:

- **Admins** bypass grants: admin override, RBAC admins (`sr-admin`), or a role listed in
  `config.admin_roles`.
- **Owners bypass grants**, independently of whether OBAC is enabled. The bypass is strict: it applies
  only when the addressed resource exists and its recorded owner is the caller (for content: owner of any
  artifact using it). Missing resources and
  resources without an owner never bypass. It can be disabled with
  `apicurio.auth.resource-based-authorization.owner-bypass.enabled=false`.
- **Creation and group deletes check artifacts too.** Creating an artifact requires `write` on the
  group and on the new name (otherwise a deny could be bypassed by creating the artifact and owning
  it), through every API that creates artifacts (core, Confluent compatibility, MCP publishing,
  Iceberg renames). Deleting a group, or all its artifacts, requires `write` on every artifact in it.
- **Indirectly accessed artifacts are authorized like direct access.** Resolving references
  (dereferencing, canonical content, Protobuf export, Confluent `format=resolved`) and registering
  content with references require `read` on every referenced artifact, transitively; the request
  fails with 403 rather than leaving a reference unresolved, so the response never depends silently
  on the caller. Write hooks that generate artifacts (embedded schemas, OpenAPI agent cards) require
  `write` on every generated artifact, plus `write` on the group when it does not exist yet, and
  embedded schemas also get owner-only authorization (`ResourceAccessGuard.requireGeneratedArtifactWrite`).
  Submitted references are authorized, for drafts too, before any hook runs, so a request rejected by authorization
  leaves no hook writes behind.
- **Grants are evaluated against stored IDs.** MySQL and SQL Server match IDs case-insensitively, so
  `GrantsAccessController` resolves an existing group or artifact to its stored spelling before
  evaluating grants; otherwise a case-only change of the ID would address the same resource under a
  name no deny rule matches.
- **RBAC runs before grants.** Grants restrict within what RBAC allows.
- **Grants files are validated strictly.** `config.admin_roles` must be an array of non-empty
  strings (a number or boolean must not become a role name). Any invalid grant (both or neither principal field, unknown
  operation or pattern type, non-boolean `deny`) rejects the whole file, since skipping an invalid
  deny rule would fail open.
- **Deny rules take precedence.** A deny on an operation also denies every operation that implies it:
  denying `read` blocks all access; denying `write` makes a resource read-only.
- **`authenticated-read-access` / `anonymous-read-access`** skip RBAC for reads, not grants.
  Unauthenticated callers get the `$anonymous` role, so anonymous access is granted explicitly.
- **Registry-wide reports** that cannot be filtered per resource (GitOps status and validation tasks)
  are limited to callers who are not restricted by grants.
- **`apicurio.authn.proxy-header.trust-proxy-authorization`** bypasses all local authorization.

### Two authorization paths

**Point access** (can this user perform this operation on this resource?): the Kroxylicious
`Authorizer.authorize()` call. The evaluator filters grants for the current user (~5-20 entries),
applies deny rules, then allow rules. In-process, ~10-50 µs per check.

**Search and list filtering** (which resources does this user see?): `GrantsData.getSearchFilterData()`
returns the user's read grants as resource-name patterns (allow-all flag, allowed exact/prefix names,
denied exact/prefix names), passed verbatim to storage as one `AuthorizationFilter`, plus the caller as
owner. Storage translates it into its query:

- SQL (`SqlAuthorizationFilter`): one condition shared by artifact, version and group searches.
  Group IDs may contain `/`, so a name pattern is decomposed at every `/` rather than split once:
  Artifact names escape `%` and `/` in the group ID (`%25`, `%2F`), so names are injective and the
  first `/` always separates group and artifact. `AuthorizationNames` decomposes a pattern into
  group/artifact clauses once, for both SQL and Elasticsearch: an exact name or a prefix containing
  `/` fixes the group; a prefix ending inside the escaped group becomes a raw group prefix. LIKE
  patterns are escaped. The default group, named `default` in grants, maps to its stored ID.
- Elasticsearch: the same clauses as `term`/`prefix` queries on keyword fields. The index does not
  store artifact ownership (it would go stale on ownership transfers), so the search decorator
  resolves the caller's owned artifacts from SQL storage and passes them as names that always match
  (capped at 1000 owned artifacts, with a warning logged when reached).

**Invariant:** for every subject and resource name, the search patterns select exactly what point
access allows. This is unit-tested by comparing both paths over adversarial names, and the SQL and
Elasticsearch translations are tested against the same reference semantics.

### Architecture

The implementation is split into two layers:

**Authorization contract** — the [Kroxylicious Authorizer API](https://github.com/kroxylicious/kroxylicious/tree/main/kroxylicious-authorizer-api)
(`io.kroxylicious:kroxylicious-authorizer-api`, which depends only on `kroxylicious-identity-api`
and annotations). It provides `Authorizer`, `Action`, `AuthorizeResult`, `Decision` and
`ResourceType`, plus `Subject`/`Identity`/`Principal` from the identity API. Registry does not
define its own copies, so any `Authorizer` implementation written against that API (Kroxylicious,
StreamsHub Console, or Registry) can be shared.

**Shared `authz` module** (`apicurio-authz-core`) — system-agnostic grants implementation:

```
authz/
  ├── User, RolePrincipal — Kroxylicious Principal implementations (User is @SingularPrincipal)
  ├── GrantsAuthorizer    — implements the Kroxylicious Authorizer, grants evaluation, file hot-reload
  ├── GrantsData          — parsed grants, search filter generation
  ├── Grant               — single grant record with matching logic
  └── SearchFilterData    — read grants as name patterns: allow-all, allowed/denied exact and prefix names
```

Dependencies: `kroxylicious-authorizer-api`, `jackson-databind` and `slf4j-api`. Only
`RolePrincipal` principals are treated as roles; other principal types that other systems attach
to a shared `Subject` cannot satisfy `principal_role` grants.

**Search pre-filtering is outside the Kroxylicious API.** `Authorizer.authorize()` answers
point-access questions only. To build the `WHERE`/query-DSL clauses for search and list endpoints,
Registry uses `GrantsData.getSearchFilterData()`, which is specific to the grants implementation.
If pre-filtering is later standardized (for example, as an optional resource-scope interface next
to `Authorizer`), `SearchFilterData` is the shape Registry needs: allow-all flag, allowed exact and
prefix names, and denied exact and prefix names.

**Registry integration** — Registry-specific:

```
app/.../auth/
  ├── AbstractAccessController.resolveResource — maps @Authorized style + parameters to a resource
  ├── AuthorizedInterceptor             — RBAC → OBAC → grants (owners bypass grants)
  ├── ResourceAccessGuard               — grants checks for body-addressed targets
  ├── ISearchAuthorizer / SearchAuthorizerProducer — entry point for all client-facing searches
  └── grants/
      ├── GrantsAccessController            — maps SecurityIdentity to a Kroxylicious Subject, calls the
      │                                       Authorizer, OTel metric, audit log
      ├── GrantsAccessControllerConfig      — configuration properties
      ├── GrantsAccessControllerInitializer — fail-fast startup, hot-reload scheduler, config warnings
      ├── GrantsSearchFilter                — adds the caller's AuthorizationFilter to searches
      └── RegistryResourceType              — Artifact and Group operations (Kroxylicious ResourceType)
app/.../storage/
  ├── dto/AuthorizationFilter               — storage-level search restriction
  ├── impl/sql/repositories/SqlAuthorizationFilter — SQL translation (all SQL variants)
  └── impl/search/ElasticsearchSearchService       — Elasticsearch translation
```

### Endpoint coverage

All styles are resolved by one method, `AbstractAccessController.resolveResource()`, shared by OBAC
and grants, so both always evaluate the resource the endpoint operates on.

| `AuthorizedStyle` | Resolved resource | Used by |
|---|---|---|
| `GroupAndArtifact` | artifact (param 0, param 1) | REST v2/v3 artifact, version, branch operations |
| `GroupOnly` | group (param 0) | REST v2/v3 group operations, listing/creating artifacts in a group |
| `ArtifactOnly` | artifact (`X-Registry-GroupId` header or group-concat subject, param 0) | Confluent compatibility API subjects |
| `GlobalId` | artifact of the global ID | `/ids/globalIds/{id}` |
| `ContentId`, `ContentHash` | every artifact using the content; allowed if any is allowed | `/ids/contentIds`, `/ids/contentHashes` |
| `CCompatSchemaId` | content ID, or global ID in legacy ID mode | Confluent compatibility API `/schemas/ids/{id}` |
| `QualifiedArtifactName` | artifact `namespace/server` | MCP Registry API |
| `IcebergNamespace`, `IcebergTable` | group = namespace, artifact = table/view (the catalog prefix is ignored) | Iceberg REST catalog |
| `None` | none | admin/system endpoints; body-addressed and list endpoints (below) |

Endpoints whose target is in the request body (creating groups, Iceberg namespace creation and
renames, MCP server publishing) call `ResourceAccessGuard.requireAccess()`, which applies OBAC and
grants with the same rules and bypasses as the interceptor. Every client-facing search or list goes through `ISearchAuthorizer`
(REST v2/v3, Confluent compatibility API, Iceberg, MCP Registry, `/.well-known` discovery);
inbound reference lists (including the Confluent `referencedby` list) and content-ID lists are
filtered per item. References are resolved through `ResourceAccessGuard.referenceLoader()`, which
checks `read` on each referenced artifact.

### Hot-reload

The grants file is polled every 5 seconds (configurable via `apicurio.auth.resource-based-authorization.grants.reload-every`; polling can be disabled via `apicurio.auth.resource-based-authorization.grants.reload-enabled=false`). Changes take effect without restart. A missing or invalid file fails startup; an invalid file on reload is logged once and the previous grants stay in effect, so a bad edit cannot lock everyone out. A file version is recorded as processed only after it has been loaded or rejected. The reload job is registered programmatically only when the feature and reload are both enabled.

File polling was chosen over `WatchService` because `WatchService` is unreliable on NFS mounts and Kubernetes ConfigMap volumes.

### Cross-system sharing

> **Superseded by Amendment 1:** systems share the Kroxylicious ACL rules format. Whether they also
> share a single file depends on an open point with Kroxylicious (types must be on the classpath).

The grants file supports multiple `resource_type` values. Each system defines its own resource types and reads only the grants relevant to it:

```json
{
  "grants": [
    {"principal": "alice", "operation": "write", "resource_type": "artifact", "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
    {"principal": "alice", "operation": "read", "resource_type": "topic", "resource_pattern_type": "prefix", "resource_pattern": "team-a."},
    {"principal_role": "ops", "operation": "read", "resource_type": "dashboard", "resource_pattern": "*"}
  ]
}
```

One ConfigMap, one source of truth. Because `GrantsAuthorizer` implements the Kroxylicious `Authorizer` API, other projects (Kroxylicious, StreamsHub Console) can consume the `authz` module without Registry-specific dependencies. Kroxylicious is building a unified authorizer that reads the same format.

### Observability

- **OTel metrics:** `apicurio.authz.decisions` counter with attributes `apicurio.registry.result` (allow/deny), `apicurio.registry.entity_type`, `apicurio.registry.operation`
- **Audit logging:** denied decisions logged to `io.apicurio.registry.audit.authz` with structured fields
- **Grants validation:** strict; any invalid grant rejects the whole file (see "Grants files are validated strictly" above), summary logged on every successful load
- **Config conflict warnings:** startup warnings when grants are enabled alongside `authenticated-read-access` or `anonymous-read-access`

### Configuration

| Property | Default | Description |
|---|---|---|
| `apicurio.auth.resource-based-authorization.enabled` | `false` | Enable per-resource authorization |
| `apicurio.auth.resource-based-authorization.grants.path` | _(none)_ | Path to JSON grants file |
| `apicurio.auth.resource-based-authorization.grants.reload-every` | `5s` | File change polling interval (valid duration, e.g. `5s`, `1m`) |
| `apicurio.auth.resource-based-authorization.grants.reload-enabled` | `true` | Set to `false` to disable hot-reload polling |

## Alternatives Considered

### OPA (Open Policy Agent) with WASM

We prototyped evaluating OPA policies compiled to WebAssembly in-process. The Rego policy was generic grant matching — the same logic the Java evaluator now does. OPA added a WASM runtime dependency (Chicory), a policy compilation workflow, and JSON serialization overhead without adding value.

The main argument for OPA was custom Rego extensibility, but custom policies would only apply to point-access checks, not search filtering — arbitrary Rego cannot be translated to SQL. This creates inconsistency: a user could be denied direct access but still see the artifact in search results.

**Rejected:** adds complexity without solving the search filtering problem.

### Kroxylicious Authorizer with ACL DSL

We prototyped using the Kroxylicious Authorizer interface together with its ACL DSL implementation (`AclAuthorizer`). The API is well-designed but at the time:

- The ACL DSL mixes policy and data — every permission change requires editing rules and restarting
- `AclAuthorizer.builder()` is package-private
- `kroxylicious-api` depends on Kafka transitively

Kroxylicious 0.25 extracted `kroxylicious-authorizer-api` so it no longer depends on the rest of Kroxylicious core, which removed the dependency objection.

**Decision (original):** the Kroxylicious `AclAuthorizer` implementation is rejected (policy/data mixing). The Kroxylicious Authorizer **API** is adopted as the contract, and `GrantsAuthorizer` implements it.

**Reversed by Amendment 1:** Registry adopts `AclAuthorizer` and its rules format, so that the
rules file format is shared with Kroxylicious and StreamsHub Console.

### Keycloak Authorization Services (UMA)

Requires registering every artifact as a Keycloak resource and keeping it in sync. Fragile at scale, ties to Keycloak specifically, and the Protection API is not designed for bulk "list all resources user X can access" queries.

**Rejected:** synchronization complexity and search filtering limitation.

### Proxy-based (Envoy + OPA sidecar)

Works for point-access. Cannot filter individual items from search response bodies.

**Rejected:** cannot solve search filtering.

### Google Zanzibar / OpenFGA

Relationship-based graph system. Our problem is flat pattern matching, not hierarchical ownership chains. Additionally, external service with the same search filtering limitation.

**Rejected:** overkill for the problem, cannot participate in SQL queries.

### ACL table JOINs

Store grants as database rows, JOIN with artifacts during search. Correct and scalable, but adds database schema, CRUD API, migration complexity. This is the right evolution when grants outgrow a file.

**Deferred:** premature for current scale (5-50 grants). The file-based approach validates the model first.

### Over-fetch and post-filter

Fetch more results than needed, filter by authorization, return the requested page. Breaks pagination: wrong total counts, unpredictable page sizes, O(n) performance with deny rate.

**Rejected:** breaks pagination contract.

## Known Limitations

### 1. No authorization caching across requests

Within a request, authorization reads each piece of metadata at most once (`AuthorizationLookups`, request-scoped): owner-only authorization, the owner bypass, the stored-ID check and global/content ID resolution share the lookups. Across requests nothing is cached, so every request reads the metadata it needs (one lookup for globalId endpoints, one per artifact or group checked) and evaluates grants again. Resolving references checks each referenced artifact. High-traffic SerDes clients will feel this.

**Impact:** increased latency on globalId endpoints and on dereferencing. **Mitigation:** a short-lived cache shared across requests, invalidated on grants reload and on ownership/existence changes ([#10600](https://github.com/Apicurio/apicurio-registry/issues/10600)).

### 2. Search results do not report the caller's permissions

Search results contain only readable resources, but not whether the caller may also write or
administer them; the API has no field for this. The UI cannot show or hide edit/delete actions per
artifact. **Mitigation:** a new, batch-evaluated `permissions` field
([#10599](https://github.com/Apicurio/apicurio-registry/issues/10599)).

### 3. Group and artifact grants are independent

Artifact access is decided by artifact grants only; group grants do not cascade to the artifacts in
the group, and a group deny does not hide its artifacts. This keeps point access and search
consistent, but operators usually need both kinds of grant (documented).

### 4. Case-sensitive search on MySQL and SQL Server, and Elasticsearch filter combinations

Point access compares names case-sensitively, so search does too on every variant: MySQL compares
the ID columns byte-wise (`CAST(... AS BINARY)`, the columns are `ascii_general_ci`) and SQL Server
uses a binary collation. A binary comparison cannot use the ID indexes, so each `=` and `LIKE` is
paired with the plain, index-usable comparison (a binary match is always a case-insensitive match,
so results are unchanged). On MySQL 8.4 this turns a full scan of the primary key into a range scan
(50k artifacts: estimated cost 9933 to 17 for a group plus artifact prefix grant). The `<>`
comparison excluding the default group stays binary only. Remaining limitation: index-routed
(Elasticsearch) searches that combine content or structure filters with content-hash filters are
rejected with `400` rather than evaluated partially.

### 5. Scale ceiling at 500+ grants

With hundreds of grants per user, the search `WHERE` clause becomes large (one `OR` branch per pattern and per `/` in it). Query plan efficiency degrades. The grants file also becomes unmanageable at scale (merge conflicts, review fatigue).

**Impact:** limits adoption to team-level, role-based deployments (5-50 grants). **Mitigation:** role-based grants (`principal_role`) keep the per-user count small. For enterprise scale, evolve to database-backed grants with ACL table JOINs.

### 6. Rules format is owned by Kroxylicious

The rules format is the Kroxylicious ACL rules language (Amendment 1). It is shared with Kroxylicious
and StreamsHub Console but is not an industry standard, and it is not interchangeable with OPA/Rego,
OpenFGA or Kafka ACLs. Registry can't change it on its own: grammar changes, new features (e.g.
`anonymous`) and fixes come from Kroxylicious releases.

**Impact:** Registry depends on Kroxylicious for format changes. **Mitigation:** the grammar is
defined by an ANTLR grammar in Kroxylicious and validated at load, so the grammar itself is the
specification; Registry contributes upstream ([#10602](https://github.com/Apicurio/apicurio-registry/issues/10602)).

### 7. RBAC constrains grants

Grants restrict within what RBAC allows — they cannot override RBAC. A `sr-readonly` user can never write regardless of grants. This is defense-in-depth, but operators must set RBAC roles appropriately (typically `sr-developer` for everyone, with grants doing fine-grained control).

**Impact:** requires RBAC role awareness when configuring grants. **Mitigation:** document the interaction clearly. Consider a future option to let grants be the sole authority.

### 8. Search pre-filtering is outside the Kroxylicious API

The Kroxylicious `Authorizer` answers point-access questions only. Search pre-filtering uses
`GrantsData`, so replacing the grants implementation with another `Authorizer` requires an equivalent
resource-scope provider. We expect to standardize this upstream as an optional interface returning
the shape of `SearchFilterData`
([#10601](https://github.com/Apicurio/apicurio-registry/issues/10601)).

### 9. Rejected requests can leave extracted schemas behind

Requests rejected by authorization leave no writes behind: references and generated targets are
authorized before any write hook runs. But embedded schema extraction registers the schemas before
the parent's rules run, so a request rejected by a rule (or another hook) can leave schema versions
behind. This predates per-resource authorization
([#10603](https://github.com/Apicurio/apicurio-registry/issues/10603)).

## Consequences

### Positive

- Users can enforce per-resource visibility: unauthorized artifacts do not appear in search results
- Correct pagination: page sizes, total counts, and offsets are accurate
- No external dependencies: no OPA sidecar, no Keycloak resource sync, no Zanzibar
- Hot-reload: permission changes take effect within seconds without restart
- Shared rules format: Registry, Kroxylicious and StreamsHub Console use the same Kroxylicious ACL rules language
- Defense-in-depth: grants layer adds to RBAC + OBAC, does not replace them

### Negative

- Rules format is owned by Kroxylicious; Registry depends on Kroxylicious releases for format changes
- File-based grants have a scale ceiling (~500 grants)
- Adds latency to every authorized request (grant evaluation) and globalId requests (storage lookup)
- Search pre-filtering is tied to the grants implementation (`GrantsData`), not the Kroxylicious `Authorizer` API

### Neutral

- Grants are managed as a file (GitOps or ConfigMap). The file can be shared across systems, so an editing UI belongs in a shared tool such as StreamsHub Console rather than in Registry
- The rules format is versioned with Kroxylicious releases; its ANTLR grammar is the specification
- The shared `authz` module is in the Registry repo, not a standalone project

## Implementation Status

- **Contract:** Kroxylicious Authorizer API (`io.kroxylicious:kroxylicious-authorizer-api` 0.25.0)
- **Grants implementation:** `authz/` — `GrantsAuthorizer`, `GrantsData`, `Grant`, `SearchFilterData`, `User`, `RolePrincipal`
- **Registry integration:** `app/.../auth/` — `AbstractAccessController.resolveResource`, `ResourceAccessGuard`,
  `AuthorizationLookups` (request-scoped metadata lookups),
  `ISearchAuthorizer`; `app/.../auth/grants/` — `GrantsAccessController`, `GrantsSearchFilter`, configuration
- **Storage:** `AuthorizationFilter` search filter, translated by `SqlAuthorizationFilter` and `ElasticsearchSearchService`
- **Tests:** `GrantsAuthorizerTest` (evaluation, search/point-access invariant, loading, reload),
  `GrantsAccessControllerTest`, `GrantsAuthorizationTest` (end-to-end across REST v2/v3, ccompat, Iceberg,
  MCP Registry, content IDs), `GrantsArtifactScopeTest` (group grant must not authorize an
  artifact-scoped operation the artifact itself denies, for the comment endpoints and
  `createArtifact`'s `ifExists` path, creation of denied names, group deletes, the outbound reference
  graph), `GrantsInteractionsTest` (anonymous/authenticated read access, RBAC admins, owner-bypass switch,
  registry-wide reports), `AuthorizationFilterStorageTest` and its PostgreSQL, MySQL and SQL Server
  variants (SQL translation, case sensitivity), `ElasticsearchAuthorizationQueryTest`
- **Example:** `distro/docker-compose/in-memory-with-authz-grants/` with a verification script
- **Documentation:** `docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc`
