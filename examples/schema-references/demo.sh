#!/usr/bin/env bash
# Registers schemas with references, then checks how the registry stores, lists, resolves and hashes
# them, against the running example. Usage: ./demo.sh   (after `docker compose up -d`; requires
# curl, jq and JDK 21 or later). Prints one ok/FAIL line per check, ends with "All checks passed."
# and exits non-zero on failure. Safe to run again: it starts by deleting the example's data.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:8081/apis}
V3="$API/registry/v3"
KAFKA=${KAFKA:-localhost:9092}
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# get <path>; status <method> <path> [body]; post <path> <body> (prints the body)
get() { curl -s --max-time 30 "$V3$1"; }
status() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$1" "$V3$2" ${3:+-H 'Content-Type: application/json' -d "$3"}; }
post() { curl -s --max-time 30 -X POST "$V3$1" -H 'Content-Type: application/json' -d "$2"; }

# refs <group> <artifact> [version] [refType]: "artifactId@version" of each reference, sorted
refs() {
  get "/groups/$1/artifacts/$2/versions/${3:-1}/references?refType=${4:-OUTBOUND}" \
    | jq -r '[.[] | "\(.groupId)/\(.artifactId)@\(.version)"] | sort | join(",")'
}

# json_artifact <group> <id> <content> [references json]: create a JSON Schema artifact
json_artifact() {
  post "/groups/$1/artifacts" "$(jq -n --arg a "$2" --arg c "$3" --argjson r "${4:-[]}" \
    '{artifactId: $a, artifactType: "JSON", firstVersion: {content: {content: $c, contentType: "application/json", references: $r}}}')"
}

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf --max-time 5 "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

# register <execution>: runs one Maven plugin execution from registration/pom.xml
register() {
  local out
  if ! out=$(../../mvnw -q -f registration "io.apicurio:apicurio-registry-maven-plugin:$version:register@$1" \
      -Dregistry.url="$V3" 2>&1); then
    echo "$out" | tail -20
    fail "the Maven plugin execution $1 failed"
    return 1
  fi
}

echo "Waiting for Registry..."
wait_for Registry "$V3/system/info"

if [ ! -d serdes/target/classes ] || [ ! -d ../../utils/maven-plugin/target/classes ]; then
  echo "Building the Maven plugin and the serdes client (first run only, a few minutes)..."
  log=$(mktemp)
  ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
fi
version=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' registration/pom.xml | head -1)

echo "Resetting the example data..."
for g in $(get "/groups?limit=100" | jq -r '.groups[].groupId'); do
  if [ "$g" = default ]; then status DELETE /groups/default/artifacts > /dev/null; else status DELETE "/groups/$g" > /dev/null; fi
done
status DELETE /admin/rules > /dev/null
docker compose exec -T kafka sh -c 'for t in invoices shipments; do /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --delete --if-exists --topic $t; done' > /dev/null 2>&1 || true

# ------------------------------------------------------------------------------ step 4
part "Step 4: references spelled out (Maven plugin, nested <references>)"
register avro-manual
check "orders-manual/Order references Money and Address" "common-manual/Address@1,common-manual/Money@1" "$(refs orders-manual Order)"
check "Address references Country, one level down" "common-manual/Country@1" "$(refs common-manual Address)"
check "a reference is named as the parent refers to it (Avro: the type's full name)" \
  "com.example.common.Address,com.example.common.Money" \
  "$(get /groups/orders-manual/artifacts/Order/versions/1/references | jq -r '[.[].name] | sort | join(",")')"
check "INBOUND: who references Address" "orders-manual/Order@1" "$(refs common-manual Address 1 INBOUND)"
graph=$(get /groups/orders-manual/artifacts/Order/versions/1/references/graph)
check "the reference graph: 4 nodes, 3 edges, depth 2, no cycle" "4/3/2/false" \
  "$(jq -r '.metadata | "\(.totalNodes)/\(.totalEdges)/\(.maxDepth)/\(.hasCycles)"' <<< "$graph")"

# ------------------------------------------------------------------------------ step 5
part "Step 5: references found by the Maven plugin (autoRefs)"
register avro-auto
check "Avro: Order's references, in groups named after their namespace" \
  "com.example.common/Address@1,com.example.common/Money@1" "$(refs com.example.orders Order)"
check "Avro: Country was found inside Address" "com.example.common/Country@1" "$(refs com.example.common Address)"
register json-auto
check "JSON Schema: customer -> address.json (one reference, used twice)" "customers/address.json@1" "$(refs customers customer)"
check "JSON Schema: address.json -> country.json" "customers/country.json@1" "$(refs customers address.json)"
register asyncapi-auto
check "AsyncAPI: the document references its two Avro payloads" \
  "events/io.example.api.dtos.CustomerDeletedEvent@1,events/io.example.api.dtos.CustomerEvent@1" "$(refs events customer-events)"
check "AsyncAPI: the Avro payload references its own types" "AVRO" \
  "$(get /groups/events/artifacts/io.example.api.dtos.Address | jq -r .artifactType)"

# ------------------------------------------------------------------------------ step 6
part "Step 6: reading content with its references: PRESERVE, DEREFERENCE, REWRITE"
order=/groups/orders-manual/artifacts/Order/versions/1/content
check "PRESERVE (default): the field type is the reference name" "com.example.common.Money" \
  "$(get "$order" | jq -r '.fields[1].type')"
check "DEREFERENCE: Money is inlined as a record" "record Money" \
  "$(get "$order?references=DEREFERENCE" | jq -r '.fields[1].type | "\(.type) \(.name)"')"
check "DEREFERENCE goes all the way down: Country inside Address" "enum" \
  "$(get "$order?references=DEREFERENCE" | jq -r '.fields[2].type.fields[2].type.type')"
check "REWRITE is not supported for Avro" 400 "$(status GET "$order?references=REWRITE")"
customer=/groups/customers/artifacts/customer/versions/1/content
check "REWRITE for JSON Schema: \$ref becomes the registry URL" \
  "$V3/groups/customers/artifacts/address.json/versions/1/content?references=REWRITE" \
  "$(get "$customer?references=REWRITE" | jq -r '.properties.billing["$ref"]')"
check "DEREFERENCE for JSON Schema: billing is the address object" "object" \
  "$(get "$customer?references=DEREFERENCE" | jq -r '.properties.billing.type')"
gid=$(get /groups/orders-manual/artifacts/Order/versions/1 | jq -r .globalId)
check "by global ID, with the same parameter" "record" \
  "$(get "/ids/globalIds/$gid?references=DEREFERENCE" | jq -r '.fields[1].type.type')"

# ------------------------------------------------------------------------------ step 7
part "Step 7: references are part of the content's identity"
manual=$(get /groups/orders-manual/artifacts/Order/versions/1 | jq -r .contentId)
auto=$(get /groups/com.example.orders/artifacts/Order/versions/1 | jq -r .contentId)
check "the same Order.avsc text with other references has another content ID" yes \
  "$([ "$manual" != "$auto" ] && echo yes || echo no)"
check "the references are read by content ID too" "common-manual/Address@1,common-manual/Money@1" \
  "$(get "/ids/contentIds/$manual/references" | jq -r '[.[] | "\(.groupId)/\(.artifactId)@\(.version)"] | sort | join(",")')"
money_hash=$(shasum -a 256 registration/schemas/avro/Money.avsc | cut -d' ' -f1)
check "a schema without references is found by the SHA-256 of its text" 200 "$(status GET "/ids/contentHashes/$money_hash")"

# ------------------------------------------------------------------------------ step 8
part "Step 8: canonical matching"
reformatted=$(jq -c . registration/schemas/avro/Money.avsc)
money_body=$(jq -n --arg c "$reformatted" '{artifactId: "Money", artifactType: "AVRO", firstVersion: {content: {content: $c, contentType: "application/json"}}}')
check "the same schema, reformatted, with canonical=true: version 1 is found" 1 \
  "$(post "/groups/common-manual/artifacts?ifExists=FIND_OR_CREATE_VERSION&canonical=true" "$money_body" | jq -r .version.version)"
check "without canonical, the reformatted text is a new version" 2 \
  "$(post "/groups/common-manual/artifacts?ifExists=FIND_OR_CREATE_VERSION" "$money_body" | jq -r .version.version)"

# ------------------------------------------------------------------------------ step 9
part "Step 9: integrity rules for references"
status POST /groups '{"groupId": "checked"}' > /dev/null
check "group checked gets INTEGRITY=FULL" 204 "$(status POST /groups/checked/rules '{"ruleType": "INTEGRITY", "config": "FULL"}')"
dangling() { echo "[{\"name\": \"b.json\", \"groupId\": \"$1\", \"artifactId\": \"b\", \"version\": \"1\"}]"; }
a_schema='{"$id": "a.json", "type": "object", "properties": {"b": {"$ref": "b.json"}}}'
check "REFS_EXIST: a reference to an artifact that does not exist is rejected" "400 INTEGRITY" \
  "$(json_artifact checked a "$a_schema" "$(dangling checked)" | jq -r '"\(.status) \(.ruleViolation.ruleType)"')"
check "ALL_REFS_MAPPED: a \$ref with no mapping is rejected" "400 INTEGRITY" \
  "$(json_artifact checked a "$a_schema" | jq -r '"\(.status) \(.ruleViolation.ruleType)"')"
# Without a rule, the same dangling reference is stored; a cycle can then be closed
json_artifact unchecked a "$a_schema" "$(dangling unchecked)" > /dev/null
json_artifact unchecked b '{"$id": "b.json", "type": "object", "properties": {"a": {"$ref": "a.json"}}}' \
  '[{"name": "a.json", "groupId": "unchecked", "artifactId": "a", "version": "1"}]' > /dev/null
check "without a rule, a -> b -> a is stored; the graph reports the cycle" true \
  "$(get /groups/unchecked/artifacts/a/versions/1/references/graph | jq -r .metadata.hasCycles)"

# ------------------------------------------------------------------------------ step 10
part "Step 10: disabling and deleting a referenced artifact"
status PUT /groups/common-manual/artifacts/Country/versions/1/state '{"state": "DISABLED"}' > /dev/null
check "a DISABLED target still resolves" "enum" \
  "$(get "$order?references=DEREFERENCE" | jq -r '.fields[2].type.fields[2].type.type')"
check "deleting a referenced artifact is allowed (#11029)" 204 "$(status DELETE /groups/common-manual/artifacts/Money)"
check "the referrer still lists the reference" "common-manual/Address@1,common-manual/Money@1" "$(refs orders-manual Order)"
check "but it can no longer be dereferenced (#11029)" 500 "$(status GET "$order?references=DEREFERENCE")"

# ------------------------------------------------------------------------------ step 11
part "Step 11: serdes register nested types as references"
if out=$(../../mvnw -q -f serdes exec:java -Dregistry.url="$V3" -Dkafka.bootstrap="$KAFKA" 2>&1); then
  grep -E '^  ok ' <<< "$out"
  ok "the serdes client's checks passed"
else
  grep -E '^  (ok|FAIL) ' <<< "$out" || tail -30 <<< "$out"
  fail "the serdes client failed"
fi
check "Avro: the serializer registered Invoice with its nested records as references" \
  "default/com.example.billing.Customer@1,default/com.example.common.Money@1" \
  "$(get /groups/default/artifacts/invoices-value/versions/1/references | jq -r '[.[] | "\(.groupId // "default")/\(.artifactId)@\(.version)"] | sort | join(",")')"
check "Protobuf: the serializer registered the imported money.proto as a reference" "money.proto" \
  "$(get /groups/default/artifacts/shipments-value/versions/1/references | jq -r '[.[].name] | join(",")')"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
