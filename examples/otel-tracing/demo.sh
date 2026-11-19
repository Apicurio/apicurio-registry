#!/usr/bin/env bash
# Sends requests through both paths of the example and checks, in Jaeger, that their traces contain
# the expected spans from every service, linked by trace ID and parent span.
# Usage: ./demo.sh   (after ./build.sh and `docker compose up -d`; requires curl, jq and openssl)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:${REGISTRY_PORT:-8081}/apis}
V3="$API/registry/v3"
JAEGER=${JAEGER:-http://localhost:${JAEGER_PORT:-16686}}
METRICS=${METRICS:-http://localhost:${COLLECTOR_METRICS_PORT:-8889}/metrics}
PRODUCER=${PRODUCER:-http://localhost:${PRODUCER_PORT:-8084}}
CONSUMER=${CONSUMER:-http://localhost:${CONSUMER_PORT:-8085}}
ORDERS=${ORDERS:-http://localhost:${ORDER_SERVICE_PORT:-8086}}
CDC=${CDC:-http://localhost:${CDC_CONSUMER_PORT:-8087}}
failures=0
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

ok()   { printf '  ok    %-70s %s\n' "$1" "${2:-}"; }
fail() { printf '  FAIL  %-70s %s\n' "$1" "${2:-}"; failures=$((failures + 1)); }

# expect <description> <expected> <actual>
expect() {
  if [[ "$3" == "$2" ]]; then ok "$1" "$3"; else fail "$1" "$3 (expected $2)"; fi
}

# wait_for <name> <url>: polls every 2s, gives up after WAIT_TIMEOUT seconds (default 180)
wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (did you run ./build.sh and 'docker compose up -d'?)" >&2
      exit 1
    fi
    sleep 2
  done
}

# new_trace: prints a fresh trace ID and span ID; demo.sh starts every trace itself, with a
# W3C traceparent header, so it knows the trace ID to look up in Jaeger
new_trace() { echo "$(openssl rand -hex 16) $(openssl rand -hex 8)"; }

# Flattens a Jaeger trace into [{svc, op, id, parent, kind, tags: {key: value}, events: [...]}]
SPANS='.data[0] as $d | [$d.spans[] | {svc: $d.processes[.processID].serviceName, op: .operationName,
  id: .spanID, parent: (.references[0].spanID // null),
  tags: (reduce .tags[] as $t ({}; .[$t.key] = ($t.value | tostring))),
  events: [.logs[]?.fields[]? | select(.key == "event" or .key == "exception.type") | .value]}]'

# fetch_trace <trace id> <jq condition on the span list>: waits (up to 60s) until Jaeger has the
# trace and the condition holds (spans arrive in batches), then leaves the span list in
# $work/<trace id>.json; the checks report what is missing if it never holds
fetch_trace() {
  local deadline=$((SECONDS + 60))
  while :; do
    curl -s "$JAEGER/api/traces/$1" | jq "$SPANS" > "$work/$1.json" 2>/dev/null || echo '[]' > "$work/$1.json"
    if jq -e ". as \$spans | $2" "$work/$1.json" > /dev/null 2>&1 || [ "$SECONDS" -ge "$deadline" ]; then return; fi
    sleep 2
  done
}

# span <trace id> <service> <operation>: prints the first matching span (or null)
span() { jq -c --arg s "$2" --arg o "$3" 'map(select(.svc == $s and .op == $o)) | first' "$work/$1.json"; }

# check_span <description> <trace id> <service> <operation> [<parent span id>]: the trace has the
# span, and (when given) it is a direct child of the parent span
check_span() {
  local s
  s=$(span "$2" "$3" "$4")
  if [[ "$s" == "null" ]]; then
    fail "$1" "no '$4' span from $3"
  elif [[ -n "${5:-}" && "$(jq -r .parent <<< "$s")" != "$5" ]]; then
    fail "$1" "parent $(jq -r .parent <<< "$s") (expected $5)"
  else
    ok "$1" "$3: $4"
  fi
}

# span_value <trace id> <service> <operation> <jq filter on the span>
span_value() { span "$1" "$2" "$3" | jq -r "if . == null then \"<no span>\" else $4 end"; }

echo "Waiting for the services..."
wait_for Registry "$V3/system/info"
wait_for Jaeger "$JAEGER/api/services"
wait_for "greeting producer" "$PRODUCER/q/health/ready"
wait_for "greeting consumer" "$CONSUMER/q/health/ready"
wait_for "order service" "$ORDERS/q/health/ready"
wait_for "CDC consumer" "$CDC/q/health/ready"

echo "1. The Registry traces its own REST API:"
read -r tid sid < <(new_trace)
status=$(curl -s -o /dev/null -w '%{http_code}' -H "traceparent: 00-$tid-$sid-01" "$V3/system/info")
expect "GET /system/info with a traceparent header" 200 "$status"
fetch_trace "$tid" 'any(.op == "GET /apis/registry/v3/system/info")'
check_span "the Registry's server span joins the caller's trace" "$tid" apicurio-registry "GET /apis/registry/v3/system/info" "$sid"
expect "  its kind" server "$(span_value "$tid" apicurio-registry "GET /apis/registry/v3/system/info" .tags[\"span.kind\"])"

read -r tid sid < <(new_trace)
status=$(curl -s -o /dev/null -w '%{http_code}' -H "traceparent: 00-$tid-$sid-01" "$V3/groups/default/artifacts/no-such-artifact")
expect "GET a missing artifact" 404 "$status"
route="GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}"
fetch_trace "$tid" 'any(.op == "storage.getArtifactMetaData") and any(.svc == "apicurio-registry" and .op == "GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}")'
check_span "the server span of the failed request" "$tid" apicurio-registry "$route" "$sid"
expect "  its HTTP status" 404 "$(span_value "$tid" apicurio-registry "$route" '.tags["http.response.status_code"]')"
expect "  its artifactId attribute" no-such-artifact "$(span_value "$tid" apicurio-registry "$route" '.tags["apicurio.registry.path.artifact_id"]')"
server_id=$(span_value "$tid" apicurio-registry "$route" .id)
check_span "a storage span under it" "$tid" apicurio-registry storage.getArtifactMetaData "$server_id"
expect "  marked as an error" true "$(span_value "$tid" apicurio-registry storage.getArtifactMetaData '.tags.error')"
expect "  with the exception" ArtifactNotFoundException \
  "$(span_value "$tid" apicurio-registry storage.getArtifactMetaData '.events | map(select(endswith("ArtifactNotFoundException"))) | first // "none" | split(".") | last')"

echo "2. Kafka serdes: one trace from the producer's REST call, through the Registry, to the consumer:"
name="demo-$(openssl rand -hex 3)"
read -r tid sid < <(new_trace)
response=$(curl -s -X POST -H "traceparent: 00-$tid-$sid-01" "$PRODUCER/greetings?name=$name")
expect "POST /greetings returns the caller's trace ID" "$tid" "$(jq -r .traceId <<< "$response")"
# Wait for every span checked below: each service exports its spans in its own batches
fetch_trace "$tid" 'all(
  ["greeting-producer", "POST /greetings"], ["greeting-producer", "create-greeting"],
  ["greeting-producer", "greetings publish"], ["greeting-producer", "serde.serialize"],
  ["greeting-producer", "serde.resolve_schema"], ["greeting-producer", "POST"],
  ["apicurio-registry", "POST /apis/registry/v3/groups/{groupId}/artifacts"],
  ["greeting-consumer", "greetings process"], ["greeting-consumer", "process-greeting-message"];
  . as [$s, $o] | any($spans[]; .svc == $s and .op == $o))'
check_span "producer: REST endpoint" "$tid" greeting-producer "POST /greetings" "$sid"
http_id=$(span_value "$tid" greeting-producer "POST /greetings" .id)
check_span "producer: @WithSpan business span" "$tid" greeting-producer create-greeting "$http_id"
expect "  its greeting.recipient attribute" "$name" "$(span_value "$tid" greeting-producer create-greeting '.tags["greeting.recipient"]')"
check_span "producer: Kafka publish span" "$tid" greeting-producer "greetings publish" "$http_id"
publish_id=$(span_value "$tid" greeting-producer "greetings publish" .id)
check_span "producer: the serializer's own span (serdes tracing)" "$tid" greeting-producer serde.serialize "$publish_id"
expect "  its topic attribute" greetings "$(span_value "$tid" greeting-producer serde.serialize '.tags["messaging.destination.name"]')"
serialize_id=$(span_value "$tid" greeting-producer serde.serialize .id)
# A schema that is not cached yet is looked up (here: registered) in a span of its own
check_span "producer: the schema lookup's span, under the serializer's" "$tid" greeting-producer serde.resolve_schema "$serialize_id"
resolve_id=$(span_value "$tid" greeting-producer serde.resolve_schema .id)
check_span "producer: HTTP call to the Registry (schema registration)" "$tid" greeting-producer POST "$resolve_id"
client_id=$(span_value "$tid" greeting-producer POST .id)
route="POST /apis/registry/v3/groups/{groupId}/artifacts"
check_span "registry: the registration request, child of that call" "$tid" apicurio-registry "$route" "$client_id"
expect "  handled by" io.apicurio.registry.rest.v3.GroupsResource.createArtifact \
  "$(span_value "$tid" apicurio-registry "$route" '.tags["code.function.name"]')"
expect "  its query (the serializer's auto-registration)" "canonical=false&ifExists=FIND_OR_CREATE_VERSION" \
  "$(span_value "$tid" apicurio-registry "$route" '.tags["url.query"]')"
registry_id=$(span_value "$tid" apicurio-registry "$route" .id)
expect "  with storage spans under it" true \
  "$(jq --arg p "$registry_id" 'any(.parent == $p and (.op | startswith("storage.")))' "$work/$tid.json")"
check_span "consumer: Kafka process span, child of the publish span" "$tid" greeting-consumer "greetings process" "$publish_id"
process_id=$(span_value "$tid" greeting-consumer "greetings process" .id)
check_span "consumer: application span with Kafka metadata" "$tid" greeting-consumer process-greeting-message "$process_id"
expect "  its greeting.original_trace_id attribute" "$tid" "$(span_value "$tid" greeting-consumer process-greeting-message '.tags["greeting.original_trace_id"]')"
expect "the trace has spans from exactly these services" "apicurio-registry,greeting-consumer,greeting-producer" \
  "$(jq -r 'map(.svc) | unique | join(",")' "$work/$tid.json")"
received=$(curl -s "$CONSUMER/consumer/greetings/batch?count=1000" \
  | jq -r --arg m "Hello, $name!" '.messages // [] | map(select(.message == $m)) | first | .extractedTraceId // "not received"')
expect "the consumer app received the greeting in the same trace" "$tid" "$received"
deserialize=$(curl -s "$JAEGER/api/traces?service=greeting-consumer&operation=serde.deserialize&limit=1&lookback=1h" \
  | jq -r '.data[0].spans[0].tags | map(select(.key == "messaging.destination.name")) | first.value // "none"')
expect "consumer: the deserializer's span exists (in its own trace, see README)" greetings "$deserialize"

echo "3. Errors are recorded on spans:"
read -r tid sid < <(new_trace)
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "traceparent: 00-$tid-$sid-01" "$PRODUCER/greetings/invalid?errorType=schema")
expect "POST /greetings/invalid?errorType=schema" 400 "$status"
fetch_trace "$tid" 'any(.op == "process-invalid-greeting")'
check_span "producer: the failed operation's span" "$tid" greeting-producer process-invalid-greeting
expect "  its status" ERROR "$(span_value "$tid" greeting-producer process-invalid-greeting '.tags["otel.status_code"]')"
expect "  its error.type attribute" schema "$(span_value "$tid" greeting-producer process-invalid-greeting '.tags["error.type"]')"
expect "  the recorded exception" java.lang.RuntimeException \
  "$(span_value "$tid" greeting-producer process-invalid-greeting '.events | map(select(. == "java.lang.RuntimeException")) | first // "none"')"

echo "4. Debezium CDC: one trace from the order service, through Debezium and the Registry, to the CDC consumer:"
# With snapshot.mode=no_data, Debezium captures only what is committed after it starts streaming:
# wait until its replication slot is active
slot_active() { docker compose exec -T postgres psql -U orderuser -d orders -tAc \
  "select active from pg_replication_slots where slot_name = 'orders_slot'" 2> /dev/null | tr -d '[:space:]'; }
deadline=$((SECONDS + 180))
until [ "$(slot_active)" = t ]; do
  [ "$SECONDS" -ge "$deadline" ] && { fail "Debezium is streaming (replication slot orders_slot active)"; break; }
  sleep 2
done
[ "$(slot_active)" = t ] && ok "Debezium is streaming (replication slot orders_slot active)"
read -r tid sid < <(new_trace)
response=$(curl -s -X POST -H "traceparent: 00-$tid-$sid-01" -H 'Content-Type: application/json' \
  -d '{"customerName": "Demo", "product": "Widget", "quantity": 2, "price": 9.99}' "$ORDERS/orders")
expect "POST /orders returns the caller's trace ID" "$tid" "$(jq -r .traceId <<< "$response")"
order_id=$(jq -r .id <<< "$response")
fetch_trace "$tid" 'all(
  ["order-service", "POST /orders"], ["order-service", "create-order-entity"],
  ["debezium-server", "debezium-cdc-orders"], ["cdc-consumer", "process-cdc-event"],
  ["cdc-consumer", "GET /apis/registry/v3/ids/globalIds/{globalId}"],
  ["apicurio-registry", "GET /apis/registry/v3/ids/globalIds/{globalId}"];
  . as [$s, $o] | any($spans[]; .svc == $s and .op == $o))'
check_span "order service: REST endpoint" "$tid" order-service "POST /orders" "$sid"
check_span "order service: the insert, whose context is stored in the row" "$tid" order-service create-order-entity \
  "$(span_value "$tid" order-service "POST /orders" .id)"
entity_id=$(span_value "$tid" order-service create-order-entity .id)
check_span "debezium: the tracing SMT continues the trace from the row" "$tid" debezium-server debezium-cdc-orders "$entity_id"
expect "  its topic attribute" dbserver1.public.orders "$(span_value "$tid" debezium-server debezium-cdc-orders '.tags["messaging.destination"]')"
check_span "cdc consumer: the event's span, also continued from the row" "$tid" cdc-consumer process-cdc-event "$entity_id"
expect "  its cdc.operation attribute" CREATE "$(span_value "$tid" cdc-consumer process-cdc-event '.tags["cdc.operation"]')"
lookup="GET /apis/registry/v3/ids/globalIds/{globalId}"
check_span "cdc consumer: REST client call to the Registry" "$tid" cdc-consumer "$lookup" \
  "$(span_value "$tid" cdc-consumer process-cdc-event .id)"
expect "  its kind" client "$(span_value "$tid" cdc-consumer "$lookup" '.tags["span.kind"]')"
check_span "registry: the schema lookup by global ID, child of that call" "$tid" apicurio-registry "$lookup" \
  "$(span_value "$tid" cdc-consumer "$lookup" .id)"
received=$(curl -s "$CDC/cdc/events/batch?count=1000" \
  | jq -r --arg t "$tid" '.events // [] | map(select(.extractedTraceId == $t)) | first | .operation // "not received"')
expect "the CDC consumer app received the insert with the order's trace ID" CREATE "$received"

# Debezium registers the schema once, when it sees the first change, and caches it after that, so
# look for that registration among all Debezium traces rather than in this order's trace
registration=$(curl -s "$JAEGER/api/traces?service=debezium-server&operation=POST&limit=20&lookback=2h" | jq -r '
  [.data[] | .processes as $p | [.spans[] | {svc: $p[.processID].serviceName, op: .operationName,
     id: .spanID, parent: (.references[0].spanID // null)}] as $s
   | $s[] | select(.svc == "debezium-server" and .op == "POST") as $c
   | $s[] | select(.svc == "apicurio-registry" and .parent == $c.id and (.op | startswith("POST /apis/registry/v2/")))
   | select(any($s[]; .svc == "order-service" and .op == "POST /orders")) | .op] | first // "none"')
if [[ "$registration" == "none" ]]; then
  fail "debezium: its schema registration is in an order's trace" "none found"
else
  ok "debezium: its schema registration is in an order's trace" "$registration"
fi

read -r tid sid < <(new_trace)
status=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "traceparent: 00-$tid-$sid-01" "$ORDERS/orders/$order_id/status?status=SHIPPED")
expect "PUT /orders/$order_id/status?status=SHIPPED" 200 "$status"
fetch_trace "$tid" 'any(.op == "process-cdc-event") and any(.op == "debezium-cdc-orders")'
check_span "debezium: the update continues the update's trace" "$tid" debezium-server debezium-cdc-orders
expect "cdc consumer: the event's cdc.operation attribute" UPDATE "$(span_value "$tid" cdc-consumer process-cdc-event '.tags["cdc.operation"]')"

# Debezium's Kafka sink sends the record later, on another thread, so its publish span starts a
# trace of its own; the CDC consumer's Kafka span continues that one (through the record headers)
publish=$(curl -s "$JAEGER/api/traces?service=debezium-server&operation=dbserver1.public.orders%20publish&limit=1&lookback=1h" \
  | jq -r '.data[0] | .processes as $p | [.spans[] | "\($p[.processID].serviceName):\(.operationName)"] | sort | join(",")')
expect "debezium's Kafka publish span is in a separate trace (today's behaviour)" \
  "cdc-consumer:dbserver1.public.orders process,debezium-server:dbserver1.public.orders publish" "$publish"
# The CDC consumer reads the schema reference from the record headers: its deserialization is traced
# too, with a schema-lookup span under it when the lookup misses the cache
cdc_deserialize=$(curl -s "$JAEGER/api/traces?service=cdc-consumer&operation=serde.deserialize&limit=20&lookback=1h" \
  | jq -r '[.data[].spans[] | select(.operationName == "serde.deserialize") | .tags[] | select(.key == "messaging.destination.name") | .value] | first // "none"')
expect "cdc consumer: the header-based deserialization has its span" dbserver1.public.orders "$cdc_deserialize"
cdc_resolve=$(curl -s "$JAEGER/api/traces?service=cdc-consumer&operation=serde.resolve_schema&limit=1&lookback=1h" | jq '.data | length')
expect "  and its first schema lookup a serde.resolve_schema span" yes "$([ "$cdc_resolve" -ge 1 ] && echo yes || echo no)"

echo "5. Every service reports to the same backend, and the Collector exposes the metrics:"
expect "Jaeger knows all six services" "apicurio-registry,cdc-consumer,debezium-server,greeting-consumer,greeting-producer,order-service" \
  "$(curl -s "$JAEGER/api/services" | jq -r '.data - ["jaeger"] | sort | join(",")')"
# metrics_ready: the Collector has received the Registry's counters (exported every 5s)
metrics_ready() { curl -s "$METRICS" | grep '^otel_apicurio_artifacts_created_total' > /dev/null; }
deadline=$((SECONDS + 60))
until metrics_ready || [ "$SECONDS" -ge "$deadline" ]; do sleep 2; done
metrics=$(curl -s "$METRICS")
# Every creation is counted, whichever API it came through: greetings-value (v3, the producer's
# serializer) and Debezium's schemas (v2). The counter starts with the Registry, so it covers at
# least the AVRO artifacts that exist now
# The Collector receives the counter every 5 seconds: poll until it has caught up
avro_counted() { curl -s "$METRICS" | grep -E '^otel_apicurio_artifacts_created_total\{.*apicurio_registry_artifact_type="AVRO".*job="apicurio-registry"' \
  | awk '{s += $NF} END {print s + 0}'; }
avro_existing() { curl -s "$V3/search/artifacts?artifactType=AVRO&limit=1" | jq .count; }
covered() { local c e; c=$(avro_counted); e=$(avro_existing); [ "$e" -ge 2 ] && [ "$c" -ge "$e" ] && echo yes || echo "no ($c counted, $e exist)"; }
deadline=$((SECONDS + 60))
until [ "$(covered)" = yes ] || [ "$SECONDS" -ge "$deadline" ]; do sleep 2; done
expect "the artifacts-created counter covers every AVRO artifact, v2 and v3" yes "$(covered)"
requests=$(grep -E '^otel_http_server_request_duration_seconds_count\{.*http_route="/apis/registry/v3/groups/\{groupId\}/artifacts".*job="apicurio-registry"' <<< "$metrics" \
  | awk '{s += $NF} END {print (s >= 1) ? "yes" : "no"}')
expect "the Registry exported HTTP server metrics per route" yes "$requests"

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
