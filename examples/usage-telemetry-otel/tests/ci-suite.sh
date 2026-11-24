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
# (absolute, new or empty; default a new temporary directory), CI_CLEANUP_SECONDS (default 5) and CI_COLLECT_SECONDS (default 300).
# A cancelled job gets SIGINT, about 7.5 seconds later SIGTERM and 2.5 seconds after that it is killed (GitHub's documentation on
# cancelling a workflow), so the cleanup after a signal is BEST EFFORT: it collects the evidence first (without container logs, whose
# runtime calls can hang), then removes the kept stack, each step limited to CI_CLEANUP_SECONDS, and a second signal ends it at once.
# An upload after a cancellation is therefore not guaranteed. What is guaranteed is that publish/ never holds a partial collection:
# the collector builds it beside publish/ and renames it into place, and a publish/ without a MANIFEST.json is set aside. Everything this run produces goes below CI_ARTIFACT_DIR: work/ holds
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
PRIVATE_REPORT=""
# What each step of the cleanup after a signal or a failed removal may take, and what the normal collection may take (seconds)
CLEANUP_SECONDS=${CI_CLEANUP_SECONDS:-5}
COLLECT_SECONDS=${CI_COLLECT_SECONDS:-300}

# --- the status table ---------------------------------------------------------------------------------------------------------------

# Every phase the suite must have run, each once and each passed. The suite passes only when the table says so: a row that is missing,
# a row that is there twice, a row nobody expects and a row that could not be written all fail it (a table that is silently short
# would otherwise read as "nothing failed").
REQUIRED_PHASES="unit dashboard-unit dashboard-promql suite-selftest browser-setup ui-tooling ui-selftest ui-runner ui-clone ui-real-tooling dashboard-test ui-seeded ui-screenshots lifecycle scenarios ui-scenario scenarios-teardown artifacts"
ROWS_FAILED=false

# record_row <name> <status> <seconds>
record_row() {
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$SUMMARY" || ROWS_FAILED=true
}

# table_problems: what is wrong with the table, empty when nothing is
table_problems() {
  $ROWS_FAILED && printf 'a status row could not be written '
  awk -F'\t' -v required="$REQUIRED_PHASES" '
    BEGIN { n = split(required, names, " "); for (i = 1; i <= n; i++) want[names[i]] = 1 }
    { seen[$1]++; status[$1] = $2; if (!($1 in want)) extra = extra " " $1 }
    END {
      for (i = 1; i <= n; i++) {
        k = names[i]
        if (seen[k] == 0) printf "%s=missing ", k
        else if (seen[k] > 1) printf "%s=duplicate ", k
        else if (status[k] != "pass") printf "%s=%s ", k, status[k]
      }
      if (extra != "") printf "unexpected rows:%s ", extra
    }' "$SUMMARY" || printf 'the status table could not be read '
}

# --- phases -----------------------------------------------------------------------------------------------------------------------

# run_phase <name> <seconds> <command...>: runs one phase, keeps its whole output in work/logs/<name>.log, records its status and
# never stops the suite
run_phase() {
  local name=$1 limit=$2 began=$SECONDS rc=0 status
  shift 2
  printf '==> %s: starting (deadline %ss)\n' "$name" "$limit"
  run_bounded "$limit" "$@" > "$LOG_DIR/$name.log" 2>&1 || rc=$?
  # The log is not echoed: it can hold anything the phase's tools printed, and the job console is not covered by the artifact
  # allowlist. It stays in work/logs and its checked tail is published by the collector.
  case $rc in
    0) status=pass ;;
    124) status="timeout" ;;
    *) status="fail($rc)" ;;
  esac
  record_row "$name" "$status" "$((SECONDS - began))"
  printf '==> %s: %s after %ss\n' "$name" "$status" "$((SECONDS - began))"
  return 0
}

skip_phase() { # skip_phase <name> <reason>
  record_row "$1" "skipped($2)" 0
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

# save_container_logs <compose project>: the logs of every container of the project, each runtime call under a deadline. They are
# collected by the allowlist like any other file; the workflow's own raw `docker compose logs` step is not used for this example
save_container_logs() {
  local name dir=$COMPONENT_DIR/$1 names
  mkdir -p "$dir"
  choose_runtime
  rtq ps -a --filter "label=com.docker.compose.project=$1" --format '{{.Names}}' 2> /dev/null || return 0
  names=$BCAP_OUT
  while read -r name; do
    case $name in *[!A-Za-z0-9._-]* | "") continue ;; esac
    run_bounded "$PROBE_TIMEOUT" "$RUNTIME" logs --tail 3000 "$name" > "$dir/$name.log" 2>&1 || true
  done <<EOT
$names
EOT
}

teardown_scenario_stack() {
  if [ -z "$SCN_LOCAL" ]; then
    # Nothing is removed by guessing: a project that the runner did not say it kept (it may have refused the name because something
    # else already uses it) is not this suite's to delete, so it is not selected by label or by name. It is named here for a person.
    record_row scenarios-teardown "fail(the kept stack was not located)" 0
    printf '==> scenarios-teardown: fail, the project %s was not reported as kept by the scenario runner, so nothing was removed\n' "$SCN_PROJECT"
    return 0
  fi
  save_container_logs "$SCN_PROJECT"
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

# the run directories the phases printed ("run directory: <path>"): only these are looked at. The Playwright report is never collected
printed_directories() { # printed_directories <label>
  sed -n "s#^$1 directory: \\(/[^ ]*\\)\$#\\1#p" "$LOG_DIR"/*.log 2> /dev/null | sort -u
}

# collect_artifacts <seconds>: the collector under a deadline of its own
collect_artifacts() {
  local line rc=0 limit=$1
  set -- --out "$PUBLISH" --start "$START_EPOCH" --ui-dir "$EXAMPLE_DIR/tests/ui" --logs "$LOG_DIR" --components "$COMPONENT_DIR" --summary "$SUMMARY"
  while IFS= read -r line; do
    [ -z "$line" ] || set -- "$@" --run-dir "$line"
  done <<EOF
$(printed_directories run)
EOF
  run_bounded "$limit" python3 -I "$EXAMPLE_DIR/tests/ci-artifacts.py" --private-report "$PRIVATE_REPORT" "$@" || rc=$?
  return "$rc"
}

# --- the end -------------------------------------------------------------------------------------------------------------------------

# publish/ is either a complete collection (it has its MANIFEST.json) or absent: the collector renames a finished staging directory
# into place, and a directory that is there without a manifest (a collector that died some other way) is set aside, never uploaded
drop_incomplete_publish() {
  if [ -d "$PUBLISH" ] && [ ! -f "$PUBLISH/MANIFEST.json" ]; then
    mv "$PUBLISH" "$(dirname "$PUBLISH")/work/incomplete-publish.$$" || true
  fi
}

# Whatever ended the run (a signal included), best effort within what a cancelled job is given (see the header). The evidence is
# collected FIRST, from what is on disk and without runtime calls, so a removal that hangs cannot take the snapshot with it; then the
# kept stack is removed, every step under CI_CLEANUP_SECONDS. A second signal ends the hook at once, and what it leaves is either a
# complete publish/ or none.
finish_hook() {
  local PROBE_TIMEOUT=$CLEANUP_SECONDS
  $FINISHED && return 0
  FINISHED=true
  drop_incomplete_publish
  if [ ! -f "${PUBLISH:-/nonexistent}/MANIFEST.json" ]; then
    record_row suite interrupted 0
    if $SCN_PENDING; then [ -n "$SCN_LOCAL" ] || locate_scenario_stack || true; fi
    collect_artifacts "$CLEANUP_SECONDS" > /dev/null 2>&1 || true
    drop_incomplete_publish
  fi
  if $SCN_PENDING; then
    if [ -n "$SCN_LOCAL" ]; then
      run_bounded "$CLEANUP_SECONDS" env "COMPOSE_PROJECT_NAME=$SCN_PROJECT" "LOCAL_DIR=$SCN_LOCAL" ./teardown.sh --purge > "$LOG_DIR/scenarios-teardown-on-exit.log" 2>&1 || true
    fi
  fi
}

main() {
  local artifact_dir=${CI_ARTIFACT_DIR:-} rc=0 failed problems
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
  PRIVATE_REPORT=$artifact_dir/work/artifact-report.json
  : > "$SUMMARY"
  RB_EXIT_HOOK=finish_hook
  trap finish_hook EXIT
  # The image name is not printed: a registry address can carry credentials, and the job console is not covered by the allowlist
  printf 'example CI suite: artifacts below %s\n' "$artifact_dir"

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

  save_container_logs "$PROJECT"
  # the table is final before the collector reads it; the collector's own status is added after it
  summarize
  collect_artifacts "$COLLECT_SECONDS" || rc=$?
  drop_incomplete_publish
  if [ "$rc" -eq 0 ]; then record_row artifacts pass 0; else record_row artifacts "fail($rc)" 0; fi
  summarize
  problems=$(table_problems)
  if [ -z "$problems" ]; then
    printf 'All phases passed.\n'
    return 0
  fi
  printf 'The suite FAILED: %s\n' "$problems"
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
