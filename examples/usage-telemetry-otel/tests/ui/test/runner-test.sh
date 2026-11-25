#!/usr/bin/env bash
# shellcheck disable=SC2012,SC2016 # ls only counts files whose names this script chose; the single-quoted text is script code for another shell
# Regressions of the public runner (./test-ui.sh, ./screenshots.sh and tests/ui/run-ui.sh) in disposable copies of the example with
# controlled stand-ins for npm, the Playwright command line and the browser package: admission and release of the per-checkout
# claim, exact status propagation, environment hygiene, browser path ownership, installed-package and browser readiness, and
# input validation before any effect. The stand-ins prove how the runner behaves at its boundaries; they do not prove that npm or
# a browser behaves in any particular way. No network, no Grafana, no stack, no browser: it runs in a few minutes.
#
# Usage: bash tests/ui/test/runner-test.sh [--source EXAMPLE_DIR] [--bash BASH_PATH]
#   --source DIR   the example directory whose runner is tested (default: this checkout); the same cases can be pointed at a
#                  previous copy of the runner to see them fail
#   --bash PATH    the interpreter the runner itself must run under (default: the interpreter that runs this script). It is
#                  pinned first in the PATH of every runner invocation, so both `#!/usr/bin/env bash` hops (the wrapper and
#                  run-ui.sh) resolve to it, and the first case asserts the BASH_VERSION each hop really reports. Running
#                  this script under one shell says nothing about the shell of the runner unless that PATH is pinned.
# Exits 0 only if every case passed.
set -o pipefail

SRC=$(cd "$(dirname "$0")/../../.." && pwd -P)
HERE=$(cd "$(dirname "$0")" && pwd -P)
PINNED=""
while [ $# -gt 0 ]; do
  case "$1" in
    --source) [ -n "${2:-}" ] || { echo "--source needs a directory" >&2; exit 2; }; SRC=$(cd "$2" && pwd -P); shift 2 ;;
    --bash) [ -n "${2:-}" ] || { echo "--bash needs an interpreter" >&2; exit 2; }; PINNED=$2; shift 2 ;;
    *) echo "unknown option '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$PINNED" ] || PINNED=$BASH
PINNED=$(cd "$(dirname "$PINNED")" && pwd -P)/$(basename "$PINNED")
EXPECT_BASH=$("$PINNED" -c 'printf %s "$BASH_VERSION"') || { echo "cannot run $PINNED" >&2; exit 2; }
TMP=$(cd "$(mktemp -d "${TMPDIR:-/tmp}/ui-runner-test.XXXXXX")" && pwd -P)
trap 'rm -rf "$TMP"' EXIT
failures=0
passed=0
ok() { passed=$((passed + 1)); printf '  ok    %s\n' "$1"; }
fail() {
  failures=$((failures + 1))
  printf '  FAIL  %s\n' "$1"
  if [ -n "${OUT:-}" ] && [ -f "$OUT" ]; then tail -6 "$OUT" | sed 's/^/          | /'; fi
}
check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then ok "$1"; else fail "$1: expected '$2', got '$3'"; fi
}
part() { printf '\n%s\n' "$1"; }

# octal_mode <file>...: the permission bits of each file, with GNU stat (-c) or BSD stat (-f). A probe that fails is discarded whole: GNU
# stat -f is "file system status", which prints a block of text for the file and then fails on the format, so the output of a probe is
# taken only when the probe succeeded.
octal_mode() {
  local file out
  for file in "$@"; do
    if out=$(stat -c '%a' "$file" 2> /dev/null); then
      printf '%s\n' "$out"
    elif out=$(stat -f '%Lp' "$file" 2> /dev/null); then
      printf '%s\n' "$out"
    else
      return 1
    fi
  done
}

# --- the disposable tree -----------------------------------------------------------------------------------------------

STATE_JSON='{"schemaVersion":2,"runId":"20261120T161252Z-85dcbd","source":{"label":"service_instance_id","instanceId":"3fdf1298-d99b-4832-9232-e4b5e3b118f0"},"timeRange":{"fromMs":1795191142000,"toMs":1795191212000},"evaluationTime":"1795191182.943","evaluationTimeSeconds":1795191182.943,"endpoints":{"registryApi":"http://127.0.0.1:8081/apis/registry/v3","prometheus":"http://127.0.0.1:9090","grafana":"http://127.0.0.1:13000","grafanaDatasourceUid":"apicurio-usage-prometheus"},"clients":{"probe":"probe-1"},"schemas":[{"globalId":46,"contentId":46},{"globalId":47,"contentId":47},{"globalId":48,"contentId":46}],"expectedSeries":[{"clientId":"billing-service","identifierType":"global_id","identifierValue":"46","operation":"DESERIALIZE","count":1},{"clientId":"billing-service","identifierType":"global_id","identifierValue":"47","operation":"UNKNOWN","count":2},{"clientId":"orders-service","identifierType":"content_id","identifierValue":"46","operation":"DESERIALIZE","count":2},{"clientId":"orders-service","identifierType":"global_id","identifierValue":"46","operation":"SERIALIZE","count":3},{"clientId":"orders-service","identifierType":"global_id","identifierValue":"48","operation":"SERIALIZE","count":1},{"clientId":"probe-1","identifierType":"global_id","identifierValue":"46","operation":"UNKNOWN","count":1}]}'
# a valid run state that the browser flows cannot use: one global ID row only, so no filter can narrow anything
THIN_STATE_JSON='{"schemaVersion":2,"runId":"r","source":{"label":"service_instance_id","instanceId":"3fdf1298-d99b-4832-9232-e4b5e3b118f0"},"timeRange":{"fromMs":1795191142000,"toMs":1795191212000},"evaluationTime":"1795191182.943","endpoints":{"grafana":"http://127.0.0.1:13000","grafanaDatasourceUid":"apicurio-usage-prometheus"},"clients":{"probe":"probe-1"},"schemas":[{"globalId":46,"contentId":46}],"expectedSeries":[{"clientId":"orders-service","identifierType":"global_id","identifierValue":"46","operation":"SERIALIZE","count":3}]}'

write_state() { printf '%s\n' "$STATE_JSON" > "$1/.local/projects/usage-telemetry-otel/run-state.json"; }

new_tree() { # new_tree: prints the path of a fresh copy of the example with stand-in tools
  local t
  t=$(mktemp -d "$TMP/tree.XXXXXX")
  mkdir -p "$t/tests/ui" "$t/.local/projects/usage-telemetry-otel" "$t/stubs/bin" "$t/stubs/pinned" "$t/stubs/claimwrap"
  ln -s "$PINNED" "$t/stubs/pinned/bash"
  printf 'printf "%%s %%s\\n" "$BASH_VERSION" "$BASH" >> "$SHELL_VERSION_LOG"\n' > "$t/stubs/shellprobe"
  printf 'process.emitWarning("a harmless warning of the Node runtime");\n' > "$t/stubs/warn.cjs"
  cat > "$t/stubs/claimwrap/claim-tool" <<'SH'
#!/bin/sh
# mkdir or ln (by the name it is called under) that does its real work and then, only for the invocation claim, injects what the
# STUB_CLAIM_* variables ask for: a signal to the runner alone, a signal to the whole process group (the runner and this command), a
# foreign claim that appears just before the real operation, a failure that creates nothing, an unwritable claim directory
case "$(basename "$0")" in mkdir) real=/bin/mkdir ;; ln) real=/bin/ln ;; esac
for last; do :; done
case "$last" in
  *.invocation-claim)
    if [ -n "${STUB_CLAIM_FOREIGN:-}" ]; then
      case "$(basename "$0")" in
        mkdir) /bin/mkdir "$last" ;;
        ln) /bin/ln -s "claim:pid=999999:runner=other:token=foreign" "$last" ;;
      esac
    fi
    [ -z "${STUB_CLAIM_FAIL:-}" ] || exit 1
    "$real" "$@"
    rc=$?
    [ -z "${STUB_CLAIM_CHMOD:-}" ] || [ ! -d "$last" ] || /bin/chmod 500 "$last"
    [ -z "${STUB_CLAIM_SIGNAL:-}" ] || kill -"$STUB_CLAIM_SIGNAL" "$PPID"
    if [ -n "${STUB_CLAIM_GROUP_SIGNAL:-}" ]; then
      group=$(ps -o pgid= -p "$PPID" | tr -d ' ')
      # "kill -SIG -- -PGID" is an error in dash (Ubuntu /bin/sh): the form with -s takes the group in every shell
      kill -s "$STUB_CLAIM_GROUP_SIGNAL" -- "-$group"
      sleep 1
    fi
    exit $rc
    ;;
esac
exec "$real" "$@"
SH
  chmod +x "$t/stubs/claimwrap/claim-tool"
  ln "$t/stubs/claimwrap/claim-tool" "$t/stubs/claimwrap/mkdir"
  ln "$t/stubs/claimwrap/claim-tool" "$t/stubs/claimwrap/ln"
  cp "$SRC/lib.sh" "$SRC/test-ui.sh" "$SRC/screenshots.sh" "$t/"
  cp "$SRC/tests/ui/run-ui.sh" "$SRC/tests/ui/package.json" "$SRC/tests/ui/package-lock.json" "$t/tests/ui/"
  [ ! -d "$SRC/tests/ui/lib" ] || cp -R "$SRC/tests/ui/lib" "$t/tests/ui/lib"
  write_state "$t"
  cat > "$t/stubs/fake-install.sh" <<'SH'
#!/bin/sh
# fake-install.sh <package dir>: puts every non-optional locked package in node_modules at its locked version
dir=$1
node -e '
const fs = require("fs"), path = require("path");
const dir = process.argv[1];
const lock = JSON.parse(fs.readFileSync(path.join(dir, "package-lock.json"), "utf8"));
for (const [key, entry] of Object.entries(lock.packages)) {
  if (!key.startsWith("node_modules/") || entry.optional) continue;
  fs.mkdirSync(path.join(dir, key), { recursive: true });
  fs.writeFileSync(path.join(dir, key, "package.json"), JSON.stringify({ name: key.slice(13), version: entry.version, main: "index.mjs", type: "module", exports: "./index.mjs" }));
}
fs.writeFileSync(path.join(dir, "node_modules/@playwright/test/index.mjs"), `
import { existsSync } from "node:fs";
const chrome = () => (process.env.STUB_EXEC_OUTSIDE ? "/elsewhere/chrome" : process.env.PLAYWRIGHT_BROWSERS_PATH + "/chromium-stub/chrome");
export const chromium = {
  executablePath: chrome,
  launch: async () => {
    if (process.env.STUB_LAUNCH_FAILS || !existsSync(chrome())) throw new Error("Executable does not exist or does not start");
    return { version: () => "stub-1", close: async () => {} };
  },
};`);
fs.mkdirSync(path.join(dir, "node_modules/.bin"), { recursive: true });
fs.copyFileSync(path.join(process.env.STUB_DIR, "playwright-stub.sh"), path.join(dir, "node_modules/.bin/playwright"));
fs.chmodSync(path.join(dir, "node_modules/.bin/playwright"), 0o755);
' "$dir"
SH
  cat > "$t/stubs/playwright-stub.sh" <<'SH'
#!/bin/sh
# stand-in for the Playwright command line: records what it saw and behaves as the STUB_* variables say
echo "playwright $* | cwd=$(pwd) | PLAYWRIGHT_BROWSERS_PATH=${PLAYWRIGHT_BROWSERS_PATH-unset}" >> "$STUB_LOG"
env | grep -E '^(UI_|PW_|PLAYWRIGHT_)' | sort | sed 's/^/env: /' >> "$STUB_LOG"
case "$1" in
  install)
    if [ "$2" = "--dry-run" ]; then
      [ ! -e "$STUB_DIR/broken" ] || { echo "Error: Cannot find module './lib/program'" >&2; exit 1; }
      [ -z "${STUB_DRYRUN_FAILS:-}" ] || exit "$STUB_DRYRUN_FAILS"
      [ -z "${STUB_DRYRUN_EMPTY:-}" ] || { echo "nothing to report"; exit 0; }
      root=${STUB_DRYRUN_ROOT:-$PLAYWRIGHT_BROWSERS_PATH}
      for d in chromium-1255 ffmpeg-1013 chromium_headless_shell-1255; do
        printf 'Some browser (playwright %s)\n  Install location:    %s/%s\n  Download url:        https://example.invalid/%s.zip\n\n' "$d" "$root" "$d" "$d"
      done
      exit 0
    fi
    echo "INSTALL-REAL gc=${PLAYWRIGHT_SKIP_BROWSER_GC-unset}" >> "$STUB_LOG"
    [ "${STUB_INSTALL_STATUS:-0}" = 0 ] || exit "$STUB_INSTALL_STATUS"
    mkdir -p "$PLAYWRIGHT_BROWSERS_PATH/chromium-stub" && : > "$PLAYWRIGHT_BROWSERS_PATH/chromium-stub/chrome"
    exit 0
    ;;
  test)
    if [ "$2" = "--list" ]; then
      [ ! -e "$STUB_DIR/broken" ] || { echo "Error: Cannot find module './lib/program'" >&2; exit 1; }
      [ -z "${STUB_LIST_FAILS:-}" ] || exit "$STUB_LIST_FAILS"
      echo "Total: 1 test in 1 file"
      exit 0
    fi
    if [ -n "${UI_BUNDLE:-}" ]; then
      eval "$(node -e 'const b = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")); console.log("RUN=" + b.runDir + "\nREPORT=" + b.reportDir); console.error("bundle: url=" + b.grafanaUrl + " datasource=" + b.datasourceUid + " mode=" + b.mode + " flows=" + b.flows)' "$UI_BUNDLE" 2>> "$STUB_LOG")"
    else
      RUN=$UI_RUN_DIR
      REPORT=$UI_REPORT_DIR
    fi
    echo "project: $2" >> "$STUB_LOG"
    echo "$$" > "$STUB_DIR/test-pid"
    readlink .invocation-claim > "$STUB_DIR/claim-record" 2> /dev/null
    case "${STUB_TAMPER:-}" in
      foreign) rm -f .invocation-claim; ln -s "claim:pid=999999:runner=other:token=foreign" .invocation-claim ;;
      fifo) rm -f .invocation-claim; mkfifo .invocation-claim ;;
      directory) rm -f .invocation-claim; mkdir .invocation-claim; echo x > .invocation-claim/inside ;;
    esac
    if [ -n "${STUB_TEST_EARLY:-}" ]; then
      mkdir -p "$RUN/pw/t1" "$RUN/screenshots"
      : > "$RUN/pw/t1/trace.zip"
      printf '\211PNG\r\n\032\n0000' > "$RUN/pw/t1/test-failed-1.png"
    fi
    : > "$STUB_DIR/test-entered"
    if [ -n "${STUB_TEST_BARRIER:-}" ]; then n=0; while [ ! -e "$STUB_TEST_BARRIER" ] && [ "$n" -lt 300 ]; do sleep 0.1; n=$((n + 1)); done; fi
    [ -z "${STUB_TEST_SLEEP:-}" ] || sleep "$STUB_TEST_SLEEP"
    if [ "${STUB_TEST_MODE:-pass}" = pass ]; then
      mkdir -p "$RUN/pw" "$REPORT" "$RUN/screenshots"
      echo '<html></html>' > "$REPORT/index.html"
      echo '{"stats":{"expected":3,"unexpected":0,"skipped":0,"flaky":0}}' > "$RUN/results.json"
      case "$2" in --project=screenshots) for n in overview filtered; do printf '\211PNG\r\n\032\n0000' > "$RUN/screenshots/$n.png"; done ;; esac
    fi
    exit "${STUB_TEST_STATUS:-0}"
    ;;
esac
exit 0
SH
  cat > "$t/stubs/bin/npm" <<'SH'
#!/bin/sh
# stand-in for npm: records, optionally waits at a barrier, optionally fails with a chosen status, and otherwise "installs"
echo "npm $*" >> "$STUB_LOG"
n=$(ls "$STUB_DIR"/npm-entered.* 2> /dev/null | wc -l | tr -d ' ')
: > "$STUB_DIR/npm-entered.$((n + 1))"
if [ -n "${STUB_NPM_BARRIER:-}" ]; then i=0; while [ ! -e "$STUB_NPM_BARRIER" ] && [ "$i" -lt 300 ]; do sleep 0.1; i=$((i + 1)); done; fi
[ "${STUB_NPM_STATUS:-0}" = 0 ] || exit "$STUB_NPM_STATUS"
rm -f "$STUB_DIR/broken"
prefix=""
while [ $# -gt 0 ]; do [ "$1" = --prefix ] && prefix=$2; shift; done
sh "$STUB_DIR/fake-install.sh" "$prefix"
SH
  chmod +x "$t/stubs/fake-install.sh" "$t/stubs/playwright-stub.sh" "$t/stubs/bin/npm" "$t/test-ui.sh" "$t/screenshots.sh" "$t/tests/ui/run-ui.sh"
  STUB_DIR="$t/stubs" sh "$t/stubs/fake-install.sh" "$t/tests/ui"
  printf '%s' "$t"
}

# runx <tree> <env assignments and command...>: runs in the tree with the stand-ins first in PATH, then the pinned interpreter
# (XPATH, when set, goes before both); status in RC, output in OUT
runx() {
  local t=$1
  shift
  OUT=$t/out.txt
  : > "$t/stubs/log"
  (cd "$t" && env PATH="${XPATH:+$XPATH:}$t/stubs/bin:$t/stubs/pinned:$PATH" STUB_DIR="$t/stubs" STUB_LOG="$t/stubs/log" "$@") > "$OUT" 2>&1
  RC=$?
}
# launchx <tree> <launcher options> -- <env assignments and command...>: the same, started by tests/ui/test/launch.py with the
# default signal dispositions and a deadline; LSTATUS and LHUNG report the outcome, the command's output is in OUT
launchx() {
  local t=$1 opts=""
  shift
  OUT=$t/out.txt
  : > "$t/stubs/log"
  while [ "$1" != "--" ]; do opts="$opts $1"; shift; done
  shift
  # shellcheck disable=SC2086 # the launcher options are separate words
  LAUNCH_RESULT=$(cd "$t" && python3 "$HERE/launch.py" --log "$OUT" $opts -- env PATH="${XPATH:+$XPATH:}$t/stubs/bin:$t/stubs/pinned:$PATH" STUB_DIR="$t/stubs" STUB_LOG="$t/stubs/log" "$@")
  LSTATUS=$(printf '%s\n' "$LAUNCH_RESULT" | sed -n 's/^status=//p')
  LHUNG=$(printf '%s\n' "$LAUNCH_RESULT" | sed -n 's/^hung=//p')
}
claim_present() { { [ -e "$1/tests/ui/.invocation-claim" ] || [ -L "$1/tests/ui/.invocation-claim" ]; } && echo yes || echo no; }
npm_calls() { grep -c '^npm ' "$1/stubs/log" 2> /dev/null || true; }
run_dirs() { ls "$1/tests/ui/test-results" 2> /dev/null | wc -l | tr -d ' '; }
no_effects() { # no_effects <tree> <description>: nothing was claimed, installed, created or run
  check "$2: no claim, no npm, no output directory, no browser run" "no 0 0 0" "$(claim_present "$1") $(npm_calls "$1") $(run_dirs "$1") $(grep -c '^playwright ' "$1/stubs/log" 2> /dev/null || true)"
}

# --- cases ---------------------------------------------------------------------------------------------------------------------

part "the interpreter under test is the one the runner really uses"
T=$(new_tree)
runx "$T" BASH_ENV="$T/stubs/shellprobe" SHELL_VERSION_LOG="$T/stubs/shells.log" ./test-ui.sh --help
check "a probe run of --help succeeds" 0 "$RC"
check "the wrapper and run-ui.sh each started a shell, and every one of them reports BASH_VERSION $EXPECT_BASH (harness shell $BASH_VERSION)" "$EXPECT_BASH" "$(awk '{ print $1 }' "$T/stubs/shells.log" | sort -u | tr '\n' ' ' | sed 's/ $//')"
check "  there were exactly two shells, the wrapper and run-ui.sh, both the pinned executable" "2 $T/stubs/pinned/bash" "$(wc -l < "$T/stubs/shells.log" | tr -d ' ') $(awk '{ print $2 }' "$T/stubs/shells.log" | sort -u | tr '\n' ' ' | sed 's/ $//')"
printf '  info  runner shells: %s\n' "$(tr '\n' ';' < "$T/stubs/shells.log")"

part "help and option errors need no tool and no project file"
T=$(new_tree)
runx "$T" PATH="$T/stubs/pinned:/usr/bin:/bin" ./test-ui.sh --help
check "--help works with no node, no npm and no jq in PATH (exit status)" 0 "$RC"
check "  and prints the usage" 1 "$(grep -c '^Usage: ./test-ui.sh' "$OUT")"
runx "$T" ./screenshots.sh -h
check "-h of screenshots.sh" 0 "$RC"
for args in "--bogus" "--state" "--grafana-url" "--scenario" "--no-such --help" "--setup-only --state"; do
  # shellcheck disable=SC2086 # the words of $args are separate options on purpose
  runx "$T" ./test-ui.sh $args
  if [ "$args" = "--no-such --help" ]; then
    check "an unknown option before --help is refused (exit status)" 1 "$RC"
  else
    check "test-ui.sh $args fails (exit status)" 1 "$RC"
  fi
done
runx "$T" ./screenshots.sh --scenario x.json
check "screenshots.sh refuses --scenario" 1 "$RC"
runx "$T" ./screenshots.sh --self-test
check "screenshots.sh refuses --self-test" 1 "$RC"
runx "$T" ./test-ui.sh --self-test --state x.json
check "--self-test refuses a state" 1 "$RC"
runx "$T" ./test-ui.sh --scenario x.json --state y.json
check "--scenario and --state together are refused" 1 "$RC"
no_effects "$T" "option errors"
for deadline in abc 0 -1 05 99999999; do
  runx "$T" UI_TEST_TIMEOUT="$deadline" ./test-ui.sh
  check "UI_TEST_TIMEOUT='$deadline' is refused before any effect" "1 no 0" "$RC $(claim_present "$T") $(npm_calls "$T")"
done
runx "$T" UI_INSTALL_TIMEOUT=abc ./test-ui.sh
check "UI_INSTALL_TIMEOUT=abc is refused" 1 "$RC"
no_effects "$T" "bad deadlines"

part "input is validated by one module before any claim, download, browser or directory"
T=$(new_tree)
STATE=$T/.local/projects/usage-telemetry-otel/run-state.json
bad_state() { # bad_state <name> <sed expression>
  sed "$2" "$STATE" > "$T/bad-$1.json"
  runx "$T" ./test-ui.sh --state "$T/bad-$1.json"
  check "run state $1 is refused (exit status)" 1 "$RC"
  no_effects "$T" "run state $1"
}
bad_state wrong-version 's/"schemaVersion":2/"schemaVersion":1/'
bad_state reversed-interval 's/"fromMs":1795191142000/"fromMs":1795191999000/'
bad_state no-evaluation-time 's/"evaluationTime":"1795191182.943",//'
bad_state fractional-time 's/"evaluationTime":"1795191182.943"/"evaluationTime":"1795191182.9431"/'
bad_state identifier-not-in-schemas 's/"identifierValue":"46"/"identifierValue":"99"/'
bad_state credentials-in-endpoint 's#http://127.0.0.1:13000#http://admin:secret@127.0.0.1:13000#'
bad_state unsafe-count 's/"count":3}/"count":9007199254740993}/'
bad_state rounded-fraction-token 's/"count":3}/"count":3.0000000000000001}/'
bad_state exponent-token 's/"count":3}/"count":3e0}/'
printf '%s\n' "$THIN_STATE_JSON" > "$T/thin.json"
runx "$T" ./test-ui.sh --state "$T/thin.json"
check "a valid run state without the selections the filter flow needs is refused by the tests runner, before any effect" "1 yes" "$RC $(grep -q 'global ID' "$OUT" && echo yes || echo no)"
no_effects "$T" "a state the flows cannot use"
runx "$T" ./screenshots.sh --state "$T/thin.json"
check "  and by the screenshots runner" 1 "$RC"
no_effects "$T" "a state the screenshot flows cannot use"
: > "$T/empty.json"
runx "$T" ./test-ui.sh --state "$T/empty.json"
check "an empty state file is refused" 1 "$RC"
printf '{"a":1}\n{"b":2}\n' > "$T/two.json"
runx "$T" ./test-ui.sh --state "$T/two.json"
check "two JSON documents are refused" 1 "$RC"
runx "$T" ./test-ui.sh --state "$T/absent.json"
check "a missing state file is refused" 1 "$RC"
no_effects "$T" "empty, two-document and missing state"
SCEN=$T/scenarios.json
cat > "$SCEN" <<'JSON'
{"schemaVersion":1,"scenarios":[{"kind":"processes","primary":"p1","other":"p2","fromMs":1795224000000,"toMs":1795224300000,"primaryRows":[["c1","global_id","1","SERIALIZE",1]],"otherRows":[["c1","global_id","1","SERIALIZE",150],["c2","global_id","1","DESERIALIZE",75]]}]}
JSON
for variant in 'schemaVersion":999' 'reversed' 'noversion'; do
  case $variant in
    reversed) sed 's/"fromMs":1795224000000,"toMs":1795224300000/"fromMs":1795224300000,"toMs":1795224000000/' "$SCEN" > "$T/scn-bad.json" ;;
    noversion) sed 's/"schemaVersion":1,//' "$SCEN" > "$T/scn-bad.json" ;;
    *) sed 's/"schemaVersion":1/"schemaVersion":999/' "$SCEN" > "$T/scn-bad.json" ;;
  esac
  runx "$T" ./test-ui.sh --scenario "$T/scn-bad.json" --grafana-url http://127.0.0.1:43000
  check "scenario '$variant' is refused" 1 "$RC"
  no_effects "$T" "scenario $variant"
done
runx "$T" ./test-ui.sh --scenario "$SCEN"
check "a scenario without a Grafana URL is refused" 1 "$RC"
runx "$T" ./test-ui.sh --state "$STATE" --grafana-url "http://user:pw@127.0.0.1:1"
check "credentials in --grafana-url are refused" 1 "$RC"
check "  and the diagnostic does not repeat them" 0 "$(grep -c 'pw@' "$OUT")"
runx "$T" GRAFANA_URL="http://user:pw@127.0.0.1:1" ./test-ui.sh
check "credentials in GRAFANA_URL are refused" 1 "$RC"
runx "$T" GRAFANA_DATASOURCE_UID="a b" ./test-ui.sh
check "an invalid GRAFANA_DATASOURCE_UID is refused" 1 "$RC"
no_effects "$T" "scenario without URL and bad overrides"

part "the Grafana address and datasource come only from the input and the public options"
T=$(new_tree)
runx "$T" UI_GRAFANA_URL=http://127.0.0.1:1 UI_DATASOURCE_UID=hostile UI_MODE=scenario UI_RUN_DIR=/elsewhere ./test-ui.sh
check "a normal run with hostile inherited UI_* variables passes" 0 "$RC"
check "  the browser stage never saw an inherited UI_GRAFANA_URL" 0 "$(grep -c '^env: UI_GRAFANA_URL=' "$T/stubs/log")"
check "  nor UI_DATASOURCE_UID, UI_MODE or UI_RUN_DIR set to the hostile values" 0 "$(grep -cE '^env: UI_(DATASOURCE_UID=hostile|MODE=scenario|RUN_DIR=/elsewhere)' "$T/stubs/log")"
check "  the run says what it ignored" 1 "$(grep -c 'ignoring inherited variables' "$OUT")"
check "  the address is the state's, port 13000" 1 "$(grep -c 'bundle: url=http://127.0.0.1:13000 ' "$T/stubs/log")"
runx "$T" GRAFANA_URL=http://127.0.0.1:2222 GRAFANA_DATASOURCE_UID=ops-prom-7 ./test-ui.sh
check "GRAFANA_URL and GRAFANA_DATASOURCE_UID are the documented overrides" "1" "$(grep -c 'bundle: url=http://127.0.0.1:2222 datasource=ops-prom-7 ' "$T/stubs/log")"
runx "$T" GRAFANA_URL=http://127.0.0.1:2222 ./test-ui.sh --grafana-url http://127.0.0.1:3333
check "the flag wins over GRAFANA_URL" 1 "$(grep -c 'bundle: url=http://127.0.0.1:3333 ' "$T/stubs/log")"
runx "$T" ./test-ui.sh --scenario "$SCEN" --grafana-url http://127.0.0.1:43000
check "a scenario run passes" 0 "$RC"
LIB_DEFAULT=$(cd "$T" && bash -c '. ./lib.sh; printf %s "$GRAFANA_DATASOURCE_UID"')
check "  its datasource default is the one lib.sh defines" 1 "$(grep -c "bundle: url=http://127.0.0.1:43000 datasource=$LIB_DEFAULT mode=scenario" "$T/stubs/log")"
check "  and it selects only the process switch project" 1 "$(grep -c '^project: --project=process-switch' "$T/stubs/log")"

part "Playwright's reporter variables cannot redirect, add or delete anything"
T=$(new_tree)
SENT=$TMP/sentinel
mkdir -p "$SENT/html" "$SENT/json" "$SENT/junit" "$SENT/blob"
echo keep > "$SENT/html/keep.txt"
echo keep > "$SENT/redirected.json"
BEFORE=$(cd "$SENT" && find . -type f | sort | tr '\n' ' ')
runx "$T" PLAYWRIGHT_HTML_OUTPUT_DIR="$SENT/html" PLAYWRIGHT_HTML_REPORT="$SENT/html" PLAYWRIGHT_HTML_OPEN=always PW_TEST_HTML_REPORT_OPEN=always \
  PLAYWRIGHT_HTML_HOST=0.0.0.0 PLAYWRIGHT_HTML_PORT=9323 PLAYWRIGHT_HTML_TITLE=t PLAYWRIGHT_HTML_ATTACHMENTS_BASE_URL=https://x/ \
  PLAYWRIGHT_JSON_OUTPUT_FILE="$SENT/redirected.json" PLAYWRIGHT_JSON_OUTPUT_DIR="$SENT/json" PLAYWRIGHT_JSON_OUTPUT_NAME=x.json \
  PLAYWRIGHT_JUNIT_OUTPUT_FILE="$SENT/junit/j.xml" PLAYWRIGHT_BLOB_OUTPUT_DIR="$SENT/blob" PW_TEST_REPORTER=html PLAYWRIGHT_LAST_RUN_OUTPUT_FILE="$SENT/last.json" ./test-ui.sh
check "a run with every reporter variable set passes (exit status)" 0 "$RC"
check "  the browser stage saw none of them" 0 "$(grep -cE '^env: (PLAYWRIGHT_(HTML|JSON|JUNIT|BLOB|LAST)|PW_TEST_)' "$T/stubs/log")"
check "  the sentinel files are untouched and nothing was added" "$BEFORE" "$(cd "$SENT" && find . -type f | sort | tr '\n' ' ')"
check "  the sentinel content is intact" "keep keep" "$(cat "$SENT/html/keep.txt" "$SENT/redirected.json" | tr '\n' ' ' | sed 's/ $//')"
check "  the report lives in this invocation's own directory" 1 "$(ls "$T"/tests/ui/playwright-report/*/index.html 2> /dev/null | wc -l | tr -d ' ')"

part "the browser directory is resolved once, absolute, and never through a link"
T=$(new_tree)
runx "$T" PLAYWRIGHT_BROWSERS_PATH=relative/browsers ./test-ui.sh
check "a relative PLAYWRIGHT_BROWSERS_PATH is refused with a diagnostic" "1 yes" "$RC $(grep -q 'must be an absolute path' "$OUT" && echo yes || echo no)"
for bad in "$T/a/../b" "$T/browsers/" "$T//browsers" "$T/./browsers" "$T/browsers"$'\a' "$T/browsers"$'\n'x; do
  runx "$T" PLAYWRIGHT_BROWSERS_PATH="$bad" ./test-ui.sh
  check "'$bad' is refused" 1 "$RC"
done
no_effects "$T" "bad browser paths"
mkdir -p "$T/real-browsers"
ln -s "$T/real-browsers" "$T/linked-browsers"
mkdir -p "$T/under"
ln -s "$T/under" "$T/under-link"
for linked in "$T/linked-browsers" "$T/under-link/browsers"; do
  runx "$T" PLAYWRIGHT_BROWSERS_PATH="$linked" ./test-ui.sh
  check "a path through a symbolic link is refused: ${linked#"$T"/}" 1 "$RC"
done
check "  nothing was written through the link" "0 0" "$(ls "$T/real-browsers" | wc -l | tr -d ' ') $(ls "$T/under" | wc -l | tr -d ' ')"
runx "$T" ./test-ui.sh
check "the default run passes" 0 "$RC"
check "  every Playwright command saw the default browser directory tests/ui/.browsers, absolute" "$(grep -c '^playwright ' "$T/stubs/log")" "$(grep -c "^playwright .*PLAYWRIGHT_BROWSERS_PATH=$T/tests/ui/.browsers\$" "$T/stubs/log" || true)"
check "  the browser was installed there" yes "$([ -e "$T/tests/ui/.browsers/chromium-stub/chrome" ] && echo yes || echo no)"
mkdir -p "$T/mine"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$T/mine" ./test-ui.sh
check "an absolute caller path is used as given" "0 yes" "$RC $([ -e "$T/mine/chromium-stub/chrome" ] && echo yes || echo no)"
mv "$T/tests/ui/test-results" "$T/tests/ui/test-results.moved"
ln -s "$T/real-browsers" "$T/tests/ui/test-results"
runx "$T" ./test-ui.sh
check "a symbolic link as the output root is refused and nothing is written through it" "1 0" "$RC $(ls "$T/real-browsers" | wc -l | tr -d ' ')"
rm -f "$T/tests/ui/test-results"
mv "$T/tests/ui/test-results.moved" "$T/tests/ui/test-results"

part "readiness covers every locked package and a browser that really starts"
T=$(new_tree)
check "a whole install with a starting browser is ready: --setup-only" 0 "$(runx "$T" ./test-ui.sh --setup-only; echo $RC)"
check "  a whole install needs no npm" 0 "$(npm_calls "$T")"
rm -rf "$T/tests/ui/.browsers"
runx "$T" ./test-ui.sh --setup-only --no-install
check "--setup-only --no-install with no browser does not claim readiness" "1 no" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
runx "$T" ./test-ui.sh --setup-only
check "  and with installs allowed the browser is fetched" "0 yes" "$RC $([ -e "$T/tests/ui/.browsers/chromium-stub/chrome" ] && echo yes || echo no)"
runx "$T" ./test-ui.sh --setup-only --no-install
check "  and then --no-install is ready" 0 "$RC"
runx "$T" STUB_LAUNCH_FAILS=1 ./test-ui.sh --setup-only --no-install
check "a browser that is present but does not start is not ready" "1 no" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
runx "$T" STUB_EXEC_OUTSIDE=1 ./test-ui.sh --no-install
check "a browser that resolves outside the owned directory is refused (the probe's own status is kept)" 2 "$RC"
printf '{"name":"playwright-core","version":"1.64.1"}' > "$T/tests/ui/node_modules/playwright-core/package.json"
runx "$T" ./test-ui.sh --no-install
check "a different playwright-core (the wrapper is right) is a failure with --no-install" "1 yes" "$RC $(grep -q 'playwright-core is 1.64.1, locked 1.65.1' "$OUT" && echo yes || echo no)"
check "  nothing ran" "0" "$(grep -c '^playwright test' "$T/stubs/log")"
runx "$T" ./test-ui.sh
check "  with installs allowed it is repaired by npm ci and the run passes" "0 1" "$RC $(npm_calls "$T")"
check "  and the repaired package is the locked one" '1.65.1' "$(sed -n 's/.*"version":"\([^"]*\)".*/\1/p' "$T/tests/ui/node_modules/playwright-core/package.json")"
rm -rf "$T/tests/ui/node_modules/typescript"
runx "$T" ./test-ui.sh --no-install
check "a missing locked package is a failure with --no-install" "1 yes" "$RC $(grep -q 'typescript is not installed' "$OUT" && echo yes || echo no)"
rm -rf "$T/tests/ui/node_modules/.bin/playwright"
runx "$T" ./test-ui.sh --no-install
check "a missing command line is a failure with --no-install" 1 "$RC"

part "exact statuses of npm, the browser installer, the browser run and the deadline"
T=$(new_tree)
rm -rf "$T/tests/ui/node_modules"
runx "$T" STUB_NPM_STATUS=124 ./test-ui.sh
check "npm ci that ends with 124 ends the run with 124" "124 yes" "$RC $(grep -q 'its deadline passed' "$OUT" && echo yes || echo no)"
check "  and the claim is released" no "$(claim_present "$T")"
runx "$T" STUB_NPM_STATUS=7 ./test-ui.sh
check "npm ci that ends with 7 ends the run with 7" "7 no" "$RC $(claim_present "$T")"
runx "$T" ./test-ui.sh --setup-only
rm -rf "$T/tests/ui/.browsers"
runx "$T" STUB_INSTALL_STATUS=124 ./test-ui.sh
check "a browser install that ends with 124 ends the run with 124" "124 no" "$RC $(claim_present "$T")"
runx "$T" STUB_INSTALL_STATUS=9 ./test-ui.sh
check "a browser install that ends with 9 ends the run with 9" 9 "$RC"
runx "$T" ./test-ui.sh --setup-only
runx "$T" STUB_TEST_STATUS=124 STUB_TEST_MODE=none ./test-ui.sh
check "a browser run that ends with 124 before writing anything (no pw directory) is 124, with the deadline message" "124 yes" "$RC $(grep -q 'passed its deadline' "$OUT" && echo yes || echo no)"
check "  the diagnostics say honestly that nothing was written" "yes yes" "$(grep -q 'no results.json' "$OUT" && echo yes || echo no) $(grep -q 'report:      none was written' "$OUT" && echo yes || echo no)"
check "  and no report is claimed to be openable" 0 "$(grep -c 'open it:' "$OUT")"
runx "$T" STUB_TEST_STATUS=5 ./test-ui.sh
check "a browser run that ends with 5 ends the run with 5 and keeps the report line" "5 1" "$RC $(grep -c 'report:      .*index.html' "$OUT")"
runx "$T" STUB_TEST_STATUS=0 STUB_TEST_MODE=none ./test-ui.sh
check "a browser run that exits 0 but wrote no results is not a pass" 1 "$RC"
runx "$T" UI_TEST_TIMEOUT=1 STUB_TEST_SLEEP=30 STUB_TEST_MODE=none ./test-ui.sh
check "the run deadline really bounds the browser run (status 124)" "124 no" "$RC $(claim_present "$T")"
runx "$T" ./test-ui.sh
check "a passing run" "0 no" "$RC $(claim_present "$T")"
runx "$T" ./screenshots.sh
check "screenshots.sh: a passing run needs both PNGs and names them" "0 2" "$RC $(grep -c 'screenshot:  ' "$OUT")"

part "one invocation at a time per checkout"
T=$(new_tree)
rm -rf "$T/tests/ui/node_modules"
touch "$TMP/never"
( runx "$T" STUB_NPM_BARRIER="$T/release-npm" ./test-ui.sh; echo "$RC" > "$T/first.rc" ) &
FIRST=$!
n=0
while [ ! -e "$T/stubs/npm-entered.1" ] && [ "$n" -lt 100 ]; do sleep 0.1; n=$((n + 1)); done
check "the first invocation is inside its dependency installation" yes "$([ -e "$T/stubs/npm-entered.1" ] && echo yes || echo no)"
check "  and holds the claim" yes "$(claim_present "$T")"
SECOND_OUT=$T/second.txt
(cd "$T" && env PATH="$T/stubs/bin:$T/stubs/pinned:$PATH" STUB_DIR="$T/stubs" STUB_LOG="$T/stubs/log2" ./test-ui.sh > "$SECOND_OUT" 2>&1; echo $? > "$T/second.rc")
check "a second invocation is refused before any effect (exit status)" 1 "$(cat "$T/second.rc")"
check "  with a clear message naming the holder" "1 1" "$(grep -c 'another invocation holds' "$SECOND_OUT") $(grep -c 'process [0-9]* is running' "$SECOND_OUT")"
check "  it ran no npm of its own: one npm call in all" 1 "$(ls "$T"/stubs/npm-entered.* | wc -l | tr -d ' ')"
check "  and the claim of the first is still there, untouched" yes "$(claim_present "$T")"
: > "$T/release-npm"
wait "$FIRST"
check "the first invocation finishes normally" 0 "$(cat "$T/first.rc")"
check "  and releases its claim" no "$(claim_present "$T")"
# a claim left by a process that no longer exists is never taken over
ln -s "claim:pid=999999:runner=test-ui.sh:token=foreign-token" "$T/tests/ui/.invocation-claim"
runx "$T" ./test-ui.sh
check "a stale claim is refused, not stolen, with a hint that it may be stale" "1 yes" "$RC $(grep -q 'may be stale' "$OUT" && echo yes || echo no)"
check "  the stale claim is still there with its own record" "yes claim:pid=999999:runner=test-ui.sh:token=foreign-token" "$(claim_present "$T") $(readlink "$T/tests/ui/.invocation-claim")"
runx "$T" ./test-ui.sh --setup-only
check "  --setup-only is refused the same way" 1 "$RC"
rm -f "$T/tests/ui/.invocation-claim"
# the claim is released on a failure and on signals
runx "$T" STUB_TEST_STATUS=1 ./test-ui.sh
check "the claim is released after a failed run" "1 no" "$RC $(claim_present "$T")"
# INT, TERM and HUP end the runner while the browser run is in progress. The launcher starts it with the default signal
# dispositions (a background job of a non-interactive shell would ignore INT)
for sig in INT TERM HUP; do
  case $sig in INT) WANT=130 ;; *) WANT=143 ;; esac
  rm -f "$T/stubs/test-entered"
  launchx "$T" --signal "$sig" --when-exists "$T/stubs/test-entered" --timeout 40 -- STUB_TEST_SLEEP=60 STUB_TEST_MODE=none ./test-ui.sh
  check "SIG$sig while the browser runs: status $WANT, no hang, claim released" "$WANT no no" "$LSTATUS $LHUNG $(claim_present "$T")"
  check "SIG$sig leaves no stand-in process of the interrupted run behind" "no" "$(kill -0 "$(cat "$T/stubs/test-pid")" 2> /dev/null && echo yes || echo no)"
done
ln -s "claim:pid=$$:runner=test-ui.sh:token=someone-else" "$T/tests/ui/.invocation-claim"
runx "$T" ./test-ui.sh --bogus
check "an invocation that is refused before it has a claim leaves a foreign claim alone" "yes claim:pid=$$:runner=test-ui.sh:token=someone-else" "$(claim_present "$T") $(readlink "$T/tests/ui/.invocation-claim")"
rm -f "$T/tests/ui/.invocation-claim"

part "the claim is complete over its whole life: acquisition, diagnostics, release and every way out"
T=$(new_tree)
CL=$T/tests/ui/.invocation-claim
echo SENTINEL-CONTENT > "$T/secret.txt"
claim_refused() { # claim_refused <description> <expected message fragment>
  check "$1: refused with status 1, no hang" "1 no" "$LSTATUS $LHUNG"
  check "  the message names the holder and says why it was not read: $2" "1" "$(grep -c "$2" "$OUT")"
  check "  nothing was read through it and nothing was claimed, installed or run" "0 0 0" "$(grep -c SENTINEL-CONTENT "$OUT") $(npm_calls "$T") $(run_dirs "$T")"
}
fresh_claim() { rm -rf "$CL"; }
fresh_claim; ln -s "claim:pid=$$:runner=test-ui.sh:token=other" "$CL"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
claim_refused "a claim of a running process" "process $$ is running"
check "  the foreign claim is untouched" "claim:pid=$$:runner=test-ui.sh:token=other" "$(readlink "$CL")"
fresh_claim; ln -s "$(printf 'claim:pid=999999:runner=x:token=\033[31mred\033]0;title\007')$(printf 'A%.0s' $(seq 1 700))" "$CL"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
check "a claim record with control sequences and a long token: refused, bounded and printable" "1 no 0" "$LSTATUS $LHUNG $(grep -c "$(printf '\033')" "$OUT")"
check "  the whole output is small, and it says the holder may be stale (the pid was read)" "yes 1" "$([ "$(wc -c < "$OUT")" -lt 4000 ] && echo yes || echo no) $(grep -c 'may be stale' "$OUT")"
fresh_claim; ln -s "$T/secret.txt" "$CL"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
claim_refused "a claim that is a link to some file (not a claim record), whose target is never opened" "not a claim record"
fresh_claim; mkdir "$CL"; mkfifo "$CL/owner"; echo SENTINEL-CONTENT > "$CL/token"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
claim_refused "a claim left by an older version (a directory with a FIFO owner file)" "not a claim record"
check "  the foreign directory is untouched" "yes yes" "$([ -p "$CL/owner" ] && echo yes || echo no) $([ -f "$CL/token" ] && echo yes || echo no)"
fresh_claim; mkfifo "$CL"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
claim_refused "a claim that is a FIFO" "not a claim record"
check "  the FIFO is untouched" yes "$([ -p "$CL" ] && echo yes || echo no)"
fresh_claim; : > "$CL"
launchx "$T" --timeout 15 -- ./test-ui.sh --self-test --no-install
claim_refused "a claim that is a regular file" "not a claim record"
fresh_claim

# the claim is made by one atomic step that carries the owner identity; its outcome is decided by what is there afterwards, not by
# the return code of the command that made it
for sig in TERM INT HUP; do
  case $sig in INT) WANT=130 ;; *) WANT=143 ;; esac
  fresh_claim
  XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_SIGNAL="$sig" ./test-ui.sh --self-test --no-install
  check "SIG$sig sent to the runner alone right after the claim was made: status $WANT, no claim left" "$WANT no no" "$LSTATUS $LHUNG $(claim_present "$T")"
  check "  nothing else was done" "0 0" "$(npm_calls "$T") $(run_dirs "$T")"
  fresh_claim
  XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_GROUP_SIGNAL="$sig" ./test-ui.sh --self-test --no-install
  check "SIG$sig sent to the runner and to the command that made the claim (its process group), which dies before it can report success: status $WANT, no claim left" "$WANT no no" "$LSTATUS $LHUNG $(claim_present "$T")"
  check "  nothing else was done" "0 0" "$(npm_calls "$T") $(run_dirs "$T")"
done
# the same group signal with the stand-in command run by dash (Ubuntu's /bin/sh), where the hosted run first failed. dash is required on
# GitHub Actions Linux (a runner without it fails here); on any other machine it is optional, and the cases run when it is there
if [ "${GITHUB_ACTIONS:-}" = true ] && [ "$(uname -s)" = Linux ]; then
  check "dash is installed (required on GitHub Actions Linux)" yes "$([ -x /bin/dash ] && echo yes || echo no)"
  HAVE_DASH=$([ -x /bin/dash ] && echo yes || echo no)
elif [ -x /bin/dash ]; then
  HAVE_DASH=yes
else
  HAVE_DASH=no
  printf '  note  /bin/dash is not installed: the extra dash group-signal cases are not run here (they are required on GitHub Actions Linux)\n'
fi
if [ "$HAVE_DASH" = yes ]; then
  /bin/mkdir "$T/stubs/claimwrap-dash"
  sed '1s#.*#\#!/bin/dash#' "$T/stubs/claimwrap/claim-tool" > "$T/stubs/claimwrap-dash/claim-tool"
  chmod +x "$T/stubs/claimwrap-dash/claim-tool"
  ln "$T/stubs/claimwrap-dash/claim-tool" "$T/stubs/claimwrap-dash/mkdir"
  ln "$T/stubs/claimwrap-dash/claim-tool" "$T/stubs/claimwrap-dash/ln"
  for sig in TERM INT; do
    case $sig in INT) WANT=130 ;; *) WANT=143 ;; esac
    fresh_claim
    XPATH="$T/stubs/claimwrap-dash" launchx "$T" --timeout 20 -- STUB_CLAIM_GROUP_SIGNAL="$sig" ./test-ui.sh --self-test --no-install
    check "SIG$sig to the runner's process group from a stand-in run by dash: status $WANT, no claim left" "$WANT no no" "$LSTATUS $LHUNG $(claim_present "$T")"
    check "  nothing else was done" "0 0" "$(npm_calls "$T") $(run_dirs "$T")"
  done
fi
fresh_claim
XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_FAIL=1 ./test-ui.sh --self-test --no-install
check "a command that fails without creating anything: refused with status 1 and a message, nothing left" "1 no" "$LSTATUS $(claim_present "$T")"
check "  and the message says the claim could not be made" 1 "$(grep -c 'could not make the claim' "$OUT")"
for sig in TERM INT; do
  case $sig in INT) WANT=130 ;; *) WANT=143 ;; esac
  XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_FOREIGN=1 STUB_CLAIM_GROUP_SIGNAL="$sig" ./test-ui.sh --self-test --no-install
  check "a claim that appears from another invocation just before ours, with a group SIG$sig: status $WANT and that claim is preserved" "$WANT yes" "$LSTATUS $(claim_present "$T")"
  check "  it is the foreign record, untouched" "claim:pid=999999:runner=other:token=foreign" "$(readlink "$CL" 2> /dev/null)"
  fresh_claim
done
XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_FOREIGN=1 ./test-ui.sh --self-test --no-install
check "the same without a signal: the lost race is an ordinary refusal that names the holder" "1 1" "$LSTATUS $(grep -c 'another invocation holds' "$OUT")"
check "  and the foreign claim is preserved" "claim:pid=999999:runner=other:token=foreign" "$(readlink "$CL" 2> /dev/null)"
fresh_claim

launchx "$T" --timeout 30 -- ./test-ui.sh
check "a normal run, started by the launcher, passes" 0 "$LSTATUS"
check "  while it ran the claim held the owner identity (pid, runner and a token)" "1" "$(grep -c '^claim:pid=[0-9][0-9]*:runner=test-ui.sh:token=[0-9A-Za-z-]*$' "$T/stubs/claim-record")"
check "  and the claim is gone at the end, with one summary printed" "no 1" "$(claim_present "$T") $(grep -c '^Result of tests' "$OUT")"
launchx "$T" --timeout 30 -- STUB_TEST_STATUS=1 ./test-ui.sh
check "a failed run keeps its status and releases the claim" "1 no" "$LSTATUS $(claim_present "$T")"
launchx "$T" --timeout 30 -- STUB_TAMPER=foreign ./test-ui.sh
check "a claim that is no longer ours is not touched on the way out" "0 yes claim:pid=999999:runner=other:token=foreign" "$LSTATUS $(claim_present "$T") $(readlink "$CL")"
fresh_claim
launchx "$T" --timeout 30 -- STUB_TAMPER=fifo ./test-ui.sh
check "a claim that became a FIFO is never opened: no hang, status kept, left alone" "0 no yes" "$LSTATUS $LHUNG $([ -p "$CL" ] && echo yes || echo no)"
fresh_claim
launchx "$T" --timeout 30 -- STUB_TAMPER=directory ./test-ui.sh
check "a claim that became a directory is left alone with its content" "0 yes" "$LSTATUS $([ -f "$CL/inside" ] && echo yes || echo no)"
fresh_claim
for sig in INT TERM HUP; do
  case $sig in INT) WANT=130 ;; *) WANT=143 ;; esac
  rm -f "$T/stubs/test-entered"
  launchx "$T" --signal "$sig" --when-exists "$T/stubs/test-entered" --timeout 40 -- STUB_TEST_EARLY=1 STUB_TEST_SLEEP=60 STUB_TEST_MODE=none ./test-ui.sh
  check "SIG$sig after the browser run left a trace and a failure screenshot: status $WANT is kept and the claim released" "$WANT no" "$LSTATUS $(claim_present "$T")"
  check "  the interrupted run still reported its artifacts, once" "1 1 1" "$(grep -c '^Result of tests' "$OUT") $(grep -c '  trace: ' "$OUT") $(grep -c '  failure screenshot: ' "$OUT")"
done
XPATH="$T/stubs/claimwrap" launchx "$T" --timeout 20 -- STUB_CLAIM_SIGNAL=TERM ./test-ui.sh --self-test --no-install
check "an interruption before any run directory exists reports no artifacts" 0 "$(grep -c '^Result of' "$OUT")"

part "setup-only prepares the tools without a run state, and explicit inputs are still checked"
T=$(new_tree)
rm -f "$T/.local/projects/usage-telemetry-otel/run-state.json"
runx "$T" ./test-ui.sh --setup-only
check "a fresh checkout without a run state: --setup-only succeeds" "0 yes" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
check "  and creates no run or report directory" "0 0" "$(run_dirs "$T") $(ls "$T/tests/ui/playwright-report" 2> /dev/null | wc -l | tr -d ' ')"
runx "$T" ./screenshots.sh --setup-only
check "  screenshots.sh --setup-only too" 0 "$RC"
runx "$T" ./test-ui.sh
check "  but a test run still needs the state" "1 yes" "$RC $(grep -q 'no run state' "$OUT" && echo yes || echo no)"
for args in "--state $T/absent.json" "--scenario $T/absent.json" "--grafana-url ftp://x" "--grafana-url http://user:pw@h"; do
  # shellcheck disable=SC2086 # the words of $args are separate options on purpose
  runx "$T" ./test-ui.sh --setup-only $args
  check "an explicit input that is wrong still fails --setup-only: $args" 1 "$RC"
done
runx "$T" GRAFANA_URL=http://user:pw@h ./test-ui.sh --setup-only
check "a wrong GRAFANA_URL still fails --setup-only" 1 "$RC"
runx "$T" GRAFANA_DATASOURCE_UID="a b" ./test-ui.sh --setup-only
check "a wrong GRAFANA_DATASOURCE_UID still fails --setup-only" 1 "$RC"
runx "$T" ./test-ui.sh --setup-only --state "$T/.local/projects/usage-telemetry-otel/absent.json"
check "an explicit missing state still fails --setup-only" 1 "$RC"
write_state "$T"
runx "$T" ./test-ui.sh --setup-only --state "$T/.local/projects/usage-telemetry-otel/run-state.json"
check "an explicit valid state is accepted by --setup-only" 0 "$RC"
printf '%s\n' "$THIN_STATE_JSON" > "$T/thin.json"
runx "$T" ./test-ui.sh --setup-only --state "$T/thin.json"
check "  an explicit state that the flows cannot use is refused even by --setup-only" 1 "$RC"

part "structured output and diagnostics are separate streams"
T=$(new_tree)
runx "$T" NODE_OPTIONS="--require $T/stubs/warn.cjs" ./test-ui.sh
check "a harmless Node warning on stderr does not reach the prepared input (full run passes)" "0 yes 0" "$RC $(grep -q 'harmless warning' "$OUT" && echo yes || echo no) $(grep -c 'is not usable\|could not be sealed' "$OUT")"
runx "$T" NODE_OPTIONS="--require $T/stubs/warn.cjs" ./test-ui.sh --setup-only
check "  and --setup-only passes" 0 "$RC"
printf '%s\n' "$THIN_STATE_JSON" > "$T/thin.json"
runx "$T" NODE_OPTIONS="--require $T/stubs/warn.cjs" ./test-ui.sh --state "$T/thin.json"
check "  an error keeps its diagnostics and its status next to a warning" "1 yes" "$RC $(grep -c 'global ID' "$OUT" | sed 's/^0$/no/;s/^[1-9].*$/yes/')"

part "readiness runs the commands the run will invoke, not only package metadata and the launch API"
T=$(new_tree)
runx "$T" ./test-ui.sh --setup-only
check "a whole install is ready and needs no npm" "0 0" "$RC $(npm_calls "$T")"
check "  the installer entry and the test entry were both executed" "1 1" "$(grep -c '^playwright install --dry-run chromium' "$T/stubs/log") $(grep -c '^playwright test --list --config=.*readiness' "$T/stubs/log")"
touch "$T/stubs/broken"
runx "$T" ./test-ui.sh --setup-only --no-install
check "a Playwright CLI that cannot load (a locked package is whole but a file of its CLI is missing): --no-install is a failure" "1 no 0" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no) $(npm_calls "$T")"
check "  the failure shows what the command said" 1 "$(grep -c "Cannot find module './lib/program'" "$OUT")"
runx "$T" ./test-ui.sh --setup-only
check "  with installs allowed it is repaired once by npm ci and then ready" "0 1 yes" "$RC $(npm_calls "$T") $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
runx "$T" ./test-ui.sh --setup-only
check "  and the normal path afterwards runs no npm at all" "0 0" "$RC $(npm_calls "$T")"
runx "$T" STUB_LIST_FAILS=3 ./test-ui.sh --setup-only
check "a command that still fails after the repair is a failure, not a loop" "1 1" "$RC $(npm_calls "$T")"
check "  claim released" no "$(claim_present "$T")"
runx "$T" STUB_DRYRUN_FAILS=4 ./test-ui.sh --setup-only --no-install
check "an installer entry that fails is not ready either" 1 "$RC"

part "the installer writes only inside the browser directory it was given"
T=$(new_tree)
B=$T/tests/ui/.browsers
OUTSIDE=$T/outside
fresh() { rm -rf "$B" "$OUTSIDE"; mkdir -p "$B" "$OUTSIDE"; : > "$T/stubs/log"; }
dest_refused() { # dest_refused <description> <message fragment> [env assignments]
  local what=$1 frag=$2
  shift 2
  runx "$T" "$@" ./test-ui.sh --setup-only
  check "$what: refused before the installer runs" "1 0" "$RC $(grep -c '^INSTALL-REAL' "$T/stubs/log")"
  check "  the message says why: $frag" yes "$(grep -q "$frag" "$OUT" && echo yes || echo no)"
  check "  nothing was written outside" 0 "$(ls "$OUTSIDE" | wc -l | tr -d ' ')"
  check "  the claim was released" no "$(claim_present "$T")"
}
fresh; ln -s "$OUTSIDE" "$B/.links"
dest_refused "a .links that is a symbolic link" "symbolic link"
fresh; ln -s "$OUTSIDE" "$B/__dirlock"
dest_refused "a __dirlock that is a symbolic link" "symbolic link"
fresh; mkdir "$B/.links"; echo x > "$T/target-file"; ln -s "$T/target-file" "$B/.links/abc"
dest_refused "an entry of .links that is a symbolic link" "symbolic link"
check "  and the target of that link keeps its content" "x" "$(cat "$T/target-file")"
fresh; ln -s "$OUTSIDE" "$B/chromium-1255"
dest_refused "the Chromium install location as a symbolic link" "symbolic link"
fresh; ln -s "$OUTSIDE" "$B/ffmpeg-1013"
dest_refused "the ffmpeg install location as a symbolic link" "symbolic link"
fresh; : > "$B/.links"
dest_refused "a .links that is a regular file" "not a directory"
fresh
dest_refused "an install location outside the browser directory in the installer's own report" "outside the browser directory" STUB_DRYRUN_ROOT="$OUTSIDE"
fresh
dest_refused "an installer report with no install location (fail closed)" "no install location" STUB_DRYRUN_EMPTY=1
fresh; mkdir "$B/.links"; mkfifo "$B/.links/abc"
dest_refused "an entry of .links that is a FIFO (the installer would block opening it for writing)" "not a regular file"
fresh; mkdir "$B/.links"; mkdir "$B/.links/abc"
dest_refused "an entry of .links that is a directory" "not a regular file"

part "the browser launcher's own marker files are checked even when nothing is installed"
T=$(new_tree)
B=$T/tests/ui/.browsers
OUTSIDE=$T/outside
fresh_browser() { rm -rf "$B" "$OUTSIDE"; mkdir -p "$B/chromium-stub" "$B/chromium-1255" "$B/chromium_headless_shell-1255" "$B/ffmpeg-1013" "$OUTSIDE"; : > "$B/chromium-stub/chrome"; echo precious > "$OUTSIDE/sentinel"; : > "$T/stubs/log"; }
marker_refused() { # marker_refused <description> <location directory> <message fragment>
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$1: --setup-only --no-install is refused before anything can launch the browser" "1 no" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
  check "  the message names the marker: $3" yes "$(grep -q "$3" "$OUT" && echo yes || echo no)"
  check "  the sentinel outside was not touched and no browser launch probe ran" "precious" "$(cat "$OUTSIDE/sentinel")"
}
fresh_browser; ln -s "$OUTSIDE/sentinel" "$B/chromium_headless_shell-1255/DEPENDENCIES_VALIDATED"
marker_refused "a DEPENDENCIES_VALIDATED marker of the headless shell that links to a file elsewhere" "$B/chromium_headless_shell-1255" "DEPENDENCIES_VALIDATED"
fresh_browser; ln -s "$OUTSIDE/sentinel" "$B/chromium-1255/DEPENDENCIES_VALIDATED"
marker_refused "the same for the Chromium directory" "$B/chromium-1255" "DEPENDENCIES_VALIDATED"
fresh_browser; mkfifo "$B/chromium-1255/DEPENDENCIES_VALIDATED"
marker_refused "a marker that is a FIFO (writing it would block)" "$B/chromium-1255" "not a regular file"
fresh_browser; mkdir "$B/chromium_headless_shell-1255/DEPENDENCIES_VALIDATED"
marker_refused "a marker that is a directory" "$B/chromium_headless_shell-1255" "not a regular file"
fresh_browser; ln -s "$OUTSIDE/sentinel" "$B/chromium-1255/INSTALLATION_COMPLETE"
marker_refused "an INSTALLATION_COMPLETE marker that links elsewhere" "$B/chromium-1255" "INSTALLATION_COMPLETE"
for loc in chromium_headless_shell-1255 chromium-1255 ffmpeg-1013; do
  fresh_browser; rmdir "$B/$loc"; echo precious > "$OUTSIDE/sentinel"; ln -s "$OUTSIDE" "$B/$loc"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "the install location $loc itself a link to a directory elsewhere: --setup-only --no-install is refused (launch mode checks the location, not only the marker)" "1 no" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
  check "  the message names the location and says it is a link" yes "$(grep -q "install location $B/$loc is a symbolic link" "$OUT" && echo yes || echo no)"
done
fresh_browser; : > "$B/chromium-1255/DEPENDENCIES_VALIDATED"; : > "$B/chromium_headless_shell-1255/DEPENDENCIES_VALIDATED"; : > "$B/chromium-1255/INSTALLATION_COMPLETE"; mkdir -p "$B/chromium-1255/app.bundle"; ln -s app.bundle "$B/chromium-1255/Current"
runx "$T" ./test-ui.sh --setup-only --no-install
check "ordinary marker files and a link inside the application bundle are accepted: ready" "0 yes" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
fresh_browser
runx "$T" ./test-ui.sh --setup-only --no-install
check "no markers at all (a launcher creates them itself): ready" 0 "$RC"

part "a probe client the dashboard can offer, and the other selections, are decided before any effect"
T=$(new_tree)
THIN_NO_PROBE=$(printf '%s' "$STATE_JSON" | node -e 'const s = JSON.parse(require("fs").readFileSync(0, "utf8")); s.expectedSeries = s.expectedSeries.filter((row) => row.clientId !== s.clients.probe); console.log(JSON.stringify(s))')
printf '%s\n' "$THIN_NO_PROBE" > "$T/no-probe.json"
runx "$T" ./test-ui.sh --state "$T/no-probe.json"
check "a run state whose probe client has no series at all: the tests runner refuses it (Grafana would not offer that client)" "1 yes" "$RC $(grep -q 'probe' "$OUT" && echo yes || echo no)"
no_effects "$T" "a state without a selectable probe client"
runx "$T" ./screenshots.sh --state "$T/no-probe.json"
check "  the screenshots runner does not use the probe client and accepts it" 0 "$RC"

part "the legitimate layouts"
T=$(new_tree)
B=$T/tests/ui/.browsers
OUTSIDE=$T/outside
fresh
fresh; mkdir -p "$B/chromium-1255/app.bundle/Contents"; ln -s Contents "$B/chromium-1255/app.bundle/Current"; mkdir "$B/.links"
runx "$T" ./test-ui.sh --setup-only
check "a legitimate layout (a real install directory whose own contents hold a link) is not rejected" "0 1" "$RC $(grep -c '^INSTALL-REAL' "$T/stubs/log")"
check "  and the installer is told not to delete other browser revisions of that directory" 1 "$(grep -c '^INSTALL-REAL gc=1$' "$T/stubs/log")"

part "the permission probe works with GNU and with BSD stat"
MODE_FILE=$TMP/mode-probe
: > "$MODE_FILE"
chmod 444 "$MODE_FILE"
mkdir -p "$TMP/stat-gnu" "$TMP/stat-bsd" "$TMP/stat-gnu-noisy" "$TMP/stat-none"
# GNU branch: the real GNU coreutils stat when the machine has it (gstat, or a stat that says it is GNU), otherwise a stand-in that
# behaves like it (marked in the check name)
GNU_KIND="a stand-in with GNU behavior"
if command -v gstat > /dev/null 2>&1 && gstat --version 2> /dev/null | grep -q 'GNU coreutils'; then
  ln -s "$(command -v gstat)" "$TMP/stat-gnu/stat"
  GNU_KIND="real GNU coreutils stat"
elif stat --version 2> /dev/null | grep -q 'GNU coreutils'; then
  ln -s "$(command -v stat)" "$TMP/stat-gnu/stat"
  GNU_KIND="real GNU coreutils stat"
else
  printf '#!/bin/sh\ncase "$1" in\n  -c) shift; [ "$1" = "%%a" ] && { shift; for f in "$@"; do printf "444\\n"; done; exit 0; } ;;\n  -f) echo "  File: $3"; echo "    ID: 1 Namelen: ?       Type: ext2/ext3"; echo "stat: cannot read file system information for %%Lp" >&2; exit 1 ;;\nesac\nexit 1\n' > "$TMP/stat-gnu/stat"
fi
chmod +x "$TMP/stat-gnu/stat" 2> /dev/null || true
# BSD branch: the real BSD stat when this is a BSD (macOS), otherwise a stand-in: -c is an illegal option, -f '%Lp' answers
if /usr/bin/stat -f '%Lp' "$MODE_FILE" > /dev/null 2>&1; then
  ln -s /usr/bin/stat "$TMP/stat-bsd/stat"
  BSD_KIND="real BSD stat"
else
  printf '#!/bin/sh\ncase "$1" in\n  -f) printf "444\\n"; exit 0 ;;\nesac\necho "stat: illegal option -- c" >&2\nexit 1\n' > "$TMP/stat-bsd/stat"
  chmod +x "$TMP/stat-bsd/stat"
  BSD_KIND="a stand-in with BSD behavior"
fi
# a stat whose -c probe prints text and then fails (the case the old code mishandled): the text must not reach the answer
printf '#!/bin/sh\ncase "$1" in\n  -c) echo "garbage from a failed probe"; exit 1 ;;\n  -f) printf "444\\n"; exit 0 ;;\nesac\nexit 1\n' > "$TMP/stat-gnu-noisy/stat"
printf '#!/bin/sh\necho "also garbage"\nexit 1\n' > "$TMP/stat-none/stat"
chmod +x "$TMP/stat-gnu-noisy/stat" "$TMP/stat-none/stat"
check "the GNU branch ($GNU_KIND): the mode is exactly 444 and nothing else" "444" "$(PATH="$TMP/stat-gnu:$PATH" octal_mode "$MODE_FILE")"
check "the BSD branch ($BSD_KIND): the mode is exactly 444 and nothing else" "444" "$(PATH="$TMP/stat-bsd:$PATH" octal_mode "$MODE_FILE")"
check "a failed first probe that printed text: only the answer of the second probe is taken" "444" "$(PATH="$TMP/stat-gnu-noisy:$PATH" octal_mode "$MODE_FILE")"
check "two files give two lines (both branches)" "444 444 444 444" \
  "$({ PATH="$TMP/stat-gnu:$PATH" octal_mode "$MODE_FILE" "$MODE_FILE"; PATH="$TMP/stat-bsd:$PATH" octal_mode "$MODE_FILE" "$MODE_FILE"; } | tr '\n' ' ' | sed 's/ $//')"
rc=0
out=$(PATH="$TMP/stat-none:$PATH" octal_mode "$MODE_FILE") || rc=$?
check "a stat that answers neither probe: nothing is printed and the status is 1" "1 [] " "$rc [$out] "
part "output directories and the npm entry points"
T=$(new_tree)
runx "$T" ./test-ui.sh
runx "$T" ./test-ui.sh
check "two invocations leave two separate run and report directories" "2 2" "$(run_dirs "$T") $(ls "$T/tests/ui/playwright-report" | wc -l | tr -d ' ')"
check "  and each run's input is sealed read-only" "444 444" "$(octal_mode "$T"/tests/ui/test-results/*/input.sealed.json | tr '\n' ' ' | sed 's/ $//')"
SCRIPTS=$(cd "$SRC/tests/ui" && node -e 'const s = require("./package.json").scripts; console.log(["test", "screenshots"].map((n) => n + "=" + s[n]).join(" "))')
check "npm test and npm run screenshots call the public wrappers" "test=../../test-ui.sh screenshots=../../screenshots.sh" "$SCRIPTS"
check "  and the scripts they name exist and are executable" "yes yes" "$([ -x "$SRC/tests/ui/../../test-ui.sh" ] && echo yes || echo no) $([ -x "$SRC/tests/ui/../../screenshots.sh" ] && echo yes || echo no)"

printf '\n%s passed, %s failed\n' "$passed" "$failures"
[ "$failures" -eq 0 ]
