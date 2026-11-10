#!/usr/bin/env bash
# Creates the sample data and verifies the ODCS data contract example against the running registry.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq)
#
# Safe to run again: it deletes the "orders" group (with its contract rules and audit log) and the
# global contract ruleset, then starts over.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:8081/apis}
R=$API/registry/v3
ORDER=$R/groups/orders/artifacts/OrderEvent
failures=0

# expect <description> <expected> <actual>
expect() {
  if [[ "$3" == "$2" ]]; then
    printf '  ok    %-62s %s\n' "$1" "$3"
  else
    printf '  FAIL  %-62s %s (expected %s)\n' "$1" "$3" "$2"
    failures=$((failures + 1))
  fi
}

# json <method> <url> [body]: the response body
json() {
  local args=(-s -X "$1" "$2")
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' -d "$3")
  curl "${args[@]}"
}

# execute <record>: passed flag of a WRITE execution against the latest OrderEvent version
execute() {
  json POST "$ORDER/versions/branch=latest/contract/execute" "{\"mode\":\"WRITE\",\"record\":$1}" | jq -r .passed
}

# wait_for <name> <url>: polls every 2s, gives up after WAIT_TIMEOUT seconds (default 180)
wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

echo "Waiting for the Registry..."
wait_for Registry "$R/system/info"

echo "Resetting the example data..."
curl -s -o /dev/null -X DELETE "$R/groups/orders"
curl -s -o /dev/null -X DELETE "$R/admin/contracts/ruleset"

echo "Registering schema orders/OrderEvent (version 1)..."
jq -n --rawfile s schemas/order-event-v1.avsc \
  '{artifactId: "OrderEvent", artifactType: "AVRO", firstVersion: {version: "1", content: {content: $s, contentType: "application/json"}}}' \
  | curl -sf -o /dev/null -X POST "$R/groups/orders/artifacts" -H 'Content-Type: application/json' -d @-

echo "Submitting contracts/orders-contract.yaml..."
result=$(curl -sf -X POST "$R/groups/orders/contracts" -H 'Content-Type: application/x-yaml' \
  --data-binary @contracts/orders-contract.yaml)

echo "Contract artifact:"
expect "contract stored as artifact orders/orders-contract" ODCS_CONTRACT \
  "$(json GET "$R/groups/orders/artifacts/orders-contract" | jq -r .artifactType)"
expect "projection reported no warnings" 0 "$(echo "$result" | jq '.projection.warnings | length')"
expect "re-submitting the same contract version is refused" 409 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$R/groups/orders/contracts" \
    -H 'Content-Type: application/x-yaml' --data-binary @contracts/orders-contract.yaml)"

echo "Projection onto OrderEvent:"
labels=$(json GET "$ORDER" | jq -c .labels)
expect "owner team label" orders-team "$(echo "$labels" | jq -r '."contract.orders-contract.owner.team"')"
expect "status label (ODCS active -> STABLE)" STABLE "$(echo "$labels" | jq -r '."contract.orders-contract.status"')"
expect "classification label" CONFIDENTIAL "$(echo "$labels" | jq -r '."contract.orders-contract.classification"')"
expect "rule projected from quality.accuracy" "odcs:orders-contract:positive-amount totalAmount > 0" \
  "$(json GET "$ORDER/contract/ruleset" | jq -r '.domainRules[0] | "\(.name) \(.expr)"')"
expect "field tags on version 1 (from the schema, and from the contract)" \
  "field-tag.customerEmail|PII field-tag.orders-contract:customerEmail|EMAIL field-tag.orders-contract:customerEmail|PII" \
  "$(json GET "$ORDER/versions/1" | jq -r '.labels | keys | join(" ")')"
expect "metadata view" "STABLE orders-team" \
  "$(json GET "$ORDER/contract/metadata" | jq -r '"\(.status) \(.ownerTeam)"')"

echo "Rule execution:"
expect "valid record passes" true "$(execute '{"orderId":"A1","customerEmail":"a@example.com","totalAmount":99.99}')"
expect "negative totalAmount fails" false "$(execute '{"orderId":"A2","customerEmail":"b@example.com","totalAmount":-5}')"

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
