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

check() {
  local name=$1 report=$2 mode=$3 needle=$4 out rc found
  out=$(bash "$subject" "$report" 2>"$work/stderr")
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

# --- the real shapes, captured from Scalpel 0.4.1 runs on this repository ----
# The status shape: a disableTriggers match. Real run output, changedFiles and
# timings trimmed for size; the fields the script reads are verbatim.
cat > "$work/v2-status.json" <<'EOF'
{"version":"2","scalpelVersion":"0.4.1","baseBranch":"main","decisionId":"6898a0457e040e268bd0be880e37f03987cf13b751e5c849f9a6277f8c1a96d6","status":"skipped","reason":"disabled by disableTriggers match","fullBuildTriggered":true,"triggerFile":".mvn/extensions.xml","changedFiles":[".mvn/extensions.xml"],"excludedUpstreamCount":0,"affectedModules":[]}
EOF

# The full decision shape: one Java file changed. The scalars are real run
# output and the counts are internally consistent with what the table prints
# (5 + 42 = 47, 47 + 10 = 57); the module membership is synthetic, chosen to
# give both lists their real lengths without embedding 15 real module objects.
cat > "$work/v2-full.json" <<'EOF'
{"version":"2","scalpelVersion":"0.4.1","baseBranch":"main","decisionId":"eb03ad1a005c7b1d1ac7ef19bb2bd9c6299c752cb3ea001fea00d3e0fd35e28d","mergeBaseId":"c4c19d62fb18e46064d1c328a4c669fca44eb11f","headId":"73a7ee7bf8001b18a8e82274adb8dd0a98194ab7","configFingerprint":"baseBranch=484541447e31;head=48454144","fullBuildTriggered":false,"triggerFile":null,"changedFiles":["app/src/main/java/io/apicurio/registry/storage/impl/sql/CommonSqlStatements.java"],"excludedUpstreamCount":42,"buildSetSize":47,"reactorModuleCount":57,"testedModulesCount":5,"affectedModules":[{"groupId":"io.apicurio","artifactId":"apicurio-registry-app","path":"app","reasons":["SOURCE_CHANGE"],"category":"DIRECT","sourceSet":"main"},{"groupId":"io.apicurio","artifactId":"apicurio-registry-docs","path":"docs","reasons":["DOWNSTREAM_DEPENDENT"],"category":"DOWNSTREAM"},{"groupId":"io.apicurio","artifactId":"apicurio-registry-cli","path":"cli","reasons":["DOWNSTREAM_DEPENDENT"],"category":"DOWNSTREAM"},{"groupId":"io.apicurio","artifactId":"apicurio-registry-distro-docker","path":"distro/docker","reasons":["DOWNSTREAM_DEPENDENT"],"category":"DOWNSTREAM"},{"groupId":"io.apicurio","artifactId":"apicurio-registry-mcp","path":"mcp","reasons":["DOWNSTREAM_DEPENDENT"],"category":"DOWNSTREAM"}],"skippedModules":[{"groupId":"io.apicurio","artifactId":"m1","path":"p1","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m2","path":"p2","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m3","path":"p3","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m4","path":"p4","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m5","path":"p5","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m6","path":"p6","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m7","path":"p7","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m8","path":"p8","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m9","path":"p9","reason":"NOT_AFFECTED"},{"groupId":"io.apicurio","artifactId":"m10","path":"p10","reason":"NOT_AFFECTED"}]}
EOF

check "v2 full: build set read from the native field" \
  "$work/v2-full.json" has "| build set, \`buildSetSize\` | 47 |"
check "v2 full: reactor read from the native field" \
  "$work/v2-full.json" has "| reactor, \`reactorModuleCount\` | 57 |"
check "v2 full: saving is the subtraction" \
  "$work/v2-full.json" has "| projected modules not built | 10 |"
check "v2 full: skippedModules is named as the actual saving" \
  "$work/v2-full.json" has "| \`skippedModules\`, the actual saving | 10 |"
check "v2 full: tested modules reported" \
  "$work/v2-full.json" has "| \`testedModulesCount\`, modules whose tests would run | 5 |"
check "v2 full: upstream prerequisites still stated" \
  "$work/v2-full.json" has "| \`excludedUpstreamCount\`, omitted from the report but still built | 42 |"

check "v2 status: explains itself" \
  "$work/v2-status.json" has "skipped the analysis"
check "v2 status: names the reason" \
  "$work/v2-status.json" has "disabled by disableTriggers match"
check "v2 status: names the trigger file" \
  "$work/v2-status.json" has '`.mvn/extensions.xml`'
check "v2 status: projects a full build" \
  "$work/v2-status.json" has "**full build**"
check "v2 status: claims no numeric saving" \
  "$work/v2-status.json" lacks "projected modules not built"

# The failed status: change detection broke, so there is nothing to project
# and the summary must not dress the run up as a full-build outcome.
jq '.status = "failed" | .reason = "change detection did not run (see build log)"' \
  "$work/v2-status.json" > "$work/v2-failed.json"
check "v2 status failed: no projection is claimed" \
  "$work/v2-failed.json" has "no projection at all"
check "v2 status failed: does not claim a full build" \
  "$work/v2-failed.json" lacks "**full build**"
check "v2 status failed: claims no numeric saving" \
  "$work/v2-failed.json" lacks "projected modules not built"

# --- regression: schema 1 is now the unknown schema --------------------------
# Scalpel 0.3.10 writes version 1. The script reads schema 2 only, so an old
# report must be refused loudly rather than half-parsed, and the refusal names
# the producer version, which is the fact that identifies what happened.
cat > "$work/v1.json" <<'EOF'
{"version":"1","scalpelVersion":"0.3.10","affectedModules":["a","b","c","d","e"],"excludedUpstreamCount":42,"fullBuildTriggered":false}
EOF
check "v1 report: refuses the numbers" \
  "$work/v1.json" has "this summary reads schema 2"
check "v1 report: names the producing Scalpel version" \
  "$work/v1.json" has '`0.3.10`'
check "v1 report: claims no saving" \
  "$work/v1.json" lacks "projected modules not built"

# --- regression: schema 2 without the counts ---------------------------------
# The shipped schema declares all four counts optional, so a producer may emit
# a version-2 report they are missing from. Derived from the full fixture with
# jq so it is exactly the real shape minus the fields, not a hand-typed
# near-copy that can drift.
jq 'del(.excludedUpstreamCount, .buildSetSize, .reactorModuleCount, .testedModulesCount)' \
  "$work/v2-full.json" > "$work/v2-partial.json"
check "v2 report missing the counts: refuses the table" \
  "$work/v2-partial.json" has "are missing, malformed, negative"
check "v2 report missing the counts: claims no saving" \
  "$work/v2-partial.json" lacks "projected modules not built"
check "v2 report missing the counts: does not blame the pin" \
  "$work/v2-partial.json" has "report bug"

# Each count is guarded alone, with the rest of the fixture valid, because a
# guard that only fires on a wholly missing shape misses a single bad field.
# Six values on one field sweep every guard arm: `null`, a quoted number and a
# boolean all fail the type arm; -1 fails the non-negative arm; 1e999 and the
# 20-digit integer are well-formed non-negative numbers that the subtraction
# could hold, but they fail the consistency arm, because a build set larger
# than the reactor is not a report the table should summarize. The other
# three fields get one representative each, which covers the per-field half of
# the guard.
for bad in null '"42"' true -1 1e999 99999999999999999999; do
  jq --argjson v "$bad" '.buildSetSize = $v' "$work/v2-full.json" > "$work/v2-bad.json"
  check "buildSetSize $bad: refuses the table" \
    "$work/v2-bad.json" lacks "projected modules not built"
done
for field in reactorModuleCount testedModulesCount excludedUpstreamCount; do
  jq --arg f "$field" '.[$f] = null' "$work/v2-full.json" > "$work/v2-bad.json"
  check "$field null: refuses the table" \
    "$work/v2-bad.json" lacks "projected modules not built"
done

# The shipped schema types the counts as integers, so 47.0 is stricter than
# the contract requires. The coercion is deliberate leniency for a producer
# that ever emits a float: bash arithmetic rejects it outright, which used to
# empty the table while still exiting 0. It must produce the table.
jq '.buildSetSize = 47.0' "$work/v2-full.json" > "$work/v2-float.json"
check "buildSetSize 47.0: coerced rather than refused" \
  "$work/v2-float.json" has "| build set, \`buildSetSize\` | 47 |"

# Mutually inconsistent counts are a report bug, refused with the reason
# rather than published as a negative saving.
jq '.reactorModuleCount = 10' "$work/v2-full.json" > "$work/v2-inconsistent.json"
check "reactor below build set: refuses the table" \
  "$work/v2-inconsistent.json" has "are missing, malformed, negative"
check "reactor below build set: claims no saving" \
  "$work/v2-inconsistent.json" lacks "projected modules not built"

# --- a report-shaped trigger, from writeFullBuildReport ----------------------
# The real producer shape for a scalpel.fullBuildTriggers match: no status
# field and no counts. Unreachable in this repo today because disableTriggers
# subsumes the default fullBuildTriggers list and is checked first, but one
# maven.config edit away, so the branch that handles it is kept honest here.
jq '.fullBuildTriggered = true | .triggerFile = "pom.xml"
    | .changedFiles = ["pom.xml"]
    | del(.excludedUpstreamCount, .buildSetSize, .reactorModuleCount, .testedModulesCount, .affectedModules, .skippedModules)' \
  "$work/v2-full.json" > "$work/v2-trigger.json"
check "trigger report: reports a full build" \
  "$work/v2-trigger.json" has "**full build**"
check "trigger report: names the file" \
  "$work/v2-trigger.json" has '`pom.xml`'
check "trigger report: builds no table" \
  "$work/v2-trigger.json" lacks "projected modules not built"

# --- degraded inputs ---------------------------------------------------------
check "missing report: explains rather than fails" \
  "$work/absent.json" has "no usable report"

printf 'not json at all' > "$work/bad.json"
check "malformed report: explains rather than fails" \
  "$work/bad.json" has "no usable report"

printf '[1,2,3]' > "$work/array.json"
check "report that is valid json but not an object" \
  "$work/array.json" has "no usable report"

# --- every branch says what the job actually did -----------------------------
for r in v2-status v2-full v2-trigger v1 bad; do
  check "$r: states that mode=report trimmed nothing" \
    "$work/$r.json" has "not a saving this run made"
done

# --- the pin must still write the schema the script reads --------------------
# Refusing numbers on an unknown schema is the safe half of the guard, and on
# its own it is a silent half: moving the pin would leave every summary without
# numbers and nothing in CI would go red. This is the loud half. It fails on
# the pull request that moves the pin, which is where teaching the script a
# newer schema belongs. The known-producer list lives in the script itself, so
# one edit teaches both this gate and the script's own refusal message.
pin_file="$(cd "$script_dir/../.." && pwd)/.mvn/extensions.xml"
writes_schema_2=$(sed -n 's/^known_schema_2="\([^"]*\)"$/\1/p' "$subject")

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
elif [ -z "$writes_schema_2" ]; then
  echo "FAIL known_schema_2 list: not found in $subject"
  fail=$((fail + 1))
elif ! grep -qwF "$pin" <<<"$writes_schema_2"; then
  echo "FAIL pinned Scalpel version: $pin is pinned, but scalpel-summary.sh lists"
  echo "      schema 2 producers: $(tr '\n' ' ' <<<"$writes_schema_2")"
  echo "      Teach the script the new schema, then add the version there."
  fail=$((fail + 1))
else
  echo "ok   pinned Scalpel $pin is a known schema 2 producer"
  pass=$((pass + 1))
fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
