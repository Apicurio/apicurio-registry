#!/bin/bash

# =============================================================================
# Search Performance Benchmark Runner
# =============================================================================
# This script runs search performance benchmarks comparing:
# - Apicurio Registry 3.1.6 (baseline)
# - Apicurio Registry with search optimizations (issue #7010)
#
# Benchmarks include:
# 1. Simple name search (substring matching)
# 2. Description search
# 3. Single label filter
# 4. Multi-label filter (AND conditions)
# 5. Negated label filter (NOT conditions)
# 6. Combined name + label search
#
# Usage: ./run-benchmark.sh [iterations]
# Example: ./run-benchmark.sh 100
# =============================================================================

set -e

ITERATIONS="${1:-50}"
BASELINE_URL="http://localhost:8080"
OPTIMIZED_URL="http://localhost:8081"

# Output file for results
RESULTS_FILE="benchmark-results-$(date +%Y%m%d-%H%M%S).csv"
SUMMARY_FILE="benchmark-summary-$(date +%Y%m%d-%H%M%S).txt"

echo "=============================================="
echo "Search Performance Benchmark"
echo "=============================================="
echo "Iterations per test: ${ITERATIONS}"
echo "Baseline URL: ${BASELINE_URL}"
echo "Optimized URL: ${OPTIMIZED_URL}"
echo "Results file: ${RESULTS_FILE}"
echo "=============================================="

# Check if both registries are available
check_registry() {
    local url=$1
    local name=$2

    echo -n "Checking ${name}... "
    if curl -s "${url}/health/ready" | grep -q "UP"; then
        echo "OK"
        return 0
    else
        echo "FAILED"
        return 1
    fi
}

check_registry "${BASELINE_URL}" "baseline (3.1.6)" || exit 1
check_registry "${OPTIMIZED_URL}" "optimized (current)" || exit 1

# Initialize results file
echo "test_name,registry,iteration,response_time_ms,result_count" > "${RESULTS_FILE}"

# Function to run a single benchmark
run_benchmark() {
    local test_name=$1
    local endpoint=$2
    local registry_name=$3
    local registry_url=$4

    local full_url="${registry_url}${endpoint}"

    for ((i=1; i<=ITERATIONS; i++)); do
        # Measure response time in milliseconds
        local start_time=$(python3 -c "import time; print(int(time.time() * 1000))")

        local response=$(curl -s -w "\n%{http_code}" "${full_url}")
        local http_code=$(echo "${response}" | tail -n1)
        local body=$(echo "${response}" | sed '$d')

        local end_time=$(python3 -c "import time; print(int(time.time() * 1000))")
        local duration=$((end_time - start_time))

        # Extract result count from response
        local count=0
        if [ "${http_code}" = "200" ]; then
            count=$(echo "${body}" | jq -r '.count // 0' 2>/dev/null || echo "0")
        fi

        echo "${test_name},${registry_name},${i},${duration},${count}" >> "${RESULTS_FILE}"
    done
}

# Function to run a test on both registries and show progress
run_test() {
    local test_name=$1
    local endpoint=$2

    echo ""
    echo "Running: ${test_name}"
    echo "  Endpoint: ${endpoint}"

    echo -n "  Baseline:  "
    run_benchmark "${test_name}" "${endpoint}" "baseline" "${BASELINE_URL}"
    echo "done"

    echo -n "  Optimized: "
    run_benchmark "${test_name}" "${endpoint}" "optimized" "${OPTIMIZED_URL}"
    echo "done"
}

echo ""
echo "Starting benchmarks..."

# =============================================================================
# Test 1: Simple name search (substring matching)
# Tests LIKE '%pattern%' performance - benefits from trigram indexes
# =============================================================================
run_test "name_search_substring" "/apis/registry/v3/search/artifacts?name=*service*&limit=100"

# =============================================================================
# Test 2: Name search with prefix
# Tests LIKE 'pattern%' performance
# =============================================================================
run_test "name_search_prefix" "/apis/registry/v3/search/artifacts?name=orders*&limit=100"

# =============================================================================
# Test 3: Description search
# Tests LIKE '%pattern%' on description field
# =============================================================================
run_test "description_search" "/apis/registry/v3/search/artifacts?description=*optimization*&limit=100"

# =============================================================================
# Test 4: Single label filter (key=value)
# Tests label search with EXISTS subquery vs JOIN
# =============================================================================
run_test "single_label_filter" "/apis/registry/v3/search/artifacts?labels=env%3Dprod&limit=100"

# =============================================================================
# Test 5: Single label filter (key only)
# Tests label key existence check
# =============================================================================
run_test "label_key_only" "/apis/registry/v3/search/artifacts?labels=team&limit=100"

# =============================================================================
# Test 6: Multi-label filter (AND)
# Tests multiple label conditions - benefits from JOIN optimization
# =============================================================================
run_test "multi_label_filter" "/apis/registry/v3/search/artifacts?labels=env%3Dprod&labels=team%3Dplatform&limit=100"

# =============================================================================
# Test 7: Three label filter
# Tests complex multi-label AND condition
# =============================================================================
run_test "three_label_filter" "/apis/registry/v3/search/artifacts?labels=env%3Dprod&labels=team%3Dplatform&labels=status%3Dactive&limit=100"

# =============================================================================
# Test 8: Combined name + label search
# Tests combined filters with JOIN optimization
# =============================================================================
run_test "name_and_label_search" "/apis/registry/v3/search/artifacts?name=*service*&labels=env%3Dprod&limit=100"

# =============================================================================
# Test 9: Group ID filter with label
# Tests filtering by groupId combined with label
# =============================================================================
run_test "group_and_label_search" "/apis/registry/v3/search/artifacts?groupId=benchmark-group-1&labels=env%3Dprod&limit=100"

# =============================================================================
# Test 10: Large result set
# Tests pagination performance with larger limits (1000 results for 10k dataset)
# =============================================================================
run_test "large_result_set" "/apis/registry/v3/search/artifacts?limit=1000"

# =============================================================================
# Test 11: Version search with labels
# Tests version search performance
# =============================================================================
run_test "version_search_with_label" "/apis/registry/v3/groups/benchmark-group-1/artifacts?limit=100"

echo ""
echo "=============================================="
echo "Benchmarks complete!"
echo "=============================================="

# Generate summary statistics
echo ""
echo "Generating summary..."

# Export variables for Python
export BENCHMARK_RESULTS_FILE="${RESULTS_FILE}"
export BENCHMARK_ITERATIONS="${ITERATIONS}"

python3 << 'PYTHON_SCRIPT'
import csv
import os
from collections import defaultdict

results_file = os.environ['BENCHMARK_RESULTS_FILE']
results = defaultdict(lambda: defaultdict(list))

with open(results_file, 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        test_name = row['test_name']
        registry = row['registry']
        response_time = int(row['response_time_ms'])
        results[test_name][registry].append(response_time)

print("\n" + "=" * 80)
print("SEARCH PERFORMANCE BENCHMARK RESULTS")
print("=" * 80)
print(f"\n{'Test Name':<30} {'Baseline (ms)':<20} {'Optimized (ms)':<20} {'Improvement':<15}")
print("-" * 85)

total_baseline = 0
total_optimized = 0

for test_name in sorted(results.keys()):
    baseline_times = results[test_name].get('baseline', [0])
    optimized_times = results[test_name].get('optimized', [0])

    baseline_avg = sum(baseline_times) / len(baseline_times) if baseline_times else 0
    optimized_avg = sum(optimized_times) / len(optimized_times) if optimized_times else 0

    total_baseline += baseline_avg
    total_optimized += optimized_avg

    if baseline_avg > 0:
        improvement = ((baseline_avg - optimized_avg) / baseline_avg) * 100
        improvement_str = f"{improvement:+.1f}%"
    else:
        improvement_str = "N/A"

    print(f"{test_name:<30} {baseline_avg:>15.1f} ms   {optimized_avg:>15.1f} ms   {improvement_str:>12}")

print("-" * 85)

if total_baseline > 0:
    total_improvement = ((total_baseline - total_optimized) / total_baseline) * 100
    print(f"{'TOTAL':<30} {total_baseline:>15.1f} ms   {total_optimized:>15.1f} ms   {total_improvement:+.1f}%")
else:
    print(f"{'TOTAL':<30} {total_baseline:>15.1f} ms   {total_optimized:>15.1f} ms   N/A")

print("\n" + "=" * 80)

# Detailed statistics
print("\nDETAILED STATISTICS:")
print("-" * 80)
for test_name in sorted(results.keys()):
    print(f"\n{test_name}:")
    for registry in ['baseline', 'optimized']:
        times = results[test_name].get(registry, [])
        if times:
            avg = sum(times) / len(times)
            min_t = min(times)
            max_t = max(times)
            sorted_times = sorted(times)
            p50 = sorted_times[len(sorted_times) // 2]
            p95 = sorted_times[int(len(sorted_times) * 0.95)]
            print(f"  {registry:>10}: avg={avg:.1f}ms, min={min_t}ms, max={max_t}ms, p50={p50}ms, p95={p95}ms")

print("\n" + "=" * 80)
PYTHON_SCRIPT

# Save summary to file
python3 << 'PYTHON_SUMMARY' > "${SUMMARY_FILE}"
import csv
import os
from collections import defaultdict
from datetime import datetime

results_file = os.environ['BENCHMARK_RESULTS_FILE']
iterations = os.environ['BENCHMARK_ITERATIONS']
results = defaultdict(lambda: defaultdict(list))

with open(results_file, 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        test_name = row['test_name']
        registry = row['registry']
        response_time = int(row['response_time_ms'])
        results[test_name][registry].append(response_time)

print("Search Performance Benchmark Summary")
print(f"Generated: {datetime.now().isoformat()}")
print(f"Iterations: {iterations}")
print("")

for test_name in sorted(results.keys()):
    baseline_times = results[test_name].get('baseline', [0])
    optimized_times = results[test_name].get('optimized', [0])

    baseline_avg = sum(baseline_times) / len(baseline_times) if baseline_times else 0
    optimized_avg = sum(optimized_times) / len(optimized_times) if optimized_times else 0

    if baseline_avg > 0:
        improvement = ((baseline_avg - optimized_avg) / baseline_avg) * 100
    else:
        improvement = 0

    print(f"{test_name}: baseline={baseline_avg:.1f}ms, optimized={optimized_avg:.1f}ms, improvement={improvement:+.1f}%")
PYTHON_SUMMARY

echo ""
echo "Results saved to: ${RESULTS_FILE}"
echo "Summary saved to: ${SUMMARY_FILE}"
