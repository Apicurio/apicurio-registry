#!/usr/bin/env bash
#
# Turn a Scalpel report into a readable summary.
#
# Usage: scalpel-summary.sh <report.json> <maven-build.log>
#
# Writes markdown to stdout. Never fails the caller: every degraded input is a
# branch that explains itself, because this only reports and a summary step
# should not redden a job.
#
# The arithmetic it exists for is stated in the output the script emits, so a
# reader of the artifact gets it without this file.

set -euo pipefail

report=${1:?usage: scalpel-summary.sh <report.json> <build.log>}
build_log=${2:?usage: scalpel-summary.sh <report.json> <build.log>}

echo "## Scalpel report"
echo
echo "This job runs \`mode=report\`. Scalpel trimmed nothing here, and no other job"
echo "in this run was trimmed either. Anything below is a projection of what a"
echo "trimming build would have done, not a saving this run made."
echo

if ! jq -e 'type == "object"' "$report" > /dev/null 2>&1; then
  # Scalpel returns before writing anything on two of its paths, so an absent
  # report is a result and not an error. The same branch covers an unreadable
  # one.
  echo "There is no usable report here, so nothing was analysed."
  echo
  echo "That is usually an outcome rather than a failure. Scalpel returns before"
  echo "writing anything when a changed file matches \`scalpel.disableTriggers\` or"
  echo "when \`scalpel.excludePaths\` removes every changed file, and both are"
  echo "common on this repository. See \`.mvn/maven.config\` for the patterns in"
  echo "force. On either of those two paths the projection is a **full build**,"
  echo "which is how the baseline in the README counts them."
  echo
  echo "A Maven failure before the session starts leaves the same empty state, and"
  echo "that one projects nothing at all. Check the Generate step when the run is"
  echo "red, because the two cases are indistinguishable from here."

elif [ "$(jq -r '.fullBuildTriggered // false' "$report")" = "true" ]; then
  # Not dead code, though it is rare here: Scalpel defaults
  # scalpel.fullBuildTriggers to `.mvn/**`, which this project also lists in
  # scalpel.disableTriggers, so the two overlap and either outcome is possible.
  trigger=$(jq -r '.triggerFile // "not recorded"' "$report")
  echo "A changed file matched \`scalpel.fullBuildTriggers\`, so the projection is a"
  echo "**full build** with no reduced build set. Trigger: \`${trigger//\`/}\`."

elif ! jq -e '(.version == "1" or .version == 1)
              and ((.affectedModules | type) == "array")
              and ((.excludedUpstreamCount | type) == "number")
              and (.excludedUpstreamCount >= 0)
              and (.excludedUpstreamCount < 100000)' "$report" > /dev/null 2>&1; then
  # The field types are validated, not just the JSON type and not just the key.
  # A newer Scalpel writes a status report that is a perfectly good object but
  # carries none of these fields, and jq's `null | length` is 0, so a type-only
  # check would read it as "zero modules built" and claim the whole reactor as a
  # saving. `has()` is not enough either: it is true for an explicit null, which
  # then reaches the shell as the bare word `null` and aborts the arithmetic.
  #
  # The upper bound is there because JSON numbers have no range limit and the
  # shell does. jq renders a very large one in exponent form, which bash
  # arithmetic rejects outright, and renders a merely huge one as digits that
  # overflow a 64-bit integer and wrap to something plausible-looking. The first
  # would fail a job this script promises never to fail, the second would publish
  # a wrong number. This reactor has 57 modules, so 100000 refuses only counts
  # that are already impossible.
  version=$(jq -r '.version // "absent"' "$report")
  echo "The report does not match the schema this summary understands (version 1),"
  echo "so no arithmetic was attempted. Reported version: \`${version//\`/}\`."
  echo
  echo "This means the Scalpel version moved. Check the pin in"
  echo "\`.mvn/extensions.xml\` against the schema described in"
  echo "\`.github/workflows/README.md\` and update this script to match."

else
  affected=$(jq '.affectedModules | length' "$report")
  # `floor` because JSON has one number type: a count serialised as 42.0 is a
  # valid number that bash arithmetic rejects as a syntax error.
  upstream=$(jq '.excludedUpstreamCount | floor' "$report")
  build_set=$(( affected + upstream ))

  # Only this branch needs the reactor, and the log is the largest input here, so
  # the scan waits until the degraded branches above have been ruled out.
  #
  # Maven prints the Reactor Build Order before any module runs, so the block is
  # complete even when the build dies partway. The Reactor Summary at the end is
  # not, and counting its SUCCESS rows on a failed build undercounts the reactor
  # by however many modules never ran.
  #
  # The block is bounded on three sides. Without that, a log truncated mid-block
  # has no terminator and every remaining `[INFO] ` line to EOF counts as a
  # module, which inflates the reactor past the build set and publishes a
  # nonsense saving instead of omitting the rows.
  reactor=$(awk '
    /^\[INFO\] Reactor Build Order:/ { in_block = 1; next }
    in_block && /^\[INFO\] *$/       { if (n) exit; next }
    in_block && /^\[INFO\] -----/    { if (n) exit; next }
    in_block && !/^\[INFO\] /        { if (n) exit; next }
    in_block                         { n++ }
    END                              { print n + 0 }
  ' "$build_log" 2>/dev/null) || reactor=0

  echo "| | modules |"
  echo "| --- | ---: |"
  echo "| \`affectedModules\`, listed in the report | $affected |"
  echo "| \`excludedUpstreamCount\`, omitted from the report but still built | $upstream |"
  echo "| projected build set | $build_set |"
  # A reactor smaller than the build set means the log was not the one that
  # produced this report. Omit the rows rather than publish a negative number.
  if [ "$reactor" -ge "$build_set" ] && [ "$reactor" -gt 0 ]; then
    echo "| reactor | $reactor |"
    echo "| projected modules not built | $(( reactor - build_set )) |"
  fi
  echo
  echo "The build set is \`affectedModules + excludedUpstreamCount\`. Upstream build"
  echo "prerequisites are dropped from the report and not from the build, so"
  echo "reading \`affectedModules\` on its own understates what compiles."
fi

echo
echo "See \`.github/workflows/README.md\`, \"Reading the Scalpel report\"."
