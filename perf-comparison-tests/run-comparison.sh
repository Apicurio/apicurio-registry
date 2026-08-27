#!/usr/bin/env bash
set -euo pipefail

OPERATION="${1:-READ_ID}"
REPETITIONS="${2:-5}"
OUTPUT="${3:-results/$OPERATION}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
PRODUCTS=(apicurio confluent karapace redpanda)

[[ "$OPERATION" =~ ^(READ_ID|READ_VERSION|REGISTER_NEW_SUBJECT|REGISTER_NEW_VERSION|REGISTER_IDEMPOTENT|COMPATIBILITY)$ ]] \
    || { echo "Invalid operation: $OPERATION" >&2; exit 2; }
[[ "$REPETITIONS" =~ ^[1-9][0-9]?$ ]] || { echo "Repetitions must be between 1 and 99" >&2; exit 2; }

mkdir -p "$OUTPUT"

for repetition in $(seq 1 "$REPETITIONS"); do
    ORDER="$(printf '%s\n' "${PRODUCTS[@]}" | python3 -c 'import random,sys; lines=sys.stdin.read().splitlines(); random.SystemRandom().shuffle(lines); print(" ".join(lines))')"
    echo "Repetition $repetition product order: $ORDER"
    for product in $ORDER; do
        run_dir="$OUTPUT/repetition-$repetition/$product"
        mkdir -p "$run_dir"
        "$ROOT/k8s/run-product.sh" "$product" "$OPERATION" "$run_dir"
    done
done

"$ROOT/scripts/compare-results.py" "$OUTPUT" "$OUTPUT/comparison"
