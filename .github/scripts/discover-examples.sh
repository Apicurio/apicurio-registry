#!/usr/bin/env bash
# Lists the examples that verify-examples.yaml runs, as a JSON matrix on stdout:
#   [{"name": "per-resource-authorization", "timeout": 20}, ...]
#
# An example is run when examples/<name>/ has an executable demo.sh and a Compose file. Optional
# settings come from examples/<name>/ci.env (KEY=value lines, # comments):
#   CI_ENABLED=false       skip the example in CI (say why in a comment)
#   CI_TIMEOUT_MINUTES=N   job timeout, default 20
# Every other line is exported to `docker compose` and demo.sh (see verify-examples.yaml).
#
# Usage: .github/scripts/discover-examples.sh [examples-dir]
set -euo pipefail

dir=${1:-examples}
entries=()

for demo in "$dir"/*/demo.sh; do
  [ -e "$demo" ] || continue
  example=$(dirname "$demo")
  name=$(basename "$example")

  compose=
  for f in compose.yaml compose.yml docker-compose.yaml docker-compose.yml; do
    [ -f "$example/$f" ] && compose=$f && break
  done
  if [ -z "$compose" ]; then
    echo "::warning::$name has demo.sh but no Compose file; skipped" >&2
    continue
  fi
  if [ ! -x "$demo" ]; then
    echo "::error::$demo is not executable (git update-index --chmod=+x)" >&2
    exit 1
  fi

  enabled=true
  timeout=20
  if [ -f "$example/ci.env" ]; then
    value() { sed -n "s/^$1=//p" "$example/ci.env" | tail -1; }
    enabled=$(value CI_ENABLED); enabled=${enabled:-true}
    timeout=$(value CI_TIMEOUT_MINUTES); timeout=${timeout:-20}
  fi
  if ! [[ "$timeout" =~ ^[0-9]+$ ]]; then
    echo "::error::$name: CI_TIMEOUT_MINUTES must be a number, got '$timeout'" >&2
    exit 1
  fi
  if [ "$enabled" = false ]; then
    echo "$name: CI_ENABLED=false; skipped" >&2
    continue
  fi
  entries+=("$(jq -cn --arg n "$name" --argjson t "$timeout" '{name: $n, timeout: $t}')")
done

if [ ${#entries[@]} -eq 0 ]; then
  echo '[]'
else
  printf '%s\n' "${entries[@]}" | jq -cs .
fi
