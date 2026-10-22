#!/bin/bash

# OTEL Performance Benchmark Script
#
# This script runs both OTEL-disabled and OTEL-enabled benchmarks
# and compares the results to measure the performance impact of
# OpenTelemetry instrumentation.
#
# Usage: ./scripts/run-otel-benchmark.sh
#
# Prerequisites:
#   - Project must be built: ./mvnw clean install -DskipTests
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================"
echo "OTEL Performance Benchmark"
echo "========================================"
echo ""
echo "Project root: $PROJECT_ROOT"
echo ""

# Create output directory
OUTPUT_DIR="$PROJECT_ROOT/target/otel-benchmark"
mkdir -p "$OUTPUT_DIR"

DISABLED_OUTPUT="$OUTPUT_DIR/otel-disabled.txt"
ENABLED_OUTPUT="$OUTPUT_DIR/otel-enabled.txt"
SUMMARY_OUTPUT="$OUTPUT_DIR/benchmark-summary.txt"

echo "Output directory: $OUTPUT_DIR"
echo ""

# Run OTEL Disabled benchmark
echo "========================================"
echo "Running benchmark: OTEL DISABLED"
echo "========================================"
cd "$PROJECT_ROOT"

./mvnw test -pl app \
    -Dtest=OpenTelemetryPerformanceTest \
    -DOpenTelemetryPerformanceTest=enabled \
    -DskipITs=true \
    2>&1 | tee "$DISABLED_OUTPUT"

echo ""
echo "========================================"
echo "Running benchmark: OTEL ENABLED"
echo "========================================"

./mvnw test -pl app \
    -Dtest=OpenTelemetryPerformanceEnabledTest \
    -DOpenTelemetryPerformanceTest=enabled \
    -DskipITs=true \
    2>&1 | tee "$ENABLED_OUTPUT"

echo ""
echo "========================================"
echo "Extracting CSV results..."
echo "========================================"

# Extract CSV lines from both outputs
echo "OTEL Disabled Results:" > "$SUMMARY_OUTPUT"
grep -E "^OTEL_DISABLED|^otel-disabled" "$DISABLED_OUTPUT" >> "$SUMMARY_OUTPUT" 2>/dev/null || echo "No results found" >> "$SUMMARY_OUTPUT"
echo "" >> "$SUMMARY_OUTPUT"

echo "OTEL Enabled Results:" >> "$SUMMARY_OUTPUT"
grep -E "^OTEL_ENABLED|^otel-enabled|^otel-fully-enabled" "$ENABLED_OUTPUT" >> "$SUMMARY_OUTPUT" 2>/dev/null || echo "No results found" >> "$SUMMARY_OUTPUT"
echo "" >> "$SUMMARY_OUTPUT"

echo ""
echo "========================================"
echo "Benchmark Complete!"
echo "========================================"
echo ""
echo "Results saved to:"
echo "  - OTEL Disabled: $DISABLED_OUTPUT"
echo "  - OTEL Enabled:  $ENABLED_OUTPUT"
echo "  - Summary:       $SUMMARY_OUTPUT"
echo ""
echo "To compare results, look for CSV lines in the output files."
echo ""

# Display summary
echo "Summary:"
echo "--------"
cat "$SUMMARY_OUTPUT"
