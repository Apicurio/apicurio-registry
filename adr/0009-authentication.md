# ADR-0009: Authentication

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-10.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#11043](https://github.com/Apicurio/apicurio-registry/issues/11043),
  [#11044](https://github.com/Apicurio/apicurio-registry/issues/11044),
  [#11046](https://github.com/Apicurio/apicurio-registry/issues/11046).
- **Scope:** How the registry establishes who is calling: the mechanisms, how they combine, the
  401/403 contract, the coarse role-based gates that consume the identity, the web console's login,
  and how clients authenticate. Which resources an identity may use (per-resource rules, owner bypass)
  is [ADR-0005](0005-per-resource-authorization.md).

## Reading this document

- Start with [the decision](#decision).
- For deployment: [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc).
- For a running system: the [example](../examples/authentication/), which checks every mechanism
  and client below, including the rejections.

## Context

The registry runs in very different places: a developer laptop with no identity provider, a company
with Keycloak or Entra ID, a platform where an authenticating proxy already sits in front of every
service, and Kubernetes, where workloads already have an identity. Its clients are just as varied:
browsers, Java applications, Kafka serializers, build tools, a CLI, tools that only speak HTTP basic.
Authentication has to fit all of them without each feature reimplementing it, and authorization has
to work the same whatever established the identity.

## Decision

1. **One chain of mechanisms, tried in order.** `AppAuthenticationMechanism` builds an ordered chain
   from `apicurio.authn.mechanism.priority` (default `basic,proxy-header,oidc`) of the mechanisms that
   are enabled; the first that produces an identity wins, otherwise the caller is anonymous. OIDC and
   Kubernetes cannot both be enabled (both read bearer tokens); startup fails.
2. **Delegate to Quarkus where it already does the job:** OIDC bearer tokens, HTTP basic and form
   login against a Quarkus identity provider. Registry-specific mechanisms (basic as client
   credentials, proxy headers, Kubernetes TokenReview) produce the same `SecurityIdentity`.
3. **Authentication is lazy; authorization decides the status.** Requests are not rejected for lack
   of credentials until an operation needs an identity: anonymous callers get **401**, identified
   callers without permission **403**. With no mechanism enabled, there is no check at all.
4. **Roles are separate from authentication.** The role source (`token` by default, `application`,
   `header`, `kubernetes`) maps the identity to `sr-admin`, `sr-developer` or `sr-readonly`; roles,
   owner-only and per-resource rules all consume the same identity.
5. **Clients get first-class support** for the same mechanisms: OAuth2 client credentials, basic and
   bearer tokens in the Java SDK, serdes, Maven plugin and CLI.

### Why a chain

Deployments mix callers: a proxy authenticates browsers while services present tokens; basic
credentials must keep working for old tools. A chain lets each request be identified by whatever it
carries, without separate endpoints or tenants, and keeps every mechanism optional.

### Why basic as client credentials

Many tools only support HTTP basic. Exchanging `client id:secret` for a token keeps the identity
provider as the single source of truth for credentials and roles, while those tools keep working.

## Mechanisms

| Mechanism | Enabled by (default off) | Identity | Notes |
|---|---|---|---|
| `oidc` | `quarkus.oidc.tenant-enabled` | Token subject, roles from the token | Signature and audience (`quarkus.oidc.client-id`) checked by Quarkus OIDC |
| basic as client credentials | `apicurio.authn.basic-client-credentials.enabled` (with OIDC) | The service account | `Basic id:secret` exchanged with `grant_type=client_credentials` (`quarkus.oidc.token-path`); tokens cached until `exp` minus `cache-expiration-offset` (10 s), failures cached 60 s; concurrent requests share one fetch; retried on IdP errors |
| basic, password grant | `quarkus.oidc.client-secret` set | The user | `Basic user:password` exchanged with `grant_type=password` |
| `basic` | `quarkus.http.auth.basic` | A user of a Quarkus identity provider | For example `quarkus.security.users.embedded.*`: no identity provider needed |
| `form` | `quarkus.http.auth.form.enabled` | As `basic` | Session cookies: needs CSRF protection |
| `proxy-header` | `apicurio.authn.proxy-header.enabled` | `X-Forwarded-User`; roles from `X-Forwarded-Groups` | No header: falls through to the next mechanism. `trust-proxy-authorization` skips the registry's own authorization |
| `kubernetes` | `apicurio.authn.kubernetes.enabled` (experimental) | Token's user; groups as roles with `role-source=kubernetes` | TokenReview against the API server, cached by token hash (`cache-expiration`, 5 min); API errors make the request anonymous |

### Status codes

| Situation | Status |
|---|---|
| No credentials, operation needs an identity | 401 `User is not authenticated.` |
| Invalid token, wrong password or secret | 401 |
| Identified, role does not allow the operation (or no role) | 403 |
| Anonymous read with `apicurio.auth.anonymous-read-access.enabled` | 200 for reads, 401 for writes |
| Malformed basic header with client credentials | 500 ([#11043](https://github.com/Apicurio/apicurio-registry/issues/11043)) |

### Web console

`GET /system/uiConfig` tells the console the type (`oidc`, `basic` or `none`) and the OIDC settings
(`apicurio.ui.auth.oidc.*`); a separately deployed console reads `REGISTRY_AUTH_*`. The console uses
the authorization code flow with a public client, and hides what the roles do not allow.

### Behaviour across storage variants

Authentication does not touch storage, except the `application` role source, whose role mappings are
stored like other registry data on every variant.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `apicurio.authn.mechanism.priority` | `basic,proxy-header,oidc` | Order of the chain |
| `quarkus.oidc.tenant-enabled` | `false` | OIDC bearer tokens |
| `apicurio.authn.basic-client-credentials.enabled` | `false` | Basic as client credentials |
| `apicurio.authn.basic-client-credentials.cache-expiration` | `5` (minutes) | Token cache when the token's expiry cannot be read |
| `apicurio.authn.basic-client-credentials.cache-expiration-offset` | `10` (seconds) | Refresh before expiry |
| `quarkus.http.auth.basic` | `false` | Basic against a Quarkus identity provider |
| `apicurio.authn.proxy-header.enabled` | `false` | Identity from proxy headers |
| `apicurio.authn.proxy-header.trust-proxy-authorization` | `false` | Skip the registry's authorization for proxied callers |
| `apicurio.authn.kubernetes.enabled` | `false` | Kubernetes TokenReview (needs `apicurio.features.experimental.enabled`) |
| `apicurio.auth.role-based-authorization` | `false` | Role checks |
| `apicurio.auth.role-source` | `token` | `token`, `application`, `header`, `kubernetes` |
| `apicurio.auth.anonymous-read-access.enabled` | `false` | Anonymous reads (dynamic) |
| `apicurio.auth.authenticated-read-access.enabled` | `false` | Reads for identities with no role |
| `apicurio.auth.admin-override.enabled` | `false` | Admin from a role, claim or user name |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Chain | [`AppAuthenticationMechanism`](../app/src/main/java/io/apicurio/registry/auth/AppAuthenticationMechanism.java) | Priority, chain, client-credentials exchange and cache |
| Strategies | [`auth`](../app/src/main/java/io/apicurio/registry/auth/) | `OidcAuthenticationStrategy`, `DelegatingAuthenticationStrategy`, `ProxyHeaderAuthenticationMechanism`, `KubernetesAuthenticationStrategy`, `TokenReviewClient` |
| Configuration | [`AuthConfig`](../app/src/main/java/io/apicurio/registry/auth/AuthConfig.java) | Every `apicurio.authn.*` and `apicurio.auth.*` setting |
| Gates | [`AuthorizedInterceptor`](../app/src/main/java/io/apicurio/registry/auth/AuthorizedInterceptor.java) | 401/403, anonymous and authenticated read, RBAC, owner-only, admin override |
| Roles | `TokenRoleProvider`, `StorageRoleProvider`, `HeaderRoleProvider`, `KubernetesRoleProvider` | Role sources |
| Clients | [`java-sdk`](../java-sdk/), [`schema-resolver`](../schema-resolver/), [`utils/maven-plugin`](../utils/maven-plugin/), [`cli`](../cli/) | Client authentication |

## Limitations and open work

1. A malformed basic header returns 500 with an internal message when client credentials are enabled
   ([#11043](https://github.com/Apicurio/apicurio-registry/issues/11043)).
2. Resetting a dynamic setting such as anonymous read access has no effect until restart
   ([#11044](https://github.com/Apicurio/apicurio-registry/issues/11044)); set it explicitly instead.
3. The Java SDK's `bearerToken()` only works with the JDK adapter
   ([#11046](https://github.com/Apicurio/apicurio-registry/issues/11046)).
4. The challenge (`WWW-Authenticate`) comes from the last mechanism of the chain only.
5. A token fetch that hangs, rather than fails, blocks the requests waiting for it (no timeout).
6. When the Kubernetes API is unavailable, Kubernetes callers become anonymous; with anonymous read
   enabled they can still read.
7. Proxy-header authentication trusts the network: the registry must not be reachable except through
   the proxy, and the proxy must overwrite the headers.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| One mechanism per deployment | Not selected: real deployments mix browsers, services and legacy tools |
| Registry-managed users and passwords | Only for development (`basic` with embedded users): credentials belong in an identity provider |
| Reject anonymous requests up front | Not selected: anonymous read access and unprotected endpoints need lazy authentication |
| Roles only from tokens | Kept as default; `application`, `header` and `kubernetes` sources cover identity providers that cannot carry roles |

## Consequences

- **Benefits:** every deployment style is covered with configuration only; one identity model for
  roles, ownership and per-resource rules; clients support the same mechanisms.
- **Costs:** many interacting settings; a misordered chain or a proxy reachable directly is a
  security risk; tokens are validated online for Kubernetes and exchanged for basic credentials,
  which adds identity-provider load and latency (mitigated by caching).

## Example and verification

- **Example:** [`examples/authentication`](../examples/authentication/):
  `docker compose up -d && ./demo.sh` checks anonymous access, users and service accounts with roles,
  basic as client credentials and its cache, embedded users, the proxy (including a spoofed header),
  Kubernetes tokens with k3s, the SDK, serdes, Maven plugin and CLI, and the console login.
- **User documentation:**
  [`assembly-configuring-registry-security.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-registry-security.adoc).

| Concern | Tests to read |
|---|---|
| Chain and configuration | `AppAuthenticationMechanismTest`, `AuthConfigAuthenticationEnabledTest` (app) |
| OIDC and client credentials | `SimpleAuthTest`, `OidcAuthenticationStrategyTest`, `OidcFailureCacheTest`, `OidcTokenStampedeTest`, `OidcRetryQuarkusTest` (app) |
| Basic, form, proxy | `BasicAuthWithPropertiesTest`, `FormAuthTest`, `ProxyHeaderAuthTest`, `ProxyHeaderTrustAuthTest`, `ProxyAndOidcDualAuthTest` (app) |
| Kubernetes | `KubernetesAuthenticationStrategyTest`, `TokenReviewClientTest`, `TokenReviewClientCircuitBreakerTest` (app) |
| Gates and roles | `AuthorizedInterceptorTest`, `AuthTestAnonymousCredentials`, `AuthTestAuthenticatedReadAccess`, `AuthTestLocalRoles`, `AuthTestNoRoles` (app) |
| Clients | `JdkAdapterAuthenticationTest` (java-sdk), `AuthCommandTest` (cli), `MojoAuthTest` (app) |

```bash
./mvnw test -pl app -Dtest='AppAuthenticationMechanismTest,OidcAuthenticationStrategyTest,ProxyHeaderAuthTest'
```

## History

| Milestone | Result |
|---|---|
| 3.2.3 | Configurable mechanism priority (`apicurio.authn.mechanism.priority`) |
| 3.3.0 | Kubernetes TokenReview authentication (experimental) |
| 3.3.1 | Form authentication |
| This ADR | First written record, with a verified example |
