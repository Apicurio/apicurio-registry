#!/usr/bin/env bash
# Verifies HTTP caching through Varnish against the running example.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq)
# Runs the test suite in test/run-tests.sh, which exits non-zero on any failure.
set -euo pipefail
cd "$(dirname "$0")"

VARNISH_URL=${VARNISH_URL:-http://localhost:8081}

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

echo "Waiting for Registry through Varnish..."
wait_for Varnish "$VARNISH_URL/apis/registry/v3/system/info"

VARNISH_URL=$VARNISH_URL ./test/run-tests.sh
echo "All checks passed."
