#!/usr/bin/env bash
# Tests for discover-examples.sh. Run: bash .github/scripts/discover-examples.test.sh
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/discover-examples.sh"
failures=0
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# example <dir> <name> [compose-file] [demo-mode]: creates examples/<name> with a demo.sh
example() {
  mkdir -p "$1/$2"
  printf '#!/usr/bin/env bash\n' > "$1/$2/demo.sh"
  chmod "${4:-755}" "$1/$2/demo.sh"
  [ -n "${3:-}" ] && touch "$1/$2/$3"
  return 0
}

# assert <description> <expected> <actual>
assert() {
  if [[ "$3" == "$2" ]]; then
    echo "ok    $1"
  else
    echo "FAIL  $1"; echo "      expected: $2"; echo "      actual:   $3"
    failures=$((failures + 1))
  fi
}

# --- an empty directory gives an empty matrix
mkdir -p "$work/empty"
assert "no examples -> []" '[]' "$(bash "$script" "$work/empty" 2>/dev/null)"

# --- every Compose file name is recognized; default timeout is 20; sorted by name
d="$work/names"
example "$d" a-compose compose.yaml
example "$d" b-compose-yml compose.yml
example "$d" c-docker-yaml docker-compose.yaml
example "$d" d-docker-yml docker-compose.yml
assert "all Compose file names, default timeout" \
  '[{"name":"a-compose","timeout":20,"build":false,"variant":""},{"name":"b-compose-yml","timeout":20,"build":false,"variant":""},{"name":"c-docker-yaml","timeout":20,"build":false,"variant":""},{"name":"d-docker-yml","timeout":20,"build":false,"variant":""}]' \
  "$(bash "$script" "$d" 2>/dev/null)"

# --- a directory without demo.sh is not an example; demo.sh without Compose is skipped with a warning
d="$work/skips"
mkdir -p "$d/no-demo"; touch "$d/no-demo/docker-compose.yml"
example "$d" no-compose
example "$d" runnable docker-compose.yml
assert "only runnable examples are listed" '[{"name":"runnable","timeout":20,"build":false,"variant":""}]' "$(bash "$script" "$d" 2>/dev/null)"
assert "missing Compose file is reported" "::warning::no-compose has demo.sh but no Compose file; skipped" \
  "$(bash "$script" "$d" 2>&1 >/dev/null)"

# --- ci.env: timeout override, CI_ENABLED=false, last value wins, other keys ignored here
d="$work/cienv"
example "$d" slow docker-compose.yml
printf '# needs the model download\nCI_TIMEOUT_MINUTES=10\nCI_TIMEOUT_MINUTES=35\nSKIP_LLM=1\n' > "$d/slow/ci.env"
example "$d" off docker-compose.yml
printf '# needs kind\nCI_ENABLED=false\n' > "$d/off/ci.env"
assert "ci.env timeout and CI_ENABLED" '[{"name":"slow","timeout":35,"build":false,"variant":""}]' "$(bash "$script" "$d" 2>/dev/null)"

# --- CI_NEEDS_BUILD needs an executable build.sh
d="$work/build"
example "$d" java docker-compose.yml
echo 'CI_NEEDS_BUILD=true' > "$d/java/ci.env"
printf '#!/usr/bin/env bash\n' > "$d/java/build.sh"; chmod 755 "$d/java/build.sh"
assert "CI_NEEDS_BUILD=true with build.sh" '[{"name":"java","timeout":20,"build":true,"variant":""}]' "$(bash "$script" "$d" 2>/dev/null)"
chmod 644 "$d/java/build.sh"
bash "$script" "$d" > /dev/null 2>&1
assert "CI_NEEDS_BUILD=true without an executable build.sh exits 1" 1 "$?"
echo 'CI_NEEDS_BUILD=yes' > "$d/java/ci.env"
bash "$script" "$d" > /dev/null 2>&1
assert "CI_NEEDS_BUILD that is not true/false exits 1" 1 "$?"

# --- CI_MATRIX: one entry per value
d="$work/matrix"
example "$d" multi docker-compose.yml
printf 'CI_MATRIX=pg kafka-sql\nCI_TIMEOUT_MINUTES=30\n' > "$d/multi/ci.env"
assert "CI_MATRIX gives one entry per value" \
  '[{"name":"multi","timeout":30,"build":false,"variant":"pg"},{"name":"multi","timeout":30,"build":false,"variant":"kafka-sql"}]' \
  "$(bash "$script" "$d" 2>/dev/null)"
echo 'CI_MATRIX=bad;value' > "$d/multi/ci.env"
bash "$script" "$d" > /dev/null 2>&1
assert "CI_MATRIX with an invalid value exits 1" 1 "$?"

# --- errors fail the discovery instead of silently dropping an example
d="$work/badtimeout"
example "$d" bad docker-compose.yml
echo 'CI_TIMEOUT_MINUTES=soon' > "$d/bad/ci.env"
bash "$script" "$d" > /dev/null 2>&1
assert "non-numeric timeout exits 1" 1 "$?"

d="$work/notexec"
example "$d" plain docker-compose.yml 644
bash "$script" "$d" > /dev/null 2>&1
assert "non-executable demo.sh exits 1" 1 "$?"

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "All discover-examples tests passed."
