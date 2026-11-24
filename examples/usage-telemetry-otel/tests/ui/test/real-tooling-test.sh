#!/usr/bin/env bash
# Regressions of the runner against the REAL pinned Playwright, in owned copies of the example: what the actual installer writes
# before it downloads anything, what the actual command line needs to start, and that the normal path is unchanged. No stubs of
# Playwright; only a deliberately unreachable local download host, so nothing is ever downloaded. The real node_modules and the
# real Chromium of the checkout are cloned into the copy and never modified in place.
#
# Usage: bash tests/ui/test/real-tooling-test.sh [--source EXAMPLE_DIR] [--bash PATH] [--selector dry-run|find-head] [--with-npm]
#   --source DIR  the example directory whose runner is tested (default: this checkout); point it at an older copy to see the cases fail
#   --bash PATH   the interpreter pinned first in PATH, as in runner-test.sh (default: the one running this script)
#   --selector dry-run|find-head   how the install locations are picked (default dry-run, from the pinned CLI's own report). find-head is
#                 the mutation mode: the old way (the first directory a listing returns); the cases that follow must then be rejected as
#                 not using the revision in use. Any other value, a missing or an empty one is refused with status 2 before anything is set up
#   --with-npm    also repair a damaged copy with the real `npm ci` (needs the npm registry or a warm npm cache)
# Needs about 650 MB of disk under TMPDIR (an APFS clone on macOS, a reflink copy where the file system has them, makes it cheap; every
# other file system gets an ordinary copy, see clone-dir.sh) and a Chromium already installed in the checkout.
set -o pipefail

SRC=$(cd "$(dirname "$0")/../../.." && pwd -P)
# shellcheck source=tests/ui/test/clone-dir.sh
. "$(dirname "$0")/clone-dir.sh"
PINNED=""
WITH_NPM=false
SELECTOR=dry-run
while [ $# -gt 0 ]; do
  case "$1" in
    --source) SRC=$(cd "$2" && pwd -P); shift 2 ;;
    --bash) PINNED=$2; shift 2 ;;
    --with-npm) WITH_NPM=true; shift ;;
    --selector)
      # checked before anything is set up: a missing value, an empty one, the next option taken as the value or an unknown name must
      # not run the suite in a mode other than the one asked for (and `shift 2` with one argument left would never advance)
      [ $# -ge 2 ] || { echo "--selector needs a value: dry-run or find-head" >&2; exit 2; }
      case "$2" in
        dry-run | find-head) SELECTOR=$2; shift 2 ;;
        "") echo "--selector needs a value: dry-run or find-head, not an empty string" >&2; exit 2 ;;
        -*) echo "--selector needs a value: dry-run or find-head, not the option '$2'" >&2; exit 2 ;;
        *) echo "unknown --selector value '$2': use dry-run or find-head" >&2; exit 2 ;;
      esac ;;
    *) echo "unknown option '$1'" >&2; exit 2 ;;
  esac
done
[ -n "$PINNED" ] || PINNED=$BASH
PINNED=$(cd "$(dirname "$PINNED")" && pwd -P)/$(basename "$PINNED")
CHECKOUT=$(cd "$(dirname "$0")/../../.." && pwd -P)
for needed in "$CHECKOUT/tests/ui/node_modules/.bin/playwright" "$CHECKOUT/tests/ui/.browsers"; do
  [ -e "$needed" ] || { echo "$needed is missing: run ./test-ui.sh --setup-only first" >&2; exit 2; }
done
TMP=$(cd "$(mktemp -d "${TMPDIR:-/tmp}/ui-real-tooling.XXXXXX")" && pwd -P)
trap 'rm -rf "$TMP"' EXIT
failures=0
passed=0
ok() { passed=$((passed + 1)); printf '  ok    %s\n' "$1"; }
fail() {
  failures=$((failures + 1))
  printf '  FAIL  %s\n' "$1"
  if [ -n "${OUT:-}" ] && [ -f "$OUT" ]; then tail -8 "$OUT" | sed 's/^/          | /'; fi
}
check() { if [ "$2" = "$3" ]; then ok "$1"; else fail "$1: expected '$2', got '$3'"; fi; }
part() { printf '\n%s\n' "$1"; }

STATE_JSON='{"schemaVersion":2,"runId":"20261120T161252Z-85dcbd","source":{"label":"service_instance_id","instanceId":"3fdf1298-d99b-4832-9232-e4b5e3b118f0"},"timeRange":{"fromMs":1795191142000,"toMs":1795191212000},"evaluationTime":"1795191182.943","evaluationTimeSeconds":1795191182.943,"endpoints":{"grafana":"http://127.0.0.1:13000","grafanaDatasourceUid":"apicurio-usage-prometheus"},"clients":{"probe":"probe-1"},"schemas":[{"globalId":46,"contentId":46},{"globalId":47,"contentId":47},{"globalId":48,"contentId":46}],"expectedSeries":[{"clientId":"billing-service","identifierType":"global_id","identifierValue":"46","operation":"DESERIALIZE","count":1},{"clientId":"billing-service","identifierType":"global_id","identifierValue":"47","operation":"UNKNOWN","count":2},{"clientId":"orders-service","identifierType":"content_id","identifierValue":"46","operation":"DESERIALIZE","count":2},{"clientId":"orders-service","identifierType":"global_id","identifierValue":"46","operation":"SERIALIZE","count":3},{"clientId":"orders-service","identifierType":"global_id","identifierValue":"48","operation":"SERIALIZE","count":1},{"clientId":"probe-1","identifierType":"global_id","identifierValue":"46","operation":"UNKNOWN","count":1}]}'

# a local server that counts the requests it receives, standing for "somewhere on the network the tests must never reach"
HITS=$TMP/hits.log
: > "$HITS"
cat > "$TMP/hits.mjs" <<'JS'
import { appendFileSync } from "node:fs";
import { createServer } from "node:http";

const [, , port, file] = process.argv;
createServer((request, response) => {
  appendFileSync(file, `${request.url}\n`);
  response.statusCode = 404;
  response.end();
}).listen(Number(port), "127.0.0.1");
JS
HITS_PORT=$(node -e 'const s = require("net").createServer().listen(0, "127.0.0.1", () => { console.log(s.address().port); s.close(); })')
node "$TMP/hits.mjs" "$HITS_PORT" "$HITS" &
HITS_PID=$!
trap 'kill "$HITS_PID" 2> /dev/null; wait "$HITS_PID" 2> /dev/null; rm -rf "$TMP"' EXIT

# owned_copy: a copy of the example whose runner is tested, with the real packages and the real browser cloned in
owned_copy() {
  local t
  t=$(mktemp -d "$TMP/copy.XXXXXX")
  mkdir -p "$t/tests/ui" "$t/.local/projects/usage-telemetry-otel" "$t/pinned"
  ln -s "$PINNED" "$t/pinned/bash"
  cp "$SRC/lib.sh" "$SRC/test-ui.sh" "$SRC/screenshots.sh" "$t/"
  cp "$SRC/tests/ui/run-ui.sh" "$SRC/tests/ui/package.json" "$SRC/tests/ui/package-lock.json" "$t/tests/ui/"
  [ ! -d "$SRC/tests/ui/lib" ] || cp -R "$SRC/tests/ui/lib" "$t/tests/ui/lib"
  printf '%s\n' "$STATE_JSON" > "$t/.local/projects/usage-telemetry-otel/run-state.json"
  clone_dir "$CHECKOUT/tests/ui/node_modules" "$t/tests/ui/node_modules" || return 1
  clone_dir "$CHECKOUT/tests/ui/.browsers" "$t/tests/ui/.browsers" || return 1
  printf '%s' "$t"
}
runx() { # runx <copy> <env assignments and command...>
  local t=$1
  shift
  OUT=$t/out.txt
  # both mirror variables are set: the Chromium-specific one has priority over the general one, so a value inherited from the caller
  # would otherwise make these offline cases download from a real host
  (cd "$t" && env PATH="$t/pinned:$PATH" PLAYWRIGHT_DOWNLOAD_HOST=http://127.0.0.1:1 PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST=http://127.0.0.1:1 PLAYWRIGHT_DOWNLOAD_CONNECTION_TIMEOUT=3000 UI_INSTALL_TIMEOUT=90 "$@") > "$OUT" 2>&1
  RC=$?
}
count() { find "$1" -mindepth 1 2> /dev/null | wc -l | tr -d ' '; }

part "the real installer: pre-existing descendants of the browser directory that point elsewhere"
T=$(owned_copy)
CACHE=$T/cache
OUTSIDE=$T/outside
mkdir -p "$CACHE" "$OUTSIDE"
ln -s "$OUTSIDE" "$CACHE/.links"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
check "a .links that points elsewhere: the run does not succeed" yes "$([ "$RC" != 0 ] && echo yes || echo no)"
check "  nothing was written through it (files in the outside directory)" 0 "$(count "$OUTSIDE")"
check "  the message names the symbolic link" 1 "$(grep -c 'symbolic link' "$OUT")"
rm -rf "$CACHE" "$OUTSIDE"; mkdir -p "$CACHE/chromium-1255" "$OUTSIDE"
rm -rf "$CACHE/chromium-1255"; ln -s "$OUTSIDE" "$CACHE/chromium-1255"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
check "the Chromium install location pointing elsewhere: no success, nothing written through it (against an older runner this passes too: the unreachable host stops the installer before it extracts anything)" "yes 0" "$([ "$RC" != 0 ] && echo yes || echo no) $(count "$OUTSIDE")"
rm -rf "$CACHE" "$OUTSIDE"; mkdir -p "$CACHE" "$OUTSIDE"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
check "an ordinary empty browser directory reaches the installer, which fails only at the unreachable download host" "yes yes" "$([ "$RC" != 0 ] && echo yes || echo no) $(grep -q 'ECONNREFUSED\|connect\|Download failed\|download' "$OUT" && echo yes || echo no)"
check "  and wrote its package reference inside the directory it was given" 1 "$(count "$CACHE/.links" | sed 's/^[2-9].*/more/')"

part "an inherited Chromium mirror setting cannot make an offline case download"
rm -rf "$CACHE"; mkdir -p "$CACHE"
: > "$HITS"
# control: the counting server really records a request that is sent to it, so a count of zero below means something
node -e 'fetch(process.argv[1]).then(() => undefined, () => undefined)' "http://127.0.0.1:$HITS_PORT/control"
n=0
while [ ! -s "$HITS" ] && [ "$n" -lt 50 ]; do sleep 0.1; n=$((n + 1)); done
check "the counting server records a request sent to it (control)" 1 "$(wc -l < "$HITS" | tr -d ' ')"
: > "$HITS"
# the caller's environment names the counting server for both mirror variables; the harness must override both
PLAYWRIGHT_CHROMIUM_DOWNLOAD_HOST="http://127.0.0.1:$HITS_PORT" PLAYWRIGHT_DOWNLOAD_HOST="http://127.0.0.1:$HITS_PORT" runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
check "an empty-cache install attempt with mirror variables inherited from the caller: no request reached the server they name" "0" "$(wc -l < "$HITS" | tr -d ' ')"
check "  and the attempt did fail at the offline host, so the installer really ran" yes "$([ "$RC" != 0 ] && echo yes || echo no)"

part "the real installer's garbage collection must not delete other browser revisions of the directory"
rm -rf "$CACHE"; mkdir -p "$CACHE/chromium-9999" "$CACHE/chromium-1234"
: > "$CACHE/chromium-9999/INSTALLATION_COMPLETE"
: > "$CACHE/chromium-1234/INSTALLATION_COMPLETE"
echo precious > "$CACHE/chromium-9999/data.txt"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
check "an unrelated revision of the same directory survives an install attempt" "precious" "$(cat "$CACHE/chromium-9999/data.txt" 2> /dev/null)"
check "  and so does the other one" yes "$([ -e "$CACHE/chromium-1234/INSTALLATION_COMPLETE" ] && echo yes || echo no)"

part "the real installer's package reference file that is not a regular file"
rm -rf "$CACHE"; mkdir -p "$CACHE"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" ./test-ui.sh --setup-only
REFERENCE=$(find "$CACHE/.links" -type f | head -1)
check "a first attempt makes the installer write its package reference in the directory (a file named after the package)" yes "$([ -n "$REFERENCE" ] && echo yes || echo no)"
rm -f "$REFERENCE"; mkfifo "$REFERENCE"
runx "$T" PLAYWRIGHT_BROWSERS_PATH="$CACHE" UI_INSTALL_TIMEOUT=15 ./test-ui.sh --setup-only
check "with a FIFO in its place the run is refused before the installer can block opening it (status 1, not the 124 of a deadline)" "1" "$RC"
check "  and the message says what is wrong" yes "$(grep -q 'not a regular file' "$OUT" && echo yes || echo no)"

part "the browser launcher's writable path: the marker, and the whole install location it sits in, even with --no-install"
# The locations come from the installed pinned CLI's own dry-run report, never from whatever directories the cache holds: a cache that
# keeps older revisions must have the fault applied to the revision that is really used
location_of() { # location_of <copy> <name prefix>: the install location the pinned CLI reports whose directory name starts with <prefix>-
  (cd "$1/tests/ui" && PLAYWRIGHT_BROWSERS_PATH="$1/tests/ui/.browsers" ./node_modules/.bin/playwright install --dry-run chromium 2> /dev/null) |
    sed -n 's/^[[:space:]]*Install location:[[:space:]]*//p' | while read -r candidate; do
      case "$(basename "$candidate")" in "$2"-*) printf '%s' "$candidate"; break ;; esac
    done
}
for dir in chromium_headless_shell chromium; do
  T=$(owned_copy)
  # the retained older revisions are created BEFORE any location is selected, so that a selector that takes the first directory a
  # listing returns is exercised on the multi-revision cache it would get wrong
  for old in 1000 1248; do mkdir -p "$T/tests/ui/.browsers/${dir}-$old"; : > "$T/tests/ui/.browsers/${dir}-$old/INSTALLATION_COMPLETE"; done
  OLDER=$T/tests/ui/.browsers/${dir}-1248
  REPORTED=$(location_of "$T" "$dir")
  if [ "$SELECTOR" = find-head ]; then
    LOC=$(find "$T/tests/ui/.browsers" -maxdepth 1 -type d -name "${dir}-*" | head -1)
  else
    LOC=$REPORTED
  fi
  check "$dir: the location the faults are applied to is the one the pinned CLI reports for the revision in use" "$REPORTED" "$LOC"
  check "$dir: that location exists and is not one of the retained older revisions" yes "$([ -d "$LOC" ] && [ "$LOC" != "$OLDER" ] && [ "$LOC" != "$T/tests/ui/.browsers/${dir}-1000" ] && echo yes || echo no)"
  mkdir -p "$T/outside"
  echo precious > "$T/outside/sentinel"
  touch -t 200001010000 "$T/outside/sentinel"
  # a marker link inside an UNUSED older revision is not what the launcher writes to: the run is ready and the sentinel is untouched.
  # Whatever marker the copy already holds there is moved aside first, so the link is certain to be the intended one
  echo retained-old-marker > "$OLDER/DEPENDENCIES_VALIDATED"
  [ ! -e "$OLDER/DEPENDENCIES_VALIDATED" ] && [ ! -L "$OLDER/DEPENDENCIES_VALIDATED" ] || mv "$OLDER/DEPENDENCIES_VALIDATED" "$OLDER/DEPENDENCIES_VALIDATED.copied"
  ln -s "$T/outside/sentinel" "$OLDER/DEPENDENCIES_VALIDATED"
  check "$dir: the unused revision's marker is the intended link to the owned sentinel" "$T/outside/sentinel" "$(readlink "$OLDER/DEPENDENCIES_VALIDATED")"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$dir: a marker link in an unused older revision is ignored (control: the guard follows the revision in use)" "0 precious" "$RC $(cat "$T/outside/sentinel")"
  rm -f "$OLDER/DEPENDENCIES_VALIDATED"
  # the same link in the revision that is really used
  mv "$LOC/DEPENDENCIES_VALIDATED" "$LOC/DEPENDENCIES_VALIDATED.original" 2> /dev/null || true
  ln -s "$T/outside/sentinel" "$LOC/DEPENDENCIES_VALIDATED"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$dir: a marker that links to an old file elsewhere, in the revision in use: not ready" "yes no" "$([ "$RC" != 0 ] && echo yes || echo no) $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
  check "  the file elsewhere keeps its content (the launcher would have truncated it)" "precious" "$(cat "$T/outside/sentinel")"
  rm -f "$LOC/DEPENDENCIES_VALIDATED"
  mv "$LOC/DEPENDENCIES_VALIDATED.original" "$LOC/DEPENDENCIES_VALIDATED" 2> /dev/null || true
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$dir: with the original marker back (or none) the same copy is ready" 0 "$RC"
  # the whole install location as a link to a directory elsewhere that holds an old ordinary marker
  mkdir -p "$T/relocated"
  mv "$LOC" "$T/relocated/$(basename "$LOC")"
  ln -s "$T/relocated/$(basename "$LOC")" "$LOC"
  echo precious > "$T/relocated/$(basename "$LOC")/DEPENDENCIES_VALIDATED"
  touch -t 200001010000 "$T/relocated/$(basename "$LOC")/DEPENDENCIES_VALIDATED"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$dir: the install location itself a link to a directory elsewhere: not ready" "yes no" "$([ "$RC" != 0 ] && echo yes || echo no) $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
  check "  the old marker there keeps its content (the launcher would have truncated it)" "precious" "$(cat "$T/relocated/$(basename "$LOC")/DEPENDENCIES_VALIDATED")"
  rm -f "$LOC"
  mv "$T/relocated/$(basename "$LOC")" "$LOC"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "$dir: the same location as the ordinary directory it was (control): ready" 0 "$RC"
done

part "the real command line: a locked package whose manifest is intact but whose CLI file is missing"
T=$(owned_copy)
runx "$T" ./test-ui.sh --setup-only --no-install
check "the intact copy is ready (normal path unchanged)" "0 yes" "$RC $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
rm "$T/tests/ui/node_modules/playwright/lib/program.js"
runx "$T" ./test-ui.sh --setup-only --no-install
check "without lib/program.js: --setup-only --no-install is not ready" "yes no" "$([ "$RC" != 0 ] && echo yes || echo no) $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
check "  the output shows the module that is missing" yes "$(grep -q "lib/program" "$OUT" && echo yes || echo no)"
check "  and the public test command with --no-install exits non-zero as well" "yes" "$(runx "$T" ./test-ui.sh --no-install; [ "$RC" != 0 ] && echo yes || echo no)"
if $WITH_NPM; then
  runx "$T" ./test-ui.sh --setup-only
  check "with installs allowed the copy is repaired by the real npm ci and ready" "0 yes yes" "$RC $([ -f "$T/tests/ui/node_modules/playwright/lib/program.js" ] && echo yes || echo no) $(grep -q 'setup finished' "$OUT" && echo yes || echo no)"
  runx "$T" ./test-ui.sh --setup-only --no-install
  check "  and the repaired copy passes --no-install" 0 "$RC"
else
  printf '  info  real npm repair skipped (--with-npm not given)\n'
fi

printf '\n%s passed, %s failed\n' "$passed" "$failures"
[ "$failures" -eq 0 ]
