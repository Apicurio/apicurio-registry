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

# The value this repository pins in .mvn/maven.config for the empty-reactor
# reasons below. Read by scalpel-summary.test.sh, which cross-checks it against
# the file itself, so the pin cannot move without this script's claims moving
# with it.
build_all_if_no_changes_pin="true"

# Report strings are printed inside markdown code spans and list items, so a
# backtick would end the span early and a newline would end the item. Flatten
# both rather than trusting a field that comes from a file path or a git ref.
# Only those two are handled: nothing here is printed into a table cell, so a
# pipe needs no escaping, and GitHub sanitizes HTML in a job summary, which
# caps the worst remaining case at a cosmetic one.
sanitize() {
  local s=${1//\`/}
  s=${s//$'\r'/ }
  printf '%s' "${s//$'\n'/ }"
}

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
  version=$(jq -r '.version // ""' "$report")

  # The schema check comes before any field is interpreted. A future schema is
  # free to rename or repurpose status, reason and fullBuildTriggered, so a
  # branch that reads them first would describe an unknown report confidently
  # and wrongly. Refusing on the version is the only claim safe to make here.
  if [ "$version" != "2" ]; then
    scalpel_version=$(jq -r '.scalpelVersion // ""' "$report")
    echo "The report was written by Scalpel \`$(sanitize "$scalpel_version")\` with schema"
    echo "\`$(sanitize "$version")\`, but this summary reads schema 2, so no numbers were"
    echo "attempted."
    echo
    echo "This means the pin in \`.mvn/extensions.xml\` moved to a version the"
    echo "summary has not been taught. Check it against the schema described in"
    echo "\`.github/workflows/README.md\` and update this script to match."

  else
    status=$(jq -r '.status // ""' "$report")
    full_triggered=$(jq -r '.fullBuildTriggered // false' "$report")

    if [ -n "$status" ]; then
      # The skip shape: Scalpel analysed the change and declined to project a
      # build set. Schema 2 status reports carry the decision fields, so the
      # branch can say what happened and name the file responsible.
      reason=$(jq -r '.reason // "not recorded"' "$report")
      # A failed report is not a decision, so leading it with the skip wording
      # would state something the arm below immediately takes back.
      if [ "$status" = "failed" ]; then
        echo "Scalpel did not complete its analysis, so there is no build set to"
        echo "project."
      else
        echo "Scalpel skipped the analysis, so there is no build set to project."
      fi
      echo
      echo "- status: \`$(sanitize "$status")\`"
      echo "- reason: \`$(sanitize "$reason")\`"
      trigger=$(jq -r '.triggerFile // ""' "$report")
      if [ -n "$trigger" ]; then
        echo "- trigger file: \`$(sanitize "$trigger")\`"
      fi
      echo
      # Each arm matches the whole reason, as the tail of the joined key, rather
      # than a fragment of it. Every arm below was read from the five
      # writeStatusReport call sites in the pinned ScalpelLifecycleParticipant, which are the only writers of this file's
      # status and reason. Two of them project an empty build and the rest leave
      # the reactor whole, so a pattern loose enough to catch an unintended
      # reason is a pattern that states the reverse of the truth. An
      # unrecognised reason is refused rather than guessed, which is why the
      # default arm makes no build claim at all.
      #
      # Three reasons Scalpel can log are deliberately absent, because it routes
      # them to target/scalpel-shadow.json rather than here: "no modules
      # affected by changes", "no modules match includePaths filters" and
      # "disabled by -pl project selection". A run that hits one of those writes
      # a full report to this file with no status field, so it lands in the
      # counts branch below instead of any arm here.
      case "$status:$reason" in
        failed:*)
          # Detection broke before either trim site could run, so the reactor is
          # whole. The projection is not the interesting part of this outcome;
          # the cause is, and the Generate step owns it.
          echo "Change detection did not run, so Scalpel trimmed nothing and the"
          echo "projection is a **full build**. That is the failure behaviour"
          echo "rather than a decision, so see the Generate step for the cause."
          ;;
        *:"no changes detected" | \
        *:"all changed files excluded by path filters")
          # The only two reasons that reach trimReactorToEmpty, and the only
          # two whose call sites consult scalpel.buildAllIfNoChanges. The echo
          # block carries the user-facing story; the pin it stands on lives in
          # build_all_if_no_changes_pin above and is guarded by the test.
          echo "The projection is a **full build**: \`scalpel.buildAllIfNoChanges\`"
          echo "is pinned to \`$build_all_if_no_changes_pin\` in \`.mvn/maven.config\`,"
          echo "so a trimming build runs every module on this outcome rather"
          echo "than none."
          echo
          echo "With the Scalpel default of \`false\` the reactor would be trimmed"
          echo "to empty here, and on the pinned version Maven then fails with"
          echo "\`NoGoalSpecifiedException\` rather than building nothing cleanly."
          echo "Scalpel 0.4.0 and earlier built every module on this outcome as"
          echo "well, so the empty build this reason's wording suggests has"
          echo "never been a working outcome on any pin."
          ;;
        *:"disabled by disableTriggers match" | \
        *:"disabled by disableOnBranch" | \
        *:"disabled by disableOnBaseBranch")
          echo "Configuration told Scalpel to stand down, so it trimmed nothing and"
          echo "the projection is a **full build**. The reason above names which"
          echo "setting did it. Only \`disableTriggers\` is set in"
          echo "\`.mvn/maven.config\`; the other two are Scalpel's own defaults,"
          echo "which this repository does not override."
          ;;
        *:"not a git repository" | \
        *:"no base branch configured")
          # Environmental, not configured: Scalpel never got as far as comparing
          # anything, so it returns without touching the reactor.
          echo "Scalpel could not start the comparison, so it trimmed nothing and the"
          echo "projection is a **full build**."
          echo
          echo "The CI job sets no \`scalpel.baseBranch\`. It runs only on"
          echo "\`pull_request\` events and checks out full history, so Scalpel derives"
          echo "the base branch from \`GITHUB_BASE_REF\`. Seeing this in CI means that"
          echo "derivation or the checkout depth broke. A local run reaches it"
          echo "normally, because neither input exists outside CI."
          ;;
        *)
          # A reason this script has not been taught. Naming a projection here
          # would be a guess, and the arms above show the guess has no safe
          # default: the same status of "skipped" covers both an empty build and
          # a full one.
          echo "Scalpel skipped for a reason this summary does not recognise, so no"
          echo "build was projected. The reason above is reproduced verbatim."
          echo
          echo "Reasons are enumerated from the Scalpel version pinned in"
          echo "\`.mvn/extensions.xml\`, so this usually means the pin moved. Add the"
          echo "new reason to the matching arm in this script."
          ;;
      esac

    elif [ "$full_triggered" = "true" ]; then
      # Not the skip path: this is writeFullBuildReport's shape, produced when a
      # changed file matches scalpel.fullBuildTriggers. Unreachable today because
      # this repo's disableTriggers subsumes the default fullBuildTriggers list
      # and is checked first, but one maven.config edit away: adding pom.xml to
      # scalpel.fullBuildTriggers would route here. It is a live outcome, not
      # future-proofing, so it says a true thing instead of printing a table.
      trigger=$(jq -r '.triggerFile // "not recorded"' "$report")
      echo "A changed file matched a full-build trigger, so the projection is a"
      echo "**full build** with no reduced build set. Trigger: \`$(sanitize "$trigger")\`."

    else
      # Counts are floored once and reused. A null or non-numeric count makes the
      # floor bindings fail, jq exits without writing, and the empty result lands
      # in the refusal branch below, which is where a malformed report belongs.
      #
      # skippedModules defaults to [] because the schema documents it as present
      # only when at least one module was skipped. A run that skips nothing omits
      # it, and reading that as a malformed report accused the producer of a bug
      # on an outcome it reports correctly.
      counts=$(jq -r '
        (.skippedModules // []) as $skipped
        | (.excludedUpstreamCount | floor) as $upstream
        | (.buildSetSize | floor) as $build_set
        | (.reactorModuleCount | floor) as $reactor
        | (.testedModulesCount | floor) as $tested
        | if ((.affectedModules | type) == "array")
              and (($skipped | type) == "array")
              and ([.excludedUpstreamCount, .buildSetSize,
                    .reactorModuleCount, .testedModulesCount]
                  | all(type == "number" and . >= 0))
              and ($build_set == ((.affectedModules | length) + $upstream))
              and ($tested <= $reactor)
              and (($skipped | length) == ($reactor - $build_set))
          then [(.affectedModules | length), ($skipped | length),
                $upstream, $build_set, $reactor, $tested] | @tsv
          else empty end' "$report" 2>/dev/null) || true

      if [ -z "$counts" ]; then
        # The version is right but the counts are missing, malformed, negative, or
        # mutually inconsistent. Only excludedUpstreamCount is required by the
        # shipped schema; buildSetSize, reactorModuleCount and testedModulesCount
        # are optional, so a producer may legally emit a version-2 report without
        # them. Without them there is no table to draw, and a null read as zero
        # would claim the whole reactor as a saving.
        echo "The report declares schema 2, which this summary reads, but its counts"
        echo "are missing, malformed, negative, or do not add up, so no numbers"
        echo "were attempted."
        echo
        echo "Only \`excludedUpstreamCount\` is required by the shipped schema, so a"
        echo "report that simply omits the optional counts is legal and lands here"
        echo "too. Counts that are present but malformed, negative or mutually"
        echo "inconsistent are the case worth reporting upstream against the"
        echo "version pinned in \`.mvn/extensions.xml\`."
      else
        IFS=$'\t' read -r affected skipped upstream build_set reactor tested <<<"$counts"

        if [ "$build_set" = 0 ]; then
          # A decision report with a zero build set is the no-affected-modules
          # family: Scalpel logged the reason to its shadow output and wrote an
          # ordinary report whose projection is an empty reactor. Drawing the
          # table would publish skippedModules = reactor as a saving, but a
          # trimming build does not perform this projection on Scalpel 0.4.2,
          # whatever buildAllIfNoChanges is set to (verified with the flag
          # both ways): the reactor stays whole, so the build that runs is a
          # full one. The string comparison rather than -eq is deliberate:
          # the counts come from producer-controlled text, and an exponent
          # rendering that [ -eq ] cannot parse would write to stderr and
          # break the never-fail contract above.
          echo "The report projects an **empty build set**: \`buildSetSize\` is 0"
          echo "and \`skippedModules\` names the whole reactor, so no table was"
          echo "drawn for it."
          echo
          echo "A trimming build on Scalpel 0.4.2 does not apply this"
          echo "projection, whatever \`scalpel.buildAllIfNoChanges\` is set to."
          echo "Scalpel routes the no-affected-modules decision to its shadow"
          echo "output and leaves the reactor whole, so the build that runs is"
          echo "a **full build** and the reactor-wide saving this shape"
          echo "suggests is not available from trimming. This repository sets"
          echo "neither \`scalpel.includePaths\` nor a \`-pl\` selection, so the"
          echo "no-affected-modules decision is the only producer of this shape"
          echo "here: changes no module in this reactor owns land in it, which"
          echo "means root-level files no pom names and paths under modules"
          echo "outside the default reactor such as \`operator\` and \`mcp\`."
        else
          echo "| | modules |"
          echo "| --- | ---: |"
          echo "| \`affectedModules\`, listed in the report | $affected |"
          echo "| \`excludedUpstreamCount\`, omitted from the report but still built | $upstream |"
          echo "| build set, \`buildSetSize\` | $build_set |"
          echo "| \`testedModulesCount\`, modules whose tests would run | $tested |"
          echo "| \`skippedModules\`, the modules a trimming build would not touch | $skipped |"
          echo "| reactor, \`reactorModuleCount\` | $reactor |"
          echo
          echo "The build set is \`affectedModules + excludedUpstreamCount\`, upstream"
          echo "prerequisites are dropped from the report and not from the build, and"
          echo "\`skippedModules\` is the saving, which is also the reactor less the"
          echo "build set. The decision is anchored by \`decisionId\`, \`mergeBaseId\`,"
          echo "\`headId\` and \`configFingerprint\` in the JSON, so two reports can be"
          echo "compared without consulting git history."
        fi
      fi
    fi
  fi
fi

echo
echo "See \`.github/workflows/README.md\`, \"Reading the Scalpel report\"."
