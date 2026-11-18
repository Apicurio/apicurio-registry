#!/usr/bin/env bash
# Checks every step of GUIDE.md: the Camel application sends Avro greetings through Kafka, the Avro
# serializer registers the schema in the registry and writes its content ID before each record, the
# Camel consumer deserializes them, and a compatibility rule decides which schema changes are allowed.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl, jq and JDK 21 or later)
# It starts the Camel application (target/quarkus-app/quarkus-run.jar, built by build.sh when it is
# missing) on APP_PORT (default 8090) and stops it at the end.
# Safe to run again: it deletes the greetings-value artifact and the greetings topic, then starts
# over. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:${REGISTRY_PORT:-8081}/apis}
R=$API/registry/v3
ARTIFACT=$R/groups/default/artifacts/greetings-value
APP=${APP:-http://localhost:${APP_PORT:-8090}}
APP_LOG=${APP_LOG:-target/camel-app.log}
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }
json() { curl -s --max-time 30 "$@"; }
code() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' "$@"; }

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

wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2" >&2
      [ "$1" = "the Camel application" ] && tail -40 "$APP_LOG" >&2
      exit 1
    fi
    sleep 2
  done
}

topics() { docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 "$@"; }
# received <name>: "<name> <message>" of the greetings the Avro consumer route got for that name
received() { json "$APP/greetings/received" | jq -r --arg n "$1" '.[] | select(.name == $n) | "\(.name) \(.message)"'; }
# raw <name>: "<magic byte> <schema ID>" of the raw records with that key
raw() { json "$APP/greetings/raw" | jq -r --arg n "$1" '.[] | select(.key == $n) | "\(.magicByte) \(.schemaId)"'; }
send() { code -X POST "$APP/greetings/send?name=$1&message=$2"; }

part "Before you start"
wait_for Registry "$R/system/info"
if [ ! -f target/quarkus-app/quarkus-run.jar ]; then
  ./build.sh > /tmp/camel-quarkus-kafka-build.log 2>&1 \
    || { tail -20 /tmp/camel-quarkus-kafka-build.log; echo "build.sh failed" >&2; exit 1; }
fi
code -X DELETE "$ARTIFACT" > /dev/null
topics --delete --topic greetings > /dev/null 2>&1 || true
# Topic deletion is asynchronous: retry the creation until the old topic is gone
check "the example data is reset (artifact deleted, topic greetings recreated)" ok \
  "$(eventually 60 ok sh -c 'docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --create --topic greetings --partitions 1 > /dev/null 2>&1 && echo ok')"

part "Step 2: start the Camel application"
mkdir -p target
# The timer fires once a second after start-up (greeting for Alice), then every GREETING_PERIOD ms
java -Dquarkus.http.port="${APP_PORT:-8090}" -Dgreeting.period="${GREETING_PERIOD:-3600000}" \
  -Dcamel.component.kafka.brokers="localhost:${KAFKA_PORT:-9092}" \
  "-Dcamel.component.kafka.additional-properties[apicurio.registry.url]=$R" \
  -jar target/quarkus-app/quarkus-run.jar > "$APP_LOG" 2>&1 &
app_pid=$!
trap 'kill "$app_pid" 2> /dev/null || true; wait "$app_pid" 2> /dev/null || true' EXIT
wait_for "the Camel application" "$APP/greetings/received"
ok "the application answers on $APP"

part "Step 3: the timer route's first greeting"
check "the consumer route deserialized the timer's first greeting" "Alice Hello from Camel!" \
  "$(eventually 60 "Alice Hello from Camel!" received Alice)"

part "Step 4: the schema the serializer registered"
check "artifact default/greetings-value (TopicIdStrategy: <topic>-value) is AVRO" AVRO \
  "$(json "$ARTIFACT" | jq -r .artifactType)"
check "  with one version, 1" 1 "$(json "$ARTIFACT/versions" | jq -r '[.versions[].version] | join(" ")')"
check "  its content is the Greeting record of src/main/resources/avro/greeting.avsc" \
  "io.apicurio.registry.examples.camelkafka.Greeting name message timestamp" \
  "$(json "$ARTIFACT/versions/1/content" | jq -r '"\(.namespace).\(.name) \([.fields[].name] | join(" "))"')"
content_id=$(json "$ARTIFACT/versions/1" | jq -r .contentId)
check "  the deserializer can fetch it by its content ID ($content_id)" Greeting \
  "$(json "$R/ids/contentIds/$content_id" | jq -r .name)"

part "Step 5: send a greeting and read it back"
check "POST /greetings/send?name=Ada&message=Hi" 204 "$(send Ada Hi)"
check "the Avro consumer route received Ada's greeting" "Ada Hi" "$(eventually 30 "Ada Hi" received Ada)"
check "the raw record starts with magic byte 0 and version 1's content ID ($content_id)" "0 $content_id" \
  "$(eventually 30 "0 $content_id" raw Ada)"

part "Step 6: schema evolution under a compatibility rule"
check "add a BACKWARD compatibility rule to greetings-value" 204 \
  "$(code -X POST "$ARTIFACT/rules" -H 'Content-Type: application/json' -d '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}')"
# version <file>: registers the file's content as a new version; prints the HTTP status, and the
# violated rule if any
version() {
  local body status
  body=$(mktemp)
  status=$(curl -s --max-time 30 -o "$body" -w '%{http_code}' -X POST "$ARTIFACT/versions" -H 'Content-Type: application/json' \
    -d "$(jq -nc --rawfile s "$1" '{content: {content: $s, contentType: "application/json"}}')")
  echo "$status $(jq -r '.ruleViolation.ruleType // empty' "$body")" | sed 's/ $//'
  rm -f "$body"
}
check "a new required field (no default) is refused by the COMPATIBILITY rule" "400 COMPATIBILITY" \
  "$(version schemas/greeting-v2-incompatible.avsc)"
check "a new field with a default is accepted as version 2" 200 "$(version schemas/greeting-v2.avsc)"
check "  the artifact now has versions 1 and 2" "1 2" "$(json "$ARTIFACT/versions" | jq -r '[.versions[].version] | join(" ")')"
check "POST /greetings/send?name=Grace&message=Again" 204 "$(send Grace Again)"
check "the consumer still reads it" "Grace Again" "$(eventually 30 "Grace Again" received Grace)"
check "the producer still writes version 1's content ID: FIND_OR_CREATE_VERSION found its schema" "0 $content_id" \
  "$(eventually 30 "0 $content_id" raw Grace)"
check "  and registered no third version" "1 2" "$(json "$ARTIFACT/versions" | jq -r '[.versions[].version] | join(" ")')"

if grep -q ' ERROR ' "$APP_LOG"; then
  fail "the application logged errors (see $APP_LOG)"
else
  ok "the application logged no errors"
fi

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."
