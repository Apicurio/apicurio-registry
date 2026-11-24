#!/usr/bin/env bash
# Tests of tests/ci-suite.sh and tests/ci-artifacts.py without a container, a browser or a network: the artifact policy against crafted
# trees, and the suite itself end to end against stand-in commands, to show that a failing phase fails the suite (and nothing else is
# skipped by it), that a second invocation runs everything again, that the kept scenario stack is removed on every path including a
# signal, and that only files the allowlist names and verification accepts reach the directory that is uploaded.
#
# Usage: ./tests/ci-suite-test.sh
# Prerequisites: bash 3.2 or later, python3, jq, od, a shell that supports job control (the suite supervises its phases as process groups).
# Ends with "All checks passed." and status 0, or with the failed checks and status 1.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2
EXAMPLE=$PWD

TMP=$(cd "$(mktemp -d "${TMPDIR:-/tmp}/usage-telemetry-otel-ci-test.XXXXXX")" && pwd -P)
trap 'rm -rf "$TMP"' EXIT
failures=0
passed=0
check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then
    passed=$((passed + 1))
    printf '  ok    %s\n' "$1"
  else
    failures=$((failures + 1))
    printf '  FAIL  %s\n        expected: %s\n        actual:   %s\n' "$1" "$2" "$3"
  fi
}
part() { printf '\n%s\n' "$1"; }
TAB=$(printf '\t')

# ---------------------------------------------------------------------------------------------------------------------------------
part "the artifact policy: a fresh tree of an invocation"
COLLECT="python3 -I $EXAMPLE/tests/ci-artifacts.py"
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
START=$(($(date +%s) - 5))
UI=$TMP/ui
mkdir -p "$UI/test-results" "$UI/playwright-report"
# png <path>: a file with the 8 byte PNG signature (written directly: a command substitution would strip its final newline byte)
png() { printf '\211PNG\r\n\032\n....' > "$1"; }

# mktrace <path> <safe|network|resource|cookie|bad-image>: a trace archive in the shape of the safe mode, or spoiled in one way
mktrace() {
  python3 -I - "$1" "$2" << 'PY'
import sys, zipfile
path, mode = sys.argv[1], sys.argv[2]
png = b"\x89PNG\r\n\x1a\n" + b"x" * 8
jpeg = b"\xff\xd8\xff" + b"x" * 8
with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
    text = b'{"version":10,"type":"context-options"}\n'
    if mode == "cookie":
        text += b'{"type":"log","message":"Set-Cookie: grafana_session=abc"}\n'
    z.writestr("trace.trace", text)
    z.writestr("trace.stacks", b"{}")
    z.writestr("trace.network", b'{"type":"resource-snapshot"}\n' if mode == "network" else b"")
    z.writestr("aria/ab@1-before.json", b"{}")
    z.writestr("screenshots/ab@1-before.png", png if mode != "bad-image" else b"not an image")
    z.writestr("screencast/page@1.jpeg", jpeg)
    if mode == "resource":
        z.writestr("resources/0123456789012345678901234567890123456789.css", b"body{}")
PY
}

RUN=$UI/test-results/$STAMP-4242-tests
REPORT=$UI/playwright-report/$STAMP-4242-tests
mkdir -p "$RUN/screenshots" "$RUN/pw/case-one-usage" "$RUN/pw/.hidden" "$REPORT/data"
echo '{"suites":[]}' > "$RUN/results.json"
png "$RUN/screenshots/overview.png"
png "$RUN/screenshots/filtered.png"
png "$RUN/screenshots/extra.png"
png "$RUN/pw/case-one-usage/test-failed-1.png"
mktrace "$RUN/pw/case-one-usage/trace.zip" safe
echo 'the page shows the table' > "$RUN/pw/case-one-usage/error-context.md"
echo '{"raw":"run state"}' > "$RUN/input.sealed.json"
echo '{"status":"failed"}' > "$RUN/pw/.last-run.json"
echo '<html>report</html>' > "$REPORT/index.html"
png "$REPORT/data/0123456789abcdef0123456789abcdef01234567.png"
LOGS=$TMP/logs
COMPONENTS=$TMP/components
mkdir -p "$LOGS" "$COMPONENTS/project-one"
echo 'unit: ok' > "$LOGS/unit.log"
echo 'stack: ready' > "$COMPONENTS/project-one/registry-1.log"
printf 'unit\tpass\t3\n' > "$TMP/summary.tsv"

OUT=$TMP/out-good
$COLLECT --out "$OUT" --start "$START" --ui-dir "$UI" --run-dir "$RUN" --report-dir "$REPORT" --logs "$LOGS" --components "$COMPONENTS" --summary "$TMP/summary.tsv" > "$TMP/good.out" 2>&1
check "a clean invocation is published without a violation (status 0)" 0 "$?"
check "  exactly the allowlisted files are published" \
  "components/project-one/registry-1.log
logs/unit.log
report/$STAMP-4242-tests/data/0123456789abcdef0123456789abcdef01234567.png
report/$STAMP-4242-tests/index.html
summary.tsv
ui/$STAMP-4242-tests/pw/case-one-usage/error-context.md
ui/$STAMP-4242-tests/pw/case-one-usage/test-failed-1.png
ui/$STAMP-4242-tests/pw/case-one-usage/trace.zip
ui/$STAMP-4242-tests/results.json
ui/$STAMP-4242-tests/screenshots/filtered.png
ui/$STAMP-4242-tests/screenshots/overview.png" \
  "$(jq -r '.published[].path' "$OUT/MANIFEST.json")"
check "  the raw run input is excluded by name, with its reason" "raw run input, never published" \
  "$(jq -r '.excluded[] | select(.path | endswith("input.sealed.json")) | .reason' "$OUT/MANIFEST.json")"
check "  so are an image and a hidden file that are not on the list" "not on the allowlist|not on the allowlist" \
  "$(jq -r '[.excluded[] | select(.path | endswith("extra.png") or endswith(".last-run.json")) | .reason] | join("|")' "$OUT/MANIFEST.json")"
check "  nothing but the manifest and those files is in the directory (file count)" "12" "$(find "$OUT" -type f | wc -l | tr -d ' ')"
check "  the manifest records a digest and the size of every published file" "11 11" \
  "$(jq -r '[.published[] | select(.sha256 | test("^[0-9a-f]{64}$"))] | length' "$OUT/MANIFEST.json") $(jq -r '[.published[] | select(.bytes > 0)] | length' "$OUT/MANIFEST.json")"

# spoil <name> <description> <text the violation must contain> <function>: one fresh tree, spoiled in one way by the function, collected
SPOIL_N=0
spoil() { # spoil <name> <description> <expected text in the violations> <function that spoils $RUN2 $REPORT2 $LOGS2>
  local name=$1 out=$TMP/out-$1 rc
  SPOIL_N=$((SPOIL_N + 1))
  RUN2=$UI/test-results/$STAMP-$((5000 + SPOIL_N))-tests
  REPORT2=$UI/playwright-report/$STAMP-$((5000 + SPOIL_N))-tests
  LOGS2=$TMP/logs-$name
  mkdir -p "$RUN2/screenshots" "$RUN2/pw/c-usage" "$REPORT2/data" "$LOGS2"
  echo '{}' > "$RUN2/results.json"
  png "$RUN2/screenshots/overview.png"
  echo '<html></html>' > "$REPORT2/index.html"
  echo 'ok' > "$LOGS2/unit.log"
  "$4"
  $COLLECT --out "$out" --start "$START" --ui-dir "$UI" --run-dir "$RUN2" --report-dir "$REPORT2" --logs "$LOGS2" > "$TMP/spoil-$name.out" 2> "$TMP/spoil-$name.err"
  rc=$?
  check "$2: status 1" 1 "$rc"
  check "  the violation says why" yes "$(grep -q "$3" "$TMP/spoil-$name.err" && echo yes || echo no)"
}
stale_file() { touch -t 200001010000 "$RUN2/results.json"; }
spoil stale "a stale file inside a fresh run" "stale" stale_file
check "  and it is not published" "0" "$(jq '[.published[] | select(.path | endswith("results.json"))] | length' "$TMP/out-stale/MANIFEST.json")"
trace_network() { mktrace "$RUN2/pw/c-usage/trace.zip" network; }
spoil trace-network "a trace that records network activity" "records network" trace_network
trace_resource() { mktrace "$RUN2/pw/c-usage/trace.zip" resource; }
spoil trace-resource "a trace with page resources (a DOM snapshot mode)" "outside the safe trace shape" trace_resource
trace_cookie() { mktrace "$RUN2/pw/c-usage/trace.zip" cookie; }
spoil trace-cookie "a trace whose text holds a cookie" "Cookie" trace_cookie
trace_image() { mktrace "$RUN2/pw/c-usage/trace.zip" bad-image; }
spoil trace-image "a trace image that is not an image" "not a PNG" trace_image
trace_zip() { echo 'not a zip' > "$RUN2/pw/c-usage/trace.zip"; }
spoil trace-notzip "a trace that is not an archive" "not a readable zip" trace_zip
log_secret() { printf 'GET /x\nAuthorization: Bearer abcdefghijklmnopqrstuvwxyz0123\n' > "$LOGS2/unit.log"; }
spoil log-secret "a log with an Authorization header" "Authorization" log_secret
check "  its log is not published" "0" "$(find "$TMP/out-log-secret" -name 'unit.log' | wc -l | tr -d ' ')"
html_jwt() { echo '<script>eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghij</script>' > "$REPORT2/index.html"; }
spoil report-jwt "a report holding a web token" "web token" html_jwt
png_bad() { echo 'not a png' > "$RUN2/screenshots/overview.png"; }
spoil png-bad "a screenshot that is not a PNG" "not a PNG" png_bad
json_bad() { echo '{not json' > "$RUN2/results.json"; }
spoil json-bad "a results file that is not JSON" "not valid" json_bad
link_dir() { ln -s "$TMP/ui" "$RUN2/pw/escape"; }
spoil symlink-dir "a link to a directory inside a run" "symbolic link" link_dir
# a link to a perfectly valid file outside the run: only the link itself makes it unacceptable
echo '{"outside":"secret-outside-content"}' > "$TMP/outside.json"
link_valid_file() { rm "$RUN2/results.json"; ln -s "$TMP/outside.json" "$RUN2/results.json"; }
spoil symlink-file "a results file that is a link to a valid JSON file outside the run" "symbolic link" link_valid_file
check "  what the link points to is not published" "0" "$(grep -rl secret-outside-content "$TMP/out-symlink-file" 2> /dev/null | wc -l | tr -d ' ')"
link_valid_png() { rm "$RUN2/screenshots/overview.png"; png "$TMP/outside.png"; ln -s "$TMP/outside.png" "$RUN2/screenshots/overview.png"; }
spoil symlink-png "a screenshot that is a link to a valid PNG outside the run" "symbolic link" link_valid_png
check "  nothing at all of that run is published from it" "0" "$(jq '[.published[] | select(.path | endswith("overview.png"))] | length' "$TMP/out-symlink-png/MANIFEST.json")"

# a very long log is published truncated (with a note), not refused
BIGRUN=$UI/test-results/$STAMP-6001-tests
mkdir -p "$BIGRUN" "$TMP/logs-big"
echo '{}' > "$BIGRUN/results.json"
head -c 3000000 /dev/zero | tr '\0' 'y' > "$TMP/logs-big/unit.log"
$COLLECT --out "$TMP/out-big" --start "$START" --ui-dir "$UI" --run-dir "$BIGRUN" --logs "$TMP/logs-big" > /dev/null 2>&1
check "a log of 3 MB is published truncated (status 0)" "0 yes" "$? $(head -c 40 "$TMP/out-big/logs/unit.log" | grep -q '^\[truncated' && echo yes || echo no)"
check "  within 2 MiB plus the note" "yes" "$([ "$(wc -c < "$TMP/out-big/logs/unit.log")" -le 2097400 ] && echo yes || echo no)"

# the suite's own summary does not pass through a run directory, so its link is judged on its own
printf 'unit\tpass\t3\n' > "$TMP/summary-target.tsv"
ln -s "$TMP/summary-target.tsv" "$TMP/summary-link.tsv"
$COLLECT --out "$TMP/out-summary-link" --start "$START" --ui-dir "$UI" --summary "$TMP/summary-link.tsv" > /dev/null 2> "$TMP/summary-link.err"
check "a summary file that is a link is refused (status 1) and not published" "1 yes 0" \
  "$? $(grep -q 'not a regular file' "$TMP/summary-link.err" && echo yes || echo no) $(jq '[.published[] | select(.path == "summary.tsv")] | length' "$TMP/out-summary-link/MANIFEST.json")"

part "the artifact policy: directories that are not this invocation's"
OLDNAME=20000101T000000Z-1-tests
mkdir -p "$UI/test-results/$OLDNAME"
echo '{}' > "$UI/test-results/$OLDNAME/results.json"
$COLLECT --out "$TMP/out-oldrun" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/$OLDNAME" > /dev/null 2> "$TMP/oldrun.err"
check "a run that started before this invocation is refused as stale (status 1)" "1 yes" "$? $(grep -q 'stale' "$TMP/oldrun.err" && echo yes || echo no)"
mkdir -p "$TMP/elsewhere/$STAMP-1-tests"
$COLLECT --out "$TMP/out-elsewhere" --start "$START" --ui-dir "$UI" --run-dir "$TMP/elsewhere/$STAMP-1-tests" > /dev/null 2> "$TMP/elsewhere.err"
check "a run directory outside test-results is refused (status 1)" "1 yes" "$? $(grep -q 'not a directory directly below' "$TMP/elsewhere.err" && echo yes || echo no)"
ln -s "$RUN" "$UI/test-results/$STAMP-9999-tests"
$COLLECT --out "$TMP/out-linkrun" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/$STAMP-9999-tests" > /dev/null 2> "$TMP/linkrun.err"
check "a run directory that is a link is refused (status 1)" "1 yes" "$? $(grep -q 'through a link' "$TMP/linkrun.err" && echo yes || echo no)"
mkdir -p "$UI/test-results/not-a-run-name"
$COLLECT --out "$TMP/out-badname" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/not-a-run-name" > /dev/null 2>&1
check "a directory that is not named like a run is refused (status 1)" 1 "$?"
mkdir -p "$TMP/out-used"
echo mine > "$TMP/out-used/file"
$COLLECT --out "$TMP/out-used" --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "an output directory that is not empty is refused (status 2) and left alone" "2 mine" "$? $(cat "$TMP/out-used/file")"
$COLLECT --out relative/out --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "a relative output path is refused (status 2)" 2 "$?"
$COLLECT --out "$TMP/out-none" --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "an invocation with nothing to publish is not a violation (status 0)" "0 0" "$? $(jq '.published | length' "$TMP/out-none/MANIFEST.json")"

# ---------------------------------------------------------------------------------------------------------------------------------
part "the suite functions"
cat > "$TMP/fn-test.sh" << SCRIPT
CI_SUITE_SOURCE_ONLY=1 . "$EXAMPLE/tests/ci-suite.sh"
LOG_DIR=$TMP/fn-logs
SUMMARY=$TMP/fn-summary.tsv
mkdir -p "\$LOG_DIR"
: > "\$SUMMARY"
run_phase passing 30 bash -c 'echo hello'
run_phase failing 30 bash -c 'echo "went wrong" >&2; exit 3'
run_phase hanging 1 bash -c 'sleep 30'
skip_phase waiting "an earlier phase failed"
{
  printf 'passing=%s\\n' "\$(phase_passed passing && echo yes || echo no)"
  printf 'failing=%s\\n' "\$(phase_passed failing && echo yes || echo no)"
  printf 'hanging=%s\\n' "\$(phase_passed hanging && echo yes || echo no)"
  printf 'waiting=%s\\n' "\$(phase_passed waiting && echo yes || echo no)"
  printf 'unknown=%s\\n' "\$(phase_passed never-ran && echo yes || echo no)"
  cut -f1,2 "\$SUMMARY"
  printf 'log=%s\\n' "\$(cat "\$LOG_DIR/failing.log")"
} > "$TMP/fn.out"
SCRIPT
bash "$TMP/fn-test.sh" > /dev/null 2>&1
check "only a phase that passed counts as passed (a failed, a timed out, a skipped and an unknown one do not)" \
  "passing=yes failing=no hanging=no waiting=no unknown=no" "$(sed -n 1,5p "$TMP/fn.out" | tr '\n' ' ' | sed 's/ $//')"
check "  the status of each is kept: the exit code, a timeout, a skip" "passing${TAB}pass failing${TAB}fail(3) hanging${TAB}timeout waiting${TAB}skipped(an earlier phase failed)" \
  "$(sed -n 6,9p "$TMP/fn.out" | tr '\n' ' ' | sed 's/ $//')"
check "  the whole output of a phase is kept in its log" "log=went wrong" "$(sed -n 10p "$TMP/fn.out")"

KEEP='--keep: nothing was cleaned up. Project ci-scn-abc123 now has 4 container(s) and 2 volume(s). The kept stack was started by the dashboard-scenarios case. Stop it with: COMPOSE_PROJECT_NAME=ci-scn-abc123 LOCAL_DIR=/tmp/usage-telemetry-otel-test.Xy12/local ./teardown.sh --purge'
printf '%s\nLogs of every script call: /tmp/usage-telemetry-otel-test.Xy12/logs\n' "$KEEP" > "$TMP/keep.log"
printf '%s\n%s\n' "$KEEP" "$KEEP" > "$TMP/keep-twice.log"
sed 's#LOCAL_DIR=/tmp#LOCAL_DIR=tmp#' "$TMP/keep.log" > "$TMP/keep-relative.log"
printf 'run directory: /a/b/test-results/20260101T000000Z-1-tests\nreport directory: /a/b/playwright-report/20260101T000000Z-1-tests\nrun directory: /a/b/test-results/20260101T000000Z-1-tests\nthe run directory: /not/at/line/start\n' > "$TMP/printed.log"
mkdir -p "$TMP/printed"
cp "$TMP/printed.log" "$TMP/printed/x.log"
cat > "$TMP/parse-test.sh" << SCRIPT
CI_SUITE_SOURCE_ONLY=1 . "$EXAMPLE/tests/ci-suite.sh"
LOG_DIR=$TMP/printed
{
  printf 'one=%s\\n' "\$(keep_line_local "$TMP/keep.log" ci-scn-abc123)"
  printf 'other-project=%s\\n' "\$(keep_line_local "$TMP/keep.log" ci-scn-zzz999 || echo none)"
  printf 'twice=%s\\n' "\$(keep_line_local "$TMP/keep-twice.log" ci-scn-abc123 || echo none)"
  printf 'relative=%s\\n' "\$(keep_line_local "$TMP/keep-relative.log" ci-scn-abc123 || echo none)"
  printf 'runs=%s\\n' "\$(printed_directories run | tr '\\n' ' ')"
  printf 'reports=%s\\n' "\$(printed_directories report | tr '\\n' ' ')"
} > "$TMP/parse.out"
SCRIPT
bash "$TMP/parse-test.sh" > /dev/null 2>&1
check "the kept stack is located from the runner's own instruction, for this project only, exactly once, as an absolute path" \
  "one=/tmp/usage-telemetry-otel-test.Xy12/local other-project=none twice=none relative=none" "$(sed -n 1,4p "$TMP/parse.out" | tr '\n' ' ' | sed 's/ $//')"
check "  the run and report directories are the ones printed at the start of a line, once each" \
  "runs=/a/b/test-results/20260101T000000Z-1-tests reports=/a/b/playwright-report/20260101T000000Z-1-tests" "$(sed -n 5,6p "$TMP/parse.out" | tr '\n' ' ' | sed 's/  */ /g; s/ $//')"

# ---------------------------------------------------------------------------------------------------------------------------------
part "the suite end to end, against stand-in commands"
EX=$TMP/example
BIN=$TMP/bin
mkdir -p "$EX/tests/ui/test" "$EX/tests/ui/test-results" "$EX/tests/ui/playwright-report" "$BIN"
cp "$EXAMPLE/lib.sh" "$EX/lib.sh"
cp "$EXAMPLE/tests/ci-suite.sh" "$EXAMPLE/tests/ci-artifacts.py" "$EX/tests/"
cat > "$BIN/npm" << 'SCRIPT'
#!/usr/bin/env bash
printf 'npm %s\n' "$*" >> "$STUB_LOG"
case " ${STUB_FAIL:-} " in *" npm-$4 "*) echo "injected failure"; exit 1 ;; esac
SCRIPT
chmod 755 "$BIN/npm"
simple_stub() { # simple_stub <path> : a command that logs its call, fails when STUB_FAIL names it, otherwise succeeds
  local label
  label=$(basename "$1" .sh)
  mkdir -p "$(dirname "$1")"
  cat > "$1" << SCRIPT
#!/usr/bin/env bash
printf '%s %s\n' "$label" "\$*" >> "\$STUB_LOG"
case " \${STUB_FAIL:-} " in *" $label "*) echo "injected failure of $label" >&2; exit 1 ;; esac
echo "stand-in $label ok"
SCRIPT
  chmod 755 "$1"
}
for stub in tests/unit-test.sh tests/dashboard-unit-test.sh tests/dashboard-promql-test.sh tests/ci-suite-test.sh tests/dashboard-test.sh \
  tests/lifecycle-test.sh tests/ui/test/runner-test.sh tests/ui/test/clone-dir-test.sh tests/ui/test/real-tooling-test.sh; do simple_stub "$EX/$stub"; done
cat > "$EX/test-ui.sh" << 'SCRIPT'
#!/usr/bin/env bash
case " $* " in
  *" --setup-only "*) label=test-ui-setup ;;
  *" --self-test "*) label=test-ui-selftest ;;
  *" --scenario "*) label=test-ui-scenario ;;
  *) label=test-ui ;;
esac
printf '%s %s\n' "$label" "$*" >> "$STUB_LOG"
case " ${STUB_FAIL:-} " in *" $label "*) echo "injected failure of $label" >&2; exit 1 ;; esac
if [ "$label" != test-ui-setup ]; then
  run="$PWD/tests/ui/test-results/$(date -u +%Y%m%dT%H%M%SZ)-$$-tests"
  mkdir -p "$run"
  echo '{"stats":{}}' > "$run/results.json"
  printf 'run directory: %s\n' "$run"
fi
SCRIPT
cat > "$EX/screenshots.sh" << 'SCRIPT'
#!/usr/bin/env bash
printf 'screenshots %s\n' "$*" >> "$STUB_LOG"
case " ${STUB_FAIL:-} " in *" screenshots "*) echo "injected failure of screenshots" >&2; exit 1 ;; esac
run="$PWD/tests/ui/test-results/$(date -u +%Y%m%dT%H%M%SZ)-$$-screenshots"
mkdir -p "$run/screenshots"
printf '\211PNG\r\n\032\n....' > "$run/screenshots/overview.png"
printf '\211PNG\r\n\032\n....' > "$run/screenshots/filtered.png"
printf 'run directory: %s\n' "$run"
SCRIPT
cat > "$EX/tests/dashboard-scenarios.sh" << 'SCRIPT'
#!/usr/bin/env bash
printf 'scenarios %s\n' "$*" >> "$STUB_LOG"
project=""
while [ $# -gt 0 ]; do case "$1" in --project) project=$2; shift 2 ;; *) shift ;; esac; done
root=$STUB_ROOT/scn
mkdir -p "$root/local" "$root/dashboard-scenarios" "$root/logs"
echo '{"scenarios":[]}' > "$root/dashboard-scenarios/scenarios.json"
echo "call log" > "$root/logs/01-call.log"
case " ${STUB_FAIL:-} " in *" scenarios-early "*) echo "injected early failure of scenarios" >&2; exit 1 ;; esac
printf -- '--keep: nothing was cleaned up. Project %s now has 4 container(s) and 2 volume(s). Stop it with: COMPOSE_PROJECT_NAME=%s LOCAL_DIR=%s/local ./teardown.sh --purge\n' "$project" "$project" "$root"
printf 'Logs of every script call: %s/logs\n' "$root"
[ -z "${STUB_SIGNAL_FILE:-}" ] || { : > "$STUB_SIGNAL_FILE"; sleep 30; }
case " ${STUB_FAIL:-} " in *" scenarios "*) echo "injected failure of scenarios" >&2; exit 1 ;; esac
SCRIPT
cat > "$EX/teardown.sh" << 'SCRIPT'
#!/usr/bin/env bash
printf 'teardown %s project=%s local=%s\n' "$*" "${COMPOSE_PROJECT_NAME:-}" "${LOCAL_DIR:-}" >> "$STUB_LOG"
case " ${STUB_FAIL:-} " in *" teardown "*) echo "injected failure of teardown" >&2; exit 1 ;; esac
SCRIPT
chmod 755 "$EX/test-ui.sh" "$EX/screenshots.sh" "$EX/tests/dashboard-scenarios.sh" "$EX/teardown.sh" "$EX/tests/ci-suite.sh"

# run_suite <name> [env assignments...]: runs the suite in the stand-in example; its output is in $TMP/<name>.out, its status in SUITE_RC
run_suite() {
  local name=$1
  shift
  rm -rf "$TMP/stub-$name"
  mkdir -p "$TMP/stub-$name"
  : > "$TMP/stub-$name/calls.log"
  (cd "$EX" && env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-$name/calls.log" STUB_ROOT="$TMP/stub-$name" REGISTRY_IMAGE=apicurio/apicurio-registry:test \
    CI_ARTIFACT_DIR="$TMP/stub-$name/artifacts" "$@" ./tests/ci-suite.sh) > "$TMP/$name.out" 2>&1
  SUITE_RC=$?
}
skipped() { case "$(phase_status "$1" "$2")" in skipped*) echo yes ;; *) echo no ;; esac; }
phase_status() { awk -F'\t' -v n="$2" '$1 == n { s = $2 } END { print s }' "$TMP/stub-$1/artifacts/work/summary.tsv"; }
calls() { grep -c "^$2 " "$TMP/stub-$1/calls.log" | tr -d ' '; }

run_suite green
check "every stand-in passing: the suite passes (status 0)" "0 yes" "$SUITE_RC $(grep -q '^All phases passed' "$TMP/green.out" && echo yes || echo no)"
check "  all 18 phases are recorded as passed" "18 18" "$(awk -F'\t' '$2 == "pass"' "$TMP/stub-green/artifacts/work/summary.tsv" | wc -l | tr -d ' ') $(wc -l < "$TMP/stub-green/artifacts/work/summary.tsv" | tr -d ' ')"
check "  the lifecycle and scenario suites ran after the browser phases, one after the other, each once" "1 1 1" "$(calls green lifecycle-test) $(calls green scenarios) $(calls green teardown)"
check "  the kept scenario stack was removed with the project and directory the runner reported" "yes" \
  "$(grep -q "^teardown --purge project=ci-scn-[0-9a-f]* local=$TMP/stub-green/scn/local\$" "$TMP/stub-green/calls.log" && echo yes || echo no)"
check "  the scenario browser run got that stack's scenario file and Grafana" "yes" \
  "$(grep -q "^test-ui-scenario --no-install --scenario $TMP/stub-green/scn/dashboard-scenarios/scenarios.json --grafana-url http://127.0.0.1:43000\$" "$TMP/stub-green/calls.log" && echo yes || echo no)"
check "  the browser phases never download (every call after the setup says --no-install)" "0" \
  "$(grep -E '^(test-ui|screenshots|test-ui-selftest|test-ui-scenario) ' "$TMP/stub-green/calls.log" | grep -vc -- '--no-install')"
check "  the uploaded directory holds the summary, the logs and the run outputs, and no violation" "yes yes yes 0" \
  "$([ -f "$TMP/stub-green/artifacts/publish/summary.tsv" ] && echo yes || echo no) $([ -f "$TMP/stub-green/artifacts/publish/logs/lifecycle.log" ] && echo yes || echo no) $([ "$(find "$TMP/stub-green/artifacts/publish/ui" -name overview.png | wc -l | tr -d ' ')" -ge 1 ] && echo yes || echo no) $(jq '.violations | length' "$TMP/stub-green/artifacts/publish/MANIFEST.json")"
check "  the container-level logs of the runners are kept as component logs" "yes" "$([ -f "$TMP/stub-green/artifacts/publish/components/lifecycle-script-calls/01-call.log" ] || [ -f "$TMP/stub-green/artifacts/publish/components/scenarios-script-calls/01-call.log" ] && echo yes || echo no)"

run_suite failing STUB_FAIL=test-ui
check "an injected failure of the seeded browser run fails the suite (status 1)" "1 yes" "$SUITE_RC $(grep -q '^The suite FAILED: .*ui-seeded=fail(1)' "$TMP/failing.out" && echo yes || echo no)"
check "  its status is kept as the phase's own" "fail(1)" "$(phase_status failing ui-seeded)"
check "  every later phase still ran and passed (nothing is skipped by the failure)" "pass pass pass pass pass" \
  "$(phase_status failing ui-screenshots) $(phase_status failing lifecycle) $(phase_status failing scenarios) $(phase_status failing ui-scenario) $(phase_status failing scenarios-teardown)"
check "  the evidence of the failing run is still collected" "yes" "$([ -f "$TMP/stub-failing/artifacts/publish/logs/ui-seeded.log" ] && grep -q 'injected failure' "$TMP/stub-failing/artifacts/publish/logs/ui-seeded.log" && echo yes || echo no)"

run_suite nosetup STUB_FAIL=test-ui-setup
check "a failed browser setup fails the suite and skips what needs the browser, not the rest" "1 yes yes" \
  "$SUITE_RC $(skipped nosetup ui-seeded) $(skipped nosetup ui-scenario)"
check "  the lifecycle phase and the stack removal still ran" "pass pass" "$(phase_status nosetup lifecycle) $(phase_status nosetup scenarios-teardown)"

run_suite noscn STUB_FAIL=scenarios
check "a failed scenario run fails the suite, skips the browser run on it and still removes the kept stack" "1 yes 1 pass" \
  "$SUITE_RC $(skipped noscn ui-scenario) $(calls noscn teardown) $(phase_status noscn scenarios-teardown)"

cat > "$BIN/stubrt" << 'SCRIPT'
#!/usr/bin/env bash
printf 'stubrt %s\n' "$*" >> "$STUB_LOG"
case "$1 $2" in "ps -aq") echo c1; echo c2 ;; "network ls") echo n1 ;; "volume ls") echo v1 ;; esac
exit 0
SCRIPT
chmod 755 "$BIN/stubrt"
run_suite early STUB_FAIL=scenarios-early CONTAINER_RUNTIME=stubrt
check "a scenario run that dies before it says where its state is: the project is still removed by label, and the suite fails" "1 yes 0 fail(the kept stack was not located)" \
  "$SUITE_RC $(grep -q '^stubrt rm -f -v c1 c2$' "$TMP/stub-early/calls.log" && grep -q '^stubrt network rm -f n1$' "$TMP/stub-early/calls.log" && grep -q '^stubrt volume rm -f v1$' "$TMP/stub-early/calls.log" && echo yes || echo no) $(calls early teardown) $(phase_status early scenarios-teardown)"
check "  only this run's project is selected by label" "yes" "$(grep '^stubrt ps' "$TMP/stub-early/calls.log" | grep -q 'label=com.docker.compose.project=ci-scn-' && echo yes || echo no)"

run_suite npmfail STUB_FAIL=npm-typecheck
check "a failing static check of the browser tooling fails the suite" "1 fail(1)" "$SUITE_RC $(phase_status npmfail ui-tooling)"

run_suite notear STUB_FAIL=teardown
check "a failed removal of the scenario stack fails the suite and is tried once more when the suite ends" "1 fail(1) 2" "$SUITE_RC $(phase_status notear scenarios-teardown) $(calls notear teardown)"

run_suite first
run_suite second
check "a second invocation runs everything again: the same 18 phases and the same stand-in calls, nothing reused" "18 18 yes" \
  "$(wc -l < "$TMP/stub-first/artifacts/work/summary.tsv" | tr -d ' ') $(wc -l < "$TMP/stub-second/artifacts/work/summary.tsv" | tr -d ' ') $([ "$(wc -l < "$TMP/stub-first/calls.log")" = "$(wc -l < "$TMP/stub-second/calls.log")" ] && echo yes || echo no)"
check "  and each stand-in ran once in each invocation" "1 1" "$(calls first lifecycle-test) $(calls second lifecycle-test)"
mkdir -p "$TMP/stub-reuse/artifacts"
echo old > "$TMP/stub-reuse/artifacts/old-result"
: > "$TMP/stub-reuse/calls.log"
(cd "$EX" && env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-reuse/calls.log" STUB_ROOT="$TMP/stub-reuse" REGISTRY_IMAGE=x CI_ARTIFACT_DIR="$TMP/stub-reuse/artifacts" ./tests/ci-suite.sh) > "$TMP/reuse.out" 2>&1
check "an artifact directory that already holds something is refused before any phase runs" "1 0 mine" "$? $(wc -l < "$TMP/stub-reuse/calls.log" | tr -d ' ') $(cat "$TMP/stub-reuse/artifacts/old-result" | sed 's/old/mine/')"
(cd "$EX" && env PATH="$BIN:$PATH" STUB_LOG="$TMP/none.log" CI_ARTIFACT_DIR="$TMP/stub-x" ./tests/ci-suite.sh) > "$TMP/noimage.out" 2>&1
check "no image to test is refused" "1 yes" "$? $(grep -q 'REGISTRY_IMAGE' "$TMP/noimage.out" && echo yes || echo no)"
(cd "$EX" && env PATH="$BIN:$PATH" STUB_LOG="$TMP/none.log" REGISTRY_IMAGE=x CI_ARTIFACT_DIR="relative/dir" ./tests/ci-suite.sh) > "$TMP/relative.out" 2>&1
check "a relative artifact directory is refused" "1 yes" "$? $(grep -q 'absolute' "$TMP/relative.out" && echo yes || echo no)"

# a signal while the scenario run is going: the kept stack is removed and the evidence is collected before the suite ends
rm -rf "$TMP/stub-term"
mkdir -p "$TMP/stub-term"
: > "$TMP/stub-term/calls.log"
(cd "$EX" && python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/term.out" --timeout 90 --signal TERM --when-exists "$TMP/stub-term/signal" -- \
  env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-term/calls.log" STUB_ROOT="$TMP/stub-term" STUB_SIGNAL_FILE="$TMP/stub-term/signal" REGISTRY_IMAGE=x \
  CI_ARTIFACT_DIR="$TMP/stub-term/artifacts" ./tests/ci-suite.sh) > "$TMP/term.launch" 2>&1
check "TERM during the scenario run: the suite ends with status 143 and without a hang" "status=143 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/term.launch") $(grep -o 'hung=[a-z]*' "$TMP/term.launch")"
check "  the kept scenario stack was removed on the way out" "yes" "$(grep -q '^teardown --purge project=ci-scn-' "$TMP/stub-term/calls.log" && echo yes || echo no)"
check "  and the evidence was collected into the upload directory" "yes" "$([ -f "$TMP/stub-term/artifacts/publish/MANIFEST.json" ] && echo yes || echo no)"

printf '\n%s passed, %s failed\n' "$passed" "$failures"
if [ "$failures" -gt 0 ]; then
  printf '%s check(s) failed.\n' "$failures"
  exit 1
fi
echo "All checks passed."
