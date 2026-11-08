# ADR-0004: AI Catalog & ARD Convergence

- **Status:** Proposed.
- **Decision date:** 2026-08-22; amended 2026-09-02 for ARD v0.91 (see [History](#history)).
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#6991 — AI Agent Registry (MCP & A2A Support)](https://github.com/Apicurio/apicurio-registry/issues/6991).
- **Scope:** Publishing Apicurio's agent and MCP metadata through the AI Catalog and ARD discovery
  standards, and the ARD search API. Apicurio stays a **metadata store and governance tool, not a
  runtime platform**. Federation, ingestion of external catalogs and Trust Manifests are out of
  scope for now (see [Decision](#decision)).

## Context

Two Linux Foundation discovery standards have emerged for the agentic AI ecosystem:

- **[AI Catalog](https://ai-catalog.io/)**, from the LF Agent Card Working Group (a joint A2A + MCP
  effort). A static, artifact-agnostic JSON envelope served at `/.well-known/ai-catalog.json`. A
  catalog has `specVersion`, optional `host`, and `entries[]`. Each entry has an `identifier`
  (recommended as `urn:air:<publisher>:<namespace>:<name>`), a `type` (IANA media type such as
  `application/a2a-agent-card+json` or `application/mcp-server-card+json`), exactly one of `url`
  (reference) or `data` (inline), and optional `displayName`, `description`, `tags[]`, `version`,
  `updatedAt`, `publisher`, and `trustManifest`. It is intentionally agnostic about the artifacts it
  indexes and defines an optional Trust Manifest layer (identity, attestations, provenance, JWS
  signatures).
- **[ARD — Agentic Resource Discovery](https://agenticresourcediscovery.org/)**, from an LF working
  group (Microsoft, Google, Hugging Face, GoDaddy, Cisco, Databricks, GitHub, Nvidia, Salesforce,
  ServiceNow, Snowflake). Built on AI Catalog, it adds the `urn:air:` naming scheme (mandatory for
  federation), discovery fields on entries (`capabilities[]`, `representativeQueries[]`), a
  mandatory REST API (`POST /search` required; `POST /explore` and `GET /agents` optional),
  federation modes (`auto`/`referrals`/`none`), and a conformance test CLI. ARD registries populate
  their indexes by crawling `ai-catalog.json` manifests (web ingestion is required).

Apicurio Registry already implements much of the underlying capability (A2A v1.0 `AGENT_CARD`
artifacts, `MCP_TOOL` artifacts, well-known endpoints, visibility tiers, structured content
extraction). Converging with these two standards lets standard discovery clients find agents and
tools registered in Apicurio without Apicurio-specific integration.

### Where Apicurio already overlaps

| LF concept | Apicurio today |
|---|---|
| A2A agent cards | `AGENT_CARD` artifact type, A2A v1.0 aligned |
| MCP server cards | `MCP_TOOL` artifact type |
| Well-known endpoints | `/.well-known/agent.json`, `/a2a`, `/agents`, `/agents/search` |
| Search with filters | `AgentSearchRequest` (skills, capabilities, modes, labels) |
| Visibility tiers | `public` / `entitled` / `private` labels |
| Content extraction for indexing | `AgentCardStructuredContentExtractor` |
| MCP server module | `mcp/` (discovery tools over the well-known endpoints) |

### Gaps to close

1. **No `/.well-known/ai-catalog.json`**: Apicurio cannot be crawled by ARD clients.
2. **Identifier format mismatch**: ARD mandates `urn:air:…`; Apicurio keys artifacts by
   `groupId/artifactId`.
3. **No ARD `POST /search`**: current search is A2A-shaped, not ARD-shaped
   (`{query:{text,filter}}` → ranked entries with `score`).
4. **No Trust Manifest support**: optional in the spec; deferrable.
5. **Federation**: the epic already decided "no federation"; ARD allows `federation: none`, so this
   is compatible.
6. **Ingestion (reverse direction)**: crawling external catalogs to register entries as artifacts;
   new scope, separate discussion.
7. **No self-describing registry entry**: a crawler that only ingests `/.well-known/ai-catalog.json`
   has no way to learn that this registry also exposes a live `POST /search` API unless the catalog
   itself carries an entry of type `application/ai-registry+json` pointing at that endpoint
   (ARD §5.3). **Fixed** in the implementation (Step 1).
8. **No `representativeQueries` support**: ARD's own publishing guide and every published reference
   implementation (Hugging Face, Ora) treat this field (2–5 sample natural-language queries per
   entry) as the primary discovery signal for semantic search. Neither the `AiCatalogEntry` schema
   nor the entry-building code populates it. Not fixed yet (Step 4).
9. **Wrong well-known path per ARD v0.91**: see [ARD v0.91 changes](#ard-v091-changes).

## Decision

Implement convergence in two increments, followed by optional later work.

1. **Step 1 — Publishing / conformance (low risk).** Serve the catalog at both
   `/.well-known/ard.json` and `/.well-known/ai-catalog.json`.
2. **Step 2 — ARD API (medium).** Implement `POST /ard/search` (required), `GET /ard/agents` and
   `POST /ard/explore` (optional), plus an MCP tool wrapping ARD search.
3. **Step 3 — Deferred.** Trust Manifest passthrough and a catalog importer.
4. **Step 4 — `representativeQueries`.** Populate it for `AGENT_CARD` entries.

### Step 1 — Publishing / conformance

Add `GET /.well-known/ai-catalog.json` **and** `GET /.well-known/ard.json` (same payload; see
[ARD v0.91 changes](#ard-v091-changes)), projecting existing `AGENT_CARD` and `MCP_TOOL` artifacts
into the AI Catalog / ARD entry envelope.

- Pure read-only projection over existing storage; no schema migration.
- Identifier mapping: `urn:air:<configured-publisher-domain>:<groupId>:<artifactId>`.
- `type`: `application/a2a-agent-card+json` / `application/mcp-server-card+json`.
- `url`: points to the existing `/.well-known/agents/{groupId}/{artifactId}` content endpoints.
- Respects the existing visibility labels (`public`/`entitled`/`private`).
- Includes a self-describing `application/ai-registry+json` entry pointing at
  `/.well-known/ard/search` whenever `apicurio.ard.enabled=true`, so a crawler that only ingests the
  static manifest can still discover the live search API (gap 7, fixed).
- Result: Apicurio becomes crawlable by AI Catalog and ARD clients.

### Step 2 — ARD API

Implement the ARD REST surface per its OpenAPI spec, exposed under `/.well-known/ard/*` (and
mirrored under `/apis/registry/v3/well-known/ard/*`):

- `POST /ard/search`: **required** for conformance. Backed by the existing filter search: a
  mandatory `query.text` partial-name match plus an optional structured `query.filter` (`type`,
  `tags`, `capabilities`, `publisher`), combined with boolean AND/OR semantics (OR within a filter
  key, AND across keys and the text query). Because only entries that satisfy every requested
  criterion are returned at all, every result's `score` is a deterministic `100` in this increment;
  there is no fuzzy/semantic ranking yet. The planned PostgreSQL full-text work (#7230) is expected
  to enable a real relevance score in a later increment. Only `federation: none` semantics are
  implemented; other values are accepted (for forward compatibility) but do not change behavior.
- `GET /ard/agents`: optional; deterministic browsing for developer portals, with a simple
  `filter=key=value[ AND key=value]*` expression (currently `type` is validated against the media
  types this registry emits) plus `pageSize`/`pageToken` pagination.
- `POST /ard/explore`: optional; facet aggregation over `type` and `publisher`, optionally narrowed
  by the same `query` used by `/ard/search`.
- **MCP tool wrapping ARD search**, moved here from Step 3. Every reviewed reference implementation
  (Hugging Face Discover Tool, GitHub Agent Finder, Ora Directory) exposes its ARD search as an MCP
  tool as a first-class feature, not an afterthought. Apicurio already has an `mcp/` module, so
  wrapping `POST /ard/search` there is low effort. Tracked as a child issue of #6991.

### Step 3 — Deferred

- Trust Manifest passthrough via artifact labels/metadata. Optional in the spec.
- Catalog importer (crawl external `ai-catalog.json`/`ard.json` and register entries). New scope,
  separate discussion.

### Step 4 — `representativeQueries`

Add an optional `representativeQueries` (2–5 strings) field to the `AiCatalogEntry` schema and
populate it for `AGENT_CARD` entries from the Agent Card's `skills[].examples` (A2A already carries
example utterances per skill) and, where absent, a generated fallback from
`displayName`/`description`. Not populated for `MCP_TOOL` entries in the first pass (no equivalent
source field exists yet in the MCP Tool artifact schema; separate follow-up). This directly improves
both conformance-tool scoring (§D.2 flags a missing/undersized `representativeQueries` as a warning)
and real semantic-search relevance in any ARD registry that crawls Apicurio's catalog.

### ARD v0.91 changes

The ARD specification published v0.91 on 2026-08-26, four days after this ADR was first written,
and it changes two things this ADR did not anticipate:

- **The normative publishing/consumption path moved from `/.well-known/ai-catalog.json` to
  `/.well-known/ard.json`.** Per the spec: *"A consumer resolving a domain's entries **MUST** fetch
  `/.well-known/ard.json`... **MAY** additionally consult [the predecessor `ai-catalog.json`]."*
  A strictly-conformant ARD crawler that only fetches `ard.json` will never discover a registry that
  serves only the predecessor path. This is a known, documented gap shared with at least one other
  reference implementation (MCP Gateway Registry, per its listing on
  `agenticresourcediscovery.org/ref_implementations/`). Step 1 therefore serves **both** paths, with
  `ard.json` as the canonical target and `ai-catalog.json` retained as a courtesy to
  AI-Catalog-only consumers.
- **The entry schema is now framed as JSON-LD** with an optional `@context` and namespaced terms.
  This does not invalidate a plain (non-`@context`) entry: the spec states a `@context`-less entry
  "is interpreted by any consumer that applies the base context, and needs no changes". No
  immediate code change is required, but future filter/facet work should be aware that filter keys
  are resolved as IRIs under the ARD base context, not matched as literal strings.

These findings, plus the self-describing-entry fix and the `representativeQueries` gap, came out of
a review of the published specifications and reference implementations.

### Behaviour across storage variants

The catalog and the ARD API are read-only projections over artifacts already in storage, so they
behave the same on SQL, KafkaSQL, GitOps and KubernetesOps. Search uses the existing structured
content search (see #8058 for SQL structured search).

## Configuration

All properties follow `.claude/rules/config-properties.md` (`apicurio.*` prefix, `@Info` in the
`app` module).

| Property | Default | Meaning |
|---|---|---|
| `apicurio.ai-catalog.enabled` | `false` | Feature gate (experimental) |
| `apicurio.ai-catalog.publisher-domain` | none | The `<publisher>` in `urn:air:`; when unset, derived from the request's host and port |
| `apicurio.ai-catalog.host-name` | `Apicurio Registry` | The catalog `host.displayName` |
| `apicurio.ai-catalog.spec-version` | `1.0` | The catalog `specVersion` |
| `apicurio.ard.enabled` | `false` | Feature gate for the ARD search API |
| `apicurio.ard.federation.default` | `none` | Federation mode reported by the ARD API |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| Configuration | [`agents/aicatalog`](../app/src/main/java/io/apicurio/registry/agents/aicatalog/), [`agents/ard`](../app/src/main/java/io/apicurio/registry/agents/ard/) | `AiCatalogConfig`, `ArdConfig`, constants |
| Well-known endpoints | [`agents/rest/wellknown`](../app/src/main/java/io/apicurio/registry/agents/rest/wellknown/) | `WellKnownResource`, `AiCatalogDiscovery` |
| Content extraction | [`AgentCardStructuredContentExtractor`](../schema-util/agents/src/main/java/io/apicurio/registry/agents/content/extract/AgentCardStructuredContentExtractor.java) | Indexed fields used by search |

## Limitations and open work

1. `representativeQueries` is not populated (Step 4).
2. ARD search scores are a constant `100`; relevance ranking depends on #7230.
3. Only `federation: none` is implemented; other values are accepted but ignored.
4. No Trust Manifest support and no catalog importer (Step 3).
5. No recorded run of the official ARD conformance CLI.
6. The ADR is still **Proposed**; it must be accepted or superseded by the planned agent registry
   umbrella ADR.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Keep only the A2A-shaped well-known API | Not selected: ARD crawlers and clients could not discover Apicurio. |
| Serve only `/.well-known/ard.json` | Not selected: AI-Catalog-only consumers still fetch `ai-catalog.json`; serving both costs one extra route. |
| Serve only `/.well-known/ai-catalog.json` | Rejected after ARD v0.91: strictly conformant ARD crawlers would never find the registry. |
| Federation (`auto`/`referrals`) | Deferred: the epic decided "no federation", and ARD allows `none`. |
| Ingest external catalogs | Deferred: reverses Apicurio's role from publisher to crawler; separate discussion. |

## Consequences

- **Benefits:** Apicurio becomes crawlable by ARD/AI Catalog clients with no storage change.
- **Costs:** two well-known paths to maintain; an evolving spec (JSON-LD framing, v0.91 path change)
  that can require further amendments.

### Success criteria

- `GET /.well-known/ai-catalog.json` and `GET /.well-known/ard.json` return a spec-valid catalog
  (passes the AI Catalog JSON Schema / ARD conformance checks) for the registered agents and tools.
- The catalog includes a self-describing `application/ai-registry+json` entry when ARD is enabled
  (done).
- Agent Card entries carry `representativeQueries`.
- ARD `POST /search` passes the official ARD conformance CLI in registry mode.

## Example and verification

- **Example:** none yet that exercises the catalog or ARD API;
  [`examples/a2a-real-world-integration`](../examples/a2a-real-world-integration/) covers the
  underlying A2A agent cards. A runnable agent registry example is planned.
- **User documentation:**
  [`assembly-ai-catalog-ard.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-ai-catalog-ard.adoc).

| Concern | Tests to read |
|---|---|
| Feature gates | `AiCatalogFeatureGateTest` |
| Catalog endpoints and entries | `WellKnownAiCatalogTest` |
| ARD API | `WellKnownArdTest` |

```bash
./mvnw test -pl app -Dtest='AiCatalogFeatureGateTest,WellKnownAiCatalogTest,WellKnownArdTest'
```

### References

- Epic: https://github.com/Apicurio/apicurio-registry/issues/6991
- AI Catalog: https://ai-catalog.io/ · https://github.com/Agent-Card/ai-catalog
- ARD: https://agenticresourcediscovery.org/ · https://github.com/ards-project/ard-spec
- ARD "how to publish" guide: https://agenticresourcediscovery.org/how_to_publish/
- A2A Protocol v1.0: https://a2a-protocol.org/latest/specification/
- A2A community Agent Registry proposal: https://github.com/a2aproject/A2A/discussions/741
- Related open issues: #7230 (full-text search), #8058 (SQL structured search), #8059
  (per-resource authz)

## History

| Milestone | Result |
|---|---|
| 2026-08-22 | First version: Steps 1–3 |
| 2026-09-02 amendment | ARD v0.91: serve both well-known paths; JSON-LD framing noted; self-describing entry fixed; MCP search tool moved into Step 2; Step 4 added |
| This ADR reformatted to [the ADR template](TEMPLATE.md) | Structure only; no change to the decisions |
