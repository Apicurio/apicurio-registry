# Shell helpers for presenting the agent registry demo. Load them into your shell with:
#
#   source ./demo-env.sh
#
# Then, for example:
#
#   open_api GET /.well-known/agents?skill=translator     # open Registry, no credentials
#   secured_api user GET /.well-known/agents              # secured Registry, as "user"
#   agents developer                                      # the agents a user discovers
#   mcp developer tools/call '{"name":"discover_agents","arguments":{}}'
#
# Paths starting with /apis or /.well-known are used as they are; any other path is relative to
# the Core Registry API v3 (/apis/registry/v3). Requires curl and jq.

OPEN=${OPEN:-http://localhost:8180}
SECURED=${SECURED:-http://localhost:8081}
KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
MCP=${MCP:-http://localhost:8082/mcp}

# token <user> [client]: an access token. developer2's password is "developer"; every other
# user's password is its user name.
token() {
  local password=$1
  [ "$1" = developer2 ] && password=developer
  curl -sf -X POST "$KEYCLOAK/realms/registry/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=${2:-apicurio-registry}&username=$1&password=$password" | jq -r .access_token
}

_url() {
  case "$2" in
    /apis/* | /.well-known/*) echo "$1$2" ;;
    *) echo "$1/apis/registry/v3$2" ;;
  esac
}

_show() {
  local response
  response=$(cat)
  echo "$response" | jq . 2>/dev/null || echo "$response"
}

# open_api <method> <path> [json body]: calls the open Registry
open_api() {
  local args=(-s -X "$1" "$(_url "$OPEN" "$2")")
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' -d "$3")
  curl "${args[@]}" | _show
}

# secured_api <user|anonymous> <method> <path> [json body]: calls the secured Registry as a user
secured_api() {
  local args=(-s -X "$2" "$(_url "$SECURED" "$3")")
  [ "$1" != anonymous ] && args+=(-H "Authorization: Bearer $(token "$1")")
  [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
  curl "${args[@]}" | _show
}

# agents <user|anonymous>: the agents a user discovers on the secured Registry
agents() {
  secured_api "$1" GET "/.well-known/agents?limit=100" | jq -r '.agents[] | "\(.groupId)/\(.artifactId)"' | sort
}

# mcp <user> <method> [params json]: one JSON-RPC call to the Registry MCP server, as a user.
# Opens a new MCP session for each call.
mcp() {
  local t sid
  t=$(token "$1" apicurio-mcp)
  local h=(-H "Authorization: Bearer $t" -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json')
  sid=$(curl -s -D - -o /dev/null "${h[@]}" "$MCP" \
    -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"demo-env","version":"1"}}}' \
    | awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}' | tr -d '\r')
  curl -s -o /dev/null "${h[@]}" -H "Mcp-Session-Id: $sid" "$MCP" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  curl -s "${h[@]}" -H "Mcp-Session-Id: $sid" "$MCP" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$2\",\"params\":${3:-{\}}}" | sed -n 's/^data: //p; /^{/p' | head -1 | jq .
}

# publish <group> <artifactId> <type> <content file> [references json]: creates an artifact on the
# open Registry from one of the files in content/. Its name and description come from the content.
publish() {
  local meta
  meta=$(jq -c '{name: (.name // .info.title), description: (.description // .info.description)} | with_entries(select(.value != null))' "$4")
  jq -n --arg id "$2" --arg t "$3" --rawfile c "$4" --argjson r "${5:-[]}" --argjson m "$meta" \
    '{artifactId: $id, artifactType: $t, firstVersion: {content: {content: $c, contentType: "application/json", references: $r}}} + $m' \
    | curl -s -X POST "$OPEN/apis/registry/v3/groups/$1/artifacts" -H 'Content-Type: application/json' -d @- | _show
}

# new_version <group> <artifactId> <content file>: adds a version on the open Registry. Prints the
# new version, or the rule violation that rejected it.
new_version() {
  jq -n --rawfile c "$3" '{content: {content: $c, contentType: "application/json"}}' \
    | curl -s -X POST "$OPEN/apis/registry/v3/groups/$1/artifacts/$2/versions" -H 'Content-Type: application/json' -d @- | _show
}
