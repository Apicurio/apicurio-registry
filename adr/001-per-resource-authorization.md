# ADR-001: Per-Resource Authorization for Apicurio Registry

- **Status:** Accepted
- **Date:** 2026-05-09
- **Authors:** Carles Arnal
- **PR:** [#7829](https://github.com/Apicurio/apicurio-registry/pull/7829)
- **Issue:** [#7724](https://github.com/Apicurio/apicurio-registry/issues/7724)

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

## Design

### Grants file format

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
| `resource_pattern_type` | No | `prefix` (startsWith), `exact` (equals), or omitted for wildcard |
| `resource_pattern` | Yes | The pattern to match (`team-a/`, `team-b/public-schema`, `*`) |
| `deny` | No | When `true`, denies the matched access. Deny rules take precedence over allow rules. |

Artifact resource names follow the format `{groupId}/{artifactId}`. Group resource names are just the group ID.

### Authorization flow

```
Request → Authentication (Keycloak/IdP)
        → Admin override                       (bypasses everything below)
        → Anonymous/authenticated read access  (bypass for reads, when enabled)
        → RBAC                                 (coarse-grained role check)
        → OBAC                                 (when enabled)
        → Per-resource grants                  (owners of the addressed resource bypass)
```

Key interactions:

- **Admins** bypass grants (admin override, or a role listed in `config.admin_roles`).
- **Owners bypass grants**, independently of whether OBAC is enabled. The bypass is strict: it applies
  only when the addressed resource exists and its recorded owner is the caller. Missing resources and
  resources without an owner never bypass. Creating an artifact still requires `write` on the group.
- **RBAC runs before grants.** Grants restrict within what RBAC allows.
- **Deny rules take precedence.** A deny on an operation also denies every operation that implies it:
  denying `read` blocks all access; denying `write` makes a resource read-only.
- **`authenticated-read-access` / `anonymous-read-access`** override read grants (startup warning).
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
  `g/a` equals `n` iff `n = g + "/" + a` for some split of `n`; `g/a` starts with `p` iff `g` starts
  with `p`, or `p` splits into `g + "/" + q` and `a` starts with `q`. LIKE patterns are escaped. The
  default group, named `default` in grants, maps to its stored ID.
- Elasticsearch: the same decomposition with `term`/`prefix` queries on keyword fields. The owner
  clause is not applied (the index stores the version owner as analyzed text), which only narrows
  results.

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
renames, MCP server publishing) call `ResourceAccessGuard.requireAccess()`, which applies the same
bypasses as the interceptor. Every client-facing search or list goes through `ISearchAuthorizer`
(REST v2/v3, Confluent compatibility API, Iceberg, MCP Registry, `/.well-known` discovery);
inbound reference lists and content-ID lists are filtered per item.

### Hot-reload

The grants file is polled every 5 seconds (configurable via `apicurio.auth.resource-based-authorization.grants.reload-every`; polling can be disabled via `apicurio.auth.resource-based-authorization.grants.reload-enabled=false`). Changes take effect without restart. A missing or invalid file fails startup; an invalid file on reload is logged and the previous grants stay in effect, so a bad edit cannot lock everyone out.

File polling was chosen over `WatchService` because `WatchService` is unreliable on NFS mounts and Kubernetes ConfigMap volumes.

### Cross-system sharing

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

One ConfigMap, one source of truth. Because `GrantsAuthorizer` implements the Kroxylicious `Authorizer` API, other projects (Kroxylicious, StreamsHub Console) can consume the `authz` module without Registry-specific dependencies.

### Observability

- **OTel metrics:** `apicurio.authz.decisions` counter with attributes `decision` (allow/deny), `resource_type`, `operation`
- **Audit logging:** denied decisions logged to `io.apicurio.registry.audit.authz` with structured fields
- **Grants validation:** missing fields skipped with warning, unrecognized values logged, summary on every load
- **Config conflict warnings:** startup warnings when grants are enabled alongside `authenticated-read-access` or `anonymous-read-access`

### Configuration

| Property | Default | Description |
|---|---|---|
| `apicurio.auth.resource-based-authorization.enabled` | `false` | Enable per-resource authorization |
| `apicurio.auth.resource-based-authorization.grants.path` | _(none)_ | Path to JSON grants file |
| `apicurio.auth.resource-based-authorization.grants.reload-every` | `5s` | File change polling interval (valid duration, e.g. `5s`, `1m`) |
| `apicurio.auth.resource-based-authorization.grants.reload-enabled` | `true` | Set to `false` to disable hot-reload polling |
| `apicurio.features.experimental.enabled` | `false` | Must be `true` (feature is experimental) |

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

**Decision:** the Kroxylicious `AclAuthorizer` implementation is rejected (policy/data mixing). The Kroxylicious Authorizer **API** is adopted as the contract, and `GrantsAuthorizer` implements it.

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

### 1. No authorization decision caching

Every request evaluates grants from scratch. The same user accessing the same artifact repeatedly recomputes the same result. GlobalId endpoints add a storage roundtrip (`getArtifactVersionMetaData`) before the grants check. High-traffic SerDes clients will feel this.

**Impact:** increased latency on globalId endpoints. **Mitigation:** add per-user, per-resource cache invalidated on grants reload.

### 2. `permissions` field in search results not populated

The UI cannot show or hide edit/delete actions per artifact. **Mitigation:** batch-evaluate
permissions for returned results in a follow-up.

### 3. Group and artifact grants are independent

Artifact access is decided by artifact grants only; group grants do not cascade to the artifacts in
the group, and a group deny does not hide its artifacts. This keeps point access and search
consistent, but operators usually need both kinds of grant (documented).

### 4. Pattern matching follows the database collation in search

Point access compares names case-sensitively. On databases with case-insensitive collations
(common for MySQL and SQL Server), search matching follows the collation and can include names that
differ only in case. **Mitigation:** documented; use consistent casing in group and artifact IDs.

### 5. Scale ceiling at 500+ grants

With hundreds of grants per user, the search `WHERE` clause becomes large (one `OR` branch per pattern and per `/` in it). Query plan efficiency degrades. The grants file also becomes unmanageable at scale (merge conflicts, review fatigue).

**Impact:** limits adoption to team-level, role-based deployments (5-50 grants). **Mitigation:** role-based grants (`principal_role`) keep the per-user count small. For enterprise scale, evolve to database-backed grants with ACL table JOINs.

### 6. Grants format is not a standard

The JSON format is our own. Cross-system adoption depends on other projects agreeing to use it. The Kroxylicious `Authorizer` interface is pluggable for point-access checks — alternative implementations can be provided — but search pre-filtering currently depends on `GrantsData`, and the grants file format itself is not interchangeable with OPA/Rego, OpenFGA, or Kafka ACLs.

**Impact:** organizational adoption challenge. **Mitigation:** format is intentionally simple (7 fields per grant), readable without tooling, and a JSON Schema can provide validation.

### 7. RBAC constrains grants

Grants restrict within what RBAC allows — they cannot override RBAC. A `sr-readonly` user can never write regardless of grants. This is defense-in-depth, but operators must set RBAC roles appropriately (typically `sr-developer` for everyone, with grants doing fine-grained control).

**Impact:** requires RBAC role awareness when configuring grants. **Mitigation:** document the interaction clearly. Consider a future option to let grants be the sole authority.

### 8. Search pre-filtering is outside the Kroxylicious API

The Kroxylicious `Authorizer` answers point-access questions only. Search pre-filtering uses
`GrantsData`, so replacing the grants implementation with another `Authorizer` requires an equivalent
resource-scope provider. We expect to standardize this upstream as an optional interface returning
the shape of `SearchFilterData`.

## Consequences

### Positive

- Users can enforce per-resource visibility: unauthorized artifacts do not appear in search results
- Correct pagination: page sizes, total counts, and offsets are accurate
- No external dependencies: no OPA sidecar, no Keycloak resource sync, no Zanzibar
- Hot-reload: permission changes take effect within seconds without restart
- Cross-system potential: same grants file can be shared with Kroxylicious, StreamsHub Console
- Defense-in-depth: grants layer adds to RBAC + OBAC, does not replace them

### Negative

- New JSON format to learn and manage (not a standard)
- File-based grants have a scale ceiling (~500 grants)
- Adds latency to every authorized request (grant evaluation) and globalId requests (storage lookup)
- Experimental feature flag required
- Search pre-filtering is tied to the grants implementation (`GrantsData`), not the Kroxylicious `Authorizer` API

### Neutral

- No UI for grants management (file-based, GitOps-managed)
- Grants format is versioned implicitly by the parser, not explicitly in the file
- The shared `authz` module is in the Registry repo, not a standalone project

## Implementation Status

- **Contract:** Kroxylicious Authorizer API (`io.kroxylicious:kroxylicious-authorizer-api` 0.25.0)
- **Grants implementation:** `authz/` — `GrantsAuthorizer`, `GrantsData`, `Grant`, `SearchFilterData`, `User`, `RolePrincipal`
- **Registry integration:** `app/.../auth/` — `AbstractAccessController.resolveResource`, `ResourceAccessGuard`,
  `ISearchAuthorizer`; `app/.../auth/grants/` — `GrantsAccessController`, `GrantsSearchFilter`, configuration
- **Storage:** `AuthorizationFilter` search filter, translated by `SqlAuthorizationFilter` and `ElasticsearchSearchService`
- **Tests:** `GrantsAuthorizerTest` (evaluation, search/point-access invariant, loading, reload),
  `GrantsAccessControllerTest`, `GrantsAuthorizationTest` (end-to-end across REST v2/v3, ccompat, Iceberg,
  MCP Registry, content IDs), `AuthorizationFilterStorageTest` (SQL translation), `ElasticsearchAuthorizationQueryTest`
- **Example:** `distro/docker-compose/in-memory-with-authz-grants/` with a verification script
- **Documentation:** `docs/modules/ROOT/pages/getting-started/assembly-configuring-resource-authorization.adoc`
