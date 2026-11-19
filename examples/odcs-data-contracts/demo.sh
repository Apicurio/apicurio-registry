#!/usr/bin/env bash
# Checks every step of GUIDE.md against the running example: the contract, its projection onto the
# schema, rule execution, global rules, the Kafka producer and consumer enforcing the rules, the
# lifecycle, search and audit, schema migration (in the registry and in the consumer), updating and
# deleting the contract.
# Usage: ./demo.sh               (after `docker compose up -d`; requires curl and jq)
#        SKIP_KAFKA=1 ./demo.sh  without the Java steps (9 and 12); they need JDK 17+ (build.sh runs
#                                when the Java demo is not compiled yet)
#        SETUP_ONLY=1 ./demo.sh  only create the state the guide starts from (step 2)
# It ends by restoring that state, so the guide and the UI can be followed afterwards.
# Safe to run again: it deletes the "orders" group, the global contract ruleset and the Kafka topic,
# then starts over. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:${REGISTRY_PORT:-8081}/apis}
R=$API/registry/v3
ORDER=$R/groups/orders/artifacts/OrderEvent
export REGISTRY_URL=$R KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-localhost:${KAFKA_PORT:-9092}}
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# json <method> <url> [body] [content type]: the response body
json() {
  local args=(-s --max-time 30 -X "$1" "$2")
  [ -n "${3:-}" ] && args+=(-H "Content-Type: ${4:-application/json}" --data-binary "$3")
  curl "${args[@]}"
}
code() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$1" "$2" ${3:+-H 'Content-Type: application/json' --data-binary "$3"}; }
# execute <record> [mode]: the result of running the latest OrderEvent version's rules on a record
execute() { json POST "$ORDER/versions/branch=latest/contract/execute" "{\"mode\":\"${2:-WRITE}\",\"record\":$1}"; }
passed() { execute "$@" | jq -r .passed; }
# java <args>: the Kafka producer and consumer (ContractSerdesDemo), output without Maven noise
java_demo() {
  local out
  out=$(../../mvnw -q -f pom.xml exec:java ${1:+-Dexec.args="$1"} 2>&1) || { echo "$out" | tail -20 >&2; return 1; }
  grep -E '^(sent|rejected|consumed|[0-9]+ message)' <<< "$out" || true
}
# eventually <seconds> <expected> <command...>: the command's output once it is expected, or the last one
eventually() {
  local deadline=$((SECONDS + $1)) expected=$2 actual=""
  shift 2
  while :; do
    actual=$("$@" 2> /dev/null || true)
    [[ "$actual" == "$expected" || "$SECONDS" -ge "$deadline" ]] && break
    sleep 1
  done
  echo "$actual"
}
# setup: the state the guide starts from (step 2): OrderEvent version 1 and the contract
setup() {
  json DELETE "$R/groups/orders" > /dev/null
  json DELETE "$R/admin/contracts/ruleset" > /dev/null
  code POST "$R/groups/orders/artifacts" "$(jq -nc --rawfile s schemas/order-event-v1.avsc \
    '{artifactId: "OrderEvent", artifactType: "AVRO", firstVersion: {version: "1", content: {content: $s, contentType: "application/json"}}}')"
  echo
  curl -s --max-time 30 -X POST "$R/groups/orders/contracts" -H 'Content-Type: application/x-yaml' \
    --data-binary @contracts/orders-contract.yaml
}

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

part "Before you start"
wait_for Registry "$R/system/info"
if [ -n "${SETUP_ONLY:-}" ]; then
  setup > /dev/null
  echo "OrderEvent version 1 and the contract orders-contract are registered."
  exit 0
fi
if [ -z "${SKIP_KAFKA:-}" ] && [ ! -d target/classes/io ]; then
  ./build.sh > /tmp/odcs-build.log 2>&1 || { tail -20 /tmp/odcs-build.log; echo "build.sh failed" >&2; exit 1; }
fi
json DELETE "$R/groups/orders" > /dev/null
json DELETE "$R/admin/contracts/ruleset" > /dev/null
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 \
  --delete --topic orders > /dev/null 2>&1 || true
# Kafka deletes a topic asynchronously: wait until it is gone, or step 9 would write to the old one
deadline=$((SECONDS + 60))
while docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --list 2> /dev/null \
  | grep -qx orders; do
  [ "$SECONDS" -ge "$deadline" ] && { fail "the orders topic is deleted"; break; }
  sleep 1
done
ok "the example data is reset"

# ------------------------------------------------------------------------------ step 2
part "Step 2: the schema and the contract"
check "register orders/OrderEvent version 1" 200 \
  "$(code POST "$R/groups/orders/artifacts" "$(jq -nc --rawfile s schemas/order-event-v1.avsc \
    '{artifactId: "OrderEvent", artifactType: "AVRO", firstVersion: {version: "1", content: {content: $s, contentType: "application/json"}}}')")"
result=$(curl -s --max-time 30 -X POST "$R/groups/orders/contracts" -H 'Content-Type: application/x-yaml' --data-binary @contracts/orders-contract.yaml)
check "submit contracts/orders-contract.yaml: stored as the artifact orders/orders-contract" ODCS_CONTRACT \
  "$(json GET "$R/groups/orders/artifacts/orders-contract" | jq -r .artifactType)"
check "  projected with no warnings" 0 "$(jq '.projection.warnings | length' <<< "$result")"

# ------------------------------------------------------------------------------ step 5
part "Step 5: what the registry did with it"
labels=$(json GET "$ORDER" | jq -c .labels)
check "owner team label" orders-team "$(jq -r '."contract.orders-contract.owner.team"' <<< "$labels")"
check "status label (ODCS active -> STABLE)" STABLE "$(jq -r '."contract.orders-contract.status"' <<< "$labels")"
check "classification label" CONFIDENTIAL "$(jq -r '."contract.orders-contract.classification"' <<< "$labels")"
check "a CEL rule projected from quality.accuracy" "odcs:orders-contract:positive-amount totalAmount > 0" \
  "$(json GET "$ORDER/contract/ruleset" | jq -r '.domainRules[0] | "\(.name) \(.expr)"')"
check "field tags on version 1 (from the schema, and from the contract)" \
  "field-tag.customerEmail|PII field-tag.orders-contract:customerEmail|EMAIL field-tag.orders-contract:customerEmail|PII" \
  "$(json GET "$ORDER/versions/1" | jq -r '.labels | keys | join(" ")')"

# ------------------------------------------------------------------------------ step 6
part "Step 6: read it back"
check "metadata" "STABLE orders-team CONFIDENTIAL" \
  "$(json GET "$ORDER/contract/metadata" | jq -r '"\(.status) \(.ownerTeam) \(.classification)"')"
check "export as an ODCS document" "v3.1.0 orders-contract" \
  "$(json GET "$ORDER/contract/export" | grep -E '^(apiVersion|id):' | awk '{print $2}' | tr -d '"' | tr '\n' ' ' | sed 's/ $//')"
check "the submitted YAML is returned unchanged" yes \
  "$(diff -q <(json GET "$R/groups/orders/contracts/orders-contract") contracts/orders-contract.yaml > /dev/null && echo yes || echo no)"

# ------------------------------------------------------------------------------ step 7
part "Step 7: check records against the contract"
check "a valid order passes" true "$(passed '{"orderId":"A1","customerEmail":"a@example.com","totalAmount":99.99}')"
check "a negative totalAmount fails" false "$(passed '{"orderId":"A2","customerEmail":"b@example.com","totalAmount":-5}')"
check "the WRITE rule does not run in READ mode" "true 0" \
  "$(execute '{"orderId":"A2","totalAmount":-5}' READ | jq -r '"\(.passed) \(.executedRules)"')"

# ------------------------------------------------------------------------------ step 8
part "Step 8: global rules, and overriding them"
json PUT "$R/admin/contracts/ruleset" '{"domainRules":[{"name":"order-id-present","kind":"CONDITION","type":"CEL","mode":"WRITE","expr":"size(orderId) > 0","onFailure":"ERROR"}],"migrationRules":[]}' > /dev/null
check "a global rule applies to every schema: an empty orderId fails" "false order-id-present" \
  "$(execute '{"orderId":"","totalAmount":10}' | jq -r '"\(.passed) \(.violations[0].ruleName)"')"
check "  both rules run on a valid order" "true 2" "$(execute '{"orderId":"A3","totalAmount":10}' | jq -r '"\(.passed) \(.executedRules)"')"
json PUT "$ORDER/versions/1/contract/ruleset" '{"domainRules":[{"name":"order-id-present","kind":"CONDITION","type":"CEL","mode":"WRITE","expr":"true","onFailure":"ERROR"}],"migrationRules":[]}' > /dev/null
check "a version rule with the same name overrides it" true "$(eventually 20 true passed '{"orderId":"","totalAmount":10}')"
json DELETE "$ORDER/versions/1/contract/ruleset" > /dev/null

# ------------------------------------------------------------------------------ step 9
if [ -z "${SKIP_KAFKA:-}" ]; then
  part "Step 9: the contract in a Kafka producer and consumer"
  out=$(java_demo) || fail "the producer and consumer ran"
  sed 's/^/  info  /' <<< "$out"
  check "the serializer sends the valid order, rejects the two invalid ones" "1 2" \
    "$(grep -c '^sent' <<< "$out" || true) $(grep -c '^rejected' <<< "$out" || true)"
  check "  only the valid order reaches the topic" "1 message(s) on topic 'orders'" "$(grep 'message(s)' <<< "$out")"
fi
json DELETE "$R/admin/contracts/ruleset" > /dev/null

# ------------------------------------------------------------------------------ step 10
part "Step 10: lifecycle"
check "a quality score" yes "$(json GET "$ORDER/contract/quality?contractId=orders-contract" | jq -r 'if .overall > 0 then "yes" else "no" end')"
promote() { json POST "$ORDER/contract/promote" "{\"contractId\":\"orders-contract\",\"targetStage\":\"$1\"}" | jq -r '.stage // .status'; }
check "promote to DEV; PROD directly is refused (400: no skipping); then STAGE, then PROD" "DEV 400 STAGE PROD" \
  "$(promote DEV) $(promote PROD) $(promote STAGE) $(promote PROD)"
check "deprecate" DEPRECATED "$(json POST "$ORDER/contract/status" '{"status":"DEPRECATED"}' | jq -r .status)"
check "  a status never moves back" 409 "$(json POST "$ORDER/contract/status" '{"status":"STABLE"}' | jq -r .status)"

# ------------------------------------------------------------------------------ step 11
part "Step 11: find contracts, and see what changed"
check "search by owner team" "1 orders/OrderEvent" \
  "$(json GET "$R/search/contracts?ownerTeam=orders-team" | jq -r '"\(.count) \([.artifacts[] | "\(.groupId)/\(.artifactId)"] | join(","))"')"
check "  by status" "0 1" \
  "$(json GET "$R/search/contracts?status=STABLE" | jq .count) $(json GET "$R/search/contracts?status=DEPRECATED" | jq .count)"
check "the audit log records every change, newest first" "STATUS_CHANGED PROMOTED PROMOTED PROMOTED" \
  "$(json GET "$ORDER/contract/audit" | jq -r '[.[0:4][].action] | join(" ")')"
check "  back to the projection" CONTRACT_PROJECTED "$(json GET "$ORDER/contract/audit" | jq -r '.[-1].action')"

# ------------------------------------------------------------------------------ step 12
part "Step 12: evolve the schema, and migrate records"
check "register version 2 (adds currency)" 2 \
  "$(json POST "$ORDER/versions" "$(jq -nc --rawfile s schemas/order-event-v2.avsc '{version: "2", content: {content: $s, contentType: "application/json"}}')" | jq -r .version)"
check "a JSONata UPGRADE rule on version 2" default-currency \
  "$(json PUT "$ORDER/versions/2/contract/ruleset" '{"domainRules":[],"migrationRules":[{"name":"default-currency","kind":"TRANSFORM","type":"JSONATA","mode":"UPGRADE","expr":"$merge([$, {\"currency\": \"USD\"}])","onFailure":"ERROR"}]}' | jq -r '.migrationRules[0].name')"
check "the registry migrates a version 1 record to version 2" "true USD" \
  "$(json POST "$ORDER/contract/migrate" '{"fromVersion":"1","toVersion":"2","record":{"orderId":"A1","customerEmail":"a@example.com","totalAmount":99.99}}' | jq -r '"\(.passed) \(.transformedRecord.currency)"')"
if [ -z "${SKIP_KAFKA:-}" ]; then
  out=$(java_demo migrate) || fail "the migrating consumer ran"
  sed 's/^/  info  /' <<< "$out"
  check "  a consumer on version 2 reads the record of step 9" "1 message(s) on topic 'orders'" "$(grep 'message(s)' <<< "$out")"
  check "  the record arrives migrated, with currency USD" USD \
    "$(grep '^consumed' <<< "$out" | head -1 | grep -oE '"currency": "[A-Z]+"' | grep -oE '[A-Z]{3}' || echo none)"
fi

# ------------------------------------------------------------------------------ step 13
part "Step 13: update the contract"
v11=$(sed -e 's/version: 1.0.0/version: 1.1.0/' -e 's/totalAmount > 0/totalAmount >= 1/' contracts/orders-contract.yaml)
result=$(json PUT "$R/groups/orders/contracts/orders-contract" "$v11" application/x-yaml)
check "version 1.1.0 replaces the rule" "1.1.0 totalAmount >= 1" \
  "$(jq -r .version <<< "$result") $(json GET "$ORDER/contract/ruleset" | jq -r '.domainRules[0].expr')"
check "  and warns that the status cannot move back to STABLE" 1 \
  "$(jq '[.projection.warnings[] | select(test("cannot move back"))] | length' <<< "$result")"
check "the contract artifact has both versions" "1.0.0,1.1.0" \
  "$(json GET "$R/groups/orders/artifacts/orders-contract/versions" | jq -r '[.versions[].version] | join(",")')"
check "state set through the API is kept" "DEPRECATED PROD" \
  "$(json GET "$ORDER/contract/metadata" | jq -r '"\(.status) \(.stage)"')"
check "submitting a version again with POST is refused" 409 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$R/groups/orders/contracts" -H 'Content-Type: application/x-yaml' --data-binary @contracts/orders-contract.yaml)"

# ------------------------------------------------------------------------------ step 14
part "Step 14: delete the contract"
check "delete" 204 "$(code DELETE "$R/groups/orders/contracts/orders-contract")"
check "everything it projected is removed: contracts, labels, rules" "[] 0 0" \
  "$(json GET "$R/groups/orders/contracts" | jq -c .) $(json GET "$ORDER" | jq '.labels | length') $(json GET "$ORDER/contract/ruleset" | jq '.domainRules | length')"
check "  and the removal is audited" CONTRACT_REMOVED "$(json GET "$ORDER/contract/audit" | jq -r '.[0].action')"

setup > /dev/null
ok "restored the state the guide starts from (version 1 and the contract)"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
