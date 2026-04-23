# POC: In-Process OPA Authorization via WASM

Proof of concept using [opa-java-wasm](https://github.com/StyraOSS/opa-java-wasm) to evaluate OPA policies in-process for per-resource authorization in Apicurio Registry. This is an alternative to the Kroxylicious Authorizer POC on the `issue-7724` branch.

## How it works

1. Authorization policies are written in [Rego](https://www.openpolicyagent.org/docs/latest/policy-language/) (OPA's policy language)
2. Policies are compiled to WebAssembly: `opa build -t wasm -e 'registry/authz/allow' registry-authz.rego`
3. At startup, Registry loads the compiled `.wasm` file and creates a thread-safe pool of policy evaluators
4. On each request, the interceptor evaluates the policy in-process — no network calls, no external OPA server

## How authentication and authorization fit together

Authentication and per-resource authorization are handled by different systems with a clean boundary:

**Keycloak (or any IdP)** handles:
- Authentication — who is this user?
- Coarse-grained RBAC — what roles/groups do they have?
- This is what Registry already does today. Nothing changes here.

**Apicurio Registry (via OPA WASM)** handles:
- Fine-grained per-resource authorization — can this user read/write *this specific artifact* in *this specific group*?
- Resource-level grants managed in Registry's own database, evaluated in-process
- Uses the authenticated identity and roles/groups from the IdP as input to the policy

The IdP doesn't need to know about artifacts, groups, or any Registry-specific resources. No resource synchronization with Keycloak, no UMA. Keycloak manages users and their roles/groups; Registry manages what those users and roles can access at the resource level.

The Rego policy can evaluate grants at either level:
- **Per-user**: `alice` can write `team-a/*`
- **Per-role/group**: anyone with IdP group `team-a-developers` can write `team-a/*`

This means user lifecycle stays in the IdP (onboarding, offboarding, team changes), while resource permissions stay in Registry (which team owns which artifacts).

## Architecture

```
REST request
  → Keycloak/IdP: authentication + RBAC (who is this user, what roles do they have)
  → AuthorizedInterceptor
    → Admin override / RBAC / OBAC checks (unchanged, driven by IdP roles)
    → OPA WASM check (OpaWasmAccessController)
      → Builds JSON input: { user, roles (from JWT), operation, resource_type, resource_name }
      → Borrows OPA policy instance from pool
      → Sets grants data (from database)
      → Evaluates WASM policy in-process
      → Returns allow/deny
```

## Separation of concerns: policy vs data

OPA's design separates **policy logic** (Rego) from **data** (JSON). This is what makes dynamic grant management possible:

- **Rego policy** (`registry-authz.rego`) — the generic authorization logic. Ships with Registry, rarely changes. Defines how to evaluate grants: principal matching, operation hierarchy, resource pattern matching. Only changes when the authorization model itself changes (e.g., adding a new resource type).

- **Grants data** (JSON) — the specific permissions. Who can access what. In the POC this is a static file; in production it would be serialized from the database. The Rego policy evaluates against this data at runtime.

This separation is key: admins manage grants through a REST API or UI, grants are stored in the database, and Registry serializes them as the JSON data context fed into OPA. The Rego policy stays unchanged. No one needs to learn Rego or compile WASM when permissions change.

Example of the data structure (POC uses a static file, production would serialize from DB):

```json
{
  "grants": [
    {
      "principal": "alice",
      "operation": "write",
      "resource_type": "artifact",
      "resource_pattern_type": "prefix",
      "resource_pattern": "team-a/"
    },
    {
      "principal_role": "team-b-developers",
      "operation": "read",
      "resource_type": "artifact",
      "resource_pattern": "*"
    }
  ]
}
```

## Configuration

```properties
# Enable OPA WASM authorization (default: false)
apicurio.auth.opa-wasm.enabled=true

# Path to compiled WASM policy file
apicurio.auth.opa-wasm.policy.path=/path/to/registry-authz.wasm

# Path to JSON permissions data file
apicurio.auth.opa-wasm.data.path=/path/to/permissions.json

# Pool size for concurrent WASM evaluation (default: 4)
apicurio.auth.opa-wasm.pool-size=4
```

## Files

| File | Purpose |
|------|---------|
| `OpaWasmAccessController.java` | `IAccessController` implementation using OPA WASM evaluation |
| `OpaWasmAccessControllerConfig.java` | Config properties |
| `OpaWasmAccessControllerInitializer.java` | Startup bean loading WASM policy and permissions data |
| `registry-authz.rego` | Rego policy (generic, ships with Registry) |
| `registry-authz.wasm` | Compiled WASM policy |
| `test-opa-permissions.json` | Example permissions data used by tests |
| `OpaWasmAccessControllerTest.java` | 14 tests covering all scenarios |

## The Rego policy

The policy supports:
- **Role-based grants** — users with the `admin` role can do everything
- **Per-user, per-resource grants** — explicit allow rules with exact or prefix resource matching
- **Operation hierarchy** — `admin` implies `write` implies `read`
- **Resource types** — `artifact` (addressed as `{groupId}/{artifactId}`) and `group`
- **Search result filtering** — evaluate each result against the policy to filter unauthorized items

## WASM compilation: when and why

A common concern is whether Registry needs a periodic process to compile Rego policies to WASM at runtime. **It does not.** OPA separates policy logic from data, and only the logic needs to be compiled. The data changes freely at runtime without any compilation step.

**What's static (compiled once, shipped with Registry):**

The Rego policy is the generic evaluation logic — "given a user, an operation, and a resource, check if any grant matches." It's the same for every Registry deployment. It gets compiled to WASM at Registry build/release time (in CI/CD), and the `.wasm` file is bundled in the container image, just like any other compiled artifact. It only changes when we release a new version of Registry that modifies the authorization model itself (e.g., adding a new resource type or a new matching strategy).

**What's dynamic (changes at runtime, no compilation):**

The grants data — who can do what on which resources. This is different for every deployment and changes frequently (new users, new teams, permission changes). It's passed to the compiled WASM policy as the `data` context via `policy.data(jsonString)`. Updating it is just passing a new JSON string — no recompilation, no restart, no downtime.

**The flow:**

```
At Registry build time (CI/CD, once per release):
  registry-authz.rego → opa build -t wasm → registry-authz.wasm → bundled in container image

At runtime (on every request):
  Grants in DB → serialize to JSON → policy.data(json) → evaluate → allow/deny

When grants change (admin adds/removes a permission):
  Updated grants in DB → re-serialize to JSON → policy.data(newJson) → done, no restart
```

The WASM binary is like a compiled Java class — you don't recompile it every time the database changes. The logic is compiled once, and it evaluates whatever data you give it.

### Compiling the policy (build time only)

```bash
opa build -t wasm -e 'registry/authz/allow' registry-authz.rego -o registry-authz-bundle.tar.gz
# Extract the .wasm file from the bundle
tar xzf registry-authz-bundle.tar.gz policy.wasm
mv policy.wasm registry-authz.wasm
```

## Running the tests

```bash
mvn test -pl app -Dtest=OpaWasmAccessControllerTest -Dcheckstyle.skip=true
```

## Comparison with Kroxylicious Authorizer POC

| | Kroxylicious ACL | OPA WASM |
|---|---|---|
| **Dependencies** | `kroxylicious-api` (Kafka transitive, excluded) | `opa-java-wasm` (Chicory + Jackson, lightweight) |
| **Policy language** | Custom ACL DSL | Rego (industry standard) |
| **Ecosystem** | Kroxylicious-specific | OPA ecosystem (Styra, bundles, conftest, playground) |
| **Expressiveness** | ACL-style name matching | Arbitrary logic (RBAC, ABAC, ReBAC, time-based) |
| **Admin UX** | Edit rules file (learn DSL) | Edit JSON data file (no Rego knowledge needed) |
| **Policy management** | Static file only | Data-driven; policy stays fixed, data changes |
| **Compilation step** | None (rules parsed at startup) | `opa build -t wasm` required when policy logic changes |
| **Thread safety** | Synchronous, single-threaded | Thread-safe pool (`OpaPolicyPool`) |
| **Built-in batching** | `Authorizer.authorize(List<Action>)` | Evaluate per-item (in-process, negligible overhead) |

## Known limitation: static JSON file doesn't scale

The POC loads permissions from a static JSON file. This works for a handful of users but breaks down at scale — a file with hundreds or thousands of user-to-resource grants becomes impossible to manage by hand, and every change requires editing the file and redeploying.

### Production path: database-backed grants with management API

The natural evolution is to store grants in Registry's own database and expose a management API:

1. **New `acl_grants` table** in Registry's SQL storage. Each row is a grant: `(principal, principal_type, operation, resource_type, resource_pattern_type, resource_pattern)`. Same structure as the JSON grants, but queryable and transactional.

2. **Management REST API** at `/apis/registry/v3/admin/acl`. CRUD endpoints for grants:
   - `GET /acl` — list all grants (filterable by principal, resource type)
   - `POST /acl` — create a grant
   - `DELETE /acl/{id}` — revoke a grant
   - Protected by `AuthorizedLevel.Admin` — only admins can manage ACLs.

3. **Startup and refresh.** On startup, load all grants from the database and serialize them as the OPA data context. Periodically refresh (or refresh on change via a simple version counter) so the in-memory policy data stays current without restarts.

4. **UI integration.** The Console can expose a permissions management page backed by the same API. Admins add/remove grants through the UI instead of editing files.

5. **Audit trail.** Every grant change is a database operation — standard audit logging applies. Who granted what, when.

This approach is fully self-contained (no external infrastructure beyond what Registry already needs), scales to any number of users and grants, and provides a proper management interface. The Rego policy stays generic and unchanged — only the data fed into it changes.

### Alternative: IdP groups instead of per-user grants

For organizations that already manage team membership in their IdP (Keycloak, Azure AD, Okta), a simpler approach is to map IdP groups to resource permissions rather than managing per-user grants. The grants table would have entries like `(principal_type=group, principal=team-a-developers, operation=write, resource_pattern=team-a/*)`, and the Rego policy would check group membership from JWT claims. This keeps the grants list short (one entry per group, not per user) and avoids duplicating user management that already happens in the IdP.

### Alternative production path: ConfigMap/file-based grants with hot-reload

For teams that manage infrastructure as code and don't need a self-service UI, grants can stay in a JSON file managed through GitOps:

1. **Grants live in a JSON file** mounted as a ConfigMap in Kubernetes (or a local file in bare-metal deployments). Same JSON structure as the POC. Admins manage permissions through their existing GitOps workflow — PR to change permissions, review, merge, ArgoCD/Flux syncs the ConfigMap.

2. **Hot-reload on file change.** Registry watches the mounted file and re-reads it when it changes. No pod restart needed — Kubernetes propagates ConfigMap updates to the mounted volume, Registry detects the change and calls `policy.data(newJson)`.

3. **GitOps-native.** Permissions are version-controlled, reviewed, and audited through Git history. No extra audit infrastructure needed.

4. **No database dependency for authorization.** The policy engine is fully self-contained with the mounted file. Authorization works even if the database is unavailable.

**Trade-offs vs database-backed:**

| | Database + API | ConfigMap/file |
|---|---|---|
| **Management UX** | REST API, UI | Git PRs, kubectl |
| **Audit trail** | DB-level logging | Git history |
| **Scale** | Any number of grants | Best for role/group-based (small file) |
| **Infrastructure** | Requires Registry's database | No extra dependencies |
| **Best fit** | Teams wanting self-service UI | Infrastructure-as-code teams |

Both paths can coexist — Registry could load from file if configured, fall back to database otherwise.

## What's next

- **Database-backed grants with management API** — replace the static JSON file with a proper storage and REST interface
- **Hot-reload of permissions data** — refresh the OPA data context when grants change without restart
- **IdP group-based evaluation** — populate roles/groups from JWT claims instead of a static mapping
- **Registry as policy registry** — store policies as versioned artifacts, serve via OPA Bundle API
