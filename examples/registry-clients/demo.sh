#!/usr/bin/env bash
# Runs the same scenario with every client of the registry (Java, Go, Python and TypeScript SDKs,
# and the CLI), and checks that each one prints the expected line for every step.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq, plus each client's toolchain:
# JDK 21+, Go, Python 3.10+, Node.js 22.18+). Builds the clients with build.sh on the first run.
# SKIP=go,python skips clients whose toolchain is missing (java, cli, go, python, typescript); in CI
# (CI=true) nothing can be skipped.
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:8081/apis}
V3="$API/registry/v3"
REGISTRY=${API%/apis}
CLIENTS=(java cli go python typescript)
SKIP=${SKIP:-}
failures=0

# What every client prints, one line per step of the scenario (see README.md)
EXPECTED=(
  "info ok"                     # 1. server information has a version
  "created 1"                   # 2. the artifact is created with version 1
  "rule BACKWARD"               # 3. the artifact's compatibility rule
  "version 2"                   # 4. a compatible version (new field with a default)
  "rejected 400"                # 5. an incompatible version is rejected (rule violation)
  "latest 2"                    # 6. the latest version...
  "fields id,amount,currency"   #    ...and its content are version 2's
  "search 1"                    # 7. search finds the artifact
  "deleted"                     # 8. the artifact is deleted...
  "missing 404"                 #    ...and is then not found
)

ok() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }

skipped() { [[ ",$SKIP," == *",$1,"* ]]; }

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

# toolchain <client>: the command the client needs, to report a missing one clearly
toolchain() {
  case $1 in
    java|cli) echo java ;;
    go) echo go ;;
    python) echo "${PYTHON:-python3}" ;;
    typescript) echo node ;;
  esac
}

# built <client>: whether build.sh has already built the client
built() {
  case $1 in
    java) [ -d java-sdk/target/classes ] ;;
    cli) [ -f ../../cli/target/quarkus-app/quarkus-run.jar ] ;;
    go) true ;;  # go run compiles it
    python) [ -x python-sdk/.venv/bin/python ] ;;
    typescript) [ -d typescript-sdk/node_modules/@apicurio/apicurio-registry-sdk/dist ] ;;
  esac
}

# scenario <client>: runs the scenario with the client; stdout is the lines to check
scenario() {
  case $1 in
    java) ../../mvnw -q exec:java -f java-sdk -Dregistry.url="$V3" ;;
    cli) cli/order-scenario.sh "$REGISTRY" schemas ;;
    go) (cd go-sdk && go run . "$V3" ../schemas) ;;
    python) python-sdk/.venv/bin/python python-sdk/order_scenario.py "$V3" schemas ;;
    typescript) (cd typescript-sdk && node --no-warnings order-scenario.ts "$V3" ../schemas) ;;
  esac
}

if [ "${CI:-}" = true ] && [ -n "$SKIP" ]; then
  echo "SKIP=$SKIP is not allowed in CI: every client must run" >&2
  exit 1
fi
for client in "${CLIENTS[@]}"; do
  if ! skipped "$client" && ! command -v "$(toolchain "$client")" > /dev/null; then
    echo "The $client client needs '$(toolchain "$client")', which is not installed." >&2
    echo "Install it, or skip the client: SKIP=$client ./demo.sh" >&2
    exit 1
  fi
done

echo "Waiting for Registry..."
wait_for Registry "$V3/system/info"

missing=()
for client in "${CLIENTS[@]}"; do
  skipped "$client" || built "$client" || missing+=("$client")
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "Building the clients (first run only, a few minutes)..."
  log=$(mktemp)
  # Builds only what is missing: every other client is skipped
  build_skip=$(for c in "${CLIENTS[@]}"; do [[ " ${missing[*]} " == *" $c "* ]] || echo "$c"; done | paste -sd, -)
  SKIP=$build_skip ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
fi

echo "Resetting the example data..."
# Every client works in the group "clients"; a failed earlier run may have left its artifact there
status=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$V3/groups/clients")
if [[ "$status" == 204 || "$status" == 404 ]]; then
  ok "group clients is empty"
else
  fail "group clients is empty: DELETE returned $status"
fi

for client in "${CLIENTS[@]}"; do
  echo "Client $client:"
  if skipped "$client"; then
    echo "  skipped (SKIP=$SKIP)"
    continue
  fi
  log=$(mktemp)
  if ! output=$(scenario "$client" 2> "$log"); then
    fail "$client: the scenario exited with an error (stderr below)"
    sed 's/^/        /' "$log" | tail -20
  fi
  rm -f "$log"
  lines=()
  while IFS= read -r line; do lines+=("$line"); done <<< "$output"
  for i in "${!EXPECTED[@]}"; do
    actual=${lines[$i]:-<nothing>}
    if [[ "$actual" == "${EXPECTED[$i]}" ]]; then
      ok "$client: $actual"
    else
      fail "$client: $actual (expected ${EXPECTED[$i]})"
    fi
  done
  if [ ${#lines[@]} -gt ${#EXPECTED[@]} ]; then
    fail "$client printed $((${#lines[@]} - ${#EXPECTED[@]})) unexpected line(s): ${lines[*]:${#EXPECTED[@]}}"
  fi
done

echo "Afterwards:"
count=$(curl -s "$V3/search/artifacts?groupId=clients" | jq -r .count)
if [[ "$count" == 0 ]]; then
  ok "every client deleted its artifact (search in group clients: 0)"
else
  fail "every client deleted its artifact (search in group clients: $count, expected 0)"
fi

if [[ $failures -gt 0 ]]; then
  echo "$failures check(s) failed."
  exit 1
fi
echo "All checks passed."
