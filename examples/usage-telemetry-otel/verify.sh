#!/usr/bin/env bash
# Checks that the stack works end to end and, from the saved run state, that Prometheus and Grafana
# still hold exactly the counters the last ./demo.sh run produced. Read-only: it sends no traffic to
# the Registry and writes nothing.
#
# Usage: ./verify.sh [--state FILE] [--stack-only] [--external]
#   --state FILE   the run state to check (default .local/projects/<project>/run-state.json, the project
#                  being COMPOSE_PROJECT_NAME or usage-telemetry-otel)
#   --stack-only   check the four services and the export path, not a demo run
#   --external     endpoints only, for a Registry, Prometheus or Grafana that this example did not start (set the
#                  *_URL variables): the schema usage dashboard is then not required to be provisioned. Without this
#                  option the dashboard of this example must be provisioned in Grafana, and a missing dashboard or a
#                  Grafana that cannot be asked fails the check
# Environment: MAX_EXPORT_AGE_SECONDS (default 30, set in lib.sh): how old the newest Registry export may be.
#              VERIFY_RETRY_SECONDS (default 12): how long a scrape target that is not up yet is awaited.
# Two different questions are answered separately. The run's counters are checked as they stood at the
# exact Prometheus time the demo sealed them (state field evaluationTime), so later fetches, a Registry
# restart or a teardown cannot change that history, and a wrong sealed count still fails. The current
# export health (is anything exporting now, how old is the newest export) is checked live; the age of the
# newest export is not proof that a process is still running.
# Exits 0 only if every check passed. A service that does not answer is reported as unknown, never as
# "no usage".
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=lib.sh
. ./lib.sh
need_commands curl jq sort diff

VERIFY_RETRY_SECONDS=${VERIFY_RETRY_SECONDS:-12}
validate_project
STATE_FILE=$(state_dir)/run-state.json
stack_only=false
external=false

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
while [ $# -gt 0 ]; do
  case "$1" in
    --state) [ $# -ge 2 ] || die "--state needs a file"; STATE_FILE=$2; shift 2 ;;
    --stack-only) stack_only=true; shift ;;
    --external) external=true; shift ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; die "unknown option '$1'" ;;
  esac
done

part "The four services"
check "Registry answers at $API/system/info" 200 "$(http_status "$API/system/info")"
check "Collector health endpoint answers at $COLLECTOR_HEALTH_URL/" 200 "$(http_status "$COLLECTOR_HEALTH_URL/")"
check "Prometheus is ready at $PROMETHEUS_URL" 200 "$(http_status "$PROMETHEUS_URL/-/ready")"
check "Grafana is healthy at $GRAFANA_URL" 200 "$(http_status "$GRAFANA_URL/api/health")"
check "the SQL usage recording is off (usage endpoints answer 409)" 409 "$(http_status "$API/admin/usage/summary")"

part "Prometheus scrapes the Collector, and the Registry's exports arrive"
# target_state <job>: 1 (up), 0 (down) or absent, from the newest scrape; non-zero when Prometheus does not answer
target_state() {
  local out
  TARGET_STATE=unknown
  out=$(prom_query "up{job=\"$1\"}") || return 2
  TARGET_STATE=$(jq -r '.data.result[0].value[1] // "absent"' <<< "$out")
  [ "$TARGET_STATE" = 1 ]
}
for job in otel-collector-usage otel-collector-internal; do
  # A target restarted a moment ago is scraped again within the scrape interval (5s); allow for that
  TARGET_STATE=unknown
  if retry_until "$VERIFY_RETRY_SECONDS" target_state "$job"; then
    ok "target $job is up"
  elif [ "$TARGET_STATE" = unknown ]; then
    fail "target $job: Prometheus did not answer, state UNKNOWN"
  else
    fail "target $job is up: got '$TARGET_STATE', expected '1'"
  fi
done
if beat=$(newest_heartbeat); then
  beat_instance=${beat% *}
  beat_age=$(($(now_epoch) - ${beat#* }))
  if [ "$beat_age" -le "$MAX_EXPORT_AGE_SECONDS" ]; then
    ok "the newest Registry export (process $beat_instance) is ${beat_age}s old (limit ${MAX_EXPORT_AGE_SECONDS}s)"
  else
    fail "the newest Registry export (process $beat_instance) is ${beat_age}s old (limit ${MAX_EXPORT_AGE_SECONDS}s): the Registry is not exporting, or the Collector or Prometheus is behind. Counts of fetches after that are unknown"
  fi
else
  fail "no Registry export is visible in Prometheus (no $HEARTBEAT_METRIC sample): the Registry is not exporting, or Prometheus cannot be queried. Usage is UNKNOWN, not zero"
fi
# Advisory only: a failed query is unknown, which is not the same as zero refused points
if out=$(prom_query 'sum(otelcol_receiver_refused_metric_points_total)'); then
  refused=$(jq -r '.data.result[0].value[1] // "0"' <<< "$out")
  if [ "$refused" = 0 ]; then ok "the Collector has refused no metric points"; else info "  note  the Collector has refused $refused metric points since it started (memory limiter or an unavailable pipeline)"; fi
else
  info "  note  UNKNOWN: Prometheus did not answer the query for refused Collector metric points, so whether any were refused is not known (advisory, not a failure)"
fi

part "Grafana reaches Prometheus"
ds_health=$(http_get "$GRAFANA_URL/api/datasources/uid/$GRAFANA_DATASOURCE_UID/health" -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" 2> /dev/null | jq -r '.status // "unknown"' || echo unreachable)
check "the provisioned datasource $GRAFANA_DATASOURCE_UID reports OK" OK "$ds_health"
# The canonical dashboard this example mounts from distro/metrics/usage must be provisioned (tests/dashboard-test.sh checks
# its content and queries). Only an explicit --external run, against services this example did not start, may skip that
DASHBOARD_UID=$(dashboard_uid) || die "$DASHBOARD_FILE is not a dashboard with a uid"
if $external; then
  info "  note  --external: the schema usage dashboard $DASHBOARD_UID is not required in this Grafana, and the mount is not checked"
elif dashboard_state "$DASHBOARD_UID"; then
  ok "the schema usage dashboard is provisioned in Grafana ($GRAFANA_URL/d/$DASHBOARD_UID)"
else
  case "$DASHBOARD_STATE" in
    missing) fail "Grafana has no dashboard $DASHBOARD_UID: the canonical dashboard was not provisioned (is $DASHBOARD_FILE mounted? see README.md; use --external only for services this example did not start)" ;;
    not-provisioned) fail "Grafana holds a dashboard $DASHBOARD_UID that no file provisioned, so it is not the canonical one this example mounts" ;;
    *) fail "Grafana did not answer a valid dashboard $DASHBOARD_UID at $GRAFANA_URL: whether it is provisioned is UNKNOWN" ;;
  esac
fi
q='count(up{job="otel-collector-usage"} == 1)'
# query_value <prom_query|grafana_query> <promql>: sets VALUE to the one numeric result. Non-zero, with the
# reason in QUERY_ERROR, when the query failed or returned no value: an error is never a value, so two
# failed queries can never be "equal".
query_value() {
  local out
  VALUE=""
  QUERY_ERROR=""
  if ! out=$("$1" "$2"); then
    QUERY_ERROR="no valid answer"
    return 1
  fi
  VALUE=$(jq -r '.data.result[0].value[1] // empty' <<< "$out")
  if ! [[ "$VALUE" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    VALUE=""
    QUERY_ERROR="it returned no value"
    return 1
  fi
}
direct_ok=false
through_ok=false
if query_value prom_query "$q"; then
  direct_ok=true
  direct=$VALUE
else
  fail "Prometheus query failed ($QUERY_ERROR) for $q: UNKNOWN, not a value to compare"
fi
if query_value grafana_query "$q"; then
  through_ok=true
  through=$VALUE
else
  fail "Grafana's query failed ($QUERY_ERROR) through datasource $GRAFANA_DATASOURCE_UID for $q: UNKNOWN, not a value to compare"
fi
if $direct_ok && $through_ok; then
  check "a query through Grafana returns what Prometheus returns ($direct)" "$direct" "$through"
fi

if $stack_only; then
  [ "$failures" -eq 0 ] || { printf '\n%s check(s) failed.\n' "$failures"; exit 1; }
  printf '\nAll checks passed (stack only; no demo run was checked).\n'
  exit 0
fi

part "The last demo run: $STATE_FILE"
# The state is read exactly once, by jq, from the bytes of the file: jq rejects what is not JSON (a raw NUL or control
# byte inside a string, trailing data, more than one value) before bash could drop or normalise it, and what it prints is
# the one complete object, serialized. Everything below comes from that value, so a ./demo.sh that replaces the file while
# this runs cannot make one run's time meet another run's series. Nothing is copied to disk.
if [ ! -f "$STATE_FILE" ]; then
  fail "no run state at $STATE_FILE. Run ./demo.sh first (or pass --stack-only)"
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
if ! STATE_JSON=$(jq -c -s 'if length == 1 and (.[0] | type) == "object" then .[0] else error("not one JSON object") end' "$STATE_FILE" 2> /dev/null); then
  fail "$STATE_FILE is not one valid JSON object as read from the file (malformed, a raw control byte, or more than one value); only version 2 (project scoped, sealed at an exact evaluation time) is supported. Run ./demo.sh again"
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
legacy=$(jq -r '.schemaVersion // "none"' <<< "$STATE_JSON" 2> /dev/null || echo unreadable)
if [ "$legacy" != 2 ]; then
  fail "$STATE_FILE has schemaVersion $legacy; only version 2 (project scoped, sealed at an exact evaluation time) is supported. Older unscoped state is neither read nor deleted: run ./demo.sh again"
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
if ! jq -e '(.project | type == "string") and (.source.instanceId | type == "string") and (.expectedSeries | length > 0) and (.evaluationTime | type == "string") and (.evaluationTime | test("^[0-9]+(\\.[0-9]+)?$"))
  and ([.schemas[] | (.globalId | type == "number") and (.contentId | type == "number")] | all)
  and ([.notCounted[] | (.request | type == "string") and (.request | test("^GET /ids/(globalIds|contentIds)/[0-9]+"))] | all)' <<< "$STATE_JSON" > /dev/null 2>&1; then
  fail "$STATE_FILE is not a complete version 2 run state (project, source, expectedSeries, evaluationTime, and the schemas' and not-counted requests' identifiers are required)"
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
state_project=$(jq -r .project <<< "$STATE_JSON")
if [ "$state_project" != "$PROJECT" ]; then
  fail "$STATE_FILE belongs to project '$state_project', not '$PROJECT'; set COMPOSE_PROJECT_NAME=$state_project to verify it"
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
run_id=$(jq -r .runId <<< "$STATE_JSON")
instance=$(jq -r .source.instanceId <<< "$STATE_JSON")
at=$(jq -r .evaluationTime <<< "$STATE_JSON")
info "  run $run_id, project $state_project, Registry process $instance, sealed at Prometheus time $at ($(jq -r .settledAt <<< "$STATE_JSON"))"

expected=$(jq -r '.expectedSeries[] | [.clientId, .identifierType, .identifierValue, .operation, (.count | tostring)] | @tsv' <<< "$STATE_JSON" | LC_ALL=C sort)
selector=$(usage_selector_for_run "$instance" <<< "$STATE_JSON") || { fail "$STATE_FILE yields no identifiers to select the run's series by"; printf '\n%s check(s) failed.\n' "$failures"; exit 1; }
# The counters at exactly the sealed time, never later: a later fetch of the same IDs has a later sample time

check_source() { # check_source <name> <prom_query|grafana_query>
  local out actual
  if ! out=$("$2" "$selector" "$at"); then
    fail "$1 did not answer: the run's counters are UNKNOWN"
    return
  fi
  actual=$(series_tsv <<< "$out")
  if [ "$actual" = "$expected" ]; then
    ok "$1 holds exactly the $(printf '%s\n' "$expected" | wc -l | tr -d ' ') expected series of this run, with their exact counts"
  else
    fail "$1 does not hold the expected series (diff of expected < and actual >):"
    diff <(printf '%s\n' "$expected") <(printf '%s\n' "$actual") >&2 || true
  fi
}
check_source Prometheus prom_query
check_source "Grafana's datasource" grafana_query

if live=$(heartbeat_epoch "$instance"); then
  age=$(($(now_epoch) - live))
  if [ "$age" -le "$MAX_EXPORT_AGE_SECONDS" ]; then
    info "  note  Registry process $instance last exported ${age}s ago (a process that stopped less than ${MAX_EXPORT_AGE_SECONDS}s ago looks the same)"
  else
    info "  note  Registry process $instance last exported ${age}s ago; it was stopped or restarted. The counts above are its retained history"
  fi
else
  info "  note  no recent export from Registry process $instance; the counts above are its retained history"
fi

if [ "$failures" -gt 0 ]; then
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
printf '\nAll checks passed.\n'
