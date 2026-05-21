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
        → Admin Override (admins bypass all checks)
        → RBAC (coarse-grained role check)
        → OBAC (owner bypass — owners can access their own artifacts)
        → Per-resource grants check (final gate)
```

Key interactions:

- **Admins** bypass the grants check entirely (via admin override or `config.admin_roles` in the grants file).
- **Artifact/group owners** bypass the grants check. Creating an artifact does not lock you out of it.
- **RBAC runs before grants.** Grants restrict within what RBAC allows. A `sr-readonly` user can never write, regardless of grants. This is defense-in-depth.
- **Deny rules take precedence.** Deny rules are evaluated before allow rules. If any deny rule matches, access is denied regardless of matching allow rules.
- **`authenticated-read-access`** overrides read grants. A startup warning is logged when both are enabled.
- **`trust-proxy-authorization`** bypasses all local authorization including grants.

### Two authorization paths

**Point-access** (can this user read this specific artifact?):

The grants evaluator filters the grants list for the current user (~5-20 entries), checks deny rules first, then checks allow rules against the operation hierarchy and resource pattern. Pure Java, in-process, ~10-50 microseconds per check.

**Search/list filtering** (which artifacts does this user see in search results?):

The grants evaluator extracts the current user's allowed resource patterns and translates them into SQL query filters:

- Group-level prefix grants → `WHERE groupId IN ('team-a', 'shared')`
- Exact artifact grants → `OR (groupId = 'team-b' AND artifactId = 'public-schema')`
- Exact deny rules → `AND NOT (groupId = 'team-a' AND artifactId = 'secret-schema')`
- Prefix deny rules → `AND NOT (groupId = 'team-a' AND artifactId LIKE 'internal-%')`

The database handles filtering, pagination, and counting natively. No over-fetching, no post-filtering.

Both paths read from the same parsed `GrantsData` object.

### Architecture

The implementation is split into two layers:

**Shared `authz` module** (`apicurio-authz-core`) — system-agnostic, designed to be reused:

```
authz/
  ├── Authorizer, Subject, Action, AuthorizeResult, Decision — interfaces
  ├── ResourceType, Principal, User, RolePrincipal             — type system
  ├── GrantsAuthorizer    — implements Authorizer, grants evaluation, file hot-reload
  ├── GrantsData          — parsed grants, search filter generation
  ├── Grant               — single grant record with matching logic
  └── SearchFilterData    — allowed groups, exact resources, denied resources
```

Zero external dependencies beyond `jackson-databind` and `slf4j-api`.

**Registry integration** (`app/.../auth/grants/`) — Registry-specific:

```
app/.../auth/grants/
  ├── GrantsAccessController            — bridges Authorizer with IAccessController, OTel metrics, audit
  ├── GrantsAccessControllerConfig      — config properties (enabled, grants path, reload interval)
  ├── GrantsAccessControllerInitializer — startup, hot-reload scheduler, config warnings
  ├── GrantsSearchFilter                — translates SearchFilterData to SQL filters
  ├── RegistryResourceType              — Artifact and Group enums (ResourceType impl)
  ├── ISearchAuthorizer                 — engine-agnostic search filtering interface
  └── SearchAuthorizerProducer          — CDI producer selecting the right ISearchAuthorizer
```

### Endpoint coverage

| AuthorizedStyle | Resource name resolution | Example endpoints |
|---|---|---|
| `GroupAndArtifact` | `buildResourceName(groupId, artifactId)` → `"team-a/my-schema"` | CRUD on `/groups/{groupId}/artifacts/{artifactId}` |
| `GroupOnly` | Raw groupId parameter | `/groups/{groupId}` |
| `ArtifactOnly` | `buildResourceName(null, subject)` → `"default/{subject}"` | Ccompat `/subjects/{subject}` |
| `GlobalId` | Storage lookup: `globalId → getArtifactVersionMetaData → groupId/artifactId` | `/ids/globalIds/{globalId}` |
| `None` | Skipped | Admin endpoints, list endpoints |

### Hot-reload

The grants file is polled every 5 seconds (configurable via `apicurio.auth.resource-based-authorization.grants.reload-every`, set to `off` to disable). Changes take effect without restart.

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

One ConfigMap, one source of truth. The shared `authz` module can be consumed by other projects (Kroxylicious, StreamsHub Console) without Registry-specific dependencies.

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
| `apicurio.auth.resource-based-authorization.grants.reload-every` | `5s` | File change polling interval. Set to `off` to disable. |
| `apicurio.features.experimental.enabled` | `false` | Must be `true` (feature is experimental) |

## Alternatives Considered

### OPA (Open Policy Agent) with WASM

We prototyped evaluating OPA policies compiled to WebAssembly in-process. The Rego policy was generic grant matching — the same logic the Java evaluator now does. OPA added a WASM runtime dependency (Chicory), a policy compilation workflow, and JSON serialization overhead without adding value.

The main argument for OPA was custom Rego extensibility, but custom policies would only apply to point-access checks, not search filtering — arbitrary Rego cannot be translated to SQL. This creates inconsistency: a user could be denied direct access but still see the artifact in search results.

**Rejected:** adds complexity without solving the search filtering problem.

### Kroxylicious Authorizer with ACL DSL

We prototyped using the Kroxylicious Authorizer interface. The API is well-designed but:

- The ACL DSL mixes policy and data — every permission change requires editing rules and restarting
- `AclAuthorizer.builder()` is package-private
- `kroxylicious-api` depends on Kafka transitively

Our `authz` module interfaces are inspired by the Kroxylicious API but defined independently with zero deps. If the Kroxylicious team extracts their API, migrating is mechanical.

**Rejected:** dependency issues and policy/data mixing. Interfaces adopted, implementation independent.

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

### 2. Elasticsearch search filtering not implemented

`SearchFilterData` is engine-agnostic, but only SQL generation exists. Elasticsearch deployments need `terms` and `must_not` query clauses.

**Impact:** blocks ES deployments from using per-resource authorization. **Mitigation:** implement ES filter translation using the same `SearchFilterData`.

### 3. `permissions` field in search results not populated

The OpenAPI spec defines a `permissions` field on `SearchedArtifact`, `SearchedGroup`, and `SearchedVersion`. No endpoint fills it in. This would let the UI show/hide edit/delete buttons per artifact.

**Impact:** UI cannot show per-artifact permission buttons. **Mitigation:** batch-evaluate permissions for returned search results.

### 4. No group-level deny in artifact search filtering

A deny rule on `resource_type: "group"` does not affect artifact search results. You can deny point-access to a group, but artifacts in that group may still appear in search if matched by another grant.

**Impact:** inconsistency between point-access and search filtering for group-level denies. **Mitigation:** exclude artifacts from denied groups in `getSearchFilterData()`.

### 5. Scale ceiling at 500+ grants

With hundreds of grants per user, the SQL `WHERE` clause becomes enormous (`IN` with hundreds of values, dozens of `NOT` clauses). Query plan efficiency degrades. The grants file also becomes unmanageable at scale (merge conflicts, review fatigue).

**Impact:** limits adoption to team-level, role-based deployments (5-50 grants). **Mitigation:** role-based grants (`principal_role`) keep the per-user count small. For enterprise scale, evolve to database-backed grants with ACL table JOINs.

### 6. Grants format is not a standard

The JSON format is our own. Cross-system adoption depends on other projects agreeing to use it. The `Authorizer` interface is pluggable — alternative implementations can be provided — but the grants file format itself is not interchangeable with OPA/Rego, OpenFGA, or Kafka ACLs.

**Impact:** organizational adoption challenge. **Mitigation:** format is intentionally simple (7 fields per grant), readable without tooling, and a JSON Schema can provide validation.

### 7. RBAC constrains grants

Grants restrict within what RBAC allows — they cannot override RBAC. A `sr-readonly` user can never write regardless of grants. This is defense-in-depth, but operators must set RBAC roles appropriately (typically `sr-developer` for everyone, with grants doing fine-grained control).

**Impact:** requires RBAC role awareness when configuring grants. **Mitigation:** document the interaction clearly. Consider a future option to let grants be the sole authority.

### 8. ContentId/ContentHash endpoints bypass grants

Content endpoints (`/ids/contentIds/{id}`, `/ids/contentHashes/{hash}`) use `AuthorizedStyle.None` and bypass grants checks entirely. A user who knows a contentId or contentHash can access artifact content regardless of grants. This is a pre-existing behavior — these endpoints were not designed for per-resource authorization because content can belong to multiple artifacts (many-to-many relationship), making grant resolution ambiguous.

**Impact:** information leakage if content IDs or hashes are guessable. **Mitigation:** these endpoints are rarely used directly by end users (primarily by SerDes clients that already have the globalId). For strict environments, restrict access at the network/proxy level.

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
- Elasticsearch not yet supported for search filtering

### Neutral

- No UI for grants management (file-based, GitOps-managed)
- Grants format is versioned implicitly by the parser, not explicitly in the file
- The shared `authz` module is in the Registry repo, not a standalone project

## Implementation Status

- **Shared module:** `authz/` — `GrantsAuthorizer`, `GrantsData`, `Grant`, full interface set
- **Registry integration:** `app/.../auth/grants/` — interceptor, search filter, owner bypass, metrics, audit
- **Tests:** 28 unit (authz) + 8 unit (app) + 34 integration (Keycloak)
- **Docker-compose example:** `distro/docker-compose/in-memory-with-authz-grants/`
- **Documentation:** AsciiDoc in `docs/`, README in `auth/grants/`
