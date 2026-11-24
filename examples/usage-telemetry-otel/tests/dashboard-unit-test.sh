#!/usr/bin/env bash
# Fast regression tests of the dashboard checks (tests/dashboard-lib.sh and tests/dashboard-test.sh). No container, stack or
# promtool is used: a throwaway HTTP backend on 127.0.0.1 plays Prometheus and Grafana. They cover what a healthy stack cannot
# show: that a query that failed is never read as an empty or a zero answer (no connection, HTTP 500, an error status in the
# body, a body of the wrong shape, or a populated element that is malformed), that a valid empty answer is no data, that two
# failures are never equal, that the rate chart is checked through its own range request with its own request flags, that rate
# assertions fail for a zero or a scaled rate, that a backend error during a mutant run is never taken for a killed mutant,
# that a scenario file is exactly one JSON object validated before any query, that an external datasource UID is honored, and
# that provisioning and the mount are required unless --external.
#
# Usage: ./tests/dashboard-unit-test.sh
# Prerequisites: bash 3.2 or later, curl, jq, python3.
# Ends with "All checks passed." and status 0, or with the failed checks and status 1.
# shellcheck disable=SC2030,SC2031 # the check counters are deliberately modified in subshells that judge an experiment on its own
set -Eeuo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib.sh
. ./lib.sh
# shellcheck source=tests/dashboard-lib.sh
. ./tests/dashboard-lib.sh
need_commands curl jq python3

TMP=$(mktemp -d "${TMPDIR:-/tmp}/dashboard-unit.XXXXXX")
OWN_PIDS=""
cleanup() {
  local p
  for p in $OWN_PIDS; do kill "$p" 2> /dev/null || true; done
  rm -rf "$TMP"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cat > "$TMP/server.py" << 'PYEOF'
import json, os, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
# usage: server.py PORTFILE LOG MODE
# MODE shapes the answers of /api/v1/query (instant), /api/v1/query_range and /api/ds/query (Grafana, instant or range):
#   data, empty (no result, no frame), emptyframe (the empty frame Grafana returns for no data), apierror (HTTP 200 with an error
#   status), http500, badshape (a success without the result array), badresult (a populated element that is {}), badvalue
#   (a value that is not a [time, number] pair), badtype (a result type or field type that is wrong), nolabels (a populated
#   element without its labels), nullprocess / nulloperation (a required label that is null), badtypeid (an identifier type that
#   is neither global_id nor content_id), thirdfield (a Grafana frame with a malformed third field), twoseries (two series, with the
#   values 3 and 99, of the same labels), multivalue (a Grafana frame with two values), rates (steady controlled rates for the range
#   requests: 1.5 per second for SERIALIZE and 0.5 for DESERIALIZE, times 2, 10 or 0 when the expression starts with
#   "2 * (", "10 * (" or "0 * ("; the zero mutant answers HTTP 500 through Grafana when $INJECT_GRAF500_ON is "0 * ("; every
#   Grafana range request fails when it is "*"), rates-dup (three identical timestamps), rates-late (every point after the window),
#   rates-early (every point before it), rates-unordered (descending timestamps) and rates-nolabels (one series without the
#   operation label and all zero, what the real vector(0) answers), rates-negative / rates-nan / rates-inf / rates-zero (the first
#   point of every series has the rate -0.01, NaN, +Inf or 0), rates-subms (three increasing times 0.1, 0.2 and 0.3 ms apart),
#   rates-premicro (every time 0.005 ms before a whole 15 s step, the first one before the window), rates-micro (every time 0.001 ms
#   after one), rates-noise (a point every 37 ms, whole milliseconds at any scale),
#   procmix-null / procmix-missing / procmix-empty / procmix-number (every instant or Grafana answer has a valid process p1 and a
#   second element whose service_instance_id is null, absent, empty or a number) and refbad-null / refbad-missing (the same, but
#   only for the instant query of the heartbeat reference, so the process table itself is valid)
# $DASH_MODE (ok, diffs, unprovisioned, missing, error, garbage) answers /api/dashboards/uid/<uid>, serving $DASHBOARD_FILE when ok.
# GET /apis/registry/v3/ids/globalIds/<id> answers 200, or 500 for the $FETCH_FAIL_ON-th such request; the client and operation
# headers are logged. Every request is appended to the log; a Grafana query body is appended on the line after its request line.
mode = sys.argv[3]
log = open(sys.argv[2], "a", buffering=1)
LABELS = {"apicurio_registry_client_id": "c1", "apicurio_registry_identifier_type": "global_id", "apicurio_registry_identifier_value": "1",
          "apicurio_registry_operation": "SERIALIZE", "service_instance_id": "p1"}
RATES = {"SERIALIZE": 1.5, "DESERIALIZE": 0.5}
fetches = [0]
def scale_of(expr):
    if expr.startswith("2 * ("): return 2.0
    if expr.startswith("10 * ("): return 10.0
    if expr.startswith("0 * ("): return 0.0
    return 1.0
BADV = {"rates-negative": ("-0.01", -0.01), "rates-nan": ("NaN", float("nan")), "rates-inf": ("+Inf", float("inf")), "rates-zero": ("0", 0.0)}
REF_PREFIX = "group by (service_instance_id) (last_over_time("
def rate_values(r, sc, n, prom):
    vals = [r * sc for _ in range(n)]
    if mode in BADV: vals[0] = BADV[mode][0 if prom else 1]
    return [v if isinstance(v, str) else (str(v) if prom else v) for v in vals]
def times(start_ms, end_ms):
    if mode == "rates-premicro": return [start_ms - 0.005 + 15000 * i for i in range(9)]
    if mode == "rates-micro": return [start_ms + 0.001 + 15000 * i for i in range(9)]
    if mode == "rates-noise": return [start_ms + 37 * i for i in range((end_ms - start_ms) // 37 + 1)]
    if mode == "rates-subms": return [end_ms - 55000 + 0.1 * i for i in range(1, 4)]
    if mode == "rates-dup": return [end_ms - 40000, end_ms - 40000, end_ms - 40000, end_ms - 25000]
    if mode == "rates-late": return [end_ms + 60000 + 15000 * i for i in range(4)]
    if mode == "rates-early": return [start_ms - 600000 + 15000 * i for i in range(4)]
    ts = list(range(start_ms, end_ms + 1, 15000))
    return ts[::-1] if mode == "rates-unordered" else ts

class H(BaseHTTPRequestHandler):
    def _send(self, status, body):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)
    def _do(self):
        url = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(url.query)
        log.write("%s %s\n" % (self.command, self.path))
        body = None
        if self.command == "POST":
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or b"{}")
            log.write(json.dumps(body) + "\n")
        if "/ids/globalIds/" in url.path:
            fetches[0] += 1
            log.write("FETCH %d client=%s operation=%s\n" % (fetches[0], self.headers.get("X-Registry-Client-Id"), self.headers.get("X-Registry-Operation")))
            fail_on = os.environ.get("FETCH_FAIL_ON", "")
            return self._send(500 if fail_on and fetches[0] == int(fail_on) else 200, {})
        if url.path.startswith("/api/dashboards/uid/"):
            dm = os.environ.get("DASH_MODE", "ok")
            uid = url.path.rsplit("/", 1)[-1]
            if dm == "missing": return self._send(404, {"message": "Dashboard not found"})
            if dm == "error": return self._send(500, {"message": "internal error"})
            if dm == "garbage": return self._send(200, {"unexpected": True})
            dash = json.load(open(os.environ["DASHBOARD_FILE"])) if dm in ("ok", "diffs") else {"uid": uid}
            if dm == "diffs": dash["title"] = dash.get("title", "") + " CHANGED IN GRAFANA"
            return self._send(200, {"meta": {"provisioned": dm in ("ok", "diffs")}, "dashboard": dash})
        graf = url.path == "/api/ds/query"
        rng = url.path == "/api/v1/query_range" or (graf and body["queries"][0].get("range") is True)
        expr = body["queries"][0]["expr"] if graf else q.get("query", [""])[0]
        if mode == "http500": return self._send(500, {"message": "boom"})
        if mode == "apierror":
            return self._send(200, {"results": {"A": {"status": 400, "error": "bad_data"}}} if graf else {"status": "error", "errorType": "bad_data", "error": "boom"})
        if mode == "badshape":
            return self._send(200, {"results": {}} if graf else {"status": "success", "data": {}})
        if mode in ("empty", "emptyframe"):
            if graf:
                frames = [] if mode == "empty" else [{"schema": {"refId": "A", "fields": []}, "data": {"values": []}}]
                return self._send(200, {"results": {"A": {"status": 200, "frames": frames}}})
            return self._send(200, {"status": "success", "data": {"resultType": "matrix" if rng else "vector", "result": []}})
        if mode.startswith("rates"):
            sc = scale_of(expr)
            if graf and expr.startswith("0 * (") and os.environ.get("INJECT_GRAF500_ON", "") in ("0 * (", "*"): return self._send(500, {"message": "injected"})
            if graf and rng and os.environ.get("INJECT_GRAF500_ON", "") == "*": return self._send(500, {"message": "injected"})
            fr = int(body["from"]) if graf else int(float(q["start"][0]) * 1000)
            to = int(body["to"]) if graf else int(float(q["end"][0]) * 1000)
            ts = times(fr, to)
            if mode == "rates-nolabels":
                if graf:
                    return self._send(200, {"results": {"A": {"status": 200, "frames": [{"schema": {"refId": "A", "fields": [{"name": "Time", "type": "time"}, {"name": "Value", "type": "number"}]}, "data": {"values": [ts, [0 for _ in ts]]}}]}}})
                return self._send(200, {"status": "success", "data": {"resultType": "matrix", "result": [{"metric": {}, "values": [[t / 1000, "0"] for t in ts]}]}})
            if graf:
                frames = [{"schema": {"refId": "A", "fields": [{"name": "Time", "type": "time"}, {"name": "Value", "type": "number", "labels": {"apicurio_registry_operation": op}}]},
                           "data": {"values": [ts, rate_values(r, sc, len(ts), False)]}} for op, r in RATES.items()]
                return self._send(200, {"results": {"A": {"status": 200, "frames": frames}}})
            return self._send(200, {"status": "success", "data": {"resultType": "matrix", "result": [
                {"metric": {"apicurio_registry_operation": op}, "values": [[t / 1000, v] for t, v in zip(ts, rate_values(r, sc, len(ts), True))]} for op, r in RATES.items()]}})
        labels = dict(LABELS)
        second = None
        if mode.startswith("procmix-") or (mode.startswith("refbad-") and not graf and not rng and expr.startswith(REF_PREFIX)):
            kind = mode.split("-", 1)[1]
            second = dict(LABELS)
            if kind == "missing": del second["service_instance_id"]
            else: second["service_instance_id"] = {"null": None, "empty": "", "number": 7}[kind]
        if mode == "nullprocess": labels["service_instance_id"] = None
        if mode == "nulloperation": labels["apicurio_registry_operation"] = None
        if mode == "badtypeid": labels["apicurio_registry_identifier_type"] = "other"
        if graf:
            f1 = {"name": "Value", "type": "number", "labels": labels}
            vals = [[1], [3]]
            fields = [{"name": "Time", "type": "time"}, f1]
            if mode == "nolabels": del f1["labels"]
            if mode == "badtype": f1["type"] = "string"
            if mode == "badvalue": vals = [[1], ["x"]]
            if mode == "thirdfield":
                fields.append({"name": "Extra", "type": "number", "labels": None})
                vals = [[1], [3], ["corrupt"]]
            if mode == "multivalue": vals = [[1, 2], [3, 99]]
            frames = [{"schema": {"refId": "A", "fields": fields}, "data": {"values": vals}}]
            if mode == "twoseries": frames = frames * 2; frames[1] = {"schema": {"refId": "A", "fields": [{"name": "Time", "type": "time"}, dict(f1)]}, "data": {"values": [[1], [99]]}}
            if second is not None: frames.append({"schema": {"refId": "A", "fields": [{"name": "Time", "type": "time"}, {"name": "Value", "type": "number", "labels": second}]}, "data": {"values": [[1], [3]]}})
            if mode == "badresult": frames = [{}]
            return self._send(200, {"results": {"A": {"status": 200, "frames": frames}}})
        el = {"metric": labels, "values" if rng else "value": ([[1, "3"], [2, "3"]] if rng else [1, "3"])}
        rtype = "matrix" if rng else "vector"
        if mode == "badresult": el = {}
        if mode == "badvalue": el = {"metric": labels, "values" if rng else "value": ([[1]] if rng else [1])}
        if mode == "badtype": rtype = "vector" if rng else "scalar"
        if mode == "nolabels": del el["metric"]
        result = [el]
        if second is not None: result.append({"metric": second, "values" if rng else "value": ([[1, "3"], [2, "3"]] if rng else [1, "3"])})
        if mode == "twoseries": result = [el, {"metric": labels, "values" if rng else "value": ([[1, "99"], [2, "99"]] if rng else [1, "99"])}]
        return self._send(200, {"status": "success", "data": {"resultType": rtype, "result": result}})
    def do_GET(self): self._do()
    def do_POST(self): self._do()
    def log_message(self, *a): pass
import socketserver
class Server(ThreadingHTTPServer):
    # HTTPServer.server_bind asks the resolver for the host's name, which takes seconds on some machines; skip it
    def server_bind(self):
        socketserver.TCPServer.server_bind(self)
        self.server_name = "127.0.0.1"
        self.server_port = self.socket.getsockname()[1]
srv = Server(("127.0.0.1", 0), H)
with open(sys.argv[1] + ".tmp", "w") as f:
    f.write(str(srv.server_port))
os.replace(sys.argv[1] + ".tmp", sys.argv[1])
srv.serve_forever()
PYEOF

# start_server <name> <mode>: sets SERVER_PORT and SERVER_LOG; the server is stopped by stop_server
SERVER_PID=""
start_server() {
  rm -f "$TMP/$1.port"
  : > "$TMP/$1.log"
  python3 -I "$TMP/server.py" "$TMP/$1.port" "$TMP/$1.log" "$2" 2> "$TMP/$1.err" &
  SERVER_PID=$!
  OWN_PIDS="$OWN_PIDS $SERVER_PID"
  local i=0
  while [ ! -s "$TMP/$1.port" ]; do
    kill -0 "$SERVER_PID" 2> /dev/null || die "the test backend $1 exited: $(cat "$TMP/$1.err")"
    i=$((i + 1))
    [ "$i" -lt 300 ] || die "the test backend $1 did not start"
    sleep 0.1
  done
  SERVER_PORT=$(cat "$TMP/$1.port")
  SERVER_LOG=$TMP/$1.log
}
stop_server() { kill "$SERVER_PID" 2> /dev/null || true; }
point_at() { PROMETHEUS_URL=http://127.0.0.1:$1; GRAFANA_URL=http://127.0.0.1:$1; export PROMETHEUS_URL GRAFANA_URL; }

set_window 1000 2000
V_INSTANCE=p1

part "set_window: only a non-empty window of non-negative integers"
for w in "a 5" "5 a" "5 5" "6 5" "-1 5" "1.5 3" "'' 3"; do
  rc=0
  # shellcheck disable=SC2086
  eval "set_window $w" 2> /dev/null || rc=$?
  check "set_window $w is refused" 2 "$rc"
done
set_window 1000 2000
check "a valid window has its length" 1000 "$WIN_RANGE_MS"

part "queries: a failure is never an empty or a zero answer, malformed populated elements are failures, valid no data is no data"
# the function, and its panel id
FNS="direct_rows:5 direct_scalar:1 direct_processes:6 direct_rate_points:4 graf_rows:5 graf_scalar:1 graf_processes:6 graf_rate_points:4"
# want_status <mode> <function>: 0 when the function must succeed on that backend mode, 1 when it must fail. Each consumer needs only what
# it reads: a table row its four typed labels, a candidate its process label, a rate point its operation and timestamps, a scalar at
# most one value; Grafana frames must have the exact two field layout whatever the consumer
want_status() {
  case "$1 $2" in
    "data "* | "empty "* | "emptyframe "*) echo 0 ;;
    "nolabels graf_scalar") echo 0 ;;
    "nullprocess direct_processes" | "nullprocess graf_"*) echo 1 ;;
    "nullprocess "*) echo 0 ;;
    "nulloperation direct_rows" | "nulloperation direct_rate_points" | "nulloperation graf_"*) echo 1 ;;
    "nulloperation "*) echo 0 ;;
    "badtypeid direct_rows" | "badtypeid graf_rows") echo 1 ;;
    "badtypeid "*) echo 0 ;;
    "thirdfield graf_"*) echo 1 ;;
    "thirdfield "*) echo 0 ;;
    "twoseries direct_scalar" | "twoseries graf_scalar" | "twoseries direct_rate_points" | "twoseries graf_rate_points") echo 1 ;;
    "twoseries "*) echo 0 ;;
    "multivalue graf_scalar") echo 1 ;;
    "multivalue "*) echo 0 ;;
    *) echo 1 ;;
  esac
}
for mode in data empty emptyframe apierror http500 badshape badresult badvalue badtype nolabels nullprocess nulloperation badtypeid thirdfield twoseries multivalue closed; do
  if [ "$mode" = closed ]; then PROMETHEUS_URL=http://127.0.0.1:1; GRAFANA_URL=http://127.0.0.1:1; export PROMETHEUS_URL GRAFANA_URL; else start_server "q-$mode" "$mode"; point_at "$SERVER_PORT"; fi
  for entry in $FNS; do
    fn=${entry%%:*}
    panel=${entry##*:}
    rc=0
    out=$("$fn" "$panel" 2> /dev/null) || rc=$?
    want=$(want_status "$mode" "$fn")
    if [ "$want" = 0 ]; then
      check "$mode: $fn succeeds" 0 "$rc"
    else
      check "$mode: $fn fails (status non-zero) and prints nothing" "1 " "$([ "$rc" -ne 0 ] && echo 1) $out"
    fi
  done
  case $mode in
    data)
      check "data: the table row" "$(printf 'c1\tglobal_id\t1\tSERIALIZE\t3')" "$(direct_rows 5)"
      check "data: the table row through Grafana" "$(printf 'c1\tglobal_id\t1\tSERIALIZE\t3')" "$(graf_rows 5)"
      check "data: the scalar" 3 "$(direct_scalar 1)"
      check "data: the scalar through Grafana" 3 "$(graf_scalar 1)"
      check "data: the process candidates" p1 "$(direct_processes 6)"
      check "data: the process candidates through Grafana" p1 "$(graf_processes 6)" ;;
    empty | emptyframe)
      check "$mode: legitimate no data is no rows" "" "$(direct_rows 5)"
      check "$mode: legitimate no data is none for a scalar" none "$(direct_scalar 1)"
      check "$mode: through Grafana too" "none" "$(graf_scalar 1)"
      check "$mode: no rows through Grafana" "" "$(graf_rows 5)"
      check "$mode: no process candidates through Grafana" "" "$(graf_processes 6)" ;;
  esac
  # The comparison helpers: a failed query is its own failure, counted apart from a value that differs, and never compared. The
  # counts are taken in a subshell, so the counters of this suite are never touched and every check below is a real check
  case $mode in
    apierror | http500 | badshape | badresult | badvalue | badtype | closed)
      counts=$(
        failures=0
        QUERY_FAILURES=0
        expect_exact "a failing query compared with the empty expectation" "" direct_rows 5 > /dev/null 2>&1
        expect_scalar "a failing query compared with none" none direct_scalar 1 > /dev/null 2>&1
        expect_exact "a failing query through Grafana compared with the empty expectation" "" graf_rows 5 > /dev/null 2>&1
        echo "$failures $QUERY_FAILURES"
      )
      check "$mode: the three comparisons are three failures, all of them query failures" "3 3" "$counts" ;;
    thirdfield | twoseries)
      counts=$(
        failures=0
        QUERY_FAILURES=0
        expect_scalar "a scalar that is not a scalar, compared with its first value" 3 direct_scalar 1 > /dev/null 2>&1
        expect_scalar "the same through Grafana" 3 graf_scalar 1 > /dev/null 2>&1
        echo "$failures $QUERY_FAILURES"
      )
      case $mode in thirdfield) want_counts="1 1" ;; *) want_counts="2 2" ;; esac
      check "$mode: a malformed layout or a vector is a query failure, never the value of its first element" "$want_counts" "$counts" ;;
    data)
      counts=$(
        failures=0
        QUERY_FAILURES=0
        expect_scalar "a value that differs" 4 direct_scalar 1 > /dev/null 2>&1
        echo "$failures $QUERY_FAILURES"
      )
      check "data: a value mismatch is one failure and no query failure" "1 0" "$counts" ;;
  esac
  if [ "$mode" != closed ]; then stop_server; fi
done

part "process candidates and the heartbeat reference share one strict projection: a malformed populated element is a query failure"
# a valid process p1 next to a second element whose process label is null, absent, empty or a number: nothing is dropped silently
for kind in null missing empty number; do
  start_server "pm-$kind" "procmix-$kind"
  point_at "$SERVER_PORT"
  for fn in direct_processes graf_processes; do
    rc=0
    out=$("$fn" 6 2> /dev/null) || rc=$?
    check "procmix-$kind: $fn fails although p1 is valid (the malformed element is not dropped, nothing is printed)" "1 " "$([ "$rc" -ne 0 ] && echo 1) $out"
  done
  rc=0
  out=$(direct_process_ids "$CANDIDATE_REFERENCE" 2> /dev/null) || rc=$?
  check "procmix-$kind: the reference expression fails the same way" "1 " "$([ "$rc" -ne 0 ] && echo 1) $out"
  stop_server
done
# only the heartbeat reference is malformed: the process table itself is valid
for kind in null missing; do
  start_server "rb-$kind" "refbad-$kind"
  point_at "$SERVER_PORT"
  check "refbad-$kind: the process table is valid and lists p1" p1 "$(direct_processes 6)"
  rc=0
  out=$(direct_process_ids "$CANDIDATE_REFERENCE" 2> /dev/null) || rc=$?
  check "refbad-$kind: the reference (a valid p1 and one malformed element) fails and prints nothing" "1 " "$([ "$rc" -ne 0 ] && echo 1) $out"
  stop_server
done
start_server rb-ok data
point_at "$SERVER_PORT"
check "data: the reference expression lists p1" p1 "$(direct_process_ids "$CANDIDATE_REFERENCE")"
stop_server
# the public comparison path: the demo section of dashboard-test.sh against a minimal run state
jq -n '{schemaVersion: 2, project: "x", runId: "r1", evaluationTime: "1130.000", evaluationTimeSeconds: 1130, settledAt: "x", settledAtEpochSeconds: 1130, startedAt: "x", startedAtEpochSeconds: 1000,
  source: {instanceId: "p1"}, timeRange: {fromMs: 1000000, toMs: 1130000}, expectedSeries: [], schemas: [{globalId: 1, contentId: 1}], clients: {probe: "probe"}, notCounted: [], endpoints: {}, metric: "m"}' > "$TMP/state.json"
for mode in data refbad-null refbad-missing; do
  start_server "pub-$mode" "$mode"
  point_at "$SERVER_PORT"
  rc=0
  ./tests/dashboard-test.sh --state "$TMP/state.json" --only demo > "$TMP/pub-$mode.out" 2>&1 || rc=$?
  compared=$({ grep -c 'the candidates are exactly the processes with a heartbeat sample' "$TMP/pub-$mode.out" || true; } | tr -d ' ')
  unknown=$({ grep -c 'the heartbeat reference over the selected interval could not be queried: UNKNOWN' "$TMP/pub-$mode.out" || true; } | tr -d ' ')
  if [ "$mode" = data ]; then
    check "public run, valid reference: the candidates are compared with the reference, no query failure about it" "1 0" "$compared $unknown"
  else
    check "public run, $mode: the reference is a query failure (UNKNOWN) and is never compared" "0 1" "$compared $unknown"
    check "  and the run exits 3" 3 "$rc"
  fi
  stop_server
done

part "the rate oracle: every point against its own window of the recorded requests, never an average"
# The window is 60 s. Requests are "operation TAB completion second"; points "operation TAB time in ms TAB rate"
steady=75000
points_answer() { printf '%s\n' "$POINTS_ANSWER"; }
# reqs_of <op> <first second> <last second> <per second>: that many requests per second in the span (fractions alternate 1 and 2)
reqs_of() { awk -v op="$1" -v a="$2" -v b="$3" -v r="$4" 'BEGIN { acc = 0; for (s = a; s <= b; s++) { acc += r; n = int(acc); acc -= n; for (i = 0; i < n; i++) printf "%s\t%d\n", op, s } }'; }
# expected_points <requests> <op> <lag> <times ms...>: the exact rolling rate of that operation at those times, with the requests shifted by lag
expected_points() {
  local reqs=$1 op=$2 lag=$3 t
  shift 3
  for t in "$@"; do
    awk -F'\t' -v op="$op" -v t="$t" -v lag="$lag" 'BEGIN { ts = t / 1000 - lag; c = 0 } $1 == op && $2 > ts - 60 && $2 <= ts { c++ } END { printf "%s\t%d\t%.6f\n", op, t, c / 60 }' <<< "$reqs"
  done
}
try_oracle() { # try_oracle <requests> <points> <lag> [steady from ms] [requested end ms]: "failures query_failures" of one approx_rate_points, in a subshell
  (
    failures=0
    QUERY_FAILURES=0
    POINTS_ANSWER=$2
    set_window "${4:-$steady}" "${5:-135000}"
    approx_rate_points "controlled" "$1" 0.25 "$3" "${4:-$steady}" points_answer x > /dev/null 2>&1
    echo "$failures $QUERY_FAILURES"
  )
}
# steady traffic: 1.5 SERIALIZE per second and 0.5 DESERIALIZE per second from second 0 to 200, points every 15 s from 75 s to 135 s
steady_reqs="$(reqs_of SERIALIZE 0 200 1.5)
$(reqs_of DESERIALIZE 0 200 0.5)"
times="75000 90000 105000 120000 135000"
# shellcheck disable=SC2086
steady_pts="$(expected_points "$steady_reqs" SERIALIZE 0 $times)
$(expected_points "$steady_reqs" DESERIALIZE 0 $times)"
check "steady traffic: the exact rolling rates pass" "0 0" "$(try_oracle "$steady_reqs" "$steady_pts" 10)"
# shellcheck disable=SC2086
lagged_pts="$(expected_points "$steady_reqs" SERIALIZE 10 $times)
$(expected_points "$steady_reqs" DESERIALIZE 10 $times)"
check "steady traffic: rates that lag the requests by the export and scrape lag pass" "0 0" "$(try_oracle "$steady_reqs" "$lagged_pts" 10)"
check "a constant zero (no series) fails" "1 0" "$(try_oracle "$steady_reqs" "" 10)"
check "zero rates fail" "1 0" "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' -v OFS='\t' '{ $3 = 0; print }')" 10)"
check "rates doubled fail" "1 0" "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' -v OFS='\t' '{ $3 = $3 * 2; print }')" 10)"
check "rates ten times too high fail" "1 0" "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' -v OFS='\t' '{ $3 = $3 * 10; print }')" 10)"
check "a missing operation fails" "1 0" "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' '$1 == "SERIALIZE"')" 10)"
check "an unexpected operation fails" "1 0" "$(try_oracle "$steady_reqs" "$steady_pts
$(printf 'UNKNOWN\t75000\t0.2\nUNKNOWN\t90000\t0.2\nUNKNOWN\t105000\t0.2')" 10)"
check "fewer than three steady points fail" "1 0" "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' '$2 <= 90000')" 10)"
check "points before the steady time are not asserted" "0 0" "$(try_oracle "$steady_reqs" "$steady_pts
$(printf 'SERIALIZE\t30000\t9\nDESERIALIZE\t30000\t9')" 10)"
# uneven traffic: active until second 75, silent after. The correct rolling rates fall 1.5, 1.125, 0.75, 0.375, 0; one average
# over the whole interval is not a rate of any of those windows
uneven_reqs="$(reqs_of SERIALIZE 0 75 1.5)"
# shellcheck disable=SC2086
uneven_pts="$(expected_points "$uneven_reqs" SERIALIZE 0 $times)"
check "uneven traffic: the correct rolling rates pass" "0 0" "$(try_oracle "$uneven_reqs" "$uneven_pts" 10)"
check "  the values are the rolling ones (about 1.5, 1.125, 0.75, 0.375, 0; whole requests per second make them differ by under 0.02)" "ok" \
  "$(printf '%s\n' "$uneven_pts" | awk -F'\t' 'BEGIN { split("1.5 1.125 0.75 0.375 0", w, " ") } { d = $3 - w[NR]; if (d < 0) d = -d; if (d > 0.02) bad = 1 } END { print bad ? "bad" : "ok" }')"
avg=$(awk -F'\t' '$2 > 15 && $2 <= 130 { c++ } END { printf "%.6f", c / 115 }' <<< "$uneven_reqs")
avg_pts=""
for t in $times; do avg_pts="$avg_pts$(printf 'SERIALIZE\t%s\t%s' "$t" "$avg")
"; done
check "  one constant average of the interval is not the rate of any window: it fails" "1 0" "$(try_oracle "$uneven_reqs" "$avg_pts" 10)"
# work after the last evaluated point cannot change what an earlier point expects: add requests from second 140 to 400
late_reqs="$uneven_reqs
$(reqs_of SERIALIZE 140 400 3)"
check "work after the last evaluated point is irrelevant: the same points still pass" "0 0" "$(try_oracle "$late_reqs" "$uneven_pts" 10)"

# --- the oracle against an independent temporal model: intervals, per-operation history, outward uncertainty --------------------
# The reference does not use the oracle's interval algebra. It enumerates instants: a recorded second s is the floored second of a
# completion instant c in [s, s+1) (candidates 0, 1/4, 1/2, 3/4 and 1 - 1e-9 of the second), the exported counter takes the request
# at c + d with the delay d between 0 and the exact lag (candidates 0, 1/4, 1/2, 3/4 and 1 of it), and the request is possibly
# counted when some candidate instant lies in (t - 60, t] and certainly counted when every candidate does (the set of instants is
# an interval, so its two ends decide). Exact fractions throughout; the bounds depend on the time and the requests only, so they
# are computed once per time before the sweep of candidate rates.
cat > "$TMP/ref.py" <<'REFPY'
import sys
from fractions import Fraction as F
tol, lag, w = F(sys.argv[1]), F(sys.argv[2]), 60
eps = F(1, 10**9)
offsets = [F(i, 4) for i in range(4)] + [1 - eps]
delays = [lag * F(i, 4) for i in range(5)]
instants = [[F(r) + o + d for o in offsets for d in delays] for op, r in (l.split("\t") for l in open(sys.argv[3]).read().splitlines() if l) if op == "S"]
bounds = []
for t in sorted({F(int(x), 1000) for x in sys.argv[4].split()}):
    certain = sum(all(t - w < x <= t for x in xs) for xs in instants)
    possible = sum(any(t - w < x <= t for x in xs) for xs in instants)
    bounds.append((F(certain, w) * (1 - tol) - F(1, w), F(possible, w) * (1 + tol) + F(1, w)))
print(" ".join("ok" if all(lo <= F(v) <= hi for lo, hi in bounds) else "bad" for v in sys.argv[5].split()))
REFPY
# lag_case <description> <requests> <lag> <times ms> <values>: the oracle's verdict on every value equals the reference's. The
# requested interval ends one millisecond after the last time, so only the per-point bands are in question here
lag_case() {
  local d=$1 reqs=$2 lag=$3 tms=$4 vals=$5 want got="" v pts t first last
  printf '%s\n' "$reqs" > "$TMP/lag-reqs.txt"
  want=$(python3 -I "$TMP/ref.py" 0.25 "$lag" "$TMP/lag-reqs.txt" "$tms" "$vals")
  first=${tms%% *}
  last=${tms##* }
  for v in $vals; do
    pts=""
    for t in $tms; do pts="$pts$(printf 'S\t%s\t%s' "$t" "$v")
"; done
    case "$(try_oracle "$reqs" "$pts" "$lag" "$first" $((last + 1)))" in "0 0") got="$got ok" ;; *) got="$got bad" ;; esac
  done
  check "$d" "$want" "${got# }"
}
sweep="0 0.01 0.02 0.04 0.1 0.25 0.3 0.36 0.4 0.5 0.6 0.65 0.7 0.9 1.0 1.2 1.27 1.3 1.5 2.0"
# the rate at 100 s is 0.5 for a lag of 0 and of 10 seconds but 1.0 for a lag of 5: 30 requests at 38 s and 30 at 92 s
burst="$(reqs_of S 38 38 30)
$(reqs_of S 92 92 30)"
lag_case "a bursty workload, lag 10: every rate a lag between 0 and 10 can produce is allowed, nothing above" "$burst" 10 "100000 100000 100000" "$sweep"
lag_case "  the same workload, lag 0" "$burst" 0 "100000 100000 100000" "$sweep"
lag_case "  the same workload, fractional lag 2.5" "$burst" 2.5 "100000 100000 100000" "$sweep"
lag_case "  a quiet interval after an old burst, lag 10 (the lower bound reaches zero)" "$(reqs_of S 10 10 30)" 10 "100000 115000 130000" "$sweep"
lag_case "  zero lag with steady requests, three points" "$(reqs_of S 0 200 0.5)" 0 "100000 115000 130000" "$sweep"
lag_case "  bursts at both ends of the window, lag 7.5" "$(reqs_of S 36 36 12)
$(reqs_of S 97 97 12)
$(reqs_of S 99 99 24)" 7.5 "100000 107000 114000" "$sweep"
# a recorded second is the floored second of a completion instant: 30 requests recorded at 90 s may really finish at 90.9 s and be
# exported at 100.9 s, so zero at 100 s is legitimate with a lag of 10 even though a whole-second model counts them as certain
old_and_burst="$(reqs_of S 0 0 1)
$(reqs_of S 90 90 30)"
lag_case "30 requests recorded at 90 s after one old request, lag 10, point at 100 s: the burst may not be counted yet (zero is allowed)" "$old_and_burst" 10 "100000" "$sweep"
lag_case "  the same with a point at 115 s: now it is certain" "$old_and_burst" 10 "115000" "$sweep"
# request-second interval edges, zero lag: recorded 99 is certainly inside (100), recorded 100 only possibly, recorded 40 possibly
# (its instants lie in [40, 41) and the window (40, 100] excludes only 40 itself), recorded 39 cannot be inside
for sec in 99 100 40 39; do
  lag_case "  zero lag, 30 requests recorded at $sec s, point at 100 s" "$(reqs_of S $sec $sec 30)" 0 "100000" "$sweep"
done
# maximum lag 10: certain from second 89 on (89 + 1 + 10 = 100), possible from 30 (30 + 1 + 10 > 40) and not below
for sec in 89 90 30 29; do
  lag_case "  lag 10, 30 requests recorded at $sec s, point at 100 s" "$(reqs_of S $sec $sec 30)" 10 "100000" "$sweep"
done
# fractional lags are never rounded inward: lag 0.5004 s rounded to 0.5 would make the burst recorded at 98 s certain at 99.5 s
lag_case "fractional lag 0.5004, 30 requests recorded at 98 s, point at 99.5 s (not aligned to a second)" "$(reqs_of S 98 98 30)" 0.5004 "99500" "$sweep"
lag_case "  lag 2.5004, the same burst, point at 100.5 s" "$(reqs_of S 98 98 30)" 2.5004 "100500" "$sweep"
lag_case "  lag 0.0004, a burst recorded at 99 s, points at 100 s and 100.0004 s" "$(reqs_of S 99 99 30)" 0.0004 "100000 100001" "$sweep"
lag_case "  lag 2.5004, nonaligned points 100.25 s, 100.5 s and 101.75 s" "$(reqs_of S 98 98 30)
$(reqs_of S 60 60 10)" 2.5004 "100250 100500 101750" "$sweep"
check "the doubled mutant of steady DESERIALIZE traffic (0.5 per second, lag 10) is still detected" "1 0" \
  "$(try_oracle "$steady_reqs" "$(printf '%s\n' "$steady_pts" | awk -F'\t' -v OFS='\t' '$1 == "DESERIALIZE" { $3 = $3 * 2 } { print }')" 10)"

# membership: each operation has its own history, and presence is decided from the requested interval, never from what another series
# returned. SERIALIZE starts at 0 s; DESERIALIZE starts at 30 s (mature at 30 + 1 + 10 + 60 = 101 s), 120 s or 140 s
tms16="75000 90000 105000 120000 135000 150000 165000 180000"
ser_reqs="$(reqs_of SERIALIZE 0 300 1.5)"
# shellcheck disable=SC2086
ser_pts="$(expected_points "$ser_reqs" SERIALIZE 0 $tms16)"
des30="$(reqs_of DESERIALIZE 30 300 1)"
des30_pts() { expected_points "$ser_reqs
$des30" DESERIALIZE 0 "$@"; }
mem() { # mem <requests> <points> <end ms> <steady from ms>: "failures query_failures"
  try_oracle "$1" "$2" 10 "$4" "$3"
}
check "an operation that becomes mature later (30 s start, mature at 101 s), all points from 75 s to 180 s: passes" "0 0" \
  "$(mem "$ser_reqs
$des30" "$ser_pts
$(des30_pts 75000 90000 105000 120000 135000 150000 165000 180000)" 180000 75000)"
check "  dropping it entirely fails (its maturity is after the first steady point, but well before the requested end)" "1 0" \
  "$(mem "$ser_reqs
$des30" "$ser_pts" 180000 75000)"
check "  its series appearing only once mature (points from 105 s on) passes" "0 0" \
  "$(mem "$ser_reqs
$des30" "$ser_pts
$(des30_pts 105000 120000 135000 150000 165000 180000)" 180000 75000)"
check "  only the points before its maturity (75 s and 90 s) fail: nothing from the mature part of the interval" "1 0" \
  "$(mem "$ser_reqs
$des30" "$ser_pts
$(des30_pts 75000 90000)" 180000 75000)"
check "  two points from the mature part are fewer than the three of the 79 seconds left: fail" "1 0" \
  "$(mem "$ser_reqs
$des30" "$ser_pts
$(des30_pts 165000 180000)" 180000 75000)"
check "  a query that ends at 100 s, before it could be observed as mature, does not require it" "0 0" \
  "$(mem "$ser_reqs
$des30" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 100000')" 100000 75000)"
check "  a query ending at 130 s leaves 29 seconds, one step of coverage required and absent: fail" "1 0" \
  "$(mem "$ser_reqs
$des30" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 130000')" 130000 75000)"
check "  the same query with that one point present passes" "0 0" \
  "$(mem "$ser_reqs
$des30" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 130000')
$(des30_pts 120000)" 130000 75000)"
# per-operation history: SERIALIZE through 135 s says nothing about DESERIALIZE, whose first request is at 120 s
des120="$(reqs_of DESERIALIZE 120 300 1)"
check "an operation starting at 120 s returned only from 120 s on passes" "0 0" \
  "$(mem "$ser_reqs
$des120" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$(printf 'DESERIALIZE\t120000\t0.01\nDESERIALIZE\t135000\t0.3')" 135000 75000)"
check "  the same operation returned at 75, 90 and 105 s fails although SERIALIZE returned points to 135 s" "1 0" \
  "$(mem "$ser_reqs
$des120" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$(printf 'DESERIALIZE\t75000\t0\nDESERIALIZE\t90000\t0\nDESERIALIZE\t105000\t0')" 135000 75000)"
check "  a mixture of points before and after its first request fails" "1 0" \
  "$(mem "$ser_reqs
$des120" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$(printf 'DESERIALIZE\t105000\t0\nDESERIALIZE\t120000\t0.01\nDESERIALIZE\t135000\t0.3')" 135000 75000)"
des140="$(reqs_of DESERIALIZE 140 300 1)"
check "an operation first requested after the query end is not required: the earlier answer still passes" "0 0" \
  "$(mem "$ser_reqs
$des140" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')" 135000 75000)"
check "  returned before it was ever requested it is unexpected" "1 0" \
  "$(mem "$ser_reqs
$des140" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$(printf 'DESERIALIZE\t75000\t0\nDESERIALIZE\t90000\t0\nDESERIALIZE\t105000\t0')" 135000 75000)"
check "  an operation first requested after the last returned point but before the requested end (125 s, last point 120 s) is not required" "0 0" \
  "$(mem "$ser_reqs
$(reqs_of DESERIALIZE 125 300 1)" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 120000')" 135000 75000)"
quiet_old="$ser_reqs
$(reqs_of DESERIALIZE 1 3 1)"
zero_des="$(printf 'DESERIALIZE\t75000\t0\nDESERIALIZE\t90000\t0\nDESERIALIZE\t105000\t0\nDESERIALIZE\t120000\t0\nDESERIALIZE\t135000\t0')"
check "an operation whose traffic aged out keeps a legitimate zero rate and stays required" "0 0" \
  "$(mem "$quiet_old" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$zero_des" 135000 75000)"
check "  its absence fails" "1 0" "$(mem "$quiet_old" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')" 135000 75000)"
check "an operation without any recorded request is unexpected" "1 0" \
  "$(mem "$ser_reqs" "$(printf '%s\n' "$ser_pts" | awk -F'\t' '$2 <= 135000')
$zero_des" 135000 75000)"

# a rate is never negative, whatever the slack, at any point; zero is a legitimate rate, not absent data
qreqs="$(reqs_of S 0 5 1)"
check "a quiet interval at zero passes (zero is a rate, not absent data)" "0 0" \
  "$(try_oracle "$qreqs" "$(printf 'S\t100000\t0\nS\t115000\t0\nS\t130000\t0')" 10 100000)"
check "  a rate of -0.01 in the quiet interval fails although the timing slack reaches -1/60" "1 0" \
  "$(try_oracle "$qreqs" "$(printf 'S\t100000\t-0.01\nS\t115000\t0\nS\t130000\t0')" 10 100000)"
check "  a negative rate before the steady time fails too" "1 0" \
  "$(try_oracle "$qreqs" "$(printf 'S\t30000\t-0.5\nS\t100000\t0\nS\t115000\t0\nS\t130000\t0')" 10 100000)"
for bad in nan NaN inf +Inf null ''; do
  check "  a rate written as '$bad' fails" "1 0" "$(try_oracle "$qreqs" "$(printf 'S\t100000\t%s\nS\t115000\t0\nS\t130000\t0' "$bad")" 10 100000)"
done

part "the traffic of the scenarios: a planned fetch is made and recorded, or the scenario fails before more traffic"
API_SAVED=$API
start_server fetch data
API=http://127.0.0.1:$SERVER_PORT/apis/registry/v3
TRAFFIC_LOG=$TMP/traffic.log
: > "$TRAFFIC_LOG"
rc=0
DASH_TRAFFIC_SLEEP=0 dash_steady_traffic 7 "$TRAFFIC_LOG" 4 > "$TMP/traffic.out" 2>&1 || rc=$?
check "steady traffic of 4 iterations succeeds and records all 4 + 2 + 2 = 8 answered fetches" "0 8" "$rc $(wc -l < "$TRAFFIC_LOG" | tr -d ' ')"
check "  c1 SERIALIZE 4, c2 SERIALIZE 2, c1 DESERIALIZE 2" "4 2 2" "$(awk '$2 == "SERIALIZE" && $3 == "c1" { a++ } $2 == "SERIALIZE" && $3 == "c2" { b++ } $2 == "DESERIALIZE" && $3 == "c1" { c++ } END { print a + 0, b + 0, c + 0 }' "$TRAFFIC_LOG")"
stop_server
FETCH_FAIL_ON=5 start_server fetchfail data
API=http://127.0.0.1:$SERVER_PORT/apis/registry/v3
: > "$TRAFFIC_LOG"
rc=0
DASH_TRAFFIC_SLEEP=0 dash_steady_traffic 7 "$TRAFFIC_LOG" 6 > "$TMP/traffic-fail.out" 2>&1 || rc=$?
check "an injected failure of the fifth fetch (iteration 3's c1 SERIALIZE) fails the traffic" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
check "  naming the global ID, client and operation of the request that failed, with its status" yes \
  "$(grep -q 'planned fetch of global ID 7 as client c1, operation SERIALIZE answered 500' "$TMP/traffic-fail.out" && echo yes || echo no)"
check "  no later traffic was sent: the server saw exactly 5 fetches" 5 "$(grep -c '^FETCH ' "$SERVER_LOG" | tr -d ' ')"
check "  only the 4 answered fetches were recorded, never the failed one" 4 "$(wc -l < "$TRAFFIC_LOG" | tr -d ' ')"
stop_server
API=$API_SAVED

part "the rate chart's own request: flags, window, datasource and the points it returns"
RATE_SCN=$TMP/rates.json
# the recorded requests behind the server's constant 1.5 and 0.5 per second, from second 940 to 1140 (the windows of the points
# from 1075 s to 1130 s lie inside)
ser=$(reqs_of SERIALIZE 940 1140 1.5 | cut -f2 | jq -R -s -c 'split("\n") | map(select(length > 0) | tonumber)')
des=$(reqs_of DESERIALIZE 940 1140 0.5 | cut -f2 | jq -R -s -c 'split("\n") | map(select(length > 0) | tonumber)')
jq -n --argjson ser "$ser" --argjson des "$des" '{schemaVersion: 1, scenarios: [{kind: "rates", instance: "p1", fromMs: 1000000, toMs: 1130000, steadyFromMs: 1075000, requests: {SERIALIZE: $ser, DESERIALIZE: $des}, lagSeconds: 10, tolerance: 0.25}]}' > "$RATE_SCN"
start_server rates rates
point_at "$SERVER_PORT"
rc=0
./tests/dashboard-test.sh --only scenarios --scenario "$RATE_SCN" > "$TMP/rates.out" 2>&1 || rc=$?
check "steady controlled rates through Prometheus and Grafana pass" 0 "$rc"
check "  both backends were asked" 2 "$(grep -c 'controlled traffic' "$TMP/rates.out" | tr -d ' ')"
check "  Prometheus got a range query with a 15 second step" 1 "$(grep -c 'GET /api/v1/query_range.*step=15' "$SERVER_LOG" | tr -d ' ')"
body=$(grep '^{' "$SERVER_LOG" | tail -1)
check "  Grafana got the panel's own flags: a range request in time series format, not an instant one" "true false time_series" \
  "$(jq -r '.queries[0] | "\(.range) \(.instant) \(.format)"' <<< "$body")"
check "  over the scenario's window" "1000000 1130000" "$(jq -r '"\(.from) \(.to)"' <<< "$body")"
check "  with the datasource of the example" apicurio-usage-prometheus "$(jq -r '.queries[0].datasource.uid' <<< "$body")"
: > "$SERVER_LOG"
rc=0
GRAFANA_DATASOURCE_UID=ops-parent ./tests/dashboard-test.sh --only scenarios --external --scenario "$RATE_SCN" > "$TMP/rates-ext.out" 2>&1 || rc=$?
check "an explicit GRAFANA_DATASOURCE_UID is honored by the public --external run" "0 ops-parent" "$rc $(grep '^{' "$SERVER_LOG" | tail -1 | jq -r '.queries[0].datasource.uid')"
jq '.scenarios[0].requests |= map_values(. + .)' "$RATE_SCN" > "$TMP/rates-wrong.json"
rc=0
./tests/dashboard-test.sh --only scenarios --scenario "$TMP/rates-wrong.json" > "$TMP/rates-wrong.out" 2>&1 || rc=$?
check "rates that differ from the controlled ones fail with exit 1 (a value mismatch of successful queries)" 1 "$rc"
stop_server
# Grafana's range request fails while its instant requests would have succeeded: the suite must fail, with exit 3
INJECT_GRAF500_ON='*' start_server rates-g500 rates
point_at "$SERVER_PORT"
rc=0
./tests/dashboard-test.sh --only scenarios --scenario "$RATE_SCN" > "$TMP/rates-g500.out" 2>&1 || rc=$?
check "a range-only Grafana failure fails the suite with exit 3, although Prometheus answered correctly" 3 "$rc"
check "  and says the query failed" yes "$(grep -q 'graf_rate_points failed' "$TMP/rates-g500.out" && echo yes || echo no)"
rc=0
./tests/dashboard-test.sh --only scenarios --scenario "$TMP/rates-wrong.json" > "$TMP/rates-mixed.out" 2>&1 || rc=$?
check "a mismatch seen by Prometheus together with a Grafana failure is exit 3, not 1" 3 "$rc"
stop_server
# points the chart's API could return that are no observations: duplicates, points before or after the requested window, descending order, a series without its operation, times finer than a millisecond that would floor into duplicates, a negative or non-finite rate
for mode in rates-dup rates-late rates-early rates-unordered rates-nolabels rates-subms rates-premicro rates-micro rates-negative rates-nan rates-inf; do
  start_server "pts-$mode" "$mode"
  point_at "$SERVER_PORT"
  set_window 1000000 1130000
  V_INSTANCE=p1
  for fn in direct_rate_points graf_rate_points; do
    rc=0
    "$fn" 4 > /dev/null 2>&1 || rc=$?
    check "$mode: $fn fails (malformed points are not observations)" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  done
  rc=0
  ./tests/dashboard-test.sh --only scenarios --scenario "$RATE_SCN" > "$TMP/pts-$mode.out" 2>&1 || rc=$?
  check "$mode: the public run exits 3, the points could not be read as observations" 3 "$rc"
  stop_server
done
start_server pts-ok rates
point_at "$SERVER_PORT"
set_window 1000000 1130000
check "rates: the well formed points of both backends are accepted, 9 per operation, 18 in all" "18 18" "$(direct_rate_points 4 | wc -l | tr -d ' ') $(graf_rate_points 4 | wc -l | tr -d ' ')"
# integer milliseconds that are not whole seconds, at epoch scale, are kept exactly
set_window 1795213778123 1795213908123
check "rates: millisecond times at epoch scale (...778123 ms) are accepted and read back exactly through Prometheus" "$(printf 'DESERIALIZE\t1795213778123\t0.5')" "$(direct_rate_points 4 | head -1)"
check "  and through Grafana" "$(printf 'DESERIALIZE\t1795213778123\t0.5')" "$(graf_rate_points 4 | head -1)"
stop_server
start_server pts-zero rates-zero
point_at "$SERVER_PORT"
set_window 1000000 1130000
check "rates: a rate of zero is a legitimate point, accepted by both backends (18 points)" "18 18" "$(direct_rate_points 4 | wc -l | tr -d ' ') $(graf_rate_points 4 | wc -l | tr -d ' ')"
stop_server
# precision follows the magnitude: a true sub-millisecond time is refused at any scale and never rounded into the window, while whole
# milliseconds are kept at any scale however much float noise the seconds representation adds
for win in "1000000 1130000" "1795213778123 1795213908123"; do
  win_from=${win% *}
  win_to=${win#* }
  for mode in rates-micro rates-premicro; do
    start_server "prec-$mode" "$mode"
    point_at "$SERVER_PORT"
    set_window "$win_from" "$win_to"
    for fn in direct_rate_points graf_rate_points; do
      rc=0
      "$fn" 4 > /dev/null 2>&1 || rc=$?
      check "$mode, window starting at $win_from ms: $fn refuses times that are not whole milliseconds" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    done
    stop_server
  done
  start_server prec-noise rates-noise
  point_at "$SERVER_PORT"
  set_window "$win_from" "$win_to"
  want=$((((130000 / 37) + 1) * 2))
  check "rates-noise, window starting at $win_from ms: a point every 37 ms is kept whole, no float noise is refused ($want points per backend)" "$want $want" \
    "$(direct_rate_points 4 | wc -l | tr -d ' ') $(graf_rate_points 4 | wc -l | tr -d ' ')"
  stop_server
done

part "the mutation experiment: a backend error is never a killed mutant"
mut_runner() { # mut_runner <name> <command...>
  OUT=$TMP/$1.out
  RC=0
  shift
  "$@" > "$OUT" 2>&1 || RC=$?
}
run_experiment() { # run_experiment <label>: prints "failures query_failures" of the experiment, run in a subshell
  local label=$1
  (
    failures=0
    QUERY_FAILURES=0
    mkdir -p "$TMP/exp-$label"
    mutant_experiment "$RATE_SCN" "$TMP/exp-$label" mut_runner > "$TMP/exp-$label.out" 2>&1
    echo "$failures $QUERY_FAILURES"
  )
}
start_server mut-ok rates
point_at "$SERVER_PORT"
check "the baseline passes while the mutants are all killed by values: no failure" "0 0" "$(run_experiment ok)"
check "  each mutant was judged on its own run" 3 "$(grep -c 'the rate assertions failed on values (exit 1)' "$TMP/exp-ok.out" | tr -d ' ')"
stop_server
INJECT_GRAF500_ON='0 * (' start_server mut-inject rates
point_at "$SERVER_PORT"
rc=0
./tests/dashboard-test.sh --only scenarios --scenario "$RATE_SCN" > /dev/null 2>&1 || rc=$?
check "with the injection active the baseline still passes (the failure is injected only into the constant zero mutant's Grafana request)" 0 "$rc"
check "an error in Prometheus-seen mismatch plus Grafana 500 during one mutant fails the experiment, once, as a query failure" "1 1" "$(run_experiment inject)"
check "  the other two mutants are still judged killed on their own" 2 "$(grep -c 'the rate assertions failed on values (exit 1)' "$TMP/exp-inject.out" | tr -d ' ')"
check "  and the failing one says its outcome is not proven" yes "$(grep -q 'constant-zero: a query failed during the mutant run (exit 3)' "$TMP/exp-inject.out" && echo yes || echo no)"
stop_server
# the verdict function on each status
for st in 1 3 0 2; do
  printf 'FAIL  controlled traffic (direct_rate_points): rates differ\n' > "$TMP/verdict.out"
  out=$( (failures=0; QUERY_FAILURES=0; mutant_verdict m "$st" "$TMP/verdict.out" > /dev/null 2>&1; echo "$failures $QUERY_FAILURES") )
  case $st in 1) want="0 0" ;; 3) want="1 1" ;; *) want="1 0" ;; esac
  check "mutant_verdict for exit status $st" "$want" "$out"
done

part "scenario files are exactly one JSON object, validated before any query"
start_server scenario data
point_at "$SERVER_PORT"
i=0
while IFS= read -r bad; do
  i=$((i + 1))
  printf '{"schemaVersion":1,"scenarios":[%s]}\n' "$bad" > "$TMP/bad-$i.json"
  : > "$SERVER_LOG"
  rc=0
  ./tests/dashboard-test.sh --only scenarios --scenario "$TMP/bad-$i.json" > "$TMP/bad-$i.out" 2>&1 || rc=$?
  check "invalid scenario $i is refused with status 2 ($(printf '%s' "$bad" | cut -c1-70))" 2 "$rc"
  check "  and no query was sent" 0 "$(wc -l < "$SERVER_LOG" | tr -d ' ')"
done << 'EOF'
{"kind":"overflow","instance":"p","fromMs":1000,"toMs":2000,"detail":-1,"total":6}
{"kind":"overflow","instance":"p","fromMs":1000,"toMs":2000,"detail":3,"total":1.5}
{"kind":"overflow","instance":"p","fromMs":1000,"toMs":2000,"detail":7,"total":6}
{"kind":"overflow","instance":"p","fromMs":2000,"toMs":2000,"detail":3,"total":6}
{"kind":"overflow","instance":"","fromMs":1000,"toMs":2000,"detail":3,"total":6}
{"kind":"overflow","fromMs":1000,"toMs":2000,"detail":3,"total":6}
{"kind":"overflow","instance":"p","fromMs":"1000","toMs":2000,"detail":3,"total":6}
{"kind":"nonsense","instance":"p","fromMs":1000,"toMs":2000}
{"kind":"heartbeat-only","fromMs":1000,"toMs":2000}
{"kind":"processes","primary":"p","other":"p","fromMs":1000,"toMs":2000,"primaryRows":[],"otherRows":[]}
{"kind":"processes","primary":"p","other":"q","fromMs":1000,"toMs":2000,"primaryRows":[["c","global_id","1","SERIALIZE","x"]],"otherRows":[]}
{"kind":"stopped","instance":"p","fromMs":1000,"toMs":2000,"rows":[["c","global_id"]]}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[-1]},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[1]},"lagSeconds":10,"tolerance":2}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[]},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[1.5]},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[1]},"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"requests":{"SERIALIZE":[1]},"lagSeconds":61,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":1500,"rates":{"SERIALIZE":1},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"steadyFromMs":2000,"requests":{"SERIALIZE":[1]},"lagSeconds":10,"tolerance":0.1}
{"kind":"rates","instance":"p","fromMs":1000,"toMs":2000,"requests":{"SERIALIZE":[1]},"lagSeconds":10,"tolerance":0.1}
EOF
: > "$TMP/empty.json"
printf ' \n\t\n' > "$TMP/blank.json"
printf '{"schemaVersion":1,"scenarios":[]}\n{"schemaVersion":1,"scenarios":[]}\n' > "$TMP/two.json"
printf '{"schemaVersion":1,"scenarios":[' > "$TMP/truncated.json"
printf '[{"schemaVersion":1}]\n' > "$TMP/array.json"
printf '42\n' > "$TMP/number.json"
printf '{"schemaVersion":2,"scenarios":[{"kind":"overflow","instance":"p","fromMs":1000,"toMs":2000,"detail":3,"total":6}]}\n' > "$TMP/v2.json"
printf '{"schemaVersion":1,"scenarios":[]}\n' > "$TMP/none.json"
for f in empty blank two truncated array number v2 none; do
  : > "$SERVER_LOG"
  rc=0
  ./tests/dashboard-test.sh --only scenarios --scenario "$TMP/$f.json" > "$TMP/$f.out" 2>&1 || rc=$?
  check "the scenario document '$f' is refused with status 2" 2 "$rc"
  check "  and no query was sent" 0 "$(wc -l < "$SERVER_LOG" | tr -d ' ')"
done
check "  an empty file is not a pass" no "$(grep -q 'All checks passed' "$TMP/empty.out" && echo yes || echo no)"
rc=0
./tests/dashboard-test.sh --only scenarios > /dev/null 2>&1 || rc=$?
check "--only scenarios without a scenario file is refused" 1 "$rc"
rc=0
./tests/dashboard-test.sh --only nonsense > /dev/null 2>&1 || rc=$?
check "an unknown section is refused" 1 "$rc"
stop_server

part "provisioning and the mount are required unless --external"
NORUNTIME=no-such-runtime-for-the-dashboard-test
mkdir -p "$TMP/shim"
printf '#!/bin/sh\nexit 1\n' > "$TMP/shim/podman"
chmod +x "$TMP/shim/podman"
for dm in ok unprovisioned missing error garbage; do
  DASH_MODE=$dm DASHBOARD_FILE=$DASHBOARD_FILE start_server "prov-$dm" data
  for runtime in none unavailable; do
    rc=0
    if [ "$runtime" = none ]; then
      CONTAINER_RUNTIME=$NORUNTIME GRAFANA_URL=http://127.0.0.1:$SERVER_PORT ./tests/dashboard-test.sh --only provisioned > "$TMP/p.out" 2>&1 || rc=$?
    else
      CONTAINER_RUNTIME=podman PATH=$TMP/shim:$PATH GRAFANA_URL=http://127.0.0.1:$SERVER_PORT ./tests/dashboard-test.sh --only provisioned > "$TMP/p.out" 2>&1 || rc=$?
    fi
    check "$dm dashboard, runtime $runtime: the run fails (an unverified mount is never a verified one)" 1 "$rc"
    if [ "$dm" = ok ]; then
      check "  the dashboard check itself still ran and passed (no abort on the runtime)" yes "$(grep -q 'ok    Grafana holds the dashboard' "$TMP/p.out" && echo yes || echo no)"
      check "  and the equality with the canonical file was checked" yes "$(grep -q "ok    Grafana's copy equals the canonical file" "$TMP/p.out" && echo yes || echo no)"
    fi
    check "  the mount is reported as NOT verified" yes "$(grep -q 'mount is NOT verified' "$TMP/p.out" && echo yes || echo no)"
  done
  rc=0
  CONTAINER_RUNTIME=$NORUNTIME GRAFANA_URL=http://127.0.0.1:$SERVER_PORT ./tests/dashboard-test.sh --only provisioned --external > "$TMP/p.out" 2>&1 || rc=$?
  check "$dm dashboard with --external: endpoints only, the run passes" 0 "$rc"
  stop_server
done

# The comparison with the canonical file: a difference, a jq failure and a diff error are three different results. A jq or diff that
# fails must never read as an empty difference (equal).
part "the provisioned copy is compared by status, not by output"
REAL_JQ=$(command -v jq)
mkdir -p "$TMP/shim-jq" "$TMP/shim-diff"
# shellcheck disable=SC2016  # the $a of the shim is literal on purpose
printf '#!/bin/sh\nfor a in "$@"; do [ "$a" = -S ] && exit 5; done\nexec "%s" "$@"\n' "$REAL_JQ" > "$TMP/shim-jq/jq"
printf '#!/bin/sh\nexit 2\n' > "$TMP/shim-diff/diff"
chmod +x "$TMP/shim-jq/jq" "$TMP/shim-diff/diff"
prov_run() { # prov_run <dash mode> <extra PATH dir or empty>
  DASH_MODE=$1 DASHBOARD_FILE=$DASHBOARD_FILE start_server "cmp-$1" data
  rc=0
  CONTAINER_RUNTIME=$NORUNTIME PATH="${2:+$2:}$PATH" GRAFANA_URL=http://127.0.0.1:$SERVER_PORT ./tests/dashboard-test.sh --only provisioned > "$TMP/c.out" 2>&1 || rc=$?
  stop_server
}
prov_run ok ""
check "an identical copy: the comparison passes" yes "$(grep -q "ok    Grafana's copy equals the canonical file" "$TMP/c.out" && echo yes || echo no)"
prov_run diffs ""
check "a copy that differs: the check fails as a difference (not as an error)" "yes no" \
  "$(grep -q "FAIL  Grafana's copy differs from the canonical file" "$TMP/c.out" && echo yes || echo no) $(grep -q 'could not be compared' "$TMP/c.out" && echo yes || echo no)"
prov_run ok "$TMP/shim-jq"
check "a jq that fails: UNKNOWN, not equal and not a difference" "no no yes" \
  "$(grep -q "ok    Grafana's copy equals" "$TMP/c.out" && echo yes || echo no) $(grep -q "copy differs" "$TMP/c.out" && echo yes || echo no) $(grep -q 'could not be compared' "$TMP/c.out" && echo yes || echo no)"
prov_run ok "$TMP/shim-diff"
check "a diff that fails (status 2): UNKNOWN, not equal" "no yes" \
  "$(grep -q "ok    Grafana's copy equals" "$TMP/c.out" && echo yes || echo no) $(grep -q 'could not be compared' "$TMP/c.out" && echo yes || echo no)"

part "the dashboard identity comes from the file"
check "the canonical file's UID" apicurio-schema-usage "$(dashboard_uid)"
printf '{"title":"x"}\n' > "$TMP/nouid.json"
rc=0
dashboard_uid "$TMP/nouid.json" > /dev/null || rc=$?
check "a dashboard without a uid is refused by dashboard_uid (non-zero)" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
rc=0
./tests/dashboard-test.sh --only static --dashboard "$TMP/nouid.json" > /dev/null 2>&1 || rc=$?
check "and by the dashboard test" 1 "$rc"
jq '.panels[0].description += " The Registry creates a random UUID at each start."' "$DASHBOARD_FILE" > "$TMP/uuid.json"
rc=0
./tests/dashboard-test.sh --only static --dashboard "$TMP/uuid.json" > "$TMP/uuid.out" 2>&1 || rc=$?
check "a description that promises a UUID created at each start fails the static checks" 1 "$rc"
jq '(.panels[] | select(.id == 6) | .description) += " It shows the exact newest heartbeat."' "$DASHBOARD_FILE" > "$TMP/exact.json"
rc=0
./tests/dashboard-test.sh --only static --dashboard "$TMP/exact.json" > /dev/null 2>&1 || rc=$?
check "an exactness claim on the process table fails the static checks" 1 "$rc"
rc=0
./tests/dashboard-test.sh --only static > /dev/null 2>&1 || rc=$?
check "the canonical file passes the static checks" 0 "$rc"
check "the process variable parser is the shared one: a definition that is not label_values(<selector>,service_instance_id) is refused" "no" \
  "$(jq '(.templating.list[] | select(.name == "instance") | .definition) = "query_result(up)"' "$DASHBOARD_FILE" > "$TMP/badvar.json"; var_selector "$TMP/badvar.json" > /dev/null 2>&1 && echo yes || echo no)"

if [ "$failures" -gt 0 ]; then
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
printf '\nAll checks passed.\n'
