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
repo_root=$(cd "$script_dir/../.." && pwd)
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

  # -e is required, not stylistic: a needle starting with "-" is parsed as an
  # option otherwise, and the assertion fails on a string the output contains.
  if grep -qF -e "$needle" <<<"$out"; then found=yes; else found=no; fi
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

# Derive a status fixture that differs from the real one only in its reason.
# Every reason test below wants the same two edits, so writing the jq out once
# keeps the reason string the only thing a reader has to compare between them.
# triggerFile is cleared because the captured shape carries one from its
# disableTriggers run, and leaving it set would put a trigger line under reasons
# that never look at a file.
reason_fixture() {
  local reason=$1 out=$2
  jq --arg r "$reason" '.reason = $r | .triggerFile = null' \
    "$work/v2-status.json" > "$out"
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

# The table's header row, used as the needle for every "no numbers were
# published" assertion. It is the one line no other branch prints, so a `lacks`
# against it fails if a branch ever grows a table, where a needle taken from a
# single data row would also pass if that row alone were dropped.
table="| | modules |"

check "v2 full: build set read from the native field" \
  "$work/v2-full.json" has "| build set, \`buildSetSize\` | 47 |"
check "v2 full: reactor read from the native field" \
  "$work/v2-full.json" has "| reactor, \`reactorModuleCount\` | 57 |"
check "v2 full: skippedModules is the saving, and it is the subtraction" \
  "$work/v2-full.json" has \
  "| \`skippedModules\`, the modules a trimming build would not touch | 10 |"
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
  "$work/v2-status.json" lacks "$table"

# The other status reason, and the one that inverted. Two things make it worth
# its own fixture. Exhausting excludePaths projected a full build up to 0.4.0,
# trimmed to empty from 0.4.1, and builds every module again once
# scalpel.buildAllIfNoChanges=true is pinned, which .mvn/maven.config now does,
# so the same reason string has meant opposite things across this
# repository's pins and the deciding setting is named in the output. And the
# report sets fullBuildTriggered to true on this outcome, which under the pin
# is the truth, so a summary that read that field before the status would
# happen to be right here and wrong everywhere the field disagrees with the
# behaviour. Derived from the real status shape so the fields the script does
# not read stay verbatim rather than drifting as a hand-typed copy.
jq '.reason = "all changed files excluded by path filters"
    | .triggerFile = null
    | .changedFiles = ["claudedocs/9973-local-verification.md"]' \
  "$work/v2-status.json" > "$work/v2-exhausted.json"
check "v2 exhausted: projects a full build" \
  "$work/v2-exhausted.json" has "**full build**"
check "v2 exhausted: does not project an empty build" \
  "$work/v2-exhausted.json" lacks "**empty build**"
check "v2 exhausted: names the setting that decides it" \
  "$work/v2-exhausted.json" has '`scalpel.buildAllIfNoChanges`'
check "v2 exhausted: states the pinned value the full build stands on" \
  "$work/v2-exhausted.json" has 'pinned to `true` in'
check "v2 exhausted: states why the empty build is unreachable" \
  "$work/v2-exhausted.json" has "NoGoalSpecifiedException"
check "v2 exhausted: notes that 0.4.0 built every module too" \
  "$work/v2-exhausted.json" has "0.4.0 and earlier built every module"
check "v2 exhausted: claims no numeric saving" \
  "$work/v2-exhausted.json" lacks "$table"

# The only other reason that reaches trimReactorToEmpty. Both empty-reactor
# reasons are literals in extension3 of the pinned version, and this one does
# not contain the "excluded by path filters" fragment, so a summary that keyed
# the empty case on that fragment alone misreported this one even before the
# flag was pinned. Both now project the same full build.
reason_fixture "no changes detected" "$work/v2-empty.json"
check "v2 no changes detected: projects a full build" \
  "$work/v2-empty.json" has "**full build**"
check "v2 no changes detected: does not project an empty build" \
  "$work/v2-empty.json" lacks "**empty build**"

# The reasons that leave the reactor whole, in the two groups the script
# distinguishes. Configuration stood Scalpel down in the first group; in the
# second it never got far enough to compare anything. Both project a full build,
# and neither may be confused with the empty-build reasons above.
for reason in \
  "disabled by disableTriggers match" \
  "disabled by disableOnBranch" \
  "disabled by disableOnBaseBranch" \
  "not a git repository" \
  "no base branch configured"; do
  reason_fixture "$reason" "$work/v2-untrimmed.json"
  check "v2 \"$reason\": projects a full build" \
    "$work/v2-untrimmed.json" has "**full build**"
  check "v2 \"$reason\": does not project an empty build" \
    "$work/v2-untrimmed.json" lacks "**empty build**"
done

# The two core reasons above are worth one more assertion because they come from
# a different jar. extension3 writes the reasons this file mostly carries, but
# ScalpelCore returns null with a skip reason of its own, and the caller copies
# that into the report verbatim. Reading only extension3 produced a reason set
# that missed these, and a real local run then landed on "no base branch
# configured" in the default arm.
reason_fixture "no base branch configured" "$work/v2-nobase.json"
check "v2 no base branch: names the input the base branch is derived from" \
  "$work/v2-nobase.json" has '`GITHUB_BASE_REF`'

# Reasons Scalpel routes to target/scalpel-shadow.json rather than to this file.
# They cannot appear here on the pinned version, so the assertion is not that
# they are classified but that they are refused: if a later pin does start
# writing them here, the default arm must decline to guess rather than silently
# sort them into whichever arm looked closest.
for reason in \
  "no modules affected by changes" \
  "no modules match includePaths filters" \
  "disabled by -pl project selection"; do
  reason_fixture "$reason" "$work/v2-shadow.json"
  check "v2 shadow-routed \"$reason\": is refused, not guessed" \
    "$work/v2-shadow.json" has "does not recognise"
done

# A reason from a pin this script has not been taught. Guessing here has no safe
# default, because the same "skipped" status covers both an empty build and a
# full one, so the only honest output names neither.
reason_fixture "some reason a later Scalpel invented" "$work/v2-unknown-reason.json"
check "v2 unknown reason: projects no build at all" \
  "$work/v2-unknown-reason.json" has "does not recognise"
check "v2 unknown reason: does not guess a full build" \
  "$work/v2-unknown-reason.json" lacks "**full build**"
check "v2 unknown reason: does not guess an empty build" \
  "$work/v2-unknown-reason.json" lacks "**empty build**"
# The needle has to be text only the refusal arm prints. Naming the pin file
# alone scored green against an arm that had dropped its pointer entirely,
# because the captured shape carries a triggerFile and the summary prints that
# path a few lines above, satisfying the assertion on its own.
check "v2 unknown reason: points at the pin that moved" \
  "$work/v2-unknown-reason.json" has "Reasons are enumerated from the Scalpel version pinned in"

# sanitize() is the only defence for strings that come from a file path or a
# git ref. Without a fixture carrying the characters it strips, every test
# exercises it with clean input and deleting the function would keep the suite
# green. A backtick would close the code span the reason is printed inside, and
# a newline would end the list item.
jq '.reason = "disabled by disableTriggers match"
    | .triggerFile = "a`b\nc"' \
  "$work/v2-status.json" > "$work/v2-hostile.json"
check "hostile trigger file: the backtick is stripped" \
  "$work/v2-hostile.json" has '- trigger file: `ab c`'
check "hostile trigger file: the newline does not end the list item" \
  "$work/v2-hostile.json" lacks 'a`b'

# The failed status: detection broke before either trim site could run, so the
# reactor is whole and the build is full. The summary has to say that rather
# than imply the run was trimmed, and it has to name the step that owns the
# cause, since the projection is not the useful part of this outcome.
jq '.status = "failed" | .reason = "change detection did not run (see build log)"' \
  "$work/v2-status.json" > "$work/v2-failed.json"
check "v2 status failed: reports the untrimmed build" \
  "$work/v2-failed.json" has "**full build**"
check "v2 status failed: points at the step that owns the cause" \
  "$work/v2-failed.json" has "Generate step"
check "v2 status failed: claims no numeric saving" \
  "$work/v2-failed.json" lacks "$table"

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
  "$work/v1.json" lacks "$table"

# The schema check has to come before any field is interpreted. A future schema
# is free to keep the status and reason keys and change what they mean, so a
# summary that branched on them first would describe an unknown report
# confidently and wrongly. Refusing on the version is the only safe claim.
jq '.version = "3" | .scalpelVersion = "0.5.0"' \
  "$work/v2-exhausted.json" > "$work/v3-status.json"
check "unknown schema with a status: refused on the version, not read" \
  "$work/v3-status.json" has "this summary reads schema 2"
check "unknown schema with a status: projects nothing" \
  "$work/v3-status.json" lacks "**empty build**"

# --- regression: schema 2 without the counts ---------------------------------
# Of the four counts the table needs, the shipped schema requires only
# excludedUpstreamCount; buildSetSize, reactorModuleCount and testedModulesCount
# are optional, so a producer may emit a version-2 report without them. Derived
# from the full fixture with jq so it is exactly the real shape minus the fields,
# not a hand-typed near-copy that can drift.
jq 'del(.excludedUpstreamCount, .buildSetSize, .reactorModuleCount, .testedModulesCount)' \
  "$work/v2-full.json" > "$work/v2-partial.json"
check "v2 report missing the counts: refuses the table" \
  "$work/v2-partial.json" has "are missing, malformed, negative"
check "v2 report missing the counts: claims no saving" \
  "$work/v2-partial.json" lacks "$table"
check "v2 report missing the counts: does not blame the pin" \
  "$work/v2-partial.json" has "omits the optional counts is legal"

# Each count is guarded alone, with the rest of the fixture valid, because a
# guard that only fires on a wholly missing shape misses a single bad field.
# Six values on one field sweep every guard arm: `null`, a quoted number and a
# boolean all fail the type arm; -1 fails the non-negative arm; 1e999 and the
# 20-digit integer are well-formed non-negative numbers, but they fail the
# consistency arms, because the skipped count cannot equal a subtraction that
# large a build set makes negative, and the build set cannot equal affected
# plus upstream at that magnitude.
for bad in null '"42"' true -1 1e999 99999999999999999999; do
  jq --argjson v "$bad" '.buildSetSize = $v' "$work/v2-full.json" > "$work/v2-bad.json"
  check "buildSetSize $bad: refuses the table" \
    "$work/v2-bad.json" lacks "$table"
done
for field in reactorModuleCount testedModulesCount excludedUpstreamCount; do
  jq --arg f "$field" '.[$f] = null' "$work/v2-full.json" > "$work/v2-bad.json"
  check "$field null: refuses the table" \
    "$work/v2-bad.json" lacks "$table"
done

# The two list guards, which the count sweep above does not reach. Both lists
# are measured with `length`, and jq gives a length for a string and an object
# too, so without the type arms a report carrying either in place of a list
# would publish a number that counts characters or keys. skippedModules is the
# subtler of the two, because it is read through `// []` and a non-null
# non-array survives that default untouched. Its object is built with exactly
# reactor - buildSetSize keys so that `length` satisfies the consistency arm:
# a shorter one is refused by that arm instead, which would leave the type arm
# unexercised and the test green against its removal.
jq '.affectedModules = "app"' "$work/v2-full.json" > "$work/v2-bad.json"
check "affectedModules not a list: refuses the table" \
  "$work/v2-bad.json" lacks "$table"
jq '.skippedModules = ([range(10)] | map({key: ("m" + tostring), value: "NOT_AFFECTED"}) | from_entries)' \
  "$work/v2-full.json" > "$work/v2-bad.json"
check "skippedModules not a list: refuses the table" \
  "$work/v2-bad.json" lacks "$table"

# The two identities the table's own prose asserts, guarded as conjuncts so a
# report violating either is refused rather than published. The first fixture
# keeps the skipped-count subtraction consistent (0 == 3 - 3) so only the
# build-set identity fires: affected 0 plus upstream 0 cannot equal a build
# set of 3. The second sends the tested count past the reactor with every
# other guard satisfiable.
jq '.affectedModules = [] | .excludedUpstreamCount = 0
    | .buildSetSize = 3 | .reactorModuleCount = 3 | .skippedModules = []' \
  "$work/v2-full.json" > "$work/v2-bad-sum.json"
check "build set above affected plus upstream: refuses the table" \
  "$work/v2-bad-sum.json" lacks "$table"
check "build set above affected plus upstream: names the inconsistency" \
  "$work/v2-bad-sum.json" has "do not add up"
jq '.testedModulesCount = 60' "$work/v2-full.json" > "$work/v2-bad-tested.json"
check "tested count above the reactor: refuses the table" \
  "$work/v2-bad-tested.json" lacks "$table"

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
  "$work/v2-inconsistent.json" lacks "$table"

# --- regression: a run that skips nothing -------------------------------------
# The schema documents skippedModules as present only when at least one module
# was skipped, so a run whose build set is the whole reactor omits the key
# entirely. Requiring it made the summary call that a report bug and print no
# table, on an outcome the producer reports correctly. Real shape: 5 of the 20
# decision reports in the 0.4.1 replay of this repository omit the key, every
# one of them with buildSetSize equal to reactorModuleCount.
cat > "$work/v2-zero-skip.json" <<'EOF'
{"version":"2","scalpelVersion":"0.4.1","baseBranch":"main","decisionId":"27c6b031262e2ad9805aaa27f126d60b676dba3b858df1c1f3b31a78eb5c0a7c","fullBuildTriggered":false,"triggerFile":null,"changedFiles":["pom.xml"],"excludedUpstreamCount":0,"buildSetSize":2,"reactorModuleCount":2,"testedModulesCount":2,"affectedModules":[{"artifactId":"a"},{"artifactId":"b"}]}
EOF
check "zero skip: still draws the table" \
  "$work/v2-zero-skip.json" has "$table"
check "zero skip: reports the saving as zero" \
  "$work/v2-zero-skip.json" has \
  "| \`skippedModules\`, the modules a trimming build would not touch | 0 |"
check "zero skip: does not accuse the producer of a report bug" \
  "$work/v2-zero-skip.json" lacks "do not add up"

# A skipped list is still required to agree with the subtraction when the key is
# absent, so an absent key against a trimmed build set stays a refusal rather
# than defaulting its way into a table that says nothing was skipped.
jq '.buildSetSize = 1' "$work/v2-zero-skip.json" > "$work/v2-zero-skip-bad.json"
check "absent skippedModules against a trimmed build set: refuses the table" \
  "$work/v2-zero-skip-bad.json" lacks "$table"

# --- a decision report with an empty build set -------------------------------
# The real no-affected-modules shape: a change confined to a module outside the
# default reactor (here operator/) writes an ordinary report with no status, a
# zero build set and skippedModules naming all 57 modules. The counts are those
# of a real 0.4.2 run on this repository (commit 0b35b825b's operator-only
# change set); the shape is derived from the full fixture above, and only the
# list lengths are read, so the elements are bare numbers. The report's
# projection is an empty reactor, but a trimming build leaves the reactor whole
# on Scalpel 0.4.2 with the flag either way, so the table's reactor-wide saving
# would be the overstatement this summary exists to prevent.
jq '.scalpelVersion = "0.4.2"
    | .changedFiles = ["operator/install/install.yaml"]
    | .excludedUpstreamCount = 0
    | .buildSetSize = 0
    | .testedModulesCount = 0
    | .affectedModules = []
    | .skippedModules = [range(57)]' \
  "$work/v2-full.json" > "$work/v2-noaffect.json"
check "v2 no affected modules: names the empty projection" \
  "$work/v2-noaffect.json" has "empty build set"
check "v2 no affected modules: states the reactor stays whole" \
  "$work/v2-noaffect.json" has "leaves the reactor whole"
check "v2 no affected modules: draws no saving table" \
  "$work/v2-noaffect.json" lacks "$table"

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
  "$work/v2-trigger.json" lacks "$table"

# --- degraded inputs ---------------------------------------------------------
check "missing report: explains rather than fails" \
  "$work/absent.json" has "no usable report"

printf 'not json at all' > "$work/bad.json"
check "malformed report: explains rather than fails" \
  "$work/bad.json" has "no usable report"

printf '[1,2,3]' > "$work/array.json"
check "report that is valid json but not an object" \
  "$work/array.json" has "no usable report"

# --- the disclaimer is unconditional -----------------------------------------
# mode=report trims nothing, and the summary has to say so on every outcome.
# The line is printed in the header, above the first branch, so one fixture
# proves it for all of them; the branches are covered by the checks above.
check "states that mode=report trimmed nothing" \
  "$work/v2-full.json" has "not a saving this run made"

# --- the pin must still write the schema the script reads --------------------
# Refusing numbers on an unknown schema is the safe half of the guard, and on
# its own it is a silent half: moving the pin would leave every summary without
# numbers and nothing in CI would go red. This is the loud half. It fails on
# the pull request that moves the pin, which is where teaching the script a
# newer schema belongs. The known-producer list lives in the script itself, so
# one edit teaches both this gate and the script's own refusal message.
pin_file="$repo_root/.mvn/extensions.xml"
writes_schema_2=$(sed -n 's/^known_schema_2="\([^"]*\)"$/\1/p' "$subject")

pin=$(awk '
  /<extension>/    { blk = "" }
                   { blk = blk $0 }
  /<\/extension>/  {
    if (blk ~ /eu\.maveniverse\.maven\.scalpel/ && match(blk, /<version>[^<]+<\/version>/)) {
      v = substr(blk, RSTART, RLENGTH)
      gsub(/<\/?version>/, "", v)
      print v
    }
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

# --- the empty-reason projection stands on the buildAllIfNoChanges pin --------
# Both empty-reactor reasons are projected as full builds on the strength of
# -Dscalpel.buildAllIfNoChanges=true in .mvn/maven.config. Dropping or renaming
# that pin silently turns the two status arms into false claims, and a
# coordinated flip of both files defeats any test that reads the value back
# from the script, because the arm interpolates the very variable being
# flipped. This is the loud half, mirroring the schema pin above: the claimed
# value is read out of the subject script (build_all_if_no_changes_pin),
# cross-checked against maven.config itself, and required to be true, because
# the full-build prose is only true when it is.
# The zero-build-set arm is deliberately outside this guard: its claim stands
# on the Scalpel version, not on the flag, and survives a deliberate flip.
config_file="$repo_root/.mvn/maven.config"
claimed_pin=$(sed -n 's/^build_all_if_no_changes_pin="\([^"]*\)"$/\1/p' "$subject")

if [ -z "$claimed_pin" ]; then
  echo "FAIL buildAllIfNoChanges pin: build_all_if_no_changes_pin not found in $subject"
  fail=$((fail + 1))
elif [ "$claimed_pin" != "true" ]; then
  echo "FAIL buildAllIfNoChanges pin: scalpel-summary.sh claims"
  echo "      buildAllIfNoChanges=$claimed_pin, but the empty-reactor arms state a"
  echo "      full build, which is only true with the flag at true. With false the"
  echo "      reactor empties and Maven dies in NoGoalSpecifiedException instead."
  fail=$((fail + 1))
elif grep -qFx -e "-Dscalpel.buildAllIfNoChanges=$claimed_pin" "$config_file"; then
  echo "ok   buildAllIfNoChanges=$claimed_pin is pinned in .mvn/maven.config"
  pass=$((pass + 1))
else
  echo "FAIL buildAllIfNoChanges pin: scalpel-summary.sh claims"
  echo "      buildAllIfNoChanges=$claimed_pin, but -Dscalpel.buildAllIfNoChanges=$claimed_pin"
  echo "      is not pinned in $config_file"
  echo "      The excludePaths rationale in maven.config and the README section"
  echo "      \"Reading the Scalpel report\" stand on the same pin. Restore it, or"
  echo "      re-derive every claim that names it."
  fail=$((fail + 1))
fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
