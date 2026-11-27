#!/usr/bin/env bash
# Checks the canonical schema usage dashboard (distro/metrics/usage/grafana/schema-usage.json) against the running example
# stack: the file itself, the copy Grafana provisioned, and the real panel queries, answered by Prometheus directly and
# through Grafana's datasource. Read-only: it sends no traffic to the Registry and writes nothing.
#
# Usage: ./tests/dashboard-test.sh [--state FILE] [--dashboard FILE] [--scenario FILE] [--only SECTION[,SECTION]] [--external]
#   --state FILE      the run state of the last ./demo.sh (default .local/projects/<project>/run-state.json). Its exact
#                     evaluation time is the instant of the historical checks, never the current time
#   --dashboard FILE  a dashboard file other than the canonical one: only for mutation checks
#   --scenario FILE   scenarios on a stack prepared for them (the dashboard-scenarios case of tests/lifecycle-test.sh, entry point
#                     tests/dashboard-scenarios.sh, writes the file and runs this script); each scenario brings its own window
#   --only SECTION    run only these sections: static, provisioned, demo, scenarios (default: all that apply)
#   --external        endpoints only, for Prometheus and Grafana that this example did not start: the dashboard is not
#                     required to be provisioned and the Grafana container's mount is not inspected. Without this option both
#                     are required: a Grafana that cannot be asked, a runtime that does not answer, or a mount that is not the
#                     canonical directory read-only is a failed check (the checks that do not need them still run)
# Environment: GRAFANA_PORT/GRAFANA_URL, PROMETHEUS_PORT/PROMETHEUS_URL, COMPOSE_PROJECT_NAME as for verify.sh.
#
# Scenario file (JSON, "schemaVersion": 1, "scenarios": [...]); every time is Unix milliseconds, every window has
# fromMs < toMs, every count is a non-negative integer, and the file is validated before the first query:
#   {"kind": "overflow", "instance": ID, "fromMs": N, "toMs": N, "detail": N, "total": N}
#       a process at its series limit: "total" fetches were sent, "detail" of them are in detailed series
#   {"kind": "heartbeat-only", "instance": ID, "fromMs": N, "toMs": N}
#       a process that has exported only the heartbeat: selectable, an export age, no observed usage
#   {"kind": "processes", "primary": ID, "other": ID, "fromMs": N, "toMs": N, "primaryRows": [[client, type, value, operation, count]...], "otherRows": [...]}
#       two processes that reuse the same typed identifiers: each has its own rows, never merged
#   {"kind": "stopped", "instance": ID, "fromMs": N, "toMs": N, "rows": [[...]...]}
#       a process that stopped and expired from the Collector: its retained counts are still read and the export age is
#       UNKNOWN, while an instant selector at the window's end finds nothing (retained counts are not current health)
#   {"kind": "rates", "instance": ID, "fromMs": N, "toMs": N, "steadyFromMs": N, "requests": {"SERIALIZE": [completion second, ...], ...}, "lagSeconds": N, "tolerance": fraction}
#       controlled traffic: the chart's own range request through Prometheus and through Grafana. "requests" are the completion
#       seconds (Unix, floored) of every request the caller really made and saw answered, per operation; each is read as an
#       interval, the request having completed at some instant of that second and been counted by the exported counter up to
#       lagSeconds later (each request has its own delay). From steadyFromMs on, every returned point is checked against its own
#       operation's requests and its own 60 second window: the accepted rate runs from the requests certainly inside the window
#       to those possibly inside it, widened by the tolerance and one request for what rate() adds at the window edges (see
#       approx_rate_points in dashboard-lib.sh for the exact endpoints). Each operation has its own history: a point earlier
#       than its first request is refused. Presence is decided from the requested interval (fromMs to toMs) and the requests:
#       once an operation's first request has matured (its first recorded second, plus one second for the floored completion
#       second, plus the lag, plus the 60 second window; the exact formula is in approx_rate_points in dashboard-lib.sh) it
#       must return a point at the evaluation steps that remain (at most three required), however many other series
#       returned. Traffic after a point, uneven traffic and a later
#       operation never change the verdict of an earlier point, and a rate must be a finite number that is not negative
# Exit status: 0 every check passed; 1 at least one assertion failed and every query that was sent was answered; 3 at least one
# query failed (no connection, an HTTP or API error, a body of the wrong shape), whatever else failed. A status 3 run proves
# nothing about the values it could not read; callers that classify results (the mutation experiment) must treat it as an error
# of its own. Other failures to start (a bad option or file) exit 1 or 2 before any query.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib.sh
. ./lib.sh
# shellcheck source=tests/dashboard-lib.sh
. ./tests/dashboard-lib.sh
need_commands curl jq sort diff awk grep comm
# The values substituted below contain characters that bash 5.2 would treat as special in a replacement
shopt -u patsub_replacement 2> /dev/null || true

# lib.sh's DASHBOARD_FILE is the one authoritative path; --dashboard replaces it for mutation checks only
CANONICAL_FILE=$DASHBOARD_FILE
validate_project
STATE_FILE=$(state_dir)/run-state.json
SCENARIO_FILE=""
ONLY=""
external=false

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
while [ $# -gt 0 ]; do
  case "$1" in
    --state) [ $# -ge 2 ] || die "--state needs a file"; STATE_FILE=$2; shift 2 ;;
    --dashboard) [ $# -ge 2 ] || die "--dashboard needs a file"; DASHBOARD_FILE=$2; shift 2 ;;
    --scenario) [ $# -ge 2 ] || die "--scenario needs a file"; SCENARIO_FILE=$2; shift 2 ;;
    --only) [ $# -ge 2 ] || die "--only needs a section list"; ONLY=$2; shift 2 ;;
    --external) external=true; shift ;;
    -h | --help) usage; exit 0 ;;
    *) usage >&2; die "unknown option '$1'" ;;
  esac
done
[ -f "$DASHBOARD_FILE" ] || die "$DASHBOARD_FILE does not exist"
jq -e . "$DASHBOARD_FILE" > /dev/null 2>&1 || die "$DASHBOARD_FILE is not valid JSON"
DASHBOARD_UID=$(dashboard_uid "$DASHBOARD_FILE") || die "$DASHBOARD_FILE has no uid"
for sec in $(printf '%s' "$ONLY" | tr ',' ' '); do
  case "$sec" in static | provisioned | demo | scenarios) ;; *) die "unknown section '$sec' (static, provisioned, demo, scenarios)" ;; esac
done
# want <section>: whether a section runs
want() {
  [ -n "$ONLY" ] || return 0
  case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac
}

# read_scenarios <file>: the file is read once, as exactly one JSON object (a lone document of any other kind, none, or several are
# refused), and SCENARIO_JSON is that one compact snapshot, used for validation and for every scenario that runs
read_scenarios() {
  SCENARIO_JSON=$(jq -c -s 'if length == 1 and (.[0] | type) == "object" then .[0] else error("the file must hold exactly one JSON object, it holds \(length) document(s)") end' "$1" 2>&1) \
    || { printf 'error: %s is not usable: %s\n' "$1" "$SCENARIO_JSON" >&2; return 1; }
}
# validate_scenarios: every problem of the snapshot, before any query. Non-zero when there is one.
validate_scenarios() {
  local problems
  problems=$(jq -r '
    def uint: type == "number" and . >= 0 and . == floor;
    def row: type == "array" and length == 5 and (.[0:4] | map(type == "string") | all) and (.[4] | uint);
    def window: (.fromMs | uint) and (.toMs | uint) and (.fromMs < .toMs);
    def nonempty: type == "string" and length > 0;
    if .schemaVersion != 1 then "schemaVersion must be 1" else empty end,
    if (.scenarios | type) != "array" or (.scenarios | length) == 0 then "scenarios must be a non-empty array" else empty end,
    (.scenarios // [] | to_entries[] | .key as $i | .value as $s
      | (if ($s.kind | IN("overflow", "heartbeat-only", "processes", "stopped", "rates")) | not then "scenario \($i): unknown kind \($s.kind)" else empty end),
        (if ($s | window) | not then "scenario \($i): fromMs and toMs must be non-negative integers with fromMs < toMs" else empty end),
        (if ($s.kind | IN("overflow", "heartbeat-only", "stopped", "rates")) and (($s.instance | nonempty) | not) then "scenario \($i): instance must be a non-empty string" else empty end),
        (if $s.kind == "overflow" then
           (if (($s.detail | uint) and ($s.total | uint)) | not then "scenario \($i): detail and total must be non-negative integers"
            elif $s.total < $s.detail then "scenario \($i): total \($s.total) is less than detail \($s.detail)" else empty end)
         elif $s.kind == "processes" then
           (if ((($s.primary | nonempty) and ($s.other | nonempty)) | not) or $s.primary == $s.other then "scenario \($i): primary and other must be two different non-empty strings" else empty end),
           (if (($s.primaryRows | type) != "array") or (($s.otherRows | type) != "array") or ((($s.primaryRows // []) + ($s.otherRows // [])) | map(row) | all | not) then "scenario \($i): primaryRows and otherRows must be arrays of [client, type, value, operation, count]" else empty end)
         elif $s.kind == "stopped" then
           (if (($s.rows | type) != "array") or (($s.rows // []) | map(row) | all | not) then "scenario \($i): rows must be an array of [client, type, value, operation, count]" else empty end)
         elif $s.kind == "rates" then
           (if (($s.steadyFromMs | uint) | not) or $s.steadyFromMs < $s.fromMs or $s.steadyFromMs >= $s.toMs then "scenario \($i): steadyFromMs must be a non-negative integer inside the window" else empty end),
           (if (($s.requests | type) != "object") or (($s.requests | length) == 0) or (($s.requests | to_entries | map(.value | type == "array" and length > 0 and all(uint)) | all) | not) then "scenario \($i): requests must be an object of operation to a non-empty array of non-negative integer completion seconds" else empty end),
           (if (($s.lagSeconds | type) != "number") or $s.lagSeconds < 0 or $s.lagSeconds > 60 then "scenario \($i): lagSeconds must be a number from 0 to 60" else empty end),
           (if (($s.tolerance | type) != "number") or $s.tolerance <= 0 or $s.tolerance >= 1 then "scenario \($i): tolerance must be a number above 0 and below 1" else empty end)
         else empty end))
  ' <<< "$SCENARIO_JSON" 2>&1) || { printf 'error: the scenario file is not usable: %s\n' "$problems" >&2; return 1; }
  if [ -n "$problems" ]; then
    printf 'error: the scenario file is not usable:\n%s\n' "$problems" >&2
    return 1
  fi
}

if [ -n "$SCENARIO_FILE" ]; then
  [ -f "$SCENARIO_FILE" ] || die "$SCENARIO_FILE does not exist"
  read_scenarios "$SCENARIO_FILE" || exit 2
  validate_scenarios || exit 2
elif [ -n "$ONLY" ]; then
  case ",$ONLY," in *",scenarios,"*) die "--only scenarios needs --scenario FILE" ;; esac
fi

INSTANCE_SELECTOR=$(var_selector "$DASHBOARD_FILE") || die "the process variable of $DASHBOARD_FILE is not a label_values(<selector>,service_instance_id) query"
# The panels, by id: the stat of fetches, the overflow stat, the export age stat, the rate chart, the table of streams and the
# process candidates. The queries are read from the dashboard file at the moment they are sent
P_SUM=1
P_OVERFLOW=2
P_AGE=3
P_RATE=4
P_TABLE=5
P_PROCESSES=6

# --- static ------------------------------------------------------------------------------------------------------------

if want static; then
  part "The dashboard file $DASHBOARD_FILE"
  info "  uid $DASHBOARD_UID (read from the file; the scripts keep no second copy)"
  check "the file has no import inputs and no id (it imports into any Grafana and provisions as is)" "none" \
    "$(jq -r 'if has("__inputs") or has("id") then "present" else "none" end' "$DASHBOARD_FILE")"
  check "every datasource reference is the dashboard's own datasource variable" "" \
    "$(jq -r '[.. | objects | select(has("datasource")) | .datasource | select(type == "object") | select(.type != "grafana") | .uid | select(. != "${datasource}")] | unique | join(",")' "$DASHBOARD_FILE")"
  check "the datasource variable selects any Prometheus datasource" "datasource prometheus" \
    "$(jq -r '.templating.list[] | select(.name == "datasource") | "\(.type) \(.query)"' "$DASHBOARD_FILE")"
  check "only built in panel types are used" "stat table text timeseries" \
    "$(jq -r '[.panels[].type] | unique | join(" ")' "$DASHBOARD_FILE")"
  check "the Registry process is one explicit choice (not multi, no All)" "false false" \
    "$(jq -r '.templating.list[] | select(.name == "instance") | "\(.multi) \(.includeAll)"' "$DASHBOARD_FILE")"
  check "the filters are Registry process, client, type, operation, global ID and content ID" "instance client type operation global_id content_id" \
    "$(jq -r '[.templating.list[] | select(.name != "datasource") | .name] | join(" ")' "$DASHBOARD_FILE")"
  demo_hits=$(grep -n -i -E 'apicurio-usage-prometheus|127\.0\.0\.1|localhost|orders-service|billing-service|probe-|usage-telemetry-otel|"admin"|password|token' "$DASHBOARD_FILE" || true)
  check "nothing demo specific is in the file (no demo datasource UID, URL, client, project or credential)" "" "$demo_hits"
  uuid_hits=$(grep -n -i -E 'random UUID|UUID created|created at each start|created every time' "$DASHBOARD_FILE" || true)
  check "the process ID is never described as always a UUID created at each start" "" "$uuid_hits"
  unbound=$(jq -r '[.panels[].targets[]?.expr | select(test("identifier_value")) | select((test("identifier_type=\"global_id\"[^}]*identifier_value=~\"\\$global_id\"") and test("identifier_type=\"content_id\"[^}]*identifier_value=~\"\\$content_id\"")) | not)] | length' "$DASHBOARD_FILE")
  check "every identifier value matcher sits in a selector that fixes the identifier type" "0" "$unbound"
  check "the process filter lists the values of the usage metric and of the generic heartbeat" "true" \
    "$(jq -r '.templating.list[] | select(.name == "instance") | (.definition | test("apicurio_usage_schema_fetches_total") and test("jvm_cpu_count"))' "$DASHBOARD_FILE")"
  check "the process candidates come from a range vector of heartbeat samples, never from a timestamp or subquery (which miss a short life)" "true false" \
    "$(jq -r --argjson id "$P_PROCESSES" '.panels[] | select(.id == $id) | .targets[0].expr | "\(test("count_over_time\\(jvm_cpu_count\\[\\$\\{__range_ms\\}ms\\]\\)")) \(test("timestamp\\(|\\]\\s*:"))"' "$DASHBOARD_FILE")"
  check "the process table makes no claim of exact or newest heartbeat times" "0" \
    "$(jq -r --argjson id "$P_PROCESSES" '.panels[] | select(.id == $id) | [.description, .title] | map(select(test("exact|newest|latest|last heartbeat"; "i"))) | length' "$DASHBOARD_FILE")"
  check "the rate chart is a range request (the chart's own request is the one tested)" "true false" \
    "$(jq -r --argjson id "$P_RATE" '.panels[] | select(.id == $id) | .targets[0] | "\(.range) \(.instant)"' "$DASHBOARD_FILE")"
  check "the stat, table and rate take one stream once across scrape routes (max by the stream labels)" "0" \
    "$(jq -r '[.panels[] | select(.id == 1 or .id == 4 or .id == 5) | .targets[0].expr | select(test("max by \\(apicurio_registry_client_id, apicurio_registry_identifier_type, apicurio_registry_identifier_value, apicurio_registry_operation\\)") | not)] | length' "$DASHBOARD_FILE")"
fi

# --- provisioned -------------------------------------------------------------------------------------------------------

# check_mount: strict. A runtime that does not answer is a failed check, never a verified mount
check_mount() {
  local canonical_dir mount_line
  RUNTIME=${CONTAINER_RUNTIME:-}
  if [ -z "$RUNTIME" ]; then
    if command -v podman > /dev/null 2>&1; then RUNTIME=podman; elif command -v docker > /dev/null 2>&1; then RUNTIME=docker; fi
  fi
  if [ -z "$RUNTIME" ] || ! command -v "$RUNTIME" > /dev/null 2>&1; then
    fail "no container runtime here, so the Grafana container's mount is NOT verified (use --external when only the endpoints are to be checked)"
    return
  fi
  export PODMAN_COMPOSE_WARNING_LOGS=false
  COMPOSE=("$RUNTIME" compose)
  canonical_dir=$(cd "$(dirname "$CANONICAL_FILE")" && pwd -P)
  if service_container grafana && [ -n "$Q_OUT" ] && rtq inspect --format '{{range .Mounts}}{{.Source}} {{.Destination}} {{.RW}}{{"\n"}}{{end}}' "$Q_OUT"; then
    mount_line=$(printf '%s\n' "$BCAP_OUT" | awk -v d="$canonical_dir" '$1 == d')
    check "the Grafana container mounts the canonical directory $canonical_dir read-only" "$canonical_dir /etc/grafana/usage-dashboards false" "$mount_line"
  else
    fail "the Grafana container of project $PROJECT could not be inspected ($RUNTIME did not answer, or the project has no Grafana container): the mount is NOT verified (use --external when only the endpoints are to be checked)"
  fi
}

# compare_provisioned <dashboard API response>: sets CMP_STATE to equal, different or unknown. Both jq runs and diff are checked by
# their own status: a jq failure or a diff error (status above 1) is unknown, never an empty difference that reads as equal.
compare_provisioned() {
  local mine theirs rc=0
  CMP_STATE=unknown
  CMP_DIFF=""
  mine=$(mktemp "${TMPDIR:-/tmp}/dashboard-cmp.XXXXXX") || return 0
  theirs=$(mktemp "${TMPDIR:-/tmp}/dashboard-cmp.XXXXXX") || { rm -f "$mine"; return 0; }
  if jq -S 'del(.id, .version)' "$DASHBOARD_FILE" > "$mine" 2> /dev/null && jq -S '.dashboard | del(.id, .version)' <<< "$1" > "$theirs" 2> /dev/null; then
    CMP_DIFF=$(diff "$mine" "$theirs" 2> /dev/null) || rc=$?
    case $rc in
      0) CMP_STATE=equal ;;
      1) CMP_STATE=different; CMP_DIFF=$(printf '%s\n' "$CMP_DIFF" | head -5 | tr '\n' ' ') ;;
    esac
  fi
  rm -f "$mine" "$theirs"
}

if want provisioned; then
  part "The dashboard Grafana provisioned from the canonical file"
  if $external; then
    info "  note  --external: provisioning and the mount are not required for these endpoints"
  else
    if dashboard_state "$DASHBOARD_UID"; then
      ok "Grafana holds the dashboard $DASHBOARD_UID, provisioned from a file"
      if dash=$(http_get "$GRAFANA_URL/api/dashboards/uid/$DASHBOARD_UID" -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD"); then
        compare_provisioned "$dash"
        case "$CMP_STATE" in
          equal) ok "Grafana's copy equals the canonical file (apart from its own id and version)" ;;
          different) fail "Grafana's copy differs from the canonical file (apart from its own id and version): ${CMP_DIFF}" ;;
          *) fail "Grafana's copy and the canonical file could not be compared (a jq or diff error, not a difference): UNKNOWN" ;;
        esac
      else
        fail "Grafana's copy of the dashboard could not be read: UNKNOWN"
      fi
    else
      case "$DASHBOARD_STATE" in
        missing) fail "Grafana has no dashboard $DASHBOARD_UID at $GRAFANA_URL: it was not provisioned" ;;
        not-provisioned) fail "Grafana holds a dashboard $DASHBOARD_UID that no file provisioned" ;;
        *) fail "Grafana did not answer a valid dashboard $DASHBOARD_UID at $GRAFANA_URL: UNKNOWN" ;;
      esac
    fi
    check_mount
  fi
fi

# rows_tsv <json rows>: sorted tab separated rows
rows_tsv() { jq -r '.[] | @tsv' <<< "$1" | LC_ALL=C sort; }
# both_scalar <description> <expected> <panel>: a one value panel through both backends
both_scalar() {
  expect_scalar "$1" "$2" direct_scalar "$3"
  expect_scalar "$1" "$2" graf_scalar "$3"
}
# both_rows <description> <expected rows>: the table panel through both backends
both_rows() {
  expect_exact "$1" "$2" direct_rows "$P_TABLE"
  expect_exact "$1" "$2" graf_rows "$P_TABLE"
}
# both_processes <description> <expected ids>: the process candidates through both backends
both_processes() {
  expect_exact "$1" "$2" direct_processes "$P_PROCESSES"
  expect_exact "$1" "$2" graf_processes "$P_PROCESSES"
}
# count_of <processes text> <id>: how many rows are exactly that process
count_of() { printf '%s\n' "$1" | awk -v i="$2" '$0 == i { n++ } END { print n + 0 }'; }

# --- demo --------------------------------------------------------------------------------------------------------------

# A known-absent window: 2001-09-09, long before any telemetry of this Registry could exist. It is not inferred from the
# seed time: a process may have been running for days before the demo
ABSENT_TO_MS=1000000000000

if want demo; then
  [ -f "$STATE_FILE" ] || die "no run state at $STATE_FILE. Run ./demo.sh first"
  STATE_JSON=$(jq -c -s 'if length == 1 and (.[0] | type) == "object" and .[0].schemaVersion == 2 then .[0] else error("not one version 2 run state") end' "$STATE_FILE") \
    || die "$STATE_FILE is not one version 2 run state"
  INSTANCE=$(jq -r .source.instanceId <<< "$STATE_JSON")
  FROM_MS=$(jq -r .timeRange.fromMs <<< "$STATE_JSON")
  TO_MS=$(jq -r '(.evaluationTimeSeconds * 1000) | floor' <<< "$STATE_JSON")
  set_window "$FROM_MS" "$TO_MS" || die "the run state's time range is not a window that ends after it starts"
  V_INSTANCE=$INSTANCE
  info "run $(jq -r .runId <<< "$STATE_JSON"), process $INSTANCE, window $FROM_MS to $TO_MS ms (sealed at Prometheus time $(jq -r .evaluationTime <<< "$STATE_JSON"))"

  expected_all=$(jq -r '.expectedSeries[] | [.clientId, .identifierType, .identifierValue, .operation, (.count | tostring)] | @tsv' <<< "$STATE_JSON" | LC_ALL=C sort)
  GLOBALS=$(jq -r '[.schemas[].globalId | tostring] | unique | join("|")' <<< "$STATE_JSON")
  CONTENTS=$(jq -r '[.schemas[].contentId | tostring] | unique | join("|")' <<< "$STATE_JSON")
  PROBE=$(jq -r .clients.probe <<< "$STATE_JSON")
  FIRST_GLOBAL=$(jq -r '.schemas[0].globalId' <<< "$STATE_JSON")
  FIRST_CONTENT=$(jq -r '.schemas[0].contentId' <<< "$STATE_JSON")

  part "The table, with the run's own identifiers"
  reset_filters
  V_GLOBAL="($GLOBALS)"
  V_CONTENT="($CONTENTS)"
  both_rows "the run's own typed identifiers return exactly its expected rows" "$expected_all"

  part "The table, unfiltered: nothing is hidden from the full set"
  reset_filters
  if full_direct=$(direct_rows "$P_TABLE") && full_graf=$(graf_rows "$P_TABLE"); then
    if [ -n "$full_direct" ] && [ "$full_direct" = "$full_graf" ]; then
      ok "Prometheus and Grafana return the same $(printf '%s\n' "$full_direct" | wc -l | tr -d ' ') series for the process with every filter set to All"
    else
      fail "the unfiltered table differs between Prometheus and Grafana, or is empty"
    fi
    check "every expected series of the run is in the unfiltered table with its exact count" "" "$(comm -23 <(printf '%s\n' "$expected_all") <(printf '%s\n' "$full_direct") || true)"
    check "the unfiltered table has no overflow row (no row without client, type, value or operation)" "0" \
      "$(printf '%s\n' "$full_direct" | awk -F'\t' '$1 == "" || $2 == "" || $3 == "" || $4 == ""' | wc -l | tr -d ' ')"
    detail_sum=$(printf '%s\n' "$full_direct" | awk -F'\t' '{ s += $5 } END { print s + 0 }')
    both_scalar "the cumulative stat equals the sum of the table" "$detail_sum" "$P_SUM"
  else
    qfail "the unfiltered table could not be queried through both backends: UNKNOWN"
  fi

  part "Typed filters: global and content IDs never mix"
  reset_filters
  V_TYPE=global_id
  V_GLOBAL=$FIRST_GLOBAL
  both_rows "type global_id, Global ID $FIRST_GLOBAL" "$(awk -F'\t' -v t=global_id -v v="$FIRST_GLOBAL" '$2 == t && $3 == v' <<< "$expected_all")"
  reset_filters
  V_TYPE=content_id
  V_CONTENT=$FIRST_CONTENT
  both_rows "type content_id, Content ID $FIRST_CONTENT (a global ID with the same number may exist: $FIRST_GLOBAL)" "$(awk -F'\t' -v t=content_id -v v="$FIRST_CONTENT" '$2 == t && $3 == v' <<< "$expected_all")"
  reset_filters
  V_GLOBAL=$FIRST_GLOBAL
  V_CONTENT=999999999999
  if only_global=$(direct_rows "$P_TABLE"); then
    check "a Global ID selection with a Content ID that does not exist returns no content_id row" "0" "$(printf '%s\n' "$only_global" | awk -F'\t' '$2 == "content_id"' | wc -l | tr -d ' ')"
    check "the same selection returns the global_id rows of that ID" "$(awk -F'\t' -v v="$FIRST_GLOBAL" '$2 == "global_id" && $3 == v { n++ } END { print n + 0 }' <<< "$expected_all")" \
      "$(printf '%s\n' "$only_global" | awk -F'\t' -v v="$FIRST_GLOBAL" '$2 == "global_id" && $3 == v { n++ } END { print n + 0 }')"
  else
    qfail "the mixed Global ID and Content ID selection could not be queried: UNKNOWN"
  fi

  part "Client and operation filters"
  reset_filters
  V_GLOBAL="($GLOBALS)"
  V_CONTENT="($CONTENTS)"
  V_CLIENT=$PROBE
  both_rows "client $PROBE" "$(awk -F'\t' -v c="$PROBE" '$1 == c' <<< "$expected_all")"
  reset_filters
  V_GLOBAL="($GLOBALS)"
  V_CONTENT="($CONTENTS)"
  V_OPERATION=SERIALIZE
  both_rows "operation SERIALIZE" "$(awk -F'\t' '$4 == "SERIALIZE"' <<< "$expected_all")"

  part "No match is no data, never a zero"
  reset_filters
  V_CLIENT=no-such-client
  both_rows "a client that does not exist returns no rows" ""
  both_scalar "the cumulative stat has no series for it, so the panel shows its no observations text" none "$P_SUM"
  reset_filters
  V_INSTANCE=no-such-process-0
  both_rows "a Registry process that does not exist returns no rows" ""
  both_scalar "no export age for a process that does not exist (UNKNOWN, not zero)" none "$P_AGE"
  V_INSTANCE=$INSTANCE

  part "Export freshness and absence are different answers"
  reset_filters
  if age_direct=$(direct_scalar "$P_AGE") && age_graf=$(graf_scalar "$P_AGE"); then
    if [[ "$age_direct" =~ ^[0-9]+(\.[0-9]+)?$ ]] && [[ "$age_graf" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
      ok "the export age at the end of the run's window is a number of seconds (Prometheus $age_direct, Grafana $age_graf)"
    else
      fail "the export age at the end of the run's window is not a number (Prometheus '$age_direct', Grafana '$age_graf')"
    fi
  else
    qfail "the export age could not be queried through both backends: UNKNOWN"
  fi
  saved_from=$WIN_FROM_MS
  saved_to=$WIN_TO_MS
  set_window $((ABSENT_TO_MS - 3600000)) "$ABSENT_TO_MS"
  both_scalar "the export age in the known-absent window of 2001 has no value (UNKNOWN, not a stale age)" none "$P_AGE"
  both_scalar "the cumulative stat in the known-absent window has no value" none "$P_SUM"
  both_processes "the process candidates in the known-absent window are none" ""
  set_window "$saved_from" "$saved_to"

  part "The process candidates"
  if proc_direct=$(direct_processes "$P_PROCESSES") && proc_graf=$(graf_processes "$P_PROCESSES"); then
    check "Prometheus and Grafana return the same candidates" "$proc_direct" "$proc_graf"
    check "the table lists the run's Registry process once" "1" "$(count_of "$proc_direct" "$INSTANCE")"
    # the independent reference is the heartbeat samples of the same interval (an instant selector at the end is not one)
    if hb_ids=$(direct_process_ids "$CANDIDATE_REFERENCE"); then
      check "the candidates are exactly the processes with a heartbeat sample in the same interval (a scrape label is never a process)" "$hb_ids" "$proc_direct"
    else
      qfail "the heartbeat reference over the selected interval could not be queried: UNKNOWN"
    fi
  else
    qfail "the process candidates could not be queried through both backends: UNKNOWN"
  fi
  if inst_direct=$(direct "count by (service_instance_id) ($INSTANCE_SELECTOR)"); then
    check "the process filter query lists the run's process" "1" "$(jq -r --arg i "$INSTANCE" '[.data.result[].metric | select(.service_instance_id == $i)] | length' <<< "$inst_direct")"
  else
    qfail "the process filter query could not be evaluated: UNKNOWN"
  fi

  part "The rate chart's own range request on the finite demo traffic (rate values are checked with controlled traffic in the dashboard-scenarios case and dashboard-promql-test.sh)"
  reset_filters
  V_GLOBAL="($GLOBALS)"
  V_CONTENT="($CONTENTS)"
  for fn in direct_rate_points graf_rate_points; do
    if rates=$("$fn" "$P_RATE"); then
      check "$fn answers only with operations that exist and no negative rate" "0" \
        "$(printf '%s\n' "$rates" | awk -F'\t' 'NF >= 3 && ($1 !~ /^(SERIALIZE|DESERIALIZE|UNKNOWN)$/ || $3 < 0) { n++ } END { print n + 0 }')"
    else
      qfail "the rate chart's range query through $fn failed: UNKNOWN"
    fi
  done

  part "Overflow of the demo process"
  reset_filters
  both_scalar "this process has no overflow series: the panel shows its no overflow text, not 0" none "$P_OVERFLOW"
fi

# --- scenarios ---------------------------------------------------------------------------------------------------------

if want scenarios && [ -n "$SCENARIO_FILE" ]; then
  n=$(jq '.scenarios | length' <<< "$SCENARIO_JSON")
  i=0
  while [ "$i" -lt "$n" ]; do
    s=$(jq -c ".scenarios[$i]" <<< "$SCENARIO_JSON")
    kind=$(jq -r .kind <<< "$s")
    set_window "$(jq -r .fromMs <<< "$s")" "$(jq -r .toMs <<< "$s")" || die "scenario $i has no valid window"
    reset_filters
    # every kind but "processes" is about one process, validated before any query
    V_INSTANCE=$(jq -r '.instance // ""' <<< "$s")
    part "Scenario $i, $kind, window $WIN_FROM_MS to $WIN_TO_MS ms"
    case "$kind" in
      overflow)
        detail=$(jq -r .detail <<< "$s")
        total=$(jq -r .total <<< "$s")
        spill=$((total - detail))
        info "  $total fetches were sent to process $V_INSTANCE, $detail of them in detailed series, so $spill went to the overflow series"
        both_scalar "the overflow panel shows exactly $spill" "$spill" "$P_OVERFLOW"
        V_CLIENT=no-such-client
        both_scalar "the overflow panel is not filtered by a client filter (the overflow series keeps no client)" "$spill" "$P_OVERFLOW"
        reset_filters
        both_scalar "the cumulative stat holds exactly the $detail fetches of the detailed series (detail plus overflow is the total $total)" "$detail" "$P_SUM"
        if rows=$(direct_rows "$P_TABLE"); then
          check "the table has no overflow row" "0" "$(printf '%s\n' "$rows" | awk -F'\t' 'NF < 5 || $1 == "" || $2 == "" || $3 == "" || $4 == ""' | wc -l | tr -d ' ')"
          check "the table holds exactly the $detail fetches" "$detail" "$(printf '%s\n' "$rows" | awk -F'\t' '{ s += $5 } END { print s + 0 }')"
        else
          qfail "the table could not be queried: UNKNOWN"
        fi
        ;;
      heartbeat-only)
        for fn in direct_processes graf_processes; do
          if listed=$("$fn" "$P_PROCESSES"); then
            check "the process candidates list $V_INSTANCE once ($fn)" "1" "$(count_of "$listed" "$V_INSTANCE")"
          else
            qfail "the process candidates query through $fn failed: UNKNOWN"
          fi
        done
        if ids=$(direct "count by (service_instance_id) ($INSTANCE_SELECTOR)"); then
          check "the process filter's query finds $V_INSTANCE although it has no usage series" "1" "$(jq -r --arg i "$V_INSTANCE" '[.data.result[].metric | select(.service_instance_id == $i)] | length' <<< "$ids")"
        else
          qfail "the process filter query could not be evaluated: UNKNOWN"
        fi
        if age=$(direct_scalar "$P_AGE"); then
          if [[ "$age" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then ok "an export age exists for it ($age s)"; else fail "no numeric export age for $V_INSTANCE ('$age')"; fi
        else
          qfail "the export age could not be queried: UNKNOWN"
        fi
        both_scalar "no usage is observed (the stat has no series, not zero)" none "$P_SUM"
        both_rows "the table has no rows" ""
        both_scalar "no overflow series" none "$P_OVERFLOW"
        ;;
      processes)
        primary=$(jq -r .primary <<< "$s")
        other=$(jq -r .other <<< "$s")
        prows=$(rows_tsv "$(jq -c .primaryRows <<< "$s")")
        orows=$(rows_tsv "$(jq -c .otherRows <<< "$s")")
        V_INSTANCE=$primary
        both_rows "process $primary has exactly its own rows" "$prows"
        V_INSTANCE=$other
        both_rows "process $other has exactly its own rows" "$orows"
        shared=$(awk -F'\t' 'NR == FNR { a[$1 FS $2 FS $3 FS $4] = 1; next } ($1 FS $2 FS $3 FS $4) in a { n++ } END { print n + 0 }' <(printf '%s\n' "$prows") <(printf '%s\n' "$orows"))
        if [ "$shared" -gt 0 ]; then
          ok "$shared stream(s) have the same client, type, identifier and operation in both processes, each with its own count"
        else
          fail "the two processes share no typed identifier tuple, so the scenario does not exercise reused identifiers"
        fi
        if both=$(direct_processes "$P_PROCESSES"); then
          check "the process candidates list both processes" "1 1" "$(count_of "$both" "$primary") $(count_of "$both" "$other")"
        else
          qfail "the process candidates could not be queried: UNKNOWN"
        fi
        ;;
      stopped)
        srows=$(rows_tsv "$(jq -c .rows <<< "$s")")
        both_rows "the stopped process keeps its retained counts" "$srows"
        both_scalar "its export age at the end of the window is UNKNOWN (no heartbeat in the five minutes before the end)" none "$P_AGE"
        if cur=$(direct "count($USAGE_METRIC{$SOURCE_LABEL=\"$V_INSTANCE\"})") && hbn=$(direct "count(jvm_cpu_count{$SOURCE_LABEL=\"$V_INSTANCE\"})"); then
          check "at the window's end an instant selector finds none of its usage series: retained counts are not current health" "none" "$(jq -r '.data.result[0].value[1] // "none"' <<< "$cur")"
          check "  and none of its heartbeat series at that same instant" "none" "$(jq -r '.data.result[0].value[1] // "none"' <<< "$hbn")"
        else
          qfail "the instant selectors could not be queried: UNKNOWN"
        fi
        for fn in direct_processes graf_processes; do
          if listed=$("$fn" "$P_PROCESSES"); then
            check "the process candidates still list it, because the range holds its heartbeat samples ($fn)" "1" "$(count_of "$listed" "$V_INSTANCE")"
          else
            qfail "the process candidates query through $fn failed: UNKNOWN"
          fi
        done
        ;;
      rates)
        reqs=$(jq -r '.requests | to_entries[] | .key as $op | .value[] | "\($op)\t\(.)"' <<< "$s")
        tol=$(jq -r .tolerance <<< "$s")
        lag=$(jq -r .lagSeconds <<< "$s")
        steady=$(jq -r .steadyFromMs <<< "$s")
        approx_rate_points "controlled traffic" "$reqs" "$tol" "$lag" "$steady" direct_rate_points "$P_RATE"
        approx_rate_points "controlled traffic" "$reqs" "$tol" "$lag" "$steady" graf_rate_points "$P_RATE"
        ;;
    esac
    i=$((i + 1))
  done
fi

if [ "$QUERY_FAILURES" -gt 0 ]; then
  printf '\n%s check(s) failed, %s of them because a query failed (exit 3: those values are UNKNOWN).\n' "$failures" "$QUERY_FAILURES"
  exit 3
fi
if [ "$failures" -gt 0 ]; then
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
printf '\nAll checks passed.\n'
