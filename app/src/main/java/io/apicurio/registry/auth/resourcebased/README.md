# POC: Per-Resource Authorization with Kroxylicious Authorizer

Proof of concept integrating the [Kroxylicious Authorizer framework](https://github.com/kroxylicious/kroxylicious/tree/main/kroxylicious-authorizer-providers) into Apicurio Registry for fine-grained, per-resource access control.

## What this demonstrates

1. **Kroxylicious Authorizer works with Registry without Kafka on the classpath.** The `kafka-clients` dependency is excluded via Maven `<exclusion>` — the authorizer API (`Subject`, `Principal`, `Authorizer`, `ResourceType`, `Action`) has no runtime dependency on Kafka.

2. **Registry-specific resource types map naturally to the Kroxylicious model.** Two enums — `RegistryArtifact` and `RegistryGroup` — implement `ResourceType` with an `implies()` hierarchy (`Admin` → `Write` → `Read`).

3. **The ACL rules language works with custom resource types.** Admin-friendly rules like:
   ```
   allow User with name = "alice" to {Read, Write} RegistryArtifact with name like "team-a/*";
   ```

4. **Batched authorization enables search result filtering.** The `Authorizer.authorize(subject, List<Action>)` API and `AuthorizeResult.partition()` method are exactly what's needed to filter list/search results per-resource in a single call.

5. **Integration with the existing auth framework is clean.** `ResourceBasedAccessController` implements `IAccessController`, is wired into `AuthorizedInterceptor` after RBAC/OBAC, and is gated by a config flag.

## Architecture

```
REST request
  → AuthorizedInterceptor
    → Admin override check
    → RBAC check (RoleBasedAccessController)
    → OBAC check (OwnerBasedAccessController)
    → Resource-based check (ResourceBasedAccessController)  ← NEW
      → Builds Kroxylicious Subject from SecurityIdentity
      → Builds Action from @Authorized annotation (style + level)
      → Calls Authorizer.authorize(subject, actions)
      → ALLOW or DENY
```

## Files

| File | Purpose |
|------|---------|
| `RegistryArtifact.java` | Resource type enum for artifacts (Read, Write, Admin) |
| `RegistryGroup.java` | Resource type enum for groups (Read, Write, Admin) |
| `ResourceBasedAccessController.java` | `IAccessController` implementation bridging to Kroxylicious `Authorizer` |
| `ResourceBasedAccessControllerConfig.java` | Config properties for enabling and configuring resource-based auth |
| `ResourceBasedAccessControllerInitializer.java` | Startup bean that loads ACL rules and initializes the authorizer |
| `AuthorizedInterceptor.java` | Modified to add resource-based check after RBAC/OBAC |
| `test-acl-rules.acl` | Example ACL rules file used by tests |
| `ResourceBasedAccessControllerTest.java` | 14 tests covering all scenarios |

## Configuration

```properties
# Enable resource-based authorization (default: false)
apicurio.auth.resource-based-authorization.enabled=true

# Path to ACL rules file
apicurio.auth.resource-based-authorization.acl.file=/path/to/rules.acl
```

## ACL Rules File Format

Uses the Kroxylicious ACL rules language. Registry resource types must be imported:

```
from io.apicurio.registry.auth.resourcebased import RegistryArtifact;
from io.apicurio.registry.auth.resourcebased import RegistryGroup;

// Team-based artifact access
allow User with name = "alice" to {Read, Write} RegistryArtifact with name like "team-a/*";
allow User with name = "bob" to Read RegistryArtifact with name like "*";

// Group management
allow User with name = "alice" to {Read, Write} RegistryGroup with name = "team-a";

// Admin access
allow User with name = "admin" to {Read, Write, Admin} RegistryArtifact with name like "*";

otherwise deny;
```

Resource names follow the pattern `{groupId}/{artifactId}` for artifacts and `{groupId}` for groups.

## Authentication: where do users come from?

Authentication and authorization are separate concerns in this design. Authentication is handled entirely by Registry's existing mechanisms — the resource-based authorization layer only consumes the authenticated principal name.

### Supported authentication mechanisms

All of Registry's existing authentication options work unchanged:

- **OIDC/OAuth2** (Keycloak, Azure AD, Okta, Auth0, etc.) — `quarkus.oidc.tenant-enabled=true`
- **HTTP Basic Auth** — `quarkus.http.auth.basic=true` with a properties file or basic-client-credentials exchanged against the OIDC server
- **Proxy headers** — `apicurio.authn.proxy-header.enabled=true` (e.g., behind Envoy, Nginx, or any auth proxy that sets `X-Forwarded-User`)

### How authentication feeds into authorization

The Quarkus `SecurityIdentity` provides the authenticated principal name. The `ResourceBasedAccessController` uses it to build a Kroxylicious `Subject`:

```java
Subject subject = new Subject(new User(securityIdentity.getPrincipal().getName()));
```

The `User` principal in the ACL rules matches against this name. For example, a user authenticates via Keycloak and gets principal `alice`. The rule `allow User with name = "alice" to Read RegistryArtifact with name like "team-a/*"` then grants access. The two layers are fully independent — you can swap authentication providers without changing authorization rules.

### Future: role-based principals

The Kroxylicious `Subject` model supports multiple principals beyond `User`. A future enhancement could populate the `Subject` with additional principals extracted from JWT claims or IdP groups:

```java
Subject subject = new Subject(Set.of(
    new User("alice"),
    new RolePrincipal("team-a-developer")
));
```

This would enable rules based on roles or groups rather than individual usernames:

```
allow RolePrincipal with name = "team-a-developer" to {Read, Write} RegistryArtifact with name like "team-a/*";
```

This is not implemented in the POC but the framework supports it natively.

## Running the tests

```bash
mvn test -pl app -Dtest=ResourceBasedAccessControllerTest -Dcheckstyle.skip=true
```

## Key findings

- **Kafka exclusion works.** No runtime issues with `kafka-clients` excluded.
- **`AclAuthorizer.builder()` is package-private.** External consumers must use the file-based `AclAuthorizerService` API. Worth raising with the Kroxylicious team — making the builder public would enable programmatic rule construction.
- **`AuthorizeResult.partition()` is a perfect fit for search filtering.** Pass a list of search results and get back allowed/denied partitions in one call.
- **The `implies()` mechanism works.** Granting `Admin` automatically grants `Write` and `Read`.

## Known limitation: policy management

The biggest operational caveat of this POC is policy lifecycle management. ACL rules live in a static file, so any change (adding a user, removing access, onboarding a new team) requires:

1. Editing the rules file
2. Updating the ConfigMap or volume mount (in Kubernetes)
3. Restarting the pod — or waiting for mount propagation if hot-reload is implemented

There is no runtime API, no UI, and no audit trail of policy changes. For environments where users and teams change frequently, this is a non-starter.

### Options to address this (roughly ordered by effort)

- **Hot-reload on file change.** Watch the rules file for modifications and re-parse automatically. Avoids pod restarts but still requires ConfigMap updates and provides no management interface.
- **Management REST API.** Add endpoints for CRUD operations on ACL rules, stored in Registry's own database. Rules loaded from DB at startup and cached in-memory. Provides an audit trail and could be exposed in the UI. This is probably the right middle ground for a production feature.
- **Registry as policy registry.** Store authorization policies as versioned artifacts in Registry itself (similar to how schemas are managed). Most powerful and most consistent with Registry's identity, but the largest scope — and raises a chicken-and-egg question (the policies that protect Registry are stored in Registry).

## Alternative: OPA policies via WASM (opa-java-wasm)

A separate POC branch implements the same authorization model using [opa-java-wasm](https://github.com/StyraOSS/opa-java-wasm) instead of the Kroxylicious Authorizer: **[PR #7829](https://github.com/Apicurio/apicurio-registry/pull/7829)** (`issue-7724-opa-wasm` branch).

This evaluates OPA policies compiled to WebAssembly in-process via Chicory (a pure-Java WASM runtime). Andrea has already built a prototype of this for Apicurio.

### Why this may be a better fit

- **No dependency concerns.** Dependencies are Chicory + Jackson — no Kafka, no transitive entanglement risk.
- **Industry standard.** OPA/Rego is widely adopted. Many organizations already run OPA infrastructure, have Rego expertise, and use tooling like Styra DAS, bundle servers, `conftest`, and the Rego playground.
- **More expressive.** Rego can model RBAC, ABAC, relationship-based, attribute-based, and time-based policies — not just ACL-style name matching.
- **Data-driven approach.** Ship a generic Rego policy with Registry; admins only manage a JSON/YAML permissions file (who can access what). No Rego knowledge or WASM compilation needed for day-to-day policy changes.
- **Better policy management story.** OPA has a mature distribution model with bundle servers and hot-reload built in, partially addressing the static-file limitation above.
- **Internal knowledge.** Andrea has already built the opa-java-wasm integration for Apicurio.

### What you lose vs Kroxylicious

- No built-in `ResourceType` enum model, batched `authorize()`, or `AuthorizeResult.partition()` — you build the integration layer yourself (straightforward, but more custom code).
- No typed framework — policy input/output is JSON, not a Java type system.

### Long-term: Registry as a policy registry

With the OPA approach, a natural evolution is to store OPA policies as versioned artifacts in Registry itself (a new artifact type for Rego source or compiled WASM bundles). Registry could expose an OPA Bundle API-compatible endpoint, and the in-process evaluator pulls policy updates automatically. Admin UX becomes: upload a new policy version through the Registry API or UI, same workflow as pushing a schema. This is significant scope, but aligns with Registry's identity as a versioned artifact store.

## Alternative: Keycloak Authorization Services

Keycloak's built-in Authorization Services (UMA 2.0) is a natural candidate since many Registry deployments already use Keycloak for authentication.

### What it offers

- **Rich permission model.** Resources, scopes, policies (role-based, user-based, time-based, JavaScript-based, aggregated), and permission evaluation — all managed via the Keycloak Admin Console UI.
- **Good admin UX.** Permissions are managed through Keycloak's UI, not config files. Non-developers can grant and revoke access without touching code or deployment artifacts.
- **Already in the ecosystem.** Many Registry deployments already use Keycloak. Adding authorization would be a natural extension with no new infrastructure to deploy.
- **Token-based evaluation.** The RPT (Requesting Party Token) flow lets the client request permissions for specific resources, and Keycloak returns what's allowed in the token itself — reducing per-request authorization calls.
- **Standard protocol.** UMA 2.0 is an established standard, not a custom framework.

### Why it's problematic for Registry

- **Resource lifecycle synchronization.** Every artifact in Registry needs a corresponding "resource" registered in Keycloak. Create artifact → create Keycloak resource. Delete artifact → delete Keycloak resource. This synchronization is an ongoing operational burden — if it drifts, authorization breaks silently. With thousands of artifacts changing frequently, keeping the two systems in sync is fragile.
- **Scale concern.** Keycloak's resource server has to manage a resource per artifact. The Protection API isn't designed for bulk "list all resources user X can access" queries, which is what search/list filtering requires. Filtering a search result page would mean querying Keycloak for permissions on every result — the API wasn't designed for this pattern.
- **Tight coupling to Keycloak.** Locks out teams using other IdPs (Azure AD, Okta, Auth0). The whole point of the authorization design is to be pluggable. Building on Keycloak Authorization Services means users who authenticate with other providers can't use per-resource authorization at all.
- **Network latency.** Every authorization check requires a call to Keycloak (unless RPTs are cached). For search result filtering, that's N calls per page. Even with caching, the invalidation problem is hard — how does Registry know when a permission changed in Keycloak?
- **Complexity.** UMA 2.0 is a complex protocol. Debugging permission denials through Keycloak's evaluation chain (policies → permissions → scopes → resources) is non-trivial, especially for administrators unfamiliar with UMA concepts.

### Verdict

Keycloak Authorization Services could work as one **implementation** behind a pluggable authorization SPI — for teams that are already deep in the Keycloak ecosystem and accept the resource synchronization overhead. But it should not be the only option, and the sync problem makes it unsuitable as the default or primary approach.

## Conclusion

This POC validated the authorization *model* (resource types, per-resource checks in the interceptor, search result filtering). The *engine* underneath could be Kroxylicious ACL, OPA WASM, Keycloak, or something else — the `ResourceBasedAccessController` integration point is the same regardless of which evaluator sits behind it.

## What's next

- Validate with a full `@QuarkusTest` using Keycloak for authentication + ACL rules for authorization
- Discuss with Kroxylicious team about publishing authorizer artifacts independently
- Add search/list result filtering using batched `authorize()` calls
- Consider role-based principals (not just `User`) for RBAC+ACL hybrid
