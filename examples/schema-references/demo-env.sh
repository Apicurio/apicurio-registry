# Shell helpers for exploring this example. Load them into your shell with:
#
#   source ./demo-env.sh
#
# Then call the Registry API:
#
#   status GET /groups/example/artifacts/greeting      # prints only the HTTP status
#   call GET /search/artifacts                         # prints the JSON response
#   call POST /groups '{"groupId": "other"}'           # with a JSON body
#
# Paths are relative to the Core Registry API v3 (/apis/registry/v3). Requires curl and jq.
# Override API if you changed the port (see README.md).

API=${API:-http://localhost:8081/apis}

# _request <method> <path> [json body] [extra curl args...]
_request() {
  # Not "path": in zsh that variable is the command search path
  local method=$1 resource=$2 body=${3:-}
  shift 3 2>/dev/null || shift $#
  local args=(-s -X "$method" "$API/registry/v3$resource")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  curl "${args[@]}" "$@"
}

# status <method> <path> [json body]: prints the HTTP status of the request
status() {
  _request "$1" "$2" "${3:-}" -o /dev/null -w '%{http_code}\n'
}

# call <method> <path> [json body]: prints the response, formatted when it is JSON
call() {
  local response
  response=$(_request "$1" "$2" "${3:-}")
  echo "$response" | jq . 2>/dev/null || echo "$response"
}
