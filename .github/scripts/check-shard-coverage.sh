#!/usr/bin/env bash
set -euo pipefail

# Validates that every test class in the app module is claimed by exactly one
# unit-test shard.  Run from the repository root.
#
# Exit 0  – all classes covered by exactly one shard
# Exit 1  – at least one class is uncovered or multi-covered

APP_TEST_DIR="app/src/test/java"

# ── Positive-match shards ────────────────────────────────────────────────────
# Each entry is: SHARD_NAME  PATTERN1 PATTERN2 …
# Patterns use Java-style "**" globs (translated to regex below).
SHARDS=(
  "app-rest|io.apicurio.registry.noprofile.rest.**|io.apicurio.registry.noprofile.ccompat.**"
  "app-sql|io.apicurio.registry.storage.impl.sql.**|io.apicurio.registry.storage.impl.search.**|io.apicurio.registry.storage.impl.polling.**|io.apicurio.registry.storage.impl.readonly.**|io.apicurio.registry.storage.decorator.**|io.apicurio.registry.storage.dto.**|io.apicurio.registry.event.sql.**"
  "app-kafkasql|io.apicurio.registry.storage.impl.kafkasql.**|io.apicurio.registry.event.kafkasql.**"
  "app-gitops|io.apicurio.registry.storage.impl.gitops.**"
  "app-kubernetesops|io.apicurio.registry.storage.impl.kubernetesops.**"
  "app-security|io.apicurio.registry.auth.**|io.apicurio.registry.rbac.**|io.apicurio.registry.tls.**|io.apicurio.registry.cors.**"
)

# ── app-other exclusion patterns ─────────────────────────────────────────────
# Must match the negation list in verify-unit-tests.yaml's app-other shard.
# A class matching zero positive shards AND matching one of these is UNCOVERED.
APP_OTHER_EXCLUSIONS=(
  "io.apicurio.registry.noprofile.rest.**"
  "io.apicurio.registry.noprofile.ccompat.**"
  "io.apicurio.registry.storage.**"
  "io.apicurio.registry.event.**"
  "io.apicurio.registry.auth.**"
  "io.apicurio.registry.rbac.**"
  "io.apicurio.registry.tls.**"
  "io.apicurio.registry.cors.**"
)

# ── Helpers ──────────────────────────────────────────────────────────────────

glob_to_regex() {
  # io.apicurio.registry.auth.**  →  ^io\.apicurio\.registry\.auth\..*$
  local g="$1"
  g="${g//./\\.}"       # escape dots
  g="${g//\*\*/.*}"     # ** → .*
  echo "^${g}$"
}

fqcn_from_path() {
  # app/src/test/java/io/apicurio/registry/auth/FooTest.java → io.apicurio.registry.auth.FooTest
  local p="${1#${APP_TEST_DIR}/}"
  p="${p%.java}"
  echo "${p//\//.}"
}

# ── Collect test classes ─────────────────────────────────────────────────────

mapfile -t TEST_FILES < <(find "$APP_TEST_DIR" \( -name '*Test.java' -o -name '*IT.java' \) -type f | sort)

if [[ ${#TEST_FILES[@]} -eq 0 ]]; then
  echo "ERROR: no test classes found under $APP_TEST_DIR"
  exit 1
fi

# ── Build compiled regexes per shard ─────────────────────────────────────────

declare -a SHARD_NAMES=()
declare -a SHARD_REGEXES=()

for entry in "${SHARDS[@]}"; do
  IFS='|' read -ra parts <<< "$entry"
  SHARD_NAMES+=("${parts[0]}")
  combined=""
  for pattern in "${parts[@]:1}"; do
    [[ -n "$combined" ]] && combined+="|"
    combined+="$(glob_to_regex "$pattern")"
  done
  SHARD_REGEXES+=("$combined")
done

other_exclusion_regex=""
for pattern in "${APP_OTHER_EXCLUSIONS[@]}"; do
  [[ -n "$other_exclusion_regex" ]] && other_exclusion_regex+="|"
  other_exclusion_regex+="$(glob_to_regex "$pattern")"
done

# ── Check each class ────────────────────────────────────────────────────────

errors=0
for f in "${TEST_FILES[@]}"; do
  fqcn="$(fqcn_from_path "$f")"
  matched=()
  for idx in "${!SHARD_NAMES[@]}"; do
    if [[ "$fqcn" =~ ${SHARD_REGEXES[$idx]} ]]; then
      matched+=("${SHARD_NAMES[$idx]}")
    fi
  done

  if [[ ${#matched[@]} -eq 0 ]]; then
    if [[ "$fqcn" =~ $other_exclusion_regex ]]; then
      echo "UNCOVERED: $fqcn (excluded by app-other but not claimed by any positive shard)"
      ((errors++))
    fi
    continue
  elif [[ ${#matched[@]} -gt 1 ]]; then
    echo "MULTI-MATCH: $fqcn → ${matched[*]}"
    ((errors++))
  fi
done

# ── Summary ──────────────────────────────────────────────────────────────────

echo "Checked ${#TEST_FILES[@]} test classes against ${#SHARD_NAMES[@]} shards + app-other exclusions."
if [[ $errors -gt 0 ]]; then
  echo "FAILED: $errors class(es) uncovered or multi-matched."
  exit 1
fi
echo "OK: every class is covered by exactly one shard."
