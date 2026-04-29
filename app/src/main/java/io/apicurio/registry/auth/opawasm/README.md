# Per-Resource Authorization with Kroxylicious Authorizer API + Java Grants Evaluator

Fine-grained per-resource authorization for Apicurio Registry, using the [Kroxylicious Authorizer](https://github.com/kroxylicious/kroxylicious/tree/main/kroxylicious-authorizer-api) interface backed by a pure Java grants evaluator.

## Architecture

### Shared `authz` module

The authorization engine lives in the `authz/` module (`apicurio-authz-core`), separate from Registry-specific code. It implements the Kroxylicious `Authorizer` interface with a pure Java grants evaluator and is designed to be shared across systems:

```
authz/ (apicurio-authz-core)
  ├── GrantsAuthorizer        — implements Kroxylicious Authorizer, grants evaluation, file hot-reload
  ├── GrantsData              — parsed grants with per-user filtering, validation, caching
  ├── Grant                   — single grant record with matching logic
  └── RolePrincipal           — role-based Principal for Subject construction

app/ (Registry-specific integration)
  ├── GrantsAccessController            — bridges Authorizer with Registry's IAccessController
  ├── GrantsAccessControllerConfig      — config properties (enabled, grants path)
  ├── GrantsAccessControllerInitializer — startup, hot-reload scheduler
  ├── GrantsSearchFilter                — translates grants to SQL/ES filters for search
  ├── RegistryResourceType              — Artifact and Group enums (ResourceType impl)
  ├── ISearchAuthorizer                 — engine-agnostic search filtering interface
  └── SearchAuthorizerProducer          — CDI producer selecting the right ISearchAuthorizer
```

### How authentication and authorization fit together

- **Keycloak (or any IdP):** authentication + coarse-grained RBAC. Unchanged from today.
- **Registry (via authz module):** fine-grained per-resource authorization. Can this user read/write this specific artifact in this specific group?

The IdP doesn't know about Registry resources. No resource synchronization, no UMA.

### Two authorization paths, same grants data

**Point-access** (can this user read this artifact?):
```
SecurityIdentity → Kroxylicious Subject
  → GrantsAuthorizer.authorize(subject, actions)
    → Pre-filters grants for current user (~5-20 entries, ~3KB)
    → Java grants evaluation in-process
    → AuthorizeResult → Decision.ALLOW / DENY
    → OTel metric recorded (apicurio.authz.decisions)
    → Denied decisions audit-logged
```

**Search/list filtering** (which artifacts does this user see?):
```
GrantsData.getAllowedValues(user, roles, "artifact", "/")
  → Set of allowed group IDs
  → SQL WHERE groupId IN ('team-a', 'shared') / ES terms query
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
- **Kafka / Kroxylicious:** `Topic`, `ConsumerGroup`
- **StreamsHub Console:** `Dashboard`, `Cluster`

### Why in-process instead of a sidecar?

Search filtering requires the authorization layer to participate in the database query. A sidecar can't add `WHERE groupId IN (...)` to SQL. In-process evaluation also eliminates network latency for point-access (~10-50us vs ~1-2ms per call).

### Pluggable authorization engines

The `authz` module uses the Kroxylicious `Authorizer` interface as its contract. The default implementation is `GrantsAuthorizer`, a pure Java grants evaluator with no external dependencies beyond `jackson-databind`. Alternative implementations (for example, an OPA-based evaluator or a custom policy engine) can be plugged in by implementing the same interface.

## Configuration

```properties
# Required: enable experimental features gate
apicurio.features.experimental.enabled=true

# Enable per-resource authorization
apicurio.auth.resource-based-authorization.enabled=true

# Path to JSON grants data file (hot-reloaded every 5s on change)
apicurio.auth.resource-based-authorization.grants.path=/opt/apicurio/authz/grants.json
```

## Grants management

**File-based with hot-reload:** Grants live in a JSON file, hot-reloaded every 5 seconds on change. Admins manage through GitOps or kubectl. No restart needed for permission changes.

**Grants validation:** On load/reload, grants with missing required fields are skipped with a warning. Unrecognized operation or pattern type values are logged. A summary is logged on every load (grant count, admin roles).

## Scaling

Per-request evaluation payload is ~3KB (current user's grants only), regardless of total grant count. Per-user data is cached so filtering happens once per user per reload. Search filtering uses cached Java objects translated to SQL -- no external engine involved.

## Monitoring

**OTel metrics:**
- `apicurio.authz.decisions` -- counter with attributes: `decision` (allow/deny), `resource_type` (artifact/group), `operation` (read/write/admin)

**Audit logging:**
- Denied decisions logged to `io.apicurio.registry.audit.authz` at INFO level
- Format: `authz.denied user="alice" operation="write" resource_type="artifact" resource="team-b/secret"`

## Running the tests

```bash
# Shared authz module (17 tests)
mvn test -pl authz -Dcheckstyle.skip=true

# Registry unit tests (8 tests)
mvn test -pl app -Dtest=GrantsAccessControllerTest -Dcheckstyle.skip=true

# Integration tests with Keycloak (34 tests)
cd integration-tests
mvn verify -Dit.test=GrantsAuthIT -Dcheckstyle.skip=true -Dgroups=auth
```

## Docker-compose example

See `distro/docker-compose/in-memory-with-opa-wasm/` for a working example with Keycloak + per-resource authorization.

## Known limitations

- `getGroupById` uses `@Authorized(style=GroupAndArtifact)` with a single parameter -- handled gracefully by falling back to group-only check (#7866).
