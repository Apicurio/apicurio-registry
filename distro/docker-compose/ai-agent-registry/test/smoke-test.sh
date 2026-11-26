#!/usr/bin/env bash
# Smoke test for the AI Agent Registry quickstart (distro/docker-compose/ai-agent-registry).
# Runs the commands from docs/.../assembly-agent-registry-quickstart.adoc against a running stack,
# and fails on the first result that differs from what the page says.
#
#   REGISTRY_URL      default http://localhost:8080
#   UI_URL            default http://localhost:8888
#   MCP_SERVER_IMAGE  default quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot
#   COMPOSE_NETWORK   default ai-agent-registry_default
set -euo pipefail

REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080}"
UI_URL="${UI_URL:-http://localhost:8888}"
MCP_SERVER_IMAGE="${MCP_SERVER_IMAGE:-quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot}"
COMPOSE_NETWORK="${COMPOSE_NETWORK:-ai-agent-registry_default}"
HERE="$(cd "$(dirname "$0")" && pwd)"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "ok:   $*"; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

# 1. The seeded catalog.
count=$(curl -fsS "$REGISTRY_URL/apis/registry/v3/search/artifacts?groupId=demo" | json "d['count']")
[ "$count" = "5" ] || fail "expected 5 artifacts in group demo, found $count"
pass "group demo has 5 artifacts"

refs=$(curl -fsS "$REGISTRY_URL/apis/registry/v3/groups/demo/artifacts/weather/versions/1/references" \
  | json "','.join(r['name']+'->'+r['artifactId'] for r in d)")
[ "$refs" = "get_forecast->weather.get_forecast" ] || fail "unexpected MCP server references: $refs"
pass "MCP server weather references weather.get_forecast"

# 2. Discovery: the web console, the ARD catalog and ARD search.
curl -fsS -o /dev/null "$UI_URL/" || fail "web console not reachable at $UI_URL"
agents=$(curl -fsS "$REGISTRY_URL/apis/registry/v3/system/uiConfig" | json "d['features'].get('agents')")
[ "$agents" = "True" ] || fail "web console Agents tab is not enabled (features.agents=$agents)"
pass "web console is up with the Agents tab enabled"

skilled=$(curl -fsS "$REGISTRY_URL/.well-known/agents?skill=translation" \
  | json "','.join(a['artifactId'] for a in d['agents'])")
[ "$skilled" = "translator-agent" ] || fail "agents with the translation skill: $skilled"
pass "skill search finds translator-agent"

code=$(curl -s -o /dev/null -w '%{http_code}' "$REGISTRY_URL/.well-known/ard.json")
[ "$code" = "200" ] || fail "/.well-known/ard.json returned $code"
pass "/.well-known/ard.json returns 200"

found=$(curl -fsS -X POST "$REGISTRY_URL/.well-known/ard/search" \
  -H 'Content-Type: application/json' \
  -d '{"query": {"text": "transla"}}' | json "','.join(r['displayName'] for r in d['results'])")
[ "$found" = "Translation Agent" ] || fail "ARD search for 'transla' found: $found"
pass "ARD search finds the Translation Agent"

# 3. The registry MCP server, over stdio, lists its tools.
# stdin stays open for 30 s so the server can answer before it sees end of input. "|| true" keeps
# set -e from aborting on a failed pipeline: an empty or partial result fails in the case below.
tools=$( { printf '%s\n' \
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}' \
    '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
    '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'; sleep 30; } \
  | docker run -i --rm --network "$COMPOSE_NETWORK" \
      -e REGISTRY_URL=http://apicurio-registry:8080 \
      -e QUARKUS_OIDC_TENANT_ENABLED=false \
      "$MCP_SERVER_IMAGE" 2>/dev/null \
  | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        m = json.loads(line)
    except ValueError:
        continue
    if m.get('id') == 2:
        print(','.join(t['name'] for t in m['result']['tools']))
        break
" || true)
case ",$tools," in
  *,search_artifacts,*) pass "registry MCP server lists $(echo "$tools" | tr ',' '\n' | wc -l | tr -d ' ') tools" ;;
  *) fail "registry MCP server did not list its tools (got: '$tools')" ;;
esac

# 4. Governance: an incompatible tool change is rejected, and the accepted version stays.
response=$(mktemp)
code=$(curl -s -o "$response" -w '%{http_code}' -X POST \
  "$REGISTRY_URL/apis/registry/v3/groups/demo/artifacts/weather.get_forecast/versions" \
  -H 'Content-Type: application/json' \
  -d @"$HERE/get-forecast-v2-incompatible.json")
[ "$code" = "400" ] || fail "incompatible tool version returned $code, expected 400: $(cat "$response")"
grep -q "A property was made 'required'" "$response" || fail "rejection does not name the change: $(cat "$response")"
versions=$(curl -fsS "$REGISTRY_URL/apis/registry/v3/groups/demo/artifacts/weather.get_forecast/versions" \
  | json "','.join(v['version'] for v in d['versions'])")
[ "$versions" = "1" ] || fail "tool versions after the rejection: $versions"
pass "incompatible tool change rejected, version 1 kept"

# 5. Importing tools from the running demo MCP server.
mcp_import() {
  curl -fsS -X POST "$REGISTRY_URL/apis/registry/v3/groups/demo/artifacts/weather/mcp-import" \
    -H 'Content-Type: application/json' -d "$1"
}
statuses="d['status']+' '+','.join(t['name']+'='+t['status'] for t in d['tools'])"

imported=$(mcp_import '{"url": "http://weather-mcp:8000/mcp"}' | json "$statuses")
[ "$imported" = "NEW_VERSION get_forecast=UNCHANGED,get_alerts=CREATED" ] \
  || fail "first import from weather-mcp: $imported"
pass "import from the running server: get_alerts created, get_forecast unchanged"

dry=$(mcp_import '{"url": "http://weather-mcp:8000/mcp-v2", "dryRun": true}' \
  | json "' '.join(t['status'] + ('|' + '|'.join(c['description'] for c in t.get('causes', [])) if t.get('causes') else '') for t in d['tools'] if t['name'] == 'get_forecast')")
case "$dry" in
  REJECTED*"A property was made 'required'."*) pass "breaking change on the redeployed server rejected (dry run)" ;;
  *) fail "dry run against /mcp-v2, get_forecast: $dry" ;;
esac
versions=$(curl -fsS "$REGISTRY_URL/apis/registry/v3/groups/demo/artifacts/weather.get_forecast/versions" \
  | json "','.join(v['version'] for v in d['versions'])")
[ "$versions" = "1" ] || fail "tool versions after the dry run: $versions"

again=$(mcp_import '{"url": "http://weather-mcp:8000/mcp"}' | json "$statuses")
[ "$again" = "UNCHANGED get_forecast=UNCHANGED,get_alerts=UNCHANGED" ] || fail "re-import: $again"
pass "importing again from the unchanged server writes nothing"

echo "Quickstart smoke test passed."
