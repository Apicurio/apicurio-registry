#!/usr/bin/env bash
# Partitions the JSON array produced by discover-examples.sh into balanced CI shards
# for verify-examples.yaml.
#
# Examples are grouped into three classes so each runner only sets up what it needs:
#   - platform-<name>: examples without a Compose file (e.g. kind/operator), kept isolated
#   - build-1..N:      Compose examples with CI_NEEDS_BUILD=true (share JDK + Maven cache)
#   - compose-1..M:    Compose examples without a Java build (no JDK / Maven setup)
#
# Within the build and compose classes, examples are packed deterministically using
# longest-processing-time-first (LPT) greedy bin-packing keyed by timeout (tie-broken
# by name and variant). Adding a new example requires no registration.
#
# Usage: .github/scripts/discover-examples.sh | .github/scripts/shard-examples.sh [compose-shards] [build-shards]
set -euo pipefail

compose_shards=${1:-3}
build_shards=${2:-3}

if ! [[ "$compose_shards" =~ ^[1-9][0-9]*$ ]]; then
  echo "::error::compose-shards must be a positive integer, got '$compose_shards'" >&2
  exit 1
fi
if ! [[ "$build_shards" =~ ^[1-9][0-9]*$ ]]; then
  echo "::error::build-shards must be a positive integer, got '$build_shards'" >&2
  exit 1
fi

jq -c --argjson compose_n "$compose_shards" --argjson build_n "$build_shards" '
  def aggregate($name; $items):
    ($items | map(.timeout) | add) as $sum
    | ($items | map(.timeout) | max) as $max
    | (if $max > 60 then $max else 60 end) as $cap
    | {
        name: $name,
        timeout: (if $sum < $cap then $sum else $cap end),
        build: ($items | any(.build)),
        mutableImage: ($items | any(.mutableImage)),
        toolchains: (
          $items
          | map(.toolchains | split(" ") | map(select(length > 0)))
          | add // []
          | unique
          | join(" ")
        ),
        examples: $items
      };

  def pack($prefix; $max_buckets):
    if length == 0 then []
    else
      ([length, $max_buckets] | min) as $k
      | sort_by([-.timeout, .name, .variant])
      | reduce .[] as $item (
          [range(0; $k) | {load: 0, items: []}];
          (
            to_entries
            | min_by([.value.load, .key])
            | .key
          ) as $idx
          | .[$idx].load += $item.timeout
          | .[$idx].items += [$item]
        )
      | to_entries
      | map(aggregate("\($prefix)-\(.key + 1)"; .value.items))
    end;

  (map(select(.compose == false))
    | sort_by([.name, .variant])
    | map(
        (if .variant != "" then "\(.name)-\(.variant)" else .name end) as $slug
        | aggregate("platform-\($slug)"; [.])
      ))
  + (map(select(.compose == true and .build == false)) | pack("compose"; $compose_n))
  + (map(select(.compose == true and .build == true)) | pack("build"; $build_n))
'
