#!/usr/bin/env bash
# Makes a fixed sequence of changes in the registry, then reads the events it published to Kafka and
# checks them: which events, in which order, with which key and payload.
# Usage: ./demo.sh                        both variants
#        VARIANTS=kafkasql ./demo.sh      one of them: postgresql kafkasql
#        KEEP=1 ./demo.sh                 leave the last variant running
# Requires Docker (or Podman) with Compose v2, curl and jq. Each variant is started, checked and
# removed with its data, so it is safe to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

[ -n "${CI_VARIANT:-}" ] && VARIANTS=$CI_VARIANT
VARIANTS=${VARIANTS:-postgresql kafkasql}
A=${API:-http://localhost:${REGISTRY_PORT:-8081}/apis}/registry/v3
B=${API_B:-http://localhost:${REGISTRY_B_PORT:-8082}/apis}/registry/v3
CONNECT=${CONNECT:-http://localhost:${CONNECT_PORT:-8083}}
failures=0
current=""

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

compose() { docker compose --profile "$current" "$@"; }
kafka_service() { if [ "$current" = postgresql ]; then echo kafka-pg; else echo kafka-ks; fi; }
code() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$1" "$2" ${3:+-H 'Content-Type: application/json' -d "$3"}; }

# events <topic> <count>: the first <count> records of the topic, one JSON object per line:
#   {"key": ..., "value": {...payload...}}
events() {
  compose exec -T "$(kafka_service)" /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
    --topic "$1" --from-beginning --max-messages "$2" --timeout-ms 60000 \
    --property print.key=true --property key.separator=$'\t' 2>/dev/null \
    | while IFS=$'\t' read -r key value; do
        jq -c --arg k "$key" '{key: $k, value: (if type == "string" then fromjson else . end)}' <<< "$value"
      done
}
# count <topic>: the number of records in the topic
count() {
  compose exec -T "$(kafka_service)" /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 \
    --topic "$1" 2>/dev/null | awk -F: '{n += $3} END {print n + 0}'
}

eventually() {
  local deadline=$((SECONDS + $1)) expected=$2 actual=""
  shift 2
  while [ "$SECONDS" -lt "$deadline" ]; do
    actual=$("$@" 2>/dev/null || true)
    [[ "$actual" == "$expected" ]] && break
    sleep 1
  done
  echo "$actual"
}

up() {
  current=$1
  compose down -v > /dev/null 2>&1 || true
  if ! compose up -d --wait > /tmp/events-up.log 2>&1; then
    tail -20 /tmp/events-up.log
    fail "$current: the services did not start"
    return 1
  fi
  local deadline=$((SECONDS + 60))
  until curl -sf --max-time 5 -o /dev/null "$A/system/info"; do
    [ "$SECONDS" -ge "$deadline" ] && { fail "$current: the registry is not reachable at $A"; return 1; }
    sleep 1
  done
}
down() {
  if [ -n "$current" ]; then compose down -v > /dev/null 2>&1 || true; fi
  current=""
}
trap '[ -n "${KEEP:-}" ] || down' EXIT

SCHEMA_V1='{"type": "record", "name": "OrderPlaced", "namespace": "com.example", "fields": [{"name": "id", "type": "string"}]}'
SCHEMA_V2='{"type": "record", "name": "OrderPlaced", "namespace": "com.example", "fields": [{"name": "id", "type": "string"}, {"name": "amount", "type": "double", "default": 0}]}'
SCHEMA_BAD='{"type": "record", "name": "OrderPlaced", "namespace": "com.example", "fields": [{"name": "id", "type": "long"}]}'
version_body() { jq -n --arg c "$1" '{content: {content: $c, contentType: "application/json"}}'; }

# changes <base>: the sequence of changes; each line of EXPECTED is the event it must produce
EXPECTED="GROUP_CREATED orders
GROUP_METADATA_UPDATED orders
ARTIFACT_CREATED orders-order-placed
ARTIFACT_VERSION_CREATED orders-order-placed-1
ARTIFACT_RULE_CONFIGURED orders-order-placed
ARTIFACT_VERSION_CREATED orders-order-placed-2
ARTIFACT_METADATA_UPDATED orders-order-placed
ARTIFACT_VERSION_STATE_CHANGED orders-order-placed-1
GLOBAL_RULE_CONFIGURED VALIDITY
ARTIFACT_VERSION_DELETED orders-order-placed-1
ARTIFACT_DELETED orders-order-placed
GROUP_DELETED orders"
changes() {
  local base=$1 s
  s="$(code POST "$base/groups" '{"groupId": "orders"}')"
  s+=" $(code PUT "$base/groups/orders" '{"description": "Order events"}')"
  s+=" $(code POST "$base/groups/orders/artifacts" "$(jq -n --arg c "$SCHEMA_V1" \
      '{artifactId: "order-placed", artifactType: "AVRO", firstVersion: {version: "1", content: {content: $c, contentType: "application/json"}}}')")"
  s+=" $(code POST "$base/groups/orders/artifacts/order-placed/rules" '{"ruleType": "COMPATIBILITY", "config": "BACKWARD"}')"
  # Not events: a dry run, and a version rejected by the rule
  s+=" $(code POST "$base/groups/orders/artifacts/order-placed/versions?dryRun=true" "$(version_body "$SCHEMA_V2")")"
  s+=" $(code POST "$base/groups/orders/artifacts/order-placed/versions" "$(version_body "$SCHEMA_BAD")")"
  s+=" $(code POST "$base/groups/orders/artifacts/order-placed/versions" "$(jq -n --arg c "$SCHEMA_V2" '{version: "2", content: {content: $c, contentType: "application/json"}}')")"
  s+=" $(code PUT "$base/groups/orders/artifacts/order-placed" '{"name": "Order placed", "description": "When a customer places an order"}')"
  s+=" $(code PUT "$base/groups/orders/artifacts/order-placed/versions/1/state" '{"state": "DEPRECATED"}')"
  s+=" $(code POST "$base/admin/rules" '{"ruleType": "VALIDITY", "config": "FULL"}')"
  s+=" $(code DELETE "$base/groups/orders/artifacts/order-placed/versions/1")"
  s+=" $(code DELETE "$base/groups/orders/artifacts/order-placed")"
  s+=" $(code DELETE "$base/groups/orders")"
  check "the changes: 12 accepted, a dry run (200) and a rule violation (400)" \
    "200 204 200 204 200 400 200 204 204 204 204 204 204" "$s"
}

# check_events <topic>: the events on the topic match EXPECTED, and their payloads are right
check_events() {
  local topic=$1 all n
  n=$(echo "$EXPECTED" | wc -l | tr -d ' ')
  check "the topic $topic holds exactly $n events (none for the dry run or the rejected version)" "$n" \
    "$(eventually 90 "$n" count "$topic")"
  all=$(events "$topic" "$n")
  local expected=$EXPECTED
  if [ "$current" = postgresql ]; then
    # On SQL storage, the first version's event comes before the artifact's
    # (https://github.com/Apicurio/apicurio-registry/issues/11097)
    expected=$(echo "$EXPECTED" | awk 'NR==3{a=$0; next} NR==4{print; print a; next} {print}')
  fi
  check "the events, in order, with their keys" "$expected" \
    "$(jq -r '"\(.value.eventType) \(.key)"' <<< "$all")"
  check "every event has a unique id" "$n" "$(jq -r .value.id <<< "$all" | sort -u | wc -l | tr -d ' ')"
  check "ARTIFACT_VERSION_CREATED carries the coordinates" "orders order-placed 2" \
    "$(jq -r 'select(.value.eventType == "ARTIFACT_VERSION_CREATED" and .value.version == "2") | .value | "\(.groupId) \(.artifactId) \(.version)"' <<< "$all")"
  check "ARTIFACT_VERSION_STATE_CHANGED carries the old and new state" "ENABLED DEPRECATED" \
    "$(jq -r 'select(.value.eventType == "ARTIFACT_VERSION_STATE_CHANGED") | .value | "\(.oldState) \(.newState)"' <<< "$all")"
  check "ARTIFACT_RULE_CONFIGURED carries the rule" "COMPATIBILITY BACKWARD" \
    "$(jq -r 'select(.value.eventType == "ARTIFACT_RULE_CONFIGURED") | .value | "\(.ruleType) \(.rule)"' <<< "$all")"
  check "ARTIFACT_METADATA_UPDATED carries the new metadata" "Order placed" \
    "$(jq -r 'select(.value.eventType == "ARTIFACT_METADATA_UPDATED") | .value.name' <<< "$all")"
  echo "$all" | jq -c '.value' | head -2 | sed 's/^/  info  /'
  check "to-cloudevents.sh: a valid CloudEvent with the reserved type name" \
    "1.0 io.apicurio.registry.artifact.version.created orders/order-placed/1" \
    "$(jq -r '"\(.specversion) \(.type) \(.subject)"' <<< "$(jq -c '.value' <<< "$all" | ./to-cloudevents.sh | grep 'version.created' | head -1)")"
  check "to-cloudevents.sh: no empty attributes; a global rule's subject is the rule type" \
    "0 io.apicurio.registry.global.rule.configured VALIDITY" \
    "$(jq -c '.value' <<< "$all" | ./to-cloudevents.sh | jq -s '[.[] | to_entries[] | select(.value == "")] | length') $(jq -c '.value' <<< "$all" | ./to-cloudevents.sh | jq -r 'select(.type | endswith("global.rule.configured")) | "\(.type) \(.subject)"')"
}

# ------------------------------------------------------------------------------------ postgresql
connector_running() {
  curl -s "$CONNECT/connectors/registry-outbox/status" | jq -r '.tasks[0].state // "none"'
}
run_postgresql() {
  part "SQL storage (PostgreSQL): the outbox table and Debezium"
  up postgresql || return 0
  check "register the outbox connector" 201 \
    "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$CONNECT/connectors" -H 'Content-Type: application/json' \
       -d "$(jq '{name: "registry-outbox", config: .}' connect/outbox-connector.json)")"
  check "the connector runs" RUNNING "$(eventually 60 RUNNING connector_running)"
  # The connector creates its replication slot when it starts: changes before that are not captured
  sleep 5
  changes "$A"
  check_events outbox.event.registry-events
  check "the outbox table is empty: each row is deleted in the transaction that wrote it" 0 \
    "$(compose exec -T postgres psql -U registry -d registry -tAc 'select count(*) from outbox')"

  # At least once: with Kafka Connect stopped, writes still succeed; the events follow later
  compose stop connect > /dev/null 2>&1
  check "with Kafka Connect stopped, a write still succeeds" 200 "$(code POST "$A/groups" '{"groupId": "while-down"}')"
  check "  and no event is published yet" 12 "$(count outbox.event.registry-events)"
  compose start connect > /dev/null 2>&1
  check "when Kafka Connect is back, the event is delivered from the database log" 13 \
    "$(eventually 120 13 count outbox.event.registry-events)"
}

# -------------------------------------------------------------------------------------- kafkasql
run_kafkasql() {
  part "KafkaSQL storage: the registry publishes to registry-events"
  up kafkasql || return 0
  changes "$A"
  check_events registry-events
  local partitions
  partitions=$(compose exec -T kafka-ks /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
    --describe --topic registry-events 2>/dev/null | grep -o 'PartitionCount: [0-9]*' | awk '{print $2}')
  check "the events topic has one partition: one total order" 1 "$partitions"
  check "a change through the other replica is published once, not once per replica" 200 \
    "$(code POST "$B/groups" '{"groupId": "via-b"}')"
  sleep 5
  check "  13 events in total" 13 "$(count registry-events)"
}

last=$(echo $VARIANTS | awk '{print $NF}')
for v in $VARIANTS; do
  case "$v" in
    postgresql | kafkasql) "run_$v" ;;
    *) fail "unknown variant '$v'" ;;
  esac
  if [ -z "${KEEP:-}" ] || [ "$v" != "$last" ]; then down; fi
done

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
