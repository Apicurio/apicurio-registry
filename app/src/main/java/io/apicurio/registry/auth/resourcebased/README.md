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

## What's next

- Validate with a full `@QuarkusTest` using Keycloak for authentication + ACL rules for authorization
- Discuss with Kroxylicious team about publishing authorizer artifacts independently
- Add search/list result filtering using batched `authorize()` calls
- Consider role-based principals (not just `User`) for RBAC+ACL hybrid
