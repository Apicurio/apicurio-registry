# Issue #9973 — Local Verification: AI Catalog / ARD End-to-End

Date: 2026-09-03
Scope: Sub-goal 1 of #9973 — verify locally that `apicurio.ai-catalog.enabled=true` and
`apicurio.ard.enabled=true` work end-to-end, as evidence prior to any public deployment
decision (which this task explicitly does NOT make).

## Environment

- Branch: `task/get-listed-ai-catalog-ard` (built from latest `origin/main`, which already
  includes the AI Catalog/ARD implementation — see `07f95d419e`, `2fca072751`,
  `e7bf042dde` in `git log`).
- Started via:

  ```bash
  cd app && ../mvnw quarkus:dev -Dlocal \
    -Dapicurio.ai-catalog.enabled=true \
    -Dapicurio.ard.enabled=true \
    -Dquarkus.http.port=8091
  ```

- Storage: default H2 (dev mode), no external infra.
- No public exposure — bound to `localhost:8091` only, for the duration of this
  verification session, then torn down.

## Seed data

Reused the exact `AGENT_CARD` and `MCP_TOOL` sample payloads from the project's own test
suite (`app/src/test/java/io/apicurio/registry/noprofile/rest/aicatalog/WellKnownAiCatalogTest.java`,
`AGENT_CARD_CONTENT` / `MCP_TOOL_CONTENT` constants), rather than fabricating new content,
plus a `representativeQueries`-bearing variant (skill `examples`) to exercise that field.

Created via the standard v3 REST API:

```bash
curl -s -X POST http://localhost:8091/apis/registry/v3/groups/demo-9973/artifacts \
  -H "Content-Type: application/json" \
  -d '{"artifactId":"catalog-agent","artifactType":"AGENT_CARD","firstVersion":{"content":{"content":"<agent-card.json>","contentType":"application/json"}}}'

curl -s -X POST http://localhost:8091/apis/registry/v3/groups/demo-9973/artifacts \
  -H "Content-Type: application/json" \
  -d '{"artifactId":"catalog-lookup-tool","artifactType":"MCP_TOOL","firstVersion":{"content":{"content":"<mcp-tool.json>","contentType":"application/json"}}}'
```

Both returned `200`/creation success with `globalId` 1 and 2 respectively.

Agent card content (`AGENT_CARD` artifact, `catalog-agent`):

```json
{
    "name": "CatalogAgent",
    "description": "An agent listed in the AI catalog",
    "version": "1.2.3",
    "supportedInterfaces": [
        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
    ],
    "capabilities": { "streaming": true },
    "skills": [
        {
            "id": "catalog-skill",
            "name": "Catalog Skill",
            "description": "A skill used for catalog tests",
            "tags": ["catalog"],
            "examples": ["What can this agent do?", "Show me the catalog skill"]
        }
    ],
    "defaultInputModes": ["text"],
    "defaultOutputModes": ["text"]
}
```

MCP tool content (`MCP_TOOL` artifact, `catalog-lookup-tool`):

```json
{
    "name": "catalog_lookup",
    "title": "Catalog Lookup",
    "description": "Look up entries in the product catalog",
    "inputSchema": {
        "type": "object",
        "properties": { "query": { "type": "string" } },
        "required": ["query"]
    }
}
```

## `GET /.well-known/ai-catalog.json`

```bash
curl -s http://localhost:8091/.well-known/ai-catalog.json | python3 -m json.tool
```

```json
{
    "specVersion": "1.0",
    "host": {
        "displayName": "Apicurio Registry",
        "identifier": "localhost:8091"
    },
    "entries": [
        {
            "identifier": "urn:air:localhost:8091:system:registry",
            "displayName": "Apicurio Registry",
            "type": "application/ai-registry+json",
            "url": "http://localhost:8091/.well-known/ard/search",
            "description": "ARD search API for this registry."
        },
        {
            "identifier": "urn:air:localhost:8091:demo-9973:catalog-agent",
            "displayName": "CatalogAgent",
            "type": "application/a2a-agent-card+json",
            "url": "http://localhost:8091/.well-known/agents/demo-9973/catalog-agent",
            "capabilities": ["catalog-skill"],
            "version": "1.2.3",
            "updatedAt": "2026-09-03T19:17:15.252Z",
            "representativeQueries": [
                "What can this agent do?",
                "Show me the catalog skill"
            ]
        },
        {
            "identifier": "urn:air:localhost:8091:demo-9973:catalog-lookup-tool",
            "displayName": "Catalog Lookup",
            "type": "application/mcp-server-card+json",
            "url": "http://localhost:8091/.well-known/mcp-tools/demo-9973/catalog-lookup-tool",
            "capabilities": [],
            "updatedAt": "2026-09-03T19:17:15.350Z"
        }
    ]
}
```

Confirms: `specVersion`, `host`, the self-describing `application/ai-registry+json` entry
(ADR-0004 gap 7 fix), `urn:air:` identifiers, `representativeQueries` populated from A2A
skill `examples` (ADR-0004 Step 4), and correct IANA media types for both artifact types.

## `GET /.well-known/ard.json`

Byte-identical to `ai-catalog.json` above (confirmed via diff on the raw bodies), matching
the ADR-0004 Amendment decision to serve both paths with the same payload, `ard.json` as
canonical.

## `POST /.well-known/ard/search`

Text query matching seeded content:

```bash
curl -s -X POST http://localhost:8091/.well-known/ard/search \
  -H "Content-Type: application/json" \
  -d '{"query": {"text": "catalog"}, "pageSize": 2}' | python3 -m json.tool
```

```json
{
    "results": [
        {
            "identifier": "urn:air:localhost:8091:demo-9973:catalog-agent",
            "displayName": "CatalogAgent",
            "type": "application/a2a-agent-card+json",
            "url": "http://localhost:8091/.well-known/agents/demo-9973/catalog-agent",
            "capabilities": ["catalog-skill"],
            "version": "1.2.3",
            "updatedAt": "2026-09-03T19:17:15.252Z",
            "score": 100,
            "source": "http://localhost:8091"
        },
        {
            "identifier": "urn:air:localhost:8091:demo-9973:catalog-lookup-tool",
            "displayName": "Catalog Lookup",
            "type": "application/mcp-server-card+json",
            "url": "http://localhost:8091/.well-known/mcp-tools/demo-9973/catalog-lookup-tool",
            "capabilities": [],
            "updatedAt": "2026-09-03T19:17:15.350Z",
            "score": 100,
            "source": "http://localhost:8091"
        }
    ]
}
```

Filtered query (`type` facet + text) correctly narrowed to one result:

```bash
curl -s -X POST http://localhost:8091/.well-known/ard/search \
  -H "Content-Type: application/json" \
  -d '{"query": {"text": "catalog", "filter": {"type": ["application/a2a-agent-card+json"]}}}'
```
→ returned only the `catalog-agent` entry.

A query with empty `query.text` and no match (`"query": {"text": "weather forecast tools"}`)
correctly returned `{"results": []}` (0 items, not an error) — this is what the official
conformance CLI also observed (see `9973-conformance-cli-output.md`), and is expected
behavior, not a bug, since neither seeded artifact's text matches that query.

## `GET /.well-known/ard/agents`

```bash
curl -s http://localhost:8091/.well-known/ard/agents | python3 -m json.tool
```

Returns the same `ai-catalog.json`-shaped envelope (`entries[]`), listing both seeded
artifacts. **Note:** this is the same shape as the manifest endpoints, not a
`{"items": [...]}` paginated-list shape. The official ARD conformance CLI expects `items`
here — see the conformance run write-up for details; this is a real, reportable gap, not
something fixed in this task.

## `POST /.well-known/ard/explore`

```bash
curl -s -X POST http://localhost:8091/.well-known/ard/explore \
  -H "Content-Type: application/json" \
  -d '{"resultType": {"facets": [{"field": "type"}, {"field": "publisher"}]}}' | python3 -m json.tool
```

```json
{
    "resultType": "facets",
    "facets": {
        "type": {
            "buckets": [
                { "value": "application/a2a-agent-card+json", "count": 1 },
                { "value": "application/mcp-server-card+json", "count": 1 }
            ],
            "otherCount": 0
        },
        "publisher": {
            "buckets": [
                { "value": "localhost", "count": 2 }
            ],
            "otherCount": 0
        }
    }
}
```

Facet aggregation over `type` and `publisher` works correctly.

## Conclusion

All four endpoints (`ai-catalog.json`, `ard.json`, `ard/search`, `ard/explore`) work
end-to-end against a local instance seeded with realistic `AGENT_CARD`/`MCP_TOOL` content,
confirming the feature functions as designed when both feature-gate properties are enabled.
`GET /ard/agents` also works but returns a different envelope shape than the official
conformance CLI expects (see `9973-conformance-cli-output.md`).

This is local-only evidence. It does **not** substitute for a real public HTTPS instance,
which is required for the `agenticresourcediscovery.org/ref_implementations/` listing bar
and is a human/infrastructure decision out of scope for this task (see PR/issue summary for
what a human needs to decide: hosting provider, TLS, domain, ongoing operational ownership).
