#!/usr/bin/env bash
# Lists the examples that verify-examples.yaml runs, as a JSON matrix on stdout:
#   [{"name": "per-resource-authorization", "timeout": 20, "build": false, "variant": ""}, ...]
#
# An example is run when examples/<name>/ has an executable demo.sh and a Compose file. Optional
# settings come from examples/<name>/ci.env (KEY=value lines, # comments):
#   CI_ENABLED=false       skip the example in CI (say why in a comment)
#   CI_TIMEOUT_MINUTES=N   job timeout, default 20
#   CI_NEEDS_BUILD=true    set up a JDK and the Maven cache, then run the example's build.sh, which
#                          builds its Java parts from this checkout (snapshots are not published)
#   CI_MATRIX=a b c        one job per value, each running demo.sh with CI_VARIANT set to it (for an
#                          example too long for one job); the job name gets the value appended
#   CI_SELF_MANAGED=true   demo.sh starts and stops its own containers (for an example that must
#                          prepare something first); the runner only checks the images and cleans up
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
  build=false
  matrix=""
  managed=false
  if [ -f "$example/ci.env" ]; then
    value() { sed -n "s/^$1=//p" "$example/ci.env" | tail -1; }
    enabled=$(value CI_ENABLED); enabled=${enabled:-true}
    timeout=$(value CI_TIMEOUT_MINUTES); timeout=${timeout:-20}
    build=$(value CI_NEEDS_BUILD); build=${build:-false}
    matrix=$(value CI_MATRIX)
    managed=$(value CI_SELF_MANAGED); managed=${managed:-false}
  fi
  if [ "$managed" != true ] && [ "$managed" != false ]; then
    echo "::error::$name: CI_SELF_MANAGED must be true or false, got '$managed'" >&2
    exit 1
  fi
  if [ "$build" != true ] && [ "$build" != false ]; then
    echo "::error::$name: CI_NEEDS_BUILD must be true or false, got '$build'" >&2
    exit 1
  fi
  if [ "$build" = true ] && [ ! -x "$example/build.sh" ]; then
    echo "::error::$name: CI_NEEDS_BUILD=true needs an executable build.sh" >&2
    exit 1
  fi
  if ! [[ "$timeout" =~ ^[0-9]+$ ]]; then
    echo "::error::$name: CI_TIMEOUT_MINUTES must be a number, got '$timeout'" >&2
    exit 1
  fi
  if [ "$enabled" = false ]; then
    echo "$name: CI_ENABLED=false; skipped" >&2
    continue
  fi
  if [ -n "$matrix" ] && ! [[ "$matrix" =~ ^[a-z0-9-]+( [a-z0-9-]+)*$ ]]; then
    echo "::error::$name: CI_MATRIX must be space-separated values of [a-z0-9-], got '$matrix'" >&2
    exit 1
  fi
  # One entry per CI_MATRIX value, or a single entry with no variant
  for variant in ${matrix:-<none>}; do
    [ "$variant" = "<none>" ] && variant=""
    entries+=("$(jq -cn --arg n "$name" --argjson t "$timeout" --argjson b "$build" --arg v "$variant" \
      --argjson m "$managed" '{name: $n, timeout: $t, build: $b, variant: $v, selfManaged: $m}')")
  done
done

if [ ${#entries[@]} -eq 0 ]; then
  echo '[]'
else
  printf '%s\n' "${entries[@]}" | jq -cs .
fi
