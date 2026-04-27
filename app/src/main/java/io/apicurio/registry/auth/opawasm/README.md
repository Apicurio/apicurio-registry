# Per-Resource Authorization with Kroxylicious Authorizer API + OPA WASM

Fine-grained per-resource authorization for Apicurio Registry, using the [Kroxylicious Authorizer](https://github.com/kroxylicious/kroxylicious/tree/main/kroxylicious-authorizer-api) interface backed by OPA policies evaluated in-process via WebAssembly.

## Architecture

### Shared `authz` module

The authorization engine lives in the `authz/` module (`apicurio-authz-core`), separate from Registry-specific code. It implements the Kroxylicious `Authorizer` interface with OPA WASM and is designed to be shared across systems:

```
authz/ (apicurio-authz-core)
  ├── OpaWasmAuthorizer  — implements Kroxylicious Authorizer interface
  ├── GrantsData         — parsed grants with per-user filtering + caching
  ├── Grant              — single grant record with matching logic
  └── RolePrincipal      — role-based Principal for Subject construction

app/ (Registry-specific integration)
  ├── OpaWasmAccessController  — bridges Authorizer with Registry's IAccessController
  ├── OpaWasmSearchFilter      — translates grants to SQL/ES filters for search
  ├── RegistryResourceType     — Artifact and Group enums (ResourceType impl)
  └── ISearchAuthorizer        — engine-agnostic search filtering interface
```

### How authentication and authorization fit together

- **Keycloak (or any IdP):** authentication + coarse-grained RBAC. Unchanged from today.
- **Registry (via authz module):** fine-grained per-resource authorization. Can this user read/write this specific artifact in this specific group?

The IdP doesn't know about Registry resources. No resource synchronization, no UMA.

### Two authorization paths, same grants data

**Point-access** (can this user read this artifact?):
```
SecurityIdentity → Kroxylicious Subject
  → OpaWasmAuthorizer.authorize(subject, actions)
    → Pre-filters grants for current user (~5-20 entries, ~3KB)
    → OPA WASM evaluation in-process
    → AuthorizeResult → Decision.ALLOW / DENY
```

**Search/list filtering** (which artifacts does this user see?):
```
GrantsData.getAllowedValues(user, roles, "artifact", "/")
  → Set of allowed group IDs
  → SQL WHERE groupId IN ('team-a', 'shared')
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

Search filtering requires the authorization layer to participate in the database query. A sidecar can't add `WHERE groupId IN (...)` to SQL. In-process WASM evaluation also eliminates network latency for point-access (~10-50us vs ~1-2ms per call).

## Configuration

```properties
apicurio.auth.opa-wasm.enabled=true
apicurio.auth.opa-wasm.policy.path=/opt/apicurio/opa/registry-authz.wasm
apicurio.auth.opa-wasm.data.path=/opt/apicurio/opa/grants.json
apicurio.auth.opa-wasm.pool-size=4
```

## Grants management

**File-based with hot-reload:** Grants live in a JSON file, hot-reloaded every 5 seconds on change. Admins manage through GitOps or kubectl. No restart needed for permission changes.

**Custom policies at deploy-time:** The `.wasm` path is a config property. Customers can compile their own Rego policy and mount it — same Registry image, different policy.

## Scaling

Per-request OPA payload is ~3KB (current user's grants only), regardless of total grant count. Search filtering uses cached Java objects translated to SQL — no OPA involved.

## Running the tests

```bash
# Shared authz module (17 tests)
mvn test -pl authz -Dcheckstyle.skip=true

# Registry unit tests (8 tests)
mvn test -pl app -Dtest=OpaWasmAccessControllerTest -Dcheckstyle.skip=true

# Integration tests with Keycloak (34 tests)
cd integration-tests
mvn verify -Dit.test=OpaWasmAuthIT -Dcheckstyle.skip=true -Dgroups=auth
```

## Docker-compose example

See `distro/docker-compose/in-memory-with-opa-wasm/` for a working example with Keycloak + OPA WASM.

## Known limitations

- Search filtering (Java/SQL) and point-access (OPA WASM) both use the same grants data but different evaluation paths. Custom Rego policies beyond grant matching would apply to point-access only.
- WASM policy changes require restart. Grants hot-reload every 5 seconds.
