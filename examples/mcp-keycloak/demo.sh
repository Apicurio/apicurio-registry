#!/usr/bin/env bash
# Checks the Registry MCP server against a Keycloak-secured Registry, in both MCP modes:
# stdio (service-account login with APICURIO_MCP_AUTH_*) and HTTP (the caller's token, forwarded).
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl, jq and docker compose)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
# Safe to run again: it starts by deleting the groups it creates (and only those).
set -euo pipefail
cd "$(dirname "$0")"

KEYCLOAK=${KEYCLOAK:-http://localhost:${KEYCLOAK_PORT:-8080}}
REGISTRY=${REGISTRY:-http://localhost:${REGISTRY_PORT:-8081}}
MCP=${MCP:-http://localhost:${MCP_PORT:-8082}/mcp}
TOKEN_URL="$KEYCLOAK/realms/registry/protocol/openid-connect/token"
V3="$REGISTRY/apis/registry/v3"
STDIO_GROUP=mcp-stdio-demo
HTTP_GROUP=mcp-http-demo
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
# check <description> <expected> <actual>
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }
# contains <description> <needle> <haystack>
contains() { if [[ "$3" == *"$2"* ]]; then ok "$1"; else fail "$1: '$2' not in '${3:0:300}'"; fi; }

# token <client>: a service account's token (client credentials grant; every secret is "test1")
token() {
  curl -s --max-time 30 "$TOKEN_URL" -d "grant_type=client_credentials&client_id=$1&client_secret=test1" | jq -r .access_token
}
# rest <token> <method> <path>: the HTTP status of a Registry REST call
rest() {
  curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$2" -H "Authorization: Bearer $1" "$V3$3"
}
# claim <token> <jq filter>: a claim of a JWT
claim() {
  local p
  p=$(cut -d. -f2 <<< "$1" | tr '_-' '/+')
  while [ $(( ${#p} % 4 )) -ne 0 ]; do p="$p="; done
  base64 -d <<< "$p" 2>/dev/null | jq -r "$2"
}

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-300}))
  until [ "$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' "$2")" = "$3" ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

INIT='{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"demo.sh","version":"1"}}}'
INITIALIZED='{"jsonrpc":"2.0","method":"notifications/initialized"}'
# call <id> <method> <params json>: a JSON-RPC request
call() { echo "{\"jsonrpc\":\"2.0\",\"id\":$1,\"method\":\"$2\",\"params\":$3}"; }
tool() { call "$1" tools/call "{\"name\":\"$2\",\"arguments\":$3}"; }

# ------------------------------------------------------------------ stdio transport
# stdio_open <client id> <secret>: starts a stdio MCP server as an MCP client would (one per
# session), connected to this script through two named pipes
stdio_open() {
  STDIO_DIR=$(mktemp -d)
  mkfifo "$STDIO_DIR/in" "$STDIO_DIR/out"
  docker compose run --rm --no-deps -T \
    -e "APICURIO_MCP_AUTH_CLIENT_ID=$1" -e "APICURIO_MCP_AUTH_CLIENT_SECRET=$2" \
    apicurio-registry-mcp-stdio < "$STDIO_DIR/in" > "$STDIO_DIR/out" 2>/dev/null &
  STDIO_PID=$!
  exec {STDIO_W}> "$STDIO_DIR/in"
  exec {STDIO_R}< "$STDIO_DIR/out"
}
# stdio_send <request>: sends one message; for a request, prints the response with the same id
# ("EOF" if the server exited first), skipping server-initiated messages and notifications
stdio_send() {
  local id line
  id=$(jq -r '.id // empty' <<< "$1")
  printf '%s\n' "$1" >&"$STDIO_W" 2>/dev/null || { echo EOF; return; }
  [ -z "$id" ] && return
  while IFS= read -r -t 180 line <&"$STDIO_R"; do
    if [ "$(jq -r 'try .id catch empty' <<< "$line" 2>/dev/null)" = "$id" ]; then
      echo "$line"
      return
    fi
  done
  echo EOF
}
# stdio_close: closes stdin, as an MCP client does at the end of a session; the server exits
stdio_close() {
  exec {STDIO_W}>&-
  wait "$STDIO_PID" 2>/dev/null || true
  exec {STDIO_R}<&-
  rm -rf "$STDIO_DIR"
}

# ------------------------------------------------------------------- HTTP transport
SID=""
# http_open <token>: initializes an MCP session over Streamable HTTP; sets SID and STATUS (the
# HTTP status). Not called in $(...), so the session survives for http_send.
http_open() {
  HTTP_TOKEN=$1
  local headers
  headers=$(mktemp)
  STATUS=$(curl -s --max-time 60 -D "$headers" -o /dev/null -w '%{http_code}' "$MCP" \
    -H "Authorization: Bearer $HTTP_TOKEN" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' -d "$INIT")
  SID=$(awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}' "$headers" | tr -d '\r')
  rm -f "$headers"
  if [ "$STATUS" = 200 ]; then http_send "$INITIALIZED" > /dev/null; fi
}
# http_send <request>: a JSON-RPC message in the open session; prints the JSON response
http_send() {
  curl -s --max-time 60 "$MCP" -H "Authorization: Bearer $HTTP_TOKEN" -H "Mcp-Session-Id: $SID" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d "$1" \
    | sed -n 's/^data: //p; /^{/p' | head -1
}

text()  { jq -r '.result.content[0].text // empty' <<< "$1"; }
# ids <response> <field>: a list tool answers one content item per entry; their <field>s, joined
ids()   { jq -r --arg f "$2" '[.result.content[].text | fromjson | .[$f]] | join(",")' <<< "$1" 2>/dev/null; }
error() { jq -r '.result.isError // false' <<< "$1"; }
names() { jq -r '[.result.tools[].name] | sort | join(",")' <<< "$1"; }

echo "Waiting for Keycloak, Registry and the MCP server..."
wait_for Keycloak "$KEYCLOAK/realms/registry" 200
wait_for Registry "$V3/system/info" 200
wait_for "the MCP server" "$MCP" 401

ADMIN=$(token admin-client)
echo "Resetting the example data..."
for g in "$STDIO_GROUP" "$HTTP_GROUP"; do rest "$ADMIN" DELETE "/groups/$g" > /dev/null; done

# A subset of the tools in mcp/src/main/java/io/apicurio/registry/mcp/servers/
EXPECTED_TOOLS="create_artifact,create_group,get_artifact_metadata,get_server_info,list_artifacts,list_groups,search_artifacts"
LIST_GROUPS='{"order":"asc","orderBy":"groupId"}'
LIST_ARTIFACTS() { echo "{\"groupId\":\"$1\",\"order\":\"asc\",\"artifactOrderBy\":\"artifactId\"}"; }
CREATE() { echo "{\"groupId\":\"$1\",\"artifactId\":\"$2\",\"artifactType\":\"JSON\",\"name\":\"Created over MCP\"}"; }

# ------------------------------------------------------------------------------- part 1
part "Part 1: the Registry is secured"
check "an anonymous REST call is refused"                  401 "$(curl -s -o /dev/null -w '%{http_code}' "$V3/groups")"
check "admin-client may read through REST"                  200 "$(rest "$ADMIN" GET /groups)"

# ------------------------------------------------------------------------------- part 2
part "Part 2: stdio mode, logged in as admin-client (sr-admin)"
stdio_open admin-client test1
r=$(stdio_send "$INIT")
check "initialize answers with the server's name"           "true" "$(jq -r '.result.serverInfo.name != null' <<< "$r" 2>/dev/null || echo "$r")"
stdio_send "$INITIALIZED"
r=$(stdio_send "$(call 1 tools/list '{}')")
all_tools=$(names "$r")
check "tools/list has the registry tools" "$EXPECTED_TOOLS" \
  "$(jq -r --arg want "$EXPECTED_TOOLS" '[.result.tools[].name | select(. as $n | $want | split(",") | index($n))] | sort | join(",")' <<< "$r")"
r=$(stdio_send "$(tool 2 get_server_info '{}')")
check "get_server_info reads the Registry's name"           "Apicurio Registry (SQL)" "$(text "$r" | jq -r .name)"
r=$(stdio_send "$(tool 3 create_group "{\"groupId\":\"$STDIO_GROUP\",\"description\":\"Created over MCP (stdio)\"}")")
check "create_group succeeds"                               "$STDIO_GROUP" "$(text "$r" | jq -r .groupId)"
r=$(stdio_send "$(tool 4 create_artifact "$(CREATE "$STDIO_GROUP" orders)")")
check "create_artifact succeeds"                            "orders" "$(text "$r" | jq -r .artifactId)"
# The tool's schema marks artifactType optional, but Registry requires it (#11272)
r=$(stdio_send "$(tool 6 create_artifact "{\"groupId\":\"$STDIO_GROUP\",\"artifactId\":\"untyped\"}")")
contains "create_artifact without artifactType: Registry answers 400" "HTTP 400" "$(text "$r")"
r=$(stdio_send "$(tool 5 list_artifacts "$(LIST_ARTIFACTS "$STDIO_GROUP")")")
check "list_artifacts reads it back"                        "orders" "$(ids "$r" artifactId)"
stdio_close
check "the artifact is visible through REST"                "Created over MCP" \
  "$(curl -s -H "Authorization: Bearer $ADMIN" "$V3/groups/$STDIO_GROUP/artifacts/orders" | jq -r .name)"
check "...owned by the service account MCP logged in as"   "admin-client" \
  "$(curl -s -H "Authorization: Bearer $ADMIN" "$V3/groups/$STDIO_GROUP/artifacts/orders" | jq -r .owner)"

# ------------------------------------------------------------------------------- part 3
part "Part 3: stdio mode, the service account's roles apply"
stdio_open readonly-client test1
stdio_send "$INIT" > /dev/null
stdio_send "$INITIALIZED"
r=$(stdio_send "$(tool 1 list_artifacts "$(LIST_ARTIFACTS "$STDIO_GROUP")")")
check "readonly-client (sr-readonly) may list artifacts"    "orders" "$(ids "$r" artifactId)"
r=$(stdio_send "$(tool 2 create_artifact "$(CREATE "$STDIO_GROUP" readonly-attempt)")")
check "...but create_artifact is an error"                  "true" "$(error "$r")"
contains "...because Registry answered 403"                 "403" "$(text "$r")"
stdio_close
check "...and nothing was created"                          404 "$(rest "$ADMIN" GET "/groups/$STDIO_GROUP/artifacts/readonly-attempt")"

# ------------------------------------------------------------------------------- part 4
part "Part 4: stdio mode, bad credentials"
# The server starts and initializes; it only asks Keycloak for a token on the first tool call
stdio_open admin-client wrong-secret
check "a wrong client secret: the session still opens"      "true" "$(stdio_send "$INIT" | jq -r '.result.serverInfo.name != null')"
stdio_send "$INITIALIZED"
r=$(stdio_send "$(tool 1 list_groups "$LIST_GROUPS")")
check "...but every tool call is an error"                  "true" "$(error "$r")"
check "...because Keycloak refused the client credentials"  "Execution failed: Failed to obtain OAuth2 token" "$(text "$r")"
stdio_close

# ------------------------------------------------------------------------------- part 5
part "Part 5: HTTP mode, the endpoint needs a token issued for it"
DEVELOPER=$(token developer-client)
READONLY=$(token readonly-client)
NOROLE=$(token no-role-client)
check "developer-client's token has the apicurio-mcp audience" "true" "$(claim "$DEVELOPER" '[.aud] | flatten | index("apicurio-mcp") != null')"
check "admin-client's token does not"                       "false" "$(claim "$ADMIN" '[.aud] | flatten | index("apicurio-mcp") != null')"
check "no token: 401"                                       401 \
  "$(curl -s -o /dev/null -w '%{http_code}' "$MCP" -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d "$INIT")"
check "a malformed token: 401"                              401 "$(http_open not-a-jwt; echo "$STATUS")"
check "a token for another audience (admin-client, sr-admin): 401" 401 "$(http_open "$ADMIN"; echo "$STATUS")"
check "the protected-resource metadata names Keycloak"      "$KEYCLOAK/realms/registry" \
  "$(curl -s "${MCP%/mcp}/.well-known/oauth-protected-resource" | jq -r '.authorization_servers[0]')"

# ------------------------------------------------------------------------------- part 6
part "Part 6: HTTP mode, the caller's token is forwarded to Registry"
http_open "$DEVELOPER"
check "developer-client opens a session"                    200 "$STATUS"
r=$(http_send "$(call 1 tools/list '{}')")
check "tools/list over HTTP is the same as over stdio"      "$all_tools" "$(names "$r")"
r=$(http_send "$(tool 2 list_groups "$LIST_GROUPS")")
contains "list_groups sees the stdio group"                 "$STDIO_GROUP" "$(ids "$r" groupId)"
r=$(http_send "$(tool 3 create_artifact "$(CREATE "$HTTP_GROUP" invoices)")")
check "create_artifact as developer-client (sr-developer)"  "invoices" "$(text "$r" | jq -r .artifactId)"
check "the artifact is visible through REST"                "Created over MCP" \
  "$(curl -s -H "Authorization: Bearer $ADMIN" "$V3/groups/$HTTP_GROUP/artifacts/invoices" | jq -r .name)"
check "...owned by the caller, not by an MCP service account" "developer-client" \
  "$(curl -s -H "Authorization: Bearer $ADMIN" "$V3/groups/$HTTP_GROUP/artifacts/invoices" | jq -r .owner)"

http_open "$READONLY"
check "readonly-client opens a session"                     200 "$STATUS"
r=$(http_send "$(tool 1 get_artifact_metadata "{\"groupId\":\"$HTTP_GROUP\",\"artifactId\":\"invoices\"}")")
check "readonly-client may read the artifact"               "invoices" "$(text "$r" | jq -r .artifactId)"
r=$(http_send "$(tool 2 create_artifact "$(CREATE "$HTTP_GROUP" readonly-attempt)")")
check "...but create_artifact is an error"                  "true" "$(error "$r")"
contains "...because Registry answered 403"                 "403" "$(text "$r")"
check "...and nothing was created"                          404 "$(rest "$ADMIN" GET "/groups/$HTTP_GROUP/artifacts/readonly-attempt")"

http_open "$NOROLE"
check "no-role-client (valid token, no registry role) opens a session" 200 "$STATUS"
r=$(http_send "$(tool 1 list_groups "$LIST_GROUPS")")
check "...but list_groups is an error"                      "true" "$(error "$r")"
contains "...because Registry answered 403"                 "403" "$(text "$r")"

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; exit 1; fi
