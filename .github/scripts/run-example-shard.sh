#!/usr/bin/env bash
# Runs every example assigned to a shard by verify-examples.yaml.
#
# Expects:
#   SHARD_NAME       display name of this shard (e.g. compose-1)
#   SHARD_EXAMPLES   JSON array of example entries from discover-examples.sh
#   IMAGE_TAG        commit SHA tag of the built Registry images
#   RUNNER_TEMP      directory for per-example failure logs (defaults to /tmp)
#   GITHUB_WORKSPACE repository root (defaults to current working directory)
#
# Each example runs in an isolated subshell so working directories and ci.env variables
# never leak to later examples in the shard. Every example in the shard runs even when
# an earlier one fails, and a summary table is written to GITHUB_STEP_SUMMARY.
set -uo pipefail

: "${SHARD_NAME:?SHARD_NAME is required}"
: "${SHARD_EXAMPLES:?SHARD_EXAMPLES is required}"
: "${IMAGE_TAG:?IMAGE_TAG is required}"

WORKSPACE="${GITHUB_WORKSPACE:-$(pwd)}"
LOGS_DIR="${RUNNER_TEMP:-/tmp}/example-logs"
mkdir -p "$LOGS_DIR"

export REGISTRY_IMAGE="apicurio/apicurio-registry:${IMAGE_TAG}"
export REGISTRY_UI_IMAGE="apicurio/apicurio-registry-ui:${IMAGE_TAG}"
export UI_IMAGE="apicurio/apicurio-registry-ui:${IMAGE_TAG}"
export MCP_SERVER_IMAGE="apicurio/apicurio-registry-mcp-server:${IMAGE_TAG}"
export REGISTRY_MUTABLE_IMAGE="apicurio/apicurio-registry:${IMAGE_TAG}-mutable"

# Minimum free disk space (MB) on / before pruning cached third-party Docker images
# between examples in a shard.
MIN_FREE_DISK_MB="${MIN_FREE_DISK_MB:-6144}"

active_dir=""
active_slug=""
active_compose="false"
active_variant=""

dump_compose_logs() {
  local dir="$1" slug="$2" variant="$3"
  local log_file="${LOGS_DIR}/${slug}.log"
  (
    cd "$dir" || exit 1
    CI_VARIANT="$variant" COMPOSE_PROFILES="$variant" \
      docker compose logs --no-color > "$log_file" 2>&1 || true
  )
  if [ -s "$log_file" ]; then
    echo "--- last 300 lines of docker compose logs (${slug}) ---"
    tail -300 "$log_file"
  fi
}

cleanup_on_signal() {
  echo "::warning::Shard ${SHARD_NAME} interrupted while running ${active_slug:-none}"
  if [ "$active_compose" = "true" ] && [ -n "$active_dir" ] && [ -d "$active_dir" ]; then
    dump_compose_logs "$active_dir" "$active_slug" "$active_variant"
    (
      cd "$active_dir" || exit 1
      CI_VARIANT="$active_variant" COMPOSE_PROFILES="$active_variant" \
        docker compose down -v --remove-orphans || true
    )
  fi
  exit 143
}
trap cleanup_on_signal TERM INT

reclaim_disk_if_low() {
  docker container prune -f >/dev/null 2>&1 || true
  docker volume prune -f >/dev/null 2>&1 || true
  docker image prune -f >/dev/null 2>&1 || true

  local free_mb
  free_mb=$(df -Pm / | awk 'NR==2 {print $4}')
  if [ -n "$free_mb" ] && [ "$free_mb" -lt "$MIN_FREE_DISK_MB" ]; then
    echo "Low disk space (${free_mb} MB free < ${MIN_FREE_DISK_MB} MB); pruning third-party Docker images..."
    local img
    while IFS= read -r img; do
      [ -n "$img" ] || continue
      docker rmi -f "$img" >/dev/null 2>&1 || true
    done < <(docker images --format '{{.Repository}}:{{.Tag}}' \
      | grep -v '<none>' \
      | grep -v -E "^apicurio/apicurio-registry[a-z-]*:${IMAGE_TAG}(-mutable)?$" || true)
    docker builder prune -af >/dev/null 2>&1 || true
  fi
}

run_with_timeout() {
  local minutes="$1"
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout "${minutes}m" "$@"
  else
    "$@"
  fi
}

run_one_example() {
  local entry="$1"
  local name variant timeout_min build self_managed released compose
  name=$(jq -r '.name' <<< "$entry")
  variant=$(jq -r '.variant' <<< "$entry")
  timeout_min=$(jq -r '.timeout' <<< "$entry")
  build=$(jq -r '.build' <<< "$entry")
  self_managed=$(jq -r '.selfManaged' <<< "$entry")
  released=$(jq -r '.releasedImages' <<< "$entry")
  compose=$(jq -r '.compose' <<< "$entry")

  local slug="${name}${variant:+-$variant}"
  local example_dir="${WORKSPACE}/examples/${name}"

  cd "$example_dir" || return 1

  export CI_VARIANT="$variant"
  export COMPOSE_PROFILES="$variant"

  # Every line except comments and the CI_* runner settings becomes example environment.
  if [ -f ci.env ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
      [[ "$line" == CI_* ]] && continue
      export "${line?}"
    done < ci.env
  fi

  local rc=0

  if [ "$build" = "true" ]; then
    echo ">>> Building Java parts (build.sh)"
    if ! MAVEN_ARGS="-s ${WORKSPACE}/.github/ci-settings.xml --no-transfer-progress" run_with_timeout "$timeout_min" ./build.sh; then
      echo "::error::${slug}: build.sh failed"
      rc=1
    fi
  fi

  if [ "$rc" -eq 0 ] && [ "$compose" = "true" ]; then
    echo ">>> Checking that the stack runs this commit's images"
    local images stale
    images=$( (docker compose config --images; docker compose config --format json \
      | jq -r '.services[].build.args // {} | .[]') | sort -u ) || rc=$?
    if [ "$rc" -eq 0 ]; then
      echo "$images"
      if [ -n "$released" ]; then
        # shellcheck disable=SC2086 # released is intentionally space-separated image:tag entries
        stale=$(echo "$images" | grep -E '(^|/)apicurio-registry[a-z-]*:' \
          | grep -vE ":${IMAGE_TAG}(-mutable)?$" \
          | grep -vxF -f <(printf '%s\n' $released) || true)
      else
        stale=$(echo "$images" | grep -E '(^|/)apicurio-registry[a-z-]*:' \
          | grep -vE ":${IMAGE_TAG}(-mutable)?$" || true)
      fi
      if [ -n "$stale" ]; then
        echo "::error::${slug}: these images are not built from this commit; select them with REGISTRY_IMAGE, REGISTRY_UI_IMAGE, REGISTRY_MUTABLE_IMAGE or MCP_SERVER_IMAGE (see examples/TEMPLATE.md):"
        echo "$stale"
        rc=1
      fi
    fi
  fi

  if [ "$rc" -eq 0 ] && [ "$self_managed" != "true" ]; then
    echo ">>> Starting Compose stack"
    local started=false
    for attempt in 1 2 3; do
      if docker compose up -d --pull missing; then
        started=true
        break
      fi
      echo "::warning::${slug}: docker compose up failed (attempt ${attempt} of 3); retrying in 20s"
      sleep 20
    done
    if [ "$started" != "true" ]; then
      echo "::error::${slug}: the stack did not start"
      rc=1
    fi
  fi

  if [ "$rc" -eq 0 ]; then
    echo ">>> Running demo.sh (pass 1)"
    run_with_timeout "$timeout_min" ./demo.sh || rc=$?
  fi

  if [ "$rc" -eq 0 ]; then
    echo ">>> Running demo.sh again (must be safe to repeat)"
    run_with_timeout "$timeout_min" ./demo.sh || rc=$?
  fi

  if [ "$rc" -ne 0 ] && [ "$compose" = "true" ]; then
    dump_compose_logs "$example_dir" "$slug" "$variant"
  fi

  if [ "$compose" = "true" ]; then
    echo ">>> Stopping Compose stack"
    docker compose down -v --remove-orphans || true
  fi

  return "$rc"
}

count=$(jq 'length' <<< "$SHARD_EXAMPLES") || { echo "::error::SHARD_EXAMPLES is not valid JSON" >&2; exit 1; }
failures=0
summary_rows=()

for ((i = 0; i < count; i++)); do
  entry=$(jq -c ".[$i]" <<< "$SHARD_EXAMPLES")
  name=$(jq -r '.name' <<< "$entry")
  variant=$(jq -r '.variant' <<< "$entry")
  compose=$(jq -r '.compose' <<< "$entry")
  label="${name}${variant:+ ($variant)}"
  slug="${name}${variant:+-$variant}"

  active_dir="${WORKSPACE}/examples/${name}"
  active_slug="$slug"
  active_compose="$compose"
  active_variant="$variant"

  start_ts=$(date +%s)
  echo "::group::Example ${label}"
  ( run_one_example "$entry" )
  rc=$?
  echo "::endgroup::"
  elapsed=$(( $(date +%s) - start_ts ))

  active_dir=""
  active_slug=""
  active_compose="false"
  active_variant=""

  if [ "$rc" -eq 0 ]; then
    echo "PASS  ${label} (${elapsed}s)"
    summary_rows+=("| \`${label}\` | PASS | ${elapsed}s |")
  else
    echo "::error::Example ${label} failed with exit code ${rc} (${elapsed}s)"
    summary_rows+=("| \`${label}\` | **FAIL** (exit ${rc}) | ${elapsed}s |")
    failures=$((failures + 1))
  fi

  if (( i + 1 < count )); then
    reclaim_disk_if_low
  fi
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Examples (${SHARD_NAME})"
    echo ""
    echo "| Example | Result | Duration |"
    echo "|---------|--------|----------|"
    printf '%s\n' "${summary_rows[@]}"
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [ "$failures" -gt 0 ]; then
  echo "::error::${failures} of ${count} example(s) failed in shard ${SHARD_NAME}"
  exit 1
fi

echo "All ${count} example(s) passed in shard ${SHARD_NAME}."
