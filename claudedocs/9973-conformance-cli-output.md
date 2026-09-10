# Issue #9973 — Official ARD Conformance CLI Results

Date: 2026-09-03
Tool: `ards-project/ard-spec` (cloned read-only to `/tmp/ard-spec-conformance-check`,
not added to this repo as a submodule or dependency), `conformance/bin/conformance-test`,
CLI version reported as `v0.9.1`.

```
$ git clone --depth 1 https://github.com/ards-project/ard-spec /tmp/ard-spec-conformance-check
$ chmod +x /tmp/ard-spec-conformance-check/conformance/bin/conformance-test
$ pip3 install jsonschema   # optional, enables JSON Schema Draft 2020-12 checks
```

## CLI usage (from `--help`)

```
Agentic Resource Discovery Conformance CLI v0.9.1
Usage:
  conformance-test manifest  <local_file_path_or_url>
  conformance-test publisher <domain>   # resolves /.well-known/ard.json per §5.1
  conformance-test registry  <registry_base_url> [--header 'Name: value']...
```

Target: the local Apicurio instance from `9973-local-verification.md`
(`quarkus:dev` on `localhost:8091`, `apicurio.ai-catalog.enabled=true`,
`apicurio.ard.enabled=true`, seeded with one `AGENT_CARD` and one `MCP_TOOL` artifact).

## 1. Manifest Validation Mode — PASS

```bash
curl -s http://localhost:8091/.well-known/ard.json -o /tmp/ard-manifest.json
conformance-test manifest /tmp/ard-manifest.json
```

```
=== Validating Manifest: /tmp/ard-manifest.json ===
  ✓ Manifest parsed successfully as valid JSON.
  ✓ Manifest validates against ArdManifest.
  ✓ All 3 entries validate against ArdEntry.
  • Running custom semantic checks...
  • Top-level members ignored by ARD (transport-defined): host, specVersion.
  • Found 3 entries to validate.

  Entry: Apicurio Registry
  ✓ [Apicurio Registry] Valid URN format. Publisher: 'localhost', Name: 'registry'.
  ✓ [Apicurio Registry] Correct Value-or-Reference delivery format (using url).
  ⚠ [Apicurio Registry] No 'representativeQueries'. The semantic index is built from this term, so the entry will not be found by search — it is a valid catalog entry but not a discoverable ARD entry.

  Entry: CatalogAgent
  ✓ [CatalogAgent] Valid URN format. Publisher: 'localhost', Name: 'catalog-agent'.
  ✓ [CatalogAgent] Correct Value-or-Reference delivery format (using url).

  Entry: Catalog Lookup
  ✓ [Catalog Lookup] Valid URN format. Publisher: 'localhost', Name: 'catalog-lookup-tool'.
  ✓ [Catalog Lookup] Correct Value-or-Reference delivery format (using url).
  ⚠ [Catalog Lookup] No 'representativeQueries'. The semantic index is built from this term, so the entry will not be found by search — it is a valid catalog entry but not a discoverable ARD entry.

=== Conformance Validation Summary ===
CONFORMANCE STATUS: PASS
Validated with 0 critical specification errors and 2 warnings.
```

Exit code: `0`.

**Interpretation:** the `application/ai-registry+json` self-describing entry and the
`MCP_TOOL` entry (`Catalog Lookup`) don't carry `representativeQueries` — expected, since
Step 4 of ADR-0004 explicitly only populates that field for `AGENT_CARD` entries from A2A
skill `examples`, not for the self-describing entry or `MCP_TOOL` entries (documented gap,
tracked as a known follow-up in the ADR, not a regression). These are warnings, not
failures, and don't affect conformance status.

## 2. Registry API Validation Mode — FAIL

```bash
conformance-test registry http://localhost:8091/.well-known/ard
```

```
=== Validating Agent Registry: http://localhost:8091/.well-known/ard ===

  Probing GET /agents...
  ✓ GET /agents responded successfully with 200 OK.
  ✗ GET /agents response is not a valid paginated object. Missing 'items' array.

  Probing POST /search (Mandated Discovery endpoint)...
  ✓ POST /search responded successfully with 200 OK.
  ✓ POST /search returned valid results list (0 items).

  Probing POST /explore (Optional Introspection endpoint)...
  ✓ POST /explore responded successfully with 200 OK.
  ✓ POST /explore returned valid facets object.

=== Conformance Validation Summary ===
CONFORMANCE STATUS: FAIL
Found 1 critical specification errors. Implementation is NOT conformant.
```

Exit code: `1`.

**Root cause of the single failure:** the CLI's registry-mode probe for `GET /agents`
requires a paginated response shaped `{"items": [...], ...}` (per ARD §5.3, the *ARD Agent
Registry API* `GET /agents` browsing-endpoint schema). Apicurio's
`GET /.well-known/ard/agents` instead returns the same envelope shape as the
`ai-catalog.json`/`ard.json` manifest endpoints (`{"specVersion", "host", "entries": [...]}`
— see `AiCatalogEntry`/manifest projection in `WellKnownResourceImpl`). This is a real,
reproducible gap between the implemented `GET /ard/agents` endpoint and the ARD Registry API
`items`-paginated schema the conformance tool expects — **not** a bug introduced or
"fixed" as part of this task. Per the task instructions, this is reported here for a human
to triage (e.g. as a new, separate issue) rather than patched in this PR.

The `POST /search` "0 items" result is expected, not a bug: the CLI's registry probe sends a
fixed mock query (`"weather forecast tools"`) that does not match either seeded artifact's
text content — the endpoint itself behaves correctly (`200`, well-formed empty `results`
array). A separately-run manual query for `"catalog"` (see `9973-local-verification.md`)
confirms the search endpoint does return non-empty, well-scored results when the query
actually matches seeded content.

`POST /explore` fully passes; no notes.

## Summary

| Mode | Result | Notes |
|---|---|---|
| `manifest` | **PASS** (0 errors, 2 warnings) | Warnings are a known, tracked gap (`representativeQueries` not yet populated for the self-describing entry or `MCP_TOOL` entries) |
| `registry` | **FAIL** (1 critical error) | `GET /ard/agents` doesn't return the `items`-paginated shape the CLI's Registry API mode expects; `POST /search` and `POST /explore` both pass |
| `publisher <domain>` | Not run | Requires a publicly resolvable domain on standard ports (`https://<domain>/.well-known/ard.json`); not applicable to a `localhost:8091` dev instance. Should be re-run once a real public demo instance exists. |

**This registry-mode failure must be triaged by a human/separate issue before Apicurio can
be listed as an ARD conformance-passing reference implementation** on
`agenticresourcediscovery.org/ref_implementations/`. No attempt was made to fix it as part
of this task, per the task's explicit scope boundary.
