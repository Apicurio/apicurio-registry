#!/usr/bin/env bash
# Runs the serdes tour against the running example and checks what it left in Kafka and the registry.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl, jq and JDK 21 or later)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
# Safe to run again: it starts by deleting the example's topics, connectors and artifacts.
# SKIP_TOUR_CHECKS=1 ./demo.sh only resets the data (for running single tour steps, GUIDE.md step 3).
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:8081/apis}
V3="$API/registry/v3"
KAFKA=${KAFKA:-localhost:9092}
CONNECT=${CONNECT:-http://localhost:8083}
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }

# check <description> <expected> <actual>
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# get <path>: a registry v3 response body
get() { curl -s --max-time 30 "$V3$1"; }

# kafka <script> <args...>: runs a Kafka CLI tool in the broker container
kafka() { docker compose exec -T kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:29092 "${@:2}"; }

# first_record <topic> [console consumer args...]: the first record of a topic, as printed
first_record() {
  kafka kafka-console-consumer.sh --topic "$1" --from-beginning --max-messages 1 --timeout-ms 20000 \
    "${@:2}" 2>/dev/null
}

# wait_for <name> <url>: polls every 2s, gives up after WAIT_TIMEOUT seconds (default 180)
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

# mvn <args>: Maven on the tour module, quiet
mvn() { ../../mvnw -q -f tour "$@"; }

echo "Waiting for Registry and Kafka..."
wait_for Registry "$V3/system/info"
kafka kafka-topics.sh --list > /dev/null

if [ ! -d tour/target/classes ] || [ ! -d connect-plugin/target/plugins/apicurio ]; then
  echo "Building the tour and the Connect plugin (first run only, a few minutes)..."
  log=$(mktemp)
  ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
fi

echo "Resetting the example data..."
if curl -sf --max-time 5 "$CONNECT/connectors" > /dev/null; then
  for c in $(curl -s "$CONNECT/connectors" | jq -r '.[]'); do curl -s -X DELETE "$CONNECT/connectors/$c" > /dev/null; done
  docker compose exec -T connect sh -c 'rm -f /tmp/orders-sink.txt /tmp/lines-*.txt'
fi
example_topics() { kafka kafka-topics.sh --list | grep -vE '^(__|connect-(configs|offsets|status)$)' || true; }
for t in $(example_topics); do
  kafka kafka-topics.sh --delete --topic "$t"
done
# Deletion is asynchronous: a producer recreating a topic that is still being deleted times out
for _ in $(seq 1 30); do
  [ -z "$(example_topics)" ] && break
  sleep 1
done
for g in $(get "/groups?limit=100" | jq -r '.groups[].groupId'); do
  if [ "$g" = default ]; then
    curl -s -o /dev/null -X DELETE "$V3/groups/default/artifacts"
  else
    curl -s -o /dev/null -X DELETE "$V3/groups/$g"
  fi
done

# ------------------------------------------------------------------------------ step 12 (setup)
echo "Registering greetings/greeting ahead of time with the Maven plugin (used in step 12)..."
version=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' tour/pom.xml | head -1)
if ! out=$(mvn "io.apicurio:apicurio-registry-maven-plugin:$version:register" -Dregistry.url="$V3" 2>&1); then
  echo "$out" | tail -20
  echo "The Maven plugin could not register greeting.avsc" >&2
  exit 1
fi

if [ -n "${SKIP_TOUR_CHECKS:-}" ]; then
  echo "Reset done (SKIP_TOUR_CHECKS is set): run single tour steps as in GUIDE.md step 3."
  exit 0
fi

# ------------------------------------------------------------------------------ steps 4 to 12
part "Steps 4 to 12: the tour (Kafka producers and consumers using the serdes)"
if out=$(mvn exec:java -Dregistry.url="$V3" -Dkafka.bootstrap="$KAFKA" 2>&1); then
  grep -E '^(Tour step|  (ok|info) )' <<< "$out"
  ok "every tour check passed"
else
  grep -E '^(Tour step|  (ok|FAIL|info) )' <<< "$out" || tail -30 <<< "$out"
  fail "the tour failed"
fi

part "What the tour left in the registry"
check "step 4: the serializer registered default/orders-value" AVRO "$(get /groups/default/artifacts/orders-value | jq -r .artifactType)"
check "step 6: default/shipments-value is a JSON Schema" JSON "$(get /groups/default/artifacts/shipments-value | jq -r .artifactType)"
check "step 7: default/payments-value is Protobuf" PROTOBUF "$(get /groups/default/artifacts/payments-value | jq -r .artifactType)"
check "step 9: RecordIdStrategy names artifacts after the records" "OrderCancelled,OrderPlaced" \
  "$(get /groups/com.example.orders/artifacts | jq -r '[.artifacts[].artifactId] | sort | join(",")')"
check "step 10: orders-value has 2 versions (the rejected one was not added)" "1,2" \
  "$(get /groups/default/artifacts/orders-value/versions | jq -r '[.versions[].version] | join(",")')"
check "step 11: the custom strategy registered inventory/stock-levels" AVRO "$(get /groups/inventory/artifacts/stock-levels | jq -r .artifactType)"
check "step 12: greetings/greeting still has only the Maven plugin's version" 1 "$(get /groups/greetings/artifacts/greeting/versions | jq -r .count)"

part "Step 5: the wire format"
content_id=$(get "/groups/default/artifacts/orders-value/versions/1" | jq -r .contentId)
bytes=$(first_record orders | head -c 5 | od -An -tx1 | tr -d ' \n')
check "orders: magic byte 0, then content ID $content_id in 4 bytes" "$(printf '00%08x' "$content_id")" "$bytes"
headers=$(first_record orders-headers --property print.headers=true --property print.value=false | tr -d '\000')
check "orders-headers: the content ID is a Kafka header" yes \
  "$(grep -q 'apicurio.value.contentId' <<< "$headers" && echo yes || echo no)"
check "orders-headers: the payload has no magic byte" yes \
  "$([ "$(first_record orders-headers | head -c 1 | od -An -tx1 | tr -d ' \n')" != 00 ] && echo yes || echo no)"

# ------------------------------------------------------------------------------ step 13
part "Step 13: Kafka Connect with the Apicurio converters"
docker compose --profile connect up -d --wait connect > /dev/null 2>&1
check "the worker has the Apicurio AvroConverter" yes \
  "$(curl -s "$CONNECT/connector-plugins?connectorsOnly=false" | jq -e 'any(.[]; .class == "io.apicurio.registry.utils.converter.AvroConverter")' > /dev/null && echo yes || echo no)"
converter='"value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
  "value.converter.apicurio.registry.url": "http://apicurio-registry:8080/apis/registry/v3"'
curl -s -o /dev/null -X PUT "$CONNECT/connectors/orders-to-file/config" -H 'Content-Type: application/json' -d "{
  \"connector.class\": \"org.apache.kafka.connect.file.FileStreamSinkConnector\",
  \"topics\": \"orders\", \"file\": \"/tmp/orders-sink.txt\", $converter}"
# A new file each run: Connect remembers how far it read a file, across connector deletions
lines_file="/tmp/lines-$(date +%s).txt"
docker compose exec -T connect sh -c "printf 'first line\nsecond line\n' > $lines_file"
curl -s -o /dev/null -X PUT "$CONNECT/connectors/file-to-lines/config" -H 'Content-Type: application/json' -d "{
  \"connector.class\": \"org.apache.kafka.connect.file.FileStreamSourceConnector\",
  \"topic\": \"connect-lines\", \"file\": \"$lines_file\", $converter,
  \"value.converter.apicurio.registry.auto-register\": \"true\"}"
sink=""
for _ in $(seq 1 30); do
  sink=$(docker compose exec -T connect cat /tmp/orders-sink.txt 2>/dev/null || true)
  [ "$(grep -c 'orderId' <<< "$sink")" -ge 4 ] && break
  sleep 2
done
check "the sink connector decoded the 4 Avro orders" 4 "$(grep -c 'orderId' <<< "$sink" || true)"
check "the decoded record keeps its fields" yes "$(grep -q 'orderId=o-4' <<< "$sink" && echo yes || echo no)"
for _ in $(seq 1 30); do
  [ "$(get /groups/default/artifacts/connect-lines-value | jq -r .artifactType 2>/dev/null)" = AVRO ] && break
  sleep 2
done
check "the source connector's converter registered default/connect-lines-value" '"string"' \
  "$(get '/groups/default/artifacts/connect-lines-value/versions/branch=latest/content')"
check "connect-lines records start with the magic byte" 00 \
  "$(first_record connect-lines | head -c 1 | od -An -tx1 | tr -d ' \n')"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
