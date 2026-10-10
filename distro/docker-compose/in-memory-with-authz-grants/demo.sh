#!/usr/bin/env bash
# Seeds sample data and verifies per-resource authorization against the running example.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq)
set -euo pipefail

KEYCLOAK=${KEYCLOAK:-http://localhost:8080}
API=${API:-http://localhost:8081/apis}
failures=0

token() {
  curl -sf -X POST "$KEYCLOAK/realms/registry/protocol/openid-connect/token" \
    -d "grant_type=password&client_id=apicurio-registry&username=$1&password=$1" | jq -r '.access_token'
}

# expect <description> <expected status> <curl args...>
expect() {
  local description=$1 expected=$2
  shift 2
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if [[ "$status" == "$expected" ]]; then
    printf '  ok    %-60s %s\n' "$description" "$status"
  else
    printf '  FAIL  %-60s %s (expected %s)\n' "$description" "$status" "$expected"
    failures=$((failures + 1))
  fi
}

# search_ids <token> -> sorted "group/artifact" list visible to the caller
search_ids() {
  curl -sf -H "Authorization: Bearer $1" "$API/registry/v3/search/artifacts?limit=100" \
    | jq -r '[.artifacts[] | "\(.groupId)/\(.artifactId)"] | sort | join(" ")'
}

echo "Waiting for Keycloak and Registry..."
until curl -sf "$KEYCLOAK/realms/registry" > /dev/null; do sleep 2; done
until curl -sf "$API/registry/v3/system/info" > /dev/null; do sleep 2; done

ADMIN=$(token admin)
DEV=$(token developer)
USER=$(token user)

echo "Seeding data as admin..."
for g in team-a team-b shared; do
  curl -s -o /dev/null -X POST "$API/registry/v3/groups" -H "Authorization: Bearer $ADMIN" \
    -H 'Content-Type: application/json' -d "{\"groupId\": \"$g\"}"
done
for ga in team-a/user-events team-a/order-schema team-a/secret-schema team-b/inventory-schema \
    team-b/public-schema shared/common-types shared/error-schema; do
  g=${ga%%/*}; a=${ga#*/}
  curl -s -o /dev/null -X POST "$API/registry/v3/groups/$g/artifacts" -H "Authorization: Bearer $ADMIN" \
    -H 'Content-Type: application/json' \
    -d "{\"artifactId\": \"$a\", \"artifactType\": \"JSON\", \"firstVersion\": {\"content\": {\"content\": \"{\\\"title\\\":\\\"$ga\\\"}\", \"contentType\": \"application/json\"}}}"
done

echo "Point access:"
art() { echo "$API/registry/v3/groups/$1/artifacts/$2"; }
expect "developer reads team-a/user-events (prefix grant)" 200 -H "Authorization: Bearer $DEV" "$(art team-a user-events)"
expect "developer reads team-a/secret-schema (deny rule)" 403 -H "Authorization: Bearer $DEV" "$(art team-a secret-schema)"
expect "developer reads team-b/public-schema (exact grant)" 200 -H "Authorization: Bearer $DEV" "$(art team-b public-schema)"
expect "developer reads team-b/inventory-schema (no grant)" 403 -H "Authorization: Bearer $DEV" "$(art team-b inventory-schema)"
expect "developer reads shared/common-types (role grant)" 200 -H "Authorization: Bearer $DEV" "$(art shared common-types)"
expect "user reads shared/common-types (role grant)" 200 -H "Authorization: Bearer $USER" "$(art shared common-types)"
expect "user reads team-a/user-events (no grant)" 403 -H "Authorization: Bearer $USER" "$(art team-a user-events)"
expect "admin reads team-b/inventory-schema (admin role)" 200 -H "Authorization: Bearer $ADMIN" "$(art team-b inventory-schema)"
expect "developer creates group team-c (no group grant)" 403 -X POST -H "Authorization: Bearer $DEV" \
  -H 'Content-Type: application/json' -d '{"groupId": "team-c"}' "$API/registry/v3/groups"

echo "Search filtering:"
check_search() {
  local who=$1 tok=$2 expected=$3 actual
  actual=$(search_ids "$tok")
  if [[ "$actual" == "$expected" ]]; then
    printf '  ok    %-60s\n' "$who sees: $actual"
  else
    printf '  FAIL  %s sees: %s\n        expected: %s\n' "$who" "$actual" "$expected"
    failures=$((failures + 1))
  fi
}
check_search developer "$DEV" \
  "shared/common-types shared/error-schema team-a/order-schema team-a/user-events team-b/public-schema"
check_search user "$USER" "shared/common-types shared/error-schema"

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
