#!/usr/bin/env bash
# Behavior tests for the example's scripts, run against real containers: two consecutive demo runs,
# alternate ports, startup failure reporting, a telemetry receiver outage and recovery, a Registry
# restart (reused identifiers, retained history) and a non-destructive teardown.
#
# Usage: REGISTRY_IMAGE=<image built from this checkout> ./tests/lifecycle-test.sh [--keep] [case...]
# Prerequisites: bash 3.2 or later, curl, jq, od, a container runtime with Compose v2 (podman preferred, or docker); python3
#   for the cases that hold a port busy; ShellCheck is used when it is installed.
# Cases (default: all, in this order): config-checks local-dir-guard startup-failures alternate-ports
#   sealed-history collector-outage registry-restart teardown series-limit
# Also available, not in the default list: dashboard-scenarios (its phases, bounded waits and deadlines are described in the header of
# tests/dashboard-scenarios.sh, its entry point).
# (the selftest-* cases exist only for tests/unit-test.sh.)
# Every invocation uses a Compose project of its own: usage-otel-test-<random>, or TEST_PROJECT when you name one. It uses
# ports 38081, 39090, 33000 and 33133 (TEST_REGISTRY_PORT, TEST_PROMETHEUS_PORT, TEST_GRAFANA_PORT,
# TEST_COLLECTOR_HEALTH_PORT) and a temporary LOCAL_DIR; two invocations at the same time need different ports.
# Ambient endpoint and configuration overrides (API, REGISTRY_URL, PROMETHEUS_URL, GRAFANA_URL,
# COLLECTOR_HEALTH_URL, OTEL_COLLECTOR_CONFIG, PROMETHEUS_CONFIG, ...) are discarded, so it cannot reach a service it
# did not start. The project is first CLAIMED: a file named after it under TEST_CLAIM_DIR (default
# /tmp/usage-telemetry-otel-claims.<uid>) that only one invocation can publish. A claim another invocation holds, even
# one whose process is gone, is never taken over: the run stops and says which file to remove by hand once you
# know nothing is using it. Then it refuses to start when the project already has containers, volumes or networks, and
# refuses the default project usage-telemetry-otel, so it never removes someone else's stack.
# Every case is independent: it starts the stack if it is not up. At the end, and on any early exit or
# INT/TERM, it first lets the script it was running settle (signalling it, then waiting up to TEST_CHILD_SETTLE_SECONDS,
# default 60, before it kills it; a second INT or TERM while it waits is ignored), then purges ONLY the test project,
# releases its claim (also when a signal interrupts that cleanup),
# and a failed cleanup makes the run fail. With --keep it leaves the stack of the last case running (and releases the
# claim) and says what is running. Logs of every script call go to $TEST_LOG_DIR.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -n "${REGISTRY_IMAGE:-}" ] || {
  echo "error: set REGISTRY_IMAGE to a Registry image built from this checkout (see README.md, 'Build the Registry image'); a published snapshot cannot show this feature" >&2
  exit 2
}

# Nothing from the caller's environment may point the scripts at another stack: endpoints and
# configuration overrides are dropped, and every URL is derived from the test ports below
unset API REGISTRY_URL PROMETHEUS_URL GRAFANA_URL COLLECTOR_HEALTH_URL OTEL_COLLECTOR_CONFIG PROMETHEUS_CONFIG \
  APICURIO_USAGE_METRICS_MAX_SERIES GRAFANA_ADMIN_PASSWORD GRAFANA_DATASOURCE_UID COMPOSE_FILE COMPOSE_PROFILES \
  COMPOSE_PROJECT_NAME LOCAL_DIR HTTP_TIMEOUT CONNECT_TIMEOUT
# One project per invocation unless TEST_PROJECT names one: two default invocations then never share a project
export COMPOSE_PROJECT_NAME=${TEST_PROJECT:-usage-otel-test-$(od -An -N4 -tx1 /dev/urandom | tr -d ' \n')}
export REGISTRY_PORT=${TEST_REGISTRY_PORT:-38081} PROMETHEUS_PORT=${TEST_PROMETHEUS_PORT:-39090}
export GRAFANA_PORT=${TEST_GRAFANA_PORT:-33000} COLLECTOR_HEALTH_PORT=${TEST_COLLECTOR_HEALTH_PORT:-33133}
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/usage-telemetry-otel-test.XXXXXX")
export LOCAL_DIR=$TEST_ROOT/local
TEST_LOG_DIR=${TEST_LOG_DIR:-$TEST_ROOT/logs}
mkdir -p "$TEST_LOG_DIR"

# shellcheck source=lib.sh
. ./lib.sh
validate_project
[ "$PROJECT" != usage-telemetry-otel ] || die "refusing to test with the default project usage-telemetry-otel, which is a stack someone may be using; set TEST_PROJECT"
detect_runtime
compose_cmd
STATE_HOME=$(state_dir)

keep=false
cases=()
for arg in "$@"; do
  case "$arg" in
    --keep) keep=true ;;
    *) cases+=("$arg") ;;
  esac
done
[ ${#cases[@]} -gt 0 ] || cases=(config-checks local-dir-guard startup-failures alternate-ports sealed-history collector-outage registry-restart teardown series-limit)

listener_pid=""
KEPT_NOTE=""
# start_listener <port>: a throwaway listener that holds a port busy (stopped by stop_listener or by the exit trap)
start_listener() {
  command -v python3 > /dev/null 2>&1 || die "python3 is needed to hold a port busy in this case"
  python3 -I -c 'import socket, sys, time
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", int(sys.argv[1])))
s.listen()
time.sleep(120)' "$1" &
  listener_pid=$!
  [ -z "${SELFTEST_PID_FILE:-}" ] || printf '%s\n' "$listener_pid" > "$SELFTEST_PID_FILE"
  sleep 1
}
stop_listener() {
  [ -z "$listener_pid" ] || { kill "$listener_pid" 2> /dev/null || true; wait "$listener_pid" 2> /dev/null || true; }
  listener_pid=""
}

# --- ownership of the test project -------------------------------------------------------------------
#
# Cleanup removes everything the Compose project holds, so the project must be this invocation's alone. An empty
# inventory at the start is not enough: another invocation may be about to start. The project is therefore claimed first.
# The claim is a file named after the project in TEST_CLAIM_DIR (default /tmp/usage-telemetry-otel-claims.<uid>, not under
# TMPDIR, which differs between invocations), written complete (this run's token and details) under a temporary name and
# published with `ln`, which fails if the name exists: it either takes the claim whole or takes nothing, so there is no
# moment at which a claim exists without its owner's token. It is test state, not host configuration. The path and token
# are known before the claim is published, so an interruption at any point either finds nothing of ours to release or finds
# a claim it can recognise and remove. release_claim removes it only while it still carries this run's token. A claim
# left by anything else is never taken over: it may belong to a live run. A SIGKILL can still leave one behind.
CLAIM_BASE=${TEST_CLAIM_DIR:-/tmp/usage-telemetry-otel-claims.$(id -u)}
CLAIM_FILE=""
CLAIM_TMP=""
CLAIM_TOKEN=""
PURGE_OWNED=false
FINISH_STARTED=false

# claim_project: both names are fixed from this run's token before anything is created, so an interruption at any point
# leaves release_claim knowing exactly what is this run's: the temporary file (its name carries the token) and the
# published claim (recognised by the token inside it). Nothing else in the claim directory is ever touched.
claim_project() {
  local file=$CLAIM_BASE/$PROJECT holder
  if [ -L "$CLAIM_BASE" ]; then die "$CLAIM_BASE is a symbolic link; refusing to use it for claims"; fi
  [ -d "$CLAIM_BASE" ] || mkdir -m 700 "$CLAIM_BASE" 2> /dev/null || [ -d "$CLAIM_BASE" ] || die "could not create the claim directory $CLAIM_BASE (its parent must exist)"
  [ -d "$CLAIM_BASE" ] && [ -O "$CLAIM_BASE" ] || die "$CLAIM_BASE is not a directory of yours; refusing to use it for claims"
  CLAIM_TOKEN=$$-$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n')
  CLAIM_FILE=$file
  CLAIM_TMP=$CLAIM_BASE/.claim.$CLAIM_TOKEN
  : > "$CLAIM_TMP" || die "could not write a claim in $CLAIM_BASE"
  printf '%s\npid %s, started %s\n' "$CLAIM_TOKEN" "$$" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$CLAIM_TMP"
  if [ -e "$file" ] || [ -L "$file" ] || ! ln "$CLAIM_TMP" "$file" 2> /dev/null; then
    # Only an ordinary file is read for the holder's details: a FIFO, a device or a link would be followed or block
    if [ -f "$file" ] && [ ! -L "$file" ]; then
      holder=$(sed -n 2p "$file" 2> /dev/null || true)
    elif [ -e "$file" ] || [ -L "$file" ]; then
      holder="a claim path that is not an ordinary file, left untouched"
    else
      holder="no claim could be published there"
    fi
    CLAIM_FILE=""
    rm -f "$CLAIM_TMP"
    CLAIM_TMP=""
    die "project $PROJECT is claimed by another run of this test (${holder:-an owner that wrote no details}; claim $file). Nothing was started and nothing was removed. Choose another TEST_PROJECT, or wait for that run to end; if you know that run is gone, delete $file yourself"
  fi
  rm -f "$CLAIM_TMP"
  CLAIM_TMP=""
}

# release_claim: removes what this invocation created: its temporary file, and its published claim while that still
# carries this invocation's token; never anyone else's
release_claim() {
  [ -z "$CLAIM_TMP" ] || { rm -f "$CLAIM_TMP"; CLAIM_TMP=""; }
  [ -n "$CLAIM_FILE" ] || return 0
  if [ -f "$CLAIM_FILE" ] && [ ! -L "$CLAIM_FILE" ] && [ "$(sed -n 1p "$CLAIM_FILE" 2> /dev/null)" = "$CLAIM_TOKEN" ]; then
    rm -f "$CLAIM_FILE"
  elif [ -e "$CLAIM_FILE" ] || [ -L "$CLAIM_FILE" ]; then
    printf 'warning: the claim %s is no longer this run'"'"'s; it was left alone\n' "$CLAIM_FILE" >&2
  fi
  CLAIM_FILE=""
}
# While finish is cleaning up, a signal that ends a supervised command there must still release the claim (run_bounded calls
# this just before it exits). Outside finish it does nothing: the claim has to outlive the cleanup that is still to come.
claim_cancel_hook() { if $FINISH_STARTED; then release_claim; fi; }
RB_EXIT_HOOK=claim_cancel_hook

# --- running scripts, and ending the run --------------------------------------------------------------

# finish: runs on every exit, including set -e failures and INT/TERM. It stops the listener, purges only
# this test's project unless --keep (and only when this invocation owns it), releases its claim, and reports. A cleanup that
# fails fails the run; a run that already failed keeps its own status. A signal during it releases the claim and ends it.
# interrupt (a signal at any other time) runs finish itself, so a signal never depends on an exit trap that may be running.
finish() { # finish <exit status>
  local rc=$1
  # Entered from the exit trap or, for a signal, from interrupt, whichever comes first; once under way a second entry
  # (the exit trap after interrupt's own exit) only makes sure the claim is released
  if $FINISH_STARTED; then release_claim; return 0; fi
  FINISH_STARTED=true
  # From here every INT or TERM is handled by interrupt, which releases the claim; before the two lines below the handlers
  # are the earlier interrupt (also safe: FINISH_STARTED is already true) or the ignore it sets for itself
  trap 'interrupt INT 130' INT
  trap 'interrupt TERM 143' TERM
  stop_listener
  if $keep; then
    if $PURGE_OWNED; then
      # Both inventories are read first and their statuses checked; a failed or hung one makes the state unknown and
      # the run fail, whatever else passed
      local cn="" vn="" unknown=""
      if count_project_containers; then cn=$Q_OUT; else unknown="the container query failed or timed out (status $?)"; fi
      if project_volume_count; then vn=$Q_OUT; else unknown="${unknown:+$unknown; }the volume query failed or timed out (status $?)"; fi
      if [ -n "$unknown" ]; then
        info "
--keep: nothing was cleaned up, but what is left of project $COMPOSE_PROJECT_NAME is UNKNOWN: $unknown.${KEPT_NOTE:+ $KEPT_NOTE} Stop it with: COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME LOCAL_DIR=$LOCAL_DIR ./teardown.sh --purge"
        [ "$rc" -ne 0 ] || rc=1
      else
        info "
--keep: nothing was cleaned up. Project $COMPOSE_PROJECT_NAME now has $cn container(s) and $vn volume(s).${KEPT_NOTE:+ $KEPT_NOTE} Stop it with: COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME LOCAL_DIR=$LOCAL_DIR ./teardown.sh --purge"
      fi
    fi
  elif $PURGE_OWNED && ! run_bounded 600 ./teardown.sh --purge > "$TEST_LOG_DIR/final-teardown.log" 2>&1; then
    printf 'error: cleanup of test project %s FAILED (see %s); its containers or volumes may remain\n' "$COMPOSE_PROJECT_NAME" "$TEST_LOG_DIR/final-teardown.log" >&2
    [ "$rc" -ne 0 ] || rc=1
  fi
  release_claim
  printf '\nLogs of every script call: %s\n' "$TEST_LOG_DIR"
  if [ "$failures" -gt 0 ]; then
    printf '%s check(s) failed.\n' "$failures"
    [ "$rc" -ne 0 ] || rc=1
  fi
  if [ "$rc" -eq 0 ]; then printf 'All checks passed.\n'; else printf 'The run FAILED (exit status %s).\n' "$rc"; fi
  exit "$rc"
}

# The script a case is running (see run_child). INT and TERM go to that script first, the run waits for it to settle (it
# ends its own provider processes), and only then does finish clean the project up: cleanup never races a script that is
# still starting or stopping the same containers.
CHILD_PID=""
CHILD_BEFORE=""
CHILD_LAUNCHING=false
CHILD_SETTLE_SECONDS=${TEST_CHILD_SETTLE_SECONDS:-60}
# The signal goes to the script's own process group (run_child made it a group leader), the way a terminal sends INT to a
# foreground job: a script without an INT trap, waiting on a foreground command, ignores INT sent to its shell alone. A
# group is only signalled when its id is the script's pid; otherwise the script's pid is signalled alone. Never the group
# of this shell or of its caller.
interrupt() { # interrupt <signal name> <exit status>
  local sig=$1 code=$2 i=0 target
  trap '' INT TERM
  if $CHILD_LAUNCHING && [ -z "$CHILD_PID" ]; then
    # the signal was handled between the launch and the assignment of CHILD_PID
    _last_background_pid
    if [ -n "$_LBP" ] && [ "$_LBP" != "$CHILD_BEFORE" ]; then CHILD_PID=$_LBP; fi
  fi
  if [ -n "$CHILD_PID" ] && kill -0 "$CHILD_PID" 2> /dev/null; then
    target=$CHILD_PID
    [ "$(ps -o pgid= -p "$CHILD_PID" 2> /dev/null | tr -d ' ')" != "$CHILD_PID" ] || target=-$CHILD_PID
    kill "-$sig" -- "$target" 2> /dev/null || true
    while kill -0 "$CHILD_PID" 2> /dev/null && [ "$i" -lt $((CHILD_SETTLE_SECONDS * 10)) ]; do
      sleep 0.1
      i=$((i + 1))
    done
    if kill -0 "$CHILD_PID" 2> /dev/null; then
      printf 'warning: the script (pid %s) did not end within %ss of %s; killing it. Processes it started in groups of their own may remain\n' "$CHILD_PID" "$CHILD_SETTLE_SECONDS" "$sig" >&2
      kill -KILL -- "$target" 2> /dev/null || true
    fi
    wait "$CHILD_PID" 2> /dev/null || true
  fi
  CHILD_PID=""
  if $FINISH_STARTED; then
    # the cleanup was already under way and is now cut short: nothing else will release the claim
    release_claim
    exit "$code"
  fi
  finish "$code"
}
trap 'finish $?' EXIT
trap 'interrupt INT 130' INT
trap 'interrupt TERM 143' TERM

claim_project
# The project must be empty before the first case: cleanup removes everything of the project, so a project that
# already has resources (a network alone counts) is not ours to remove
count_project_containers || die "could not inspect project $PROJECT (the runtime query failed or timed out); not starting"
existing_containers=$Q_OUT
project_volumes || die "could not list the volumes of project $PROJECT; not starting"
existing_volumes=$Q_OUT
project_networks || die "could not list the networks of project $PROJECT; not starting"
existing_networks=$Q_OUT
if [ "$existing_containers" != 0 ] || [ -n "$existing_volumes" ] || [ -n "$existing_networks" ]; then
  die "project $PROJECT already has containers, volumes or networks. Remove them first with: COMPOSE_PROJECT_NAME=$PROJECT ./teardown.sh --purge (or choose another TEST_PROJECT). Nothing was removed"
fi
PURGE_OWNED=true

step=0
RC=0
OUT=""
# run_child <command...>: an executable (a script, env, shellcheck) as a job of its own, with its output in $OUT and its
# status in RC. Job control gives it its own process group, so a terminal's INT reaches this shell only and is passed on
# by interrupt() to the one script; it also keeps INT from being ignored, as it is for a plain background job. The wait
# is interruptible, so a signal sent to this shell is acted on at once rather than when the script ends.
run_child() {
  local had_m=false
  case $- in *m*) had_m=true ;; esac
  set -m
  _last_background_pid
  CHILD_BEFORE=$_LBP
  CHILD_LAUNCHING=true
  "$@" > "$OUT" 2>&1 < /dev/null &
  CHILD_PID=$!
  CHILD_LAUNCHING=false
  $had_m || set +m
  [ -z "${SELFTEST_CHILD_PID_FILE:-}" ] || printf '%s\n' "$CHILD_PID" >> "$SELFTEST_CHILD_PID_FILE"
  wait "$CHILD_PID" || RC=$?
  CHILD_PID=""
}
# run <name> <command...>: runs a command, keeps its whole output in $TEST_LOG_DIR/NN-name.log (RC, OUT). A function
# (run_bounded, compose, ...) runs in this shell, which supervises it; an executable runs through run_child.
run() {
  local name=$1
  shift
  step=$((step + 1))
  OUT=$(printf '%s/%02d-%s.log' "$TEST_LOG_DIR" "$step" "$name")
  RC=0
  if [ "$(type -t "$1")" = file ]; then
    run_child "$@"
  else
    "$@" > "$OUT" 2>&1 || RC=$?
  fi
  printf '%s\nexit=%s\n' "# $*" "$RC" >> "$OUT"
}
assert_rc() { # assert_rc <description> <expected exit status>
  check "$1 (exit status $2)" "$2" "$RC"
}
assert_nonzero() {
  if [ "$RC" -ne 0 ]; then ok "$1 (exit status $RC)"; else fail "$1: expected a non-zero exit status, got 0 (log $OUT)"; fi
}
assert_in_output() { # assert_in_output <description> <fixed text>
  if grep -q -F -- "$2" "$OUT"; then ok "$1"; else fail "$1: '$2' not found in $OUT"; fi
}
# failures comes from lib.sh
# shellcheck disable=SC2154
dump_if_failed() { [ "$failures" -eq 0 ] || { echo "--- tail of $OUT"; tail -30 "$OUT"; }; }

# abort_run <message>: the scenario cannot go on; leave with status 1 (the exit trap does the owned cleanup)
abort_run() {
  printf 'error: %s\n' "$1" >&2
  exit 1
}
# must <name> <description> <command...>: a prerequisite that changes something (start, stop, restart, purge, a demo).
# It is run and its status asserted; when it fails the whole run is abandoned before anything more is sent to any
# endpoint. Steps that are EXPECTED to fail (a refused port, a broken configuration) call `run` directly instead.
must() {
  local name=$1 desc=$2
  shift 2
  run "$name" "$@"
  assert_rc "$desc" 0
  if [ "$RC" -ne 0 ]; then
    dump_if_failed
    abort_run "$desc failed (status $RC, see above); the scenario is abandoned before anything more is sent"
  fi
}
restart_registry() {
  must restart-registry "the Registry container is restarted" run_bounded 180 "${COMPOSE_CMD[@]}" restart registry
  retry_until 120 http_get "$API/system/info" > /dev/null 2>&1 || abort_run "the Registry did not come back within 120s; the scenario is abandoned"
  ok "the Registry answers again"
}
# stack_up [ENV=value ...]: setup.sh, with optional environment, as a prerequisite
stack_up() { must setup "setup.sh starts the stack" env ${1+"$@"} ./setup.sh; }
demo() { must demo "demo.sh passes" ./demo.sh; }
# check_query <description> <expected> <query function> [args]: runs the query in this shell first. A query that fails
# or times out is a failed check and is never compared with anything; its answer is Q_OUT.
check_query() {
  local desc=$1 want=$2 rc=0
  shift 2
  Q_ERR=""
  "$@" || rc=$?
  if [ "$rc" -eq 0 ]; then
    check "$desc" "$want" "$Q_OUT"
  elif [ -n "$Q_ERR" ]; then
    fail "$desc: $Q_ERR, so the value is unknown"
  elif [ "$rc" -eq 124 ]; then
    fail "$desc: the query timed out (status 124), so the value is unknown"
  else
    fail "$desc: the query failed (status $rc), so the value is unknown"
  fi
}
# project_resources: Q_OUT is "<containers> <volumes>" of the test project
project_resources() {
  local c
  count_project_containers || return $?
  c=$Q_OUT
  project_volume_count || return $?
  Q_OUT="$c $Q_OUT"
}
# ensure_empty_project <case>: a case that assumes an empty project (an earlier case may have left the stack running)
# empties THIS invocation's project first, as an ordinary prerequisite; on a fresh project there is nothing to remove
ensure_empty_project() {
  project_resources || abort_run "could not read what the test project holds (status $?); not continuing"
  if [ "$Q_OUT" != "0 0" ]; then
    must "purge-before-$1" "the test project is emptied before the $1 case" ./teardown.sh --purge
  fi
}
# assert_others_unchanged <description> <inventory taken before>: the foreign containers are what they were.
# Only two valid inventories are compared; a failed or timed-out query is a failure of its own.
assert_others_unchanged() {
  local rc=0
  other_containers || rc=$?
  if [ "$rc" -eq 124 ]; then
    fail "$1: the inventory query timed out (status 124), so the foreign containers are unknown"
  elif [ "$rc" -ne 0 ]; then
    fail "$1: the inventory query failed (status $rc), so the foreign containers are unknown"
  else
    check "$1" "$2" "$Q_OUT"
  fi
}
contains_word() { # contains_word <space separated words> <word>: yes or no
  case " $1 " in
    *" $2 "*) echo yes ;;
    *) echo no ;;
  esac
}
state_value() { jq -r "$1" "$STATE_HOME/run-state.json"; }

# outage_recovered <client> <instance> <count>: recovery is complete only when the outage client's count is exactly
# <count> AND the same Registry process is exporting fresh data now, with the age limit verify.sh applies. The first
# batch the Registry retries after the outage can carry an old measurement time, so the count alone can be right while
# the newest export is still more than a minute old and verify.sh would fail. RECOVERY_REASON says what is missing.
RECOVERY_TIMEOUT=${RECOVERY_TIMEOUT:-90}
RECOVERY_REASON=""
# shellcheck disable=SC2329 # called through retry_until
outage_recovered() {
  local client=$1 instance=$2 want=$3 out got beat age
  RECOVERY_REASON=""
  out=$(prom_query "$USAGE_METRIC{apicurio_registry_client_id=\"$client\",$SOURCE_LABEL=\"$instance\"}") \
    || { RECOVERY_REASON="Prometheus did not answer the query for the outage client's count, so it is unknown"; return 1; }
  got=$(jq -r '.data.result[0].value[1] // "none"' <<< "$out")
  if [ "$got" != "$want" ]; then
    RECOVERY_REASON="the outage client's count is '$got', expected $want"
    return 1
  fi
  beat=$(heartbeat_epoch "$instance") \
    || { RECOVERY_REASON="the count is $want, but no heartbeat of process $instance could be read from Prometheus, so fresh exports are unknown"; return 1; }
  age=$(($(now_epoch) - beat))
  if [ "$age" -gt "$MAX_EXPORT_AGE_SECONDS" ]; then
    RECOVERY_REASON="the count is $want, but the newest export of process $instance is ${age}s old (limit ${MAX_EXPORT_AGE_SECONDS}s): the retried batch carried an old measurement time and no fresh export followed it"
    return 1
  fi
}
# wait_outage_recovery <client> <instance> <count>: waits up to RECOVERY_TIMEOUT seconds; the status is the last attempt's
wait_outage_recovery() { retry_until "$RECOVERY_TIMEOUT" outage_recovered "$@"; }

# --- cases -------------------------------------------------------------------------------------------

case_config_checks() {
  part "config-checks: Compose and the scripts' own validation"
  run bash-syntax syntax_check_each lib.sh setup.sh demo.sh verify.sh teardown.sh tests/lifecycle-test.sh tests/unit-test.sh tests/fixtures/fake-runtime.sh
  assert_rc "every script parses with bash -n (one invocation per file)" 0
  if command -v shellcheck > /dev/null 2>&1; then
    run shellcheck shellcheck -x lib.sh setup.sh demo.sh verify.sh teardown.sh tests/lifecycle-test.sh tests/unit-test.sh tests/fixtures/fake-runtime.sh
    assert_rc "ShellCheck finds nothing" 0
  else
    info "  skip  ShellCheck is not installed"
  fi
  run compose-config compose config -q
  assert_rc "docker-compose.yml validates with the defaults" 0
  if REGISTRY_IMAGE=apicurio/apicurio-registry:abc123 composeq config --images 2> /dev/null; then
    check "CI's image check sees exactly one Apicurio image, the one REGISTRY_IMAGE names" "apicurio/apicurio-registry:abc123" \
      "$(printf '%s\n' "$BCAP_OUT" | sort -u | grep -E '(^|/)apicurio-registry[a-z-]*:' | tr '\n' ' ' | sed 's/ $//')"
  else
    fail "docker-compose.yml: the image listing failed (status $?), so CI's image check is unknown"
  fi
  if composeq config --format json; then
    published=$(printf '%s' "$BCAP_OUT" | jq -r '[.services[].ports[]? | "\(.host_ip):\(.published)"] | join(" ")')
    case "$published" in
      *0.0.0.0* | *"::"*) fail "a host port is not bound to loopback: $published" ;;
      *) ok "every published host port is bound to 127.0.0.1: $published" ;;
    esac
    check "the Collector's OTLP port is not published" 0 \
      "$(printf '%s' "$BCAP_OUT" | jq '[.services["otel-collector"].ports[]? | select(.target == 4318 or .target == 4317)] | length')"
  else
    fail "docker-compose.yml: the configuration listing failed (status $?), so the published ports are unknown"
  fi
  run bad-port ./setup.sh --registry-port abc
  assert_nonzero "setup.sh rejects a port that is not a number"
  assert_in_output "  and says which setting is wrong" "REGISTRY_PORT must be a port number"
  run bad-project ./setup.sh --project 'Not Valid'
  assert_nonzero "setup.sh rejects an invalid project name"
  run bad-runtime env CONTAINER_RUNTIME=nonexistent ./setup.sh
  assert_nonzero "setup.sh rejects an unknown CONTAINER_RUNTIME"
  assert_in_output "  and names the choices" "must be podman or docker"
  run bad-teardown ./teardown.sh --bogus
  assert_nonzero "teardown.sh rejects an unknown option"
}

case_local_dir_guard() {
  part "local-dir-guard: scripts never write into a directory they did not create"
  local foreign=$TEST_ROOT/foreign
  mkdir -p "$foreign"
  printf 'precious\n' > "$foreign/user-file.txt"
  run demo-foreign env LOCAL_DIR="$foreign" ./demo.sh
  assert_nonzero "demo.sh refuses a LOCAL_DIR that is not its own"
  assert_in_output "  and says why" "refusing to write there"
  check "the user's file is untouched and nothing was added" "user-file.txt" "$(find "$foreign" -mindepth 1 -maxdepth 1 -exec basename {} \; | tr '\n' ' ' | sed 's/ $//')"
}

case_startup_failures() {
  part "startup-failures: the setup reports why and starts nothing it cannot finish"
  ensure_empty_project startup-failures
  local busy_port=$((REGISTRY_PORT + 100))
  start_listener "$busy_port"
  run busy-port env REGISTRY_PORT="$busy_port" ./setup.sh
  stop_listener
  assert_nonzero "a busy host port stops setup.sh"
  assert_in_output "  naming the setting and the port" "REGISTRY_PORT=$busy_port"
  check_query "  before any container was created" 0 count_project_containers

  printf 'receivers:\n  otlp: {}\nservice:\n  pipelines:\n    metrics:\n      receivers: [otlp]\n      exporters: [does-not-exist]\n' > "$TEST_ROOT/broken-collector.yaml"
  run broken-collector env OTEL_COLLECTOR_CONFIG="$TEST_ROOT/broken-collector.yaml" ./setup.sh
  assert_nonzero "a Collector configuration the Collector rejects stops setup.sh"
  assert_in_output "  naming the file" "broken-collector.yaml"
  check_query "  before any container was created" 0 count_project_containers

  run missing-config env OTEL_COLLECTOR_CONFIG="$TEST_ROOT/no-such-file.yaml" ./setup.sh
  assert_nonzero "a missing configuration file stops setup.sh (a bind mount would have created a directory)"
  check "  and created nothing at that path" no "$([ -e "$TEST_ROOT/no-such-file.yaml" ] && echo yes || echo no)"

  run missing-image env REGISTRY_IMAGE=localhost/apicurio/apicurio-registry:no-such-tag ./setup.sh
  assert_nonzero "a Registry image that does not exist stops setup.sh"
  assert_in_output "  and tells how to build one" "build it from this checkout"
  check "  and kept the diagnostics" yes "$(ls -d "$STATE_HOME"/logs/setup-* > /dev/null 2>&1 && echo yes || echo no)"
  must teardown-after-failure "teardown.sh cleans up after a failed start" ./teardown.sh

  # An image that starts and stops at once: setup.sh must notice, not wait out the whole timeout
  rt image inspect docker.io/library/alpine:3 > /dev/null 2>&1 || run_bounded 300 "$RUNTIME" pull docker.io/library/alpine:3 > /dev/null 2>&1 || true
  local began=$SECONDS
  run exited-registry env REGISTRY_IMAGE=docker.io/library/alpine:3 REGISTRY_READY_TIMEOUT=120 ./setup.sh
  assert_nonzero "a Registry container that exits makes setup.sh fail"
  assert_in_output "  naming the container that stopped" "the registry container stopped"
  if [ $((SECONDS - began)) -lt 100 ]; then ok "  in $((SECONDS - began))s, long before the 120s readiness limit"; else fail "  it waited $((SECONDS - began))s"; fi
  check "  and saved the container logs" yes "$(ls "$STATE_HOME"/logs/setup-*/registry.log > /dev/null 2>&1 && echo yes || echo no)"
  must teardown-after-exit "teardown.sh --purge cleans up" ./teardown.sh --purge
  check_query "nothing of the test project remains" "0 0" project_resources
}

case_alternate_ports() {
  part "alternate-ports: the whole flow on ports other than the defaults, and two demo runs in a row"
  stack_up
  if service_container registry && [ -n "$Q_OUT" ] && rtq port "$Q_OUT" 2> /dev/null; then
    published=$(printf '%s' "$BCAP_OUT" | tr '\n' ' ')
    case "$published" in
      *"127.0.0.1:$REGISTRY_PORT"*) ok "the Registry is published on 127.0.0.1:$REGISTRY_PORT" ;;
      *) fail "the Registry is not published on 127.0.0.1:$REGISTRY_PORT ($published)" ;;
    esac
  else
    fail "the Registry's published ports are unknown: the container lookup or the port query failed"
  fi
  check_query "the Collector's OTLP port 4318 is not published" 0 published_port_count otel-collector 4318
  check "Prometheus answers on the alternate port" 200 "$(http_status "$PROMETHEUS_URL/-/ready")"
  check "Grafana answers on the alternate port" 200 "$(http_status "$GRAFANA_URL/api/health")"
  must second-setup "running setup.sh again is safe" ./setup.sh
  demo
  first_run=$(state_value .runId)
  first_instance=$(state_value .source.instanceId)
  first_run_file=$STATE_HOME/runs/$first_run.json
  demo
  check "the second run is a different run" yes "$([ "$(state_value .runId)" != "$first_run" ] && echo yes || echo no)"
  check "  on the same Registry process" "$first_instance" "$(state_value .source.instanceId)"
  check "  with new global IDs (no run reuses another's series)" yes \
    "$([ "$(jq '.schemas[0].globalId' "$first_run_file")" != "$(state_value '.schemas[0].globalId')" ] && echo yes || echo no)"
  run verify ./verify.sh
  assert_rc "verify.sh passes on the latest run" 0
  run verify-first ./verify.sh --state "$first_run_file"
  assert_rc "verify.sh still passes on the first run (its counters were not disturbed)" 0
  dump_if_failed
}

case_selftest_early_exit() {
  part "selftest-early-exit: starts a listener and exits; the exit trap must stop it (used by tests/unit-test.sh)"
  start_listener $((REGISTRY_PORT + 100))
  exit 3
}

case_selftest_hold() {
  part "selftest-hold: starts a listener and waits, to be interrupted (used by tests/unit-test.sh)"
  start_listener $((REGISTRY_PORT + 100))
  retry_until 100 false || true
}

case_selftest_demo_abort() {
  part "selftest-demo-abort: a demo that fails must stop the scenario (used by tests/unit-test.sh)"
  demo
  touch "${SELFTEST_MARKER:?}"
}

case_selftest_inventory() {
  part "selftest-inventory: the foreign-container assertion on an empty inventory (used by tests/unit-test.sh)"
  local before
  other_containers || abort_run "the inventory before failed (status $?)"
  before=$Q_OUT
  [ "${SELFTEST_INVENTORY_AFTER:-none}" = none ] || printf '%s' "$SELFTEST_INVENTORY_AFTER" > "${SELFTEST_FLAG:?}"
  assert_others_unchanged "no other container was started, stopped or removed" "$before"
}

case_selftest_keep_queries() {
  part "selftest-keep-queries: makes one inventory query fail before the --keep summary is printed (used by tests/unit-test.sh)"
  local f=${FAKE_FLAG_DIR:?}
  case "${SELFTEST_FAIL:-none}" in
    containers) printf 6 > "$f/compose-ps" ;;
    volumes) printf 5 > "$f/volume" ;;
    both) printf 6 > "$f/compose-ps"; printf 5 > "$f/volume" ;;
    volumes-hang) printf stall > "$f/volume" ;;
  esac
}

case_selftest_ports() {
  part "selftest-ports: the published-port assertion of the alternate-ports case, against a fake runtime (used by tests/unit-test.sh)"
  local f=${FAKE_FLAG_DIR:?}
  case "${SELFTEST_PORTS:-empty}" in
    lookup-fails) printf 6 > "$f/compose-ps" ;;
    no-container) ;;
    port-hangs) printf 'cccccccccccc1111\n' > "$f/compose-ps-out"; printf stall > "$f/port" ;;
    *) printf 'cccccccccccc1111\n' > "$f/compose-ps-out" ;;
  esac
  check_query "the Collector's OTLP port 4318 is not published" 0 published_port_count otel-collector 4318
  # leave the fake runtime as found, so the owned cleanup at exit sees an empty project
  rm -f "$f/compose-ps-out" "$f/compose-ps" "$f/port"
}

case_selftest_recovery() {
  part "selftest-recovery: the outage recovery wait, against a controlled Prometheus (used by tests/unit-test.sh)"
  local began=$SECONDS
  if wait_outage_recovery selftest-client "${SELFTEST_INSTANCE:-x}" 5; then
    ok "recovered after $((SECONDS - began))s"
  else
    fail "not recovered after $((SECONDS - began))s: $RECOVERY_REASON"
  fi
}

# selftest-child: runs the command in SELFTEST_CHILD (words separated by spaces) as one script call, to be interrupted
case_selftest_child() {
  part "selftest-child: runs one script call that the test interrupts (used by tests/unit-test.sh)"
  local words=()
  read -r -a words <<< "${SELFTEST_CHILD:?}"
  run selftest-child "${words[@]}"
  info "  the script ended with status $RC"
}

case_selftest_reset() {
  part "selftest-reset: ensure_empty_project against the fake runtime (used by tests/unit-test.sh)"
  [ "${SELFTEST_LEFTOVER:-no}" != yes ] || printf 'cccccccccccc1111\n' > "${FAKE_FLAG_DIR:?}/compose-ps-out"
  ensure_empty_project selftest-reset
}

case_selftest_endpoints() {
  part "selftest-endpoints: demo.sh must talk only to the test ports (used by tests/unit-test.sh)"
  run demo-endpoints ./demo.sh
  info "  demo.sh exited with status $RC against whatever answers on the test ports"
}

case_sealed_history() {
  part "sealed-history: a later fetch of the same IDs does not invalidate a sealed run; a wrong sealed count still does"
  stack_up
  demo
  local gid instance
  gid=$(state_value '.schemas[0].globalId')
  instance=$(state_value .source.instanceId)
  # Straight after the demo returns, well inside the 5 second margin an earlier design used
  check "a later fetch of the run's first schema answers 200" 200 \
    "$(http_status "$API/ids/globalIds/$gid" -H 'X-Registry-Client-Id: later-reader' -H 'X-Registry-Operation: SERIALIZE')"
  # shellcheck disable=SC2329 # called through retry_until
  later_visible() {
    prom_query "$USAGE_METRIC{apicurio_registry_client_id=\"later-reader\",$SOURCE_LABEL=\"$instance\",apicurio_registry_identifier_value=\"$gid\"}" \
      | jq -e '.data.result | length > 0' > /dev/null
  }
  if retry_until 60 later_visible; then ok "the later fetch is now visible in Prometheus under the same process"; else fail "the later fetch did not reach Prometheus within 60s"; fi
  run verify-sealed ./verify.sh
  assert_rc "verify.sh still passes: the sealed snapshot does not contain the later fetch" 0
  jq '.expectedSeries[0].count += 1' "$STATE_HOME/run-state.json" > "$TEST_ROOT/wrong-count.json"
  run verify-wrong-count ./verify.sh --state "$TEST_ROOT/wrong-count.json"
  assert_nonzero "a wrong count in the sealed window fails verification"
  assert_in_output "  and reports the difference" "does not hold the expected series"
  jq --arg t "$(date +%s)" '.evaluationTime = $t | .evaluationTimeSeconds = ($t | tonumber)' "$STATE_HOME/run-state.json" > "$TEST_ROOT/moved-time.json"
  run verify-moved-time ./verify.sh --state "$TEST_ROOT/moved-time.json"
  assert_nonzero "the same run evaluated after the later fetch fails, which is what sealing prevents"
  assert_in_output "  because of the later reader's series" "later-reader"
}

case_collector_outage() {
  part "collector-outage: fetches keep working while the receiver is down, and the counts arrive after it returns"
  stack_up
  demo
  local gid instance client i=0 code seconds slow=0 bad=0 count=5
  instance=$(state_value .source.instanceId)
  client=outage-$(date +%s)-$RANDOM
  # The outage fetches use a schema of their own, so they cannot add series to the demo run's identifiers
  local schema body
  schema=$(jq -nc --arg ns "test.$RANDOM$RANDOM" '{type: "record", name: "Outage", namespace: $ns, fields: []}')
  body=$(jq -nc --arg a "$client" --arg c "$schema" \
    '{artifactId: $a, artifactType: "AVRO", firstVersion: {version: "1", content: {content: $c, contentType: "application/json"}}}')
  gid=$(api_post "/groups/usage-otel-test/artifacts" "$body" | jq -r .version.globalId) || gid=""
  check "the outage schema was registered" yes "$([[ "$gid" =~ ^[0-9]+$ ]] && echo yes || echo no)"
  [[ "$gid" =~ ^[0-9]+$ ]] || abort_run "the outage schema was not registered; the scenario is abandoned"
  must stop-collector "the Collector container is stopped" run_bounded 120 "${COMPOSE_CMD[@]}" stop otel-collector
  # Prometheus can no longer scrape the Collector: the outage must show as an unknown state
  # shellcheck disable=SC2329 # called through retry_until
  down() { [ "$(prom_query 'up{job="otel-collector-usage"}' | jq -r '.data.result[0].value[1] // "x"')" = 0 ]; }
  if retry_until 60 down; then ok "Prometheus reports the Collector target down"; else fail "Prometheus still reports the Collector target as up after 60s"; fi
  run verify-during-outage env VERIFY_RETRY_SECONDS=0 ./verify.sh --stack-only
  assert_nonzero "verify.sh reports the outage instead of passing"
  while [ "$i" -lt "$count" ]; do
    i=$((i + 1))
    seconds=$(curl -s --max-time 10 -o /dev/null -w '%{http_code} %{time_total}' "$API/ids/globalIds/$gid" \
      -H "X-Registry-Client-Id: $client" -H 'X-Registry-Operation: SERIALIZE' || echo "000 99")
    code=${seconds%% *}
    [ "$code" = 200 ] || bad=$((bad + 1))
    awk -v t="${seconds#* }" 'BEGIN { exit !(t < 3) }' || slow=$((slow + 1))
  done
  check "$count fetches during the outage all answered 200" 0 "$bad"
  check "  each within 3 seconds (this shows the request path is not blocked; it does not show zero overhead or lossless export)" 0 "$slow"
  must start-collector "the Collector container is started again" run_bounded 120 "${COMPOSE_CMD[@]}" start otel-collector
  if retry_until 60 http_get "$COLLECTOR_HEALTH_URL/" > /dev/null 2>&1; then ok "the Collector is healthy again"; else fail "the Collector did not come back within 60s"; fi
  if wait_outage_recovery "$client" "$instance" "$count"; then
    ok "after recovery Prometheus shows all $count outage fetches for process $instance, and that process is exporting fresh data (counters are cumulative in the Registry, so a later export carries them)"
  else
    fail "after recovery: $RECOVERY_REASON"
  fi
  run verify-after-outage ./verify.sh
  assert_rc "verify.sh passes again, on the last demo run too" 0
  dump_if_failed
}

case_registry_restart() {
  part "registry-restart: a new process is a new source, reused IDs do not collide, history is kept"
  stack_up
  # Start from a process with empty storage, so the first run's IDs are the ones a restart hands out again
  restart_registry
  demo
  local old_instance old_gid before_run_file
  before_run_file=$STATE_HOME/runs/$(state_value .runId).json
  old_instance=$(jq -r .source.instanceId "$before_run_file")
  old_gid=$(jq -r '.schemas[0].globalId' "$before_run_file")
  restart_registry
  demo
  new_instance=$(state_value .source.instanceId)
  new_gid=$(state_value '.schemas[0].globalId')
  check "the restarted Registry is a different source" yes "$([ "$new_instance" != "$old_instance" ] && echo yes || echo no)"
  check "its in-memory storage started again, so the first global ID is reused (before $old_gid, after $new_gid)" "$old_gid" "$new_gid"
  same_series=$(prom_query "count by ($SOURCE_LABEL) (last_over_time($USAGE_METRIC{apicurio_registry_client_id=\"orders-service\",apicurio_registry_identifier_type=\"global_id\",apicurio_registry_identifier_value=\"$old_gid\",apicurio_registry_operation=\"SERIALIZE\"}[1h]))" \
    | jq -r '[.data.result[].metric.service_instance_id] | join(" ")')
  check "Prometheus holds the same client, type, ID and operation under the process before the restart" yes "$(contains_word "$same_series" "$old_instance")"
  check "  and under the process after it, as separate series" yes "$(contains_word "$same_series" "$new_instance")"
  run verify-old env MAX_EXPORT_AGE_SECONDS=20 ./verify.sh --state "$before_run_file"
  assert_rc "the run from before the restart still verifies from retained history" 0
  assert_in_output "  and its last export is reported, not assumed to be recent" "last exported"
  run verify-new ./verify.sh
  assert_rc "the run after the restart verifies" 0
  dump_if_failed
}

case_teardown() {
  part "teardown: stops only this project, keeps the data, purges only on request"
  stack_up
  demo
  before_teardown_run=$STATE_HOME/runs/$(state_value .runId).json
  other_containers || abort_run "could not take the inventory of the foreign containers before the teardown (status $?); not continuing"
  others_before=$Q_OUT
  must teardown "teardown.sh stops the stack" ./teardown.sh
  check_query "no container of the project remains" 0 count_project_containers
  check_query "its two volumes (Prometheus and Grafana) remain" 2 project_volume_count
  check "the run state is kept" yes "$([ -f "$STATE_HOME/run-state.json" ] && echo yes || echo no)"
  assert_others_unchanged "no other container was started, stopped or removed" "$others_before"
  must teardown-again "teardown.sh is safe to run when nothing is running" ./teardown.sh
  stack_up
  run verify-history ./verify.sh --state "$before_teardown_run"
  assert_rc "after setup.sh the run from before the teardown still verifies (the telemetry volume kept its history)" 0
  assert_in_output "  and reports the process's last export, not a claim that it is running" "last exported"
  must purge "teardown.sh --purge succeeds" ./teardown.sh --purge
  check_query "after --purge the project has no containers or volumes" "0 0" project_resources
  check "  the run state is gone, because its history is" no "$([ -f "$STATE_HOME/run-state.json" ] && echo yes || echo no)"
  assert_others_unchanged "  and no other container was touched" "$others_before"
}


case_series_limit() {
  part "series-limit: a Registry process past its series limit folds detail into one overflow series, and the demo says so"
  ensure_empty_project series-limit
  stack_up APICURIO_USAGE_METRICS_MAX_SERIES=4
  local schema body gid c
  schema=$(jq -nc --arg ns "test.$RANDOM$RANDOM" '{type: "record", name: "Limit", namespace: $ns, fields: []}')
  body=$(jq -nc --arg c "$schema" '{artifactId: "limit", artifactType: "AVRO", firstVersion: {version: "1", content: {content: $c, contentType: "application/json"}}}')
  gid=$(api_post "/groups/usage-otel-test/artifacts" "$body" | jq -r .version.globalId) || gid=""
  [[ "$gid" =~ ^[0-9]+$ ]] || abort_run "the small-limit schema was not registered; the scenario is abandoned"
  for c in c1 c2 c3 c4 c5 c6; do
    [ "$(http_status "$API/ids/globalIds/$gid" -H "X-Registry-Client-Id: $c" -H 'X-Registry-Operation: SERIALIZE')" = 200 ] || fail "fetch as $c did not answer 200"
  done
  # shellcheck disable=SC2329 # called through retry_until
  folded() { [ "$(prom_query "sum($USAGE_METRIC{otel_metric_overflow=\"true\"})" | jq -r '.data.result[0].value[1] // "0"')" = 3 ]; }
  if retry_until 60 folded; then ok "6 fetches by 6 clients with a limit of 4: 3 keep their labels, the other 3 are in one series labeled otel_metric_overflow=true"; else fail "the overflow series did not reach 3"; fi
  check "the total over all series is still exactly 6" 6 "$(prom_query "sum($USAGE_METRIC)" | jq -r '.data.result[0].value[1] // "none"')"
  check "the overflow series carries no client or identifier labels" 0 \
    "$(prom_query "$USAGE_METRIC{otel_metric_overflow=\"true\"}" | jq '[.data.result[].metric | keys[] | select(startswith("apicurio_registry_"))] | length')"
  TELEMETRY_TIMEOUT=20 run demo-over-limit ./demo.sh
  assert_nonzero "demo.sh fails instead of passing on series it cannot tell apart"
  assert_in_output "  and says the series limit was reached" "has reached its series limit"
  if $keep; then
    KEPT_NOTE="The kept stack was started by the series-limit case with APICURIO_USAGE_METRICS_MAX_SERIES=4 and holds its overflow data."
  else
    must purge-after "clean up" ./teardown.sh --purge
  fi
}

# --- dashboard-scenarios -------------------------------------------------------------------------------
#
# Not in the default list (it waits more than five minutes on purpose); run it with `tests/dashboard-scenarios.sh`, which is this
# case under this runner's ownership: the project claim, the empty-project preflight (containers, volumes and networks), the
# invocation's own directories, the interruptible script calls with their settle-before-cleanup rule, and the owned cleanup.
# The case adds nothing of its own to any of those. It creates the situations the finite demo cannot show, writes one scenario
# file in its own directory, and runs tests/dashboard-test.sh on it and on three rate mutants.
case_dashboard_scenarios() {
  part "dashboard-scenarios: heartbeat-only, overflow, restart with reused identifiers, steady rates and a stopped process, read through the canonical dashboard"
  # shellcheck source=tests/dashboard-lib.sh
  . ./tests/dashboard-lib.sh
  ensure_empty_project dashboard-scenarios
  local dir=$TEST_ROOT/dashboard-scenarios
  mkdir -p "$dir"
  local t0 t_hb_end t_ovf_end t_rate_start t_rate_end t_loop_end steady_from beat a b schema gid_a gid_b c
  local n1 n2 n3 requests
  t0=$(($(date +%s) * 1000 - 10000))
  stack_up APICURIO_USAGE_METRICS_MAX_SERIES=4
  beat=$(newest_heartbeat) || abort_run "no heartbeat in Prometheus after setup; the scenario is abandoned"
  a=${beat% *}
  info "first process: $a"
  t_hb_end=$(($(date +%s) * 1000))
  sleep 1
  check_query "the first process has no usage series before any fetch" none dash_usage_count "$a"
  schema='{"type":"record","name":"Scenario","fields":[{"name":"f","type":"string"}]}'
  gid_a=$(dash_create_artifact scenario-a "$schema") || abort_run "could not create the artifact in the first process"
  for c in c1 c2 c3 c4 c5 c6; do
    dash_fetch_recorded "$gid_a" "$c" SERIALIZE "$dir/requests-first.log" || abort_run "a planned fetch of the first process failed (see the line above); the scenario is abandoned"
  done
  # shellcheck disable=SC2329 # called through retry_until
  sent_a() { dash_sent_total "$a" 6; }
  retry_until 90 sent_a || abort_run "Prometheus did not show the 6 fetches of the first process within 90s"
  sleep 6
  t_ovf_end=$(($(date +%s) * 1000))
  ok "six fetches by six clients at a limit of four reached Prometheus (in this pinned SDK the split was observed in sequence, 3 detailed and 3 overflow; it is not a claim about concurrent requests)"

  restart_registry
  # shellcheck disable=SC2329 # called through retry_until
  second_process() {
    local x
    x=$(newest_heartbeat) || return 1
    [ "${x% *}" != "$a" ] || return 1
    b=${x% *}
  }
  retry_until 120 second_process || abort_run "no heartbeat of a second process within 120s"
  info "second process: $b"
  gid_b=$(dash_create_artifact scenario-b "$schema") || abort_run "could not create the artifact in the second process"
  [ "$gid_b" = "$gid_a" ] || info "note: the second process assigned global ID $gid_b, not $gid_a; the processes scenario will say that reused identifiers were not exercised"

  # Steady traffic. Every successful request is recorded with its completion second, so the expected rates come from the
  # requests inside the interval the chart is asked about, not from the whole loop and not from anything that happens later
  : > "$dir/requests.log"
  t_rate_start=$(($(date +%s) * 1000))
  dash_steady_traffic "$gid_b" "$dir/requests.log" 150 || abort_run "a planned fetch of the steady traffic failed (see the line above); the scenario is abandoned before more traffic"
  t_loop_end=$(($(date +%s) * 1000))
  n1=$(awk '$2 == "SERIALIZE" && $3 == "c1"' "$dir/requests.log" | wc -l | tr -d ' ')
  n2=$(awk '$2 == "SERIALIZE" && $3 == "c2"' "$dir/requests.log" | wc -l | tr -d ' ')
  n3=$(awk '$2 == "DESERIALIZE" && $3 == "c1"' "$dir/requests.log" | wc -l | tr -d ' ')
  [ $((n1 + n2 + n3)) = 300 ] || abort_run "the steady traffic recorded $((n1 + n2 + n3)) answered fetches, not the 300 it plans (150 iterations: 150 c1 SERIALIZE, 75 c2 SERIALIZE, 75 c1 DESERIALIZE); the scenario is abandoned"
  info "sent c1 SERIALIZE $n1, c2 SERIALIZE $n2, c1 DESERIALIZE $n3 in $(((t_loop_end - t_rate_start) / 1000)) seconds"
  # shellcheck disable=SC2329 # called through retry_until
  sent_b() { dash_sent_total "$b" $((n1 + n2 + n3)); }
  retry_until 90 sent_b || abort_run "Prometheus did not show the $((n1 + n2 + n3)) fetches of the second process within 90s"
  # The chart is read from the start of the traffic to 130 seconds in. A rate point looks back 60 seconds, so from 75 seconds in
  # the whole look-back lies inside the traffic. Every point is then checked against the requests recorded in its own 60 second
  # window (dashboard-test.sh), so traffic that is uneven, or that continues after the last point, cannot change an expectation.
  # Tolerances: each recorded completion second stands for an interval; the counter moves when it is exported (every 5 s) and
  # scraped (every 5 s), so up to 10 seconds after its requests (lagSeconds), which widens that interval. A point may also be
  # 25 percent off, plus one request of slack, for what rate() adds at the window edges (extrapolation and scrape alignment).
  # The exact bounds are in approx_rate_points in dashboard-lib.sh
  t_rate_end=$((t_rate_start + 130000))
  steady_from=$((t_rate_start + 75000))
  [ "$t_rate_end" -lt "$t_loop_end" ] || abort_run "the traffic ended before the interval in which the rates are read"
  requests=$(awk '{ print $2 "\t" $1 }' "$dir/requests.log" | jq -R -s -c '[split("\n")[] | select(length > 0) | split("\t") | {op: .[0], t: (.[1] | tonumber)}] | group_by(.op) | map({key: .[0].op, value: map(.t)}) | from_entries') \
    || abort_run "could not build the recorded requests"
  # The first process stopped at the restart. Prometheus keeps answering an instant query with its last sample for five minutes
  # (the lookback) and the Collector serves a silent process for five minutes: wait for both, bounded to seven minutes, and
  # evaluate at one instant that the scenario then also uses as the end of its window
  local t_end=0
  # shellcheck disable=SC2329 # called through retry_until
  stale_at_now() {
    local t out
    t=$(($(date +%s) * 1000))
    out=$(prom_query "count($USAGE_METRIC{$SOURCE_LABEL=\"$a\"}) or count(jvm_cpu_count{$SOURCE_LABEL=\"$a\"})" "$((t / 1000))") || return 1
    [ "$(jq -r '.data.result[0].value[1] // "none"' <<< "$out")" = none ] || return 1
    t_end=$((t / 1000 * 1000))
  }
  retry_until 420 stale_at_now || abort_run "the first process's usage and heartbeat series were still in the instant view seven minutes after it stopped"

  jq -n --arg a "$a" --arg b "$b" --argjson t0 "$t0" --argjson hb "$t_hb_end" --argjson ovf "$t_ovf_end" --argjson rs "$t_rate_start" --argjson re "$t_rate_end" \
    --argjson steady "$steady_from" --argjson tend "$t_end" --argjson n1 "$n1" --argjson n2 "$n2" --argjson n3 "$n3" --arg g "$gid_a" --arg gb "$gid_b" \
    --argjson requests "$requests" '
    { schemaVersion: 1, scenarios: [
      { kind: "heartbeat-only", instance: $a, fromMs: $t0, toMs: $hb },
      { kind: "overflow", instance: $a, fromMs: $t0, toMs: $ovf, detail: 3, total: 6 },
      { kind: "processes", primary: $a, other: $b, fromMs: $t0, toMs: $tend,
        primaryRows: [["c1", "global_id", $g, "SERIALIZE", 1], ["c2", "global_id", $g, "SERIALIZE", 1], ["c3", "global_id", $g, "SERIALIZE", 1]],
        otherRows: [["c1", "global_id", $gb, "SERIALIZE", $n1], ["c2", "global_id", $gb, "SERIALIZE", $n2], ["c1", "global_id", $gb, "DESERIALIZE", $n3]] },
      { kind: "rates", instance: $b, fromMs: $rs, toMs: $re, steadyFromMs: $steady, requests: $requests, lagSeconds: 10, tolerance: 0.25 },
      { kind: "stopped", instance: $a, fromMs: $t0, toMs: $tend,
        rows: [["c1", "global_id", $g, "SERIALIZE", 1], ["c2", "global_id", $g, "SERIALIZE", 1], ["c3", "global_id", $g, "SERIALIZE", 1]] } ] }' > "$dir/scenarios.json" \
    || abort_run "could not write the scenario file"
  jq '{schemaVersion, scenarios: [.scenarios[] | select(.kind == "rates")]}' "$dir/scenarios.json" > "$dir/scenarios-rates.json" || abort_run "could not write the rates scenario file"
  info "scenario files: $dir"

  run dashboard-test ./tests/dashboard-test.sh --only static,provisioned,scenarios --scenario "$dir/scenarios.json"
  assert_rc "tests/dashboard-test.sh passes every scenario" 0
  dump_if_failed
  mutant_experiment "$dir/scenarios-rates.json" "$dir" run
  if $keep; then
    KEPT_NOTE="The kept stack was started by the dashboard-scenarios case with APICURIO_USAGE_METRICS_MAX_SERIES=4; the scenario files are in $dir."
  else
    must purge-after "clean up" ./teardown.sh --purge
  fi
}
# dash_create_artifact <artifact id> <content>: prints the global ID
dash_create_artifact() {
  api_post "/groups/dashboard-scenarios/artifacts" "$(jq -nc --arg a "$1" --arg c "$2" \
    '{artifactId: $a, artifactType: "AVRO", firstVersion: {version: "1", content: {content: $c, contentType: "application/json"}}}')" | jq -er '.version.globalId'
}
# dash_sent_total <process> <expected>: the cumulative fetches of a process in Prometheus, detail and overflow together
dash_sent_total() {
  local out
  out=$(prom_query "sum($USAGE_METRIC{$SOURCE_LABEL=\"$1\"})") || return 1
  [ "$(jq -r '.data.result[0].value[1] // "none"' <<< "$out")" = "$2" ]
}
# dash_usage_count <process>: the number of usage series of a process, "none" without any (Q_OUT, as check_query expects)
dash_usage_count() {
  local out
  out=$(prom_query "count($USAGE_METRIC{$SOURCE_LABEL=\"$1\"})") || return 1
  Q_OUT=$(jq -r '.data.result[0].value[1] // "none"' <<< "$out")
}

for c in "${cases[@]}"; do
  fn=case_${c//-/_}
  if declare -F "$fn" > /dev/null; then "$fn"; else die "unknown case '$c'"; fi
done

