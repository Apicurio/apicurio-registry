#!/usr/bin/env bash
# Runs stock Confluent clients and tools against Apicurio Registry's Confluent-compatible API, and
# checks what they see: the wire format and schema IDs, how subjects map to artifacts and groups,
# references, compatibility, soft and permanent deletes, errors, and the v7 and v8 APIs.
# Usage: ./demo.sh            KEEP=1 ./demo.sh   (leave the stack running)
# Requires Docker (or Podman) with Compose v2, curl and jq. Starts from an empty stack and removes it
# at the end, so it is safe to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

R=http://localhost:${REGISTRY_PORT:-8081}
RG=http://localhost:${REGISTRY_GROUPS_PORT:-8082}
V7=$R/apis/ccompat/v7
V8=$R/apis/ccompat/v8
V3=$R/apis/registry/v3
# The Confluent tools run inside the Compose network
SR_V7=http://registry:8080/apis/ccompat/v7
SR_V8=http://registry:8080/apis/ccompat/v8
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# cc <method> <url> [body]: a ccompat call; prints "<status> <body>"
cc() {
  local out
  out=$(curl -s --max-time 30 -w '\n%{http_code}' -X "$1" "$2" -H 'Content-Type: application/vnd.schemaregistry.v1+json' \
    ${3:+-d "$3"} "${@:4}")
  echo "$(tail -1 <<< "$out") $(sed '$d' <<< "$out")"
}
schema() { jq -nc --arg s "$1" --arg t "${2:-AVRO}" '{schema: $s, schemaType: $t}'; }
# err <cc output>: "<status> <error_code>"
err() { local s b; read -r s b <<< "$1"; echo "$s $(jq -r .error_code <<< "$b")"; }
tools() { docker compose exec -T confluent "$@" 2> /dev/null | grep -v '^Processed' || true; }
produce() { # tool topic url schema-property...
  local tool=$1 topic=$2 url=$3
  shift 3
  docker compose exec -T confluent "$tool" --bootstrap-server kafka:9092 --topic "$topic" \
    --property schema.registry.url="$url" "$@" > /dev/null 2>&1
}
consume() { # tool topic url: the first record, with its schema ID
  tools "$1" --bootstrap-server kafka:9092 --topic "$2" --from-beginning --max-messages 1 --timeout-ms 30000 \
    --property schema.registry.url="$3" --property print.schema.ids=true --property schema.id.separator=' id=' | tail -1
}
wire_id() { # topic: the schema ID in the first record's header (magic byte 0, then 4 bytes)
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic "$1" \
    --from-beginning --max-messages 1 --timeout-ms 30000 2> /dev/null | head -c 5 | od -An -tu1 |
    awk 'NF == 5 {print "magic=" $1 " id=" ($2 * 16777216 + $3 * 65536 + $4 * 256 + $5)}'
}

trap '[ -n "${KEEP:-}" ] || docker compose down -v > /dev/null 2>&1 || true' EXIT

part "Start Kafka, two registries and the Confluent tools"
docker compose down -v > /dev/null 2>&1 || true
if ! docker compose up -d --wait > /tmp/ccompat-up.log 2>&1; then
  tail -20 /tmp/ccompat-up.log
  fail "the services did not start"
  exit 1
fi
ok "started"

ORDER_V1='{"type":"record","name":"OrderPlaced","namespace":"com.example","fields":[{"name":"id","type":"string"},{"name":"amount","type":"double"}]}'
ORDER_V2='{"type":"record","name":"OrderPlaced","namespace":"com.example","fields":[{"name":"id","type":"string"},{"name":"amount","type":"double"},{"name":"currency","type":"string","default":"EUR"}]}'
ORDER_BAD='{"type":"record","name":"OrderPlaced","namespace":"com.example","fields":[{"name":"id","type":"long"},{"name":"amount","type":"double"}]}'
ADDRESS='{"type":"record","name":"Address","namespace":"com.example","fields":[{"name":"city","type":"string"}]}'
SHIPMENT='{"type":"record","name":"Shipment","namespace":"com.example","fields":[{"name":"id","type":"string"},{"name":"to","type":"com.example.Address"}]}'
PAYMENT='syntax = "proto3"; package com.example; message Payment { string id = 1; double amount = 2; }'
INVOICE='{"type":"object","title":"Invoice","properties":{"id":{"type":"string"}},"required":["id"]}'

# ------------------------------------------------------------------------------ step 1
part "Step 1: Confluent serializers and deserializers, unchanged"
echo '{"id":"o-1","amount":12.5}' | produce kafka-avro-console-producer orders "$SR_V7" --property value.schema="$ORDER_V1"
check "kafka-avro-console-producer registers orders-value (v7 API), kafka-avro-console-consumer reads it (v8 API)" \
  '{"id":"o-1","amount":12.5} id=1' "$(consume kafka-avro-console-consumer orders "$SR_V8")"
CID=$(curl -s "$V3/groups/default/artifacts/orders-value/versions/1" | jq .contentId)
check "the record is in Confluent's wire format, and its schema ID is the version's contentId" "magic=0 id=$CID" "$(wire_id orders)"
echo '{"id":"p-1","amount":3.5}' | produce kafka-protobuf-console-producer payments "$SR_V7" --property value.schema="$PAYMENT"
check "Protobuf: kafka-protobuf-console-producer and -consumer" '{"id":"p-1","amount":3.5}' \
  "$(consume kafka-protobuf-console-consumer payments "$SR_V8" | sed 's/ id=.*//')"
echo '{"id":"i-1"}' | produce kafka-json-schema-console-producer invoices "$SR_V7" --property value.schema="$INVOICE"
check "JSON Schema: kafka-json-schema-console-producer and -consumer" '{"id":"i-1"}' \
  "$(consume kafka-json-schema-console-consumer invoices "$SR_V8" | sed 's/ id=.*//')"
check "each subject is an artifact of the matching type" "invoices-value JSON orders-value AVRO payments-value PROTOBUF" \
  "$(curl -s "$V3/search/artifacts?orderby=artifactId" | jq -r '[.artifacts[] | "\(.artifactId) \(.artifactType)"] | join(" ")')"

# ------------------------------------------------------------------------------ step 2
part "Step 2: subjects, artifacts and groups"
check "a subject is an artifact in the default group" "default" \
  "$(curl -s "$V3/search/artifacts?artifactId=orders-value" | jq -r '.artifacts[0].groupId // "default"')"
check "the same schema under another subject gets the same ID (contentId)" "200 {\"id\":$CID}" \
  "$(cc POST "$V7/subjects/orders-copy/versions" "$(schema "$ORDER_V1")")"
curl -s -o /dev/null -X POST "$V3/groups/billing/artifacts" -H 'Content-Type: application/json' \
  -d '{"artifactId": "refund-value", "artifactType": "AVRO", "firstVersion": {"content": {"content": "{\"type\": \"string\"}", "contentType": "application/json"}}}'
check "an artifact in another group is not a subject" "404 40401" \
  "$(err "$(cc GET "$V7/subjects/refund-value/versions")")"
check "  unless the client names the group (X-Registry-GroupId)" "200 [1]" \
  "$(cc GET "$V7/subjects/refund-value/versions" "" -H 'X-Registry-GroupId: billing')"
check "with group-concat enabled, subject orders:order-placed is artifact order-placed in group orders" \
  "200 orders/order-placed" \
  "$(cc POST "$RG/apis/ccompat/v7/subjects/orders:order-placed/versions" "$(schema "$ORDER_V1")" | cut -d' ' -f1) $(curl -s "$RG/apis/registry/v3/search/artifacts" | jq -r '.artifacts[0] | "\(.groupId)/\(.artifactId)"')"
check "  and every artifact is listed as group:artifact" '["orders:order-placed"]' \
  "$(curl -s "$RG/apis/ccompat/v7/subjects")"

# ------------------------------------------------------------------------------ step 3
part "Step 3: references"
cc POST "$V7/subjects/address/versions" "$(schema "$ADDRESS")" > /dev/null
SHIPMENT_ID=$(cc POST "$V7/subjects/shipments-value/versions" \
  "$(jq -nc --arg s "$SHIPMENT" '{schema: $s, references: [{name: "com.example.Address", subject: "address", version: 1}]}')" | cut -d' ' -f2- | jq .id)
check "a schema registered with a reference keeps it (ccompat)" '[{"name":"com.example.Address","subject":"address","version":1}]' \
  "$(curl -s "$V7/subjects/shipments-value/versions/1" | jq -c .references)"
check "  as an Apicurio artifact reference (v3)" '[{"artifactId":"address","version":"1","name":"com.example.Address"}]' \
  "$(curl -s "$V3/groups/default/artifacts/shipments-value/versions/1/references" | jq -c .)"
echo '{"id":"s-1","to":{"city":"Paris"}}' | produce kafka-avro-console-producer shipments "$SR_V7" \
  --property value.schema.id="$SHIPMENT_ID" --property auto.register.schemas=false
check "a Confluent producer uses it by ID, and the consumer resolves the reference" "{\"id\":\"s-1\",\"to\":{\"city\":\"Paris\"}} id=$SHIPMENT_ID" \
  "$(consume kafka-avro-console-consumer shipments "$SR_V8")"

# ------------------------------------------------------------------------------ step 4
part "Step 4: compatibility"
check "with no rule, the check endpoint applies BACKWARD, like Confluent" '200 {"is_compatible":false}' \
  "$(cc POST "$V7/compatibility/subjects/orders-value/versions/latest" "$(schema "$ORDER_BAD")")"
check "  but registration does not: with no rule, anything is accepted (ADR-0001, limitation 2)" 200 \
  "$(cc POST "$V7/subjects/orders-copy/versions" "$(schema "$ORDER_BAD")" | cut -d' ' -f1)"
check "PUT /config/orders-value BACKWARD" '200 {"compatibility":"BACKWARD","normalize":false}' \
  "$(cc PUT "$V7/config/orders-value" '{"compatibility": "BACKWARD"}')"
check "  is an Apicurio COMPATIBILITY rule" BACKWARD \
  "$(curl -s "$V3/groups/default/artifacts/orders-value/rules/COMPATIBILITY" | jq -r .config)"
check "an incompatible schema is now rejected: 409, error_code 409" "409 409" \
  "$(err "$(cc POST "$V7/subjects/orders-value/versions" "$(schema "$ORDER_BAD")")")"
check "  and the Confluent producer fails to send" "failed" \
  "$(echo '{"id":1,"amount":1}' | produce kafka-avro-console-producer orders "$SR_V7" --property value.schema="$ORDER_BAD" && echo sent || echo failed)"
check "a compatible schema is registered: version 2" "200 [1,2]" \
  "$(cc POST "$V7/subjects/orders-value/versions" "$(schema "$ORDER_V2")" > /dev/null; cc GET "$V7/subjects/orders-value/versions")"
curl -s -o /dev/null -X PUT "$V3/groups/default/artifacts/orders-value/rules/COMPATIBILITY" -H 'Content-Type: application/json' \
  -d '{"ruleType": "COMPATIBILITY", "config": "FULL"}'
check "a rule changed through the Apicurio API is the subject's config" '200 {"compatibilityLevel":"FULL"}' \
  "$(cc GET "$V7/config/orders-value")"

# ------------------------------------------------------------------------------ step 5
part "Step 5: soft and permanent deletes"
check "a soft delete of version 2" "200 2" "$(cc DELETE "$V7/subjects/orders-value/versions/2")"
check "  is the version's state DISABLED" DISABLED "$(curl -s "$V3/groups/default/artifacts/orders-value/versions/2" | jq -r .state)"
check "  hidden from the versions" "200 [1]" "$(cc GET "$V7/subjects/orders-value/versions")"
check "  unless deleted=true" "200 [1,2]" "$(cc GET "$V7/subjects/orders-value/versions?deleted=true")"
# The status of 40405, 40406 and 40407 is 409; Confluent's is 404 (#11125)
check "a second soft delete: error_code 40406" 40406 \
  "$(err "$(cc DELETE "$V7/subjects/orders-value/versions/2")" | cut -d' ' -f2)"
check "a permanent delete of a version not soft deleted first: error_code 40407" 40407 \
  "$(err "$(cc DELETE "$V7/subjects/orders-value/versions/1?permanent=true")" | cut -d' ' -f2)"
check "a permanent delete after the soft delete removes the version" "200 2 404" \
  "$(cc DELETE "$V7/subjects/orders-value/versions/2?permanent=true") $(curl -s -o /dev/null -w '%{http_code}' "$V3/groups/default/artifacts/orders-value/versions/2")"
check "a soft-deleted subject (both its versions) is hidden, but listed with deleted=true" "200 [1,2] false true" \
  "$(cc DELETE "$V7/subjects/orders-copy") $(curl -s "$V7/subjects" | jq 'index("orders-copy") != null') $(curl -s "$V7/subjects?deleted=true" | jq 'index("orders-copy") != null')"
check "  reading it: error_code 40401" 40401 "$(err "$(cc GET "$V7/subjects/orders-copy/versions")" | cut -d' ' -f2)"
check "  permanently deleted, it is gone from the registry" "404" \
  "$(cc DELETE "$V7/subjects/orders-copy?permanent=true" > /dev/null; curl -s -o /dev/null -w '%{http_code}' "$V3/groups/default/artifacts/orders-copy")"

# ------------------------------------------------------------------------------ step 6
part "Step 6: errors"
check "unknown subject: 404 40401" "404 40401" "$(err "$(cc GET "$V7/subjects/nope/versions")")"
check "unknown version: 404 40402" "404 40402" "$(err "$(cc GET "$V7/subjects/orders-value/versions/9")")"
check "unknown schema ID: 404 40403" "404 40403" "$(err "$(cc GET "$V7/schemas/ids/99999")")"
check "invalid schema: 422 42201" "422 42201" "$(err "$(cc POST "$V7/subjects/x/versions" '{"schema": "{not a schema"}')")"
check "invalid compatibility level: 422 42203" "422 42203" "$(err "$(cc PUT "$V7/config" '{"compatibility": "SOMETIMES"}')")"

# ------------------------------------------------------------------------------ step 7
part "Step 7: the v7 and v8 APIs"
for p in subjects schemas/types "schemas/ids/$CID/subjects" config "subjects/orders-value/versions"; do
  check "GET /$p: the same on v7 and v8" "$(curl -s "$V7/$p")" "$(curl -s "$V8/$p")"
done
check "GET a schema: v8 adds an empty references list" '{"v7":null,"v8":[]}' \
  "$(jq -nc --argjson a "$(curl -s "$V7/schemas/ids/$CID")" --argjson b "$(curl -s "$V8/schemas/ids/$CID")" '{v7: $a.references, v8: $b.references}')"
check "the mode is stored per subject" '200 {"mode":"READONLY"}' \
  "$(cc PUT "$V7/mode/payments-value" '{"mode": "READONLY"}' > /dev/null; cc GET "$V7/mode/payments-value")"
# Confluent rejects writes in READONLY (422, 42205); Apicurio stores the mode only (#11124)
check "  but not enforced today: a write in READONLY succeeds (should be 422, #11124)" 200 \
  "$(cc POST "$V7/subjects/payments-value/versions" "$(schema 'syntax = "proto3"; package com.example; message Payment { string id = 1; double amount = 2; string note = 3; }' PROTOBUF)" \
     | cut -d' ' -f1)"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
