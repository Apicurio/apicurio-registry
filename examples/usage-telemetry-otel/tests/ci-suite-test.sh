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
# collect <collector arguments>: the collector with a private report beside its output directory; the names and reasons it holds are
# shown on stderr as VIOLATION lines for the checks below (the collector's own output carries only counts)
collect() {
  local previous="" out="" arg report rc
  for arg in "$@"; do
    [ "$previous" = --out ] && out=$arg
    previous=$arg
  done
  report=$out.private.json
  rm -f "$report"
  python3 -I "$EXAMPLE/tests/ci-artifacts.py" --private-report "$report" "$@"
  rc=$?
  [ ! -f "$report" ] || jq -r '.violations[] | "VIOLATION \(.path): \(.reason)"' "$report" >&2
  return "$rc"
}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
START=$(($(date +%s) - 5))
UI=$TMP/ui
mkdir -p "$UI/test-results" "$UI/playwright-report"
# png <path>: a file with the 8 byte PNG signature (written directly: a command substitution would strip its final newline byte)
png() { printf '\211PNG\r\n\032\n....' > "$1"; }

# mktrace <path> <safe|network|resource|cookie|bad-image|dup|extra>: a trace archive in the shape of the safe mode, or spoiled in one way
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
    if mode == "dup":
        import warnings
        warnings.simplefilter("ignore")
        z.writestr("trace.trace", b'{"type":"log","message":"Set-Cookie: grafana_session=second"}\n')
    if mode == "extra":
        z.writestr("SENTINEL-MEMBER-5d2c.bin", b"x")
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
# a Playwright results file: counts, and the free text of a test (title, error) that must never be published
RJSON='{"stats":{"startTime":"2026-11-24T10:00:00.000Z","duration":1234.6,"expected":5,"skipped":1,"unexpected":0,"flaky":0},"suites":[{"title":"SENTINEL-TITLE-77","errors":[{"message":"Cookie: sentinel-error-text"}]}]}'
echo "$RJSON" > "$RUN/results.json"
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
collect --out "$OUT" --start "$START" --ui-dir "$UI" --run-dir "$RUN" --logs "$LOGS" --components "$COMPONENTS" --summary "$TMP/summary.tsv" > "$TMP/good.out" 2>&1
check "a clean invocation is published without a violation (status 0)" 0 "$?"
check "  exactly the allowlisted files are published" \
  "components/project-one/registry-1.log
logs/unit.log
summary.tsv
ui/$STAMP-4242-tests/pw/case-one-usage/error-context.md
ui/$STAMP-4242-tests/pw/case-one-usage/test-failed-1.png
ui/$STAMP-4242-tests/results-summary.json
ui/$STAMP-4242-tests/screenshots/filtered.png
ui/$STAMP-4242-tests/screenshots/overview.png" \
  "$(jq -r '.published[].path' "$OUT/MANIFEST.json")"
check "  the raw run input is excluded, with its reason in the private report" "raw run input, never published" \
  "$(jq -r '.excluded[] | select(.path | endswith("input.sealed.json")) | .reason' "$OUT.private.json")"
check "  so are an image and a hidden file that are not on the list" "not on the allowlist|not on the allowlist" \
  "$(jq -r '[.excluded[] | select(.path | endswith("extra.png") or endswith(".last-run.json")) | .reason] | join("|")' "$OUT.private.json")"
check "  nothing but the manifest and those files is in the directory (file count)" "9" "$(find "$OUT" -type f | wc -l | tr -d ' ')"
check "  the Playwright report and its data tree stay private even though the run printed them (nothing of them is published)" "0 0" \
  "$(find "$OUT" -path '*report*' | wc -l | tr -d ' ') $(grep -rl '<html>report</html>' "$OUT" | wc -l | tr -d ' ')"
check "  the raw results.json and the trace archive stay private: the run's numbers are published as numbers only" "yes 0" \
  "$([ "$(jq -cS . "$OUT/ui/$STAMP-4242-tests/results-summary.json")" = '{"duration":1235,"expected":5,"flaky":0,"skipped":1,"unexpected":0}' ] && echo yes || echo no) $(grep -rl -e SENTINEL-TITLE-77 -e sentinel-error-text "$OUT" | wc -l | tr -d ' ')"
check "  the trace archive is not published (not on the allowlist, so never opened)" "0 yes" \
  "$(find "$OUT" -name 'trace.zip' | wc -l | tr -d ' ') $(jq -r '[.excluded[] | select(.path | endswith("trace.zip")) | .reason] | join("|")' "$OUT.private.json" | grep -q 'not on the allowlist' && echo yes || echo no)"
check "  the manifest records a digest and the size of every published file" "8 8" \
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
  echo "$RJSON" > "$RUN2/results.json"
  png "$RUN2/screenshots/overview.png"
  echo '<html></html>' > "$REPORT2/index.html"
  echo 'ok' > "$LOGS2/unit.log"
  "$4"
  collect --out "$out" --start "$START" --ui-dir "$UI" --run-dir "$RUN2" --logs "$LOGS2" > "$TMP/spoil-$name.out" 2> "$TMP/spoil-$name.err"
  rc=$?
  check "$2: status 1" 1 "$rc"
  check "  the violation says why" yes "$(grep -q "$3" "$TMP/spoil-$name.err" && echo yes || echo no)"
}
stale_file() { touch -t 200001010000 "$RUN2/results.json"; }
spoil stale "a stale file inside a fresh run" "stale" stale_file
check "  and it is not published" "0" "$(jq '[.published[] | select(.path | endswith("results-summary.json"))] | length' "$TMP/out-stale/MANIFEST.json")"
# Only numbers in range become part of the summary: a text, a boolean, a negative and an absurd value are left out, and a file with no usable
# count is a violation
NR=$UI/test-results/$STAMP-5800-tests
mkdir -p "$NR"
echo '{"stats":{"expected":"SENTINEL-NUMTEXT","unexpected":true,"flaky":-1,"skipped":2,"duration":1e15}}' > "$NR/results.json"
collect --out "$TMP/out-numeric" --start "$START" --ui-dir "$UI" --run-dir "$NR" > "$TMP/numeric.out" 2>&1
check "a results file whose stats hold a text, a boolean, a negative and an absurd number: only the valid count is published, and no text from it" "0 {\"skipped\":2} 0" \
  "$? $(jq -cS . "$TMP/out-numeric/ui/$STAMP-5800-tests/results-summary.json") $(grep -rl SENTINEL-NUMTEXT "$TMP/out-numeric" | wc -l | tr -d ' ')"
NR2=$UI/test-results/$STAMP-5801-tests
mkdir -p "$NR2"
echo '{"stats":{"expected":"only text"}}' > "$NR2/results.json"
collect --out "$TMP/out-numeric2" --start "$START" --ui-dir "$UI" --run-dir "$NR2" > /dev/null 2>&1
check "a results file with no usable count is a violation (status 1) and publishes no summary" "1 0" "$? $(find "$TMP/out-numeric2" -name results-summary.json | wc -l | tr -d ' ')"

# Trace archives are never published and never opened, whatever they hold: one of each kind, and a named pipe, in one run
TR=$UI/test-results/$STAMP-5900-tests
mkdir -p "$TR/screenshots"
echo "$RJSON" > "$TR/results.json"
for mode in safe network resource cookie bad-image dup extra; do mkdir -p "$TR/pw/c-$mode"; mktrace "$TR/pw/c-$mode/trace.zip" "$mode"; done
mkdir -p "$TR/pw/c-fifo" "$TR/pw/c-notzip"
mkfifo "$TR/pw/c-fifo/trace.zip"
echo 'not a zip' > "$TR/pw/c-notzip/trace.zip"
python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/traces.log" --timeout 30 -- \
  python3 -I "$EXAMPLE/tests/ci-artifacts.py" --out "$TMP/out-traces" --start "$START" --ui-dir "$UI" --run-dir "$TR" --private-report "$TMP/traces.private.json" > "$TMP/traces.launch" 2>&1
check "trace archives of every kind, and a named pipe called trace.zip: no hang, status 0, none published" "status=0 hung=no 0 9" \
  "$(grep -o 'status=[0-9-]*' "$TMP/traces.launch") $(grep -o 'hung=[a-z]*' "$TMP/traces.launch") $(find "$TMP/out-traces" -name trace.zip | wc -l | tr -d ' ') $(jq '[.excluded[] | select(.path | endswith("trace.zip"))] | length' "$TMP/traces.private.json")"
log_secret() { printf 'GET /x\nAuthorization: Bearer abcdefghijklmnopqrstuvwxyz0123\n' > "$LOGS2/unit.log"; }
spoil log-secret "a log with an Authorization header" "Authorization" log_secret
check "  its log is not published" "0" "$(find "$TMP/out-log-secret" -name 'unit.log' | wc -l | tr -d ' ')"
# A report that embeds a token is not a violation any more: it is never collected, so nothing of it can leave
html_jwt() { echo '<script>eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghij</script>' > "$REPORT2/index.html"; }
SPOIL_N=$((SPOIL_N + 1))
RUN2=$UI/test-results/$STAMP-$((5000 + SPOIL_N))-tests; REPORT2=$UI/playwright-report/$STAMP-$((5000 + SPOIL_N))-tests
mkdir -p "$RUN2/screenshots" "$REPORT2/data"; echo "$RJSON" > "$RUN2/results.json"; html_jwt
collect --out "$TMP/out-report-jwt" --start "$START" --ui-dir "$UI" --run-dir "$RUN2" > /dev/null 2>&1
check "a Playwright report that holds a web token is not collected: status 0 and nothing of it published" "0 0" \
  "$? $(grep -rl eyJhbGciOiJIUzI1NiJ9 "$TMP/out-report-jwt" | wc -l | tr -d ' ')"

# Configured Grafana credentials: an UNRELATED dummy password, in every form a tool might print it
S_PW='Dum my:Unrel@ted/9x2Qv7'
S_PW_B64=$(printf '%s' "$S_PW" | base64 | tr -d '\n')
S_PW_BASIC=$(printf 'admin:%s' "$S_PW" | base64 | tr -d '\n')
S_PW_URL=$(python3 -I -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$S_PW")
for form in "curl -u admin:$S_PW http://grafana" "the password is $S_PW" "Basic-looking blob $S_PW_B64" "blob $S_PW_BASIC" "login=admin&password=$S_PW_URL"; do
  pw_log() { printf 'ok line\n%s\n' "$form" > "$LOGS2/unit.log"; }
  GRAFANA_ADMIN_PASSWORD=$S_PW spoil "pw-$(printf '%s' "$form" | cksum | cut -d' ' -f1)" "a log that prints the configured Grafana password in one form" "configured credential" pw_log
done
check "  none of those logs, in no form, reached any output directory" "0" "$(grep -rl -e "$S_PW" -e "$S_PW_B64" -e "$S_PW_BASIC" -e "$S_PW_URL" "$TMP"/out-pw-* 2> /dev/null | wc -l | tr -d ' ')"
# the same password in an error context is withheld too
pw_context() { printf 'the page said %s\n' "$S_PW" > "$RUN2/pw/c-usage/error-context.md"; }
GRAFANA_ADMIN_PASSWORD=$S_PW spoil pw-context "an error context that holds the configured Grafana password" "configured credential" pw_context
check "  the context is not published, and the password is in nothing published" "0 0" \
  "$(find "$TMP/out-pw-context" -name error-context.md | wc -l | tr -d ' ') $(grep -rl -e "$S_PW" "$TMP/out-pw-context" | wc -l | tr -d ' ')"
# No exemption by value: a configured secret that is short, or that is the word admin, is searched for like any other (and withholds more);
# only an absent or empty variable is not scanned, and the demo's default password is public because nothing puts it in the environment
S_SHORT=x9Kq
pw_case() { # pw_case <name> <log text> <env assignments...>: one collection of one log with those variables set (others unset)
  local name=$1 text=$2 rc=0
  shift 2
  mkdir -p "$TMP/logs-$name"
  printf '%s\n' "$text" > "$TMP/logs-$name/unit.log"
  (unset GRAFANA_ADMIN_PASSWORD GF_SECURITY_ADMIN_PASSWORD CI_REDACT_ENV; for assignment in "$@"; do export "${assignment?}"; done
    collect --out "$TMP/out-$name" --start "$START" --ui-dir "$UI" --logs "$TMP/logs-$name" > "$TMP/$name.out" 2>&1) || rc=$?
  PW_RC=$rc
  PW_PUBLISHED=$([ -f "$TMP/out-$name/logs/unit.log" ] && echo yes || echo no)
}
# A configured credential in a published NAME (a container name, a test slot) withholds that file as well; the name is never published
S_PATHPW=Dummy-Path-Pw-5a1c
mkdir -p "$TMP/comp-pw/project-x" "$TMP/logs-pathpw"
echo ok > "$TMP/comp-pw/project-x/$S_PATHPW.log"; echo ok > "$TMP/comp-pw/project-x/fine.log"; echo ok > "$TMP/logs-pathpw/unit.log"
PATHRUN=$UI/test-results/$STAMP-5950-tests
mkdir -p "$PATHRUN/pw/$S_PATHPW-usage" "$PATHRUN/pw/other-usage"
png "$PATHRUN/pw/$S_PATHPW-usage/test-failed-1.png"; png "$PATHRUN/pw/other-usage/test-failed-1.png"
rc=0
(export GRAFANA_ADMIN_PASSWORD=$S_PATHPW; python3 -I "$EXAMPLE/tests/ci-artifacts.py" --out "$TMP/out-pathpw" --start "$START" --ui-dir "$UI" --run-dir "$PATHRUN" --logs "$TMP/logs-pathpw" --components "$TMP/comp-pw" --private-report "$TMP/pathpw.private.json" > "$TMP/pathpw.out" 2>&1) || rc=$?
check "a configured credential in a published name (a container log, a test directory): status 1 and those two files are not published" "1 0" \
  "$rc $(find "$TMP/out-pathpw" -name "*$S_PATHPW*" | wc -l | tr -d ' ')"
check "  the other files with ordinary names are still published, and the credential is in no output or manifest" "yes yes 0" \
  "$([ -f "$TMP/out-pathpw/components/project-x/fine.log" ] && echo yes || echo no) $([ -f "$TMP/out-pathpw/ui/$STAMP-5950-tests/pw/other-usage/test-failed-1.png" ] && echo yes || echo no) $(grep -rl "$S_PATHPW" "$TMP/out-pathpw" "$TMP/pathpw.out" | wc -l | tr -d ' ')"
pw_case pw-implicit 'login as admin with the password admin'
check "the demo's default password, not supplied through the environment: status 0, the log is published" "0 yes" "$PW_RC $PW_PUBLISHED"
pw_case pw-empty 'login as admin with the password admin' GRAFANA_ADMIN_PASSWORD=
check "  a variable that is present but empty is not scanned either" "0 yes" "$PW_RC $PW_PUBLISHED"
pw_case pw-explicit-admin 'login as admin with the password admin' GRAFANA_ADMIN_PASSWORD=admin
check "a configured Grafana password that equals admin is a configured secret: the log is withheld (status 1)" "1 no" "$PW_RC $PW_PUBLISHED"
pw_case pw-short "curl -u admin:$S_SHORT http://grafana" GF_SECURITY_ADMIN_PASSWORD=$S_SHORT
check "a short configured secret (4 bytes) is searched for: the log is withheld (status 1)" "1 no" "$PW_RC $PW_PUBLISHED"
check "  the short value is in neither the collector's output nor the published files" "0" \
  "$(grep -rl -e "$S_SHORT" "$TMP/pw-short.out" "$TMP/out-pw-short" 2> /dev/null | wc -l | tr -d ' ')"
pw_case pw-redact-admin 'the token is admin here' CI_REDACT_ENV=MY_TOKEN MY_TOKEN=admin
check "CI_REDACT_ENV naming a variable whose value is admin is not exempt: the log is withheld (status 1)" "1 no" "$PW_RC $PW_PUBLISHED"
check "  the word is in neither the collector's output nor the published files" "0" \
  "$(grep -rl -e admin "$TMP/pw-redact-admin.out" "$TMP/out-pw-redact-admin" 2> /dev/null | wc -l | tr -d ' ')"
pw_case pw-redact-absent 'the token is admin here' CI_REDACT_ENV=NOT_SET_ANYWHERE
check "CI_REDACT_ENV naming a variable that is absent scans nothing for it (status 0, published)" "0 yes" "$PW_RC $PW_PUBLISHED"
png_bad() { echo 'not a png' > "$RUN2/screenshots/overview.png"; }
spoil png-bad "a screenshot that is not a PNG" "not a PNG" png_bad
json_bad() { echo '{not json' > "$RUN2/results.json"; }
spoil json-bad "a results file that is not JSON" "not a valid UTF-8 JSON object" json_bad
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
echo "$RJSON" > "$BIGRUN/results.json"
head -c 3000000 /dev/zero | tr '\0' 'y' > "$TMP/logs-big/unit.log"
collect --out "$TMP/out-big" --start "$START" --ui-dir "$UI" --run-dir "$BIGRUN" --logs "$TMP/logs-big" > /dev/null 2>&1
check "a log of 3 MB is published truncated (status 0)" "0 yes" "$? $(head -c 40 "$TMP/out-big/logs/unit.log" | grep -q '^\[truncated' && echo yes || echo no)"
check "  within 2 MiB plus the note" "yes" "$([ "$(wc -c < "$TMP/out-big/logs/unit.log")" -le 2097400 ] && echo yes || echo no)"

# the suite's own summary does not pass through a run directory, so its link is judged on its own
printf 'unit\tpass\t3\n' > "$TMP/summary-target.tsv"
ln -s "$TMP/summary-target.tsv" "$TMP/summary-link.tsv"
collect --out "$TMP/out-summary-link" --start "$START" --ui-dir "$UI" --summary "$TMP/summary-link.tsv" > /dev/null 2> "$TMP/summary-link.err"
check "a summary file that is a link is refused (status 1) and not published" "1 yes 0" \
  "$? $(grep -q 'not a regular file' "$TMP/summary-link.err" && echo yes || echo no) $(jq '[.published[] | select(.path == "summary.tsv")] | length' "$TMP/out-summary-link/MANIFEST.json")"

part "the artifact policy: directories that are not this invocation's"
OLDNAME=20000101T000000Z-1-tests
mkdir -p "$UI/test-results/$OLDNAME"
echo '{}' > "$UI/test-results/$OLDNAME/results.json"
collect --out "$TMP/out-oldrun" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/$OLDNAME" > /dev/null 2> "$TMP/oldrun.err"
check "a run that started before this invocation is refused as stale (status 1)" "1 yes" "$? $(grep -q 'stale' "$TMP/oldrun.err" && echo yes || echo no)"
mkdir -p "$TMP/elsewhere/$STAMP-1-tests"
collect --out "$TMP/out-elsewhere" --start "$START" --ui-dir "$UI" --run-dir "$TMP/elsewhere/$STAMP-1-tests" > /dev/null 2> "$TMP/elsewhere.err"
check "a run directory outside test-results is refused (status 1)" "1 yes" "$? $(grep -q 'not a directory directly below' "$TMP/elsewhere.err" && echo yes || echo no)"
ln -s "$RUN" "$UI/test-results/$STAMP-9999-tests"
collect --out "$TMP/out-linkrun" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/$STAMP-9999-tests" > /dev/null 2> "$TMP/linkrun.err"
check "a run directory that is a link is refused (status 1)" "1 yes" "$? $(grep -q 'through a link' "$TMP/linkrun.err" && echo yes || echo no)"
mkdir -p "$UI/test-results/not-a-run-name"
collect --out "$TMP/out-badname" --start "$START" --ui-dir "$UI" --run-dir "$UI/test-results/not-a-run-name" > /dev/null 2>&1
check "a directory that is not named like a run is refused (status 1)" 1 "$?"
mkdir -p "$TMP/out-used"
echo mine > "$TMP/out-used/file"
collect --out "$TMP/out-used" --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "an output directory that is not empty is refused (status 2) and left alone" "2 mine" "$? $(cat "$TMP/out-used/file")"
collect --out relative/out --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "a relative output path is refused (status 2)" 2 "$?"
collect --out "$TMP/out-none" --start "$START" --ui-dir "$UI" > /dev/null 2>&1
check "an invocation with nothing to publish is not a violation (status 0)" "0 0" "$? $(jq '.published | length' "$TMP/out-none/MANIFEST.json")"

# archives and files built to defeat the checks: a repeated member name, a member name that is a sentinel, named pipes
extra_named() { : > "$RUN2/screenshots/SENTINEL-EXCLUDED-8e21.png"; }
RUN2=$UI/test-results/$STAMP-6001-tests; REPORT2=$UI/playwright-report/$STAMP-6001-tests; LOGS2=$TMP/logs-excl
mkdir -p "$RUN2/screenshots" "$LOGS2"
echo "$RJSON" > "$RUN2/results.json"; png "$RUN2/screenshots/overview.png"; extra_named; echo ok > "$LOGS2/unit.log"
collect --out "$TMP/out-excl" --start "$START" --ui-dir "$UI" --run-dir "$RUN2" --logs "$LOGS2" > "$TMP/excl.out" 2> "$TMP/excl.err"
check "an excluded file with a sentinel name: status 0, and the name is not in the console output, the manifest or the upload" "0 0 0" \
  "$? $(grep -c SENTINEL-EXCLUDED "$TMP/excl.out" "$TMP/excl.err" | awk -F: '{ s += $2 } END { print s }') $(grep -rl SENTINEL-EXCLUDED "$TMP/out-excl" | wc -l | tr -d ' ')"
check "  the manifest keeps only a count of what was excluded" "1" "$(jq '.excluded_count' "$TMP/out-excl/MANIFEST.json")"
check "  and the private report has the name for the person on the runner" "yes" "$(grep -q SENTINEL-EXCLUDED "$TMP/out-excl.private.json" && echo yes || echo no)"
# a named pipe must be refused without being opened (an open would wait for a writer for ever). Each variant has its own run directory
# and its own logs, so one pipe cannot be mistaken for the other; each must be the ONLY violation, with the exact reason, at its own path
fifo_run() { rm -f "$RUN2/screenshots/overview.png"; mkfifo "$RUN2/screenshots/overview.png"; }
fifo_log() { mkfifo "$LOGS2/pipe.log"; }
FIFO_N=0
for variant in fifo_run fifo_log; do
  FIFO_N=$((FIFO_N + 1))
  RUN2=$UI/test-results/$STAMP-$((7000 + FIFO_N))-tests; REPORT2=$UI/playwright-report/$STAMP-$((7000 + FIFO_N))-tests; LOGS2=$TMP/logs-$variant
  mkdir -p "$RUN2/screenshots" "$RUN2/pw/c-usage" "$LOGS2"
  echo "$RJSON" > "$RUN2/results.json"; png "$RUN2/screenshots/overview.png"; echo ok > "$LOGS2/unit.log"
  "$variant"
  python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/$variant.log" --timeout 30 -- \
    python3 -I "$EXAMPLE/tests/ci-artifacts.py" --out "$TMP/out-$variant" --start "$START" --ui-dir "$UI" --run-dir "$RUN2" --logs "$LOGS2" --private-report "$TMP/$variant.private.json" > "$TMP/$variant.launch" 2>&1
  check "a named pipe ($variant) is a violation, not a hang: status 1 and no timeout" "status=1 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/$variant.launch") $(grep -o 'hung=[a-z]*' "$TMP/$variant.launch")"
  case $variant in
    fifo_run) want_path="run $STAMP-$((7000 + FIFO_N))-tests/screenshots/overview.png" ;;
    *) want_path="logs/pipe.log" ;;
  esac
  check "  it is the only violation, at its own path, with the exact reason (private report)" "1|$want_path|not a regular file (a symbolic link or another kind of entry)" \
    "$(jq -r '[(.violations | length), .violations[0].path, .violations[0].reason] | join("|")' "$TMP/$variant.private.json")"
done

# an invalid date in a run name is a controlled refusal, not a crash
BADDATE=$UI/test-results/20261399T250000Z-1-tests
mkdir -p "$BADDATE"
python3 -I "$EXAMPLE/tests/ci-artifacts.py" --out "$TMP/out-baddate" --start "$START" --ui-dir "$UI" --run-dir "$BADDATE" --private-report "$TMP/baddate.private.json" > "$TMP/baddate.out" 2>&1
check "a run name with a date that does not exist: status 1, no traceback, a manifest and the exact reason" "1 0 yes 1|its name carries a date that does not exist" \
  "$? $(grep -c Traceback "$TMP/baddate.out") $([ -f "$TMP/out-baddate/MANIFEST.json" ] && echo yes || echo no) $(jq -r '[(.violations | length), .violations[0].reason] | join("|")' "$TMP/baddate.private.json")"

# a header written as a JSON or quoted key is a credential like the header line
S_JSON=SENTINEL-JSONAUTH-61d0
json_header() { printf '{"level":"info","authorization": "%s"}\n' "$S_JSON" > "$LOGS2/unit.log"; printf "{'Set-Cookie': '%s'}\n" "$S_JSON" > "$LOGS2/second.log"; }
spoil json-header "a log with the credential header as a JSON key" "Authorization, Cookie or Set-Cookie header" json_header
check "  the value is in neither the console output, the manifest nor the upload" "0" "$(grep -rl "$S_JSON" "$TMP/out-json-header" "$TMP/spoil-json-header.out" | wc -l | tr -d ' ')"
check "  both logs were withheld" "0" "$(find "$TMP/out-json-header/logs" -name '*.log' 2> /dev/null | wc -l | tr -d ' ')"

# a collector that is interrupted part way leaves nothing in the output directory (the collection is staged and renamed into place)
python3 -I - "$EXAMPLE/tests/ci-artifacts.py" "$TMP/out-atomic" "$START" "$UI" "$RUN" "$LOGS" << 'PY' > /dev/null 2>&1
import importlib.util, os, sys
path, out, start, ui, run, logs = sys.argv[1:7]
spec = importlib.util.spec_from_file_location("ci_artifacts", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
calls = [0]
original = module.Collector.publish
def publish(self, *args, **kwargs):
    calls[0] += 1
    if calls[0] == 3:
        os._exit(143)
    return original(self, *args, **kwargs)
module.Collector.publish = publish
sys.argv = ["ci-artifacts.py", "--out", out, "--start", start, "--ui-dir", ui, "--run-dir", run, "--logs", logs]
sys.exit(module.main())
PY
check "a collector ended after its third file: the output directory does not exist" "143 no" "$? $([ -e "$TMP/out-atomic" ] && echo yes || echo no)"
check "  only a hidden staging directory beside it was written, and it has no manifest" "1 no" \
  "$(find "$TMP" -maxdepth 1 -type d -name '.out-atomic.staging.*' | wc -l | tr -d ' ') $([ -f "$(find "$TMP" -maxdepth 1 -type d -name '.out-atomic.staging.*' | head -1)/MANIFEST.json" ] && echo yes || echo no)"


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

# The status table gate: the suite passes only when every required phase has exactly one row and it passed
cat > "$TMP/table-test.sh" << SCRIPT
CI_SUITE_SOURCE_ONLY=1 . "$EXAMPLE/tests/ci-suite.sh"
full() { for p in \$REQUIRED_PHASES; do printf '%s\\tpass\\t1\\n' "\$p"; done; }
SUMMARY=$TMP/table.tsv
show() { printf '%s=[%s]\\n' "\$1" "\$(table_problems | sed 's/ *\$//')"; }
{
  full > "\$SUMMARY"; show complete
  full | grep -v '^lifecycle' > "\$SUMMARY"; show missing
  { full; printf 'unit\\tpass\\t1\\n'; } > "\$SUMMARY"; show duplicate
  { full; printf 'mystery\\tpass\\t1\\n'; } > "\$SUMMARY"; show unexpected
  full | sed 's/^ui-seeded.pass/ui-seeded\\tfail(1)/' > "\$SUMMARY"; show failed
  full | sed 's/^dashboard-test\\tpass/dashboard-test\\tskipped(x)/' > "\$SUMMARY"; show skipped
  : > "\$SUMMARY"; show empty
  full > "\$SUMMARY"; mkdir -p "$TMP/table-dir"; SUMMARY=$TMP/table-dir; record_row extra pass 1 2> /dev/null; SUMMARY=$TMP/table.tsv; show unwritable
  ROWS_FAILED=false; SUMMARY=$TMP/does-not-exist.tsv; show unreadable
} > "$TMP/table.out"
SCRIPT
bash "$TMP/table-test.sh" > /dev/null 2>&1
check "the table gate: a complete table has no problem" "complete=[]" "$(sed -n 1p "$TMP/table.out")"
check "  a missing row, a duplicate row and a row nobody expects are each a problem" "missing=[lifecycle=missing] duplicate=[unit=duplicate] unexpected=[unexpected rows: mystery]" \
  "$(sed -n 2,4p "$TMP/table.out" | tr '\n' ' ' | sed 's/ $//')"
check "  a failed phase, a skipped phase and an empty table are problems" "failed=[ui-seeded=fail(1)] skipped=[dashboard-test=skipped(x)]" "$(sed -n 5,6p "$TMP/table.out" | tr '\n' ' ' | sed 's/ $//')"
check "  an empty table lists every phase as missing (it never reads as nothing failed)" "yes" "$(sed -n 7p "$TMP/table.out" | grep -q 'unit=missing.*artifacts=missing' && echo yes || echo no)"
check "  a row that could not be written is a problem, with exactly its own message" "unwritable=[a status row could not be written]" "$(sed -n 8p "$TMP/table.out")"
check "  a table that could not be read is a problem, with its own message (gawk also lists every phase as missing, awk does not)" "yes" "$(sed -n 9p "$TMP/table.out" | grep -q 'the status table could not be read' && echo yes || echo no)"

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
cp "$EXAMPLE/tests/ci-suite.sh" "$EX/tests/"
cp "$EXAMPLE/tests/ci-artifacts.py" "$EX/tests/ci-artifacts-real.py"
# The collector the suite calls is a stand-in that hands over to the real one, except in the runs that make the collection hang
cat > "$EX/tests/ci-artifacts.py" << 'SCRIPT'
import os, signal, sys, time
mode = os.environ.get("STUB_COLLECT", "")
counter = os.environ.get("STUB_COLLECT_COUNT", "")
calls = 0
if counter:
    calls = int(open(counter).read()) if os.path.exists(counter) else 0
    open(counter, "w").write(str(calls + 1))
out = sys.argv[sys.argv.index("--out") + 1]
if mode and calls == 0:
    # a collector that died part way and left a half filled output directory behind (the real one cannot), then hangs
    os.makedirs(os.path.join(out, "partial-marker"))
    open(os.environ["STUB_COLLECT_SIGNAL"], "w").close()
    time.sleep(60)
if mode == "two-signal" and calls == 1:
    os.kill(os.getppid(), signal.SIGTERM)
    time.sleep(60)
os.execv(sys.executable, [sys.executable, "-I", os.path.join(os.path.dirname(os.path.abspath(__file__)), "ci-artifacts-real.py")] + sys.argv[1:])
SCRIPT
cat > "$BIN/npm" << 'SCRIPT'
#!/usr/bin/env bash
printf 'npm %s\n' "$*" >> "$STUB_LOG"
[ "$4" != fixtures ] || printf 'fixtures-env PLAYWRIGHT_BROWSERS_PATH=%s\n' "${PLAYWRIGHT_BROWSERS_PATH:-unset}" >> "$STUB_LOG"
case " ${STUB_FAIL:-} " in *" npm-$4 "*) echo "injected failure"; exit 1 ;; esac
SCRIPT
chmod 755 "$BIN/npm"
# a container runtime that touches nothing: it logs its calls and answers the queries the suite makes
cat > "$BIN/stubrt" << 'SCRIPT'
#!/usr/bin/env bash
printf 'stubrt %s\n' "$*" >> "$STUB_LOG"
case "$1" in
  ps) case " $* " in *" -aq "*) printf 'c1\nc2\n' ;; *" --format "*) echo c-one ;; esac ;;
  network | volume) [ "$2" != ls ] || { [ "$1" = network ] && echo n1 || echo v1; } ;;
  logs) echo "container log of $4" ;;
esac
exit 0
SCRIPT
chmod 755 "$BIN/stubrt"
simple_stub() { # simple_stub <path> : a command that logs its call, fails when STUB_FAIL names it, otherwise succeeds
  local label
  label=$(basename "$1" .sh)
  mkdir -p "$(dirname "$1")"
  cat > "$1" << SCRIPT
#!/usr/bin/env bash
printf '%s %s\n' "$label" "\$*" >> "\$STUB_LOG"
case " \${STUB_FAIL:-} " in *" $label "*) echo "injected failure of $label" >&2; exit 1 ;; esac
echo "stand-in $label ok"
[ -z "\${STUB_ECHO:-}" ] || echo "\$STUB_ECHO"
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
  echo '{"stats":{"duration":10,"expected":3,"skipped":0,"unexpected":0,"flaky":0}}' > "$run/results.json"
  printf 'run directory: %s\n' "$run"
  report="$PWD/tests/ui/playwright-report/$(date -u +%Y%m%dT%H%M%SZ)-$$-tests"
  mkdir -p "$report/data"
  echo '<html>SENTINEL-REPORT-3d8c</html>' > "$report/index.html"
  printf '\211PNG\r\n\032\n....' > "$report/data/0123456789abcdef0123456789abcdef01234567.png"
  printf 'report directory: %s\n' "$report"
  [ -z "${STUB_EXTRA:-}" ] || : > "$run/$STUB_EXTRA"
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
# a runner that owns its project but ended before it wrote the scenario file (lifecycle-test.sh writes it late)
[ -z "${STUB_NO_SCENARIO_FILE:-}" ] || rm -f "$root/dashboard-scenarios/scenarios.json"
echo "call log" > "$root/logs/01-call.log"
case " ${STUB_FAIL:-} " in *" scenarios-early "*) echo "injected early failure of scenarios" >&2; exit 1 ;; esac
# a runner that is ended before it ever says what it kept, and has printed something that must not be repeated
[ -z "${STUB_NO_HANDOFF:-}" ] || { echo "RUNNER-OUTPUT-SENTINEL-31bd"; : > "$STUB_SIGNAL_FILE"; sleep 30; }
printf -- '--keep: nothing was cleaned up. Project %s now has 4 container(s) and 2 volume(s). Stop it with: COMPOSE_PROJECT_NAME=%s LOCAL_DIR=%s/local ./teardown.sh --purge\n' "$project" "$project" "$root"
printf 'Logs of every script call: %s/logs\n' "$root"
[ -z "${STUB_SIGNAL_FILE:-}" ] || { : > "$STUB_SIGNAL_FILE"; sleep 30; }
case " ${STUB_FAIL:-} " in *" scenarios "*) echo "injected failure of scenarios" >&2; exit 1 ;; esac
SCRIPT
cat > "$EX/teardown.sh" << 'SCRIPT'
#!/usr/bin/env bash
printf 'teardown %s project=%s local=%s publish-present=%s\n' "$*" "${COMPOSE_PROJECT_NAME:-}" "${LOCAL_DIR:-}" "$([ -f "${CI_ARTIFACT_DIR:-/nonexistent}/publish/MANIFEST.json" ] && echo yes || echo no)" >> "$STUB_LOG"
[ -z "${STUB_TEARDOWN_HANG:-}" ] || sleep 300
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
  (cd "$EX" && env -u PLAYWRIGHT_BROWSERS_PATH PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-$name/calls.log" STUB_ROOT="$TMP/stub-$name" REGISTRY_IMAGE=apicurio/apicurio-registry:test CONTAINER_RUNTIME=stubrt \
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
  "$(grep -q "^teardown --purge project=ci-scn-[0-9a-f]* local=$TMP/stub-green/scn/local publish-present=no\$" "$TMP/stub-green/calls.log" && echo yes || echo no)"
check "  the scenario browser run got that stack's scenario file and Grafana" "yes" \
  "$(grep -q "^test-ui-scenario --no-install --scenario $TMP/stub-green/scn/dashboard-scenarios/scenarios.json --grafana-url http://127.0.0.1:43000\$" "$TMP/stub-green/calls.log" && echo yes || echo no)"
check "  the browser phases never download (every call after the setup says --no-install)" "0" \
  "$(grep -E '^(test-ui|screenshots|test-ui-selftest|test-ui-scenario) ' "$TMP/stub-green/calls.log" | grep -vc -- '--no-install')"
check "  the uploaded directory holds the summary, the logs and the run outputs, and no violation" "yes yes yes 0" \
  "$([ -f "$TMP/stub-green/artifacts/publish/summary.tsv" ] && echo yes || echo no) $([ -f "$TMP/stub-green/artifacts/publish/logs/lifecycle.log" ] && echo yes || echo no) $([ "$(find "$TMP/stub-green/artifacts/publish/ui" -name overview.png | wc -l | tr -d ' ')" -ge 1 ] && echo yes || echo no) $(jq '.violation_count' "$TMP/stub-green/artifacts/publish/MANIFEST.json")"
check "  the per-call logs a runner printed a path for are not copied (no component logs are taken from paths in a runner's output)" "0" "$(find "$TMP/stub-green/artifacts/publish/components" -name '*script-calls*' -o -name '01-call.log' | wc -l | tr -d ' ')"

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

run_suite early STUB_FAIL=scenarios-early
# Refused project, negative control: the runner refused the project (something already uses the name, which the stand-in runtime reports
# as containers of that project) and never said it kept anything. The suite must not delete what it did not claim.
check "a scenario runner that refused the project: the suite fails, and nothing was deleted by label or by name" "1 0 0 fail(the kept stack was not located)" \
  "$SUITE_RC $(grep -c '^stubrt \(rm\|network rm\|volume rm\) ' "$TMP/stub-early/calls.log") $(calls early teardown) $(phase_status early scenarios-teardown)"
check "  it says so and names the project for a person" "1" "$(grep -c '^==> scenarios-teardown: cleanup unavailable, the scenario runner handed over no verified kept project (ci-scn-[0-9a-f]*); nothing was removed' "$TMP/early.out" )"
check "  the runtime was only asked to list (for the container logs), never to remove" "0" "$(grep '^stubrt' "$TMP/stub-early/calls.log" | grep -c ' rm ')"

# An OWNED stack without scenario JSON: the runner printed its keep line for this project but never wrote the scenario file. The stack is
# still torn down (the keep line is the proof of ownership), and the browser phase does not run on data that is not there.
run_suite noscenario STUB_NO_SCENARIO_FILE=1
check "a kept stack the runner owns but without scenario JSON: it is torn down once, for this project and the directory the runner reported" "1 yes" \
  "$(calls noscenario teardown) $(grep -q "^teardown --purge project=ci-scn-[0-9a-f]* local=$TMP/stub-noscenario/scn/local " "$TMP/stub-noscenario/calls.log" && echo yes || echo no)"
check "  the teardown phase passed, and nothing was pending afterwards (no second teardown at exit, no unavailable sentence)" "pass 0" \
  "$(phase_status noscenario scenarios-teardown) $(grep -c 'cleanup unavailable' "$TMP/noscenario.out")"
check "  the browser phase did not run (no scenario browser call) and is recorded as skipped, and the suite fails" "0 yes 1" \
  "$(calls noscenario test-ui-scenario) $(skipped noscenario ui-scenario) $SUITE_RC"
check "  the scenario runner's own phase still passed and the stack's container logs were still collected" "pass yes" \
  "$(phase_status noscenario scenarios) $([ -d "$TMP/stub-noscenario/artifacts/publish/components" ] && echo yes || echo no)"

run_suite npmfail STUB_FAIL=npm-typecheck
check "a failing static check of the browser tooling fails the suite" "1 fail(1)" "$SUITE_RC $(phase_status npmfail ui-tooling)"

# the rate panel DOM fixture runs inside the required ui-tooling phase (no phase of its own), after the other tooling checks, in the
# Chromium directory ./test-ui.sh uses, and its status is the phase's
check "the rate panel fixture runs once in the required ui-tooling phase, after unit, and no phase was added" "1 yes 18" \
  "$(grep -c '^npm --prefix tests/ui run fixtures$' "$TMP/stub-green/calls.log" | tr -d ' ') $([ "$(grep -n '^npm --prefix tests/ui run unit$' "$TMP/stub-green/calls.log" | cut -d: -f1)" -lt "$(grep -n '^npm --prefix tests/ui run fixtures$' "$TMP/stub-green/calls.log" | cut -d: -f1)" ] && echo yes || echo no) $(wc -l < "$TMP/stub-green/artifacts/work/summary.tsv" | tr -d ' ')"
check "  it gets the verified Chromium directory of the checkout, and nothing is installed for it" "yes 0" \
  "$(grep -q "^fixtures-env PLAYWRIGHT_BROWSERS_PATH=$EX/tests/ui/.browsers\$" "$TMP/stub-green/calls.log" && echo yes || echo no) $(grep -c 'npm.* \(install\|ci\)' "$TMP/stub-green/calls.log" | tr -d ' ')"
run_suite fixturefail STUB_FAIL=npm-fixtures
check "a nonzero result of the rate panel fixture fails the ui-tooling phase and the suite, and the later phases still ran" "1 fail(1) pass" "$SUITE_RC $(phase_status fixturefail ui-tooling) $(phase_status fixturefail ui-selftest)"
check "  the fixture was not run when an earlier tooling check failed (the chain stops at the first failure)" "0" "$(grep -c '^npm --prefix tests/ui run fixtures$' "$TMP/stub-npmfail/calls.log" | tr -d ' ')"
run_suite fixturepath PLAYWRIGHT_BROWSERS_PATH=/named/browsers
check "  a browser directory the caller names is the one the fixture gets" "yes" "$(grep -q '^fixtures-env PLAYWRIGHT_BROWSERS_PATH=/named/browsers$' "$TMP/stub-fixturepath/calls.log" && echo yes || echo no)"

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
# the variable is removed explicitly: a REGISTRY_IMAGE exported to this test (as it is when someone runs it for a real image) must not satisfy it
(cd "$EX" && env -u REGISTRY_IMAGE PATH="$BIN:$PATH" STUB_LOG="$TMP/none.log" CI_ARTIFACT_DIR="$TMP/stub-x" ./tests/ci-suite.sh) > "$TMP/noimage.out" 2>&1
check "no image to test is refused, with REGISTRY_IMAGE removed from the environment" "1 yes" "$? $(grep -q 'REGISTRY_IMAGE' "$TMP/noimage.out" && echo yes || echo no)"
(cd "$EX" && env REGISTRY_IMAGE= PATH="$BIN:$PATH" STUB_LOG="$TMP/none.log" CI_ARTIFACT_DIR="$TMP/stub-y" ./tests/ci-suite.sh) > "$TMP/emptyimage.out" 2>&1
check "  an empty REGISTRY_IMAGE is refused too" "1 yes" "$? $(grep -q 'REGISTRY_IMAGE' "$TMP/emptyimage.out" && echo yes || echo no)"
(cd "$EX" && env PATH="$BIN:$PATH" STUB_LOG="$TMP/none.log" REGISTRY_IMAGE=x CI_ARTIFACT_DIR="relative/dir" ./tests/ci-suite.sh) > "$TMP/relative.out" 2>&1
check "a relative artifact directory is refused" "1 yes" "$? $(grep -q 'absolute' "$TMP/relative.out" && echo yes || echo no)"

# Sentinels: values that must never reach the job console or the upload directory. The image is exported (as in CI) with a credential in
# its address; every phase prints a Cookie header; a run directory holds a file that is not on the allowlist, named with a sentinel.
S_IMAGE=SENTINEL-IMAGE-CRED-3f9a
S_COOKIE=SENTINEL-COOKIE-9c1e
S_FILE=SENTINEL-EXCLUDED-4b7d
run_suite sentinel "REGISTRY_IMAGE=user:$S_IMAGE@registry.invalid/apicurio/apicurio-registry:t" "STUB_ECHO=Cookie: $S_COOKIE" "STUB_EXTRA=$S_FILE.txt"
check "a run whose phases print a cookie header: the suite fails (status 1)" 1 "$SUITE_RC"
check "  no sentinel reaches the job console" "0" "$(grep -c "$S_IMAGE\|$S_COOKIE\|$S_FILE" "$TMP/sentinel.out")"
check "  no sentinel is in anything that is uploaded" "0" "$(grep -rl "$S_IMAGE\|$S_COOKIE\|$S_FILE" "$TMP/stub-sentinel/artifacts/publish" | wc -l | tr -d ' ')"
check "  the log with the header was withheld, and the upload directory still has its manifest" "no yes" \
  "$([ -f "$TMP/stub-sentinel/artifacts/publish/logs/unit.log" ] && echo yes || echo no) $([ -f "$TMP/stub-sentinel/artifacts/publish/MANIFEST.json" ] && echo yes || echo no)"
check "  the names and reasons are kept privately on the runner, outside the upload directory" "yes yes" \
  "$(grep -q "$S_FILE" "$TMP/stub-sentinel/artifacts/work/artifact-report.json" && echo yes || echo no) $(grep -q 'Cookie' "$TMP/stub-sentinel/artifacts/work/artifact-report.json" && echo yes || echo no)"
check "  the published summary and the suite agree that the artifacts phase failed" "yes yes" \
  "$(grep -q '^artifacts	fail' "$TMP/stub-sentinel/artifacts/publish/summary.tsv" && echo yes || echo no) $(phase_status sentinel artifacts | grep -q '^fail' && echo yes || echo no)"
check "  and in the green run the published summary has exactly one artifacts row that passed" "1 pass" \
  "$(grep -c '^artifacts	' "$TMP/stub-green/artifacts/publish/summary.tsv") $(awk -F'\t' '$1 == "artifacts" { print $2 }' "$TMP/stub-green/artifacts/publish/summary.tsv")"
check "  the Playwright report the browser runs printed is not collected: no report path, and its content is nowhere in the upload" "0 0" \
  "$(find "$TMP/stub-green/artifacts/publish" -path '*report*' | wc -l | tr -d ' ') $(grep -rl SENTINEL-REPORT-3d8c "$TMP/stub-green/artifacts/publish" | wc -l | tr -d ' ')"
check "  the example stack's container logs went through the collector too" "yes" "$([ -f "$TMP/stub-green/artifacts/publish/components/usage-telemetry-otel/c-one.log" ] && echo yes || echo no)"
check "  the console never echoes a phase log (the phase's own stand-in line is not in it)" "0" "$(grep -c 'stand-in .* ok' "$TMP/green.out")"

# Configured Grafana credentials through the whole suite: the stand-in phases print the password the way curl -v or a failing test would
run_suite creds "GRAFANA_ADMIN_PASSWORD=$S_PW" "STUB_ECHO=curl -u admin:$S_PW http://grafana:3000 and $S_PW_BASIC"
check "a configured Grafana password printed by every phase: the suite fails (the logs are withheld, status 1)" 1 "$SUITE_RC"
check "  the password, in no form, is in the console output (what the Actions log would show)" "0" "$(grep -c -e "$S_PW" -e "$S_PW_B64" -e "$S_PW_BASIC" -e "$S_PW_URL" "$TMP/creds.out")"
check "  nor in anything that is uploaded" "0" "$(grep -rl -e "$S_PW" -e "$S_PW_B64" -e "$S_PW_BASIC" -e "$S_PW_URL" "$TMP/stub-creds/artifacts/publish" | wc -l | tr -d ' ')"
check "  the suite's own code never prints the environment or traces its commands (the console is not scrubbed)" "0" \
  "$(grep -c 'printenv\|set -x\|^ *env *$\|declare -p\|set | ' "$EXAMPLE/tests/ci-suite.sh" | tr -d ' ')"

# setup.sh never prints a configured Grafana password (its banner reaches terminals and job logs); the demo default is public and is shown
sed -n '/^grafana_login_note() {/,/^}/p' "$EXAMPLE/setup.sh" > "$TMP/note-fn.sh"
check "the banner's password note is a function setup.sh defines" "yes" "$([ -s "$TMP/note-fn.sh" ] && echo yes || echo no)"
note_for() { env "GRAFANA_ADMIN_PASSWORD=$1" bash -c ". \"$TMP/note-fn.sh\"; grafana_login_note" 2> /dev/null; }
S_BANNER=Banner-Dummy-Pw-4f7e
check "  a configured password is not in the note (and the note says where it is configured)" "0 yes" \
  "$(note_for $S_BANNER | grep -c "$S_BANNER") $(note_for $S_BANNER | grep -q 'GRAFANA_ADMIN_PASSWORD' && echo yes || echo no)"
check "  a short configured password and one that equals no default are not in the note either" "0" "$(note_for x9Kq | grep -c 'x9Kq')"
check "  the demo default is shown as the default" "yes" "$(note_for admin | grep -q 'password admin, the demo default' && echo yes || echo no)"
check "  and no other line of setup.sh prints the variable (only the note's comparison and the authenticated health request mention it)" "0" \
  "$(grep -n 'GRAFANA_ADMIN_PASSWORD' "$EXAMPLE/setup.sh" | grep -vc 'http_get\|= admin\|^[0-9]*:#\|set in GRAFANA_ADMIN_PASSWORD')"

# a signal while the scenario run is going: the kept stack is removed and the evidence is collected before the suite ends
rm -rf "$TMP/stub-term"
mkdir -p "$TMP/stub-term"
: > "$TMP/stub-term/calls.log"
(cd "$EX" && python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/term.out" --timeout 90 --signal TERM --when-exists "$TMP/stub-term/signal" -- \
  env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-term/calls.log" STUB_ROOT="$TMP/stub-term" STUB_SIGNAL_FILE="$TMP/stub-term/signal" REGISTRY_IMAGE=x CONTAINER_RUNTIME=stubrt \
  CI_ARTIFACT_DIR="$TMP/stub-term/artifacts" ./tests/ci-suite.sh) > "$TMP/term.launch" 2>&1
check "TERM during the scenario run: the suite ends with status 143 and without a hang" "status=143 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/term.launch") $(grep -o 'hung=[a-z]*' "$TMP/term.launch")"
check "  the kept scenario stack was removed on the way out" "yes" "$(grep -q '^teardown --purge project=ci-scn-' "$TMP/stub-term/calls.log" && echo yes || echo no)"
check "  and the evidence was collected into the upload directory" "yes" "$([ -f "$TMP/stub-term/artifacts/publish/MANIFEST.json" ] && echo yes || echo no)"

# A signal while the scenario run is going and a removal that never returns: the snapshot is taken first and the cleanup is bounded
rm -rf "$TMP/stub-hang"
mkdir -p "$TMP/stub-hang"
: > "$TMP/stub-hang/calls.log"
(cd "$EX" && python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/hang.out" --timeout 90 --signal TERM --when-exists "$TMP/stub-hang/signal" -- \
  env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-hang/calls.log" STUB_ROOT="$TMP/stub-hang" STUB_SIGNAL_FILE="$TMP/stub-hang/signal" STUB_TEARDOWN_HANG=1 \
  CI_CLEANUP_SECONDS=4 REGISTRY_IMAGE=x CONTAINER_RUNTIME=stubrt CI_ARTIFACT_DIR="$TMP/stub-hang/artifacts" ./tests/ci-suite.sh) > "$TMP/hang.launch" 2>&1
check "TERM with a removal that hangs: the suite still ends (status 143, no hang) within the cleanup deadline" "status=143 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/hang.launch") $(grep -o 'hung=[a-z]*' "$TMP/hang.launch")"
check "  the snapshot existed before the removal started" "yes" "$(grep -q '^teardown --purge .*publish-present=yes' "$TMP/stub-hang/calls.log" && echo yes || echo no)"
check "  and its summary records that the run was interrupted" "yes" \
  "$(grep -q '^suite	interrupted' "$TMP/stub-hang/artifacts/publish/summary.tsv" && echo yes || echo no)"

# has_aside <dir>: whether a directory set aside as an incomplete publish is in it
has_aside() {
  local entry
  for entry in "$1"/incomplete-publish.*; do [ -e "$entry" ] && return 0; done
  return 1
}
# A signal while the scenario runner is going, before it ever handed over a kept project: cleanup is reported unavailable in a fixed sentence,
# nothing is deleted by label or by name, and nothing the runner printed is repeated
rm -rf "$TMP/stub-nohandoff"
mkdir -p "$TMP/stub-nohandoff"
: > "$TMP/stub-nohandoff/calls.log"
(cd "$EX" && python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/nohandoff.out" --timeout 90 --signal TERM --when-exists "$TMP/stub-nohandoff/signal" -- \
  env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-nohandoff/calls.log" STUB_ROOT="$TMP/stub-nohandoff" STUB_SIGNAL_FILE="$TMP/stub-nohandoff/signal" STUB_NO_HANDOFF=1 \
  CI_CLEANUP_SECONDS=4 REGISTRY_IMAGE=x CONTAINER_RUNTIME=stubrt CI_ARTIFACT_DIR="$TMP/stub-nohandoff/artifacts" ./tests/ci-suite.sh) > "$TMP/nohandoff.launch" 2>&1
check "TERM while the runner has handed over nothing: status 143 and no hang" "status=143 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/nohandoff.launch") $(grep -o 'hung=[a-z]*' "$TMP/nohandoff.launch")"
check "  the console says cleanup is unavailable, once, in the fixed sentence, naming only the suite's own project" "1" \
  "$(grep -c '^==> scenarios-teardown: cleanup unavailable, the scenario runner handed over no verified kept project (ci-scn-[0-9a-f]*); nothing was removed$' "$TMP/nohandoff.out")"
# The sentinel matches no secret pattern, so the collector publishes the runner's log (tail) as it does for every phase: it is in the
# upload on purpose, which proves the collection ran, and it must still not be repeated on the console by the suite
check "  nothing the runner printed is repeated on the console, while its log is collected (the sentinel is in the published scenarios.log)" "0 1" \
  "$(grep -c RUNNER-OUTPUT-SENTINEL-31bd "$TMP/nohandoff.out") $(grep -c RUNNER-OUTPUT-SENTINEL-31bd "$TMP/stub-nohandoff/artifacts/publish/logs/scenarios.log" 2> /dev/null)"
check "  and nothing was deleted: no teardown call and no remove call of the runtime" "0 0" \
  "$(calls nohandoff teardown) $(grep -c '^stubrt .* rm \|^stubrt rm ' "$TMP/stub-nohandoff/calls.log")"

# A signal while the collection itself is running (stand-in collectors that hang): publish/ is a complete collection or absent
for variant in hang-once two-signal; do
  rm -rf "$TMP/stub-$variant"
  mkdir -p "$TMP/stub-$variant"
  : > "$TMP/stub-$variant/calls.log"
  (cd "$EX" && python3 -I "$EXAMPLE/tests/ui/test/launch.py" --log "$TMP/$variant.out" --timeout 90 --signal TERM --when-exists "$TMP/stub-$variant/collect-signal" -- \
    env PATH="$BIN:$PATH" STUB_LOG="$TMP/stub-$variant/calls.log" STUB_ROOT="$TMP/stub-$variant" STUB_COLLECT="$variant" STUB_COLLECT_COUNT="$TMP/stub-$variant/collect-count" \
    STUB_COLLECT_SIGNAL="$TMP/stub-$variant/collect-signal" CI_CLEANUP_SECONDS=4 REGISTRY_IMAGE=x CONTAINER_RUNTIME=stubrt \
    CI_ARTIFACT_DIR="$TMP/stub-$variant/artifacts" ./tests/ci-suite.sh) > "$TMP/$variant.launch" 2>&1
  check "TERM during the collection ($variant): the suite ends with status 143 and no hang" "status=143 hung=no" "$(grep -o 'status=[0-9-]*' "$TMP/$variant.launch") $(grep -o 'hung=[a-z]*' "$TMP/$variant.launch")"
  check "  the half filled directory the collector left was set aside, never left in publish/" "yes" \
    "$(has_aside "$TMP/stub-$variant/artifacts/work" && echo yes || echo no)"
  check "  nothing from it is in publish/" "0" "$(find "$TMP/stub-$variant/artifacts/publish" -name partial-marker 2> /dev/null | wc -l | tr -d ' ')"
done
check "  with one signal the hook collected again: publish/ has its manifest and says the run was interrupted" "yes yes" \
  "$([ -f "$TMP/stub-hang-once/artifacts/publish/MANIFEST.json" ] && echo yes || echo no) $(grep -q '^suite	interrupted' "$TMP/stub-hang-once/artifacts/publish/summary.tsv" && echo yes || echo no)"
check "  with a second signal during that collection the hook ended at once: publish/ does not exist at all" "no" "$([ -e "$TMP/stub-two-signal/artifacts/publish" ] && echo yes || echo no)"

printf '\n%s passed, %s failed\n' "$passed" "$failures"
if [ "$failures" -gt 0 ]; then
  printf '%s check(s) failed.\n' "$failures"
  exit 1
fi
echo "All checks passed."
