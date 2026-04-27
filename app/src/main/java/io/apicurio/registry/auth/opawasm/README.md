# In-Process OPA Authorization via WASM

Per-resource authorization for Apicurio Registry using [opa-java-wasm](https://github.com/StyraOSS/opa-java-wasm). OPA policies compiled to WebAssembly are evaluated in-process — no external OPA server, no sidecar, no network calls.

## How authentication and authorization fit together

Authentication and per-resource authorization are handled by different systems with a clean boundary:

**Keycloak (or any IdP)** handles:
- Authentication — who is this user?
- Coarse-grained RBAC — what roles/groups do they have?
- This is what Registry already does today. Nothing changes here.

**Apicurio Registry (via OPA WASM)** handles:
- Fine-grained per-resource authorization — can this user read/write *this specific artifact* in *this specific group*?
- Grants managed via a JSON file (mounted as ConfigMap, managed through GitOps, or edited via a future management API)
- Uses the authenticated identity and roles/groups from the IdP as input to the policy

The IdP doesn't need to know about artifacts, groups, or any Registry-specific resources. No resource synchronization with Keycloak, no UMA. Keycloak manages users and their roles/groups; Registry manages what those users and roles can access at the resource level.

## Architecture

Two authorization paths, both using the same grants data:

**Point-access checks** (can this user read this specific artifact?):

```
REST request → AuthorizedInterceptor
  → Admin override / RBAC / OBAC checks (unchanged, driven by IdP roles)
  → OPA WASM check (OpaWasmAccessController)
    → Pre-filters grants for current user (~5-20 grants, ~3KB)
    → Borrows OPA policy instance from thread-safe pool
    → Evaluates WASM policy in-process
    → Returns allow/deny
```

**Search/list filtering** (which artifacts does this user see in search results?):

```
REST request → ISearchAuthorizer (OpaWasmSearchFilter)
  → Reads current user's allowed groups from cached GrantsData
  → Translates to SQL filter: WHERE groupId IN ('team-a', 'shared')
  → Database handles filtering + pagination correctly
  → No OPA involved — pure Java + SQL
```

## Why in-process instead of a sidecar?

Search/list filtering requires the authorization layer to participate in the database query. With a sidecar, search filtering would require over-fetching from the DB and post-filtering via network calls, breaking pagination. In-process evaluation lets us translate grants into SQL/Elasticsearch filters and let the database do the work.

For point-access checks, in-process WASM evaluation is ~10-50 microseconds vs ~1-2 milliseconds for a localhost sidecar call.

## Separation of concerns: policy vs data

OPA separates **policy logic** (Rego) from **data** (JSON):

- **Rego policy** (`registry-authz.rego`) — the generic authorization logic. Ships with Registry. Defines how to evaluate grants: principal matching, operation hierarchy, resource pattern matching. Compiled to WASM at Registry build/release time. Customers can provide their own at deploy time.

- **Grants data** (JSON file) — the specific permissions. Who can access what. Hot-reloaded every 5 seconds when the file changes. No restart, no Rego knowledge, no WASM compilation needed.

Example grants file:

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
    }
  ]
}
```

Grants support:
- **Per-user** (`principal`): `alice` can write `team-a/*`
- **Per-role** (`principal_role`): anyone with IdP role `sr-developer` can read `public/*`
- **Pattern types**: `prefix` (startsWith), `exact`, or `*` (wildcard)
- **Operation hierarchy**: `admin` implies `write` implies `read`
- **Resource types**: `artifact` (addressed as `{groupId}/{artifactId}`) and `group`

## WASM compilation

The Rego policy is compiled to WASM once, at Registry build/release time. It does NOT need recompilation when grants change — only the data changes at runtime.

Customers can provide their own `.wasm` at deploy time (mounted via volume/ConfigMap). The path is a config property, not hardcoded. Compilation takes ~1 second:

```bash
opa build -t wasm -e 'registry/authz/allow' registry-authz.rego -o bundle.tar.gz
tar xzf bundle.tar.gz policy.wasm
```

## Configuration

```properties
# Enable OPA WASM authorization (default: false)
apicurio.auth.opa-wasm.enabled=true

# Path to compiled WASM policy file (can be mounted via volume/ConfigMap)
apicurio.auth.opa-wasm.policy.path=/opt/apicurio/opa/registry-authz.wasm

# Path to JSON grants data file (hot-reloaded on change)
apicurio.auth.opa-wasm.data.path=/opt/apicurio/opa/grants.json

# Pool size for concurrent WASM evaluation (default: 4)
apicurio.auth.opa-wasm.pool-size=4
```

## Scaling

**Total grant count doesn't affect per-request performance.** Point-access checks pre-filter grants for the current user (~5-20 grants, ~3KB JSON) before passing to OPA, regardless of how many total grants exist. Search filtering uses cached Java objects translated to SQL, no OPA involved.

| Scale | Total grants | Per-request OPA payload |
|---|---|---|
| Small team | 100 | ~3KB (user's grants only) |
| Medium org | 1,000 | ~3KB (user's grants only) |
| Large org | 10,000 | ~3KB (user's grants only) |

For most deployments, role-based grants keep the file small (10-50 entries). Per-user grants at large scale (thousands of users) work but the file itself becomes harder to manage by hand — a management API or GitOps workflow is recommended.

## Grants management

**File-based with hot-reload (implemented):**

Grants live in a JSON file mounted as a ConfigMap or volume. Registry watches the file every 5 seconds and reloads on change — no restart needed. Admins manage permissions through GitOps (PR → review → merge → ArgoCD syncs ConfigMap) or kubectl.

**Management REST API (future):**

For teams that want a self-service UI instead of editing files: CRUD endpoints at `/apis/registry/v3/admin/acl`, backed by the same JSON structure. The API writes to the grants file (or a database), and the in-memory cache refreshes automatically.

## Running the tests

```bash
# Unit tests (21 tests, no containers)
mvn test -pl app -Dtest=OpaWasmAccessControllerTest -Dcheckstyle.skip=true

# Integration tests (34 tests, Keycloak + OPA WASM)
cd integration-tests
mvn verify -Dit.test=OpaWasmAuthIT -Dcheckstyle.skip=true -Dgroups=auth
```

## Docker-compose example

See `distro/docker-compose/in-memory-with-opa-wasm/` for a working example with Keycloak, Registry, and OPA WASM authorization. Includes pre-configured users with different per-resource access.

## Known limitations

**Two authorization paths can diverge.** Search filtering (Java/SQL) and point-access checks (OPA WASM) use separate code paths. Custom Rego policies with logic beyond simple grant matching (time-based rules, attribute checks) would apply to point-access but not search filtering. For the standard grants model this isn't an issue — both paths evaluate the same grants data.

**WASM policy changes require restart.** The grants data hot-reloads, but a new `.wasm` file requires a restart (or a future hot-reload extension).
