#!/usr/bin/env bash
# Seeds sample data and verifies <feature> against the running example.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
set -euo pipefail

API=${API:-http://localhost:8081/apis}
V3="$API/registry/v3"
failures=0

# expect <description> <expected status> <curl args...>: checks the HTTP status of a request
expect() {
  local description=$1 expected=$2
  shift 2
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' "$@")
  if [[ "$status" == "$expected" ]]; then
    printf '  ok    %-60s %s\n' "$description" "$status"
  else
    printf '  FAIL  %-60s %s (expected %s)\n' "$description" "$status" "$expected"
    failures=$((failures + 1))
  fi
}

# expect_value <description> <expected value> <jq filter> <curl args...>: checks a response value
expect_value() {
  local description=$1 expected=$2 filter=$3
  shift 3
  local actual
  actual=$(curl -s "$@" | jq -r "$filter" 2>/dev/null || echo "<invalid response>")
  if [[ "$actual" == "$expected" ]]; then
    printf '  ok    %-60s %s\n' "$description" "$actual"
  else
    printf '  FAIL  %-60s %s (expected %s)\n' "$description" "$actual" "$expected"
    failures=$((failures + 1))
  fi
}

# wait_for <name> <url>: polls every 2s, gives up after WAIT_TIMEOUT seconds (default 180)
wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

# create_artifact <group> <artifact> <type> <content>: idempotent, so the script can run twice
create_artifact() {
  jq -n --arg a "$2" --arg t "$3" --arg c "$4" \
    '{artifactId: $a, artifactType: $t, firstVersion: {content: {content: $c, contentType: "application/json"}}}' \
    | curl -s -o /dev/null -X POST "$V3/groups/$1/artifacts?ifExists=FIND_OR_CREATE_VERSION" \
        -H 'Content-Type: application/json' -d @-
}

echo "Waiting for Registry..."
wait_for Registry "$V3/system/info"

echo "Seeding data..."
# <Replace with the example's data>
create_artifact example greeting JSON '{"type": "object", "properties": {"text": {"type": "string"}}}'

echo "<First idea>:"
# <Replace with checks for the feature, including the failure cases>
expect "reads example/greeting" 200 "$V3/groups/example/artifacts/greeting"
expect "reads a missing artifact" 404 "$V3/groups/example/artifacts/missing"
expect_value "example/greeting has type JSON" JSON '.artifactType' "$V3/groups/example/artifacts/greeting"

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
