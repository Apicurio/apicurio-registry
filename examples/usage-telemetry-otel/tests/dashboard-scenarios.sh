#!/usr/bin/env bash
# Runs the dashboard checks for the situations the finite demo cannot create, on a real, disposable stack of its own, by running
# the dashboard-scenarios case of tests/lifecycle-test.sh. It adds no ownership code of its own: the lifecycle runner holds the
# project claim (the same one lifecycle runs use, so the two entry points cannot meet on a project), refuses a project that
# already has containers, volumes or a network, gives the run its own directories, settles the script it is running on INT or
# TERM before it cleans up, and purges only its own project at the end.
#
# Usage: REGISTRY_IMAGE=<image> ./tests/dashboard-scenarios.sh [--project NAME] [--registry-port N] [--prometheus-port N]
#                                                              [--grafana-port N] [--collector-health-port N] [--keep]
# Defaults: project dash-scn-<random>, ports 48081, 49090, 43000 and 43133 (the lifecycle runner's own defaults are 38081 and up).
# --keep leaves the stack and the scenario files in place (the run says where, and how to remove them).
# Duration is set by phases and bounded waits, not by one number. Setup, the first fetches and the restart take a couple of
# minutes (each wait has its own deadline: Prometheus showing the fetches 90 s, a heartbeat of the second process 120 s). The
# steady traffic takes about three minutes and runs while the first process is silent. That process is read only after more than
# five minutes without a sample (Prometheus' five minute instant lookback and the Collector's five minute metric expiration),
# waited for up to seven minutes from the end of the traffic. The checks and three mutant runs then take about a minute. One
# observed run took six minutes; a slow machine or a slow image pull takes longer, up to the sum of those deadlines.
# Situations, in this order, each with its own time window: heartbeat-only (a new process has exported only its heartbeat),
# overflow (six fetches by six clients at a series limit of four), restart (a new process reusing the same typed identifiers),
# rates (traffic recorded request by request; every point of the chart's own range request is checked against the requests of its own
# 60 second window, so uneven traffic or later work cannot change another point's expectation),
# stopped (the first process, read after Prometheus and the Collector have forgotten it).
# The exit status is the lifecycle runner's: 0 only if every check passed.
# Cancellation: a signal sent to this process alone reaches the script call being run (it settles before the cleanup). The
# case's own waits and queries run in the runner's shell: a read-only HTTP request that is in flight when the signal arrives is
# waited for until it answers or its own limit (HTTP_TIMEOUT, 10 seconds by default) ends it, and only then does the runner
# exit with 143, clean up its own project once and release its claim. Nothing is started after the signal.
set -euo pipefail
cd "$(dirname "$0")/.."

project=dash-scn-$(od -An -N3 -tx1 /dev/urandom | tr -d ' \n')
rp=48081 pp=49090 gp=43000 cp=43133
keep=""
port() { # port <option> <value>
  case "$2" in "" | *[!0-9]*) echo "error: $1 needs a port number" >&2; exit 2 ;; esac
}
while [ $# -gt 0 ]; do
  case "$1" in
    --project) [ $# -ge 2 ] || { echo "error: --project needs a name" >&2; exit 2; }; project=$2; shift 2 ;;
    --registry-port) [ $# -ge 2 ] || port "$1" ""; port "$1" "$2"; rp=$2; shift 2 ;;
    --prometheus-port) [ $# -ge 2 ] || port "$1" ""; port "$1" "$2"; pp=$2; shift 2 ;;
    --grafana-port) [ $# -ge 2 ] || port "$1" ""; port "$1" "$2"; gp=$2; shift 2 ;;
    --collector-health-port) [ $# -ge 2 ] || port "$1" ""; port "$1" "$2"; cp=$2; shift 2 ;;
    --keep) keep=--keep; shift ;;
    -h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "error: unknown option '$1'" >&2; exit 2 ;;
  esac
done
export TEST_PROJECT=$project TEST_REGISTRY_PORT=$rp TEST_PROMETHEUS_PORT=$pp TEST_GRAFANA_PORT=$gp TEST_COLLECTOR_HEALTH_PORT=$cp
# exec: the lifecycle runner is this process, so a signal reaches its own handlers and nothing here outlives it
if [ -n "$keep" ]; then exec ./tests/lifecycle-test.sh "$keep" dashboard-scenarios; fi
exec ./tests/lifecycle-test.sh dashboard-scenarios
