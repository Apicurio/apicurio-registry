#!/usr/bin/env bash
# The registry-clients scenario with the CLI (acr). Prints one line per step; demo.sh compares them
# with the other clients'.
# Usage: ./order-scenario.sh <registry URL, without /apis/...> <schemas directory>
# Runs the CLI built by ../build.sh as a JVM application; set ACR to run another one (for example
# ACR=acr for an installed native CLI). Requires jq.
set -euo pipefail
registry=$1
schemas=$2
group=clients
artifact=cli-order
cli_jar="$(cd "$(dirname "$0")/../../.." && pwd)/cli/target/quarkus-app/quarkus-run.jar"

# A configuration of its own, so the scenario neither uses nor changes the user's contexts
home=$(mktemp -d)
trap 'rm -rf "$home"' EXIT
mkdir -p "$home/current"
echo '{}' > "$home/current/config.json"
acr() {
  if [ -n "${ACR:-}" ]; then
    ACR_HOME="$home" ACR_CURRENT_HOME="$home/current" $ACR "$@"
  else
    ACR_HOME="$home" ACR_CURRENT_HOME="$home/current" java -jar "$cli_jar" "$@"
  fi
}

# status <acr args...>: the HTTP status of a command that should fail, or "none" if it succeeded.
# The CLI exits with 3 on any server error; --verbose logs every response on stderr ("< 404 Not
# Found"), and the last one is the request that failed.
status() {
  local log
  if log=$(acr --verbose "$@" 2>&1 >/dev/null); then
    echo none
  else
    echo "$log" | sed -n 's/^< \([0-9][0-9][0-9]\) .*/\1/p' | tail -1
  fi
}

acr config set update.check-enabled=false > /dev/null
acr context create demo "$registry" > /dev/null 2>&1

# 1. Server information
version=$(acr version -o json | jq -r '."Server version" // empty')
[ -n "$version" ] && echo "info ok" || echo "info empty"

# 2. Create the artifact with its first version. The output of `artifact create` has no version, so
# the version is read back.
acr artifact create "$artifact" -g "$group" -f "$schemas/order-v1.avsc" --type AVRO > /dev/null
echo "created $(acr artifact version get -g "$group" -a "$artifact" branch=latest -o json | jq -r .version)"

# 3. BACKWARD compatibility rule on the artifact
acr artifact rule create -g "$group" -a "$artifact" COMPATIBILITY -c BACKWARD > /dev/null
echo "rule $(acr artifact rule get -g "$group" -a "$artifact" COMPATIBILITY -o json | jq -r .config)"

# 4. A compatible version: a new field with a default
echo "version $(acr artifact version create -g "$group" -a "$artifact" -f "$schemas/order-v2.avsc" -o json 2>/dev/null | jq -r .version)"

# 5. An incompatible version: a new field without a default, rejected by the rule
echo "rejected $(status artifact version create -g "$group" -a "$artifact" -f "$schemas/order-v3-incompatible.avsc")"

# 6. The latest version and its content
echo "latest $(acr artifact version get -g "$group" -a "$artifact" branch=latest -o json | jq -r .version)"
echo "fields $(acr artifact version get -g "$group" -a "$artifact" branch=latest --content | jq -r '[.fields[].name] | join(",")')"

# 7. Search
echo "search $(acr search artifact -g "$group" -a "$artifact" -o json | jq -r .count)"

# 8. Delete, then read it again
acr artifact delete "$artifact" -g "$group" > /dev/null
echo "deleted"
echo "missing $(status artifact get "$artifact" -g "$group")"
