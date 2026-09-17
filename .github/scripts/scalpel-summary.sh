#!/usr/bin/env bash
#
# Turn a Scalpel report into a readable summary.
#
# Usage: scalpel-summary.sh <report.json>
#
# Writes markdown to stdout. Never fails the caller: every degraded input is a
# branch that explains itself, because this only reports and a summary step
# should not redden a job.
#
# Reads report schema 2, the schema shipped inside the pinned scalpel jars as
# scalpel-report-v2.schema.json. The report carries the build set, reactor and
# tested-module counts natively, so the only arithmetic here is one subtraction,
# done in jq where numbers have no shell limits, and no build log is read.

set -euo pipefail

report=${1:?usage: scalpel-summary.sh <report.json>}

# Producers whose schema 2 this script has been checked against. scalpel-summary.test.sh
# reads this list and fails when the pin in .mvn/extensions.xml moves to a version
# absent from it, which is where teaching this script a newer schema belongs.
# 0.4.2 ships scalpel-report-v2.schema.json byte-identical to 0.4.1; it changes only
# how an empty trim is applied to the session, which mode=report never reaches.
# shellcheck disable=SC2034  # consumed by scalpel-summary.test.sh, not here
known_schema_2="0.4.1 0.4.2"

echo "## Scalpel report"
echo
echo "This job runs \`mode=report\`. Scalpel trimmed nothing here, and no other job"
echo "in this run was trimmed either. Anything below is a projection of what a"
echo "trimming build would have done, not a saving this run made."
echo

# Field reads happen once, after the object guard, so a malformed report never
# reaches jq again and never trips the never-fail contract.
if ! jq -e 'type == "object"' "$report" > /dev/null 2>&1; then
  # Scalpel 0.4.1 onwards writes a report on every path it can take, including
  # the skip paths and a missing base branch, so an absent or unreadable report
  # is no longer one of its outcomes. What is left is a Maven failure before the
  # session started. Check the Generate step when the run is red.
  echo "There is no usable report here, so nothing was analysed."
  echo
  echo "Every Scalpel outcome writes a report on the pinned version, including"
  echo "the skip paths, so this usually means the Maven session died before"
  echo "Scalpel ran. Check the Generate step in that case. A file corrupted on"
  echo "upload produces the same empty state."

else
  status=$(jq -r '.status // ""' "$report")
  full_triggered=$(jq -r '.fullBuildTriggered // false' "$report")
  version=$(jq -r '.version // ""' "$report")
  scalpel_version=$(jq -r '.scalpelVersion // ""' "$report")
  counts=""

  if [ -n "$status" ]; then
    # The skip shape: Scalpel analysed the change and declined to project a
    # build set. Schema 2 status reports carry the decision fields, so the
    # branch can say what happened and name the file responsible.
    reason=$(jq -r '.reason // "not recorded"' "$report")
    echo "Scalpel skipped the analysis, so there is no build set to project."
    echo
    echo "- status: \`${status//\`/}\`"
    echo "- reason: \`${reason//\`/}\`"
    trigger=$(jq -r '.triggerFile // ""' "$report")
    if [ -n "$trigger" ]; then
      echo "- trigger file: \`${trigger//\`/}\`"
    fi
    echo
    if [ "$status" = "failed" ]; then
      # Change detection did not run, so there is nothing to project and no
      # full-build claim to make. Point at the step that owns the cause.
      echo "Change detection did not run, so there is no projection at all."
      echo "See the Generate step, which owns the cause of this status."
    else
      echo "The projection for this outcome is a **full build**: a changed file"
      echo "matched \`scalpel.disableTriggers\` or \`scalpel.fullBuildTriggers\`, or"
      echo "\`scalpel.excludePaths\` removed every changed file. The reason above"
      echo "says which. This is how the baseline in the README counts these runs."
    fi

  elif [ "$full_triggered" = "true" ]; then
    # Not the skip path: this is writeFullBuildReport's shape, produced when a
    # changed file matches scalpel.fullBuildTriggers. Unreachable today because
    # this repo's disableTriggers subsumes the default fullBuildTriggers list
    # and is checked first, but one maven.config edit away: adding pom.xml to
    # scalpel.fullBuildTriggers would route here. It is a live outcome, not
    # future-proofing, so it says a true thing instead of printing a table.
    trigger=$(jq -r '.triggerFile // "not recorded"' "$report")
    echo "A changed file matched a full-build trigger, so the projection is a"
    echo "**full build** with no reduced build set. Trigger: \`${trigger//\`/}\`."

  elif [ "$version" != "2" ]; then
    echo "The report was written by Scalpel \`${scalpel_version//\`/}\` with schema"
    echo "\`${version//\`/}\`, but this summary reads schema 2, so no numbers were"
    echo "attempted."
    echo
    echo "This means the pin in \`.mvn/extensions.xml\` moved to a version the"
    echo "summary has not been taught. Check it against the schema described in"
    echo "\`.github/workflows/README.md\` and update this script to match."

  elif ! counts=$(jq -r '
      if ((.affectedModules | type) == "array")
          and ((.skippedModules | type) == "array")
          and (.reactorModuleCount >= .buildSetSize)
          and ((.skippedModules | length)
              == ((.reactorModuleCount | floor) - (.buildSetSize | floor)))
          and ([.excludedUpstreamCount, .buildSetSize, .reactorModuleCount, .testedModulesCount]
              | all(type == "number" and . >= 0))
      then [(.affectedModules | length),
            (.skippedModules | length),
            (.excludedUpstreamCount | floor),
            (.buildSetSize | floor),
            (.reactorModuleCount | floor),
            (.testedModulesCount | floor),
            ((.reactorModuleCount | floor) - (.buildSetSize | floor))] | @tsv
      else empty end' "$report" 2>/dev/null) || [ -z "$counts" ]; then
    # The version is right but the counts are missing, malformed, negative, or
    # mutually inconsistent. The shipped schema declares all four counts
    # optional, so a producer that emits nulls is a schema-legal state this
    # script must refuse rather than read as zeros; that is a report bug, not
    # a moved pin.
    echo "The report declares schema 2, which this summary reads, but its counts"
    echo "are missing, malformed, negative, or do not add up, so no numbers"
    echo "were attempted."
    echo
    echo "That is a report bug in the Scalpel version pinned in"
    echo "\`.mvn/extensions.xml\` and is worth reporting upstream."
  else
    IFS=$'\t' read -r affected skipped upstream build_set reactor tested not_built \
      <<<"$counts"

    echo "| | modules |"
    echo "| --- | ---: |"
    echo "| \`affectedModules\`, listed in the report | $affected |"
    echo "| \`excludedUpstreamCount\`, omitted from the report but still built | $upstream |"
    echo "| build set, \`buildSetSize\` | $build_set |"
    echo "| \`testedModulesCount\`, modules whose tests would run | $tested |"
    echo "| \`skippedModules\`, the actual saving | $skipped |"
    echo "| reactor, \`reactorModuleCount\` | $reactor |"
    echo "| projected modules not built | $not_built |"
    echo
    echo "The build set is \`affectedModules + excludedUpstreamCount\`, upstream"
    echo "prerequisites are dropped from the report and not from the build, and"
    echo "\`skippedModules\` is the part a trimming build would not touch. The"
    echo "decision is anchored by \`decisionId\`, \`mergeBaseId\`, \`headId\` and"
    echo "\`configFingerprint\` in the JSON, so two reports can be compared without"
    echo "consulting git history."
  fi
fi

echo
echo "See \`.github/workflows/README.md\`, \"Reading the Scalpel report\"."
