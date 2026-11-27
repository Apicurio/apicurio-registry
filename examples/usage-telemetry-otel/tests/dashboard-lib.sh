# shellcheck shell=bash
# shellcheck disable=SC2034 # variables set here are read by the scripts that source this file
# Helpers of the dashboard checks (tests/dashboard-test.sh, the dashboard-scenarios case of tests/lifecycle-test.sh,
# dashboard-promql-test.sh and dashboard-unit-test.sh): the canonical dashboard's panel queries with the dashboard
# variables filled in, and strict query functions for Prometheus directly and for Grafana's query API.
#
# Strictness is the point of this file. Every query function either succeeds with a structurally valid answer or returns
# non-zero because the query failed: no connection, an HTTP error, an error status, a body of the wrong shape, or a populated
# element that is malformed. A valid answer with no series (Prometheus: an empty result array of the right result type;
# Grafana: no frame, or the empty frame it returns when there is no data) is the only no-data. A failure is never turned
# into an empty or a zero result, and two failures are never "equal". Callers use the expect_* helpers, which count a failed
# query apart from a value mismatch (QUERY_FAILURES), so that a backend error is never mistaken for an observed mismatch.
# Source after lib.sh.

# The window every query is evaluated in: the panel time range (WIN_FROM_MS to WIN_TO_MS) and its end as the instant of
# an instant query. $__range_ms in a panel is the length of the window, as Grafana's backend interpolates it.
WIN_FROM_MS=""
WIN_TO_MS=""
WIN_RANGE_MS=""
WIN_AT=""
QUERY_FAILURES=0
# is_uint <value>: a non-negative decimal integer without a sign, a fraction or leading garbage
is_uint() { case "$1" in "" | *[!0-9]*) return 1 ;; *) return 0 ;; esac; }
# set_window <from ms> <to ms>: both non-negative integers, the window not empty
set_window() {
  is_uint "$1" && is_uint "$2" || return 2
  [ "$2" -gt "$1" ] || return 2
  WIN_FROM_MS=$1
  WIN_TO_MS=$2
  WIN_RANGE_MS=$(($2 - $1))
  WIN_AT=$(awk -v t="$2" 'BEGIN { printf "%.3f", t / 1000 }')
}
# qfail <message>: a check that failed because a query failed, which is a different outcome from a value that differs
qfail() {
  QUERY_FAILURES=$((QUERY_FAILURES + 1))
  fail "$@"
}

# The dashboard variables as Grafana interpolates them into a regular expression matcher: All is the variable's allValue,
# a selection is its value
V_INSTANCE=""
V_CLIENT='.*'
V_TYPE='.*'
V_OPERATION='.*'
V_GLOBAL='.*'
V_CONTENT='.*'
reset_filters() { V_CLIENT='.*'; V_TYPE='.*'; V_OPERATION='.*'; V_GLOBAL='.*'; V_CONTENT='.*'; }

# panel_expr <panel id> [file]: the first query of a panel, as written in the dashboard file
panel_expr() { jq -er --argjson id "$1" '.panels[] | select(.id == $id) | .targets[0].expr' "${2:-$DASHBOARD_FILE}"; }
# panel_target <panel id> [file]: the first query of a panel without its expression and datasource: the request flags the
# panel really sends (instant or range, format, legend)
panel_target() { jq -ec --argjson id "$1" '.panels[] | select(.id == $id) | .targets[0] | del(.expr, .datasource)' "${2:-$DASHBOARD_FILE}"; }
# var_selector [file]: the selector inside label_values(<selector>,service_instance_id) of the process variable; the one
# strict parser of that definition
var_selector() {
  jq -er '.templating.list[] | select(.name == "instance") | .definition | capture("^label_values\\((?<s>.*),service_instance_id\\)$") | .s' "${1:-$DASHBOARD_FILE}"
}

# fill <expr>: the query with the dashboard variables replaced (Grafana does that in the browser)
fill() {
  local e=$1
  e=${e//\$instance/$V_INSTANCE}
  e=${e//\$client/$V_CLIENT}
  e=${e//\$operation/$V_OPERATION}
  e=${e//\$global_id/$V_GLOBAL}
  e=${e//\$content_id/$V_CONTENT}
  e=${e//\$type/$V_TYPE}
  printf '%s' "$e"
}

# fill_macros <expr>: what Grafana's query backend replaces, for a query sent to Prometheus directly. $__rate_interval is
# 60 seconds here (four scrape intervals of Grafana's 15 second default interval)
fill_macros() {
  local e=$1
  e=${e//\$\{__range_ms\}/$WIN_RANGE_MS}
  e=${e//\$__range_ms/$WIN_RANGE_MS}
  e=${e//\$__rate_interval/60s}
  printf '%s' "$e"
}

# --- Prometheus, direct --------------------------------------------------------------------------------------------------

# direct <expr> [instant]: the Prometheus answer to the filled query at the window's end (instant query). Non-zero unless the
# body is a success of result type vector whose every element has a metric object and a [time, numeric string] value.
direct() {
  local e out
  e=$(fill_macros "$(fill "$1")")
  out=$(prom_query "$e" "${2:-$WIN_AT}") || return 1
  jq -e 'def pair: type == "array" and length == 2 and (.[0] | type == "number") and (.[1] | type == "string") and ((.[1] | try tonumber catch null) != null);
    .data.resultType == "vector" and (.data.result | type == "array")
    and (.data.result | all(type == "object" and (.metric | type == "object") and (.value | pair)))' \
    > /dev/null 2>&1 <<< "$out" || return 1
  printf '%s' "$out"
}
# direct_range <expr> <from ms> <to ms> <step seconds>: a range query. Non-zero unless the body is a success of result type
# matrix whose every element has a metric object and a non-empty list of [time, numeric string] pairs.
direct_range() {
  local e out
  e=$(fill_macros "$(fill "$1")")
  out=$(curl -sS --fail --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -G --data-urlencode "query=$e" \
    --data-urlencode "start=$(awk -v t="$2" 'BEGIN { printf "%.3f", t / 1000 }')" --data-urlencode "end=$(awk -v t="$3" 'BEGIN { printf "%.3f", t / 1000 }')" \
    --data-urlencode "step=$4" "$PROMETHEUS_URL/api/v1/query_range") || return 1
  jq -e 'def pair: type == "array" and length == 2 and (.[0] | type == "number") and (.[1] | type == "string") and ((.[1] | try tonumber catch null) != null);
    .status == "success" and .data.resultType == "matrix" and (.data.result | type == "array")
    and (.data.result | all(type == "object" and (.metric | type == "object") and (.values | type == "array" and length > 0 and all(pair))))' \
    > /dev/null 2>&1 <<< "$out" || return 1
  printf '%s' "$out"
}

# --- Grafana's query API -------------------------------------------------------------------------------------------------

# graf <panel id> [from ms] [to ms]: the answer of Grafana's query API to the panel's own query: the expression of the
# dashboard file with the variables filled in, and the request flags (instant or range, format) of the panel's target.
# Non-zero unless the API answered 200 with no error and every frame has a layout this test understands. The layouts the panels'
# requests really produce (read from the running API, Grafana 13.3.1) are exactly two: the empty frame (no fields and no values,
# which is how Grafana says "no data"), and one frame per series with exactly two fields, a time field and a number field, whose
# value arrays are numbers of equal length; the number field's labels are absent, null or an object of strings. Any other layout, such as a
# third field, is refused rather than ignored.
graf() {
  local panel=$1 from=${2:-$WIN_FROM_MS} to=${3:-$WIN_TO_MS} expr target out
  expr=$(fill "$(panel_expr "$panel")") || return 1
  target=$(panel_target "$panel") || return 1
  out=$(jq -n --arg e "$expr" --arg from "$from" --arg to "$to" --arg uid "$GRAFANA_DATASOURCE_UID" --argjson t "$target" \
    '{queries: [($t + {refId: "A", datasource: {type: "prometheus", uid: $uid}, expr: $e, intervalMs: 15000, maxDataPoints: 1000})], from: $from, to: $to}' \
    | curl -sS --fail --max-time "$HTTP_TIMEOUT" --connect-timeout "$CONNECT_TIMEOUT" -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
      -H 'Content-Type: application/json' -d @- "$GRAFANA_URL/api/ds/query" 2> /dev/null) || return 1
  jq -e '
    def numbers: type == "array" and all(type == "number");
    def labelsok: . == null or (type == "object" and all(.[]; type == "string"));
    def frame: type == "object" and (.schema | type == "object") and (.schema.fields | type == "array") and (.data | type == "object") and (.data.values | type == "array")
      and ((.schema.fields | length) == (.data.values | length))
      and (if (.schema.fields | length) == 0 then true
           else (.schema.fields | length) == 2 and .schema.fields[0].type == "time" and .schema.fields[1].type == "number"
             and (.schema.fields[0] | type == "object") and (.schema.fields[1] | type == "object") and (.schema.fields[1].labels | labelsok)
             and (.data.values[0] | numbers) and (.data.values[1] | numbers) and ((.data.values[0] | length) == (.data.values[1] | length)) end);
    (.results | type == "object") and (.results.A | type == "object") and .results.A.status == 200 and ((.results.A.error // "") == "")
    and (.results.A.frames | type == "array") and (.results.A.frames | all(frame))' > /dev/null 2>&1 <<< "$out" || return 1
  printf '%s' "$out"
}
# frames_rows <labels...>: from a validated Grafana answer on stdin, "label values TAB ... TAB value" per row of every frame. A frame
# with rows must carry every wanted label as a non-empty string, and the identifier type, when wanted, must be global_id or
# content_id: a missing, null, empty or wrong-typed label is a malformed answer (non-zero status), never an empty cell.
frames_rows() {
  jq -r --args '
    def lab: type == "string" and length > 0;
    . as $all | $ARGS.positional as $want
    | [$all.results.A.frames[] | select((.schema.fields | length) > 0) | . as $f
        | if ($f.data.values[1] | length) == 0 then empty
          elif ($f.schema.fields[1].labels | type) != "object" then error("labels")
          else range(0; $f.data.values[1] | length) as $i
            | [($want[] as $l | $f.schema.fields[1].labels[$l] | if lab and (($l != "apicurio_registry_identifier_type") or IN("global_id", "content_id")) then . else error("label") end), $f.data.values[1][$i]]
          end]
    | .[] | map(tostring) | join("\t")' "$@" 2> /dev/null
}

# --- the answers of the panels -------------------------------------------------------------------------------------------

# Each function takes a panel id and succeeds only for a valid answer; direct_* run the panel's expression at the window's
# end (an instant query) and graf_* send the panel's request through Grafana. Each consumer states what it needs: a table row
# needs its four typed labels, a process candidate its process label, a rate point its operation, a scalar nothing but at most one
# value. A result that lacks what the consumer needs is a failure, not no data.
# The rows of the table panel, "client TAB type TAB value TAB operation TAB count", sorted
direct_rows() {
  local out
  out=$(direct "$(panel_expr "$1")") || return 1
  jq -e 'def lab: type == "string" and length > 0;
    .data.result | all(.metric | (.apicurio_registry_client_id | lab) and (.apicurio_registry_identifier_type | IN("global_id", "content_id"))
      and (.apicurio_registry_identifier_value | lab) and (.apicurio_registry_operation | lab))' > /dev/null 2>&1 <<< "$out" || return 1
  jq -r '.data.result[] | [.metric.apicurio_registry_client_id, .metric.apicurio_registry_identifier_type, .metric.apicurio_registry_identifier_value, .metric.apicurio_registry_operation, .value[1]] | @tsv' <<< "$out" | LC_ALL=C sort
}
graf_rows() {
  local out rows
  out=$(graf "$1") || return 1
  rows=$(frames_rows apicurio_registry_client_id apicurio_registry_identifier_type apicurio_registry_identifier_value apicurio_registry_operation <<< "$out") || return 1
  printf '%s\n' "$rows" | LC_ALL=C sed '/^$/d' | LC_ALL=C sort
}
# The single number of a one value panel, "none" when the answer is a valid empty one (the panel shows its no data text). More
# than one series, frame or value is not a scalar: it fails instead of being cut to its first element.
direct_scalar() {
  local out
  out=$(direct "$(panel_expr "$1")") || return 1
  jq -e '(.data.result | length) <= 1' > /dev/null 2>&1 <<< "$out" || return 1
  jq -r '.data.result[0].value[1] // "none"' <<< "$out"
}
graf_scalar() {
  local out
  out=$(graf "$1") || return 1
  jq -e '[.results.A.frames[] | select((.schema.fields | length) > 0)] | length <= 1 and all(.data.values[1] | length == 1)' > /dev/null 2>&1 <<< "$out" || return 1
  jq -r '[.results.A.frames[] | select((.schema.fields | length) > 0) | .data.values[1][0]] | (.[0] // "none") | tostring' <<< "$out"
}
# The process candidates of an expression's instant answer: one "service_instance_id" per line, sorted. Every element must carry
# a non-empty string process label; a missing, null or empty one fails the query instead of being dropped. The process table's
# expression and the independent heartbeat reference both go through here.
direct_process_ids() {
  local out
  out=$(direct "$1") || return 1
  jq -e 'def lab: type == "string" and length > 0; .data.result | all(.metric.service_instance_id | lab)' > /dev/null 2>&1 <<< "$out" || return 1
  jq -r '.data.result[].metric.service_instance_id' <<< "$out" | LC_ALL=C sort
}
direct_processes() { direct_process_ids "$(panel_expr "$1")"; }
graf_processes() {
  local out rows
  out=$(graf "$1") || return 1
  rows=$(frames_rows service_instance_id <<< "$out") || return 1
  printf '%s\n' "$rows" | LC_ALL=C sed '/^$/d' | cut -f1 | LC_ALL=C sort
}
# The rate chart: "operation TAB time in ms TAB rate" for every returned point, from the window's start to its end at a
# 15 second step (the step Grafana uses for this request), sorted by operation and time. The panel's own range request. Every
# series carries one operation label and no operation has two series. A point is only an observation when its time has
# millisecond precision (Prometheus answers seconds with three decimals, Grafana whole milliseconds), the times of a series
# strictly increase and lie inside the requested window (Prometheus answers from the start to the end; Grafana aligns its
# points down to the step, so its first point may lie up to one step before the start), and the rate is a finite number that is
# not negative (a counter's rate cannot be; zero is a legitimate value). A series that breaks that is malformed, not
# observations. The only accommodation for floating point is the representation error of the product (seconds times 1000) at
# its own magnitude: a time may differ from a whole millisecond by at most two units in the last place of that product, so the
# allowance grows and shrinks with the magnitude of the time. A time finer than that is a real sub-millisecond time, refused
# whatever its size (it would floor into a duplicate or move across the window's start), and the window bounds are checked on
# the whole milliseconds that remain.
direct_rate_points() {
  local out
  out=$(direct_range "$(panel_expr "$1")" "$WIN_FROM_MS" "$WIN_TO_MS" 15) || return 1
  jq -e --argjson lo "$WIN_FROM_MS" --argjson hi "$WIN_TO_MS" 'def lab: type == "string" and length > 0;
    def ms: (. * 1000) as $m | ($m | round) as $r | (2 * pow(2; (($m | fabs) | frexp | .[1]) - 53)) as $tol | (($m - $r) | fabs) <= $tol;
    def rate: tonumber | . as $v | ((($v | isnan) or ($v | isinfinite)) | not) and $v >= 0;
    def inwin: . as $a | all($a[]; . >= $lo and . <= $hi) and ([range(1; $a | length)] | all($a[.] > $a[. - 1]));
    (.data.result | all(.metric.apicurio_registry_operation | lab))
    and ([.data.result[].metric.apicurio_registry_operation] | (length == (unique | length)))
    and (.data.result | all(all(.values[]; (.[0] | ms) and (.[1] | rate))))
    and (.data.result | all([.values[][0] * 1000 | round] | inwin))' > /dev/null 2>&1 <<< "$out" || return 1
  jq -r '.data.result[] | .metric.apicurio_registry_operation as $op | .values[] | [$op, (.[0] * 1000 | round), (.[1] | tonumber)] | @tsv' <<< "$out" | LC_ALL=C sort -k1,1 -k2,2n
}
graf_rate_points() {
  local out rows
  out=$(graf "$1") || return 1
  jq -e --argjson lo "$((WIN_FROM_MS - 14999))" --argjson hi "$WIN_TO_MS" 'def lab: type == "string" and length > 0;
    def rate: . as $v | ((($v | isnan) or ($v | isinfinite)) | not) and $v >= 0;
    def inwin: . as $a | all($a[]; . >= $lo and . <= $hi) and ([range(1; $a | length)] | all($a[.] > $a[. - 1]));
    [.results.A.frames[] | select((.schema.fields | length) > 0)] as $f
    | ($f | all(.schema.fields[1].labels | type == "object" and (.apicurio_registry_operation | lab)))
    and ([$f[].schema.fields[1].labels.apicurio_registry_operation] | (length == (unique | length)))
    and ($f | all(all(.data.values[0][]; . == floor) and all(.data.values[1][]; rate)))
    and ($f | all(.data.values[0] | inwin))' > /dev/null 2>&1 <<< "$out" || return 1
  rows=$(jq -r '.results.A.frames[] | select((.schema.fields | length) > 0) | . as $f
    | range(0; $f.data.values[1] | length) as $i | [$f.schema.fields[1].labels.apicurio_registry_operation, $f.data.values[0][$i], $f.data.values[1][$i]] | @tsv' <<< "$out") || return 1
  printf '%s\n' "$rows" | LC_ALL=C sed '/^$/d' | LC_ALL=C sort -k1,1 -k2,2n
}

# --- expectations ----------------------------------------------------------------------------------------------------------

# expect_exact <description> <expected text> <function> <panel>: the function must succeed and return exactly the expected
# text. A failing query is its own failure (counted in QUERY_FAILURES) and is never compared.
expect_exact() {
  local d=$1 want=$2 fn=$3 panel=$4 got
  if ! got=$("$fn" "$panel"); then
    qfail "$d: the query through $fn failed (not an answer, UNKNOWN)"
    return
  fi
  if [ "$got" = "$want" ]; then
    ok "$d ($fn)"
  else
    fail "$d ($fn): rows differ (expected vs actual):"
    diff <(printf '%s\n' "$want") <(printf '%s\n' "$got") >&2 || true
  fi
}
# expect_scalar <description> <expected> <function> <panel>
expect_scalar() {
  local d=$1 want=$2 fn=$3 panel=$4 got
  if ! got=$("$fn" "$panel"); then
    qfail "$d: the query through $fn failed (not an answer, UNKNOWN)"
    return
  fi
  check "$d ($fn)" "$want" "$got"
}

# The rate window of the panel's query, in seconds: fill_macros gives $__rate_interval as 60s, and Grafana's is four scrape
# intervals of its 15 s default, which is also 60 s
RATE_WINDOW_SECONDS=60

# approx_rate_points <description> <requests "operation TAB completion second" lines> <tolerance, fraction> <lag seconds> <steady from ms> <function> <panel>:
# every returned point is checked against what the requests recorded for ITS OWN operation and rate window predict; nothing
# that happens after a point, in another point's window or in another operation enters its expectation.
# The temporal model, in milliseconds. A recorded second s is a floored second: the request really completed at some instant c
# in [s, s+1) (left end included, right end excluded). The exported counter counts it at c + d, where the export and scrape
# delay d is between 0 and <lag> seconds and is each request's own; <lag> is rounded UP to a whole millisecond, so uncertainty
# is never rounded inward. The request is therefore counted at some instant of [s, s+1+lag), the right end excluded. The rate
# window of a point at t is (t - 60, t], left end excluded, right end included. A request is CERTAINLY inside when
# s > t - 60 and s + 1 + lag <= t, and POSSIBLY inside when s <= t and s + 1 + lag > t - 60. The accepted rate is the certain
# count over 60 s times (1 - tolerance), minus one request over 60 s, up to the possible count over 60 s times (1 + tolerance),
# plus one request over 60 s; the tolerance and that one request cover what rate() adds at the window edges (extrapolation
# and scrape alignment), not the timestamp uncertainty, which the intervals above carry.
# Every returned rate must be a plain number that is not negative, whatever the slack. Each operation has its own history: a
# point earlier than the operation's first recorded second (a series cannot exist before its first request) or of an operation
# without any recorded request is unexpected, whatever other operations returned.
# Presence is decided from the requested interval (WIN_TO_MS, set by set_window) and the requests, never from what a series
# returned: an operation whose first request is mature, that is s + 1 + lag + 60 s at or before a point, has had a full window of
# history there. From the later of <steady from ms> and that maturity to the requested end there are floor(length / 15 s) steps
# of the 15 s evaluation at least (Prometheus anchors them at the window start, Grafana at multiples of 15 s), and the operation
# must return a point for at least the smaller of three and that number of them, so a mature operation can never be omitted
# entirely, while no coverage beyond three points is demanded and an operation that could not yet be observed before the
# requested end is not required. A failing query is a query failure, never a mismatch.
approx_rate_points() {
  local d=$1 reqs=$2 tol=$3 lag=$4 from=$5 fn=$6 panel=$7 got verdict
  if ! got=$("$fn" "$panel"); then
    qfail "$d: the query through $fn failed (not an answer, UNKNOWN)"
    return
  fi
  verdict=$(awk -F'\t' -v tol="$tol" -v lag="$lag" -v from="$from" -v end="$WIN_TO_MS" -v w="$RATE_WINDOW_SECONDS" 'BEGIN { wms = w * 1000; lagms = int(lag * 1000); if (lagms < lag * 1000) lagms++ }
    NR == FNR { if (NF >= 2) { n[$1]++; sec[$1, n[$1]] = $2 * 1000; if (!($1 in first) || $2 * 1000 < first[$1]) first[$1] = $2 * 1000 } next }
    function count(op, t, certain,   i, c, s) {
      c = 0
      for (i = 1; i <= n[op]; i++) { s = sec[op, i]; if (certain ? (s > t - wms && s + 1000 + lagms <= t) : (s <= t && s + 1000 + lagms > t - wms)) c++ }
      return c
    }
    NF >= 3 { np++; po[np] = $1; pt[np] = $2 + 0; pr[np] = $3; seen[$1] = 1 }
    END {
      for (i = 1; i <= np; i++) {
        op = po[i]; t = pt[i]
        if (pr[i] !~ /^-?[0-9]+([.][0-9]*)?([eE][-+]?[0-9]+)?$/) { bad = bad sprintf(" %s@%d=%s(not a number)", op, t, pr[i]); continue }
        v = pr[i] + 0
        if (v < 0) { bad = bad sprintf(" %s@%d=%.3f(negative)", op, t, v); continue }
        if (!(op in first)) continue
        if (t < first[op]) { bad = bad sprintf(" %s@%d(before its first request at %d)", op, t, first[op]); continue }
        if (t < from) continue
        lo = count(op, t, 1) / w * (1 - tol) - 1 / w; hi = count(op, t, 0) / w * (1 + tol) + 1 / w
        if (v < lo || v > hi) bad = bad sprintf(" %s@%d=%.3f(expected %.3f to %.3f)", op, t, v, lo < 0 ? 0 : lo, hi)
      }
      for (k in first) {
        start = first[k] + 1000 + lagms + wms; if (start < from) start = from
        avail = int((end - start) / 15000); if (end < start) avail = 0
        need = avail < 3 ? avail : 3
        if (need > 0) {
          c = 0; for (i = 1; i <= np; i++) if (po[i] == k && pt[i] >= start) c++
          if (!(k in seen)) bad = bad " missing:" k; else if (c < need) bad = bad " " k ":only " c " of the " need " points from " start
        }
      }
      for (k in seen) if (!(k in first)) bad = bad " unexpected:" k
      print (bad == "" ? "ok" : bad)
    }' <(printf '%s\n' "$reqs" | awk -F'\t' 'NF >= 2') <(printf '%s\n' "$got" | awk -F'\t' 'NF >= 3'))
  if [ "$verdict" = ok ]; then
    ok "$d ($fn): $(printf '%s\n' "$got" | awk -F'\t' -v from="$from" '$2 + 0 >= from { n[$1]++; s[$1] += $3 } END { for (k in n) printf "%s mean %.3f over %d points  ", k, s[k] / n[k], n[k] }')"
  else
    fail "$d ($fn):$verdict"
  fi
}

# --- the traffic of the scenarios ------------------------------------------------------------------------------------------

# dash_fetch_recorded <global id> <client> <operation> <log>: one real fetch. Only an answer of 200 counts: it is recorded as
# "completion second operation client" in the log. Anything else prints which request failed and returns non-zero without
# recording it, so the caller can stop: a planned request is either made and recorded or the scenario fails.
dash_fetch_recorded() {
  local st
  st=$(http_status "$API/ids/globalIds/$1" -H "X-Registry-Client-Id: $2" -H "X-Registry-Operation: $3")
  if [ "$st" != 200 ]; then
    printf 'error: the planned fetch of global ID %s as client %s, operation %s answered %s, not 200; the scenario is abandoned before more traffic is sent\n' "$1" "$2" "$3" "$st" >&2
    return 1
  fi
  printf '%s %s %s\n' "$(date +%s)" "$3" "$2" >> "$4"
}
# dash_steady_traffic <global id> <log> <iterations>: per iteration c1 SERIALIZE, and every second iteration also c2 SERIALIZE and
# c1 DESERIALIZE, with a pause of DASH_TRAFFIC_SLEEP seconds (default 1) after each. It stops at the first request that fails.
dash_steady_traffic() {
  local gid=$1 log=$2 n=$3 i
  for i in $(seq 1 "$n"); do
    dash_fetch_recorded "$gid" c1 SERIALIZE "$log" || return 1
    if [ $((i % 2)) = 0 ]; then
      dash_fetch_recorded "$gid" c2 SERIALIZE "$log" || return 1
      dash_fetch_recorded "$gid" c1 DESERIALIZE "$log" || return 1
    fi
    sleep "${DASH_TRAFFIC_SLEEP:-1}"
  done
}
# The candidates' independent reference over the same interval: the last sample of each heartbeat inside the selected range
# (a different range function from the panel's count_over_time, the same start-excluded, end-included bounds). An instant selector at
# the range end is not a reference: a heartbeat inside the range can already be stale there.
# shellcheck disable=SC2016 # a Grafana macro, not a shell variable
CANDIDATE_REFERENCE='group by (service_instance_id) (last_over_time(jvm_cpu_count[${__range_ms}ms]))'

# --- the mutation experiment -------------------------------------------------------------------------------------------

# mutant_verdict <name> <exit status of dashboard-test.sh> <its output file>: a mutant is killed only by a value or label
# mismatch of successful queries (exit status 1). Status 3 means a query failed somewhere, which proves nothing about the
# mutant, even when another backend saw the expected mismatch; status 0 means the mutant survived.
mutant_verdict() {
  case "$2" in
    1)
      if grep -q 'FAIL .*controlled traffic' "$3"; then
        ok "$1: the rate assertions failed on values (exit 1): $(grep 'FAIL .*controlled traffic' "$3" | head -1 | cut -c1-200)"
      else
        fail "$1: exit 1 but not from the rate assertions (see $3)"
      fi ;;
    3) qfail "$1: a query failed during the mutant run (exit 3), so the mutant's outcome is not proven (see $3)" ;;
    0) fail "$1: the mutant SURVIVED: every rate assertion passed (see $3)" ;;
    *) fail "$1: dashboard-test.sh exited $2, not a mutant verdict (see $3)" ;;
  esac
}
# mutant_experiment <scenario file> <owned directory> <runner>: runs tests/dashboard-test.sh once per rate mutant of panel
# 4's query (the rate multiplied by zero, which keeps the labels so the values alone are judged, the rate doubled, the rate ten times higher), each as its own run with its own exit status. The
# runner is called as `runner <name> <command...>` and leaves the command's status in RC and its output file in OUT.
mutant_experiment() {
  local scenario=$1 dir=$2 runner=$3 name expr
  for name in constant-zero doubled ten-times; do
    case $name in
      constant-zero) expr='"0 * (" + . + ")"' ;;
      doubled) expr='"2 * (" + . + ")"' ;;
      *) expr='"10 * (" + . + ")"' ;;
    esac
    jq "(.panels[] | select(.id == 4) | .targets[0].expr) |= ($expr)" "$DASHBOARD_FILE" > "$dir/mutant-$name.json" || { fail "$name: could not build the mutant"; continue; }
    "$runner" "mutant-$name" ./tests/dashboard-test.sh --only scenarios --scenario "$scenario" --dashboard "$dir/mutant-$name.json"
    mutant_verdict "$name" "$RC" "$OUT"
  done
}
