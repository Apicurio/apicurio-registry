# POC: In-Process OPA Authorization via WASM

Proof of concept using [opa-java-wasm](https://github.com/StyraOSS/opa-java-wasm) to evaluate OPA policies in-process for per-resource authorization in Apicurio Registry. This is an alternative to the Kroxylicious Authorizer POC on the `issue-7724` branch.

## How it works

1. Authorization policies are written in [Rego](https://www.openpolicyagent.org/docs/latest/policy-language/) (OPA's policy language)
2. Policies are compiled to WebAssembly: `opa build -t wasm -e 'registry/authz/allow' registry-authz.rego`
3. At startup, Registry loads the compiled `.wasm` file and creates a thread-safe pool of policy evaluators
4. On each request, the interceptor evaluates the policy in-process — no network calls, no external OPA server

## Architecture

```
REST request
  → AuthorizedInterceptor
    → Admin override / RBAC / OBAC checks (unchanged)
    → OPA WASM check (OpaWasmAccessController)
      → Builds JSON input: { user, operation, resource_type, resource_name }
      → Borrows OPA policy instance from pool
      → Sets permissions data (roles, grants)
      → Evaluates WASM policy in-process
      → Returns allow/deny
```

## Separation of concerns: policy vs data

The Rego policy (`registry-authz.rego`) defines the **authorization logic** — it's generic and ships with Registry. It doesn't change when users or permissions change.

The permissions **data** (`permissions.json`) defines **who can access what** — this is what admins manage. It's a simple JSON file:

```json
{
  "roles": {
    "admin": ["admin"],
    "alice": ["team-a-developer"]
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
      "principal": "bob",
      "operation": "read",
      "resource_type": "artifact",
      "resource_pattern": "*"
    }
  ]
}
```

An admin who needs to grant a new user access edits the JSON data file — no Rego knowledge, no WASM compilation.

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

## Compiling the policy

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

## What's next

- **Hot-reload of permissions data** — watch the JSON file for changes and update the policy data at runtime without restart
- **Management REST API** — CRUD operations on grants stored in Registry's database, replacing the JSON file
- **Registry as policy registry** — store policies as versioned artifacts, serve via OPA Bundle API
- **Role-based evaluation** — populate roles from JWT claims or IdP groups instead of a static mapping
