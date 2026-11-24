#!/usr/bin/env bash
# The example's own checks as the required CI pipeline runs them, one phase after the other, each under its own deadline, with the
# exit status of every phase kept and the suite failing unless EVERY phase passed. verify-examples.yaml runs this once per job, after
# the two demo.sh runs and with the example's stack still up (CI_SUITE in ci.env). Run it locally the same way:
#   REGISTRY_IMAGE=<image built from this checkout> ./tests/ci-suite.sh
#
# Phases, in this order (a phase that needs another one is recorded as skipped when that one failed, which fails the suite):
#   unit, dashboard-unit, dashboard-promql, suite-selftest   no stack (the PromQL phase needs the container runtime)
#   browser-setup                                            ./test-ui.sh --setup-only (locked npm packages, Chromium)
#   ui-tooling, ui-selftest, ui-runner, ui-clone, ui-real-tooling   the browser tooling's own checks, on this operating system
#   dashboard-test, ui-seeded, ui-screenshots                the example's stack and the last demo.sh run, read-only
#   lifecycle                                                tests/lifecycle-test.sh, a project of its own
#   scenarios, ui-scenario, scenarios-teardown               tests/dashboard-scenarios.sh --keep (a project of its own), the process
#                                                            tests on it, then its removal whatever happened before
#   artifacts                                                the fail-closed collection (tests/ci-artifacts.py)
# The phases never overlap: the lifecycle and scenario runs compare the containers they did not start, so nothing else may start or
# stop containers while they run. The example's own stack is only read.
#
# Environment: REGISTRY_IMAGE (required: the image built from this checkout), CONTAINER_RUNTIME (CI sets docker), CI_ARTIFACT_DIR
# (absolute, new or empty; default a new temporary directory). Everything this run produces goes below CI_ARTIFACT_DIR: work/ holds
# the full logs and stays on the machine, publish/ holds what the collector accepted and is the only part meant to leave it.
# Prerequisites: bash 3.2 or later, curl, jq, od, python3, node and npm (the locked browser tooling), a container runtime with Compose v2.
# Exit status: 0 only when every phase passed; 1 otherwise; 2 for a missing prerequisite.
set -uo pipefail

EXAMPLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
cd "$EXAMPLE_DIR" || exit 2
# shellcheck source=lib.sh
. ./lib.sh

START_EPOCH=$(date +%s)
SCN_PROJECT=ci-scn-$(od -An -N3 -tx1 /dev/urandom | tr -d ' \n')
SCN_GRAFANA_PORT=43000
SCN_FILE=""
SCN_LOCAL=""
SCN_PENDING=false
FINISHED=false
SUMMARY=""
LOG_DIR=""
COMPONENT_DIR=""
PUBLISH=""

# --- phases -----------------------------------------------------------------------------------------------------------------------

# run_phase <name> <seconds> <command...>: runs one phase, keeps its whole output in work/logs/<name>.log, records its status and
# never stops the suite
run_phase() {
  local name=$1 limit=$2 began=$SECONDS rc=0 status
  shift 2
  printf '==> %s: starting (deadline %ss)\n' "$name" "$limit"
  run_bounded "$limit" "$@" > "$LOG_DIR/$name.log" 2>&1 || rc=$?
  # A failed phase prints its whole log; a passed one only its end (the full log stays in work/logs and in the published tail)
  printf '::group::%s output\n' "$name"
  if [ "$rc" -eq 0 ]; then tail -n 80 "$LOG_DIR/$name.log"; else cat "$LOG_DIR/$name.log"; fi
  printf '::endgroup::\n'
  case $rc in
    0) status=pass ;;
    124) status="timeout" ;;
    *) status="fail($rc)" ;;
  esac
  printf '%s\t%s\t%s\n' "$name" "$status" "$((SECONDS - began))" >> "$SUMMARY"
  printf '==> %s: %s after %ss\n' "$name" "$status" "$((SECONDS - began))"
  return 0
}

skip_phase() { # skip_phase <name> <reason>
  printf '%s\tskipped(%s)\t0\n' "$1" "$2" >> "$SUMMARY"
  printf '==> %s: skipped, %s\n' "$1" "$2"
}

phase_passed() { # phase_passed <name>
  [ "$(awk -F'\t' -v n="$1" '$1 == n { s = $2 } END { print s }' "$SUMMARY")" = pass ]
}

# --- the kept scenario stack -------------------------------------------------------------------------------------------------------

# keep_line_local <log> <project>: the LOCAL_DIR of the kept project, from the runner's own instruction to stop it
# ("Stop it with: COMPOSE_PROJECT_NAME=<project> LOCAL_DIR=<dir> ./teardown.sh --purge"); exactly one match or nothing
keep_line_local() {
  local found
  found=$(sed -n "s#.*Stop it with: COMPOSE_PROJECT_NAME=$2 LOCAL_DIR=\\(/[^ ]*\\) ./teardown.sh --purge.*#\\1#p" "$1")
  case $found in
    "" | *$'\n'*) return 1 ;;
  esac
  printf '%s' "$found"
}

locate_scenario_stack() {
  SCN_LOCAL=$(keep_line_local "$LOG_DIR/scenarios.log" "$SCN_PROJECT") || SCN_LOCAL=""
  if [ -n "$SCN_LOCAL" ] && [ -f "$(dirname "$SCN_LOCAL")/dashboard-scenarios/scenarios.json" ]; then
    SCN_FILE=$(dirname "$SCN_LOCAL")/dashboard-scenarios/scenarios.json
    return 0
  fi
  SCN_FILE=""
  return 1
}

# Not lib.sh's detect_runtime: that one dies when no runtime works, which would end the suite before it removed the kept stack
choose_runtime() {
  RUNTIME=${CONTAINER_RUNTIME:-}
  if [ -z "$RUNTIME" ]; then
    if command -v podman > /dev/null 2>&1; then RUNTIME=podman; else RUNTIME=docker; fi
  fi
}

# the logs of every container of the kept project, bounded, before it is removed
save_scenario_container_logs() {
  local name dir=$COMPONENT_DIR/$SCN_PROJECT
  mkdir -p "$dir"
  choose_runtime
  "$RUNTIME" ps -a --filter "label=com.docker.compose.project=$SCN_PROJECT" --format '{{.Names}}' 2> /dev/null | while read -r name; do
    case $name in *[!A-Za-z0-9._-]* | "") continue ;; esac
    "$RUNTIME" logs --tail 3000 "$name" > "$dir/$name.log" 2>&1 || true
  done
}

# The kept project removed by its compose label, for the case where the runner died before it printed where its state is (the
# teardown script needs that directory): containers, networks and volumes of the project and nothing else
remove_scenario_project_by_label() {
  local kind ids filter="label=com.docker.compose.project=$SCN_PROJECT"
  choose_runtime
  ids=$("$RUNTIME" ps -aq --filter "$filter" 2> /dev/null) || ids=""
  # shellcheck disable=SC2086  # ids are whitespace separated container ids
  [ -z "$ids" ] || "$RUNTIME" rm -f -v $ids > /dev/null 2>&1 || true
  for kind in network volume; do
    ids=$("$RUNTIME" "$kind" ls -q --filter "$filter" 2> /dev/null) || ids=""
    # shellcheck disable=SC2086
    [ -z "$ids" ] || "$RUNTIME" "$kind" rm -f $ids > /dev/null 2>&1 || true
  done
}

teardown_scenario_stack() {
  if [ -z "$SCN_LOCAL" ]; then
    printf '%s\tfail(the kept stack was not located)\t0\n' scenarios-teardown >> "$SUMMARY"
    printf '==> scenarios-teardown: fail, the kept project %s could not be located; it is removed by label when the suite ends\n' "$SCN_PROJECT"
    return 0
  fi
  save_scenario_container_logs
  run_phase scenarios-teardown 900 env "COMPOSE_PROJECT_NAME=$SCN_PROJECT" "LOCAL_DIR=$SCN_LOCAL" ./teardown.sh --purge
  if phase_passed scenarios-teardown; then SCN_PENDING=false; fi
}

# copies the per-call logs a runner says it kept ("Logs of every script call: <dir>") as component logs of that phase
keep_script_logs() { # keep_script_logs <phase>
  local dir target file
  dir=$(sed -n 's#^Logs of every script call: \(/[^ ]*\)$#\1#p' "$LOG_DIR/$1.log" | tail -1)
  [ -n "$dir" ] && [ -d "$dir" ] || return 0
  target=$COMPONENT_DIR/$1-script-calls
  mkdir -p "$target"
  for file in "$dir"/*.log; do
    [ -f "$file" ] && [ ! -L "$file" ] || continue
    cp "$file" "$target/$(basename "$file")"
  done
}

# --- artifacts ---------------------------------------------------------------------------------------------------------------------

# the run and report directories the phases printed ("run directory: <path>"): only these are looked at
printed_directories() { # printed_directories <label>
  sed -n "s#^$1 directory: \\(/[^ ]*\\)\$#\\1#p" "$LOG_DIR"/*.log 2> /dev/null | sort -u
}

collect_artifacts() {
  local line rc=0
  set -- --out "$PUBLISH" --start "$START_EPOCH" --ui-dir "$EXAMPLE_DIR/tests/ui" --logs "$LOG_DIR" --components "$COMPONENT_DIR" --summary "$SUMMARY"
  while IFS= read -r line; do
    [ -z "$line" ] || set -- "$@" --run-dir "$line"
  done <<EOF
$(printed_directories run)
EOF
  while IFS= read -r line; do
    [ -z "$line" ] || set -- "$@" --report-dir "$line"
  done <<EOF
$(printed_directories report)
EOF
  python3 -I "$EXAMPLE_DIR/tests/ci-artifacts.py" "$@" || rc=$?
  return "$rc"
}

# --- the end -------------------------------------------------------------------------------------------------------------------------

# whatever ended the run (a signal included): the kept stack is removed and the evidence collected, once
finish_hook() {
  $FINISHED && return 0
  FINISHED=true
  if $SCN_PENDING; then
    [ -n "$SCN_LOCAL" ] || locate_scenario_stack || true
    if [ -n "$SCN_LOCAL" ]; then
      COMPOSE_PROJECT_NAME=$SCN_PROJECT LOCAL_DIR=$SCN_LOCAL ./teardown.sh --purge > "$LOG_DIR/scenarios-teardown-on-exit.log" 2>&1 || true
    else
      remove_scenario_project_by_label
    fi
  fi
  [ -d "${PUBLISH:-}" ] || collect_artifacts > /dev/null 2>&1 || true
}

main() {
  local artifact_dir=${CI_ARTIFACT_DIR:-} rc=0 failed
  [ -n "${REGISTRY_IMAGE:-}" ] || die "REGISTRY_IMAGE must name the image built from this checkout: the suite never runs a published image"
  need_commands curl jq od python3 node npm sed awk
  if [ -z "$artifact_dir" ]; then
    artifact_dir=$(mktemp -d "${TMPDIR:-/tmp}/usage-telemetry-otel-ci.XXXXXX")
  elif [ "${artifact_dir#/}" = "$artifact_dir" ]; then
    die "CI_ARTIFACT_DIR must be an absolute path, got '$artifact_dir'"
  elif [ -e "$artifact_dir" ] && { [ ! -d "$artifact_dir" ] || [ -L "$artifact_dir" ] || [ -n "$(ls -A "$artifact_dir")" ]; }; then
    die "CI_ARTIFACT_DIR must be a new or empty directory (a stale directory would mix runs): $artifact_dir"
  fi
  mkdir -p "$artifact_dir/work/logs" "$artifact_dir/work/components"
  artifact_dir=$(cd "$artifact_dir" && pwd -P)
  LOG_DIR=$artifact_dir/work/logs
  COMPONENT_DIR=$artifact_dir/work/components
  PUBLISH=$artifact_dir/publish
  SUMMARY=$artifact_dir/work/summary.tsv
  : > "$SUMMARY"
  RB_EXIT_HOOK=finish_hook
  trap finish_hook EXIT
  printf 'example CI suite: image %s, artifacts below %s\n' "$REGISTRY_IMAGE" "$artifact_dir"

  run_phase unit 1500 ./tests/unit-test.sh
  run_phase dashboard-unit 900 ./tests/dashboard-unit-test.sh
  run_phase dashboard-promql 900 ./tests/dashboard-promql-test.sh
  run_phase suite-selftest 600 ./tests/ci-suite-test.sh

  run_phase browser-setup 1200 ./test-ui.sh --setup-only
  if phase_passed browser-setup; then
    run_phase ui-tooling 600 ui_tooling_checks
    run_phase ui-selftest 900 ./test-ui.sh --no-install --self-test
    run_phase ui-runner 1500 bash tests/ui/test/runner-test.sh --bash "$(command -v bash)"
    run_phase ui-clone 300 bash tests/ui/test/clone-dir-test.sh
    run_phase ui-real-tooling 2400 bash tests/ui/test/real-tooling-test.sh --bash "$(command -v bash)" --with-npm
    run_phase dashboard-test 900 ./tests/dashboard-test.sh
    run_phase ui-seeded 1200 ./test-ui.sh --no-install
    run_phase ui-screenshots 900 ./screenshots.sh --no-install
  else
    for failed in ui-tooling ui-selftest ui-runner ui-clone ui-real-tooling dashboard-test ui-seeded ui-screenshots; do skip_phase "$failed" "browser-setup did not pass"; done
  fi

  run_phase lifecycle 2700 ./tests/lifecycle-test.sh
  keep_script_logs lifecycle

  SCN_PENDING=true
  run_phase scenarios 2400 ./tests/dashboard-scenarios.sh --keep --project "$SCN_PROJECT" --grafana-port "$SCN_GRAFANA_PORT"
  keep_script_logs scenarios
  locate_scenario_stack || true
  if phase_passed scenarios && phase_passed browser-setup && [ -n "$SCN_FILE" ]; then
    run_phase ui-scenario 900 ./test-ui.sh --no-install --scenario "$SCN_FILE" --grafana-url "http://127.0.0.1:$SCN_GRAFANA_PORT"
  else
    skip_phase ui-scenario "the scenario stack or the browser tooling is not usable"
  fi
  teardown_scenario_stack

  # the table is final before the collector reads it; the collector's own status is added after it
  summarize
  collect_artifacts || rc=$?
  if [ "$rc" -eq 0 ]; then printf 'artifacts\tpass\t0\n' >> "$SUMMARY"; else printf 'artifacts\tfail(%s)\t0\n' "$rc" >> "$SUMMARY"; fi
  summarize
  if awk -F'\t' '$2 != "pass" { bad = 1 } END { exit bad }' "$SUMMARY"; then
    printf 'All phases passed.\n'
    return 0
  fi
  printf 'The suite FAILED: %s\n' "$(awk -F'\t' '$2 != "pass" { printf "%s=%s ", $1, $2 }' "$SUMMARY")"
  return 1
}

summarize() {
  printf '\nphase\tstatus\tseconds\n'
  cat "$SUMMARY"
}

# the browser tooling's own static checks (type check, static rules, Node unit tests)
ui_tooling_checks() {
  npm --prefix tests/ui run typecheck && npm --prefix tests/ui run static && npm --prefix tests/ui run unit
}

# a test can load the functions without running the suite
if [ "${CI_SUITE_SOURCE_ONLY:-}" != 1 ]; then
  main "$@"
fi
