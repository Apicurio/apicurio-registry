#!/usr/bin/env bash
# Proves the canonical dashboard's PromQL with promtool (the one in the pinned Prometheus image), on controlled series:
# no running stack is needed, only a container runtime and the image. The expressions are the ones in the dashboard file,
# with the dashboard variables and Grafana's time macros filled in; nothing is copied here.
#
# Usage: ./tests/dashboard-promql-test.sh [--dashboard FILE]
# Environment: CONTAINER_RUNTIME (podman or docker), PROMQL_IMAGE (default: the Prometheus image of docker-compose.yml).
# Cases:
#   streams   one logical stream (process, client, type, identifier, operation) seen through two Collector scrape routes,
#             one after the other and overlapping, is counted once in the stat, the table and the overflow; a process that
#             restarted with the same typed identifiers, another process and a content ID with a global ID's number stay apart
#   rates     counter progress becomes a rate per operation, once per stream (not once per scrape route), and a content ID
#             stream is added to a global ID stream of the same operation
#   processes the process candidates: a process is listed when Prometheus holds at least one heartbeat sample of it inside the
#             range (start excluded, end included), at exact bounds and arbitrary alignments, once per process when two scrape
#             routes report it, and however old that sample is
#   brief     a process whose whole life is one sample followed by a stale marker, and a brief one between the points a
#             minute-step subquery would sample, is still listed; a stale marker alone is not a sample
#   historical the independent reference used by dashboard-test.sh (the last heartbeat sample of the same interval) agrees with the
#             panel, while an instant inventory at the range end would miss a process whose heartbeat is stale by then
#   selector  a process that has exported only the heartbeat is a choice of the process filter
# Mutations of the dashboard queries (summing the scrape routes, a constant rate, a rate ten times too high, an instant
# selector for the candidates, a minute-step subquery for the candidates, a selector without the heartbeat) must each make
# promtool fail on an expectation. This is arithmetic on controlled series; it says nothing about the Grafana or Prometheus
# request path, which dashboard-test.sh checks.
# Exits 0 only if every check passed.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib.sh
. ./lib.sh
# shellcheck source=tests/dashboard-lib.sh
. ./tests/dashboard-lib.sh
need_commands jq sed awk

while [ $# -gt 0 ]; do
  case "$1" in
    --dashboard) [ $# -ge 2 ] || die "--dashboard needs a file"; DASHBOARD_FILE=$2; shift 2 ;;
    -h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option '$1'" ;;
  esac
done
[ -f "$DASHBOARD_FILE" ] || die "$DASHBOARD_FILE does not exist"

detect_runtime
IMAGE=${PROMQL_IMAGE:-$(sed -n 's/^    image: \(prom\/prometheus:[^[:space:]]*\).*/\1/p' docker-compose.yml | head -1)}
[ -n "$IMAGE" ] || die "no Prometheus image found in docker-compose.yml; set PROMQL_IMAGE"
ensure_local_dir
WORK=$(mktemp -d "$STATE_DIR/promql-test.XXXXXX") || die "could not create a working directory below $STATE_DIR"
trap 'rm -rf "$WORK"' EXIT
chmod 755 "$WORK"
printf 'groups: []\n' > "$WORK/rules.yml"

M=apicurio_usage_schema_fetches_total
# series <metric labels...> <values>: one input series. A label is name=value
series() {
  local values=$1 name="" labels="" l
  shift
  for l in "$@"; do
    if [ "${l%%=*}" = __name__ ]; then name=${l#*=}; else labels="$labels,${l%%=*}=\"${l#*=}\""; fi
  done
  printf "      - series: '%s{%s}'\n        values: '%s'\n" "$name" "${labels#,}" "$values"
}
usage() { # usage <values> <route> <process> <client> <type> <value> <operation>
  local values=$1 route=$2 p=$3 c=$4 t=$5 v=$6 o=$7
  series "$values" "__name__=$M" "job=$route" "instance=collector-$route:8889" "service_instance_id=$p" "apicurio_registry_client_id=$c" \
    "apicurio_registry_identifier_type=$t" "apicurio_registry_identifier_value=$v" "apicurio_registry_operation=$o"
}
overflow() { series "$1" "__name__=$M" "job=$2" "instance=collector-$2:8889" "service_instance_id=$3" "otel_metric_overflow=true"; }
heartbeat() { series "$1" "__name__=jvm_cpu_count" "job=$2" "instance=collector-$2:8889" "service_instance_id=$3"; }

# A test file: rules, interval, input series (arguments are the series text), then the checks appended by `case_*`
begin_file() { # begin_file <name> <interval>
  CUR=$WORK/$1.yml
  printf 'rule_files: [rules.yml]\nevaluation_interval: %s\ntests:\n  - interval: %s\n    input_series:\n' "$2" "$2" > "$CUR"
}
add_series() { printf '%s\n' "$1" >> "$CUR"; }
begin_checks() { printf '    promql_expr_test:\n' >> "$CUR"; }
# expect <expr> <eval time> <labels and value ...>: each remaining argument is "labels|value"; none means an empty answer
expect() {
  local expr=$1 at=$2 s
  shift 2
  printf '      - expr: %s\n        eval_time: %s\n' "$expr" "$at" >> "$CUR"
  if [ $# -eq 0 ]; then printf '        exp_samples: []\n' >> "$CUR"; return; fi
  printf '        exp_samples:\n' >> "$CUR"
  for s in "$@"; do printf "          - labels: '%s'\n            value: %s\n" "${s%|*}" "${s##*|}" >> "$CUR"; done
}

# run_promtool <file name>: promtool test rules on one generated file. Sets PT_OUT; the status is promtool's
run_promtool() {
  local rc=0
  bcap_all 180 "$RUNTIME" run --rm -v "$WORK:/t:ro" --entrypoint promtool "$IMAGE" test rules "/t/$1" || rc=$?
  PT_OUT=$BCAP_OUT
  return "$rc"
}
must_pass() { # must_pass <description> <file>
  local rc=0
  run_promtool "$2" || rc=$?
  if [ "$rc" -eq 0 ]; then ok "$1"; else fail "$1: promtool exited $rc"; printf '%s\n' "$PT_OUT" >&2; fi
}
# must_fail <description> <file>: a mutant must be caught by the expectations themselves (exit 1 and a FAILED report), not
# by a broken file
must_fail() {
  local rc=0
  run_promtool "$2" || rc=$?
  if [ "$rc" -eq 1 ] && grep -q 'FAILED' <<< "$PT_OUT" && grep -q 'exp:' <<< "$PT_OUT"; then
    ok "$1: promtool rejects the mutant (exit 1, an expectation failed)"
  else
    fail "$1: the mutant was not caught by an expectation (promtool exit $rc)"
    printf '%s\n' "$PT_OUT" | head -20 >&2
  fi
}

# Panel expressions with the variables of the case filled in, and the window's range for $__range_ms (set by the caller)
# MUT names a function that rewrites an expression (a mutant of the dashboard query); empty means the canonical one
MUT=""
panel() {
  local e
  e=$(panel_expr "$1")
  if [ -n "$MUT" ]; then e=$("$MUT" "$e" "$1"); fi
  fill_macros "$(fill "$e")"
}

LBL_X='apicurio_registry_client_id="c1",apicurio_registry_identifier_type="global_id",apicurio_registry_identifier_value="1",apicurio_registry_operation="SERIALIZE"'

# --- streams ----------------------------------------------------------------------------------------------------------

build_streams() { # build_streams <name>: with MUT set, only the first checks matter (a mutant must fail on them)
  begin_file "$1" 5s
  # X: one stream, Collector a until 40s with 8, Collector b from 30s with 10 (a 10 s overlap, a changed scrape route)
  add_series "$(usage '8x8 _x20' a p1 c1 global_id 1 SERIALIZE)"
  add_series "$(usage '_x6 10x14' b p1 c1 global_id 1 SERIALIZE)"
  # Y: content ID 1 (not global ID 1) of the same process, one route
  add_series "$(usage '5x20' a p1 c2 content_id 1 DESERIALIZE)"
  # X2: another global ID
  add_series "$(usage '2x20' a p1 c3 global_id 2 UNKNOWN)"
  # the same typed identifiers and client in another process, and in a process that restarted (a new ID)
  add_series "$(usage '7x20' a p2 c1 global_id 1 SERIALIZE)"
  add_series "$(usage '3x20' a p3 c1 global_id 1 SERIALIZE)"
  # overflow of p1 through two routes, and of p2
  add_series "$(overflow '3x8 _x20' a p1)"
  add_series "$(overflow '_x6 5x14' b p1)"
  add_series "$(overflow '9x20' a p2)"
  begin_checks
  WIN_RANGE_MS=100000
  V_INSTANCE=p1
  reset_filters
  expect "$(panel 1)" 100s '{}|17'
  expect "$(panel 5)" 100s \
    "{apicurio_registry_client_id=\"c1\",apicurio_registry_identifier_type=\"global_id\",apicurio_registry_identifier_value=\"1\",apicurio_registry_operation=\"SERIALIZE\"}|10" \
    "{apicurio_registry_client_id=\"c2\",apicurio_registry_identifier_type=\"content_id\",apicurio_registry_identifier_value=\"1\",apicurio_registry_operation=\"DESERIALIZE\"}|5" \
    "{apicurio_registry_client_id=\"c3\",apicurio_registry_identifier_type=\"global_id\",apicurio_registry_identifier_value=\"2\",apicurio_registry_operation=\"UNKNOWN\"}|2"
  expect "$(panel 2)" 100s '{}|5'
  # typed filters: global ID 1 only, content ID 1 only
  V_TYPE=global_id; V_GLOBAL=1
  [ -z "$MUT" ] || return 0
  expect "$(panel 5)" 100s "{$LBL_X}|10"
  V_TYPE=content_id; V_CONTENT=1; V_GLOBAL='.*'
  expect "$(panel 5)" 100s '{apicurio_registry_client_id="c2",apicurio_registry_identifier_type="content_id",apicurio_registry_identifier_value="1",apicurio_registry_operation="DESERIALIZE"}|5'
  # a global ID filter of 2 with the content ID filter of 1 and both types: global 2 and content 1, never global 1
  reset_filters; V_GLOBAL=2; V_CONTENT=1
  expect "$(panel 1)" 100s '{}|7'
  # the restarted process and the other process: their own counts for the same typed identifiers
  reset_filters; V_INSTANCE=p3
  expect "$(panel 1)" 100s '{}|3'
  V_INSTANCE=p2
  expect "$(panel 1)" 100s '{}|7'
  expect "$(panel 2)" 100s '{}|9'
  # a client that does not exist: no series at all, not a zero
  V_INSTANCE=p1; V_CLIENT=nobody
  expect "$(panel 1)" 100s
  expect "$(panel 5)" 100s
  # a process that does not exist: nothing, for the overflow too
  reset_filters; V_INSTANCE=p9
  expect "$(panel 2)" 100s
}

part "streams: one stream through several Collector scrape routes is counted once"
build_streams streams
must_pass "the stat, the table and the overflow count one stream once; typed identifiers, processes and no-match stay apart" streams.yml

# --- rates ------------------------------------------------------------------------------------------------------------

build_rates() { # build_rates <name>
  begin_file "$1" 5s
  # SERIALIZE: c1 +1 per 5 s through Collector a, also through Collector b from 60 s (an overlap); c3 +1 per 5 s; a content ID
  # stream c4 +1 per 5 s. DESERIALIZE: c2 +2 per 5 s. A second process p2 with the same identifiers is not selected.
  add_series "$(usage '0+1x40' a p1 c1 global_id 1 SERIALIZE)"
  add_series "$(usage '_x12 13+1x28' b p1 c1 global_id 1 SERIALIZE)"
  add_series "$(usage '0+1x40' a p1 c3 global_id 1 SERIALIZE)"
  add_series "$(usage '0+1x40' a p1 c4 content_id 7 SERIALIZE)"
  add_series "$(usage '0+2x40' a p1 c2 global_id 1 DESERIALIZE)"
  add_series "$(usage '0+5x40' a p2 c1 global_id 1 SERIALIZE)"
  begin_checks
  WIN_RANGE_MS=180000
  V_INSTANCE=p1
  reset_filters
  # promtool compares floats exactly: round to three decimals
  expect "round($(panel 4), 0.001)" 180s '{apicurio_registry_operation="SERIALIZE"}|0.6' '{apicurio_registry_operation="DESERIALIZE"}|0.4'
  V_OPERATION=SERIALIZE
  expect "round($(panel 4), 0.001)" 180s '{apicurio_registry_operation="SERIALIZE"}|0.6'
}
part "rates: counter progress per operation, once per stream"
build_rates rates
must_pass "the rate is 0.6 per second for SERIALIZE (three streams, one seen through two routes) and 0.4 for DESERIALIZE" rates.yml

# --- processes --------------------------------------------------------------------------------------------------------

build_processes() { # build_processes <name>
  begin_file "$1" 5s
  # p1: heartbeats every 5 s from 0 to 60 s through Collector a, and from 30 to 55 s through Collector b (`_xN` is N blank samples); p2: 0, 5 and 10 s
  add_series "$(heartbeat '1x12' a p1)"
  add_series "$(heartbeat '_x6 1x5' b p1)"
  add_series "$(heartbeat '1x2' a p2)"
  begin_checks
  local c at R p1 p2 want
  # each case: evaluation time in ms, range in ms, whether p1 and p2 are listed ("-": not listed)
  for c in "62000 100 - -" "62000 2000 - -" "62000 2100 1 -" "62000 70000 1 1" "61500 1600 1 -" "61500 1500 - -" \
    "61500 1501 1 -" "60000 1 1 -" "400000 400000 1 1" "400000 300000 - -" "400000 339999 - -" "400000 340001 1 -" \
    "62000 62000 1 1" "62000 61999 1 1" "62000 62001 1 1" "13000 3000 - -" "13000 3001 1 1" \
    "10000 10000 1 1" "10000 9999 1 1"; do
    # shellcheck disable=SC2086
    set -- $c
    at=$1 R=$2 p1=$3 p2=$4
    WIN_RANGE_MS=$R
    want=()
    [ "$p1" = - ] || want+=("{service_instance_id=\"p1\"}|1")
    [ "$p2" = - ] || want+=("{service_instance_id=\"p2\"}|1")
    expect "$(panel 6)" "${at}ms" ${want[@]+"${want[@]}"}
  done
}
part "processes: a process is listed when it has a heartbeat sample inside the range"
build_processes processes
must_pass "candidate lists: exact bounds (start excluded, end included), arbitrary alignment, a sample older than the lookback, one row per process" processes.yml

build_brief() { # build_brief <name>
  begin_file "$1" 5s
  # p3: one sample at 15 s, a stale marker at 20 s; p4: one sample at 125 s, a stale marker at 130 s, between the points a
  # one minute subquery step would sample, with nothing else of it before or after
  add_series "$(heartbeat '_x3 1 stale' a p3)"
  add_series "$(heartbeat '_x25 1 stale' a p4)"
  begin_checks
  local c at R p3 p4 want
  for c in "400000 400000 1 1" "400000 385000 - 1" "400000 385001 1 1" "400000 275000 - -" "400000 275001 - 1" \
    "130000 5000 - -" "130000 5001 - 1" "20000 6000 1 -" "20000 5000 - -" "20000 5001 1 -"; do
    # shellcheck disable=SC2086
    set -- $c
    at=$1 R=$2 p3=$3 p4=$4
    WIN_RANGE_MS=$R
    want=()
    [ "$p3" = - ] || want+=("{service_instance_id=\"p3\"}|1")
    [ "$p4" = - ] || want+=("{service_instance_id=\"p4\"}|1")
    expect "$(panel 6)" "${at}ms" ${want[@]+"${want[@]}"}
  done
}
part "brief: a short life that ends in a stale marker is listed; the stale marker itself is not a sample"
build_brief brief
must_pass "a heartbeat at 15 s and a stale marker at 20 s is listed over a 400 s range, a lone sample between subquery points too, at exact bounds" brief.yml

# --- historical ---------------------------------------------------------------------------------------------------------

build_historical() { # build_historical <name>
  begin_file "$1" 5s
  # p3: one sample at 15 s and a stale marker at 20 s; p5: heartbeats until the end of the evaluation
  add_series "$(heartbeat '_x3 1 stale' a p3)"
  add_series "$(heartbeat '1x80' a p5)"
  begin_checks
  WIN_RANGE_MS=400000
  # at 400 s over the whole 400 s: the panel and the independent reference (the last sample of each heartbeat in the same
  # interval) both list p3 and p5; an instant selector at the end lists only p5, so it cannot serve as the reference
  expect "$(panel 6)" 400s '{service_instance_id="p3"}|1' '{service_instance_id="p5"}|1'
  expect "$(fill_macros "$CANDIDATE_REFERENCE")" 400s '{service_instance_id="p3"}|1' '{service_instance_id="p5"}|1'
  expect 'group by (service_instance_id) (jvm_cpu_count)' 400s '{service_instance_id="p5"}|1'
  # a shorter interval that ends after p3's stale marker and starts after its sample holds only p5, in the panel and the reference
  WIN_RANGE_MS=300000
  expect "$(panel 6)" 400s '{service_instance_id="p5"}|1'
  expect "$(fill_macros "$CANDIDATE_REFERENCE")" 400s '{service_instance_id="p5"}|1'
}
part "historical: the candidates are compared with the heartbeat samples of the same interval, not with an instant inventory"
build_historical historical
must_pass "a heartbeat inside the range that is already stale at its end is a candidate and a reference member; an instant selector would miss it" historical.yml

# --- selector ---------------------------------------------------------------------------------------------------------

build_selector() { # build_selector <name> <selector override>
  begin_file "$1" 5s
  add_series "$(usage '4x10' a p1 c1 global_id 1 SERIALIZE)"
  add_series "$(heartbeat '1x10' a p1)"
  add_series "$(heartbeat '1x10' a p2)"
  add_series "$(usage '1x10' a p3 c1 global_id 1 SERIALIZE)"
  begin_checks
  local sel=${2:-$(var_selector)}
  expect "count by (service_instance_id) ($sel)" 40s '{service_instance_id="p1"}|2' '{service_instance_id="p2"}|1' '{service_instance_id="p3"}|1'
}
part "selector: a heartbeat-only process is a choice of the process filter"
build_selector selector
must_pass "p2 exported only the heartbeat and is selectable next to the processes that have usage" selector.yml

# --- mutants ----------------------------------------------------------------------------------------------------------

part "mutants: each must be rejected by the expectations"
mut_sum_routes() { printf '%s' "${1//max by (/sum by (}"; }
mut_zero_rate() { if [ "$2" = 4 ]; then printf 'vector(0)'; else printf '%s' "$1"; fi; }
mut_scaled_rate() { if [ "$2" = 4 ]; then printf '10 * (%s)' "$1"; else printf '%s' "$1"; fi; }
# shellcheck disable=SC2016 # the text is a Grafana macro, not a shell variable
# the candidates from an instant selector: the five minute lookback leaks samples from before the range
# shellcheck disable=SC2016 # the text is a Grafana macro, not a shell variable
mut_instant_candidates() {
  local pat='count_over_time(jvm_cpu_count[${__range_ms}ms])'
  if [ "$2" = 6 ]; then printf '%s' "${1//"$pat"/jvm_cpu_count}"; else printf '%s' "$1"; fi
}
# the candidates from a minute-step subquery of timestamps: a short life falls between its points
# shellcheck disable=SC2016 # the text is a Grafana macro, not a shell variable
mut_subquery_candidates() { if [ "$2" = 6 ]; then printf '%s' 'group by (service_instance_id) (max_over_time(timestamp(jvm_cpu_count)[${__range_ms}ms:1m]))'; else printf '%s' "$1"; fi; }
MUT=mut_sum_routes; build_streams m-sum-routes
must_fail "summing the scrape routes instead of taking one stream (stat, table, overflow)" m-sum-routes.yml
MUT=mut_sum_routes; build_rates m-rate-sum-routes
must_fail "summing the scrape routes of a rate" m-rate-sum-routes.yml
MUT=mut_zero_rate; build_rates m-rate-zero
must_fail "a constant zero rate" m-rate-zero.yml
MUT=mut_scaled_rate; build_rates m-rate-scaled
must_fail "a rate ten times too high" m-rate-scaled.yml
MUT=mut_instant_candidates; build_processes m-instant-candidates
must_fail "candidates from an instant selector (the lookback leaks a sample from before the range)" m-instant-candidates.yml
MUT=mut_subquery_candidates; build_brief m-subquery-candidates
must_fail "candidates from a minute-step subquery (a short life between its points is lost)" m-subquery-candidates.yml
MUT=""
no_hb=$(var_selector)
no_hb=${no_hb//|jvm_cpu_count/}
build_selector m-no-heartbeat "$no_hb"
must_fail "a process selector without the heartbeat" m-no-heartbeat.yml

if [ "$failures" -gt 0 ]; then
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
printf '\nAll checks passed.\n'
