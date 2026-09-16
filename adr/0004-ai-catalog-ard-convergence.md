# ADR-0004: AI Catalog & ARD Convergence

**Date:** 2026-08-22 (updated 2026-09-02 — see [Amendment](#amendment-2026-09-02-ard-v091))  
**Status:** Proposed  
**Issue:** [#6991 — AI Agent Registry (MCP & A2A Support)](https://github.com/Apicurio/apicurio-registry/issues/6991)

## Context

Two Linux Foundation discovery standards have emerged for the agentic AI ecosystem:

- **[AI Catalog](https://ai-catalog.io/)** — from the LF Agent Card Working Group (a joint
  A2A + MCP effort). A static, artifact-agnostic JSON envelope served at
  `/.well-known/ai-catalog.json`. A catalog has `specVersion`, optional `host`, and
  `entries[]`. Each entry has an `identifier` (recommended as
  `urn:air:<publisher>:<namespace>:<name>`), a `type` (IANA media type such as
  `application/a2a-agent-card+json` or `application/mcp-server-card+json`), exactly one of
  `url` (reference) or `data` (inline), and optional `displayName`, `description`, `tags[]`,
  `version`, `updatedAt`, `publisher`, and `trustManifest`. It is intentionally agnostic
  about the artifacts it indexes and defines an optional Trust Manifest layer (identity,
  attestations, provenance, JWS signatures).

- **[ARD — Agentic Resource Discovery](https://agenticresourcediscovery.org/)** — from an LF
  working group (Microsoft, Google, Hugging Face, GoDaddy, Cisco, Databricks, GitHub,
  Nvidia, Salesforce, ServiceNow, Snowflake). Built on AI Catalog, it adds the `urn:air:`
  naming scheme (mandatory for federation), discovery fields on entries (`capabilities[]`,
  `representativeQueries[]`), a mandatory REST API (`POST /search` required; `POST /explore`
  and `GET /agents` optional), federation modes (`auto`/`referrals`/`none`), and a
  conformance test CLI. ARD registries populate their indexes by crawling
  `ai-catalog.json` manifests (web ingestion is required).

Apicurio Registry already implements much of the underlying capability (A2A v1.0
`AGENT_CARD` artifacts, `MCP_TOOL` artifacts, well-known endpoints, visibility tiers,
structured content extraction). Converging with these two standards positions Apicurio as
an early reference implementation and gives the project a voice in governance that is
still forming.

This ADR stays within the established project boundary: Apicurio is a **metadata store and
governance tool, not a runtime platform**.

## Where Apicurio already overlaps

| LF concept | Apicurio today |
|---|---|
| A2A agent cards | `AGENT_CARD` artifact type, A2A v1.0 aligned |
| MCP server cards | `MCP_TOOL` artifact type |
| Well-known endpoints | `/.well-known/agent.json`, `/a2a`, `/agents`, `/agents/search` |
| Search with filters | `AgentSearchRequest` (skills, capabilities, modes, labels) |
| Visibility tiers | `public` / `entitled` / `private` labels |
| Content extraction for indexing | `AgentCardStructuredContentExtractor` |
| MCP server module | `mcp/` (discovery tools over the well-known endpoints) |

## Gaps to close

1. **No `/.well-known/ai-catalog.json`** — Apicurio cannot be crawled by ARD clients.
2. **Identifier format mismatch** — ARD mandates `urn:air:…`; Apicurio keys artifacts by
   `groupId/artifactId`.
3. **No ARD `POST /search`** — current search is A2A-shaped, not ARD-shaped
   (`{query:{text,filter}}` → ranked entries with `score`).
4. **No Trust Manifest support** — optional in the spec; deferrable.
5. **Federation** — the epic already decided "no federation"; ARD allows
   `federation: none`, so this is compatible.
6. **Ingestion (reverse direction)** — crawling external catalogs to register entries as
   artifacts; new scope, separate discussion.
7. **No self-describing registry entry** — a crawler that only ingests
   `/.well-known/ai-catalog.json` has no way to learn that this registry also exposes a live
   `POST /search` API unless the catalog itself carries an entry of type
   `application/ai-registry+json` pointing at that endpoint (ARD §5.3). **Fixed** in the
   implementation (see Amendment below).
8. **No `representativeQueries` support** — ARD's own publishing guide and every published
   reference implementation (Hugging Face, Ora) treat this field — 2–5 sample natural-language
   queries per entry — as the primary discovery signal for semantic search. Neither the
   `AiCatalogEntry` schema nor the entry-building code populates it. Not fixed yet; tracked as
   a gap below.
9. **Wrong well-known path per ARD v0.91** — see Amendment.

## Amendment (2026-09-02): ARD v0.91

The ARD specification published v0.91 on 2026-08-26, four days after this ADR was first
written, and it changes two things this ADR did not anticipate:

- **The normative publishing/consumption path moved from `/.well-known/ai-catalog.json` to
  `/.well-known/ard.json`.** Per the spec: *"A consumer resolving a domain's entries **MUST**
  fetch `/.well-known/ard.json`... **MAY** additionally consult [the predecessor
  `ai-catalog.json`]."* A strictly-conformant ARD crawler that only fetches `ard.json` will
  never discover a registry that serves only the predecessor path. This is a known,
  documented gap shared with at least one other reference implementation (MCP Gateway
  Registry, per its listing on `agenticresourcediscovery.org/ref_implementations/`), but it
  is still a gap: Step 1 below is revised to serve **both** paths, with `ard.json` as the
  canonical target and `ai-catalog.json` retained as a courtesy to AI-Catalog-only consumers.
- **The entry schema is now framed as JSON-LD** with an optional `@context` and namespaced
  terms. This does not invalidate a plain (non-`@context`) entry — the spec states a
  `@context`-less entry "is interpreted by any consumer that applies the base context, and
  needs no changes" — so no immediate code change is required here, but future filter/facet
  work should be aware that filter keys are resolved as IRIs under the ARD base context, not
  matched as literal strings.

These findings, plus the self-describing-entry fix and the `representativeQueries` gap
above, came out of a review against two live community pages:
[ai-catalog.io](https://ai-catalog.io/) and
[agenticresourcediscovery.org/ref_implementations](https://agenticresourcediscovery.org/ref_implementations/).
Tracked as follow-up work in #6991 (see child issues to be filed).

## Decision

Implement convergence in two increments, followed by optional later work.

### Step 1 — Publishing / conformance (low risk)
Add `GET /.well-known/ai-catalog.json` **and** `GET /.well-known/ard.json` (same payload;
see Amendment above), projecting existing `AGENT_CARD` and `MCP_TOOL` artifacts into the AI
Catalog / ARD entry envelope.

- Pure read-only projection over existing storage; no schema migration.
- Identifier mapping: `urn:air:<configured-publisher-domain>:<groupId>:<artifactId>`.
- `type`: `application/a2a-agent-card+json` / `application/mcp-server-card+json`.
- `url`: points to the existing `/.well-known/agents/{groupId}/{artifactId}` content
  endpoints.
- Respects the existing visibility labels (`public`/`entitled`/`private`).
- Includes a self-describing `application/ai-registry+json` entry pointing at
  `/.well-known/ard/search` whenever `apicurio.ard.enabled=true`, so a crawler that only
  ingests the static manifest can still discover the live search API (gap 7, fixed).
- Result: Apicurio becomes crawlable and can be listed on the ai-catalog.io
  "Implementations" page.

### Step 2 — ARD API (medium)
Implement the ARD REST surface per its OpenAPI spec, exposed under `/.well-known/ard/*`
(and mirrored under `/apis/registry/v3/well-known/ard/*`):

- `POST /ard/search` — **required** for conformance. Backed by the existing filter search:
  a mandatory `query.text` partial-name match plus an optional structured `query.filter`
  (`type`, `tags`, `capabilities`, `publisher`), combined with boolean AND/OR semantics
  (OR within a filter key, AND across keys and the text query). Because only entries that
  satisfy every requested criterion are returned at all, every result's `score` is a
  deterministic `100` in this increment — there is no fuzzy/semantic ranking yet. The
  planned PostgreSQL full-text work (#7230) is expected to enable a real relevance score in
  a later increment. Only `federation: none` semantics are implemented; other values are
  accepted (for forward compatibility) but do not change behavior.
- `GET /ard/agents` — optional; deterministic browsing for developer portals, with a
  simple `filter=key=value[ AND key=value]*` expression (currently `type` is validated
  against the media types this registry emits) plus `pageSize`/`pageToken` pagination.
- `POST /ard/explore` — optional; facet aggregation over `type` and `publisher`, optionally
  narrowed by the same `query` used by `/ard/search`.

### Step 3 — Optional / later (revised)
- ~~MCP server tool wrapping ARD search~~ — **re-prioritized, no longer "optional/later."**
  Every reviewed reference implementation (Hugging Face Discover Tool, GitHub Agent Finder,
  Ora Directory) exposes its ARD search as an MCP tool as a first-class feature, not an
  afterthought. Apicurio already has an `mcp/` module; wrapping `POST /ard/search` there is
  low-effort relative to its value for "looking like a real reference implementation." Moved
  into Step 2 scope; tracked as a new child issue (see #6991 update).
- Trust Manifest passthrough via artifact labels/metadata. Still deferred — optional in the
  spec and not required for the initial listing goal.
- Catalog importer (crawl external `ai-catalog.json`/`ard.json` and register entries). Still
  new scope, separate discussion.

### Step 4 — `representativeQueries` (new)
Add an optional `representativeQueries` (2–5 strings) field to the `AiCatalogEntry` schema
and populate it for `AGENT_CARD` entries from the Agent Card's `skills[].examples` (A2A
already carries example utterances per skill) and, where absent, a generated fallback from
`displayName`/`description`. Not populated for `MCP_TOOL` entries in the first pass (no
equivalent source field exists yet in the MCP Tool artifact schema — separate follow-up).
This directly improves both conformance-tool scoring (§D.2 flags a missing/undersized
`representativeQueries` as a warning) and real semantic-search relevance in any ARD registry
that crawls Apicurio's catalog.

### Step 5 — Get listed (new)
The two target listing pages have different, concrete requirements neither previously had a
tracked task:
- **`ai-catalog.io/implementations/`** — low friction. "Community Projects" are added by
  opening a PR against `Agent-Card/ai-catalog`'s docs. No live endpoint is required for this
  list (it currently includes SDKs/libraries alongside one live testbed service).
- **`agenticresourcediscovery.org/ref_implementations/`** — every entry on this page
  (Hugging Face, GitHub, Cisco, Ora, ANS Finder, MCP Gateway Registry) is a live,
  HTTPS-reachable endpoint, and at least one (ANS Finder) documents having run and passed the
  official `ard-spec/conformance/bin/conformance-test registry <url>` CLI. Getting listed
  here requires: (a) a publicly reachable demo/staging Apicurio instance with
  `apicurio.ai-catalog.enabled`/`apicurio.ard.enabled` turned on (the feature currently
  defaults to disabled Developer Preview, with no public instance), and (b) actually running
  the conformance CLI against it and recording the result. Both are new, previously-untracked
  work.

### Configuration
New properties (all following `.claude/rules/config-properties.md`: `apicurio.*` prefix,
`@Info` in the `app` module; config docs regenerated via the config-generator module):

- `apicurio.ai-catalog.enabled` (feature gate, default `false`, experimental)
- `apicurio.ai-catalog.publisher-domain` (the `<publisher>` for `urn:air:`; when unset, derived
  from the incoming request's host and port)
- `apicurio.ai-catalog.host-name` (default `Apicurio Registry`; the catalog `host.displayName`)
- `apicurio.ai-catalog.spec-version` (default `1.0`; the catalog `specVersion`)
- `apicurio.ard.enabled` (feature gate for the ARD search API)
- `apicurio.ard.federation.default` (`none` initially)

## Community engagement plan

These are LF (not CNCF) efforts. Two distinct venues:

**AI Catalog — LF Agent Card Working Group** (`Agent-Card/ai-catalog`)
- Discord: `discord.gg/wh4XQQG4tt` (day-to-day).
- Open TSC community meetings (LF Zoom); agendas/notes in a public Google Doc.
- Normative changes: open an issue first; TSC seats currently held by Google, Microsoft,
  Anthropic, PulseMCP.
- Governance is explicitly **temporary** and will move to a permanent model later — early
  active implementers are well positioned for the steady-state TSC.

**ARD** (`ards-project/ard-spec`)
- Normative spec changes: open an issue to discuss first; a maintainer lands it.
- Non-normative (examples, conformance tooling, reference implementations, docs): PRs
  welcome directly.
- The conformance test CLI is the lowest-friction contribution entry point.

### Recommended tactical path
1. Join Discord + watch both repos; introduce yourself as working on Apicurio's A2A/MCP
   agent registry.
2. Attend one AI Catalog TSC community meeting (open, no invite needed).
3. File 1–2 well-scoped issues from real implementation experience:
   - **URN ↔ registry identity mapping** (existing registries key artifacts differently
     from `urn:air:`),
   - **registry-backed vs. static-manifest catalog behavior** (how
     `/.well-known/ai-catalog.json` should behave behind a permissioned,
     visibility-tiered registry).
4. Once Apicurio support lands, contribute Apicurio as a listed implementation and as an
   ARD conformance target.

## Success criteria

- `GET /.well-known/ai-catalog.json` and `GET /.well-known/ard.json` return a spec-valid
  catalog (passes the AI Catalog JSON Schema / ARD conformance checks) for the registered
  agents and tools.
- The catalog includes a self-describing `application/ai-registry+json` entry when ARD is
  enabled (done).
- Agent Card entries carry `representativeQueries`.
- ARD `POST /search` passes the official ARD conformance CLI in registry mode, run against a
  live, publicly reachable demo instance (not just locally).
- Apicurio listed on the ai-catalog.io implementations page (Community Projects).
- Apicurio listed on the agenticresourcediscovery.org reference-implementations page.
- Active participation in at least one working-group venue (Discord + one meeting or one
  accepted issue/PR).

## References

- Epic: https://github.com/Apicurio/apicurio-registry/issues/6991
- AI Catalog: https://ai-catalog.io/ · https://github.com/Agent-Card/ai-catalog
- AI Catalog implementations: https://ai-catalog.io/implementations/
- ARD: https://agenticresourcediscovery.org/ · https://github.com/ards-project/ard-spec
- ARD reference implementations: https://agenticresourcediscovery.org/ref_implementations/
- ARD "how to publish" guide: https://agenticresourcediscovery.org/how_to_publish/
- A2A Protocol v1.0: https://a2a-protocol.org/latest/specification/
- A2A community Agent Registry proposal: https://github.com/a2aproject/A2A/discussions/741
- Related open issues: #7230 (full-text search), #8058 (SQL structured search),
  #8059 (per-resource authz)
