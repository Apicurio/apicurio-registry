# Shell helpers for presenting this example. Load them into your shell with:
#
#   source ./demo-env.sh
#
# Then call the Registry API as any of the example users:
#
#   status developer GET /groups/team-a/artifacts/user-events      # prints only the HTTP status
#   call developer GET /search/artifacts                             # prints the JSON response
#   call admin PUT /groups/team-a/artifacts/secret-schema '{"owner":"developer"}'
#   status anonymous GET /groups/shared/artifacts/common-types       # no credentials at all
#
# Paths are relative to the Core Registry API v3 (/apis/registry/v3). Requires curl and jq.
# Override KEYCLOAK and API if you changed the ports (see README.md).

KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
API=${API:-http://localhost:8081/apis}

# token <user>: prints an access token for one of the example users
token() {
  local password=$1
  # developer2's password is "developer"; every other user's password is its user name
  [ "$1" = developer2 ] && password=developer
  curl -sf -X POST "$KEYCLOAK/realms/registry/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=apicurio-registry&username=$1&password=$password" | jq -r '.access_token'
}

# _request <user> <method> <path> [json body] [extra curl args...]
_request() {
  # Not "path": in zsh that variable is the command search path
  local user=$1 method=$2 resource=$3 body=${4:-}
  shift 4 2>/dev/null || shift $#
  local args=(-s -X "$method" "$API/registry/v3$resource")
  if [ "$user" != anonymous ]; then
    args+=(-H "Authorization: Bearer $(token "$user")")
  fi
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  curl "${args[@]}" "$@"
}

# status <user> <method> <path> [json body]: prints the HTTP status of the request
status() {
  _request "$1" "$2" "$3" "${4:-}" -o /dev/null -w '%{http_code}\n'
}

# call <user> <method> <path> [json body]: prints the response, formatted when it is JSON
call() {
  local response
  response=$(_request "$1" "$2" "$3" "${4:-}")
  echo "$response" | jq . 2>/dev/null || echo "$response"
}

# visible <user>: the artifacts a user's search returns, one "group/artifact" per line
visible() {
  _request "$1" GET "/search/artifacts?limit=100" | jq -r '.artifacts[] | "\(.groupId)/\(.artifactId)"' | sort
}

# denials: the last authorization denials in the Registry audit log
denials() {
  docker logs apicurio-registry-api-resource-authz 2>&1 | grep 'authz.denied' | tail -"${1:-5}"
}
