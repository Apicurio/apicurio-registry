#!/usr/bin/env bash
# Creates the demo data and checks every feature shown in GUIDE.md, against the running stack.
#
#   ./demo.sh               all parts (the Quarkus LangChain4j part calls Ollama)
#   SKIP_LLM=1 ./demo.sh    everything except the steps that call the model
#
# Run after `docker compose up -d`. Requires curl and jq. Safe to run again: existing data is
# reused, and every check reads the current state.
set -euo pipefail
cd "$(dirname "$0")"

OPEN=${OPEN:-http://localhost:8180}
SECURED=${SECURED:-http://localhost:8081}
KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
MCP=${MCP:-http://localhost:8082/mcp}
ORCHESTRATOR=${ORCHESTRATOR:-http://localhost:11020/workflow}
V3=/apis/registry/v3
failures=0

# ------------------------------------------------------------------------------ helpers
ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }

# check <description> <expected> <actual>
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# token <user>: an access token for an example user. developer2's password is "developer".
token() {
  local password=$1
  [ "$1" = developer2 ] && password=developer
  curl -sf -X POST "$KEYCLOAK/realms/registry/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=${2:-apicurio-registry}&username=$1&password=$password" | jq -r .access_token
}

# req <base> <user> <method> <path> [body] [extra curl args]: the response body. user "-" = none.
req() {
  local base=$1 user=$2 method=$3 resource=$4 body=${5:-}
  shift 5 2>/dev/null || shift $#
  local args=(-s -X "$method" "$base$resource")
  [ "$user" != "-" ] && args+=(-H "Authorization: Bearer $(token "$user")")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}" "$@"
}
code() {
  local args=("$@")
  [ $# -lt 5 ] && args+=("")
  req "${args[@]}" -o /dev/null -w '%{http_code}'
}

# version_body <file> [version] [references json]: a CreateVersion body for a content file
version_body() {
  jq -n --rawfile c "$1" --arg v "${2:-}" --argjson r "${3:-[]}" \
    '{content: {content: $c, contentType: "application/json", references: $r}} + (if $v == "" then {} else {version: $v} end)'
}

# create <base> <user> <group> <id> <type> <file> [references json] [labels json]: 200 or 409
create() {
  local body
  # The artifact's name and description come from the content, when it has them
  local meta
  meta=$(jq -c '{name: (.name // .info.title), description: (.description // .info.description)} | with_entries(select(.value != null))' "$6" 2>/dev/null || echo '{}')
  body=$(jq -n --arg id "$4" --arg t "$5" --argjson l "${8:-{\}}" --argjson m "$meta" --argjson v "$(version_body "$6" 1 "${7:-[]}")" \
    '{artifactId: $id, artifactType: $t, labels: $l, firstVersion: $v} + $m')
  code "$1" "$2" POST "$V3/groups/$3/artifacts" "$body"
}
created() { [[ "$1" == 200 || "$1" == 409 ]] && echo yes || echo "no ($1)"; }

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-300}))
  until curl -sf -o /dev/null "$2"; do
    if [ "$SECONDS" -ge "$deadline" ]; then echo "Timed out waiting for $1 at $2. Is 'docker compose up -d' running?" >&2; exit 1; fi
    sleep 3
  done
}

echo "Waiting for the services..."
wait_for "open Registry" "$OPEN$V3/system/info"
wait_for "secured Registry" "$SECURED$V3/system/info"
wait_for Keycloak "$KEYCLOAK/realms/registry"
wait_for orchestrator "$ORCHESTRATOR/agents"

# ------------------------------------------------------------------------------ part 1
part "Part 1: agent artifact types (open Registry, group 'demo')"
code "$OPEN" - POST "$V3/groups" '{"groupId":"demo"}' > /dev/null
# Content is checked against each type's schema when a VALIDITY rule applies
code "$OPEN" - POST "$V3/groups/demo/rules" '{"ruleType":"VALIDITY","config":"FULL"}' > /dev/null
check "MCP_TOOL search_guides"     yes "$(created "$(create "$OPEN" - demo search_guides MCP_TOOL content/search-tool-maxlength-10.json)")"
check "MCP_TOOL get_forecast"      yes "$(created "$(create "$OPEN" - demo get_forecast MCP_TOOL content/forecast-tool.json)")"
check "MCP_TOOL packing_list"      yes "$(created "$(create "$OPEN" - demo packing_list MCP_TOOL content/packing-list-tool.json)")"
check "MODEL_SCHEMA chat-model"    yes "$(created "$(create "$OPEN" - demo chat-model MODEL_SCHEMA content/chat-model-schema.json)")"
check "PROMPT_TEMPLATE city-briefing" yes "$(created "$(create "$OPEN" - demo city-briefing PROMPT_TEMPLATE content/briefing-prompt.json)")"
check "invalid MCP_TOOL (no inputSchema) is rejected" 400 \
  "$(code "$OPEN" - POST "$V3/groups/demo/artifacts" '{"artifactId":"broken","artifactType":"MCP_TOOL","firstVersion":{"content":{"content":"{\"name\":\"broken\"}","contentType":"application/json"}}}')"

# ------------------------------------------------------------------------------ part 2
part "Part 2: references between agents, servers and tools (INTEGRITY rule)"
req "$OPEN" - POST "$V3/groups/demo/rules" '{"ruleType":"INTEGRITY","config":"REFS_EXIST"}' -o /dev/null
check "MCP_SERVER travel references get_forecast" yes "$(created "$(create "$OPEN" - demo travel MCP_SERVER content/travel-server.json \
  '[{"groupId":"demo","artifactId":"get_forecast","version":"1","name":"get_forecast"}]')")"
check "AGENT_CARD trip-planner references a tool, a server, a model and a prompt" yes "$(created "$(create "$OPEN" - demo trip-planner AGENT_CARD content/trip-planner-card.json \
  '[{"groupId":"demo","artifactId":"search_guides","version":"1","name":"search_guides"},
    {"groupId":"demo","artifactId":"travel","version":"1","name":"travel"},
    {"groupId":"demo","artifactId":"chat-model","version":"1","name":"model"},
    {"groupId":"demo","artifactId":"city-briefing","version":"1","name":"prompt"}]')")"
check "an MCP_TOOL cannot reference another tool" 400 \
  "$(create "$OPEN" - demo tool-with-reference MCP_TOOL content/packing-list-tool.json '[{"groupId":"demo","artifactId":"get_forecast","version":"1","name":"get_forecast"}]')"
check "a reference to a missing artifact is rejected" 400 \
  "$(code "$OPEN" - POST "$V3/groups/demo/artifacts/travel/versions" "$(version_body content/travel-server.json '' '[{"groupId":"demo","artifactId":"no-such-tool","version":"1","name":"missing"}]')")"
check "inbound references: who uses get_forecast" "travel" \
  "$(req "$OPEN" - GET "$V3/groups/demo/artifacts/get_forecast/versions/1/references?refType=INBOUND" | jq -r '[.[].artifactId] | sort | join(",")')"

# ------------------------------------------------------------------------------ part 3
part "Part 3: compatibility rules keep published contracts safe"
for a in search_guides trip-planner; do
  req "$OPEN" - POST "$V3/groups/demo/artifacts/$a/rules" '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}' -o /dev/null
done
response=$(req "$OPEN" - POST "$V3/groups/demo/artifacts/search_guides/versions" "$(version_body content/search-tool-maxlength-5.json)")
check "tool: narrowing an input (maxLength 10 -> 5) is rejected" "/inputSchema/properties/query/maxLength" \
  "$(echo "$response" | jq -r '.causes[0].context // .status')"
if [ "$(req "$OPEN" - GET "$V3/groups/demo/artifacts/search_guides/versions" | jq .count)" = 1 ]; then
  check "tool: widening an input (maxLength 10 -> 20) is accepted" 200 \
    "$(code "$OPEN" - POST "$V3/groups/demo/artifacts/search_guides/versions" "$(version_body content/search-tool-maxlength-20.json)")"
else ok "tool: widening an input was accepted on an earlier run"; fi
response=$(req "$OPEN" - POST "$V3/groups/demo/artifacts/trip-planner/versions" "$(version_body content/trip-planner-card-without-check-weather.json)")
check "agent card: removing a skill is rejected" "Skill 'check-weather' was removed" \
  "$(echo "$response" | jq -r '.causes[0].description // .status')"

# ------------------------------------------------------------------------------ part 4
part "Part 4: prompt templates"
rendered=$(req "$OPEN" - POST "$V3/groups/demo/artifacts/city-briefing/versions/branch=latest/render" '{"variables":{"city":"Lisbon","weather":"sunny, 24 C"}}')
check "render with variables and a default" "Write a short briefing for a visit to Lisbon. Mention the weather: sunny, 24 C." \
  "$(echo "$rendered" | jq -r .rendered)"
check "a missing required variable is reported" "weather" \
  "$(req "$OPEN" - POST "$V3/groups/demo/artifacts/city-briefing/versions/branch=latest/render" '{"variables":{"city":"Lisbon"}}' | jq -r '[.validationErrors[].variableName] | join(",")')"

# ------------------------------------------------------------------------------ part 5
part "Part 5: Agent Cards generated from OpenAPI"
create "$OPEN" - demo weather-api OPENAPI content/weather-api-openapi.json > /dev/null
check "a companion Agent Card is created" "Weather API" \
  "$(req "$OPEN" - GET "$V3/groups/demo/artifacts/weather-api-agent-card/versions/branch=latest/content" | jq -r .name)"
if [ "$(req "$OPEN" - GET "$V3/groups/demo/artifacts/weather-api/versions" | jq .count)" = 1 ]; then
  code "$OPEN" - POST "$V3/groups/demo/artifacts/weather-api/versions" "$(version_body content/weather-api-openapi-v2.json)" > /dev/null
fi
check "updating the OpenAPI updates the card" "2.0.0 current-weather,forecast" \
  "$(req "$OPEN" - GET "$V3/groups/demo/artifacts/weather-api-agent-card/versions/branch=latest/content" | jq -r '.version + " " + ([.skills[].id] | join(","))')"

# ------------------------------------------------------------------------------ part 6
part "Part 6: discovery endpoints"
check "Registry's own Agent Card" "Apicurio Registry (agent registry demo)" "$(req "$OPEN" - GET /.well-known/agent.json | jq -r .name)"
check "agents with the translator skill (published by the Quarkus translator)" "translator" \
  "$(req "$OPEN" - GET "/.well-known/agents?skill=translator" | jq -r '[.agents[].artifactId] | join(",")')"
check "MCP tools that take a 'city' parameter" "get_forecast" \
  "$(req "$OPEN" - GET "/.well-known/mcp-tools?parameter=city&limit=50" | jq -r '[.tools[] | select(.groupId=="demo") | .artifactId] | join(",")')"
check "tools that accept get_forecast's output (chaining)" "packing_list" \
  "$(req "$OPEN" - GET "/.well-known/mcp-tools/demo/get_forecast/compatible" | jq -r '[.tools[] | select(.groupId=="demo") | .artifactId] | join(",")')"
check "AI Catalog lists the trip planner" "true" \
  "$(req "$OPEN" - GET /.well-known/ai-catalog.json | jq '[.entries[].displayName] | index("Trip planner") != null')"
check "ARD search by name" "Trip planner" \
  "$(req "$OPEN" - POST /.well-known/ard/search '{"query":{"text":"trip"}}' | jq -r '[.results[].displayName] | join(",")')"
check "ARD explore: facets by type" "true" \
  "$(req "$OPEN" - POST /.well-known/ard/explore '{"query":{"text":"*"},"resultType":{"facets":[{"field":"type","limit":10}]}}' | jq '.facets.type.buckets | length > 0')"

# ------------------------------------------------------------------------------ part 7
part "Part 7: MCP Registry API"
code "$OPEN" - POST /apis/mcp-registry/v0.1/publish "$(cat content/calendar-server.json)" > /dev/null
check "published server is listed" "io.apicur.demo/calendar" \
  "$(req "$OPEN" - GET "/apis/mcp-registry/v0.1/servers?search=calendar" | jq -r '[.servers[].server.name] | join(",")')"
code "$OPEN" - PATCH "/apis/mcp-registry/v0.1/servers/io.apicur.demo%2Fcalendar/versions/1.0.0/status" '{"status":"deprecated","statusMessage":"Use the travel server"}' > /dev/null
check "status changed to deprecated" "deprecated" \
  "$(req "$OPEN" - GET "/apis/mcp-registry/v0.1/servers/io.apicur.demo%2Fcalendar/versions/1.0.0" | jq -r '._meta["io.modelcontextprotocol.registry/official"].status')"

# ------------------------------------------------------------------------------ part 8
part "Part 8: importing tools from the live weather MCP server"
# The Quarkus MCP extension finds MCP servers by artifact description, set here from the content
create "$OPEN" - weather-tools weather MCP_SERVER content/weather-server.json > /dev/null
check "dry run lists getWeather and writes nothing" "getWeather" \
  "$(req "$OPEN" - POST "$V3/groups/weather-tools/artifacts/weather/mcp-import" '{"url":"http://weather-server:11040/mcp","dryRun":true}' | jq -r '[.tools[].name] | join(",")')"
req "$OPEN" - POST "$V3/groups/weather-tools/artifacts/weather/mcp-import" '{"url":"http://weather-server:11040/mcp"}' -o /dev/null
check "the server references the imported tool" "weather.getWeather" \
  "$(req "$OPEN" - GET "$V3/groups/weather-tools/artifacts/weather/versions/branch=latest/references" | jq -r '[.[].artifactId] | join(",")')"
check "importing again changes nothing" "UNCHANGED" \
  "$(req "$OPEN" - POST "$V3/groups/weather-tools/artifacts/weather/mcp-import" '{"url":"http://weather-server:11040/mcp"}' | jq -r .status)"
check "an import from a host that is not allowed is refused" 400 \
  "$(code "$OPEN" - POST "$V3/groups/weather-tools/artifacts/weather/mcp-import" '{"url":"https://evil.example.com/mcp"}')"

# ------------------------------------------------------------------------------ part 9
part "Part 9: Quarkus LangChain4j extensions"
check "the orchestrator discovers both A2A agents" "summarizer,translator" \
  "$(curl -s "$ORCHESTRATOR/agents" | jq -r 'keys | sort | join(",")')"
req "$OPEN" - POST "$V3/groups/default/artifacts/translator/rules" '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}' -o /dev/null
translator_versions=$(req "$OPEN" - GET "$V3/groups/default/artifacts/translator/versions" | jq .count)
card_without_skill=$(req "$OPEN" - GET "$V3/groups/default/artifacts/translator/versions/branch=latest/content" \
  | jq -c '.skills = [.skills[] | select(.id != "translator")]')
check "the translator cannot lose its only skill" 400 \
  "$(code "$OPEN" - POST "$V3/groups/default/artifacts/translator/versions" \
     "$(jq -n --arg c "$card_without_skill" '{content: {content: $c, contentType: "application/json"}}')")"
check "the accepted translator version is kept" "$translator_versions" \
  "$(req "$OPEN" - GET "$V3/groups/default/artifacts/translator/versions" | jq .count)"
if [ -n "${SKIP_LLM:-}" ]; then
  echo "  skip  the briefing (SKIP_LLM is set)"
else
  briefing=$(curl -s -X POST "$ORCHESTRATOR/briefing" -H 'Content-Type: application/json' \
    -d '{"city":"Amsterdam","context":"Visitors will walk through the city and meet colleagues to discuss open standards."}')
  check "briefing: MCP weather, then A2A summarize, then A2A translate" "MCP weather,A2A summarize,A2A translate" \
    "$(echo "$briefing" | jq -r '[.steps[].operation] | join(",")' 2>/dev/null || echo "error")"
  check "briefing: the weather came from the MCP server" "true" \
    "$(echo "$briefing" | jq '.steps[0].output | contains("16 C")' 2>/dev/null || echo "error")"
  check "briefing: the summary was handed to the translator" "true" \
    "$(echo "$briefing" | jq '.steps[2].input == .steps[1].output' 2>/dev/null || echo "error")"
fi

# ------------------------------------------------------------------------------ part 10
part "Part 10: who sees which agents (secured Registry)"
card() { # card <name> <skill>: a small Agent Card file
  local f
  f=$(mktemp)
  jq --arg n "$1" --arg s "$2" '.name = $n | .description = ($n + " (visibility demo)") | .skills = [{id: $s, name: $s, description: $s, tags: ["demo"]}]' \
    content/trip-planner-card.json > "$f"
  echo "$f"
}
for g in agents team-b; do code "$SECURED" admin POST "$V3/groups" "{\"groupId\":\"$g\"}" > /dev/null; done
create "$SECURED" admin agents public-agent AGENT_CARD "$(card "Public agent" public-skill)" '[]' '{"apicurio.agent.visibility":"public"}' > /dev/null
create "$SECURED" admin agents entitled-agent AGENT_CARD "$(card "Entitled agent" entitled-skill)" '[]' '{"apicurio.agent.visibility":"entitled"}' > /dev/null
create "$SECURED" developer agents private-agent AGENT_CARD "$(card "Private agent" private-skill)" '[]' '{"apicurio.agent.visibility":"private"}' > /dev/null
create "$SECURED" admin team-b team-b-agent AGENT_CARD "$(card "Team B agent" team-b-skill)" '[]' '{"apicurio.agent.visibility":"entitled"}' > /dev/null
discovered() { req "$SECURED" "$1" GET "/.well-known/agents?limit=100" | jq -r '[.agents[].artifactId] | sort | join(",")'; }
check "anonymous"                           "public-agent" "$(discovered -)"
check "user (sr-readonly)"                  "entitled-agent,public-agent" "$(discovered user)"
check "developer2 (sr-developer)"           "entitled-agent,public-agent" "$(discovered developer2)"
check "developer (owns the private agent, may read team-b)" "entitled-agent,private-agent,public-agent,team-b-agent" "$(discovered developer)"
check "admin"                               "entitled-agent,private-agent,public-agent,team-b-agent" "$(discovered admin)"
check "developer2 cannot read team-b at all" 403 "$(code "$SECURED" developer2 GET "$V3/groups/team-b/artifacts/team-b-agent")"
check "ARD search applies the same rules (user)" "Entitled agent,Public agent" \
  "$(req "$SECURED" user POST /.well-known/ard/search '{"query":{"text":"agent"},"pageSize":50}' | jq -r '[.results[] | select(.type | contains("a2a")) | .displayName] | sort | join(",")')"

# ------------------------------------------------------------------------------ part 11
part "Part 11: the Registry MCP server, as a logged-in user"
# mcp_call <user> <method> <params json>: a JSON-RPC call over Streamable HTTP, with the user's token
mcp_call() {
  local t sid
  t=$(token "$1" apicurio-mcp)
  local h=(-H "Authorization: Bearer $t" -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json')
  sid=$(curl -s -D - -o /dev/null "${h[@]}" "$MCP" \
    -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"demo.sh","version":"1"}}}' \
    | awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}' | tr -d '\r')
  curl -s -o /dev/null "${h[@]}" -H "Mcp-Session-Id: $sid" "$MCP" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  curl -s "${h[@]}" -H "Mcp-Session-Id: $sid" "$MCP" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$2\",\"params\":$3}" \
    | sed -n 's/^data: //p; /^{/p' | head -1
}
check "an unauthenticated MCP request is refused" 401 \
  "$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' "$MCP" -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{}}')"
check "tools/list includes discover_agents" "true" \
  "$(mcp_call developer tools/list '{}' | jq '[.result.tools[].name] | index("discover_agents") != null')"
agents_for() { mcp_call "$1" tools/call '{"name":"discover_agents","arguments":{}}' | jq -r '.result.content[0].text'; }
check "discover_agents as developer finds the team-b agent" "true" "$(agents_for developer | grep -q team-b-agent && echo true || echo false)"
check "discover_agents as user does not"                    "false" "$(agents_for user | grep -q team-b-agent && echo true || echo false)"

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; exit 1; fi
