#!/usr/bin/env bash
# Tests for shard-examples.sh. Run: bash .github/scripts/shard-examples.test.sh
set -uo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
discover="$dir/discover-examples.sh"
shard="$dir/shard-examples.sh"
failures=0

# assert <description> <expected> <actual>
assert() {
  if [[ "$3" == "$2" ]]; then
    echo "ok    $1"
  else
    echo "FAIL  $1"; echo "      expected: $2"; echo "      actual:   $3"
    failures=$((failures + 1))
  fi
}

# --- empty input -> []
assert "empty list -> []" '[]' "$(echo '[]' | bash "$shard")"

# --- invalid shard counts fail fast
echo '[]' | bash "$shard" 0 3 >/dev/null 2>&1
assert "zero compose-shards exits 1" 1 "$?"
echo '[]' | bash "$shard" 3 abc >/dev/null 2>&1
assert "non-numeric build-shards exits 1" 1 "$?"

# --- classes stay separated: platform (compose=false), compose (build=false), build (build=true)
sample='[
  {"name":"op","timeout":45,"build":true,"variant":"","selfManaged":true,"releasedImages":"","mutableImage":false,"compose":false,"toolchains":""},
  {"name":"c-heavy","timeout":30,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""},
  {"name":"c-med","timeout":25,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""},
  {"name":"c-light","timeout":20,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""},
  {"name":"b-heavy","timeout":40,"build":true,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":true,"compose":true,"toolchains":"go node"},
  {"name":"b-light","timeout":20,"build":true,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":"python go"}
]'

out=$(echo "$sample" | bash "$shard" 2 2)
assert "shard names follow class separation" \
  '["platform-op","compose-1","compose-2","build-1","build-2"]' \
  "$(jq -c 'map(.name)' <<< "$out")"

# LPT bin packing: c-heavy (30) -> compose-1, c-med (25) -> compose-2, c-light (20) -> compose-2 (load 45 vs 30)
assert "compose shards balance by timeout" \
  '[["c-heavy"],["c-med","c-light"]]' \
  "$(jq -c '[.[] | select(.name | startswith("compose-")) | .examples | map(.name)]' <<< "$out")"

assert "compose shards never set build=true" \
  'false' \
  "$(jq -c '[.[] | select(.name | startswith("compose-")) | .build] | any' <<< "$out")"

assert "build shards aggregate mutableImage and toolchains per shard" \
  '[{"name":"build-1","mutableImage":true,"toolchains":"go node"},{"name":"build-2","mutableImage":false,"toolchains":"go python"}]' \
  "$(jq -c '[.[] | select(.name | startswith("build-")) | {name, mutableImage, toolchains}]' <<< "$out")"

# --- single shard merges toolchains and deduplicates, caps timeout at 60
single=$(echo "$sample" | bash "$shard" 1 1)
assert "single build shard merges toolchains and caps timeout at 60" \
  '{"name":"build-1","timeout":60,"mutableImage":true,"toolchains":"go node python"}' \
  "$(jq -c '.[] | select(.name == "build-1") | {name, timeout, mutableImage, toolchains}' <<< "$single")"

# --- fewer examples than shard count never creates empty shards
few='[{"name":"only","timeout":20,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""}]'
assert "no empty shards when examples < shard count" \
  '["compose-1"]' \
  "$(echo "$few" | bash "$shard" 3 3 | jq -c 'map(.name)')"

# --- single example with timeout > 60 keeps its timeout
long_ex='[{"name":"huge","timeout":75,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""}]'
assert "single example timeout > 60 is preserved" \
  '75' \
  "$(echo "$long_ex" | bash "$shard" 3 3 | jq -c '.[0].timeout')"

# --- end-to-end against the real examples/ directory: every discovered example appears exactly once
repo_root="$(cd "$dir/../.." && pwd)"
discovered=$(bash "$discover" "$repo_root/examples")
sharded=$(echo "$discovered" | bash "$shard" 3 3)
assert "every real example appears exactly once across shards" \
  "$(jq -c 'map("\(.name):\(.variant)") | sort' <<< "$discovered")" \
  "$(jq -c '[.[].examples[] | "\(.name):\(.variant)"] | sort' <<< "$sharded")"

assert "real examples pack into at most 8 shards" \
  'true' \
  "$(jq -c 'length <= 8' <<< "$sharded")"

# --- run-example-shard.sh: isolation, repeat demo.sh, stale-image check, continues past failure
runner="$dir/run-example-shard.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/bin" "$work/repo/examples/ex-a" "$work/repo/examples/ex-b" "$work/repo/examples/ex-bad" "$work/tmp"

# Stub docker: handles compose config/up/down/logs and prune commands
cat > "$work/bin/docker" << 'EOF'
#!/usr/bin/env bash
if [ "$1" = "compose" ]; then
  case "$2" in
    config)
      if [ "${3:-}" = "--images" ]; then
        cat "${PWD}/stub-images.txt"
      else
        echo '{"services":{}}'
      fi
      ;;
    up|down) exit 0 ;;
    logs) echo "stub-compose-log-for-$(basename "$PWD")" ;;
  esac
  exit 0
fi
exit 0
EOF
chmod +x "$work/bin/docker"

# ex-a sets a custom env var in ci.env and records how many times demo.sh ran
printf 'CUSTOM_LEAK_TEST=from-a\nCI_TIMEOUT_MINUTES=5\n' > "$work/repo/examples/ex-a/ci.env"
printf 'apicurio/apicurio-registry:abc123\n' > "$work/repo/examples/ex-a/stub-images.txt"
cat > "$work/repo/examples/ex-a/demo.sh" << 'EOF'
#!/usr/bin/env bash
[ "${CUSTOM_LEAK_TEST:-}" = "from-a" ] || exit 91
[ "${REGISTRY_IMAGE:-}" = "apicurio/apicurio-registry:abc123" ] || exit 92
echo "run" >> "$PWD/runs.log"
EOF
chmod +x "$work/repo/examples/ex-a/demo.sh"

# ex-bad uses a stale image tag and must fail without preventing ex-b from running
printf 'apicurio/apicurio-registry:latest-snapshot\n' > "$work/repo/examples/ex-bad/stub-images.txt"
printf '#!/usr/bin/env bash\nexit 0\n' > "$work/repo/examples/ex-bad/demo.sh"
chmod +x "$work/repo/examples/ex-bad/demo.sh"

# ex-b asserts CUSTOM_LEAK_TEST did NOT leak from ex-a
printf 'apicurio/apicurio-registry:abc123\n' > "$work/repo/examples/ex-b/stub-images.txt"
cat > "$work/repo/examples/ex-b/demo.sh" << 'EOF'
#!/usr/bin/env bash
[ -z "${CUSTOM_LEAK_TEST:-}" ] || exit 93
[ "${CI_VARIANT:-}" = "pg" ] || exit 94
echo "run" >> "$PWD/runs.log"
EOF
chmod +x "$work/repo/examples/ex-b/demo.sh"

shard_json='[
  {"name":"ex-a","timeout":5,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""},
  {"name":"ex-bad","timeout":5,"build":false,"variant":"","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""},
  {"name":"ex-b","timeout":5,"build":false,"variant":"pg","selfManaged":false,"releasedImages":"","mutableImage":false,"compose":true,"toolchains":""}
]'

summary_file="$work/tmp/summary.md"
PATH="$work/bin:$PATH" \
  SHARD_NAME="test-shard" \
  SHARD_EXAMPLES="$shard_json" \
  IMAGE_TAG="abc123" \
  GITHUB_WORKSPACE="$work/repo" \
  RUNNER_TEMP="$work/tmp" \
  GITHUB_STEP_SUMMARY="$summary_file" \
  bash "$runner" >/dev/null 2>&1
rc=$?

assert "shard exits non-zero when an example has a stale image" 1 "$rc"
assert "ex-a ran demo.sh twice" "2" "$(wc -l < "$work/repo/examples/ex-a/runs.log" | tr -d ' ')"
assert "ex-b still ran twice after ex-bad failed and saw no leaked ci.env" "2" "$(wc -l < "$work/repo/examples/ex-b/runs.log" | tr -d ' ')"
assert "ex-bad captured compose logs on failure" "stub-compose-log-for-ex-bad" "$(cat "$work/tmp/example-logs/ex-bad.log")"
# shellcheck disable=SC2016 # literal markdown backticks in step summary
assert "step summary records pass and fail rows" "true" "$(grep -q '`ex-a` | PASS' "$summary_file" && grep -q '`ex-bad` | \*\*FAIL\*\*' "$summary_file" && grep -q '`ex-b (pg)` | PASS' "$summary_file" && echo true || echo false)"

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "All shard-examples tests passed."
