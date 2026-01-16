#!/bin/bash

# =============================================================================
# Search Performance Benchmark - Complete Runner
# =============================================================================
# This script runs the complete benchmark workflow:
# 1. Builds the current registry image (with optimizations)
# 2. Starts both registry instances (baseline and optimized)
# 3. Seeds test data into both registries
# 4. Runs the performance benchmarks
# 5. Displays and saves results
#
# Prerequisites:
# - Docker and Docker Compose installed
# - curl, jq, python3 available
# - Maven for building the registry (optional, can use pre-built image)
#
# Usage: ./run-all.sh [--skip-build] [--num-artifacts N] [--iterations N]
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# Default values - 10k artifacts meets acceptance criteria for large datasets
SKIP_BUILD=false
NUM_ARTIFACTS=10000
NUM_LABELS=5
ITERATIONS=50
PARALLEL_JOBS=10

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --num-artifacts)
            NUM_ARTIFACTS="$2"
            shift 2
            ;;
        --iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        --parallel)
            PARALLEL_JOBS="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-build       Skip building the optimized registry image"
            echo "  --num-artifacts N  Number of artifacts to create (default: 10000)"
            echo "  --iterations N     Number of benchmark iterations (default: 50)"
            echo "  --parallel N       Number of parallel seeding jobs (default: 10)"
            echo "  --help             Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=============================================="
echo "Search Performance Benchmark - Full Run"
echo "=============================================="
echo "Skip build: ${SKIP_BUILD}"
echo "Artifacts: ${NUM_ARTIFACTS}"
echo "Parallel jobs: ${PARALLEL_JOBS}"
echo "Benchmark iterations: ${ITERATIONS}"
echo "=============================================="

# Step 1: Build the optimized registry image
if [ "${SKIP_BUILD}" = false ]; then
    echo ""
    echo "Step 1: Building optimized registry image..."
    echo "----------------------------------------------"

    # Go to project root
    cd "${SCRIPT_DIR}/../.."

    # Build the project and create Docker image
    echo "Building project (this may take a few minutes)..."
    mvn clean package -DskipTests -Dquarkus.container-image.build=true -Dquarkus.container-image.name=apicurio-registry -Dquarkus.container-image.tag=latest-snapshot -pl app -am

    cd "${SCRIPT_DIR}"
    echo "Build complete!"
else
    echo ""
    echo "Step 1: Skipping build (using existing images)"
fi

# Step 2: Start the registry instances
echo ""
echo "Step 2: Starting registry instances..."
echo "----------------------------------------------"

# Stop any existing containers
docker compose down -v 2>/dev/null || true

# Start containers
docker compose up -d

# Wait for registries to be ready
echo "Waiting for registries to be ready..."
for i in {1..120}; do
    baseline_ready=$(curl -s http://localhost:8080/health/ready 2>/dev/null | grep -c "UP" || echo "0")
    optimized_ready=$(curl -s http://localhost:8081/health/ready 2>/dev/null | grep -c "UP" || echo "0")

    if [ "${baseline_ready}" = "1" ] && [ "${optimized_ready}" = "1" ]; then
        echo "Both registries are ready!"
        break
    fi

    if [ $i -eq 120 ]; then
        echo "Registries not ready after 120 seconds. Check docker logs."
        docker compose logs
        exit 1
    fi

    printf "\rWaiting... %d/120s (baseline: %s, optimized: %s)" $i \
        $([ "${baseline_ready}" = "1" ] && echo "ready" || echo "waiting") \
        $([ "${optimized_ready}" = "1" ] && echo "ready" || echo "waiting")
    sleep 1
done

# Step 3: Seed test data
echo ""
echo "Step 3: Seeding test data..."
echo "----------------------------------------------"

echo "Seeding baseline registry (3.1.6)..."
./seed-data.sh http://localhost:8080 ${NUM_ARTIFACTS} ${NUM_LABELS} 10 3 ${PARALLEL_JOBS}

echo ""
echo "Seeding optimized registry (current)..."
./seed-data.sh http://localhost:8081 ${NUM_ARTIFACTS} ${NUM_LABELS} 10 3 ${PARALLEL_JOBS}

# Step 4: Run benchmarks
echo ""
echo "Step 4: Running benchmarks..."
echo "----------------------------------------------"
./run-benchmark.sh ${ITERATIONS}

# Step 5: Summary
echo ""
echo "=============================================="
echo "Benchmark Complete!"
echo "=============================================="
echo ""
echo "To view the results again, check the generated CSV and summary files."
echo "To stop the registries: docker compose down"
echo "To stop and remove data: docker compose down -v"
