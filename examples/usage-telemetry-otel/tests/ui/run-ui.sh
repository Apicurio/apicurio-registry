#!/usr/bin/env bash
# Shared runner of ./test-ui.sh and ./screenshots.sh: Playwright tests against a Grafana that is already running. It never
# starts, restarts or purges a stack and sends no traffic to the Registry. It is not a read-only command: it downloads locked npm
# packages and Chromium when they are missing and writes its own output directories.
#
# Usage: ./test-ui.sh      [--state FILE] [--grafana-url URL] [--scenario FILE] [--self-test] [--setup-only] [--no-install]
#        ./screenshots.sh  [--state FILE] [--grafana-url URL] [--setup-only] [--no-install]
#   --state FILE        the run state of the demo run to test (default .local/projects/<project>/run-state.json, the project
#                       being COMPOSE_PROJECT_NAME or usage-telemetry-otel). It is read once, validated and sealed; the invocation
#                       works on that snapshot. Its recorded Grafana endpoint and datasource are used unless overridden.
#   --grafana-url URL   a Grafana other than the one the state records (an explicitly set GRAFANA_URL does the same). An http or
#                       https URL without credentials, query or fragment, served at the root or below a path; nothing else sets
#                       the address.
#   --scenario FILE     test-ui.sh only: the scenarios.json of ./tests/dashboard-scenarios.sh --keep (schemaVersion 1). Runs only
#                       the process switch test on its "processes" scenario and needs --grafana-url (or GRAFANA_URL), the Grafana
#                       of that scenario stack; the run state is not used. Without this option that test is NOT SELECTED.
#   --self-test         test-ui.sh only: browser self-checks that need no Grafana and no state (what a trace stores)
#   --setup-only        make the locked dependencies and the browser ready, prove that the Playwright commands run and that the
#                       browser starts, then stop. It needs no run state; an input you name (--state, --scenario, --grafana-url,
#                       GRAFANA_URL, GRAFANA_DATASOURCE_UID) is still checked.
#   --no-install        never download anything; missing or different dependencies, a Playwright command that cannot run or a
#                       browser that does not start are errors
# Environment (the public inputs; every other UI_*, PW_* and PLAYWRIGHT_* variable is ignored and reported):
#   GRAFANA_DATASOURCE_UID  the Prometheus datasource UID of the Grafana under test (default: the state's, or the example's own)
#   GRAFANA_ADMIN_PASSWORD  the Grafana admin password typed into the login form (default admin, the demo account)
#   PLAYWRIGHT_BROWSERS_PATH  an absolute, normal path for the browser download (default tests/ui/.browsers, git-ignored; the
#                             shared Playwright cache is never used unless you name it)
#   PLAYWRIGHT_DOWNLOAD_HOST, PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST, PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT  download mirror settings
#   UI_INSTALL_TIMEOUT (seconds, default 900) bounds each install, UI_TEST_TIMEOUT (default 600) the browser run
# One invocation at a time per checkout: a claim, the symbolic link tests/ui/.invocation-claim, is held from before the first download to
# the end; a competing invocation is refused, and a claim left by a crash is never removed for you. Each invocation owns fresh
# directories, tests/ui/test-results/<run>/ and tests/ui/playwright-report/<run>/, never reuses or prunes those of another, and
# prints the paths of the report, the screenshots, every failure screenshot and every trace, also when it is interrupted. Exit
# status: 0 only if every test ran and passed; the status of a failed npm, browser install, probe or browser run is passed on as
# it is (124: a deadline); INT ends it with 130, TERM and HUP with 143; invalid input, a competing invocation or a failed check
# exits 1.
set -euo pipefail

# --- 1. options: help and every option error need no tool and no project file ----------------------------------------------

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
bad() { printf 'error: %s\n' "$*" >&2; exit 1; }

what=${1:-}
[ $# -ge 1 ] || bad "internal: the runner is started by ./test-ui.sh or ./screenshots.sh"
shift
case "$what" in
  tests | screenshots) ;;
  *) bad "internal: unknown runner mode '$what'" ;;
esac
SELF=./test-ui.sh
[ "$what" = screenshots ] && SELF=./screenshots.sh
STATE_FILE=""
SCENARIO_FILE=""
URL_FLAG=""
self_test=false
setup_only=false
install=true
while [ $# -gt 0 ]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --state) [ $# -ge 2 ] || bad "--state needs a file"; STATE_FILE=$2; shift 2 ;;
    --grafana-url) [ $# -ge 2 ] || bad "--grafana-url needs a URL"; URL_FLAG=$2; shift 2 ;;
    --scenario)
      [ "$what" = tests ] || bad "--scenario belongs to ./test-ui.sh; ./screenshots.sh photographs the seeded run"
      [ $# -ge 2 ] || bad "--scenario needs a file"
      SCENARIO_FILE=$2
      shift 2
      ;;
    --self-test)
      [ "$what" = tests ] || bad "--self-test belongs to ./test-ui.sh"
      self_test=true
      shift
      ;;
    --setup-only) setup_only=true; shift ;;
    --no-install) install=false; shift ;;
    *) usage >&2; bad "unknown option '$1'" ;;
  esac
done
if [ -n "$SCENARIO_FILE" ] && [ -n "$STATE_FILE" ]; then bad "--scenario and --state are different inputs; give one of them"; fi
if $self_test && { [ -n "$SCENARIO_FILE" ] || [ -n "$STATE_FILE" ] || [ -n "$URL_FLAG" ]; }; then bad "--self-test needs no state, scenario or Grafana URL"; fi
STATE_GIVEN=false
[ -z "$STATE_FILE" ] || STATE_GIVEN=true

# the public environment, read before anything else changes it
GIVEN_GRAFANA_URL=${GRAFANA_URL:-}
GIVEN_DATASOURCE_UID=${GRAFANA_DATASOURCE_UID:-}
GIVEN_BROWSERS_PATH=${PLAYWRIGHT_BROWSERS_PATH:-}
INSTALL_TIMEOUT=${UI_INSTALL_TIMEOUT:-900}
TEST_TIMEOUT=${UI_TEST_TIMEOUT:-600}
for deadline in "UI_INSTALL_TIMEOUT=$INSTALL_TIMEOUT" "UI_TEST_TIMEOUT=$TEST_TIMEOUT"; do
  case "${deadline#*=}" in
    "" | *[!0-9]* | 0 | 0?*) bad "${deadline%%=*} must be a whole number of seconds from 1 to 86400, got '${deadline#*=}'" ;;
  esac
  [ "${#deadline}" -le 30 ] && [ "${deadline#*=}" -le 86400 ] || bad "${deadline%%=*} must be at most 86400 seconds"
done

# --- 2. a deterministic environment ---------------------------------------------------------------------------------------
# Internal transport variables and Playwright's own controls are not inputs. Reporter variables would send the report or the JSON
# file elsewhere, delete a directory that is not ours or open a server; the browser path and the download mirrors are public.

IGNORED=""
for name in $(compgen -e | grep -E '^(UI|PW|PLAYWRIGHT)_' || true); do
  case "$name" in
    PLAYWRIGHT_BROWSERS_PATH | PLAYWRIGHT_DOWNLOAD_HOST | PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST | PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT) continue ;;
    UI_INSTALL_TIMEOUT | UI_TEST_TIMEOUT) unset "$name"; continue ;;
  esac
  IGNORED="$IGNORED $name"
  unset "$name"
done
unset INIT_CWD 2> /dev/null || true

# --- 3. prerequisites ----------------------------------------------------------------------------------------------------

command -v node > /dev/null 2>&1 || bad "'node' is required (Node.js 20 or later) but was not found in PATH"
node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 20 ? 0 : 1)' || bad "Node.js 20 or later is required, found $(node -p process.versions.node)"
command -v date > /dev/null 2>&1 || bad "'date' is required but was not found in PATH"

cd "$(dirname "$0")/../.."
# shellcheck source=lib.sh
. ./lib.sh
validate_project
[ -n "$STATE_FILE" ] || STATE_FILE=$(state_dir)/run-state.json
UI_DIR=$(cd tests/ui && pwd -P)
CLI=$UI_DIR/lib/cli.mjs
PW=$UI_DIR/node_modules/.bin/playwright
[ -z "$IGNORED" ] || info "ignoring inherited variables that are not inputs of this runner:$IGNORED"

# refuse_links <path> <what>: nothing below an existing symbolic link is written (links directly under / are the macOS aliases)
refuse_links() {
  local path=$1 what=$2 part="" prefix=""
  local IFS=/
  for part in $path; do
    [ -n "$part" ] || continue
    prefix="$prefix/$part"
    if [ -L "$prefix" ]; then
      case "$prefix" in
        /*/*) bad "$what ($path) passes through the symbolic link $prefix; give the physical path. Nothing was changed" ;;
      esac
    fi
  done
}

# --- 4. paths: resolved once, absolute, before any command changes directory ------------------------------------------------

BROWSERS=$UI_DIR/.browsers
if [ -n "$GIVEN_BROWSERS_PATH" ]; then
  node "$CLI" path --absolute "$GIVEN_BROWSERS_PATH" 2> /dev/null || bad "PLAYWRIGHT_BROWSERS_PATH must be an absolute path without a '.' or '..' part, a repeated or trailing slash or control characters (Playwright resolves a relative one from a directory that differs between its commands), got $(printf '%q' "$GIVEN_BROWSERS_PATH")"
  BROWSERS=$GIVEN_BROWSERS_PATH
fi
refuse_links "$BROWSERS" "the browser directory"
for owned in node_modules test-results playwright-report; do
  if [ -L "$UI_DIR/$owned" ]; then bad "$UI_DIR/$owned is a symbolic link; refusing to write through it"; fi
done
export PLAYWRIGHT_BROWSERS_PATH=$BROWSERS

# --- 5. the input: read once, validated and resolved by one module, before any claim, download, browser or directory --------
# Structured results are the standard output of the command line module and nothing else; its diagnostics go to the terminal.

URL=${URL_FLAG:-$GIVEN_GRAFANA_URL}
if $self_test || { $setup_only && ! $STATE_GIVEN && [ -z "$SCENARIO_FILE" ]; }; then
  # no run state is needed: only the overrides that were named are validated
  MODE=selftest
  if ! BUNDLE=$(node "$CLI" prepare --mode selftest ${URL:+--grafana-url "$URL"} ${GIVEN_DATASOURCE_UID:+--datasource-uid "$GIVEN_DATASOURCE_UID"}); then exit 1; fi
else
  if [ -n "$SCENARIO_FILE" ]; then
    MODE=scenario
    INPUT=$SCENARIO_FILE
    [ -f "$INPUT" ] || bad "$INPUT does not exist or is not a file"
  else
    MODE=seeded
    INPUT=$STATE_FILE
    [ -e "$INPUT" ] || bad "no run state at $INPUT. Run ./demo.sh first (or give --state FILE)"
    [ -f "$INPUT" ] || bad "$INPUT is not a file"
  fi
  [ -s "$INPUT" ] || bad "$INPUT is empty"
  if ! BUNDLE=$(node "$CLI" prepare --mode "$MODE" --flows "$what" --input "$INPUT" \
    ${URL:+--grafana-url "$URL"} ${GIVEN_DATASOURCE_UID:+--datasource-uid "$GIVEN_DATASOURCE_UID"} --default-datasource-uid "$GRAFANA_DATASOURCE_UID"); then
    exit 1
  fi
fi

# --- 6. one invocation per checkout --------------------------------------------------------------------------------------
# The claim is one symbolic link, tests/ui/.invocation-claim, made by one atomic step (ln -s) whose target text IS the owner
# identity: claim:pid=<pid>:runner=<wrapper>:token=<unique>. There is no empty or half recorded state to be left behind, nothing is
# read from the claim but that one short target, and whether this invocation owns the claim is decided by what is there afterwards,
# never by the return code of the command that made it: a signal that kills that command after it created the link still leaves a
# claim that is recognizably ours. The way out is installed before the claim is made, and a signal that arrives while it is being
# made is only noted and acted on once the outcome is known. Only a link whose target is exactly this invocation's record is ever
# removed; any other entry (a link of another invocation, a file, a FIFO, a directory left by an older version) is left alone.

CLAIM=$UI_DIR/.invocation-claim
CLAIM_RECORD="claim:pid=$$:runner=${SELF#./}:token=$$-$SECONDS-$RANDOM-$RANDOM"
CLAIM_HELD=false
PENDING_SIGNAL=""
FINISHED=false
SUMMARY_DONE=false
RUN_DIR=""
REPORT_DIR=""
LABEL=""

# print_summary: what this invocation left, once; best effort, it never changes a status
print_summary() {
  $SUMMARY_DONE && return 0
  SUMMARY_DONE=true
  [ -n "$RUN_DIR" ] || return 0
  node "$CLI" summary --run-dir "$RUN_DIR" --report-dir "$REPORT_DIR" --ui-dir "$UI_DIR" --label "$LABEL" || true
}

# claim_target: sets CLAIM_NOW to the target of the claim link (empty when it is not a link or cannot be read); a read that was
# itself killed is repeated, so a signal aimed at a whole process group cannot hide a claim that is there
claim_target() {
  local attempt=0
  CLAIM_NOW=""
  [ -L "$CLAIM" ] || return 1
  while [ "$attempt" -lt 3 ]; do
    if CLAIM_NOW=$(readlink "$CLAIM" 2> /dev/null); then return 0; fi
    CLAIM_NOW=""
    [ -L "$CLAIM" ] || return 1
    attempt=$((attempt + 1))
  done
  return 1
}

# release_claim: removes the claim link when, and only when, it is a link with this invocation's own record
# shellcheck disable=SC2329 # called by finish
release_claim() {
  $CLAIM_HELD || return 0
  CLAIM_HELD=false
  if claim_target && [ "$CLAIM_NOW" = "$CLAIM_RECORD" ]; then
    rm -f "$CLAIM"
  fi
}

# finish: every way out runs it once (the EXIT trap, or run_bounded's hook when a signal ends a supervised command); a signal
# during it changes nothing, and it never replaces the status the shell is exiting with
# shellcheck disable=SC2329 # called through the EXIT trap and RB_EXIT_HOOK
finish() {
  $FINISHED && return 0
  FINISHED=true
  trap '' INT TERM HUP
  set +e
  print_summary
  release_claim
  return 0
}

trap finish EXIT
trap 'PENDING_SIGNAL=130' INT
trap 'PENDING_SIGNAL=143' TERM
trap 'PENDING_SIGNAL=143' HUP
if [ ! -e "$CLAIM" ] && [ ! -L "$CLAIM" ]; then
  ln -s "$CLAIM_RECORD" "$CLAIM" 2> /dev/null || true
  # the link lands inside an existing directory that appeared in the meantime: take back exactly the link made here
  if [ -d "$CLAIM" ] && [ ! -L "$CLAIM" ] && [ -L "$CLAIM/$CLAIM_RECORD" ]; then rm -f "$CLAIM/$CLAIM_RECORD"; fi
fi
if claim_target && [ "$CLAIM_NOW" = "$CLAIM_RECORD" ]; then CLAIM_HELD=true; fi
trap 'exit 130' INT
trap 'exit 143' TERM
# HUP ends the supervised process group like TERM does (a plain exit would orphan it; a self-sent TERM crashes bash 3.2)
trap '_rb_signal 143' HUP
RB_EXIT_HOOK=finish
[ -z "$PENDING_SIGNAL" ] || exit "$PENDING_SIGNAL"
if ! $CLAIM_HELD; then
  if [ ! -e "$CLAIM" ] && [ ! -L "$CLAIM" ]; then
    bad "could not make the claim $CLAIM (the link could not be created); nothing was claimed"
  fi
  holder=""
  detail="it is not a claim record (a file, a directory, a FIFO or a link of another kind), so it was not read"
  if claim_target; then
    # at most 512 printable characters of the one target; the target is only text and is never opened
    holder=$(printf '%s' "$CLAIM_NOW" | head -c 512 | tr -cd '[:print:]') || holder=""
    case "$holder" in
      claim:pid=[0-9]*:*)
        holder_pid=${holder#claim:pid=}
        holder_pid=${holder_pid%%:*}
        detail="its record says: $holder"
        case "$holder_pid" in
          *[!0-9]* | "") detail="$detail; its process could not be read" ;;
          *)
            if [ "${#holder_pid}" -le 9 ] && kill -0 "$holder_pid" 2> /dev/null; then detail="$detail; process $holder_pid is running"; else detail="$detail; process $holder_pid is not running, so the claim may be stale"; fi
            ;;
        esac
        ;;
      *) detail="its target is not a claim record, so it was not read" ;;
    esac
  fi
  bad "another invocation holds $CLAIM ($detail). Wait for it. This runner never removes a claim it did not make: if you have checked that it is stale, remove that entry yourself"
fi

# --- 7. tooling: every locked package, every Playwright command the run invokes, and a browser that really starts ------------

TOOL_PROBLEMS=""
TOOL_STATUS=1
DRYRUN_TEXT=""
# check_tooling: sets TOOL_PROBLEMS (empty when ready), TOOL_STATUS (124 for a deadline, else 1) and DRYRUN_TEXT, the installer's
# own report of where it would write. The locked packages come first; only then are the commands run that a test run and an
# install invoke, because a package can have the right manifest and still lack a file its command line loads.
check_tooling() {
  local rc=0
  TOOL_PROBLEMS=""
  TOOL_STATUS=1
  DRYRUN_TEXT=""
  TOOL_PROBLEMS=$(node "$CLI" installed --dir "$UI_DIR") || rc=$?
  case "$rc" in
    0) ;;
    1) return 0 ;;
    *) bad "the dependency check itself failed (status $rc)" ;;
  esac
  rc=0
  bcap_all 120 "$PW" install --dry-run chromium || rc=$?
  if [ "$rc" -ne 0 ]; then
    [ "$rc" -ne 124 ] || TOOL_STATUS=124
    TOOL_PROBLEMS="playwright install --dry-run chromium failed with status $rc:
$(printf '%s\n' "$BCAP_OUT" | head -12)"
    return 0
  fi
  DRYRUN_TEXT=$BCAP_OUT
  rc=0
  bcap_all 120 "$PW" test --list --config="$UI_DIR/lib/readiness/playwright.config.ts" || rc=$?
  if [ "$rc" -ne 0 ]; then
    [ "$rc" -ne 124 ] || TOOL_STATUS=124
    TOOL_PROBLEMS="playwright test --list failed with status $rc:
$(printf '%s\n' "$BCAP_OUT" | head -12)"
  fi
  return 0
}

check_tooling
if [ -n "$TOOL_PROBLEMS" ]; then
  if ! $install; then
    printf 'error: the locked tooling in %s is not usable:\n%s\nRun %s --setup-only (or without --no-install).\n' "$UI_DIR" "$TOOL_PROBLEMS" "$SELF" >&2
    exit "$TOOL_STATUS"
  fi
  command -v npm > /dev/null 2>&1 || bad "'npm' is required to install the locked dependencies but was not found in PATH"
  info "repairing the locked dependencies (npm ci in tests/ui, at most ${INSTALL_TIMEOUT}s):"
  printf '%s\n' "$TOOL_PROBLEMS"
  rc=0
  run_bounded "$INSTALL_TIMEOUT" npm ci --prefix "$UI_DIR" --no-audit --no-fund || rc=$?
  if [ "$rc" -ne 0 ]; then
    printf 'error: npm ci failed with status %s%s\n' "$rc" "$([ "$rc" -eq 124 ] && echo ' (its deadline passed)')" >&2
    exit "$rc"
  fi
  check_tooling
  if [ -n "$TOOL_PROBLEMS" ]; then
    printf 'error: npm ci finished but the tooling is still not usable:\n%s\n' "$TOOL_PROBLEMS" >&2
    exit "$TOOL_STATUS"
  fi
fi
# What Playwright would write to is checked before anything can launch the browser or install it, with or without --no-install: the
# launcher writes a marker file in the browser directories it validates and follows a link left in its place
destination_mode=launch
$install && destination_mode=install
rc=0
destinations=$(printf '%s\n' "$DRYRUN_TEXT" | node "$CLI" browser-destinations --root "$BROWSERS" --for "$destination_mode") || rc=$?
case "$rc" in
  0) ;;
  1) printf 'error: Playwright would write outside the browser directory %s (or through a link inside it); nothing was installed or started:\n%s\n' "$BROWSERS" "$destinations" >&2; exit 1 ;;
  *) bad "the browser destination check itself failed (status $rc)" ;;
esac
if $install; then
  info "making sure Chromium is present in $BROWSERS (a download only when missing, at most ${INSTALL_TIMEOUT}s)"
  rc=0
  # the installer deletes browser revisions that no project references: never in a directory that may hold other revisions
  export PLAYWRIGHT_SKIP_BROWSER_GC=1
  run_bounded "$INSTALL_TIMEOUT" "$PW" install chromium || rc=$?
  unset PLAYWRIGHT_SKIP_BROWSER_GC
  if [ "$rc" -ne 0 ]; then
    printf 'error: playwright install chromium failed with status %s%s\n' "$rc" "$([ "$rc" -eq 124 ] && echo ' (its deadline passed)')" >&2
    exit "$rc"
  fi
fi
rc=0
run_bounded 120 node "$UI_DIR/lib/probe-browser.mjs" || rc=$?
if [ "$rc" -ne 0 ]; then
  printf 'error: Chromium does not start from %s (status %s)%s\n' "$BROWSERS" "$rc" "$($install || echo "; run $SELF --setup-only to install it")" >&2
  exit "$rc"
fi
if $setup_only; then
  info "setup finished: the locked dependencies are whole, the Playwright commands run and Chromium starts"
  exit 0
fi

# --- 8. this invocation's own directories and the sealed input ---------------------------------------------------------------

for root in test-results playwright-report; do
  [ -e "$UI_DIR/$root" ] && [ ! -d "$UI_DIR/$root" ] && bad "$UI_DIR/$root exists and is not a directory"
  mkdir -p "$UI_DIR/$root"
done
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$-$what"
RUN_DIR=$UI_DIR/test-results/$RUN_ID
REPORT_DIR=$UI_DIR/playwright-report/$RUN_ID
LABEL="$what ($RUN_ID)"
[ ! -e "$REPORT_DIR" ] && [ ! -L "$REPORT_DIR" ] || bad "$REPORT_DIR already exists"
mkdir "$RUN_DIR" || bad "could not create $RUN_DIR, which must not exist yet"
mkdir "$RUN_DIR/screenshots"
if ! SHA=$(printf '%s' "$BUNDLE" | node "$CLI" seal --run-dir "$RUN_DIR" --report-dir "$REPORT_DIR"); then
  bad "the input could not be sealed"
fi
case "$SHA" in
  *[!0-9a-f]* | "") bad "the sealing step did not return a SHA-256 hash" ;;
esac
[ "${#SHA}" -eq 64 ] || bad "the sealing step did not return a SHA-256 hash"
export UI_BUNDLE=$RUN_DIR/input.sealed.json
export UI_BUNDLE_SHA256=$SHA
export GRAFANA_ADMIN_PASSWORD
PROJECT_NAMES=usage
[ "$what" = screenshots ] && PROJECT_NAMES=screenshots
[ "$MODE" = scenario ] && PROJECT_NAMES=process-switch
[ "$MODE" = selftest ] && PROJECT_NAMES=self-test

printf '%s' "$BUNDLE" | node "$CLI" describe --runner "$SELF"
if [ "$MODE" = seeded ] && [ "$what" = tests ]; then
  info "process switch test: NOT SELECTED (no --scenario FILE); it is not covered by this run"
fi
[ "$MODE" != scenario ] || info "seeded run tests: NOT SELECTED (scenario mode); they are not covered by this run"
info "run directory: $RUN_DIR"
info "report directory: $REPORT_DIR"

# --- 9. the browser run and what it left ---------------------------------------------------------------------------------

cd "$UI_DIR"
rc=0
run_bounded "$TEST_TIMEOUT" "$PW" test --project="$PROJECT_NAMES" || rc=$?

printf '\n'
print_summary
if [ "$rc" -eq 124 ]; then
  printf 'error: the browser run passed its deadline of %ss (UI_TEST_TIMEOUT)\n' "$TEST_TIMEOUT" >&2
fi
if [ "$rc" -ne 0 ]; then
  exit "$rc"
fi
verdict_screenshots=""
[ "$what" = screenshots ] && verdict_screenshots=overview,filtered
node "$CLI" verdict --run-dir "$RUN_DIR" --screenshots "$verdict_screenshots" || exit 1
exit 0
