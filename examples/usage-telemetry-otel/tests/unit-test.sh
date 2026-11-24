#!/usr/bin/env bash
# Fast regression tests for the example's scripts. No real container, daemon or external service is used:
# the container runtime is tests/fixtures/fake-runtime.sh, and every network endpoint is a throwaway
# listener on 127.0.0.1. They cover the defects a real-stack run does not show: process trees that outlive a
# timeout, state shared between projects, a runtime that returns nothing or hangs, a test harness that
# reports success after failing to clean up, and endpoints inherited from the caller's environment.
#
# Usage: ./tests/unit-test.sh [case...]
# Prerequisites: bash 3.2 or later, curl, jq, od, python3 (for the throwaway HTTP backends and port listeners), ps, and a
#   shell that supports job control (set -m).
# Cases (default: all): process-tree group-lifetime outer-cancel launch-trap launch-split syntax-each empty-engine
#   bounded-probes runtime-probe atomic-publication project-isolation symlink-base path-spelling state-validation
#   state-snapshot target-state selector-types setup-freshness setup-logs outage-recovery harness-cleanup root-cancel
#   claims claim-signals claim-boundaries claim-types finish-boundaries project-reset listener-ownership state-bytes
#   endpoint-isolation failed-setup verify-failures inventory-status keep-and-ports dashboard-provisioning dashboard-owner
# Ends with "All checks passed." and status 0, or with the failed checks and status 1.
set -Eeuo pipefail
trap 'rc=$?; [ "$BASH_SUBSHELL" -ne 0 ] || printf "unit-test aborted at line %s (status %s): a command failed outside a check\n" "$LINENO" "$rc" >&2' ERR
cd "$(dirname "$0")/.."

TMP=$(mktemp -d "${TMPDIR:-/tmp}/usage-telemetry-otel-unit.XXXXXX")
FAKEBIN=$TMP/bin
mkdir -p "$FAKEBIN"
ln -s "$PWD/tests/fixtures/fake-runtime.sh" "$FAKEBIN/podman"
export FAKE_LOG=$TMP/fake.log FAKE_PIDS=$TMP/fake.pids FAKE_FLAG_DIR=$TMP/flags
mkdir -p "$FAKE_FLAG_DIR"
: > "$FAKE_LOG"
: > "$FAKE_PIDS"
OWN_PIDS=""

# Nothing from the caller's environment may steer these tests
unset API REGISTRY_URL PROMETHEUS_URL GRAFANA_URL COLLECTOR_HEALTH_URL OTEL_COLLECTOR_CONFIG PROMETHEUS_CONFIG \
  APICURIO_USAGE_METRICS_MAX_SERIES COMPOSE_PROJECT_NAME LOCAL_DIR PROBE_TIMEOUT CONTAINER_RUNTIME REGISTRY_IMAGE GRAFANA_DATASOURCE_UID
export PATH=$FAKEBIN:$PATH CONTAINER_RUNTIME=podman

# shellcheck source=lib.sh
. ./lib.sh

cleanup() {
  local p
  for p in $OWN_PIDS $(cat "$FAKE_PIDS" 2> /dev/null); do kill "$p" 2> /dev/null || true; done
  if [ -n "${UNIT_TEST_KEEP:-}" ]; then echo "kept $TMP" >&2; else rm -rf "$TMP"; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

alive() { kill -0 "$1" 2> /dev/null; }
dead() { ! alive "$1"; }
# all_dead <pid...>: after a short grace period none is running
all_dead() {
  local p
  for p in "$@"; do retry_until 5 dead "$p" || return 1; done
}
read_pid() { cat "$1" 2> /dev/null || true; }
checksum() { cksum < "$1"; }

# fake runtime in this shell
detect_runtime
compose_cmd

# --- process-tree ------------------------------------------------------------------------------------

case_process_tree() {
  part "process-tree: a timeout, INT or TERM ends the command and everything it started, and only that"
  mkdir -p "$TMP/tree"
  cat > "$TMP/tree/tree.sh" << 'EOF'
#!/usr/bin/env bash
# child and grandchild of the command, pids recorded in the files named by $1
sleep 61 &
echo $! > "$1.child"
bash -c 'sleep 62 & echo $! > "$1.grand"; wait' tree "$1" &
wait
EOF
  chmod +x "$TMP/tree/tree.sh"
  local control rc=0 child grand started
  sleep 100 &
  control=$!
  OWN_PIDS="$OWN_PIDS $control"

  started=$SECONDS
  # 3 seconds: long enough for a loaded machine to start the command and record its children
  run_bounded 3 "$TMP/tree/tree.sh" "$TMP/tree/t1" || rc=$?
  check "a command that overruns its deadline is reported as timed out" 124 "$rc"
  check "  within the deadline plus the grace period" yes "$([ $((SECONDS - started)) -le 10 ] && echo yes || echo no)"
  check "  after the command had started its child and grandchild" yes "$([ -s "$TMP/tree/t1.child" ] && [ -s "$TMP/tree/t1.grand" ] && echo yes || echo no)"
  child=$(read_pid "$TMP/tree/t1.child")
  grand=$(read_pid "$TMP/tree/t1.grand")
  check "  and neither its child nor its grandchild survives" yes "$(all_dead "$child" "$grand" && echo yes || echo no)"
  check "an unrelated process of the caller is untouched" yes "$(alive "$control" && echo yes || echo no)"

  rc=0
  run_bounded 5 bash -c 'exit 7' || rc=$?
  check "the exit status of a command that finishes in time is kept" 7 "$rc"

  cat > "$TMP/tree/signalled.sh" << EOF
#!/usr/bin/env bash
cd "$PWD"
. ./lib.sh
run_bounded 60 "$TMP/tree/tree.sh" "$TMP/tree/\$1"
EOF
  local sig name want h
  for sig in TERM INT; do
    name=s$sig
    want=143
    [ "$sig" = INT ] && want=130
    # Job control gives the helper its own process group and lets it receive INT as a terminal would send it
    set -m
    bash "$TMP/tree/signalled.sh" "$name" > /dev/null 2>&1 &
    h=$!
    set +m
    retry_until 10 test -s "$TMP/tree/$name.grand" || true
    child=$(read_pid "$TMP/tree/$name.child")
    grand=$(read_pid "$TMP/tree/$name.grand")
    check "$sig: the command's child and grandchild are running before the signal" yes "$([ -n "$child" ] && [ -n "$grand" ] && alive "$child" && alive "$grand" && echo yes || echo no)"
    kill "-$sig" "$h" 2> /dev/null || true
    rc=0
    wait "$h" 2> /dev/null || rc=$?
    check "$sig: the interrupted script leaves with the conventional status" "$want" "$rc"
    check "  and neither child nor grandchild survives" yes "$(all_dead "$child" "$grand" && echo yes || echo no)"
    check "  and the unrelated process of the caller is untouched" yes "$(alive "$control" && echo yes || echo no)"
  done
  kill "$control" 2> /dev/null || true
}

# --- syntax-each -------------------------------------------------------------------------------------

case_syntax_each() {
  part "syntax-each: a syntax error in any file is found, not only in the first"
  mkdir -p "$TMP/syn"
  printf 'echo fine\n' > "$TMP/syn/valid.sh"
  printf 'if then fi (\n' > "$TMP/syn/invalid.sh"
  local out rc=0
  bash -n "$TMP/syn/valid.sh" "$TMP/syn/invalid.sh" 2> /dev/null || rc=$?
  check "why the check is needed: bash -n with two files stops after the first (status $rc)" 0 "$rc"
  rc=0
  out=$(syntax_check_each "$TMP/syn/valid.sh" "$TMP/syn/invalid.sh") || rc=$?
  check "the invalid file after a valid one is caught" 1 "$rc"
  check "  and named" "syntax error: $TMP/syn/invalid.sh" "$out"
  rc=0
  syntax_check_each "$TMP/syn/invalid.sh" "$TMP/syn/valid.sh" > /dev/null || rc=$?
  check "the invalid file before a valid one is caught" 1 "$rc"
  rc=0
  syntax_check_each lib.sh setup.sh demo.sh verify.sh teardown.sh tests/lifecycle-test.sh tests/unit-test.sh tests/fixtures/fake-runtime.sh > /dev/null || rc=$?
  check "every script of the example parses" 0 "$rc"
}

# --- empty-engine ------------------------------------------------------------------------------------

case_empty_engine() {
  part "empty-engine: no foreign container is a valid empty answer; a failed or hung query is an error"
  local out rc
  export FAKE_COMPOSE_PS_OUT=$'aaaaaaaaaaaa1111\nbbbbbbbbbbbb2222\n'
  export FAKE_PS_OUT=$'aaaaaaaaaaaa running\nbbbbbbbbbbbb exited\n'
  rc=0
  other_containers || rc=$?
  check "a runtime with only the project's own containers: success" 0 "$rc"
  check "  and an empty set" "" "$Q_OUT"
  export FAKE_PS_OUT=$'aaaaaaaaaaaa running\ncccccccccccc running\nbbbbbbbbbbbb exited\n'
  rc=0
  other_containers || rc=$?
  check "a foreign container is listed, the project's are not" "cccccccccccc running" "$Q_OUT"
  rc=0
  FAKE_PS_RC=5 other_containers > /dev/null || rc=$?
  check "a failed runtime query is a failure, not an empty set" 5 "$rc"
  rc=0
  FAKE_COMPOSE_PS_RC=6 other_containers > /dev/null || rc=$?
  check "a failed project query is a failure too" 6 "$rc"
  rc=0
  PROBE_TIMEOUT=2 FAKE_STALL=ps other_containers > /dev/null || rc=$?
  check "a hung runtime query times out (124), not an empty set" 124 "$rc"
  unset FAKE_COMPOSE_PS_OUT FAKE_PS_OUT
}

# --- bounded-probes ----------------------------------------------------------------------------------

case_bounded_probes() {
  part "bounded-probes: every runtime query has a deadline, a timeout is an error, and nothing is left running"
  local rc started stall
  export PROBE_TIMEOUT=2
  export FAKE_COMPOSE_PS_OUT=$'dddddddddddd0000\n'
  rc=0; started=$SECONDS
  FAKE_STALL=compose-ps service_container registry > /dev/null || rc=$?
  check "service_container with a hung ps: timed out" 124 "$rc"
  check "  within the bound" yes "$([ $((SECONDS - started)) -le 8 ] && echo yes || echo no)"
  rc=0
  FAKE_STALL=compose-ps count_project_containers > /dev/null || rc=$?
  check "count_project_containers with a hung ps: timed out, not zero" 124 "$rc"
  rc=0
  FAKE_STALL=inspect container_state registry > /dev/null || rc=$?
  check "container_state with a hung inspect: timed out" 124 "$rc"
  rc=0
  FAKE_STALL=volume project_volumes > /dev/null || rc=$?
  check "project_volumes with a hung volume listing: timed out, not empty" 124 "$rc"
  rc=0
  FAKE_STALL=compose-ps compose config -q > /dev/null || rc=$?
  check "a quick compose query still works while only ps hangs" 0 "$rc"

  mkdir -p "$TMP/probe-state"
  for stall in volume compose-ps; do
    rc=0; started=$SECONDS
    LOCAL_DIR=$TMP/probe-state FAKE_STALL=$stall ./teardown.sh > "$TMP/teardown-$stall.out" 2>&1 || rc=$?
    check "teardown.sh with a hung $stall query does not report success" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  it says the query timed out" yes "$(grep -q 'timed out' "$TMP/teardown-$stall.out" && echo yes || echo no)"
    check "  within the bound" yes "$([ $((SECONDS - started)) -le 30 ] && echo yes || echo no)"
  done
  rc=0
  LOCAL_DIR=$TMP/probe-state REGISTRY_IMAGE=dummy FAKE_STALL=compose-ps ./setup.sh > "$TMP/setup-stall.out" 2>&1 || rc=$?
  check "setup.sh with a hung compose ps does not go on" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  it says the query timed out" yes "$(grep -q 'timed out' "$TMP/setup-stall.out" && echo yes || echo no)"
  unset FAKE_COMPOSE_PS_OUT PROBE_TIMEOUT
  # shellcheck disable=SC2046
  check "no command left by the hung queries is still running" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
}

# --- project-isolation -------------------------------------------------------------------------------

case_project_isolation() {
  part "project-isolation: purging one project never touches another's state, even in the same LOCAL_DIR"
  local base=$TMP/shared outside=$TMP/outside before_b after_b p
  mkdir -p "$outside/dir"
  printf 'outside file\n' > "$outside/file.json"
  printf 'outside dir file\n' > "$outside/dir/x.json"
  for p in proj-a proj-b proj-c; do
    LOCAL_DIR=$base COMPOSE_PROJECT_NAME=$p bash -c ". ./lib.sh; ensure_local_dir"
    printf '{"schemaVersion":2,"project":"%s","runId":"r1"}\n' "$p" > "$base/projects/$p/run-state.json"
    printf '{"schemaVersion":2,"project":"%s","runId":"r1"}\n' "$p" > "$base/projects/$p/runs/r1.json"
    printf 'log of %s\n' "$p" > "$base/projects/$p/logs/setup.log"
  done
  printf 'a note the user keeps here\n' > "$base/notes.txt"
  printf '{"schemaVersion":1,"legacy":"unscoped"}\n' > "$base/run-state.json"
  ln -s "$outside/file.json" "$base/projects/proj-a/runs/linked.json"
  rm -rf "$base/projects/proj-c/runs"
  ln -s "$outside/dir" "$base/projects/proj-c/runs"
  before_b=$(cd "$base/projects/proj-b" && cksum run-state.json runs/r1.json logs/setup.log)
  local legacy_before notes_before
  legacy_before=$(checksum "$base/run-state.json")
  notes_before=$(checksum "$base/notes.txt")

  local rc=0
  LOCAL_DIR=$base ./teardown.sh --project proj-a > "$TMP/td-a.out" 2>&1 || rc=$?
  check "teardown.sh (no purge) succeeds" 0 "$rc"
  check "  and keeps the project's own run state" yes "$([ -f "$base/projects/proj-a/run-state.json" ] && echo yes || echo no)"
  rc=0
  LOCAL_DIR=$base ./teardown.sh --project proj-a --purge > "$TMP/td-a.out" 2>&1 || rc=$?
  check "teardown.sh --project proj-a --purge succeeds" 0 "$rc"
  check "  project A's run state is gone" no "$([ -e "$base/projects/proj-a/run-state.json" ] || [ -e "$base/projects/proj-a/runs/r1.json" ] && echo yes || echo no)"
  after_b=$(cd "$base/projects/proj-b" && cksum run-state.json runs/r1.json logs/setup.log 2>&1 || echo "B's files are missing")
  check "  project B's state and logs are byte for byte unchanged" "$before_b" "$after_b"
  check "  a file the user keeps in the shared directory is untouched" "$notes_before" "$(checksum "$base/notes.txt")"
  check "  the old unscoped run-state.json is neither read nor deleted" "$legacy_before" "$(checksum "$base/run-state.json")"
  check "  a symbolic link inside A's runs is removed as a link, its target is not" "outside file" "$(cat "$outside/file.json")"
  rc=0
  LOCAL_DIR=$base ./teardown.sh --project proj-c --purge > "$TMP/td-c.out" 2>&1 || rc=$?
  check "purging a project whose runs directory is a symbolic link is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and deletes nothing behind the link" "outside dir file" "$(cat "$outside/dir/x.json")"

  rc=0
  LOCAL_DIR=$base ./teardown.sh --project 'Bad Name' --purge > "$TMP/td-bad.out" 2>&1 || rc=$?
  check "an invalid project name is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"

  rc=0
  LOCAL_DIR=$base REGISTRY_IMAGE=dummy FAKE_CONFIG_RC=1 ./setup.sh --project proj-new > "$TMP/setup-new.out" 2>&1 || rc=$?
  check "setup.sh --project stops at the (faked) configuration failure" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  but had already derived its state directory from the option, not from the default project" yes \
    "$([ -d "$base/projects/proj-new/logs" ] && [ ! -e "$base/projects/usage-telemetry-otel" ] && echo yes || echo no)"

  ln -s "$outside" "$TMP/linked-local"
  rc=0
  LOCAL_DIR=$TMP/linked-local ./teardown.sh --project proj-b --purge > "$TMP/td-link.out" 2>&1 || rc=$?
  check "a LOCAL_DIR that is a symbolic link is not written through by purge" "outside dir file" "$(cat "$outside/dir/x.json")"
}

# --- state-validation --------------------------------------------------------------------------------

case_state_validation() {
  part "state-validation: verify.sh refuses legacy or foreign run state and leaves it alone"
  local dir=$TMP/state rc out before
  mkdir -p "$dir"
  printf '{"schemaVersion":1,"runId":"old","source":{"instanceId":"x"},"expectedSeries":[{"clientId":"a"}],"settledAtEpochSeconds":1}\n' > "$dir/legacy.json"
  printf '{"schemaVersion":2,"project":"other-project","runId":"r","source":{"instanceId":"x"},"expectedSeries":[{"clientId":"a"}],"schemas":[{"globalId":1,"contentId":1}],"notCounted":[],"evaluationTime":"1795129704.514"}\n' > "$dir/foreign.json"
  printf '{"schemaVersion":2,"project":"usage-telemetry-otel","runId":"r","source":{"instanceId":"x"},"expectedSeries":[{"clientId":"a"}],"schemas":[{"globalId":1,"contentId":1}],"notCounted":[]}\n' > "$dir/no-time.json"
  local f
  for f in legacy foreign no-time; do
    before=$(checksum "$dir/$f.json")
    rc=0
    # ports nothing listens on: the stack checks fail at once and the state checks still run
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59981 PROMETHEUS_PORT=59982 GRAFANA_PORT=59983 COLLECTOR_HEALTH_PORT=59984 \
      ./verify.sh --state "$dir/$f.json" > "$dir/$f.out" 2>&1 || rc=$?
    check "verify.sh rejects the $f state" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  without changing it" "$before" "$(checksum "$dir/$f.json")"
  done
  check "  a legacy (version 1) state is explained, not migrated" yes "$(grep -q 'only version 2' "$dir/legacy.out" && echo yes || echo no)"
  check "  a state of another project names that project" yes "$(grep -q "belongs to project 'other-project'" "$dir/foreign.out" && echo yes || echo no)"
  check "  a state without an evaluation time is incomplete" yes "$(grep -q 'not a complete version 2 run state' "$dir/no-time.out" && echo yes || echo no)"
}

# --- harness-cleanup ---------------------------------------------------------------------------------

# harness <name> <env assignments...> -- <lifecycle-test args...>: runs tests/lifecycle-test.sh with the
# fake runtime; sets H_RC and H_OUT
harness() {
  local name=$1
  shift
  local envs=()
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  H_OUT=$TMP/harness-$name.out
  : > "$FAKE_LOG"
  H_RC=0
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" TEST_PROJECT=ut-project REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-$name" ${envs[@]+"${envs[@]}"} \
    ./tests/lifecycle-test.sh "$@" > "$H_OUT" 2>&1 || H_RC=$?
}
down_calls() { grep -c ' down ' "$FAKE_LOG" || true; }
in_out() { grep -q -F -- "$1" "$H_OUT" && echo yes || echo no; }

case_harness_cleanup() {
  part "harness-cleanup: the lifecycle test cleans up only its own project, on every exit, and cleanup failures fail it"
  harness ok -- local-dir-guard
  check "a passing run exits 0 and says so" "0 yes" "$H_RC $(in_out 'All checks passed.')"
  check "  and purged its own project once" 1 "$(down_calls)"
  check "  and only that project" yes "$(grep -q -- '-p ut-project down' "$FAKE_LOG" && ! grep -- ' down ' "$FAKE_LOG" | grep -q -v -- '-p ut-project' && echo yes || echo no)"

  harness cleanfail FAKE_DOWN_RC=9 -- local-dir-guard
  check "a cleanup that fails makes an otherwise green run fail" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  it says the cleanup failed" yes "$(in_out 'cleanup of test project ut-project FAILED')"
  check "  and does not claim success" no "$(in_out 'All checks passed.')"

  harness earlyexit -- no-such-case
  check "an unexpected exit is not success" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  yet the exit trap still ran the scoped cleanup" 1 "$(down_calls)"

  local pidfile=$TMP/listener.pid
  harness listener SELFTEST_PID_FILE="$pidfile" FAKE_DOWN_RC=9 -- selftest-early-exit
  check "an early exit with status 3 and a failing cleanup keeps status 3" 3 "$H_RC"
  # shellcheck disable=SC2046
  check "  and the busy-port listener the case started is stopped" yes "$([ -s "$pidfile" ] && all_dead $(cat "$pidfile") && echo yes || echo no)"

  harness keep -- --keep local-dir-guard
  check "--keep exits 0" 0 "$H_RC"
  check "  and runs no cleanup" 0 "$(down_calls)"
  check "  and says what is left" yes "$(in_out '--keep: nothing was cleaned up. Project ut-project now has 0 container(s)')"

  harness existing FAKE_COMPOSE_PS_OUT=someexistingid -- local-dir-guard
  check "a test project that already has containers is refused" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  and nothing was purged" 0 "$(down_calls)"
  harness default TEST_PROJECT=usage-telemetry-otel -- local-dir-guard
  check "the default project is refused" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  and nothing was purged" 0 "$(down_calls)"

  # TERM while a case is running: scoped cleanup, status 143
  : > "$FAKE_LOG"
  set -m
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" TEST_PROJECT=ut-project REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-term" SELFTEST_PID_FILE="$TMP/term.pid" \
    ./tests/lifecycle-test.sh selftest-hold > "$TMP/harness-term.out" 2>&1 &
  local h=$! rc=0
  set +m
  retry_until 20 test -s "$TMP/term.pid" || true
  kill -TERM "$h" 2> /dev/null || true
  wait "$h" 2> /dev/null || rc=$?
  check "TERM during a case ends the run with status 143" 143 "$rc"
  H_OUT=$TMP/harness-term.out
  check "  runs the scoped cleanup" 1 "$(down_calls)"
  # shellcheck disable=SC2046
  check "  and stops the listener" yes "$(all_dead $(cat "$TMP/term.pid") && echo yes || echo no)"
}

# --- endpoint-isolation ------------------------------------------------------------------------------

case_endpoint_isolation() {
  part "endpoint-isolation: ambient endpoint variables never reach the services the lifecycle test drives"
  write_server
  start_server "$TMP/foreign.log" plain 59971 59972 59973 59974
  start_server "$TMP/test.log" plain 59961 59962 59963 59964
  harness endpoints \
    API=http://127.0.0.1:59971/apis/registry/v3 REGISTRY_URL=http://127.0.0.1:59971 PROMETHEUS_URL=http://127.0.0.1:59972 \
    GRAFANA_URL=http://127.0.0.1:59973 COLLECTOR_HEALTH_URL=http://127.0.0.1:59974 \
    TEST_REGISTRY_PORT=59961 TEST_PROMETHEUS_PORT=59962 TEST_GRAFANA_PORT=59963 TEST_COLLECTOR_HEALTH_PORT=59964 \
    -- selftest-endpoints
  check "the foreign endpoints received no request at all" 0 "$(wc -l < "$TMP/foreign.log" | tr -d ' ')"
  check "the test ports received the demo's requests: the Registry's system info" yes \
    "$(grep -q '^59961 GET /apis/registry/v3/system/info' "$TMP/test.log" && echo yes || echo no)"
  check "  and Prometheus' readiness" yes "$(grep -q '^59962 GET /-/ready' "$TMP/test.log" && echo yes || echo no)"
  check "  and nothing was sent to the foreign Registry's API path" 0 "$(grep -c '^5997' "$TMP/test.log" || true)"
}

# --- group-lifetime ----------------------------------------------------------------------------------

case_group_lifetime() {
  part "group-lifetime: the deadline covers the whole process group, also when its leader exits first"
  mkdir -p "$TMP/gl"
  # The leader starts a child that keeps the leader's stdout open, records its pid, and exits at once
  cat > "$TMP/gl/leader.sh" << 'SH'
#!/usr/bin/env bash
sleep 15 &
echo $! > "$1.child"
echo out
exit 0
SH
  cat > "$TMP/gl/quick.sh" << 'SH'
#!/usr/bin/env bash
sleep 1 &
exit 3
SH
  chmod +x "$TMP/gl/leader.sh" "$TMP/gl/quick.sh"
  local rc=0 started value child

  started=$SECONDS
  run_bounded 3 "$TMP/gl/leader.sh" "$TMP/gl/direct" > /dev/null || rc=$?
  child=$(read_pid "$TMP/gl/direct.child")
  check "direct call: a leader that exits first does not end the invocation while its child lives (timed out)" 124 "$rc"
  check "  within the deadline plus the grace period" yes "$([ $((SECONDS - started)) -le 9 ] && echo yes || echo no)"
  check "  and the child is stopped" yes "$([ -n "$child" ] && all_dead "$child" && echo yes || echo no)"

  rc=0; started=$SECONDS
  bcap 3 "$TMP/gl/leader.sh" "$TMP/gl/subst" || rc=$?
  value=$BCAP_OUT
  child=$(read_pid "$TMP/gl/subst.child")
  check "supervised capture: the same, although the child holds the output open (timed out)" 124 "$rc"
  check "  returned before the child's own end, not after it" yes "$([ $((SECONDS - started)) -le 9 ] && echo yes || echo no)"
  check "  with the output the leader wrote" out "$value"
  check "  and the child is stopped" yes "$([ -n "$child" ] && all_dead "$child" && echo yes || echo no)"

  rc=0; started=$SECONDS
  run_bounded 10 "$TMP/gl/quick.sh" || rc=$?
  check "a leader that exits 3 while a child finishes in time keeps its own status" 3 "$rc"
  check "  after the child finished, not before" yes "$([ $((SECONDS - started)) -ge 1 ] && echo yes || echo no)"

  cat > "$TMP/gl/signalled.sh" << EOF2
#!/usr/bin/env bash
cd "$PWD"
. ./lib.sh
run_bounded 60 "$TMP/gl/leader.sh" "$TMP/gl/\$1"
EOF2
  local sig want h
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    sed -i.bak 's/sleep 15 &/sleep 61 \&/' "$TMP/gl/leader.sh" && rm -f "$TMP/gl/leader.sh.bak"
    set -m
    bash "$TMP/gl/signalled.sh" "s$sig" > /dev/null 2>&1 &
    h=$!
    set +m
    retry_until 10 test -s "$TMP/gl/s$sig.child" || true
    child=$(read_pid "$TMP/gl/s$sig.child")
    check "$sig: the leader has exited and its child is still running" yes "$([ -n "$child" ] && alive "$child" && echo yes || echo no)"
    kill "-$sig" "$h" 2> /dev/null || true
    rc=0
    wait "$h" 2> /dev/null || rc=$?
    check "$sig: the interrupted script leaves with the conventional status" "$want" "$rc"
    check "  and the surviving child is stopped" yes "$([ -n "$child" ] && all_dead "$child" && echo yes || echo no)"
  done

  # Isolation that cannot be established must not mean "run it with half a timeout": the command is not started
  mkdir -p "$TMP/gl/badps"
  printf '#!/bin/sh\nexit 1\n' > "$TMP/gl/badps/ps"
  chmod +x "$TMP/gl/badps/ps"
  rc=0
  rm -f "$TMP/gl/started"
  PATH=$TMP/gl/badps:$PATH run_bounded 5 bash -c "echo started > $TMP/gl/started" 2> "$TMP/gl/badps.err" || rc=$?
  check "when the process table cannot be read, run_bounded fails closed (status 125)" 125 "$rc"
  check "  and did not start the command" no "$([ -e "$TMP/gl/started" ] && echo yes || echo no)"
  check "  and says why" yes "$(grep -q 'process group' "$TMP/gl/badps.err" && echo yes || echo no)"
}

# --- symlink-base ------------------------------------------------------------------------------------

case_symlink_base() {
  part "symlink-base: purge refuses a linked base, marker or project path before it touches anything"
  local real=$TMP/real-base link=$TMP/link-base rc before after variant
  mkdir -p "$real"
  LOCAL_DIR=$real COMPOSE_PROJECT_NAME=proj-a bash -c ". ./lib.sh; ensure_local_dir"
  printf '{"schemaVersion":2,"project":"proj-a"}\n' > "$real/projects/proj-a/run-state.json"
  printf '{"schemaVersion":2,"project":"proj-a","runId":"r1"}\n' > "$real/projects/proj-a/runs/r1.json"
  ln -s "$real" "$link"
  before=$(cd "$real/projects/proj-a" && cksum run-state.json runs/r1.json)
  : > "$FAKE_LOG"
  rc=0
  LOCAL_DIR=$link ./teardown.sh --project proj-a --purge > "$TMP/symlink-base.out" 2>&1 || rc=$?
  after=$(cd "$real/projects/proj-a" && cksum run-state.json runs/r1.json 2>&1 || echo missing)
  check "a symbolic-link LOCAL_DIR that points at a real, marked directory with the project's state is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  the target's state is byte for byte unchanged" "$before" "$after"
  check "  it says the base is a symbolic link" yes "$(grep -q 'symbolic link' "$TMP/symlink-base.out" && echo yes || echo no)"
  check "  and refused before the project's containers and volumes were touched" 0 "$(grep -c ' down ' "$FAKE_LOG" || true)"

  for variant in marker projects project runs; do
    rm -rf "$TMP/v-$variant" "$TMP/v-$variant-target"
    mkdir -p "$TMP/v-$variant-target"
    LOCAL_DIR=$TMP/v-$variant COMPOSE_PROJECT_NAME=proj-a bash -c ". ./lib.sh; ensure_local_dir"
    printf '{"schemaVersion":2,"project":"proj-a"}\n' > "$TMP/v-$variant/projects/proj-a/run-state.json"
    printf 'target file\n' > "$TMP/v-$variant-target/run-state.json"
    mkdir -p "$TMP/v-$variant-target/runs"
    printf 'target run\n' > "$TMP/v-$variant-target/runs/r1.json"
    case $variant in
      marker) rm "$TMP/v-$variant/$LOCAL_MARKER"; ln -s "$TMP/v-$variant-target/run-state.json" "$TMP/v-$variant/$LOCAL_MARKER" ;;
      projects) rm -rf "$TMP/v-$variant/projects"; mkdir -p "$TMP/v-$variant-target/proj-a"; cp "$TMP/v-$variant-target/run-state.json" "$TMP/v-$variant-target/proj-a/run-state.json"; ln -s "$TMP/v-$variant-target" "$TMP/v-$variant/projects" ;;
      project) rm -rf "$TMP/v-$variant/projects/proj-a"; ln -s "$TMP/v-$variant-target" "$TMP/v-$variant/projects/proj-a" ;;
      runs) rm -rf "$TMP/v-$variant/projects/proj-a/runs"; ln -s "$TMP/v-$variant-target/runs" "$TMP/v-$variant/projects/proj-a/runs" ;;
    esac
    before=$(cd "$TMP/v-$variant-target" && cksum run-state.json runs/r1.json)
    : > "$FAKE_LOG"
    rc=0
    LOCAL_DIR=$TMP/v-$variant ./teardown.sh --project proj-a --purge > "$TMP/v-$variant.out" 2>&1 || rc=$?
    after=$(cd "$TMP/v-$variant-target" && cksum run-state.json runs/r1.json 2>&1 || echo missing)
    check "a symbolic link as the $variant path is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  and what it points at is untouched" "$before" "$after"
    check "  and nothing was purged" 0 "$(grep -c ' down ' "$FAKE_LOG" || true)"
  done

  rc=0
  : > "$FAKE_LOG"
  LOCAL_DIR=$TMP/never-created ./teardown.sh --project proj-a --purge > "$TMP/never.out" 2>&1 || rc=$?
  check "purging with no LOCAL_DIR at all is fine" 0 "$rc"
  check "  and teardown creates no directory" no "$([ -e "$TMP/never-created" ] && echo yes || echo no)"
  check "  the compose project was purged" 1 "$(grep -c ' down ' "$FAKE_LOG" || true)"
}

# --- listeners shared by the endpoint cases ----------------------------------------------------------

write_server() {
  cat > "$TMP/server.py" << 'PYEOF'
import json, os, re, socketserver, sys, threading, time, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
# usage: server.py LOG MODE PORT [PORT ...]   the ports, in order: registry collector prometheus grafana
# setup-late-N and recover-late-N: the first N heartbeat answers are old, the rest fresh; every answer is also appended
# ("stale" or "fresh") to $HB_LOG when set. Besides those: setup-advstale (a heartbeat that advances but stays 90s old), setup-hb503 (the heartbeat query fails), and
# flap (the usage collector target is up on the first query and the query fails afterwards).
# Modes besides plain: series (the usage series of the JSON file in $SERIES_FILE, selected by the query's label matchers),
# setup-stale, setup-late-N and setup-fresh (a healthy stack whose heartbeat is old for ever, old for its first N answers, or
# always recent), and recover-stale, recover-late-N and recover-fresh (the same for a Registry
# whose outage client already has its count of 5).
log = open(sys.argv[1], "a", buffering=1)
mode = sys.argv[2]
ports = [int(p) for p in sys.argv[3:]]
roles = dict(zip(ports, ["registry", "collector", "prometheus", "grafana"]))
USAGE = "apicurio_usage_schema_fetches_total"
started = time.time()
first_heartbeat_query = []
flaps = []

def heartbeat_time(now):
    ts = heartbeat_time_of(now)
    if os.environ.get("HB_LOG"):
        with open(os.environ["HB_LOG"], "a") as f:
            f.write("stale\n" if now - ts > 30 else "fresh\n")
    return ts

def heartbeat_time_of(now):
    m = re.match(r"(?:setup|recover)-(stale|fresh|advstale|late-(\d+))$", mode)
    if not m:
        return now - 2
    if m.group(1) == "fresh":
        return now - 2
    if m.group(1) == "stale":
        return started - 120
    if m.group(1) == "advstale":
        # the first answer is 120s old, every later one 90s old: it advances, and it is still stale
        if not first_heartbeat_query:
            first_heartbeat_query.append(now)
            return started - 120
        return now - 90
    # late-N: the first N heartbeat answers are old, every later one is fresh (by count, so no timing decides it)
    first_heartbeat_query.append(now)
    return started - 120 if len(first_heartbeat_query) <= int(m.group(2)) else now - 2

def series_vector(selected):
    return {"status": "success", "data": {"resultType": "vector", "result": [
        {"metric": {"__name__": USAGE, "service_instance_id": s.get("instance", "inst-1"), "apicurio_registry_client_id": s["client"],
                    "apicurio_registry_identifier_type": s["type"], "apicurio_registry_identifier_value": s["value"],
                    "apicurio_registry_operation": s["operation"]},
         "value": [time.time(), str(s["count"])]} for s in selected]}}

def select_series(query):
    # a union ("or") of selectors of one metric with = and =~ matchers; =~ is anchored, as in PromQL
    with open(os.environ["SERIES_FILE"]) as f:
        series = json.load(f)
    chosen = []
    for branch in query.split(" or "):
        m = re.match(r"^\s*([A-Za-z_:][A-Za-z0-9_:]*)\{(.*)\}\s*$", branch)
        if not m or m.group(1) != USAGE:
            continue
        matchers = re.findall(r'([A-Za-z_][A-Za-z0-9_]*)(=~|=)"((?:[^"\\]|\\.)*)"', m.group(2))
        for i, s in enumerate(series):
            labels = {"service_instance_id": s.get("instance", "inst-1"), "apicurio_registry_client_id": s["client"],
                      "apicurio_registry_identifier_type": s["type"], "apicurio_registry_identifier_value": s["value"],
                      "apicurio_registry_operation": s["operation"]}
            if i in [c[0] for c in chosen]:
                continue
            if all((labels.get(k, "") == v) if op == "=" else (re.fullmatch(v, labels.get(k, "")) is not None) for k, op, v in matchers):
                chosen.append((i, s))
    return series_vector([c[1] for c in chosen])

def vector(value):
    return {"status": "success", "data": {"resultType": "vector", "result": [] if value is None else [{"metric": {}, "value": [time.time(), str(value)]}]}}

def prom_answer(query, fail, empty, value, qtime=""):
    # Only the final count query is made to fail or come back empty; readiness, targets, heartbeat and the
    # datasource health stay healthy, so a verification that passes anyway is a false green
    final = query.startswith("count(up{job=")
    if fail and final:
        return 503, {"status": "error"}
    if mode == "setup-hb503" and "timestamp(jvm_cpu_count" in query:
        return 503, {"status": "error"}
    if mode == "flap" and query == 'up{job="otel-collector-usage"}':
        flaps.append(1)
        return (200, vector(0)) if len(flaps) == 1 else (503, {"status": "error"})
    if mode == "series" and query.startswith(USAGE + "{"):
        # with $SERIES_TIME set the history exists only at that evaluation time: a query at any other time sees nothing
        if os.environ.get("SERIES_TIME") and qtime != os.environ["SERIES_TIME"]:
            return 200, series_vector([])
        return 200, select_series(query)
    if mode.startswith("recover") and query.startswith(USAGE + "{"):
        return 200, series_vector([{"client": "selftest-client", "type": "global_id", "value": "1", "operation": "SERIALIZE", "count": 5, "instance": "x"}])
    if "timestamp(jvm_cpu_count" in query:
        now = time.time()
        return 200, {"status": "success", "data": {"resultType": "vector", "result": [{"metric": {"service_instance_id": "x"}, "value": [now, str(heartbeat_time(now))]}]}}
    if query.startswith("up{job="):
        return 200, vector(1)
    if mode.startswith("setup") and final:
        return 200, vector(2)
    if "otelcol_receiver_refused" in query:
        return 200, vector(0)
    return 200, vector(None if (empty and final) else value)

class H(BaseHTTPRequestHandler):
    def _do(self):
        role = roles.get(self.server.server_port, "?")
        url = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(url.query).get("query", [""])[0]
        qtime = urllib.parse.parse_qs(url.query).get("time", [""])[0]
        log.write("%d %s %s\n" % (self.server.server_port, self.command, self.path))
        status, body = 200, {}
        if mode in ("plain", "plain-post-fails"):
            if self.command == "POST":
                status = 500
        elif role == "registry":
            status = 409 if url.path.endswith("/admin/usage/summary") else 200
        elif role == "prometheus":
            if url.path.endswith("/api/v1/query"):
                status, body = prom_answer(query, mode in ("direct503", "both503"), mode == "empty-both", 1, qtime)
        elif role == "grafana":
            if url.path.startswith("/api/dashboards/uid/"):
                # the canonical dashboard as Grafana reports it: $DASH_MODE is ok (provisioned), unprovisioned, missing (404),
                # error (500) or garbage (200 with a body that is not a dashboard)
                dm = os.environ.get("DASH_MODE", "ok")
                uid = url.path.rsplit("/", 1)[-1]
                if dm == "missing":
                    status, body = 404, {"message": "Dashboard not found"}
                elif dm == "error":
                    status, body = 500, {"message": "internal error"}
                elif dm == "garbage":
                    status, body = 200, {"unexpected": True}
                else:
                    status, body = 200, {"meta": {"provisioned": dm == "ok"}, "dashboard": {"uid": uid}}
            elif "/health" in url.path and "datasources" in url.path:
                body = {"status": "OK"}
            elif url.path.endswith("/api/v1/query"):
                status, body = prom_answer(query, mode in ("grafana503", "both503"), mode == "empty-both", 2 if mode == "mismatch" else 1, qtime)
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)
    def do_GET(self): self._do()
    def do_HEAD(self): self._do()
    def do_POST(self): self._do()
    def log_message(self, *a): pass

class Server(ThreadingHTTPServer):
    # HTTPServer.server_bind asks the resolver for the host's name (socket.getfqdn), which takes about nine
    # seconds per server on some machines; the listening socket only exists after that. Skip the lookup.
    def server_bind(self):
        socketserver.TCPServer.server_bind(self)
        self.server_name = "127.0.0.1"
        self.server_port = self.socket.getsockname()[1]

servers = [Server(("127.0.0.1", p), H) for p in ports]
# Every socket is bound and listening: tell the starter, with this process's own pid, so that an answer from some other
# listener on the same ports cannot be taken for this one
if os.environ.get("SERVER_READY"):
    with open(os.environ["SERVER_READY"] + ".tmp", "w") as f:
        f.write(str(os.getpid()))
    os.replace(os.environ["SERVER_READY"] + ".tmp", os.environ["SERVER_READY"])
for s in servers[1:]:
    threading.Thread(target=s.serve_forever, daemon=True).start()
servers[0].serve_forever()
PYEOF
}
# start_server <log> <mode> <ports...>: a throwaway HTTP backend on 127.0.0.1 (stopped by cleanup). It returns only when
# the process it started has bound every port and said so (its own pid in <log>.ready, written after the binds) and is
# still alive. An answer over HTTP proves nothing: another listener may already hold a port. A bind failure ends the test
# with the process's own diagnostics, before any scenario sends a request.
start_server() {
  local logfile=$1 mode=$2 pid i=0
  shift 2
  command -v python3 > /dev/null 2>&1 || die "python3 is required to run the throwaway HTTP backends of this test; it was not found in PATH"
  : > "$logfile"
  rm -f "$logfile.ready"
  rm -f "$logfile.hb"
  SERVER_READY=$logfile.ready HB_LOG=$logfile.hb python3 -I "$TMP/server.py" "$logfile" "$mode" "$@" 2> "$logfile.err" &
  pid=$!
  OWN_PIDS="$OWN_PIDS $pid"
  while [ "$(cat "$logfile.ready" 2> /dev/null)" != "$pid" ]; do
    alive "$pid" || die "the test backend for ports $* exited before it was ready (its error: $(tail -n 2 "$logfile.err" | tr '\n' ' ')). Something else may be listening on one of them; nothing was sent"
    i=$((i + 1))
    [ "$i" -lt 300 ] || die "the test backend for ports $* did not report that it was listening within 30s"
    sleep 0.1
  done
  alive "$pid" || die "the test backend for ports $* is not running although it reported ready"
}

# --- failed-setup ------------------------------------------------------------------------------------

case_failed_setup() {
  part "failed-setup: when setup is refused, nothing is written to whatever occupies the ports"
  write_server
  start_server "$TMP/occupied.log" plain 59941 59942 59943 59944
  local envs=(TEST_REGISTRY_PORT=59941 TEST_COLLECTOR_HEALTH_PORT=59942 TEST_PROMETHEUS_PORT=59943 TEST_GRAFANA_PORT=59944)
  local c p
  for p in 59941 59942 59943 59944; do
    check "control: port $p is really occupied (a TCP connect succeeds)" yes "$( (exec 3<> "/dev/tcp/127.0.0.1/$p") 2> /dev/null && echo yes || echo no)"
  done
  for c in alternate-ports sealed-history collector-outage registry-restart teardown series-limit; do
    : > "$TMP/occupied.log"
    harness "refused-$c" "${envs[@]}" -- "$c"
    check "$c: setup refused the occupied ports, so the run fails" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
    check "  the refusal is reported" yes "$(in_out 'already in use')"
    check "  no POST (no schema, no artifact) reached the occupied ports" 0 "$(grep -c ' POST ' "$TMP/occupied.log" || true)"
    check "  no request that creates anything of any kind either" 0 "$(grep -c -E -v ' (GET|HEAD) ' "$TMP/occupied.log" || true)"
    check "  and the owned cleanup still ran once" 1 "$(down_calls)"
  done
  : > "$TMP/occupied.log"
  harness demo-abort "${envs[@]}" SELFTEST_MARKER="$TMP/continued" -- selftest-demo-abort
  check "a demo that did not produce valid state stops the run" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  and the scenario did not continue after it" no "$([ -e "$TMP/continued" ] && echo yes || echo no)"
}

# --- verify-failures ---------------------------------------------------------------------------------

case_verify_failures() {
  part "verify-failures: a failed query is never equal to another failed query"
  write_server
  local mode rc out
  for mode in ok direct503 grafana503 both503 empty-both mismatch; do
    start_server "$TMP/vf-$mode.log" "$mode" 59921 59922 59923 59924
    out=$TMP/vf-$mode.out
    rc=0
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59921 COLLECTOR_HEALTH_PORT=59922 PROMETHEUS_PORT=59923 GRAFANA_PORT=59924 \
      ./verify.sh --stack-only > "$out" 2>&1 || rc=$?
    case $mode in
      ok)
        check "control: a healthy backend passes verify --stack-only" "0 yes" "$rc $(grep -q 'All checks passed' "$out" && echo yes || echo no)" ;;
      direct503)
        check "Prometheus failing the final query: verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says Prometheus's query failed" yes "$(grep -q 'Prometheus query failed' "$out" && echo yes || echo no)" ;;
      grafana503)
        check "Grafana failing the final query: verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says Grafana's query failed" yes "$(grep -q "Grafana's query failed" "$out" && echo yes || echo no)" ;;
      both503)
        check "both failing the final query: verification fails, not equal-by-error" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says so for both" yes "$(grep -q 'Prometheus query failed' "$out" && grep -q "Grafana's query failed" "$out" && echo yes || echo no)"
        check "  and never prints an ok line for the comparison" no "$(grep -q 'ok .*returns what Prometheus returns' "$out" && echo yes || echo no)" ;;
      empty-both)
        check "both answering with no data: verification fails, 'absent' is not a value" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)" ;;
      mismatch)
        check "different answers from the two: verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)" ;;
    esac
    kill "${OWN_PIDS##* }" 2> /dev/null || true
    sleep 1
  done
}

# --- inventory-status --------------------------------------------------------------------------------

case_inventory_status() {
  part "inventory-status: a failed or hung inventory query is a failed check, whatever it was compared with"
  local flag=$FAKE_FLAG_DIR/ps
  rm -f "$flag"
  harness inv-ok SELFTEST_FLAG="$flag" SELFTEST_INVENTORY_AFTER=none -- selftest-inventory
  check "control: an unchanged (empty) inventory before and after passes" "0 yes" "$H_RC $(in_out 'All checks passed.')"
  rm -f "$flag"
  harness inv-fail SELFTEST_FLAG="$flag" SELFTEST_INVENTORY_AFTER=9 -- selftest-inventory
  check "an empty inventory before and a failing query after is a failure" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  reported as a failed query, not as unchanged" yes "$(in_out 'inventory query failed')"
  check "  and not as success" no "$(in_out 'All checks passed.')"
  rm -f "$flag"
  harness inv-hang PROBE_TIMEOUT=2 SELFTEST_FLAG="$flag" SELFTEST_INVENTORY_AFTER=stall -- selftest-inventory
  check "a hung query after is a failure too" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  reported as timed out" yes "$(in_out 'timed out')"
  # shellcheck disable=SC2046
  check "  with no process left behind" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
}


# --- outer-cancel ------------------------------------------------------------------------------------

# fake_hung: the fake runtime (or a script of this test) has started its provider and hung process
fake_hung() { [ "$(wc -l < "$FAKE_PIDS" | tr -d ' ')" -ge 2 ]; }

# cancel_check <name> <signal> <expected status> [ENV=value ...] -- <command...>
# Runs the command as an entry point of its own (job control, so it can receive INT as a terminal would send it),
# waits until it has started its hung process, signals ONLY that entry-point shell, and checks that it leaves with the
# conventional status, quickly, that every process the fake runtime or the script started is gone, and that an
# unrelated sibling process of this test is untouched.
cancel_check() {
  local name=$1 sig=$2 want=$3
  shift 3
  local envs=()
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  mkdir -p "$TMP/oc"
  : > "$FAKE_PIDS"
  sleep 120 &
  local sib=$! h rc=0 t0=$SECONDS i=0
  OWN_PIDS="$OWN_PIDS $sib"
  set -m
  env ${envs[@]+"${envs[@]}"} "$@" > "$TMP/oc/$name.out" 2>&1 &
  h=$!
  set +m
  retry_until 30 fake_hung || true
  t0=$SECONDS
  kill "-$sig" "$h" 2> /dev/null || true
  while alive "$h" && [ "$i" -lt 100 ]; do sleep 0.1; i=$((i + 1)); done
  if alive "$h"; then rc=still-running; kill -9 "$h" 2> /dev/null || true; else wait "$h" 2> /dev/null || rc=$?; fi
  check "$name: $sig to the entry-point shell makes it leave with status $want" "$want" "$rc"
  check "  within 8 seconds" yes "$([ $((SECONDS - t0)) -le 8 ] && echo yes || echo no)"
  # shellcheck disable=SC2046
  check "  and every process it had started is gone" yes "$([ -s "$FAKE_PIDS" ] && all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  check "  and an unrelated sibling process is untouched" yes "$(alive "$sib" && echo yes || echo no)"
  kill "$sib" 2> /dev/null || true
  # shellcheck disable=SC2046
  kill $(cat "$FAKE_PIDS" 2> /dev/null) 2> /dev/null || true
}

case_outer_cancel() {
  part "outer-cancel: a signal sent to the entry-point script reaches the commands it supervises, on every call path"
  mkdir -p "$TMP/oc" "$TMP/oc-state"
  cat > "$TMP/oc/live.sh" << 'SH'
#!/usr/bin/env bash
# a live leader with a child that holds its stdout
sleep 61 &
echo $! >> "$FAKE_PIDS"
echo $$ >> "$FAKE_PIDS"
echo live-output
sleep 62
SH
  cat > "$TMP/oc/early.sh" << 'SH'
#!/usr/bin/env bash
# a leader that exits at once, leaving a child that holds its stdout
sleep 61 &
echo $! >> "$FAKE_PIDS"
echo $$ >> "$FAKE_PIDS"
echo early-output
exit 0
SH
  chmod +x "$TMP/oc/live.sh" "$TMP/oc/early.sh"
  local sig want
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    # the runtime query path of the library: service_container -> compose ps -> a provider -> a hung process
    cancel_check "lib-query-$sig" "$sig" "$want" FAKE_STALL=compose-ps -- \
      bash -c '. ./lib.sh; detect_runtime; compose_cmd; service_container registry; echo done'
    # the real entry points
    cancel_check "setup-$sig" "$sig" "$want" FAKE_STALL=compose-ps LOCAL_DIR="$TMP/oc-state" REGISTRY_IMAGE=dummy -- ./setup.sh
    cancel_check "teardown-$sig" "$sig" "$want" FAKE_STALL=volume LOCAL_DIR="$TMP/oc-state" -- ./teardown.sh
    # supervised capture of a command whose leader is alive, and of one whose leader exited first
    # shellcheck disable=SC2016 # the inner shell expands them
    cancel_check "capture-live-$sig" "$sig" "$want" -- \
      bash -c '. ./lib.sh; rc=0; bcap 30 "$1" || rc=$?; echo "rc=$rc out=[$BCAP_OUT]"' _ "$TMP/oc/live.sh"
    # shellcheck disable=SC2016 # the inner shell expands them
    cancel_check "capture-early-$sig" "$sig" "$want" -- \
      bash -c '. ./lib.sh; rc=0; bcap 30 "$1" || rc=$?; echo "rc=$rc out=[$BCAP_OUT]"' _ "$TMP/oc/early.sh"
  done

  # deadlines, with the output the command wrote kept
  local out rc started
  for kind in live early; do
    : > "$FAKE_PIDS"
    rc=0; started=$SECONDS
    out=$(bash -c '. ./lib.sh; rc=0; bcap 3 "$1" || rc=$?; echo "rc=$rc out=[$BCAP_OUT]"' _ "$TMP/oc/$kind.sh" 2> /dev/null) || rc=$?
    check "capture-$kind deadline: the entry point finishes normally and reports a timeout with the output so far" "rc=124 out=[$kind-output]" "$out"
    check "  within the bound" yes "$([ $((SECONDS - started)) -le 9 ] && echo yes || echo no)"
    # shellcheck disable=SC2046
    check "  and its child is gone" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  done

  # the old pattern is refused, not silently unsafe
  rc=0
  out=$(bash -c '. ./lib.sh; value=$(run_bounded 5 echo hi); echo "status=$? value=[$value]"' 2> "$TMP/oc/guard.err") || rc=$?
  check "run_bounded inside a command substitution is refused with status 125 and runs nothing" "status=125 value=[]" "$out"
  check "  and the refusal says why" yes "$(grep -q 'entry-point shell' "$TMP/oc/guard.err" && echo yes || echo no)"
  out=$(bash -c '. ./lib.sh; run_bounded 5 echo hi | cat; echo "status=${PIPESTATUS[0]}"' 2> /dev/null) || true
  check "  also inside a pipeline" "status=125" "$out"
}

# --- path-spelling -----------------------------------------------------------------------------------

case_path_spelling() {
  part "path-spelling: a linked state path is refused however it is spelled, and ordinary temp paths still work"
  local real=$TMP/real-base link=$TMP/link-base phys=$TMP/phys v before after rc n variants=()
  mkdir -p "$real" "$phys"
  LOCAL_DIR=$real COMPOSE_PROJECT_NAME=proj-a bash -c ". ./lib.sh; ensure_local_dir"
  printf '{"schemaVersion":2,"project":"proj-a"}\n' > "$real/projects/proj-a/run-state.json"
  printf '{"schemaVersion":2,"project":"proj-a","runId":"r1"}\n' > "$real/projects/proj-a/runs/r1.json"
  ln -s "$real" "$link"
  ln -s "$real" "$phys/lk"
  variants=("$link" "$link/" "$link//" "$link/." "${link%/*}//${link##*/}" "$link/./" "$phys/lk/" "$phys/lk//")
  before=$(cd "$real" && find . | LC_ALL=C sort | cksum; cd "$real/projects/proj-a" && cksum run-state.json runs/r1.json)
  n=0
  for v in "${variants[@]}"; do
    n=$((n + 1))
    : > "$FAKE_LOG"
    rc=0
    LOCAL_DIR=$v ./teardown.sh --project proj-a --purge > "$TMP/ps-$n.out" 2>&1 || rc=$?
    after=$(cd "$real" && find . | LC_ALL=C sort | cksum; cd "$real/projects/proj-a" && cksum run-state.json runs/r1.json 2>&1 || echo missing)
    check "purge with LOCAL_DIR=$v is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  the marker-owned target is untouched" "$before" "$after"
    check "  nothing was purged" 0 "$(grep -c ' down ' "$FAKE_LOG" || true)"
    check "  and the refusal names the link" yes "$(grep -q 'symbolic link' "$TMP/ps-$n.out" && echo yes || echo no)"
  done
  # a linked component below the base, with a path that does not exist yet: nothing may be created through it
  for v in "$phys/lk/inner" "$phys/lk/inner/deeper"; do
    rc=0
    LOCAL_DIR=$v COMPOSE_PROJECT_NAME=proj-a bash -c ". ./lib.sh; ensure_local_dir" > "$TMP/ps-inner.out" 2>&1 || rc=$?
    check "creating state below the linked component $v is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  and nothing was created behind the link" no "$([ -e "$real/inner" ] && echo yes || echo no)"
  done
  # demo.sh writes state, so it must refuse before it sends anything
  for v in "$link/" "$link/."; do
    rc=0
    LOCAL_DIR=$v REGISTRY_PORT=59799 ./demo.sh > "$TMP/ps-demo.out" 2>&1 || rc=$?
    after=$(cd "$real" && find . | LC_ALL=C sort | cksum; cd "$real/projects/proj-a" && cksum run-state.json runs/r1.json 2>&1 || echo missing)
    check "demo.sh with LOCAL_DIR=$v refuses and leaves the target alone" "yes $before" "$([ "$rc" -ne 0 ] && echo yes || echo no) $after"
  done
  rc=0
  LOCAL_DIR=$real/../real-base ./teardown.sh --project proj-a --purge > "$TMP/ps-dots.out" 2>&1 || rc=$?
  check "a path with a .. component is refused (its target cannot be told from the spelling)" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"

  # ordinary temp paths keep working: $TMPDIR (a /var alias on macOS) and a path under /tmp (an alias on macOS)
  local viatmp
  viatmp=$(mktemp -d /tmp/usage-telemetry-otel-spelling.XXXXXX)
  for v in "$TMP/ok-base" "$viatmp/base" "$viatmp/base/"; do
    rc=0
    LOCAL_DIR=$v COMPOSE_PROJECT_NAME=proj-a bash -c ". ./lib.sh; ensure_local_dir" > "$TMP/ps-ok.out" 2>&1 || rc=$?
    check "an ordinary path, $v, is accepted" 0 "$rc"
  done
  rm -rf "$viatmp"
  # the default base is spelled physically, so a checkout reached through a link is not mistaken for a linked state path
  ln -s "$PWD" "$TMP/linked-checkout"
  out=$(cd "$TMP/linked-checkout" && bash -c '. ./lib.sh; printf "%s" "$LOCAL_DIR"')
  check "the default LOCAL_DIR of a checkout reached through a symbolic link is its physical path" "$(cd "$PWD" && pwd -P)/.local" "$out"
}

# --- keep-and-ports ----------------------------------------------------------------------------------

case_keep_and_ports() {
  part "keep-and-ports: failed queries are unknown, never zero, in the --keep summary and in the published-port check"
  local flags=$FAKE_FLAG_DIR mode
  mkdir -p "$flags"
  # the summary at the end of a --keep run
  rm -f "$flags"/*
  harness keep-ok SELFTEST_FAIL=none -- --keep selftest-keep-queries
  check "control: with both inventories answering, --keep ends successfully and says what is left" "0 yes yes" \
    "$H_RC $(in_out '0 container(s) and 0 volume(s)') $(in_out 'All checks passed.')"
  for mode in containers volumes both volumes-hang; do
    rm -f "$flags"/*
    local penv=()
    [ "$mode" = volumes-hang ] && penv=(PROBE_TIMEOUT=2)
    harness "keep-$mode" SELFTEST_FAIL=$mode ${penv[@]+"${penv[@]}"} -- --keep selftest-keep-queries
    check "--keep with the $mode inventory query failing: the suite fails" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
    check "  the summary says the state is unknown" yes "$(in_out 'UNKNOWN')"
    check "  it never prints a count it did not get" no "$(in_out ' 0 volume(s)')"
    check "  and never claims success" no "$(in_out 'All checks passed.')"
    check "  --keep is honored: nothing was cleaned up" 0 "$(down_calls)"
  done
  rm -f "$flags"/*

  # the published-port check
  for mode in empty published lookup-fails no-container port-fails port-hangs; do
    rm -f "$flags"/*
    local penv=()
    [ "$mode" = port-hangs ] && penv=(PROBE_TIMEOUT=2)
    case $mode in
      published) harness "ports-$mode" SELFTEST_PORTS=$mode FAKE_PORT_OUT=$'4318/tcp -> 127.0.0.1:41234\n' -- selftest-ports ;;
      port-fails) harness "ports-$mode" SELFTEST_PORTS=$mode FAKE_PORT_RC=7 -- selftest-ports ;;
      *) harness "ports-$mode" SELFTEST_PORTS=$mode ${penv[@]+"${penv[@]}"} -- selftest-ports ;;
    esac
    case $mode in
      empty)
        check "a successful empty port mapping counts as 0 published, and passes" "0 yes" "$H_RC $(in_out 'All checks passed.')" ;;
      published)
        check "a published 4318 is counted and fails the check" yes "$([ "$H_RC" -ne 0 ] && in_out "got '1'")" ;;
      lookup-fails)
        check "a failed container lookup is unknown: the check fails" yes "$([ "$H_RC" -ne 0 ] && in_out 'could not find the container')" ;;
      no-container)
        check "an empty container ID is unknown: the check fails" yes "$([ "$H_RC" -ne 0 ] && in_out 'could not find the container')" ;;
      port-fails)
        check "a failed port query is unknown: the check fails" yes "$([ "$H_RC" -ne 0 ] && in_out 'port query failed')" ;;
      port-hangs)
        check "a hung port query is unknown: the check fails as timed out" yes "$([ "$H_RC" -ne 0 ] && in_out 'timed out')" ;;
    esac
    check "  and none of them is reported as success" "$([ "$mode" = empty ] && echo yes || echo no)" "$(in_out 'All checks passed.')"
  done
  rm -f "$flags"/*
}

# --- runtime-probe -----------------------------------------------------------------------------------

case_runtime_probe() {
  part "runtime-probe: the Compose version banner counts only when the version command itself succeeded"
  local rc out started
  rc=0
  out=$(bash -c '. ./lib.sh; detect_runtime; echo detected' 2>&1) || rc=$?
  check "control: a successful v2 banner is accepted" "0 detected" "$rc $(printf '%s' "$out" | tail -1)"
  rc=0
  out=$(FAKE_VERSION_RC=7 bash -c '. ./lib.sh; detect_runtime; echo detected' 2>&1) || rc=$?
  check "a valid v2 banner with exit status 7 is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and the status is named" yes "$(printf '%s' "$out" | grep -q 'failed with status 7' && echo yes || echo no)"
  check "  and detection is not reported as done" no "$(printf '%s' "$out" | grep -q '^detected' && echo yes || echo no)"
  : > "$FAKE_PIDS"
  rc=0
  started=$SECONDS
  out=$(FAKE_STALL=compose-version DETECT_TIMEOUT=2 bash -c '. ./lib.sh; detect_runtime; echo detected' 2>&1) || rc=$?
  check "a version command that hangs is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  as timed out" yes "$(printf '%s' "$out" | grep -q 'timed out after 2s' && echo yes || echo no)"
  check "  within the bound" yes "$([ $((SECONDS - started)) -le 10 ] && echo yes || echo no)"
  # shellcheck disable=SC2046
  check "  and nothing it started is left" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  rc=0
  out=$(FAKE_VERSION_OUT='podman-compose version 1.0.6' bash -c '. ./lib.sh; detect_runtime; echo detected' 2>&1) || rc=$?
  check "podman-compose is still refused with its own explanation" yes "$([ "$rc" -ne 0 ] && printf '%s' "$out" | grep -q 'podman-compose' && echo yes || echo no)"
}

# --- selector-types ----------------------------------------------------------------------------------

case_selector_types() {
  part "selector-types: a run's series are selected by (identifier type, value) pairs, never by bare numbers"
  local state='{"schemas":[{"globalId":5,"contentId":10},{"globalId":6,"contentId":11},{"globalId":7,"contentId":10}],"notCounted":[{"request":"GET /ids/globalIds/5/references"},{"request":"GET /ids/globalIds/9000000000000000"},{"request":"GET /ids/contentIds/9000000000000001"}]}'
  local pairs sel rc bad
  pairs=$(identifier_pairs <<< "$state" | tr '\n' ' ')
  check "the pairs of a run: schemas' global and content IDs, and the IDs of the requests that must not be counted, typed" \
    "content_id:10 content_id:11 content_id:9000000000000001 global_id:5 global_id:6 global_id:7 global_id:9000000000000000 " "$pairs"
  sel=$(usage_selector_for_run inst-1 <<< "$state")
  local g='apicurio_usage_schema_fetches_total{service_instance_id="inst-1",apicurio_registry_identifier_type="global_id",apicurio_registry_identifier_value=~"5|6|7|9000000000000000"}'
  local c='apicurio_usage_schema_fetches_total{service_instance_id="inst-1",apicurio_registry_identifier_type="content_id",apicurio_registry_identifier_value=~"10|11|9000000000000001"}'
  check "the selector is one typed selector per identifier type, joined with or" "$g or $c" "$sel"
  check "the same number as a global ID and as a content ID stays two identifiers" "content_id:7 global_id:7 " \
    "$(identifier_pairs <<< '{"schemas":[{"globalId":7,"contentId":7}],"notCounted":[]}' | tr '\n' ' ')"
  for bad in 'global_id:1|2' 'bogus:1' 'global_id:' 'content_id:x'; do
    rc=0
    sel=$(usage_selector inst-1 "$bad" 2> /dev/null) || rc=$?
    check "the identifier '$bad' is refused (status 2) and no selector is printed" "2 []" "$rc [$sel]"
  done
  rc=0
  sel=$(usage_selector inst-1 2> /dev/null) || rc=$?
  check "no identifier at all is refused too" "2 []" "$rc [$sel]"

  # verify.sh against a controlled Prometheus that applies the selector's label matchers to a fixed set of series
  mkdir -p "$TMP/skew"
  cat > "$TMP/skew/state.json" << 'JSON'
{"schemaVersion":2,"project":"usage-telemetry-otel","runId":"run-2","source":{"label":"service_instance_id","instanceId":"inst-1"},
 "evaluationTime":"1795129704.514","settledAt":"2026-01-01T00:00:00Z",
 "schemas":[{"globalId":5,"contentId":10},{"globalId":6,"contentId":11},{"globalId":7,"contentId":10}],
 "notCounted":[{"request":"GET /ids/globalIds/5/references","identifierValue":"5"},{"request":"GET /ids/globalIds/9000000000000000","identifierValue":"9000000000000000"},{"request":"GET /ids/contentIds/9000000000000001","identifierValue":"9000000000000001"}],
 "expectedSeries":[
  {"clientId":"orders-service","identifierType":"global_id","identifierValue":"5","operation":"SERIALIZE","count":3},
  {"clientId":"orders-service","identifierType":"content_id","identifierValue":"10","operation":"DESERIALIZE","count":2},
  {"clientId":"billing-service","identifierType":"global_id","identifierValue":"7","operation":"UNKNOWN","count":1}]}
JSON
  local base='{"client":"orders-service","type":"global_id","value":"5","operation":"SERIALIZE","count":3},{"client":"orders-service","type":"content_id","value":"10","operation":"DESERIALIZE","count":2},{"client":"billing-service","type":"global_id","value":"7","operation":"UNKNOWN","count":1}'
  printf '[%s]\n' "$base" > "$TMP/skew/series.json"
  write_server
  SERIES_FILE=$TMP/skew/series.json start_server "$TMP/skew/server.log" series 59931 59932 59933 59934
  local name extra want
  for name in exact leaked-content-7 other-process intruder counted-negative; do
    case $name in
      exact) extra=""; want=pass ;;
      leaked-content-7) extra=',{"client":"orders-service","type":"content_id","value":"7","operation":"DESERIALIZE","count":2}'; want=pass ;;
      other-process) extra=',{"instance":"other-inst","client":"orders-service","type":"global_id","value":"5","operation":"SERIALIZE","count":9}'; want=pass ;;
      intruder) extra=',{"client":"intruder","type":"global_id","value":"5","operation":"SERIALIZE","count":1}'; want=fail ;;
      counted-negative) extra=',{"client":"orders-service","type":"content_id","value":"9000000000000001","operation":"SERIALIZE","count":1}'; want=fail ;;
    esac
    printf '[%s%s]\n' "$base" "$extra" > "$TMP/skew/series.json"
    rc=0
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      ./verify.sh --state "$TMP/skew/state.json" > "$TMP/skew/$name.out" 2>&1 || rc=$?
    case $want in
      pass) check "verify.sh with $name: the run still verifies (exit status 0)" 0 "$rc" ;;
      fail)
        check "verify.sh with $name: the run does not verify" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and the diff shows the unexpected series" yes "$(grep -q -E 'intruder|9000000000000001' "$TMP/skew/$name.out" && echo yes || echo no)"
        ;;
    esac
  done
  kill "${OWN_PIDS##* }" 2> /dev/null || true
  sleep 1
}

# --- setup-freshness ---------------------------------------------------------------------------------

case_setup_freshness() {
  part "setup-freshness: setup waits for a NEW export; an old heartbeat retained from a previous process is not readiness"
  write_server
  mkdir -p "$TMP/sf"
  local mode rc began waited out limit
  for mode in setup-stale setup-advstale setup-hb503 setup-late-2 setup-fresh; do
    start_server "$TMP/sf/$mode.log" "$mode" 59911 59912 59913 59914
    limit=30
    case $mode in setup-stale | setup-advstale | setup-hb503) limit=6 ;; esac
    out=$TMP/sf/$mode.out
    rc=0
    began=$SECONDS
    FAKE_COMPOSE_PS_OUT=$'registry\notel-collector\nprometheus\ngrafana\n' LOCAL_DIR=$TMP/sf/state REGISTRY_IMAGE=dummy READY_TIMEOUT=$limit \
      ./setup.sh --registry-port 59911 --collector-health-port 59912 --prometheus-port 59913 --grafana-port 59914 > "$out" 2>&1 || rc=$?
    waited=$((SECONDS - began))
    case $mode in
      setup-stale)
        check "a heartbeat 120s old that never advances: setup.sh fails instead of reporting export-ready" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  after waiting for a new export for the whole ${limit}s" yes "$([ "$waited" -ge "$limit" ] && echo yes || echo no)"
        check "  naming the missing export" yes "$(grep -q 'a new metric export from the Registry.* was not ready after 6s' "$out" && echo yes || echo no)"
        check "  and never printing it as ready" no "$(grep -q 'ok    ready: a new metric export' "$out" && echo yes || echo no)"
        check "  the fixture served only old answers" 0 "$(grep -c fresh "$TMP/sf/$mode.log.hb" || true)"
        ;;
      setup-advstale)
        check "a backlog that advances (120s old, then 90s old) but stays older than the limit: setup.sh fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  after waiting for a fresh export for the whole ${limit}s" yes "$([ "$waited" -ge "$limit" ] && echo yes || echo no)"
        check "  and never printing it as ready" no "$(grep -q 'ok    ready: a new metric export' "$out" && echo yes || echo no)"
        check "  the fixture served only old answers" 0 "$(grep -c fresh "$TMP/sf/$mode.log.hb" || true)"
        ;;
      setup-hb503)
        check "a heartbeat query that fails: setup.sh fails, the export is unknown and not assumed" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and never printing it as ready" no "$(grep -q 'ok    ready: a new metric export' "$out" && echo yes || echo no)"
        check "  naming what it was waiting for" yes "$(grep -q 'was not ready after 6s' "$out" && echo yes || echo no)"
        ;;
      setup-late-2)
        check "an old heartbeat whose third answer is fresh: setup.sh passes" 0 "$rc"
        check "  only after the old answers, in this order: baseline, one old poll, then the fresh export" "stale stale fresh" "$(tr '\n' ' ' < "$TMP/sf/$mode.log.hb" | sed 's/ $//')"
        check "  and says the new export arrived" yes "$(grep -q 'ok    ready: a new metric export' "$out" && echo yes || echo no)"
        ;;
      setup-fresh)
        check "a Registry that keeps exporting (setup.sh run again on a running stack): passes" 0 "$rc"
        check "  by waiting for a later export" yes "$(grep -q 'ok    ready: a new metric export' "$out" && echo yes || echo no)"
        ;;
    esac
    kill "${OWN_PIDS##* }" 2> /dev/null || true
    sleep 1
  done
}

# --- outage-recovery ---------------------------------------------------------------------------------

case_outage_recovery() {
  part "outage-recovery: recovery needs the exact count AND fresh ongoing exports of the same process"
  write_server
  mkdir -p "$TMP/or/slow"
  # An HTTP client that takes 2s over every heartbeat query: what a loaded machine or a slow link looks like
  printf '#!/bin/sh\ncase " $* " in *timestamp*) sleep 2 ;; esac\nexec %s "$@"\n' "$(command -v curl)" > "$TMP/or/slow/curl"
  chmod +x "$TMP/or/slow/curl"
  local variant mode log hb penv began
  local tport=(TEST_REGISTRY_PORT=59901 TEST_COLLECTOR_HEALTH_PORT=59902 TEST_PROMETHEUS_PORT=59903 TEST_GRAFANA_PORT=59904)
  for variant in normal slow; do
    for mode in recover-fresh recover-late-3 recover-stale; do
      log=$TMP/or/$variant-$mode.log
      hb=$log.hb
      penv=()
      [ "$variant" != slow ] || penv=(PATH="$TMP/or/slow:$PATH")
      start_server "$log" "$mode" 59901 59902 59903 59904
      # What decides each verdict is the order of the answers the fixture gave, never how many queries fit in a time
      case $mode in
        recover-fresh)
          harness "$variant-$mode" "${tport[@]}" RECOVERY_TIMEOUT=30 ${penv[@]+"${penv[@]}"} -- selftest-recovery
          check "$variant: exact count and a fresh export: recovered" "0 yes" "$H_RC $(in_out 'ok    recovered after')"
          check "  on the first attempt: the fixture gave exactly one answer, fresh" "fresh" "$(tr '\n' ' ' < "$hb" | sed 's/ $//')"
          ;;
        recover-late-3)
          harness "$variant-$mode" "${tport[@]}" RECOVERY_TIMEOUT=60 ${penv[@]+"${penv[@]}"} -- selftest-recovery
          check "$variant: exact count, old for three answers and then fresh: recovered" "0 yes" "$H_RC $(in_out 'ok    recovered after')"
          check "  and only at the first fresh answer, after the three old ones" "stale stale stale fresh" "$(tr '\n' ' ' < "$hb" | sed 's/ $//')"
          ;;
        recover-stale)
          began=$SECONDS
          harness "$variant-$mode" "${tport[@]}" RECOVERY_TIMEOUT=4 ${penv[@]+"${penv[@]}"} -- selftest-recovery
          check "  and it ended: bounded, well within 40s although the limit is 4s and every query takes up to 2s" yes "$([ $((SECONDS - began)) -le 40 ] && echo yes || echo no)"
          check "$variant: exact count, but the newest export stays old for good: the run fails" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
          check "  saying the export is old although the count is right" yes "$(in_out 'the count is 5, but the newest export of process x is')"
          check "  after at least one answer, none of them fresh" "0 yes" "$(grep -c fresh "$hb" || true) $([ -s "$hb" ] && echo yes || echo no)"
          check "  and not claiming success" no "$(in_out 'ok    recovered after')"
          ;;
      esac
      kill "${OWN_PIDS##* }" 2> /dev/null || true
      sleep 1
    done
  done
}

# --- atomic-publication ------------------------------------------------------------------------------

case_atomic_publication() {
  part "atomic-publication: write_atomically publishes only to an absent or ordinary file, and never through a directory or link"
  local d=$TMP/ap rc=0
  mkdir -p "$d/ext-dir"
  printf 'external data\n' > "$d/ext-dir/keep.txt"
  printf 'external file\n' > "$d/ext-file.txt"
  put() {
    rc=0
    printf '%s\n' "$2" | bash -c '. ./lib.sh; write_atomically "$1"' _ "$1" > "$d/put.out" 2>&1 || rc=$?
  }
  put "$d/absent.json" '{"v":1}'
  check "an absent destination: written" '0 {"v":1}' "$rc $(cat "$d/absent.json")"
  put "$d/absent.json" '{"v":2}'
  check "an existing ordinary file: replaced" '0 {"v":2}' "$rc $(cat "$d/absent.json")"
  check "  with no temporary file left" 0 "$(find "$d" -maxdepth 1 -name '.tmp.*' | wc -l | tr -d ' ')"

  mkdir -p "$d/a-dir"
  put "$d/a-dir" '{"v":3}'
  check "a directory as the destination: refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  nothing was put inside it" 0 "$(find "$d/a-dir" -mindepth 1 | wc -l | tr -d ' ')"
  check "  and it says so" yes "$(grep -q 'not an ordinary file' "$d/put.out" && echo yes || echo no)"

  ln -s "$d/ext-dir" "$d/link-to-dir"
  put "$d/link-to-dir" '{"v":4}'
  check "a symbolic link to an external directory: refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  the external directory holds only what it held" "keep.txt" "$(find "$d/ext-dir" -mindepth 1 -exec basename {} \; | tr '\n' ' ' | sed 's/ $//')"
  check "  and the link itself is intact" "$d/ext-dir" "$(readlink "$d/link-to-dir")"

  ln -s "$d/ext-file.txt" "$d/link-to-file"
  put "$d/link-to-file" '{"v":5}'
  check "a symbolic link to an external file: refused, the file untouched" "yes external file" "$([ "$rc" -ne 0 ] && echo yes || echo no) $(cat "$d/ext-file.txt")"

  ln -s "$d/no-such-target" "$d/broken-link"
  put "$d/broken-link" '{"v":6}'
  check "a broken symbolic link: refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and its target was not created" no "$([ -e "$d/no-such-target" ] && echo yes || echo no)"
  check "  nor was a temporary file left in the directory" 0 "$(find "$d" -maxdepth 1 -name '.tmp.*' | wc -l | tr -d ' ')"

  # A failure while publishing keeps the old valid data: the directory cannot take the temporary file
  mkdir -p "$d/locked"
  printf '{"v":"old"}\n' > "$d/locked/state.json"
  if [ "$(id -u)" -ne 0 ]; then
    chmod 555 "$d/locked"
    put "$d/locked/state.json" '{"v":"new"}'
    chmod 755 "$d/locked"
    check "a write that cannot complete: reported as failed" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  and the old valid state is intact" '{"v":"old"}' "$(cat "$d/locked/state.json")"
  else
    info "  skip  running as root: a read-only directory does not stop the write"
  fi
  check "the external data was never touched by any of the above" "external data" "$(cat "$d/ext-dir/keep.txt")"
  unset -f put
}

# --- launch-trap -------------------------------------------------------------------------------------

case_launch_trap() {
  part "launch-trap: a signal that arrives while the new command's group is being recorded still ends it"
  local d=$TMP/lt rc leader child h sib lp
  mkdir -p "$d/bin" "$d/barrier"
  cat > "$d/bin/ps" << SHIM
#!/bin/sh
# holds the first process-group lookup of run_bounded until the test releases it, so a signal can arrive in that window
case " \$* " in
  *" pgid= "*)
    if [ ! -e "$d/barrier/reached" ]; then
      : > "$d/barrier/reached"
      while [ ! -e "$d/barrier/release" ]; do sleep 0.05; done
    fi ;;
esac
exec $(command -v ps) "\$@"
SHIM
  chmod +x "$d/bin/ps"
  cat > "$d/cmd.sh" << 'CMD'
#!/usr/bin/env bash
# a command with a child of its own; both pids recorded in the files named by $1
echo $$ > "$1.leader"
sleep 61 &
echo $! > "$1.child"
wait
CMD
  chmod +x "$d/cmd.sh"
  cat > "$d/entry.sh" << ENTRY
#!/usr/bin/env bash
cd "$PWD"
. ./lib.sh
run_bounded 60 "$d/cmd.sh" "$d/rec"
ENTRY
  sleep 120 &
  sib=$!
  OWN_PIDS="$OWN_PIDS $sib"
  set -m
  PATH=$d/bin:$PATH bash "$d/entry.sh" > "$d/entry.out" 2>&1 &
  h=$!
  set +m
  retry_until 20 test -e "$d/barrier/reached" || true
  retry_until 20 test -s "$d/rec.child" || true
  leader=$(read_pid "$d/rec.leader")
  child=$(read_pid "$d/rec.child")
  check "the command is running and the lookup of its group is held" yes "$([ -n "$leader" ] && [ -n "$child" ] && alive "$leader" && alive "$child" && [ -e "$d/barrier/reached" ] && echo yes || echo no)"
  kill -TERM "$h" 2> /dev/null || true
  : > "$d/barrier/release"
  rc=0
  wait "$h" 2> /dev/null || rc=$?
  check "TERM sent in that window: the script leaves with 143" 143 "$rc"
  check "  and the command's leader is gone" yes "$([ -n "$leader" ] && all_dead "$leader" && echo yes || echo no)"
  check "  and so is its child" yes "$([ -n "$child" ] && all_dead "$child" && echo yes || echo no)"
  check "  and an unrelated process of the caller is untouched" yes "$(alive "$sib" && echo yes || echo no)"
  kill "$sib" 2> /dev/null || true

  # The still narrower window, between the launch and the assignment of the pid: the handler finds the job through $!
  set -m
  sleep 100 &
  lp=$!
  set +m
  OWN_PIDS="$OWN_PIDS $lp"
  rc=0
  ( _RB_PID=""; _RB_BEFORE=""; _rb_signal 143 ) > /dev/null 2>&1 || rc=$?
  check "a signal handled after the launch and before the pid is recorded ends the new job (status 143)" "143 yes" "$rc $(all_dead "$lp" && echo yes || echo no)"
  set -m
  sleep 100 &
  lp=$!
  set +m
  OWN_PIDS="$OWN_PIDS $lp"
  rc=0
  ( _RB_PID=""; _RB_BEFORE=$lp; _rb_signal 143 ) > /dev/null 2>&1 || rc=$?
  check "a signal handled before any launch ends nothing (the previous job is not this command)" "143 yes" "$rc $(alive "$lp" && echo yes || echo no)"
  kill "$lp" 2> /dev/null || true
}

# --- root-cancel -------------------------------------------------------------------------------------

# root_cancel <name> <signal> <expected status> <ready command> [ENV=value ...] -- <script call words>
# Starts tests/lifecycle-test.sh as an entry point of its own (job control, so it can receive INT as a terminal would
# send it), with a case that runs the script call as the one script it is executing. When the ready command succeeds it
# signals ONLY the lifecycle root, and checks the status, that the script it was running (and everything the fake runtime
# started for it) is gone, that the project cleanup came after, and that an unrelated sibling process is untouched.
# The ready command runs in this shell. Sets RC_OUT to the root's output file.
root_cancel() {
  local name=$1 sig=$2 want=$3 ready=$4
  shift 4
  local envs=()
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  local call=$* h rc=0 sib t0 i=0 child cpid
  RC_OUT=$TMP/rc-$name.out
  : > "$FAKE_LOG"
  : > "$FAKE_PIDS"
  rm -f "$TMP/rc-$name.child"
  sleep 120 &
  sib=$!
  OWN_PIDS="$OWN_PIDS $sib"
  set -m
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" TEST_PROJECT=ut-project REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-rc-$name" \
    TEST_REGISTRY_PORT=59881 TEST_COLLECTOR_HEALTH_PORT=59882 TEST_PROMETHEUS_PORT=59883 TEST_GRAFANA_PORT=59884 \
    SELFTEST_CHILD="$call" SELFTEST_CHILD_PID_FILE="$TMP/rc-$name.child" ${envs[@]+"${envs[@]}"} \
    ./tests/lifecycle-test.sh selftest-child > "$RC_OUT" 2>&1 &
  h=$!
  set +m
  retry_until 40 "$ready" || true
  cpid=$(read_pid "$TMP/rc-$name.child")
  check "$name: the script call is running when the signal is sent" yes "$([ -n "$cpid" ] && alive "$cpid" && echo yes || echo no)"
  t0=$SECONDS
  kill "-$sig" "$h" 2> /dev/null || true
  while alive "$h" && [ "$i" -lt 250 ]; do sleep 0.1; i=$((i + 1)); done
  if alive "$h"; then rc=still-running; kill -9 "$h" 2> /dev/null || true; else wait "$h" 2> /dev/null || rc=$?; fi
  check "$name: $sig to the lifecycle root ends it with status $want" "$want" "$rc"
  check "  within 20 seconds" yes "$([ $((SECONDS - t0)) -le 20 ] && echo yes || echo no)"
  check "  the script it was running is gone" yes "$([ -n "$cpid" ] && all_dead "$cpid" && echo yes || echo no)"
  child=$(cat "$FAKE_PIDS" 2> /dev/null | tr '\n' ' ')
  # shellcheck disable=SC2086
  check "  and everything the fake runtime started for it is gone" yes "$(all_dead $child && echo yes || echo no)"
  check "  the project was cleaned up by the root after that (down calls: ${CANCEL_DOWNS:-1})" "${CANCEL_DOWNS:-1}" "$(down_calls)"
  check "  its claim was released" yes "$([ ! -e "$TMP/claims/ut-project" ] && echo yes || echo no)"
  check "  an unrelated sibling process is untouched" yes "$(alive "$sib" && echo yes || echo no)"
  kill "$sib" 2> /dev/null || true
  # shellcheck disable=SC2046
  kill $(cat "$FAKE_PIDS" 2> /dev/null) 2> /dev/null || true
}

ready_hung() { fake_hung; }
# the call's own down has hung: take the switch away so that the cleanup that follows is not held up too
ready_hung_then_release_down() { fake_hung && rm -f "$FAKE_FLAG_DIR/down"; }
ready_demo_waiting() { [ -s "$TMP/rc-$CANCEL_NAME.child" ] && sleep 2; }
ready_marker() { [ -e "$TMP/rc/ready" ]; }

case_root_cancel() {
  part "root-cancel: TERM or INT sent to the lifecycle root reaches the script it is running, which settles before cleanup"
  mkdir -p "$TMP/rc" "$TMP/claims"
  local sig want
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    CANCEL_NAME=setup-$sig
    root_cancel "$CANCEL_NAME" "$sig" "$want" ready_hung FAKE_STALL=compose-ps-running -- ./setup.sh
    CANCEL_NAME=demo-$sig
    root_cancel "$CANCEL_NAME" "$sig" "$want" ready_demo_waiting -- ./demo.sh
    CANCEL_NAME=teardown-$sig
    # the script's own down hangs; the switch is removed once it hangs, so the root's cleanup afterwards does not
    printf stall > "$FAKE_FLAG_DIR/down"
    CANCEL_DOWNS=2
    root_cancel "$CANCEL_NAME" "$sig" "$want" ready_hung_then_release_down -- ./teardown.sh
    unset CANCEL_DOWNS
  done

  # a script with cleanup of its own: the root waits for it, so the project cleanup comes after the script has settled
  cat > "$TMP/rc/settle.sh" << 'SH'
#!/usr/bin/env bash
# on TERM or INT it works for a second, records that it settled, then leaves
trap 'sleep 1; echo child-settled >> "$FAKE_LOG"; exit 0' TERM INT
: > "$TMP_READY"
while :; do sleep 0.2; done
SH
  chmod +x "$TMP/rc/settle.sh"
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    rm -f "$TMP/rc/ready"
    CANCEL_NAME=settle-$sig
    root_cancel "$CANCEL_NAME" "$sig" "$want" ready_marker TMP_READY="$TMP/rc/ready" -- "$TMP/rc/settle.sh"
    check "  the script settled (its own cleanup finished) before the project cleanup began" yes \
      "$(awk '/child-settled/ { a = NR } / down / { b = NR } END { print (a && b && a < b) ? "yes" : "no" }' "$FAKE_LOG")"
  done

  # a script that ignores the signal is ended after the grace period; the root still leaves with the conventional status
  cat > "$TMP/rc/stubborn.sh" << 'SH'
#!/usr/bin/env bash
trap '' TERM INT
: > "$TMP_READY"
while :; do sleep 0.2; done
SH
  chmod +x "$TMP/rc/stubborn.sh"
  rm -f "$TMP/rc/ready"
  CANCEL_NAME=stubborn-TERM
  root_cancel "$CANCEL_NAME" TERM 143 ready_marker TMP_READY="$TMP/rc/ready" TEST_CHILD_SETTLE_SECONDS=2 -- "$TMP/rc/stubborn.sh"
  check "  and said it had to kill the script that ignored the signal" yes "$(grep -q 'did not end within 2s' "$RC_OUT" && echo yes || echo no)"

  # negative stress: a provider process that ignores TERM (needs KILL after the grace period inside the script) still ends
  # the whole call with nothing left behind
  CANCEL_NAME=stubborn-provider-TERM
  root_cancel "$CANCEL_NAME" TERM 143 ready_hung FAKE_STALL=compose-ps-running FAKE_STUBBORN=1 -- ./setup.sh
}


# --- claims ------------------------------------------------------------------------------------------

# start_root <name> <ENV=value ...> -- <lifecycle args>: a lifecycle root as an entry point of its own; sets R_PID and R_OUT
start_root() {
  local name=$1
  shift
  local envs=()
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  R_OUT=$TMP/root-$name.out
  set -m
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-root-$name" \
    ${envs[@]+"${envs[@]}"} ./tests/lifecycle-test.sh "$@" > "$R_OUT" 2>&1 &
  R_PID=$!
  set +m
}
ready_root_listening() { [ -s "$TMP/root-pid-$1" ]; }

case_claims() {
  part "claims: two lifecycle roots never share or release each other's project"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local a rc b_out down_a
  : > "$FAKE_LOG"

  # the same explicit project, the first root alive
  start_root first TEST_PROJECT=ut-shared SELFTEST_PID_FILE="$TMP/root-pid-first" -- selftest-hold
  a=$R_PID
  retry_until 30 test -s "$TMP/root-pid-first" || true
  check "the first root holds the project (its claim exists with its details)" yes "$(sed -n 2p "$TMP/claims/ut-shared" 2> /dev/null | grep -q "pid $a," && echo yes || echo no)"
  : > "$FAKE_LOG"
  harness second TEST_PROJECT=ut-shared -- local-dir-guard
  check "a second root for the same explicit project is refused" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  saying that the project is claimed by another run" yes "$(in_out 'is claimed by another run of this test')"
  check "  and naming the claim to remove by hand if that run is gone" yes "$(in_out "delete $TMP/claims/ut-shared yourself")"
  check "  before any cleanup: no project was torn down" 0 "$(down_calls)"
  check "  the first root is still running" yes "$(alive "$a" && echo yes || echo no)"
  check "  and its claim is still its own" yes "$(sed -n 2p "$TMP/claims/ut-shared" 2> /dev/null | grep -q "pid $a," && echo yes || echo no)"
  kill -TERM "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  check "TERM to the first root: it leaves with 143, cleaning up its own project once" "143 1" "$rc $(down_calls)"
  check "  and releases its claim" yes "$([ ! -e "$TMP/claims/ut-shared" ] && echo yes || echo no)"
  harness third TEST_PROJECT=ut-shared -- local-dir-guard
  check "after that the project can be claimed again" "0 yes" "$H_RC $(in_out 'All checks passed.')"

  # default projects: every invocation has a project of its own
  : > "$FAKE_LOG"
  start_root hold TEST_PROJECT= SELFTEST_PID_FILE="$TMP/root-pid-hold" -- selftest-hold
  a=$R_PID
  retry_until 30 test -s "$TMP/root-pid-hold" || true
  harness other TEST_PROJECT= -- local-dir-guard
  check "a second root with the default project name, while the first is alive, passes" "0 yes" "$H_RC $(in_out 'All checks passed.')"
  b_out=$(grep -o -E '\-p usage-otel-test-[0-9a-f]{8} down' "$FAKE_LOG" | sort -u | tr '\n' ' ')
  check "  and cleaned up one project of the shape usage-otel-test-<random>, not the first root's" 1 "$(printf '%s\n' "$b_out" | tr ' ' '\n' | grep -c '^usage-otel-test-')"
  check "  the first root's claim is untouched by the second root's release" 1 "$(find "$TMP/claims" -mindepth 1 -maxdepth 1 -type f | wc -l | tr -d ' ')"
  check "  and the first root is still running" yes "$(alive "$a" && echo yes || echo no)"
  kill -TERM "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  down_a=$(grep -c ' down ' "$FAKE_LOG" || true)
  check "TERM to the first root: 143, and now both roots have cleaned up their own (different) projects" "143 2" "$rc $down_a"
  check "  the two projects differ" 2 "$(grep -o -E '\-p usage-otel-test-[0-9a-f]{8} down' "$FAKE_LOG" | sort -u | wc -l | tr -d ' ')"
  check "  and no claim is left behind" 0 "$(find "$TMP/claims" -mindepth 1 | wc -l | tr -d ' ')"

  # a project that already holds a network, and nothing else, is not ours to remove
  : > "$FAKE_LOG"
  harness network-only TEST_PROJECT=ut-net FAKE_NETWORKS=ut-net_default -- local-dir-guard
  check "a project that already has a network is refused" yes "$([ "$H_RC" -ne 0 ] && echo yes || echo no)"
  check "  without removing anything" 0 "$(down_calls)"
  check "  and its claim was released" yes "$([ ! -e "$TMP/claims/ut-net" ] && echo yes || echo no)"

  # --keep releases the claim (the stack stays, and the existing inventory check protects it)
  harness keepclaim TEST_PROJECT=ut-keep -- --keep local-dir-guard
  check "--keep: exits 0 and releases the claim, leaving no cleanup" "0 0 yes" "$H_RC $(down_calls) $([ ! -e "$TMP/claims/ut-keep" ] && echo yes || echo no)"

  # a claim the root cannot tell is stale is never taken over
  printf 'token-of-nobody\npid 1, started long ago\n' > "$TMP/claims/ut-stale"
  : > "$FAKE_LOG"
  harness stale TEST_PROJECT=ut-stale -- local-dir-guard
  check "a claim left by something else is not reclaimed, whatever its age" "yes 0 yes" \
    "$([ "$H_RC" -ne 0 ] && echo yes || echo no) $(down_calls) $([ -f "$TMP/claims/ut-stale" ] && echo yes || echo no)"
  check "  and the stranger's claim is byte for byte what it was" "token-of-nobody pid 1, started long ago" "$(tr '\n' ' ' < "$TMP/claims/ut-stale" | sed 's/ $//')"
  rm -f "$TMP/claims/ut-stale"
}

# --- project-reset -----------------------------------------------------------------------------------

case_project_reset() {
  part "project-reset: a case that needs an empty project empties its own project first, and only then"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local f=$FAKE_FLAG_DIR
  rm -f "$f"/*
  : > "$FAKE_LOG"
  harness reset-empty -- selftest-reset
  check "an empty project: the case runs without a purge of its own (only the final cleanup)" "0 1" "$H_RC $(down_calls)"
  rm -f "$f"/*
  : > "$FAKE_LOG"
  harness reset-busy SELFTEST_LEFTOVER=yes -- selftest-reset
  check "a project an earlier case left running: purged first as an ordinary checked step, then the final cleanup" "0 2" "$H_RC $(down_calls)"
  check "  every purge was of this project only" yes "$(grep ' down ' "$FAKE_LOG" | grep -q -v -- '-p ut-project' && echo no || echo yes)"
}

# --- listener-ownership ------------------------------------------------------------------------------

case_listener_ownership() {
  part "listener-ownership: a backend that did not bind its ports is never mistaken for the foreign listener that answers on them"
  write_server
  mkdir -p "$TMP/lo"
  start_server "$TMP/lo/foreign.log" plain 59951 59952 59953 59954
  local fpid="${OWN_PIDS##* }" rc=0 out
  check "control: a started backend reports ready with its own pid (the foreign listener's ports answer)" "$fpid" "$(cat "$TMP/lo/foreign.log.ready")"
  : > "$TMP/lo/foreign.log"
  ( start_server "$TMP/lo/second.log" plain 59951 59952 59953 59954 ) > "$TMP/lo/second.out" 2>&1 || rc=$?
  check "a second backend on the same four ports, answered by the foreign listener, is refused" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  out=$(cat "$TMP/lo/second.out")
  check "  with the bind error as its diagnostic" yes "$(printf '%s' "$out" | grep -q 'Address already in use' && echo yes || echo no)"
  check "  saying that nothing was sent" yes "$(printf '%s' "$out" | grep -q 'nothing was sent' && echo yes || echo no)"
  check "  and it never reported ready" no "$([ -e "$TMP/lo/second.log.ready" ] && echo yes || echo no)"
  check "  and no request of any kind reached the foreign listener" 0 "$(wc -l < "$TMP/lo/foreign.log" | tr -d ' ')"
  check "  and the foreign listener is still running" yes "$(alive "$fpid" && echo yes || echo no)"
  rc=0
  ( start_server "$TMP/lo/third.log" plain 59955 59956 59951 59957 ) > "$TMP/lo/third.out" 2>&1 || rc=$?
  check "one taken port among free ones is refused too" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and nothing reached the foreign listener" 0 "$(wc -l < "$TMP/lo/foreign.log" | tr -d ' ')"
  kill "$fpid" 2> /dev/null || true
  sleep 1
}

# --- state-snapshot ----------------------------------------------------------------------------------

case_state_snapshot() {
  part "state-snapshot: verify.sh reads the run state once; a replacement of the file during the run cannot mix two runs"
  write_server
  local d=$TMP/ss rc real
  mkdir -p "$d/bin"
  real=$(command -v jq)
  cat > "$d/state.json" << 'JSON'
{"schemaVersion":2,"project":"usage-telemetry-otel","runId":"run-A","source":{"label":"service_instance_id","instanceId":"inst-1"},
 "evaluationTime":"1795129704.514","settledAt":"2026-01-01T00:00:00Z",
 "schemas":[{"globalId":5,"contentId":10},{"globalId":6,"contentId":11}],
 "notCounted":[{"request":"GET /ids/globalIds/9000000000000000","identifierValue":"9000000000000000"}],
 "expectedSeries":[
  {"clientId":"orders-service","identifierType":"global_id","identifierValue":"5","operation":"SERIALIZE","count":3},
  {"clientId":"orders-service","identifierType":"content_id","identifierValue":"10","operation":"DESERIALIZE","count":2}]}
JSON
  cat > "$d/replacement.json" << 'JSON'
{"schemaVersion":2,"project":"usage-telemetry-otel","runId":"run-B","source":{"label":"service_instance_id","instanceId":"inst-1"},
 "evaluationTime":"1795129999.000","settledAt":"2026-01-02T00:00:00Z",
 "schemas":[{"globalId":105,"contentId":110},{"globalId":106,"contentId":111}],
 "notCounted":[{"request":"GET /ids/globalIds/9000000000000000","identifierValue":"9000000000000000"}],
 "expectedSeries":[
  {"clientId":"billing-service","identifierType":"global_id","identifierValue":"105","operation":"SERIALIZE","count":7}]}
JSON
  printf '{ not json\n' > "$d/malformed.json"
  printf '[{"client":"orders-service","type":"global_id","value":"5","operation":"SERIALIZE","count":3},{"client":"orders-service","type":"content_id","value":"10","operation":"DESERIALIZE","count":2}]\n' > "$d/series.json"
  # A jq that, right after the first call whose filter mentions evaluationTime, atomically replaces the state file once:
  # the replacement lands while verify.sh is still working on the state it already holds
  cat > "$d/bin/jq" << SHIM
#!/bin/sh
"$real" "\$@"
rc=\$?
case " \$* " in
  *evaluationTime*)
    if [ ! -e "$d/swapped" ]; then
      : > "$d/swapped"
      cp "\$SWAP_WITH" "$d/state.new" && mv "$d/state.new" "$d/state.json"
    fi ;;
esac
exit \$rc
SHIM
  chmod +x "$d/bin/jq"
  # The history exists only at run A's evaluation time: a query at any other time sees nothing
  SERIES_TIME=1795129704.514 SERIES_FILE=$d/series.json start_server "$d/server.log" series 59931 59932 59933 59934
  local which prom_all prom_a graf_all graf_a
  for which in replacement malformed; do
    cp "$d/state.json" "$d/state.keep"
    rm -f "$d/swapped"
    : > "$d/server.log"
    rc=0
    SWAP_WITH=$d/$which.json PATH=$d/bin:$PATH VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      ./verify.sh --state "$d/state.json" > "$d/$which.out" 2>&1 || rc=$?
    prom_all=$(grep -c '^59933 GET /api/v1/query.*apicurio_usage_schema_fetches_total' "$d/server.log" || true)
    prom_a=$(grep -c '^59933 GET /api/v1/query.*apicurio_usage_schema_fetches_total.*time=1795129704.514' "$d/server.log" || true)
    graf_all=$(grep -c '^59934 GET /api/datasources/proxy/uid/.*apicurio_usage_schema_fetches_total' "$d/server.log" || true)
    graf_a=$(grep -c '^59934 GET /api/datasources/proxy/uid/.*apicurio_usage_schema_fetches_total.*time=1795129704.514' "$d/server.log" || true)
    check "  both Prometheus and Grafana were asked about the run's series, every time at run A's evaluation time (never the replacement's)" "yes yes" \
      "$([ "$prom_all" -ge 1 ] && [ "$prom_all" = "$prom_a" ] && echo yes || echo no) $([ "$graf_all" -ge 1 ] && [ "$graf_all" = "$graf_a" ] && echo yes || echo no)"
    check "the file was replaced by a valid ($which) state while verify.sh ran (the replacement happened)" yes "$([ -e "$d/swapped" ] && ! cmp -s "$d/state.json" "$d/state.keep" && echo yes || echo no)"
    check "  verify.sh still checked exactly the run it had read, run A (exit status 0)" 0 "$rc"
    check "  and reported run A, not the replacement" "yes no" "$(grep -q 'run run-A,' "$d/$which.out" && echo yes || echo no) $(grep -q 'run run-B' "$d/$which.out" && echo yes || echo no)"
    cp "$d/state.keep" "$d/state.json"
  done
  rc=0
  VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
    ./verify.sh --state "$d/malformed.json" > "$d/malformed-direct.out" 2>&1 || rc=$?
  check "a state that is not JSON still fails verification" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and is reported, not trusted" yes "$(grep -q 'only version 2' "$d/malformed-direct.out" && echo yes || echo no)"
  # Mutants of verify.sh, as disposable copies: each reads one value from the live file instead of the snapshot. The
  # replacement lands in between, so each must now fail; a test that cannot tell them from the real script proves nothing.
  mkdir -p "$d/mut"
  cp lib.sh "$d/mut/lib.sh"
  local mut mutname
  for mutname in time expected; do
    # shellcheck disable=SC2016 # sed expressions, not shell expansions
    case $mutname in
      time) mut='s|^at=\$(jq -r .evaluationTime <<< "\$STATE_JSON")|at=$(jq -r .evaluationTime "$STATE_FILE")|' ;;
      expected) mut='s|<<< "\$STATE_JSON" \| LC_ALL=C sort)$|"$STATE_FILE" \| LC_ALL=C sort)|' ;;
    esac
    sed -e "$mut" verify.sh > "$d/mut/verify.sh"
    chmod +x "$d/mut/verify.sh"
    check "mutant '$mutname' really differs from verify.sh" yes "$(cmp -s verify.sh "$d/mut/verify.sh" && echo no || echo yes)"
    cp "$d/state.json" "$d/state.keep"
    rm -f "$d/swapped"
    rc=0
    SWAP_WITH=$d/replacement.json PATH=$d/bin:$PATH VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      "$d/mut/verify.sh" --state "$d/state.json" > "$d/mut-$mutname.out" 2>&1 || rc=$?
    check "  the mutant that reads the $mutname from the live file instead of the snapshot fails under the same replacement" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    cp "$d/state.keep" "$d/state.json"
  done
  kill "${OWN_PIDS##* }" 2> /dev/null || true
  sleep 1
}

# --- target-state ------------------------------------------------------------------------------------

case_target_state() {
  part "target-state: a failed query is unknown, never the answer of an earlier attempt"
  write_server
  local d=$TMP/ts rc
  mkdir -p "$d"
  start_server "$d/server.log" flap 59921 59922 59923 59924
  rc=0
  VERIFY_RETRY_SECONDS=2 REGISTRY_PORT=59921 COLLECTOR_HEALTH_PORT=59922 PROMETHEUS_PORT=59923 GRAFANA_PORT=59924 \
    ./verify.sh --stack-only > "$d/out" 2>&1 || rc=$?
  check "a target that is down on one attempt and whose query then fails: verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
  check "  and says Prometheus did not answer (state UNKNOWN)" yes "$(grep -q 'target otel-collector-usage: Prometheus did not answer, state UNKNOWN' "$d/out" && echo yes || echo no)"
  check "  and does not report the earlier answer, 0, as the current state" no "$(grep -q "otel-collector-usage is up: got '0'" "$d/out" && echo yes || echo no)"
  kill "${OWN_PIDS##* }" 2> /dev/null || true
  sleep 1
}

# --- setup-logs --------------------------------------------------------------------------------------

case_setup_logs() {
  part "setup-logs: setup writes only into a log directory of its own; nothing that already exists is written through"
  local d=$TMP/sl rc base f
  mkdir -p "$d/ext"
  LOCAL_DIR=$d/local COMPOSE_PROJECT_NAME=ut-logs bash -c '. ./lib.sh; ensure_local_dir'
  base=$d/local/projects/ut-logs/logs
  for f in validate-collector validate-prometheus compose-up; do
    printf 'precious %s\n' "$f" > "$d/ext/$f.txt"
    ln -s "$d/ext/$f.txt" "$base/$f.log"
  done
  printf 'a note of the user\n' > "$base/notes.txt"
  mkdir -p "$base/setup-20200101T000000Z"
  printf 'an earlier run\n' > "$base/setup-20200101T000000Z/registry.log"
  local before
  before=$(cd "$d" && cksum ext/*.txt local/projects/ut-logs/logs/notes.txt local/projects/ut-logs/logs/setup-20200101T000000Z/registry.log)
  : > "$FAKE_LOG"
  rc=0
  FAKE_COMPOSE_PS_OUT=$'registry\notel-collector\nprometheus\ngrafana\n' LOCAL_DIR=$d/local REGISTRY_IMAGE=dummy READY_TIMEOUT=2 REGISTRY_READY_TIMEOUT=2 \
    ./setup.sh --project ut-logs --registry-port 59871 --collector-health-port 59872 --prometheus-port 59873 --grafana-port 59874 > "$d/setup.out" 2>&1 || rc=$?
  check "setup.sh ran its validation and start (it fails later, at readiness, with nothing listening)" yes "$([ "$rc" -ne 0 ] && grep -q ' up -d' "$FAKE_LOG" && echo yes || echo no)"
  check "  every file behind an existing link, the user's note and an earlier run's logs are byte for byte unchanged" "$before" \
    "$(cd "$d" && cksum ext/*.txt local/projects/ut-logs/logs/notes.txt local/projects/ut-logs/logs/setup-20200101T000000Z/registry.log)"
  check "  the existing links are still links" 3 "$(find "$base" -maxdepth 1 -type l | wc -l | tr -d ' ')"
  check "  and its own logs are in a fresh directory of this invocation" yes \
    "$([ "$(find "$base" -mindepth 2 -maxdepth 2 \( -name validate-collector.log -o -name compose-up.log \) -type f | wc -l | tr -d ' ')" = 2 ] && echo yes || echo no)"
  check "  that the diagnostics name" yes "$(grep -q 'Full logs: .*/logs/setup-' "$d/setup.out" && echo yes || echo no)"

  if [ "$(id -u)" -ne 0 ]; then
    chmod 555 "$base"
    : > "$FAKE_LOG"
    rc=0
    FAKE_COMPOSE_PS_OUT=$'registry\n' LOCAL_DIR=$d/local REGISTRY_IMAGE=dummy ./setup.sh --project ut-logs --registry-port 59871 --collector-health-port 59872 --prometheus-port 59873 --grafana-port 59874 > "$d/readonly.out" 2>&1 || rc=$?
    chmod 755 "$base"
    check "a log directory that cannot be created: setup.sh fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  says so" yes "$(grep -q 'could not create a log directory' "$d/readonly.out" && echo yes || echo no)"
    check "  before the runtime was asked to run or start anything" 0 "$(grep -c -E ' (up|run|down) ' "$FAKE_LOG" || true)"
  else
    info "  skip  running as root: a read-only directory does not stop the write"
  fi
}

# --- claim-signals -----------------------------------------------------------------------------------

case_claim_signals() {
  part "claim-signals: a signal during a hung final teardown leaves no claim behind (acquisition and finish are covered statement by statement by claim-boundaries and finish-boundaries)"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local a rc
  : > "$FAKE_LOG"
  # during the final teardown: the purge of the project hangs and the root is signalled
  : > "$FAKE_LOG"
  : > "$FAKE_PIDS"
  printf stall > "$FAKE_FLAG_DIR/down"
  start_root fin TEST_PROJECT=ut-cs -- local-dir-guard
  a=$R_PID
  retry_until 40 fake_hung || true
  check "the final teardown is running (and hung) with the claim held" yes "$([ -f "$TMP/claims/ut-cs" ] && fake_hung && echo yes || echo no)"
  kill -TERM "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  rm -f "$FAKE_FLAG_DIR/down"
  check "TERM during the final teardown: the root ends with 143" 143 "$rc"
  check "  its claim is released" yes "$([ ! -e "$TMP/claims/ut-cs" ] && echo yes || echo no)"
  # shellcheck disable=SC2046
  check "  and nothing the teardown started is left" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  harness after-fin TEST_PROJECT=ut-cs -- local-dir-guard
  check "  the project can be claimed again by the next invocation" "0 yes" "$H_RC $(in_out 'All checks passed.')"

  # the same with INT
  : > "$FAKE_PIDS"
  printf stall > "$FAKE_FLAG_DIR/down"
  start_root fin-int TEST_PROJECT=ut-cs -- local-dir-guard
  a=$R_PID
  retry_until 40 fake_hung || true
  kill -INT "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  rm -f "$FAKE_FLAG_DIR/down"
  check "INT during the final teardown: 130, claim released" "130 yes" "$rc $([ ! -e "$TMP/claims/ut-cs" ] && echo yes || echo no)"
  # shellcheck disable=SC2046
  kill $(cat "$FAKE_PIDS" 2> /dev/null) 2> /dev/null || true

  # a claim of someone else is never released by this run's cleanup, even when its own claim attempt was refused
  printf 'token-of-nobody\npid 1, started long ago\n' > "$TMP/claims/ut-other"
  harness foreign TEST_PROJECT=ut-other -- local-dir-guard
  check "a claim that is not this run's survives a refused run untouched" "yes token-of-nobody" "$([ "$H_RC" -ne 0 ] && echo yes || echo no) $(sed -n 1p "$TMP/claims/ut-other")"
  rm -f "$TMP/claims/ut-other"
}

# --- launch-split ------------------------------------------------------------------------------------

case_launch_split() {
  part "launch-split: a signal between the two statements that used to record the job cannot leave it half recorded"
  local d=$TMP/ls rc=0 h i=0 pid
  mkdir -p "$d"
  cat > "$d/cmd.sh" << 'CMD'
#!/usr/bin/env bash
sleep 61 &
wait
CMD
  chmod +x "$d/cmd.sh"
  # TERM is sent to the shell by its own DEBUG trap at the first command after _RB_PID is set: exactly between the
  # assignments of the pre-fix code (_RB_PID=$pid, then _RB_PGID=$pid)
  cat > "$d/entry.sh" << ENTRY
#!/usr/bin/env bash
set -T
cd "$PWD"
. ./lib.sh
trap 'if [ -n "\${_RB_PID:-}" ] && [ ! -e "$d/sent" ]; then echo "\$_RB_PID" > "$d/pid"; : > "$d/sent"; kill -TERM \$\$; fi' DEBUG
run_bounded 60 "$d/cmd.sh"
ENTRY
  set -m
  bash "$d/entry.sh" > "$d/entry.out" 2>&1 &
  h=$!
  set +m
  while alive "$h" && [ "$i" -lt 150 ]; do sleep 0.1; i=$((i + 1)); done
  if alive "$h"; then rc=still-running; kill -KILL "$h" 2> /dev/null || true; else wait "$h" 2> /dev/null || rc=$?; fi
  pid=$(read_pid "$d/pid")
  check "TERM delivered right after the job's identity was recorded: the script leaves with 143, not hung" 143 "$rc"
  check "  the job's leader is gone" yes "$([ -n "$pid" ] && all_dead "$pid" && echo yes || echo no)"
  check "  and nobody is left in its process group" "" "$(_group_members "${pid:-0}" | tr '\n' ' ' | sed 's/ $//')"
  [ -z "$pid" ] || kill -KILL -- "-$pid" 2> /dev/null || true
}

# --- signal injection --------------------------------------------------------------------------------

# inject_run <name> <function> <n> <signal> -- <lifecycle args>: runs tests/lifecycle-test.sh, sourced by a shell whose DEBUG
# trap sends <signal> to that shell just before the <n>th command executed by <function> (its own statements; for finish
# also the call that enters it). That is the real boundary between two statements, not a pause the code arranges for
# itself. Sets I_RC (the status) and I_HIT (yes when the signal was sent, no when <function> ran fewer commands).
inject_run() {
  local name=$1 fn=$2 n=$3 sig=$4 h
  shift 5
  I_OUT=$TMP/inj-$name.out
  rm -f "$TMP/inj-$name.hit"
  set -m
  # shellcheck disable=SC2016 # the inner shell expands them
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" TEST_PROJECT=ut-inj REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-inj-$name" \
    INJ_FN="$fn" INJ_N="$n" INJ_SIG="$sig" INJ_HIT="$TMP/inj-$name.hit" \
    bash -c 'set -T
INJ_COUNT=0
trap '"'"'if [ "$BASH_SUBSHELL" -eq 0 ]; then case "${FUNCNAME[0]:-}|$BASH_COMMAND" in "$INJ_FN|"* | *"|$INJ_FN" | *"|$INJ_FN "*) INJ_COUNT=$((INJ_COUNT + 1)); if [ "$INJ_COUNT" -eq "$INJ_N" ]; then : > "$INJ_HIT"; kill -"$INJ_SIG" $$; fi ;; esac; fi'"'"' DEBUG
. "$0" "$@"' "$PWD/tests/lifecycle-test.sh" "$@" > "$I_OUT" 2>&1 &
  h=$!
  set +m
  I_RC=0
  wait "$h" 2> /dev/null || I_RC=$?
  I_HIT=$([ -e "$TMP/inj-$name.hit" ] && echo yes || echo no)
}

# claim_residue: what a run leaves in the claim directory (a claim file of the injection project or any temporary claim file)
claim_residue() { find "$TMP/claims" -mindepth 1 \( -name 'ut-inj' -o -name '.claim.*' \) | wc -l | tr -d ' '; }

# --- finish-boundaries -------------------------------------------------------------------------------

case_finish_boundaries() {
  part "finish-boundaries: a signal between any two statements of the final cleanup, or at its entry, leaves no claim behind"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local sig n hits bad want
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    hits=0
    bad=""
    for n in $(seq 1 60); do
      : > "$FAKE_LOG"
      inject_run "fin-$sig-$n" finish "$n" "$sig" -- local-dir-guard
      [ "$I_HIT" = yes ] || break
      hits=$((hits + 1))
      [ "$I_RC" = "$want" ] || bad="$bad rc$n=$I_RC"
      [ "$(claim_residue)" = 0 ] || bad="$bad residue$n"
      rm -f "$TMP"/claims/.claim.* 2> /dev/null
      rm -f "$TMP/claims/ut-inj"
    done
    check "$sig injected at each of the $hits boundaries of finish (entry call and every statement): status $want and no claim or temporary file left" "" "${bad# }"
    check "  that is a meaningful number of boundaries (more than 10)" yes "$([ "$hits" -gt 10 ] && echo yes || echo no)"
    harness "after-fin-$sig" TEST_PROJECT=ut-inj -- local-dir-guard
    check "  and the project is claimed again by the next invocation" "0 yes" "$H_RC $(in_out 'All checks passed.')"
  done
}

# --- claim-boundaries --------------------------------------------------------------------------------

case_claim_boundaries() {
  part "claim-boundaries: a signal between any two statements of claim acquisition leaves neither a claim nor a temporary file"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local sig n hits bad want
  for sig in TERM INT; do
    want=143
    [ "$sig" = INT ] && want=130
    hits=0
    bad=""
    for n in $(seq 1 60); do
      inject_run "claim-$sig-$n" claim_project "$n" "$sig" -- local-dir-guard
      [ "$I_HIT" = yes ] || break
      hits=$((hits + 1))
      [ "$I_RC" = "$want" ] || bad="$bad rc$n=$I_RC"
      [ "$(claim_residue)" = 0 ] || bad="$bad residue$n"
      rm -f "$TMP"/claims/.claim.* 2> /dev/null
      rm -f "$TMP/claims/ut-inj"
    done
    check "$sig injected at each of the $hits statements of claim_project: status $want and nothing of this run left in the claim directory" "" "${bad# }"
    check "  that is a meaningful number of boundaries (more than 8)" yes "$([ "$hits" -gt 8 ] && echo yes || echo no)"
    harness "after-claim-$sig" TEST_PROJECT=ut-inj -- local-dir-guard
    check "  and the project is claimed again by the next invocation" "0 yes" "$H_RC $(in_out 'All checks passed.')"
  done
  # another run's temporary file and claim are never touched by this run's cleanup
  printf 'token-of-nobody\npid 1\n' > "$TMP/claims/.claim.somebody-else"
  harness other-tmp TEST_PROJECT=ut-inj -- local-dir-guard
  check "a temporary file of another run survives a complete run untouched" "0 yes" "$H_RC $([ -f "$TMP/claims/.claim.somebody-else" ] && echo yes || echo no)"
  rm -f "$TMP/claims/.claim.somebody-else"
}

# --- claim-types -------------------------------------------------------------------------------------

case_claim_types() {
  part "claim-types: a claim path that is not an ordinary file is refused without being read, followed or removed"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims" "$TMP/ct"
  local kind a i rc
  : > "$FAKE_LOG"
  for kind in fifo symlink directory; do
    rm -rf "$TMP/claims/ut-$kind" "$TMP/ct/target"
    case $kind in
      fifo) mkfifo "$TMP/claims/ut-fifo" ;;
      symlink)
        printf 'SECRET-LINE-ONE\nSECRET-OWNER-DETAIL\n' > "$TMP/ct/target"
        ln -s "$TMP/ct/target" "$TMP/claims/ut-symlink"
        ;;
      directory) mkdir "$TMP/claims/ut-directory" ;;
    esac
    start_root "ct-$kind" TEST_PROJECT="ut-$kind" -- local-dir-guard
    a=$R_PID
    i=0
    while alive "$a" && [ "$i" -lt 100 ]; do sleep 0.1; i=$((i + 1)); done
    if alive "$a"; then
      rc=blocked
      kill -KILL -- "-$a" 2> /dev/null || kill -KILL "$a" 2> /dev/null || true
      wait "$a" 2> /dev/null || true
    else
      rc=0
      wait "$a" 2> /dev/null || rc=$?
    fi
    check "a $kind at the claim path: the run ends by itself within 10s with a refusal (not blocked on reading it)" "yes" "$([ "$rc" != blocked ] && [ "$rc" -ne 0 ] && echo yes || echo no)"
    check "  saying it is not an ordinary file and was left untouched" yes "$(grep -q 'not an ordinary file, left untouched' "$R_OUT" && echo yes || echo no)"
    check "  without echoing anything it contains" no "$(grep -q 'SECRET' "$R_OUT" && echo yes || echo no)"
    case $kind in
      fifo) check "  and the FIFO is still there" yes "$([ -p "$TMP/claims/ut-fifo" ] && echo yes || echo no)" ;;
      symlink) check "  and the link and its target are unchanged" "yes SECRET-LINE-ONE" "$([ -L "$TMP/claims/ut-symlink" ] && echo yes || echo no) $(sed -n 1p "$TMP/ct/target")" ;;
      directory) check "  and the directory is still there" yes "$([ -d "$TMP/claims/ut-directory" ] && echo yes || echo no)" ;;
    esac
    check "  and nothing was torn down" 0 "$(down_calls)"
    check "  no temporary claim file was left" 0 "$(find "$TMP/claims" -name '.claim.*' | wc -l | tr -d ' ')"
    rm -rf "$TMP/claims/ut-$kind"
  done
}

# --- state-bytes -------------------------------------------------------------------------------------

case_state_bytes() {
  part "state-bytes: verify.sh judges the bytes of the state file as jq reads them, before bash can drop or change any"
  write_server
  local d=$TMP/sb rc jqrc name
  mkdir -p "$d"
  python3 -I - "$d" << 'PY'
import json, sys
d = sys.argv[1]
state = {"schemaVersion": 2, "project": "usage-telemetry-otel", "runId": "run-bytes", "source": {"label": "service_instance_id", "instanceId": "inst-1"},
         "evaluationTime": "1795129704.514", "settledAt": "2026-01-01T00:00:00Z",
         "schemas": [{"globalId": 5, "contentId": 10}], "notCounted": [],
         "expectedSeries": [{"clientId": "orders-é\"x\\y\u0001z", "identifierType": "global_id", "identifierValue": "5", "operation": "SERIALIZE", "count": 3}]}
series = [{"client": "orders-é\"x\\y\u0001z", "type": "global_id", "value": "5", "operation": "SERIALIZE", "count": 3}]
text = json.dumps(state, indent=1)
open(d + "/valid.json", "w").write(text)           # json.dumps writes the escapes: é \" \\ \u0001
open(d + "/series.json", "w").write(json.dumps(series))
raw = text.encode()
def insert(after, byte):
    i = raw.index(after) + len(after)
    return raw[:i] + byte + raw[i:]
# NUL in the client string (the fixture series holds the client without it, so a reader that strips the NUL sees a match)
open(d + "/nul-client.json", "wb").write(insert("orders-".encode(), b"\x00"))
# NUL and other control bytes in a field the series do not depend on
for name, byte in (("nul-runid", b"\x00"), ("ctrl01-runid", b"\x01"), ("ctrl1f-runid", b"\x1f")):
    open(d + "/" + name + ".json", "wb").write(insert(b'"run-', byte))
open(d + "/two-values.json", "w").write(text + "\n" + text + "\n")
open(d + "/array.json", "w").write("[" + text + "]")
open(d + "/trailing.json", "w").write(text + " x")
PY
  SERIES_FILE=$d/series.json start_server "$d/server.log" series 59931 59932 59933 59934
  for name in valid nul-client nul-runid ctrl01-runid ctrl1f-runid two-values array trailing; do
    jqrc=0
    jq -e . "$d/$name.json" > /dev/null 2>&1 || jqrc=$?
    rc=0
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      ./verify.sh --state "$d/$name.json" > "$d/$name.out" 2>&1 || rc=$?
    case $name in
      valid)
        check "control: a valid state with escaped characters (\\u00e9, \\\", \\\\, \\u0001) verifies (exit status 0)" "0 0" "$jqrc $rc"
        ;;
      nul-client | nul-runid)
        check "$name: jq rejects a raw NUL inside a string in the file (the premise)" yes "$([ "$jqrc" -ne 0 ] && echo yes || echo no)"
        check "  and verify.sh rejects it too, instead of verifying the NUL-stripped text" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  saying why" yes "$(grep -q 'not one valid JSON object' "$d/$name.out" && echo yes || echo no)"
        ;;
      two-values | array | trailing) ;; # stricter than jq on purpose: checked below
      *)
        check "$name: verify.sh agrees with jq on the bytes of the file (jq status $jqrc)" "$([ "$jqrc" -ne 0 ] && echo reject || echo accept)" "$([ "$rc" -ne 0 ] && echo reject || echo accept)"
        ;;
    esac
  done
  for name in two-values array trailing; do
    check "$name is never one run state: rejected" yes "$(grep -q 'not one valid JSON object' "$d/$name.out" && echo yes || echo no)"
  done
  kill "${OWN_PIDS##* }" 2> /dev/null || true
  sleep 1
}

# --- dashboard-provisioning ---------------------------------------------------------------------------

case_dashboard_provisioning() {
  part "dashboard-provisioning: the example's dashboard must be provisioned; only an explicit --external run may skip it"
  write_server
  local dm rc out
  for dm in ok unprovisioned missing error garbage; do
    DASH_MODE=$dm start_server "$TMP/dp-$dm.log" ok 59931 59932 59933 59934
    out=$TMP/dp-$dm.out
    rc=0
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      ./verify.sh --stack-only > "$out" 2>&1 || rc=$?
    case $dm in
      ok)
        check "a provisioned dashboard: verify --stack-only passes" "0 yes" "$rc $(grep -q 'the schema usage dashboard is provisioned' "$out" && echo yes || echo no)" ;;
      unprovisioned)
        check "a dashboard with the UID that no file provisioned: verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says no file provisioned it" yes "$(grep -q 'no file provisioned' "$out" && echo yes || echo no)" ;;
      missing)
        check "a missing dashboard (404): verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says Grafana has no such dashboard" yes "$(grep -q 'Grafana has no dashboard' "$out" && echo yes || echo no)" ;;
      error | garbage)
        check "a dashboard lookup that errors or returns garbage ($dm): verification fails" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
        check "  and says whether it is provisioned is UNKNOWN" yes "$(grep -q 'is UNKNOWN' "$out" && echo yes || echo no)" ;;
    esac
    rc=0
    VERIFY_RETRY_SECONDS=0 REGISTRY_PORT=59931 COLLECTOR_HEALTH_PORT=59932 PROMETHEUS_PORT=59933 GRAFANA_PORT=59934 \
      ./verify.sh --stack-only --external > "$out.external" 2>&1 || rc=$?
    check "the same Grafana with --external: the dashboard is not required, so verification passes" 0 "$rc"
    check "  and the run says it skipped the dashboard" yes "$(grep -q -- '--external: the schema usage dashboard' "$out.external" && echo yes || echo no)"
    kill "${OWN_PIDS##* }" 2> /dev/null || true
    sleep 1
  done
  # setup.sh waits, within its deadline, for the dashboard to be provisioned
  mkdir -p "$TMP/dp-setup"
  local mode limit began waited
  for mode in missing ok; do
    DASH_MODE=$mode start_server "$TMP/dp-setup/$mode.log" setup-fresh 59931 59932 59933 59934
    limit=30
    [ "$mode" != missing ] || limit=6
    out=$TMP/dp-setup/$mode.out
    rc=0
    began=$SECONDS
    FAKE_COMPOSE_PS_OUT=$'registry\notel-collector\nprometheus\ngrafana\n' LOCAL_DIR=$TMP/dp-setup/state REGISTRY_IMAGE=dummy READY_TIMEOUT=$limit \
      ./setup.sh --registry-port 59931 --collector-health-port 59932 --prometheus-port 59933 --grafana-port 59934 > "$out" 2>&1 || rc=$?
    waited=$((SECONDS - began))
    if [ "$mode" = missing ]; then
      check "setup.sh with a dashboard that never appears: fails after the deadline" yes "$([ "$rc" -ne 0 ] && echo yes || echo no)"
      check "  waiting the whole ${limit}s" yes "$([ "$waited" -ge "$limit" ] && echo yes || echo no)"
      check "  naming the dashboard" yes "$(grep -q 'the dashboard apicurio-schema-usage provisioned from the canonical file was not ready after' "$out" && echo yes || echo no)"
      check "  and never printing it as ready" no "$(grep -q 'ok    ready: the dashboard' "$out" && echo yes || echo no)"
    else
      check "setup.sh with a provisioned dashboard: passes" 0 "$rc"
      check "  and says the dashboard is ready" yes "$(grep -q 'ok    ready: the dashboard apicurio-schema-usage provisioned' "$out" && echo yes || echo no)"
    fi
    kill "${OWN_PIDS##* }" 2> /dev/null || true
    sleep 1
  done
}

# --- dashboard-owner ---------------------------------------------------------------------------------

# start_entry <name> <env assignments...> -- <entry args...>: tests/dashboard-scenarios.sh as an entry point of its own (job
# control, so it can receive a signal as a terminal sends it); E_PID and E_OUT
start_entry() {
  local name=$1
  shift
  local envs=()
  while [ "$1" != -- ]; do envs+=("$1"); shift; done
  shift
  E_OUT=$TMP/entry-$name.out
  set -m
  env TMPDIR="$TMP" TEST_CLAIM_DIR="$TMP/claims" REGISTRY_IMAGE=dummy TEST_LOG_DIR="$TMP/hlogs-entry-$name" REGISTRY_READY_TIMEOUT=2 READY_TIMEOUT=2 \
    ${envs[@]+"${envs[@]}"} ./tests/dashboard-scenarios.sh "$@" > "$E_OUT" 2>&1 &
  E_PID=$!
  set +m
}
# entry_run <name> <env assignments...> -- <entry args...>: runs the entry point to its end; E_RC and E_OUT
entry_run() {
  start_entry "$@"
  E_RC=0
  wait "$E_PID" 2> /dev/null || E_RC=$?
}
# runtime_state_calls: how many calls of the fake runtime start, stop or inventory a project
runtime_state_calls() { grep -c -E ' (up|down)( |$)|podman (ps|volume|network) ' "$FAKE_LOG" || true; }

case_dashboard_owner() {
  part "dashboard-owner: the dashboard scenarios run under the lifecycle runner's ownership (claim, preflight, own directories, settle then clean up)"
  rm -rf "$TMP/claims"
  mkdir -p "$TMP/claims"
  local ports=(--registry-port 59891 --collector-health-port 59892 --prometheus-port 59893 --grafana-port 59894)
  local a rc sentinel

  # a project that already has a network, and nothing else, is not theirs to remove
  : > "$FAKE_LOG"
  entry_run net FAKE_NETWORKS=ut-dnet_default -- --project ut-dnet "${ports[@]}"
  check "a project that already has a network is refused before anything is started or removed" "yes 0" "$([ "$E_RC" -ne 0 ] && echo yes || echo no) $(grep -c ' up \| down ' "$FAKE_LOG" || true)"
  check "  with the lifecycle runner's message" yes "$(grep -q 'already has containers, volumes or networks' "$E_OUT" && echo yes || echo no)"
  check "  and its claim was released" yes "$([ ! -e "$TMP/claims/ut-dnet" ] && echo yes || echo no)"

  # a planted symlink where the old driver wrote a fixed file name is never written through: the run writes only in its own
  # directories, whatever the caller's LOCAL_DIR holds
  sentinel=$TMP/dsym-sentinel
  printf 'sentinel\n' > "$sentinel"
  mkdir -p "$TMP/dsym-local/projects/ut-dsym"
  ln -s "$sentinel" "$TMP/dsym-local/projects/ut-dsym/driver-setup.log"
  : > "$FAKE_LOG"
  entry_run sym LOCAL_DIR="$TMP/dsym-local" -- --project ut-dsym "${ports[@]}"
  check "a symlink planted at the old fixed output name is not written through" "sentinel $(readlink "$TMP/dsym-local/projects/ut-dsym/driver-setup.log")" "$(cat "$sentinel") $sentinel"
  check "  the caller's LOCAL_DIR is untouched (the runner has its own)" 1 "$(find "$TMP/dsym-local" -type f -o -type l | wc -l | tr -d ' ')"
  check "  the run failed at setup (nothing is serving) and cleaned up its own project once" "yes 1" "$([ "$E_RC" -ne 0 ] && echo yes || echo no) $(down_calls)"
  check "  its claim was released" yes "$([ ! -e "$TMP/claims/ut-dsym" ] && echo yes || echo no)"

  # the entry point is the runner: its claim carries the entry's own pid, so there is no second owner
  : > "$FAKE_LOG"
  : > "$FAKE_PIDS"
  start_entry hold FAKE_STALL=compose-ps-running -- --project ut-dshared "${ports[@]}"
  a=$E_PID
  retry_until 40 fake_hung || true
  check "an entry run in its setup holds the project: its claim names the entry's pid" yes "$(sed -n 2p "$TMP/claims/ut-dshared" 2> /dev/null | grep -q "pid $a," && echo yes || echo no)"
  : > "$FAKE_LOG"
  harness other TEST_PROJECT=ut-dshared -- local-dir-guard
  check "a lifecycle run for the same project is refused (cross entry point admission)" "yes yes 0" \
    "$([ "$H_RC" -ne 0 ] && echo yes || echo no) $(in_out 'is claimed by another run of this test') $(runtime_state_calls)"
  : > "$FAKE_LOG"
  entry_run second -- --project ut-dshared "${ports[@]}"
  check "a second entry run for the same project is refused too, before it enters setup" "yes yes 0" \
    "$([ "$E_RC" -ne 0 ] && echo yes || echo no) $(grep -q 'is claimed by another run of this test' "$E_OUT" && echo yes || echo no) $(runtime_state_calls)"
  check "  the first entry run is still running and its claim is its own" yes "$(alive "$a" && sed -n 2p "$TMP/claims/ut-dshared" 2> /dev/null | grep -q "pid $a," && echo yes || echo no)"
  : > "$FAKE_LOG"
  kill -TERM "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  check "TERM sent to the entry run alone, during its setup: 143" 143 "$rc"
  # shellcheck disable=SC2046
  check "  the setup it was running, and everything the runtime started for it, is gone" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  check "  its own project was cleaned up once, after that" 1 "$(down_calls)"
  check "  and its claim was released" yes "$([ ! -e "$TMP/claims/ut-dshared" ] && echo yes || echo no)"

  # the other direction: a lifecycle run holds the project, an entry run is refused
  : > "$FAKE_LOG"
  start_root holdlife TEST_PROJECT=ut-dlife SELFTEST_PID_FILE="$TMP/root-pid-dlife" -- selftest-hold
  a=$R_PID
  retry_until 30 test -s "$TMP/root-pid-dlife" || true
  : > "$FAKE_LOG"
  entry_run refused -- --project ut-dlife "${ports[@]}"
  check "an entry run for a project a lifecycle run holds is refused, with nothing started" "yes yes 0" \
    "$([ "$E_RC" -ne 0 ] && echo yes || echo no) $(grep -q 'is claimed by another run of this test' "$E_OUT" && echo yes || echo no) $(runtime_state_calls)"
  kill -TERM "$a" 2> /dev/null || true
  wait "$a" 2> /dev/null || true

  # during the final cleanup: the run fails at setup, its purge hangs, and the entry run is signalled
  : > "$FAKE_LOG"
  : > "$FAKE_PIDS"
  printf stall > "$FAKE_FLAG_DIR/down"
  start_entry fin -- --project ut-dfin "${ports[@]}"
  a=$E_PID
  retry_until 60 fake_hung || true
  check "the cleanup after a failed setup is running (and hung) with the claim held" yes "$([ -f "$TMP/claims/ut-dfin" ] && fake_hung && echo yes || echo no)"
  kill -TERM "$a" 2> /dev/null || true
  rc=0
  wait "$a" 2> /dev/null || rc=$?
  rm -f "$FAKE_FLAG_DIR/down"
  check "TERM during that cleanup: 143, the claim released, nothing the teardown started is left" "143 yes" "$rc $([ ! -e "$TMP/claims/ut-dfin" ] && echo yes || echo no)"
  # shellcheck disable=SC2046
  check "  no process of the fake runtime remains" yes "$(all_dead $(cat "$FAKE_PIDS") && echo yes || echo no)"
  entry_run again -- --project ut-dfin "${ports[@]}"
  check "  and the project can be claimed again by the next entry run (it is then refused only by the empty fake stack, not by a claim)" yes "$(grep -q 'is claimed by another run' "$E_OUT" && echo no || echo yes)"
}

# --- run ---------------------------------------------------------------------------------------------

cases=("$@")
[ ${#cases[@]} -gt 0 ] || cases=(process-tree group-lifetime outer-cancel launch-trap launch-split syntax-each empty-engine bounded-probes runtime-probe atomic-publication project-isolation symlink-base path-spelling state-validation state-snapshot target-state selector-types setup-freshness setup-logs outage-recovery harness-cleanup root-cancel claims claim-signals claim-boundaries claim-types finish-boundaries project-reset listener-ownership state-bytes endpoint-isolation failed-setup verify-failures inventory-status keep-and-ports dashboard-provisioning dashboard-owner)
for c in "${cases[@]}"; do
  fn=case_${c//-/_}
  if declare -F "$fn" > /dev/null; then "$fn"; else die "unknown case '$c'"; fi
done

# failures comes from lib.sh
# shellcheck disable=SC2154
if [ "$failures" -gt 0 ]; then
  printf '\n%s check(s) failed.\n' "$failures"
  exit 1
fi
printf '\nAll checks passed.\n'
