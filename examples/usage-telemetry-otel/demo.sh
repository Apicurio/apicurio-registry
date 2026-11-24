#!/usr/bin/env bash
# Sends real schema registrations and fetches to the Registry and checks that Prometheus holds EXACTLY
# the counters those requests must produce, for the one Registry process that served them. It also
# checks the requests that must not be counted, that the SQL usage recording is still off, and that
# Grafana's datasource returns the same numbers.
#
# Usage: ./demo.sh     (after ./setup.sh, or `docker compose up -d`; needs curl and jq)
# Safe to run any number of times: every run creates new schemas (the run ID is part of their content),
# so no run touches another run's series or deletes anything. Each run writes its fixture to
# .local/projects/<project>/run-state.json (see README.md, "Run state"), which ./verify.sh and later browser tests read.
# Ends with "All checks passed." and exit status 0, or a FAIL line, a diagnosis and exit status 1.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh
need_commands curl jq od tr sort diff

# Seconds the telemetry may take to travel Registry -> Collector -> Prometheus (export every 5s, scrape every 5s)
TELEMETRY_TIMEOUT=${TELEMETRY_TIMEOUT:-90}

GROUP=usage-telemetry-otel-demo
CLIENT_ORDERS=orders-service
CLIENT_BILLING=billing-service
# IDs no schema has: fetching them must fail and must not be counted
MISSING_GLOBAL_ID=9000000000000000
MISSING_CONTENT_ID=9000000000000001

RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$(od -An -N3 -tx1 /dev/urandom | tr -d ' \n')
RUN_COMPACT=${RUN_ID//-/}
PROBE_CLIENT=probe-$RUN_ID
START_EPOCH=$(now_epoch)

TAB=$'\t'
schema_rows=""       # artifactId, version, globalId, contentId (tab separated)
negative_rows=""     # description, request, client, status, identifier
expected_rows=""     # client, type, value, operation, count

summary_and_exit() {
  if [ "$failures" -gt 0 ]; then
    printf '\n%s check(s) failed.\n' "$failures"
    exit 1
  fi
  printf '\nAll checks passed.\n'
  exit 0
}

# abort <message>: a check that makes the rest meaningless
abort() {
  fail "$1"
  summary_and_exit
}

# --- requests ---------------------------------------------------------------------------------------

# avro <extra fields as a JSON array>: an Avro record unique to this run
avro() {
  jq -nc --arg ns "com.example.r$RUN_COMPACT" --argjson extra "$1" \
    '{type: "record", name: "Order", namespace: $ns, fields: ([{name: "orderId", type: "string"}] + $extra)}'
}

# create_artifact <artifactId> <schema>: "globalId contentId" of its first version
create_artifact() {
  api_post "/groups/$GROUP/artifacts" "$(jq -nc --arg a "$1" --arg c "$2" \
    '{artifactId: $a, artifactType: "AVRO", firstVersion: {version: "1", content: {content: $c, contentType: "application/json"}}}')" \
    | jq -r '"\(.version.globalId) \(.version.contentId)"'
}

# add_version <artifactId> <version> <schema>: "globalId contentId"
add_version() {
  api_post "/groups/$GROUP/artifacts/$1/versions" "$(jq -nc --arg v "$2" --arg c "$3" \
    '{version: $v, content: {content: $c, contentType: "application/json"}}')" \
    | jq -r '"\(.globalId) \(.contentId)"'
}

# fetch <client or -> <operation or -> <globalIds|contentIds> <id> [suffix]: the HTTP status
fetch() {
  local client=$1 op=$2 kind=$3 id=$4 suffix=${5:-} headers=()
  [ "$client" = - ] || headers+=(-H "X-Registry-Client-Id: $client")
  [ "$op" = - ] || headers+=(-H "X-Registry-Operation: $op")
  http_status "$API/ids/$kind/$id$suffix" ${headers[@]+"${headers[@]}"}
}

# fetches <client> <operation or -> <globalIds|contentIds> <id> <times>: sends them, checks every one
# answered 200, and records the counter they must produce
fetches() {
  local client=$1 op=$2 kind=$3 id=$4 times=$5 sent=0 bad=0 type value_op
  while [ "$sent" -lt "$times" ]; do
    [ "$(fetch "$client" "$op" "$kind" "$id")" = 200 ] || bad=$((bad + 1))
    sent=$((sent + 1))
  done
  type=global_id
  [ "$kind" = contentIds ] && type=content_id
  value_op=$op
  [ "$op" = - ] && value_op=UNKNOWN
  check "$times x GET /ids/$kind/$id as $client ($value_op) answered 200" 0 "$bad"
  expected_rows="$expected_rows$client$TAB$type$TAB$id$TAB$value_op$TAB$times
"
}

# negative <description> <expected status> <client or -> <kind> <id> <suffix>
negative() {
  local description=$1 want=$2 client=$3 kind=$4 id=$5 suffix=$6 got
  got=$(fetch "$client" - "$kind" "$id" "$suffix")
  check "not counted: $description (HTTP $want)" "$want" "$got"
  negative_rows="$negative_rows$description${TAB}GET /ids/$kind/$id$suffix$TAB$client$TAB$want$TAB$id
"
}

# --- Prometheus and Grafana ------------------------------------------------------------------------

PROBE_STATE=unknown
INSTANCE=""
# find_instance: which Registry process served this run? The probe client is unique to the run, so the
# one series that carries its name names the process. Nothing is guessed from "the newest" data.
# shellcheck disable=SC2329 # called through retry_until
find_instance() {
  local out count
  if ! out=$(prom_query "$USAGE_METRIC{apicurio_registry_client_id=\"$PROBE_CLIENT\"}"); then
    PROBE_STATE=unknown
    return 1
  fi
  count=$(jq '.data.result | length' <<< "$out")
  case "$count" in
    0) PROBE_STATE=absent; return 1 ;;
    1) PROBE_STATE=found
       INSTANCE=$(jq -r ".data.result[0].metric.$SOURCE_LABEL // empty" <<< "$out")
       [ -n "$INSTANCE" ] || { PROBE_STATE=unlabeled; return 1; } ;;
    *) PROBE_STATE=ambiguous; return 1 ;;
  esac
}

EXPECTED=""
ACTUAL=""
QUERY_FAILED=""
# matches_exactly <prom_query|grafana_query> <selector> [evaluation time]: the series of this run's
# identifiers equal the expected counters exactly: no missing series, no extra series, no different value
matches_exactly() {
  local out
  QUERY_FAILED=""
  if ! out=$("$1" "$2" "${3:-}"); then
    QUERY_FAILED=yes
    return 1
  fi
  ACTUAL=$(series_tsv <<< "$out")
  [ "$ACTUAL" = "$EXPECTED" ]
}

# --- the run -----------------------------------------------------------------------------------------

part "Before you start"
info "  run $RUN_ID, Registry $API"
ensure_local_dir
retry_until 60 http_get "$API/system/info" > /dev/null 2>&1 || abort "the Registry does not answer at $API/system/info within 60s. Start the stack with ./setup.sh (same ports)"
ok "the Registry answers"
retry_until 60 http_get "$PROMETHEUS_URL/-/ready" > /dev/null 2>&1 || abort "Prometheus does not answer at $PROMETHEUS_URL within 60s. Start the stack with ./setup.sh (same ports)"
ok "Prometheus answers"
check "the SQL usage recording is off (the usage endpoints answer 409)" 409 "$(http_status "$API/admin/usage/summary")"

part "Step 1: schemas for this run, and the identifiers the Registry gave them"
v1=$(avro '[]')
v2=$(avro '[{"name": "currency", "type": "string"}]')
created=$(create_artifact "orders-$RUN_ID" "$v1") || abort "could not create orders-$RUN_ID"
read -r ORDERS_V1_GID ORDERS_V1_CID <<< "$created"
created=$(add_version "orders-$RUN_ID" 2 "$v2") || abort "could not add version 2 of orders-$RUN_ID"
read -r ORDERS_V2_GID ORDERS_V2_CID <<< "$created"
created=$(create_artifact "orders-copy-$RUN_ID" "$v1") || abort "could not create orders-copy-$RUN_ID"
read -r COPY_V1_GID COPY_V1_CID <<< "$created"
schema_rows="orders-$RUN_ID${TAB}1$TAB$ORDERS_V1_GID$TAB$ORDERS_V1_CID
orders-$RUN_ID${TAB}2$TAB$ORDERS_V2_GID$TAB$ORDERS_V2_CID
orders-copy-$RUN_ID${TAB}1$TAB$COPY_V1_GID$TAB$COPY_V1_CID
"
printf '  %-46s %-8s %-10s %s\n' ARTIFACT VERSION GLOBAL_ID CONTENT_ID
printf '%s' "$schema_rows" | while IFS=$'\t' read -r a v g c; do printf '  %-46s %-8s %-10s %s\n' "$a" "$v" "$g" "$c"; done
check "orders-copy 1 has the same content as orders 1, so the same content ID" "$ORDERS_V1_CID" "$COPY_V1_CID"
check "  but its own global ID (a global ID is one version; a content ID can be shared)" yes "$([ "$COPY_V1_GID" != "$ORDERS_V1_GID" ] && echo yes || echo no)"

part "Step 2: requests that must NOT be counted (sent first, so a stray count shows up in the checks below)"
negative "no client ID" 200 - globalIds "$ORDERS_V1_GID" ""
negative "an invalid client ID" 200 "not a valid id!" globalIds "$ORDERS_V1_GID" ""
negative "the references of a schema" 200 "$CLIENT_ORDERS" globalIds "$ORDERS_V1_GID" /references
negative "a global ID that does not exist" 404 "$CLIENT_ORDERS" globalIds "$MISSING_GLOBAL_ID" ""
negative "a content ID that does not exist" 404 "$CLIENT_ORDERS" contentIds "$MISSING_CONTENT_ID" ""

part "Step 3: requests that MUST be counted (two stable client names, a finite number of fetches)"
fetches "$CLIENT_ORDERS" SERIALIZE globalIds "$ORDERS_V1_GID" 3
fetches "$CLIENT_ORDERS" DESERIALIZE contentIds "$ORDERS_V1_CID" 2
fetches "$CLIENT_ORDERS" SERIALIZE globalIds "$COPY_V1_GID" 1
fetches "$CLIENT_BILLING" DESERIALIZE globalIds "$ORDERS_V1_GID" 1
fetches "$CLIENT_BILLING" - globalIds "$ORDERS_V2_GID" 2
# Last: once its series is visible, every earlier request has been exported too
fetches "$PROBE_CLIENT" - globalIds "$ORDERS_V1_GID" 1
EXPECTED=$(printf '%s' "$expected_rows" | LC_ALL=C sort | sed '/^$/d')
schemas_json=$(printf '%s' "$schema_rows" | jq -R -s -c --arg g "$GROUP" \
  'split("\n") | map(select(length > 0) | split("\t") | {groupId: $g, artifactId: .[0], version: .[1], globalId: (.[2] | tonumber), contentId: (.[3] | tonumber)})')
negatives_json=$(printf '%s' "$negative_rows" | jq -R -s -c \
  'split("\n") | map(select(length > 0) | split("\t") | {description: .[0], request: .[1], clientId: (if .[2] == "-" then null else .[2] end), status: (.[3] | tonumber), identifierValue: .[4]})')

part "Step 4: which Registry process served this run?"
if ! retry_until "$TELEMETRY_TIMEOUT" find_instance; then
  case "$PROBE_STATE" in
    unknown) abort "Prometheus did not answer within ${TELEMETRY_TIMEOUT}s, so the counters are UNKNOWN (this is not evidence that nothing was counted). Check $PROMETHEUS_URL and ./verify.sh" ;;
    ambiguous) abort "more than one series carries the probe client $PROBE_CLIENT; the run cannot be attributed to one Registry process" ;;
    unlabeled) abort "the probe series has no $SOURCE_LABEL label: the Collector configuration does not keep the Registry's process identity (see config/otel-collector.yaml)" ;;
  esac
  if beat=$(newest_heartbeat 2> /dev/null); then
    overflow=$(prom_query "$USAGE_METRIC{otel_metric_overflow=\"true\",$SOURCE_LABEL=\"${beat% *}\"}" 2> /dev/null | jq -r '.data.result[0].value[1] // empty' || true)
    if [ -n "$overflow" ]; then
      abort "Registry process ${beat% *} has reached its series limit (apicurio.usage.metrics.max-series): $overflow fetches were folded into the overflow series ($USAGE_METRIC{otel_metric_overflow=\"true\"}), which has no client or identifier labels, so this run's series cannot be told apart. Totals stay exact, detail does not. Restart the Registry (series are cumulative until then) or raise the limit (APICURIO_USAGE_METRICS_MAX_SERIES)"
    fi
    abort "the Registry's exports reach Prometheus, but $USAGE_METRIC did not appear within ${TELEMETRY_TIMEOUT}s. The Registry image probably predates the usage metrics feature, or APICURIO_USAGE_METRICS_ENABLED is not true on it. Check the image (REGISTRY_IMAGE) and ./setup.sh's output"
  fi
  abort "nothing from the Registry reached Prometheus within ${TELEMETRY_TIMEOUT}s (no heartbeat either). Run ./verify.sh to see which hop is broken"
fi
ok "the probe $PROBE_CLIENT was counted by Registry process $INSTANCE"
# The same typed (identifier type, value) pairs verify.sh derives from the saved state: a global ID and a content ID
# with the same number are different identifiers, and the IDs of the requests that must not be counted are included
SELECTOR=$(jq -nc --argjson s "$schemas_json" --argjson n "$negatives_json" '{schemas: $s, notCounted: $n}' | usage_selector_for_run "$INSTANCE") \
  || abort "could not build the series selector of this run from its identifiers"

part "Step 5: the exact counters, from Prometheus"
if ! retry_until "$TELEMETRY_TIMEOUT" matches_exactly prom_query "$SELECTOR"; then
  if [ -n "$QUERY_FAILED" ]; then
    abort "Prometheus did not answer within ${TELEMETRY_TIMEOUT}s, so the counters are UNKNOWN"
  fi
  printf '  diff of expected (<) and actual (>) series, as client, type, id, operation, count:\n' >&2
  diff <(printf '%s\n' "$EXPECTED") <(printf '%s\n' "$ACTUAL") >&2 || true
  abort "the counters of Registry process $INSTANCE are not the expected ones within ${TELEMETRY_TIMEOUT}s"
fi
ok "the series with this run's identifiers match exactly: 6 series, none missing, none extra, so none of step 2's requests was counted"
# A later export must show the same numbers: the first match could have been a scrape taken half way
# An unknown heartbeat is unknown: it must not become "no export seen yet" (0), which any later export would beat
first_beat=$(heartbeat_epoch "$INSTANCE") || abort "the heartbeat of Registry process $INSTANCE could not be read from Prometheus, so a later export cannot be recognised and the run cannot be sealed"
# shellcheck disable=SC2329 # called through retry_until
later_export() {
  local beat
  beat=$(heartbeat_epoch "$INSTANCE") || return 1
  [ "$beat" -gt "$first_beat" ]
}
retry_until 60 later_export || abort "no later export from Registry process $INSTANCE arrived within 60s, so the numbers cannot be called settled"
matches_exactly prom_query "$SELECTOR" || abort "the counters changed after a later export"
ok "they are unchanged by the next export, so the counts are final"
SETTLED_EPOCH=$(now_epoch)
# Seal the run at an exact instant taken from the data, never from this machine's clock: the newest
# sample time among the series just verified. Later fetches land in later exports, so they have later
# sample times and cannot enter a query made at exactly this time. The same selector at exactly that
# time must return exactly the expected series before the run state is written.
out=$(prom_query "timestamp($SELECTOR)") || abort "Prometheus did not answer while the run was being sealed, so its counters are UNKNOWN"
EVAL_TIME=$(jq -r '.data.result[].value[1]' <<< "$out" | sort -n | tail -1)
[ -n "$EVAL_TIME" ] || abort "the sealed series have no sample time"
matches_exactly prom_query "$SELECTOR" "$EVAL_TIME" || abort "the snapshot at sample time $EVAL_TIME does not hold exactly the expected series, so the run cannot be sealed"
ok "the run is sealed at Prometheus time $EVAL_TIME, the newest sample time of its series, and that snapshot is exactly the expected one"
while IFS=$'\t' read -r client type value op count; do
  [ -n "$client" ] && ok "$client  $type=$value  $op  = $count"
done <<< "$EXPECTED"

part "Step 6: the same numbers through Grafana's datasource"
if retry_until 30 matches_exactly grafana_query "$SELECTOR" "$EVAL_TIME"; then
  ok "Grafana's Prometheus datasource returns the identical 6 series"
elif [ -n "$QUERY_FAILED" ]; then
  fail "Grafana's datasource did not answer at $GRAFANA_URL (unknown, not zero)"
else
  fail "Grafana's datasource returned different series than Prometheus"
fi

part "Step 7: save the run state"
if [ "$failures" -gt 0 ]; then
  info "  skip  not written: a run with failed checks must not become the fixture later checks trust"
  summary_and_exit
fi
expected_json=$(printf '%s\n' "$EXPECTED" | jq -R -s -c \
  'split("\n") | map(select(length > 0) | split("\t") | {clientId: .[0], identifierType: .[1], identifierValue: .[2], operation: .[3], count: (.[4] | tonumber)})')
state=$(jq -n \
  --arg runId "$RUN_ID" --arg project "$PROJECT" --arg evalTime "$EVAL_TIME" --arg instance "$INSTANCE" --arg probe "$PROBE_CLIENT" \
  --arg metric "$USAGE_METRIC" --arg heartbeat "$HEARTBEAT_METRIC" --arg sourceLabel "$SOURCE_LABEL" \
  --arg api "$API" --arg prom "$PROMETHEUS_URL" --arg grafana "$GRAFANA_URL" --arg ds "$GRAFANA_DATASOURCE_UID" \
  --argjson start "$START_EPOCH" --argjson settled "$SETTLED_EPOCH" \
  --arg startIso "$(iso_from_epoch "$START_EPOCH")" --arg settledIso "$(iso_from_epoch "$SETTLED_EPOCH")" \
  --argjson schemas "$schemas_json" --argjson expected "$expected_json" --argjson negatives "$negatives_json" \
  --arg orders "$CLIENT_ORDERS" --arg billing "$CLIENT_BILLING" \
  '{
    schemaVersion: 2,
    project: $project,
    runId: $runId,
    startedAt: $startIso,
    settledAt: $settledIso,
    startedAtEpochSeconds: $start,
    settledAtEpochSeconds: $settled,
    evaluationTime: $evalTime,
    evaluationTimeSeconds: ($evalTime | tonumber),
    timeRange: {fromMs: (($start - 30) * 1000), toMs: (($settled + 30) * 1000)},
    endpoints: {registryApi: $api, prometheus: $prom, grafana: $grafana, grafanaDatasourceUid: $ds},
    source: {label: $sourceLabel, instanceId: $instance},
    metric: {
      name: $metric,
      heartbeatName: $heartbeat,
      labels: {clientId: "apicurio_registry_client_id", identifierType: "apicurio_registry_identifier_type",
               identifierValue: "apicurio_registry_identifier_value", operation: "apicurio_registry_operation"}
    },
    clients: {stable: [$orders, $billing], probe: $probe},
    schemas: $schemas,
    expectedSeries: $expected,
    notCounted: $negatives
  }')
printf '%s\n' "$state" | write_atomically "$STATE_DIR/runs/$RUN_ID.json" || abort "could not write $STATE_DIR/runs/$RUN_ID.json"
printf '%s\n' "$state" | write_atomically "$STATE_DIR/run-state.json" || abort "could not write $STATE_DIR/run-state.json"
ok "wrote $STATE_DIR/run-state.json (and runs/$RUN_ID.json)"
info "  Prometheus: $PROMETHEUS_URL/graph?g0.expr=$USAGE_METRIC%7B$SOURCE_LABEL%3D%22$INSTANCE%22%7D"

summary_and_exit
