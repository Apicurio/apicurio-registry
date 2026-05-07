# Per-Resource Authorization with Java Grants Evaluator

Fine-grained per-resource authorization for Apicurio Registry, using a shared `Authorizer` interface backed by a pure Java grants evaluator. No external dependencies beyond Jackson.

## Authorization flow

```
Authentication → Admin Override → RBAC → OBAC → Per-resource grants check
```

- **Admins** bypass the grants check entirely (via admin override or `config.admin_roles`)
- **Artifact/group owners** bypass the grants check — ownership implies access
- **RBAC** is checked before grants. Grants restrict within what RBAC allows, not instead of it. A `sr-readonly` user can never write regardless of grants — RBAC denies the write before grants run. To use grants for fine-grained control, users need `sr-developer` at the RBAC level.
- **`authenticated-read-access`** overrides read grants — all authenticated users can read everything. A startup warning is logged when this is enabled alongside per-resource authorization.
- **`anonymous-read-access`** overrides read grants for anonymous users. Same startup warning.
- **`trust-proxy-authorization`** bypasses all local authorization including grants. The proxy is trusted to have done authorization already.

## Architecture

### Shared `authz` module

The authorization engine lives in the `authz/` module (`apicurio-authz-core`), separate from Registry-specific code. It defines its own interfaces and provides a pure Java grants evaluator. Zero external dependencies beyond `jackson-databind` and `slf4j-api`. Designed to be shared across systems.

```
authz/ (apicurio-authz-core)
  ├── Authorizer, Subject, Action, AuthorizeResult, Decision — interfaces
  ├── ResourceType, Principal, User, RolePrincipal           — type system
  ├── GrantsAuthorizer        — implements Authorizer, grants evaluation, file hot-reload
  ├── GrantsData              — parsed grants with per-user filtering, validation, caching
  └── Grant                   — single grant record with matching logic

app/ (Registry-specific integration)
  ├── GrantsAccessController            — bridges Authorizer with IAccessController, OTel metrics, audit logging
  ├── GrantsAccessControllerConfig      — config properties (enabled, grants path)
  ├── GrantsAccessControllerInitializer — startup, hot-reload scheduler, config conflict warnings
  ├── GrantsSearchFilter                — translates grants to SQL/ES filters for search
  ├── RegistryResourceType              — Artifact and Group enums (ResourceType impl)
  ├── ISearchAuthorizer                 — engine-agnostic search filtering interface
  └── SearchAuthorizerProducer          — CDI producer selecting the right ISearchAuthorizer
```

### How authentication and authorization fit together

- **Keycloak (or any IdP):** authentication + coarse-grained RBAC. Unchanged from today.
- **Registry (via authz module):** fine-grained per-resource authorization.

The IdP doesn't know about Registry resources. No resource synchronization, no UMA.

### Two authorization paths, same grants data

**Point-access** (can this user read this artifact?):
```
SecurityIdentity → Subject
  → GrantsAuthorizer.authorize(subject, actions)
    → Filters grants for current user (~5-20 entries)
    → Matches principal → operation hierarchy → resource pattern
    → AuthorizeResult → Decision.ALLOW / DENY
    → OTel metric recorded (apicurio.authz.decisions)
    → Denied decisions audit-logged
```

**Search/list filtering** (which artifacts does this user see?):
```
GrantsData.getSearchFilterData(user, roles, "artifact", "/")
  → SearchFilterData with allowed groups, exact artifact grants, and denied exact resources
  → SQL WHERE (groupId IN ('team-a', 'shared') OR (groupId = 'team-b' AND artifactId = 'public-schema'))
         AND NOT (groupId = 'team-a' AND artifactId = 'secret-schema')
  → Database handles filtering + pagination correctly
```

Both paths read from the same `GrantsData` object, parsed from the same grants file.

### Cross-system sharing

The grants file supports multiple resource types. One file, one ConfigMap, one source of truth:

```json
{
  "config": { "admin_roles": ["sr-admin"] },
  "grants": [
    {"principal": "alice", "operation": "write", "resource_type": "artifact", "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
    {"principal": "alice", "operation": "read", "resource_type": "topic", "resource_pattern_type": "prefix", "resource_pattern": "team-a."},
    {"principal_role": "ops", "operation": "read", "resource_type": "dashboard", "resource_pattern": "*"}
  ]
}
```

Each system defines its own `ResourceType` enums and depends on `apicurio-authz-core`:
- **Registry:** `RegistryResourceType.Artifact`, `RegistryResourceType.Group`
- **Kafka:** `Topic`, `ConsumerGroup`
- **StreamsHub Console:** `Dashboard`, `Cluster`

### Pluggable authorization

The `authz` module defines its own `Authorizer` interface. The default implementation (`GrantsAuthorizer`) is pure Java. Alternative implementations (OPA, custom engines) can be plugged in by implementing the same interface.

## Configuration

```properties
# Required: enable experimental features gate
apicurio.features.experimental.enabled=true

# Enable per-resource authorization
apicurio.auth.resource-based-authorization.enabled=true

# Path to JSON grants data file (hot-reloaded every 5s on change)
apicurio.auth.resource-based-authorization.grants.path=/opt/apicurio/authz/grants.json
```

## Grants format

- **`principal`** or **`principal_role`** — match by username or IdP role
- **`operation`** — `read`, `write`, or `admin` (hierarchy: admin > write > read)
- **`resource_type`** — system-specific (`artifact`, `group`, `topic`, `dashboard`, etc.)
- **`resource_pattern_type`** — `prefix` (startsWith), `exact` (equals), or omitted for wildcard
- **`resource_pattern`** — the pattern (`team-a/`, `my-topic`, `*`)
- **`deny`** — optional boolean, default `false`. When `true`, denies the matched access. Deny rules take precedence over allow rules.

### Deny rules

Deny rules use the same fields as allow rules, with the addition of `"deny": true`. When the grants evaluator processes a request, deny rules are evaluated first. If any deny rule matches, access is denied regardless of any allow rules that also match.

This enables patterns like "allow everything in `team-a/*` but deny `team-a/secret-schema`":

```json
{"principal": "alice", "operation": "write", "resource_type": "artifact", "resource_pattern_type": "prefix", "resource_pattern": "team-a/"},
{"principal": "alice", "operation": "read", "resource_type": "artifact", "resource_pattern_type": "exact", "resource_pattern": "team-a/secret-schema", "deny": true}
```

In this example, Alice can read and write all artifacts under `team-a/` except `team-a/secret-schema`, which is explicitly denied.

### Artifact-level search filtering

Exact artifact grants (e.g., `team-b/public-schema`) appear in search results even when the user has no group-level access to the rest of that group. Deny rules also apply to search filtering — denied artifacts are excluded from results via SQL `NOT` clauses, even if they match an allow rule.

The SQL filter generates a `WHERE` clause combining group-level `IN` clauses, artifact-level matches, and deny exclusions:

```sql
WHERE (groupId IN ('team-a', 'shared') OR (groupId = 'team-b' AND artifactId = 'public-schema'))
  AND NOT (groupId = 'team-a' AND artifactId = 'secret-schema')
```

This ensures that users see all artifacts they are explicitly granted access to, and never see artifacts that are explicitly denied.

## Grants management

**Hot-reload:** Grants file watched every 5 seconds. Changes take effect without restart.

**Validation:** Missing required fields → grant skipped with warning. Unrecognized operation or pattern type → warning. Summary logged on every load.

**Proxy headers:** `X-Forwarded-User` → principal, `X-Forwarded-Groups` → roles. Both `principal` and `principal_role` grants match against these.

## Scaling

Per-request evaluation is O(n) where n is the user's grant count (~5-20), not total grants. Search filtering uses cached Java objects translated to SQL — no per-item evaluation.

## Monitoring

**OTel metrics:**
- `apicurio.authz.decisions` — counter with attributes: `decision` (allow/deny), `resource_type` (artifact/group), `operation` (read/write/admin)

**Audit logging:**
- Denied decisions logged to `io.apicurio.registry.audit.authz` at INFO level
- Format: `authz.denied user="alice" operation="write" resource_type="artifact" resource="team-b/secret"`

## Running the tests

```bash
# Shared authz module (26 tests)
mvn test -pl authz -Dcheckstyle.skip=true

# Registry unit tests (8 tests)
mvn test -pl app -Dtest=GrantsAccessControllerTest -Dcheckstyle.skip=true

# Integration tests with Keycloak (34 tests)
cd integration-tests
mvn verify -Dit.test=GrantsAuthIT -Dcheckstyle.skip=true -Dgroups=auth
```

## Docker-compose example

See `distro/docker-compose/in-memory-with-authz-grants/` for a working example with Keycloak + per-resource authorization.

## Known limitations

- `getGroupById` uses `@Authorized(style=GroupAndArtifact)` with a single parameter — handled gracefully by falling back to group-only check (#7866).
- The `permissions` field in search results (for UI show/hide) is defined in the OpenAPI spec but not yet populated (#7867).
