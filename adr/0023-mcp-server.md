# ADR-0023: MCP server

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** the [`mcp/`](../mcp/) module; open work in
  [#11094](https://github.com/Apicurio/apicurio-registry/issues/11094) and
  [#10641](https://github.com/Apicurio/apicurio-registry/issues/10641).
- **Scope:** The registry's own MCP server: a separate application that exposes registry operations
  as MCP tools to AI clients, its two transports, how it authenticates to the registry, and its tool
  set. Not covered: the agent-registry features inside the registry application (MCP tool artifacts,
  the MCP Registry API at `/mcp-registry/v0.1`, A2A, ARD), which are in
  [ADR-0014](0014-agent-registry.md); how the registry authenticates callers
  ([ADR-0009](0009-authentication.md)); the Java SDK itself ([ADR-0022](0022-client-sdks-and-cli.md)).

## Reading this document

- Start with [the decision](#decision).
- For deployment: [transports and authentication](#transports-and-authentication),
  [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc).
- For a running system: the [mcp-keycloak example](../examples/mcp-keycloak/).
- For code changes: the [source map](#source-map) and [verification](#example-and-verification).

## Context

AI assistants (Claude Desktop, Cursor, VS Code, MCP Inspector, agent frameworks) talk to tools
through the Model Context Protocol. Users want to ask such an assistant to list groups, read a
schema, create a version or test a change against the rules, without writing REST calls. The
registry already has a complete, versioned REST API, a generated Java SDK and an authorization model
(roles, owner) enforced on every call. Whatever exposes the registry to MCP clients must not weaken
that model: a tool call has to be authorized exactly like the equivalent REST call, and the identity
the registry sees must be either a configured service account or the human caller.

Note the two different "MCP" things in this repository. The registry *stores* MCP metadata (tool
artifacts, an MCP Registry API) for agent discovery ([ADR-0014](0014-agent-registry.md)). This ADR is
about the opposite direction: an MCP *server* that lets an AI client *use* the registry.

## Decision

1. **A separate Quarkus application, not part of the registry app.** The `mcp/` module
   (`apicurio-registry-mcp-server`) is built on the Quarkiverse `quarkus-mcp-server` extension
   (`quarkus-mcp-server.version`, 1.13.2, in the root [`pom.xml`](../pom.xml)) with the stdio, SSE and
   HTTP transport artifacts. It is shipped as a fast-jar distribution and as the image
   `quay.io/apicurio/apicurio-registry-mcp-server` (tagged by
   [`release-images.yaml`](../.github/workflows/release-images.yaml); examples select it with
   `MCP_SERVER_IMAGE`).
2. **It reaches the registry only through the Java SDK.** `RegistryClientResolver` builds a
   `RegistryClient` from `registry.url` (default `localhost:8080`) with the JDK HTTP adapter, retry
   and the `apicurio.mcp.tls.*` settings; `RegistryService` wraps the SDK calls the tools use. The
   server has no storage and no access to the registry's database, so every tool call is a REST call
   that the registry authorizes.
3. **Both transports are compiled in; HTTP is closed unless enabled at runtime.**
   `quarkus.mcp.server.stdio.enabled` and `quarkus.mcp.server.http.enabled` are both `true` in the
   packaged `application.properties`, with `/mcp` as the HTTP root path. `apicurio.mcp.http.enabled`
   defaults to `false`, and `McpHttpGateFilter` answers `503` on `/mcp/**` and
   `/.well-known/oauth-protected-resource` until it is set.
4. **HTTP mode requires inbound OIDC.** When `apicurio.mcp.http.enabled=true`,
   `McpHttpAuthValidator` refuses to start unless the HTTP transport is compiled in and
   `quarkus.oidc.tenant-enabled=true` (OIDC is off by default so stdio does not need an issuer). It
   also validates `registry.url` and, when no client credentials are configured, probes its host and
   port with a 3 s TCP connect.
5. **Two identity models, one per transport.** stdio uses one OAuth2 client-credentials service
   account (`apicurio.mcp.auth.*`) for every call. HTTP, with `apicurio.mcp.http.forward-token=true`
   (the default), forwards each caller's bearer token to the registry, so the registry applies that
   caller's roles and records them as owner.
6. **Wrong credentials stop the server at startup.** `ClientCredentialsStartupCheck` requests a token
   when `apicurio.mcp.auth.enabled=true`; see [startup check](#startup-credentials-check).
7. **Tools are thin wrappers with uniform errors.** Every tool calls `RegistryService` inside
   `Utils.handleError`, which turns registry errors into an MCP tool error (`ToolCallException`)
   instead of a protocol failure. Mutating admin operations are restricted by safe mode.

### Why a separate server

The MCP server reuses the authorization the registry already enforces by being an ordinary REST
client of it. It can run where the MCP client is (stdio is one process per session, started by the
client), scale and be upgraded on its own, and stay out of the registry's dependency set and attack
surface. The cost is one more deployable and a network hop per tool call.

## Transports and authentication

| | stdio | HTTP (Streamable HTTP at `/mcp`) |
|---|---|---|
| Started by | The MCP client, one process per session | The operator, long-running |
| Inbound authentication | None: the client owns the process | OIDC bearer token (Quarkus OIDC); audience via `QUARKUS_OIDC_TOKEN_AUDIENCE` |
| Identity seen by the registry | The `apicurio.mcp.auth.*` service account, or anonymous if auth is disabled | The caller's forwarded token |
| Registry client | One shared client built at startup | A new client per request with the caller's token, sharing cached transport options |
| Startup check | `system.info()` call, plus the token check when auth is enabled | Config validation; TCP probe of `registry.url` |

**Token forwarding.** `RegistryClientResolver.getClient()` takes the token from the request's
`SecurityIdentity` (`AccessTokenCredential`) or `JsonWebToken`. It rejects a JWT whose `exp` is more
than 30 s in the past, so callers get a clear "re-authenticate" message instead of a registry 401;
opaque tokens are not checked. If forwarding is on and an authenticated request carries no usable
token, it throws rather than fall back to the service account, so per-user roles cannot be widened to
the service account's. If both `forward-token` and `auth.enabled` are set, the validator logs that
client credentials are used only for stdio.

**OAuth discovery.** With `QUARKUS_OIDC_RESOURCE_METADATA_ENABLED=true`, Quarkus OIDC serves
`/.well-known/oauth-protected-resource`, naming the authorization server so HTTP clients can start a
login; the gate filter blocks it while HTTP mode is disabled.

**What the registry enforces.** The MCP server does no authorization of its own. The registry applies
its roles (`sr-readonly`, `sr-developer`, `sr-admin`), owner-based checks and per-resource rules
([ADR-0005](0005-per-resource-authorization.md)) to the identity above. A 403 reaches the client as a
tool error; for authenticated callers it adds a hint naming the registry roles, which is withheld from
anonymous callers.

### Startup credentials check

`ClientCredentialsStartupCheck` posts a client-credentials request to the token endpoint before the
server accepts sessions:

- `200` with a non-empty `access_token`: start.
- Any `4xx` (wrong client ID or secret, disabled client): fail at once, naming
  `APICURIO_MCP_AUTH_CLIENT_ID` and `APICURIO_MCP_AUTH_CLIENT_SECRET`.
- `5xx`, connection errors and timeouts: retry up to 6 attempts with exponential backoff (1 s,
  doubling, capped at 8 s), all within an overall 60 s deadline that also bounds each request and
  body read.
- Error text reports only the OAuth2 `error` and `error_description` fields, and the secret is
  removed from it in raw and form-encoded forms.

## Tools

Tool names are the Java method names (snake_case); descriptions and argument texts are shared
constants in [`Descriptions.java`](../mcp/src/main/java/io/apicurio/registry/mcp/Descriptions.java).

| Server class | Tools | Writes? |
|---|---|---|
| `SystemMCPServer` | `get_server_info` | No |
| `AdminMCPServer` | `get_artifact_types`, `list_configuration_properties`, `get_configuration_property`, `update_configuration_property` | Last one |
| `GroupsMCPServer` | `list_groups`, `get_group_metadata`, `search_groups`, `create_group`, `update_group_metadata` | `create_*`, `update_*` |
| `ArtifactsMCPServer` | `list_artifacts`, `get_artifact_metadata`, `search_artifacts`, `create_artifact`, `update_artifact_metadata` | `create_*`, `update_*` |
| `VersionsMCPServer` | `list_versions`, `get_version_content`, `get_version_metadata`, `search_versions`, `create_version`, `update_version_state`, `update_version_metadata`, `update_version_content` | `create_*`, `update_*` |
| `RulesMCPServer` | `test_schema_rules` (a dry-run version create against the configured rules) | No |
| `AgentsMCPServer` | `discover_agents`, `get_agent_card`, `search_mcp_tools`, `get_mcp_tool` | No |
| `ArdMCPServer` | `ard_search` | No |
| `PromptTemplateMCPServer` | `list_mcp_prompts`, `get_mcp_prompt`, `render_prompt_template` | No |

MCP prompts: `create_new_artifact_version` (`MCPPrompts`) and `render_registry_prompt`
(`PromptTemplateMCPServer`). There are no delete tools.

- **Required arguments** are the `@ToolArg` parameters not marked `required = false`.
  `create_artifact` requires `artifactType`, because an artifact created without content has nothing
  to detect the type from.
- **Safe mode** (`apicurio.mcp.safe-mode`, default `true`): `update_configuration_property` may only
  change `apicurio.rest.mutability.artifact-version-content.enabled`.
- **Paging:** list and search tools ask for `apicurio.mcp.paging.limit + 1` items; with
  `limit-error=true` (default) they fail when more exist instead of returning a silently truncated
  list.
- There is no read-only mode and no experimental flag on individual tools: what a caller can change
  is decided by the registry's authorization.

## Configuration

Properties come from [`McpConfig`](../mcp/src/main/java/io/apicurio/registry/mcp/McpConfig.java)
(prefix `apicurio.mcp`) and the packaged `application.properties`.

| Property | Default | Meaning |
|---|---|---|
| `registry.url` | `localhost:8080` | Registry base URL used by the SDK client |
| `apicurio.mcp.http.enabled` | `false` | Open the HTTP transport (requires OIDC) |
| `apicurio.mcp.http.forward-token` | `true` | Forward the caller's bearer token to the registry |
| `apicurio.mcp.auth.enabled` | `false` | OAuth2 client credentials to the registry |
| `apicurio.mcp.auth.token-endpoint`, `.client-id`, `.client-secret`, `.scope` | (none) | Client-credentials settings; the first three are required when enabled |
| `apicurio.mcp.safe-mode` | `true` | Whitelist for `update_configuration_property` |
| `apicurio.mcp.paging.limit` | `200` | Maximum items a list or search tool returns |
| `apicurio.mcp.paging.limit-error` | `true` | Fail instead of truncating above the limit |
| `apicurio.mcp.tls.trust-all` / `.verify-host` | `false` / `true` | TLS to the registry and token endpoint |
| `apicurio.mcp.tls.truststore.*`, `.keystore.*` | (none) | JKS, PKCS12 or PEM trust store; JKS or PKCS12 key store for mTLS |
| `quarkus.mcp.server.stdio.enabled`, `quarkus.mcp.server.http.enabled` | `true`, `true` | Transports compiled in (build time) |
| `quarkus.oidc.tenant-enabled` | `false` | Must be `true` for HTTP mode |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Config | [`McpConfig`](../mcp/src/main/java/io/apicurio/registry/mcp/McpConfig.java), [`application.properties`](../mcp/src/main/resources/application.properties) | Properties and transport defaults |
| HTTP gate | [`McpHttpGateFilter`](../mcp/src/main/java/io/apicurio/registry/mcp/McpHttpGateFilter.java) | 503 on `/mcp` and resource metadata until enabled |
| HTTP startup | [`McpHttpAuthValidator`](../mcp/src/main/java/io/apicurio/registry/mcp/McpHttpAuthValidator.java) | Requires OIDC and HTTP transport; checks `registry.url` |
| Credentials check | [`ClientCredentialsStartupCheck`](../mcp/src/main/java/io/apicurio/registry/mcp/ClientCredentialsStartupCheck.java) | Token request, retry, redaction |
| Registry client | [`RegistryClientResolver`](../mcp/src/main/java/io/apicurio/registry/mcp/RegistryClientResolver.java) | Service-account or forwarded-token client, TLS |
| Operations | [`RegistryService`](../mcp/src/main/java/io/apicurio/registry/mcp/RegistryService.java) | SDK calls, paging limit, safe mode |
| Errors | [`Utils`](../mcp/src/main/java/io/apicurio/registry/mcp/Utils.java) | `handleError`, registry error formatting |
| Tools | [`servers/`](../mcp/src/main/java/io/apicurio/registry/mcp/servers/) | `@Tool` and `@Prompt` methods |
| Prompts | [`PromptTemplateConverter`](../mcp/src/main/java/io/apicurio/registry/mcp/PromptTemplateConverter.java) | Renders `PROMPT_TEMPLATE` content |

## Limitations and open work

1. List and search tools cannot page; they fail above `apicurio.mcp.paging.limit`
   ([#10641](https://github.com/Apicurio/apicurio-registry/issues/10641)).
2. Tool results include an empty `additionalData` on every object
   ([#10957](https://github.com/Apicurio/apicurio-registry/issues/10957)).
3. No rule-management tools; only `test_schema_rules`
   ([#9741](https://github.com/Apicurio/apicurio-registry/issues/9741)).
4. The stability level of each transport is not stated
   ([#11094](https://github.com/Apicurio/apicurio-registry/issues/11094)).
5. The operator does not deploy the MCP server
   ([#10295](https://github.com/Apicurio/apicurio-registry/issues/10295)); generating client config is
   an open question ([#9966](https://github.com/Apicurio/apicurio-registry/issues/9966)).
6. Prompt rendering does not support `{{else}}`/`{{#unless}}` or nested `{{#if}}`
   ([#9753](https://github.com/Apicurio/apicurio-registry/issues/9753),
   [#9461](https://github.com/Apicurio/apicurio-registry/issues/9461)) and is separate from the REST
   path's rendering ([#9483](https://github.com/Apicurio/apicurio-registry/issues/9483)).
7. Token expiry is checked only for JWTs; in stdio mode every caller shares one service account.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| MCP endpoint inside the registry app | One deployable fewer and no extra hop, but stdio (one process per client session) would be impossible, and the MCP extension and its endpoints would be in every registry deployment. |
| Direct storage access instead of the SDK | Faster, but would bypass the REST layer's authorization, validation and rules, and couple the server to every storage variant. |
| stdio only | Simplest and no inbound auth, but one shared identity and no remote or multi-user use. |
| HTTP only | Per-user identity, but needs an OIDC provider and a running service even for one local user. |
| HTTP with a service account instead of forwarding | Supported (`forward-token=false`), but every caller then has the account's roles; forwarding is the default. |

## Consequences

- **Benefits:** the registry's authorization applies unchanged to AI clients; HTTP callers keep their
  own roles and ownership; misconfiguration (no OIDC, wrong secret, unreachable registry) fails at
  startup; the tools follow the API through the generated SDK.
- **Costs:** a second artifact and image to release and run; each tool is hand-written and the user
  documentation lists them separately, so new API features need new tools; HTTP mode needs several
  Quarkus OIDC settings beyond `apicurio.mcp.*`.

## Example and verification

- **Example:** [`examples/mcp-keycloak`](../examples/mcp-keycloak/): `docker compose up -d &&
  ./demo.sh` drives the server over stdio (as `admin-client` and `readonly-client`, and with a wrong
  secret that stops startup) and over HTTP (no, malformed and wrong-audience tokens refused with 401;
  `sr-developer` writes owned by the caller; `sr-readonly` and no-role callers refused with 403).
  See its [guide](../examples/mcp-keycloak/GUIDE.md).
- [`examples/agent-registry`](../examples/agent-registry/) runs the server in HTTP mode for
  `discover_agents` and per-user agent visibility.
- **User documentation:**
  [`assembly-mcp-server-integration.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc)
  and [`assembly-mcp-tool-features.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-mcp-tool-features.adoc).

| Concern | Tests to read |
|---|---|
| HTTP gate and defaults | `McpHttpGateFilterTest`, `McpHttpDisabledByDefaultTest`, `PackagedConfigDefaultsTest` |
| HTTP startup validation | `McpHttpAuthValidatorTest` |
| Token forwarding and expiry | `McpHttpTokenForwardingTest`, `RegistryClientResolverTest` |
| Client credentials | `McpAuthenticationTest`, `ClientCredentialsStartupCheckTest` |
| Tools, errors and paging | `ArtifactsMCPServerTest`, `RegistryServiceTest`, `UtilsErrorFormattingTest`, `DescriptionsTest` |
| Prompts | `PromptTemplateConverterTest` |

```bash
./mvnw test -pl mcp -Dtest='McpHttp*Test,ClientCredentialsStartupCheckTest,RegistryClientResolverTest'
```

## History

| Milestone | Result |
|---|---|
| `c1f13723da` (2025-06-17) | MCP server moved to the root `mcp/` module |
| [#8460](https://github.com/Apicurio/apicurio-registry/pull/8460) | HTTP transport with OAuth token forwarding |
| [#9991](https://github.com/Apicurio/apicurio-registry/pull/9991) | `ard_search` tool |
| [#11283](https://github.com/Apicurio/apicurio-registry/pull/11283) | `create_artifact` requires `artifactType` |
| [#11323](https://github.com/Apicurio/apicurio-registry/pull/11323) | Client credentials checked at startup: fail fast on 4xx, bounded retry, secret redaction |
