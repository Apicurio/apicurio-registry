#!/usr/bin/env bash
#
# Tests for scalpel-summary.sh. Plain bash, no test framework: the repository
# has no bats dependency and this needs none.
#
# Usage: bash .github/scripts/scalpel-summary.test.sh
#
# SC2016: backticks in the expected-output needles are literal markdown, not
# command substitution. SC2001: the seds below prefix every line of a captured
# block, which parameter expansion cannot do.
# shellcheck disable=SC2016,SC2001

set -uo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
subject="$script_dir/scalpel-summary.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

pass=0
fail=0

# A Reactor Build Order block of $1 modules, followed by a Reactor Summary of
# $2 successful modules. The two differ when a build dies partway.
make_log() {
  local order=$1 success=$2 file=$3 i
  {
    echo "[INFO] Reactor Build Order:"
    echo "[INFO] "
    for ((i = 1; i <= order; i++)); do
      printf '[INFO] Registry :: Mod%-4d [jar]\n' "$i"
    done
    echo "[INFO] "
    echo "[INFO] Reactor Summary:"
    echo "[INFO] "
    for ((i = 1; i <= success; i++)); do
      printf '[INFO] Registry :: Mod%-4d SUCCESS [  1.000 s]\n' "$i"
    done
  } > "$file"
}

check() {
  local name=$1 report=$2 log=$3 mode=$4 needle=$5 out rc found
  out=$(bash "$subject" "$report" "$log" 2>"$work/stderr")
  rc=$?

  if [ "$rc" -ne 0 ]; then
    echo "FAIL $name: exited $rc (must never fail the job)"
    sed 's/^/      /' "$work/stderr"
    fail=$((fail + 1))
    return
  fi

  # Exiting 0 is not enough on its own. An arithmetic syntax error inside an
  # if/elif branch abandons the rest of that branch without propagating out, so
  # the script can print its header, lose the table and still exit 0. Empty
  # output satisfies every `lacks` assertion, which is how a suite of green
  # tests missed exactly that. Anything on stderr is the tell.
  if [ -s "$work/stderr" ]; then
    echo "FAIL $name: exited 0 but wrote to stderr"
    sed 's/^/      /' "$work/stderr"
    fail=$((fail + 1))
    return
  fi

  if grep -qF "$needle" <<<"$out"; then found=yes; else found=no; fi
  case "$mode:$found" in
    has:yes | lacks:no) ;;
    has:no | lacks:yes)
      echo "FAIL $name: expected to $mode \"$needle\""
      sed 's/^/      /' <<<"$out"
      fail=$((fail + 1))
      return
      ;;
    # A typo in the mode argument used to score ok while asserting nothing:
    # both arms of the old condition compared against a literal, so any other
    # word satisfied neither and fell through to the pass counter.
    *)
      echo "FAIL $name: unknown assertion mode \"$mode\", expected has or lacks"
      fail=$((fail + 1))
      return
      ;;
  esac

  echo "ok   $name"
  pass=$((pass + 1))
}

# --- the real shape, from the PR 10087 artifact ------------------------------
# 5 affected + 42 upstream = 47 built of a 57-module reactor, 10 not built.
cat > "$work/v1.json" <<'EOF'
{"version":"1","affectedModules":["a","b","c","d","e"],"excludedUpstreamCount":42,"fullBuildTriggered":false}
EOF
make_log 57 57 "$work/full.log"

check "v1 report: build set is affected + upstream" \
  "$work/v1.json" "$work/full.log" has "| projected build set | 47 |"
check "v1 report: reactor scraped from the build log" \
  "$work/v1.json" "$work/full.log" has "| reactor | 57 |"
check "v1 report: saving is the remainder, not the affected count" \
  "$work/v1.json" "$work/full.log" has "| projected modules not built | 10 |"

# --- regression: a newer Scalpel writes a status report ----------------------
# Scalpel 0.4.0 writes schema version 2, and on a skip that report carries only
# baseBranch, status, reason and fullBuildTriggered. It is a valid object, so a
# `type == "object"` guard admits it; jq's `null | length` is 0, so the
# arithmetic then claimed the entire reactor as a saving.
cat > "$work/v2-status.json" <<'EOF'
{"version":"2","baseBranch":"main","status":"SKIPPED","reason":"no changes matched","fullBuildTriggered":false}
EOF
check "v2 status report: refuses to do the arithmetic" \
  "$work/v2-status.json" "$work/full.log" has "does not match the schema"
check "v2 status report: claims no saving" \
  "$work/v2-status.json" "$work/full.log" lacks "projected modules not built"
check "v2 status report: names the version it found" \
  "$work/v2-status.json" "$work/full.log" has '`2`'

# An object missing only excludedUpstreamCount is the same hazard.
cat > "$work/v1-partial.json" <<'EOF'
{"version":"1","affectedModules":["a"],"fullBuildTriggered":false}
EOF
check "v1 report missing excludedUpstreamCount: refuses the arithmetic" \
  "$work/v1-partial.json" "$work/full.log" lacks "projected build set"

# The same hazard by another route. jq's has() is true for an explicit null, and
# the value then reaches the shell as the bare word `null`, which aborts the
# arithmetic under `set -u` and exits 1. A quoted number and a boolean are the
# same shape of input. A negative count is a number the guard must still refuse,
# because it would print a build set below the affected count. The last two are
# numbers the shell cannot hold: jq renders 1e999 in exponent form, which bash
# arithmetic rejects, and renders the other as digits that overflow a 64-bit
# integer and wrap to something that still looks like a count.
for bad_count in null '"42"' true -1 1e999 99999999999999999999; do
  printf '{"version":"1","affectedModules":["a"],"excludedUpstreamCount":%s}\n' \
    "$bad_count" > "$work/v1-badcount.json"
  check "excludedUpstreamCount $bad_count: refuses the arithmetic" \
    "$work/v1-badcount.json" "$work/full.log" lacks "projected build set"
done

# JSON has one number type, so a count serialised as 42.0 is a legitimate count
# rather than a malformed one. bash arithmetic rejects it outright, which used
# to empty the table while still exiting 0. It must produce the table.
cat > "$work/v1-float.json" <<'EOF'
{"version":"1","affectedModules":["a","b","c","d","e"],"excludedUpstreamCount":42.0,"fullBuildTriggered":false}
EOF
check "excludedUpstreamCount 42.0: coerced rather than refused" \
  "$work/v1-float.json" "$work/full.log" has "| projected build set | 47 |"

# --- regression: a build that died partway -----------------------------------
# The Reactor Build Order block is printed before any module runs, so it is
# complete at 57 even though only 49 modules reported SUCCESS. Counting SUCCESS
# rows instead would give a reactor of 49 against a build set of 47 and publish
# "2 modules not built" for a build that never finished.
make_log 57 49 "$work/died.log"
check "failed build: reactor comes from the build order, not the summary" \
  "$work/v1.json" "$work/died.log" has "| reactor | 57 |"

# --- full-build trigger ------------------------------------------------------
cat > "$work/trigger.json" <<'EOF'
{"version":"1","fullBuildTriggered":true,"triggerFile":"pom.xml"}
EOF
check "full build trigger: reports a full build" \
  "$work/trigger.json" "$work/full.log" has "**full build**"
check "full build trigger: names the file" \
  "$work/trigger.json" "$work/full.log" has '`pom.xml`'

# --- degraded inputs ---------------------------------------------------------
check "missing report: explains rather than fails" \
  "$work/absent.json" "$work/full.log" has "no usable report"

printf 'not json at all' > "$work/bad.json"
check "malformed report: explains rather than fails" \
  "$work/bad.json" "$work/full.log" has "no usable report"

printf '[1,2,3]' > "$work/array.json"
check "report that is valid json but not an object" \
  "$work/array.json" "$work/full.log" has "no usable report"

check "missing build log: omits the reactor rows" \
  "$work/v1.json" "$work/absent.log" lacks "| reactor |"
check "missing build log: still reports the build set" \
  "$work/v1.json" "$work/absent.log" has "| projected build set | 47 |"

# A log from a different run, too short to be this report's reactor.
make_log 12 12 "$work/short.log"
check "reactor below the build set: omits the rows rather than going negative" \
  "$work/v1.json" "$work/short.log" lacks "modules not built"

# A log whose build order block has no blank terminator, because it was
# truncated or because a parallel build interleaved something else into it.
# Every `[INFO] ` line to EOF used to count as a module, which inflates the
# reactor past the build set and publishes whatever saving the log was long
# enough to produce.
{
  echo "[INFO] Reactor Build Order:"
  echo "[INFO] "
  for i in $(seq 1 57); do printf '[INFO] Registry :: Mod%-4d [jar]\n' "$i"; done
  echo "[INFO] ------------------< io.apicurio:apicurio-registry >-------------"
  for i in $(seq 1 400); do echo "[INFO] Building module $i"; done
} > "$work/unterminated.log"
check "build order without a blank terminator: reactor stays at 57" \
  "$work/v1.json" "$work/unterminated.log" has "| reactor | 57 |"

# The third bound. A log that leaves the `[INFO] ` prefix behind entirely, which
# is what a build with warnings or a plugin writing on its own prefix looks like.
{
  echo "[INFO] Reactor Build Order:"
  echo "[INFO] "
  for i in $(seq 1 57); do printf '[INFO] Registry :: Mod%-4d [jar]\n' "$i"; done
  for i in $(seq 1 400); do echo "[WARNING] noise $i"; done
} > "$work/noprefix.log"
check "build order ended by a non-INFO line: reactor stays at 57" \
  "$work/v1.json" "$work/noprefix.log" has "| reactor | 57 |"

# --- every branch says what the job actually did -----------------------------
for r in v1 v2-status trigger bad; do
  check "$r: states that mode=report trimmed nothing" \
    "$work/$r.json" "$work/full.log" has "not a saving this run made"
done

# --- the pin must still write the schema the script reads --------------------
# Refusing arithmetic on an unknown schema is the safe half of the guard, and on
# its own it is a silent half: moving the pin would leave every summary without
# numbers and nothing in CI would go red. This is the loud half. It fails on the
# pull request that moves the pin, which is where teaching the script a newer
# schema belongs. Scalpel 0.4.0 writes schema 2 and, on a skip, a status report
# with none of the fields the arithmetic needs, so a bump is a real code change
# and not a version string to wave through.
pin_file="$(cd "$script_dir/../.." && pwd)/.mvn/extensions.xml"
writes_schema_1="0.3.10"

pin=$(awk '
  /<extension>/    { blk = "" }
                   { blk = blk $0 }
  /<\/extension>/  {
    if (blk ~ /eu\.maveniverse\.maven\.scalpel/ && match(blk, /<version>[^<]+<\/version>/))
      print substr(blk, RSTART + 9, RLENGTH - 19)
  }
' "$pin_file" 2>/dev/null) || pin=""

if [ -z "$pin" ]; then
  echo "FAIL pinned Scalpel version: none found in $pin_file"
  fail=$((fail + 1))
elif ! grep -qxF "$pin" <<<"$writes_schema_1"; then
  echo "FAIL pinned Scalpel version: $pin is pinned, but scalpel-summary.sh reads"
  echo "      report schema 1, written by: $(tr '\n' ' ' <<<"$writes_schema_1")"
  echo "      Teach the script the new schema, then list the version here."
  fail=$((fail + 1))
else
  echo "ok   pinned Scalpel $pin still writes report schema 1"
  pass=$((pass + 1))
fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
