# ADR-0014: Agent Registry

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-16.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#6991](https://github.com/Apicurio/apicurio-registry/issues/6991),
  [#7896](https://github.com/Apicurio/apicurio-registry/pull/7896),
  [#7910](https://github.com/Apicurio/apicurio-registry/pull/7910).
- **Scope:** The agent registry as a whole: its artifact types, discovery endpoints, the MCP Registry
  API, MCP tool import, prompt rendering, and how all of it is kept out of the core registry. The
  discovery standards (AI Catalog, ARD) are [ADR-0004](0004-ai-catalog-ard-convergence.md); custom
  artifact types in general are [ADR-0013](0013-custom-artifact-types.md).

## Context

AI agents, MCP servers, tools, prompts and model descriptions are contracts, like APIs and schemas:
teams publish them, others discover and depend on them, and they change. They need what the
registry already provides (versions, references, compatibility rules, access control), and the
discovery protocols the agent ecosystem uses (A2A `/.well-known` cards, the MCP Registry API, AI
Catalog and ARD). The area moves quickly, while the core registry must stay stable, so the feature
has to be optional, removable, and unable to destabilise core code paths.

## Decision

1. **Agents are artifacts.** Five artifact types, with content accepters, validators, compatibility
   checkers, reference finders and dereferencers: `AGENT_CARD` (A2A), `MCP_SERVER`, `MCP_TOOL`,
   `PROMPT_TEMPLATE` and `MODEL_SCHEMA`. They are native Java types in `schema-util/agents`,
   registered through the `ArtifactTypeProviderContributor` ServiceLoader, and are always
   available: storage, versions, references and rules work for them whether or not any agent
   feature is enabled.
2. **A metadata store, not a runtime.** The registry stores, governs and serves descriptions of
   agents and tools; it does not run, proxy or invoke them (the one exception is MCP tool import,
   which reads a server's tool list on request).
3. **Every protocol surface is a separate, opt-in, experimental feature:**

   | Feature | Property | Surface |
   |---|---|---|
   | A2A | `apicurio.a2a.enabled` | `/.well-known/agent.json` (the registry's own card), `/.well-known/agents`; Agent Cards generated from OpenAPI (`apicurio.a2a.openapi-integration.enabled`) |
   | MCP tools | `apicurio.mcp-tools.enabled` | `/.well-known/mcp-tools`, search by parameter, tool chaining (`/compatible`) |
   | MCP Registry | `apicurio.mcp-registry.enabled` | `/apis/mcp-registry/v0.1/*`, the MCP Registry API over `MCP_SERVER` artifacts |
   | MCP import | `apicurio.mcp-import.enabled` | `POST .../mcp-import`: read a live server's tools into `MCP_TOOL` artifacts |
   | AI Catalog, ARD | `apicurio.ai-catalog.enabled`, `apicurio.ard.enabled` | `/.well-known/ai-catalog.json`, `ard.json`, `/.well-known/ard/*` ([ADR-0004](0004-ai-catalog-ard-convergence.md)) |
   | Prompt rendering | (always, for `PROMPT_TEMPLATE`) | `POST .../versions/{v}/render` |

   Each flag requires `apicurio.features.experimental.enabled=true`; the registry refuses to start
   otherwise, instead of silently ignoring the flag. Flags control endpoints only, never storage.
4. **One-way dependency, enforced.** Agent code lives in `io.apicurio.registry.agents..` (app) and
   `schema-util/agents`. It may use core freely; **core must not reference it.** Core reaches agent
   behaviour only through the extension points in `io.apicurio.registry.extensions`
   (`ArtifactVersionWriteHook`, `PromptRenderHandler`, `McpToolImportHandler`, `FeatureLabelOwner`,
   `ApiExceptionMapper`) and the artifact type ServiceLoader. `AgentsBoundaryTest` (ArchUnit, plus a
   source scan for inlined constants) fails the build otherwise. Endpoints that are part of the core
   API but implemented by agents answer 404 when no handler is present.
5. **Feature metadata in labels uses reserved namespaces.** Agent features that store metadata on
   artifacts use `apicurio.<feature>.*` label keys (`apicurio.mcp-registry.*`,
   `apicurio.a2a.openapi-agent-card.*`, `apicurio.agent.visibility`), declared as constants and, when the
   feature owns them, registered with a `FeatureLabelOwner` so a metadata update that replaces the
   label map keeps them. Labels are user-editable, so none of them carries security decisions except
   `apicurio.agent.visibility`, which is a user-managed **discovery** filter (below), not an
   access-control grant.
6. **Outbound connections are policy-checked.** MCP import connects to a URL supplied in the
   request, so it is an SSRF vector: `McpImportTargetPolicy` requires HTTPS (unless allowed),
   no user information, a host on `apicurio.mcp-import.allowed-hosts` (empty refuses all), and then
   every resolved address must pass `OutboundAddressPolicy`: link-local, multicast, reserved and
   cloud metadata addresses are refused even when listed, and so is loopback unless the
   development override `apicurio.mcp-import.loopback.enabled` is on. Every refusal has the same
   message, so the endpoint cannot probe the network. Response size, tool count and time are bounded.

### Why one registry, not a separate agent registry

Agents reference schemas, APIs and each other, and need the same governance. A separate service
would duplicate storage, rules, auth and UI, and lose cross-references. The cost, coupling an
unstable area to a stable one, is what Decision 4 contains.

### Why an enforced boundary rather than a separate module

The feature uses core storage, REST and rules everywhere; a separate Maven module would need most of
core's API to be public. A package boundary checked at build time gives the same guarantee (core
compiles and behaves the same without agent code) at a fraction of the cost.

## Model

### Discovery and visibility

`apicurio.agent.visibility` on an artifact is `public`, `entitled` or `private` (default
`apicurio.a2a.default-visibility`, `entitled`). When authentication is enabled, discovery endpoints
(`/.well-known/agents`, the AI Catalog, ARD) list `public` entries to anyone, `entitled` entries to
callers with read access to the artifact, and `private` entries to their owner and administrators;
an unknown value is treated as `private`. Without authentication, everything is listed. Visibility
filters discovery; reading
the artifact through the core API is governed by the usual authorization
([ADR-0005](0005-per-resource-authorization.md)).

### Components

| Component | Packages | Notes |
|---|---|---|
| Artifact types | `schema-util/agents` (`content`, `rules`, `types`) | Accepters, validators, compatibility (for example, a removed skill or tool parameter is incompatible), references, dereferencing |
| A2A | `agents.a2a`, `agents.a2a.openapi` | Registry's own card; Agent Cards generated and kept in sync from OpenAPI artifacts |
| Well-known endpoints | `agents.rest.wellknown` | Agents, MCP tools, AI Catalog, ARD |
| MCP tools | `agents.mcptools` | Tool search and chaining compatibility |
| MCP Registry API | `agents.mcpregistry` | v0.1 API, status, cursors, its own error mapper |
| MCP import | `agents.mcpimport` | Client, target policy, idempotent import (`UNCHANGED`) |
| Inventory sync | `agents.inventory` | Keeps a group in sync with Kubernetes and Google Cloud agent inventories ([ADR-0028](0028-agent-inventory-sync.md)) |
| Prompts | `agents.services` | Rendering with variables, defaults and validation errors |
| Registry MCP server | `mcp/` module | Tools for AI assistants over the core API, including ARD search ([ADR-0023](0023-mcp-server.md)) |

## Configuration

Feature flags are in Decision 3. Other properties:

| Property | Default | Meaning |
|---|---|---|
| `apicurio.features.experimental.enabled` | `false` | Required by every agent feature flag |
| `apicurio.a2a.default-visibility` | `entitled` | Visibility of agent artifacts without the label |
| `apicurio.a2a.agent.*` | | The registry's own Agent Card |
| `apicurio.mcp-import.allowed-hosts` | none (refuse all) | Hosts, wildcards, IPs or CIDR ranges import may connect to |
| `apicurio.mcp-import.insecure-http.enabled`, `loopback.enabled` | `false` | Development escapes; the allow-list still applies |
| `apicurio.mcp-import.timeout.seconds`, `response.max-size`, `tools.max-size` | 10, 1 MiB, 500 | Bounds on an import |
| `apicurio.mcp-registry.max-page-size` | 100 | MCP Registry API paging |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Boundary | [`extensions`](../app/src/main/java/io/apicurio/registry/extensions/), [`AgentsBoundaryTest`](../app/src/test/java/io/apicurio/registry/architecture/AgentsBoundaryTest.java) | Extension points; the build-time check |
| Agent code | [`agents`](../app/src/main/java/io/apicurio/registry/agents/) | All agent features |
| Artifact types | [`schema-util/agents`](../schema-util/agents/src/main/java/io/apicurio/registry/agents/) | The five types |
| Outbound policy | [`OutboundAddressPolicy`](../app/src/main/java/io/apicurio/registry/http/OutboundAddressPolicy.java), [`McpImportTargetPolicy`](../app/src/main/java/io/apicurio/registry/agents/mcpimport/McpImportTargetPolicy.java) | SSRF protection |
| Label rules | [`.claude/rules/labels.md`](../.claude/rules/labels.md) | Reserved namespaces, `FeatureLabelOwner` |

## Limitations and open work

1. Every feature is experimental; endpoints may change incompatibly between minor releases.
2. MCP import checks the resolved addresses, but the HTTP client resolves the host again when it
   connects, so DNS rebinding is not caught. A CIDR entry constrains only the first resolution, so
   it does not help either: allow-list hostnames whose DNS you control, or IP literals.
3. The MCP Registry API keeps a legacy unprefixed label (`mcp-server-version-id`) for data written
   before the namespace rule.
4. ARD search has no relevance ranking, and Trust Manifests and catalog import are deferred
   ([ADR-0004](0004-ai-catalog-ard-convergence.md)).
5. Visibility is a discovery filter only; use authorization to protect content.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| A separate agent registry service | Not selected: duplicated governance, no references to schemas and APIs |
| A separate Maven module for agent code | Not selected: most of core would need to become public API; the ArchUnit boundary gives the guarantee |
| Agent types as configured (script or webhook) types | Replaced: they started as script types and became native Java in #7896, so script providers were removed (#7910, [ADR-0013](0013-custom-artifact-types.md)) |
| Proxying or invoking agents | Not selected: the registry is a metadata store; runtime belongs to agent frameworks |
| Visibility as access control | Not selected: labels are user-editable; access control stays in authorization |

## Consequences

- **Benefits:** agents, tools and prompts get versions, references, rules and access control;
  standard discovery protocols; core is unaffected when the features are off, and is protected from
  agent code by the build.
- **Costs:** many feature flags; experimental endpoints to keep in step with fast-moving standards;
  every new core interaction needs an extension point.

## Example and verification

- **Example:** [`examples/agent-registry`](../examples/agent-registry/): `./demo.sh` creates every
  agent artifact type with references, checks compatibility rules, prompt rendering, Agent Cards
  generated from OpenAPI, the discovery endpoints (A2A, MCP tools, AI Catalog, ARD), the MCP Registry
  API, importing tools from a live MCP server and the import policy, Quarkus LangChain4j agents
  discovering each other through the registry, visibility per user, and the registry MCP server. CI
  runs it.
- **User documentation:**
  [`assembly-agent-registry-quickstart.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-agent-registry-quickstart.adoc)
  and the A2A, MCP tool, MCP Registry, AI Catalog and ARD, and MCP server chapters next to it.

| Concern | Tests to read |
|---|---|
| The boundary | `AgentsBoundaryTest` (app) |
| Import policy | `McpImportTargetPolicyTest` (app) |
| Discovery | `WellKnownResourceTest`, `WellKnownMcpToolsTest`, `WellKnownAiCatalogTest`, `WellKnownArdTest`, `AiCatalogFeatureGateTest` (app) |
| Visibility | `AgentVisibilityFilterTest`, `A2A*VisibilityTest` (app) |

## History

| Milestone | Result |
|---|---|
| 3.x ([#6991](https://github.com/Apicurio/apicurio-registry/issues/6991)) | A2A, MCP tools, MCP Registry, AI Catalog and ARD, prompts, MCP import |
| [#7896](https://github.com/Apicurio/apicurio-registry/pull/7896), [#7910](https://github.com/Apicurio/apicurio-registry/pull/7910) | Prompt and model types made native; script providers removed |
| This ADR | First umbrella record; ADR-0004 accepted |
