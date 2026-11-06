# Shell helpers for presenting this example. Load them into your shell with:
#
#   source ./demo-env.sh
#
# Then call the Registry API:
#
#   call GET /groups/orders/artifacts/OrderEvent/contract/metadata     # prints the JSON response
#   status GET /groups/orders/contracts/orders-contract                # prints only the HTTP status
#   call POST /admin/contracts/ruleset '{"domainRules":[]}'            # with a JSON body
#   submit contracts/orders-contract.yaml                              # submits an ODCS contract
#   check '{"orderId":"A1","totalAmount":-5}'                          # runs the WRITE rules on a record
#
# Paths are relative to the Core Registry API v3 (/apis/registry/v3). Requires curl and jq.
# Override API if you changed the port (see README.md).

API=${API:-http://localhost:8081/apis}
# Shortcut for the schema artifact used throughout the guide
ORDER=/groups/orders/artifacts/OrderEvent

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
  # printf, not echo: zsh's echo would turn the "\n" escapes inside JSON strings into newlines
  printf '%s\n' "$response" | jq . 2>/dev/null || printf '%s\n' "$response"
}

# submit <file> [method] [contract id]: submits an ODCS contract to group "orders" and prints the
# projection summary. Use "submit <file> PUT orders-contract" to update an existing contract.
submit() {
  local resource=/groups/orders/contracts
  [ "${2:-POST}" = PUT ] && resource="$resource/$3"
  curl -s -X "${2:-POST}" "$API/registry/v3$resource" -H 'Content-Type: application/x-yaml' \
    --data-binary @"$1" | jq .
}

# check <json record> [mode] [version]: executes the contract rules of OrderEvent against a record
# (mode WRITE by default, version "latest" by default) and prints the result
check() {
  local version=${3:-branch=latest}
  call POST "$ORDER/versions/$version/contract/execute" "{\"mode\":\"${2:-WRITE}\",\"record\":$1}"
}
