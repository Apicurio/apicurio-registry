#!/bin/bash

###############################################################################
# CI Test Runner for HTTP Caching
#
# Starts docker-compose stack (or uses dev environment), runs tests, and cleans up.
# Can be used with both docker-compose.yaml (full stack) or docker-compose-dev.yaml
# (Varnish only, pointing to local quarkus:dev).
###############################################################################

set -e  # Exit on error
set -o pipefail

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Default configuration
COMPOSE_FILE="docker-compose.yaml"
USE_DEV_MODE=false
SKIP_CLEANUP=false
MAX_WAIT_TIME=120  # Maximum seconds to wait for services
TEST_ARGS=""
REGISTRY_IMAGE=""
UI_IMAGE=""

###############################################################################
# Help Message
###############################################################################

show_help() {
    cat << EOF
HTTP Caching CI Test Runner

Starts docker-compose stack, runs HTTP caching tests, and cleans up automatically.

USAGE:
    $0 [OPTIONS]

OPTIONS:
    --dev                Use dev mode (docker-compose-dev.yaml)
                        Assumes Registry is running on localhost:8080 (e.g., quarkus:dev)
                        Only starts Varnish container

    --compose-file FILE  Specify custom docker-compose file
                        Default: docker-compose.yaml (or docker-compose-dev.yaml with --dev)

    --registry-image IMG Specify Registry docker image to use
                        Passed as REGISTRY_IMAGE env var to docker compose

    --ui-image IMG      Specify UI docker image to use
                        Passed as UI_IMAGE env var to docker compose

    --skip-cleanup      Don't stop containers after tests complete
                        Useful for debugging or inspecting cache state

    --debug             Enable debug output in tests (passed to run-tests.sh)

    --fail-fast         Stop test execution on first failure (passed to run-tests.sh)

    --suite SUITE       Run specific test suite (passed to run-tests.sh)
                        See run-tests.sh --help for available suites

    --help, -h          Show this help message

ENVIRONMENT VARIABLES:
    VARNISH_URL         Varnish cache URL (auto-detected based on mode)
    REGISTRY_URL        Registry URL (auto-detected based on mode)

EXAMPLES:
    # Run with full docker-compose stack
    $0

    # Run with dev mode (local quarkus:dev + Varnish in container)
    $0 --dev

    # Run with debug output and keep containers running
    $0 --dev --debug --skip-cleanup

    # Run specific test suite in dev mode
    $0 --dev --suite ids-globalids

    # Run with fail-fast mode
    $0 --fail-fast

EXIT CODES:
    0    All tests passed
    1    Tests failed or error occurred

DESCRIPTION:
    This script automates the full test lifecycle:
    1. Starts docker-compose stack (full or dev mode)
    2. Waits for services to be healthy (max ${MAX_WAIT_TIME}s)
    3. Runs HTTP caching tests via run-tests.sh
    4. Displays test results
    5. Cleans up (stops containers) unless --skip-cleanup

    In DEV mode:
    - Uses docker-compose-dev.yaml
    - Expects Registry on localhost:8080 (e.g., mvn quarkus:dev)
    - Only starts Varnish container
    - Varnish listens on localhost:6081 (not 8081 to avoid conflict)

    In CI mode (default):
    - Uses docker-compose.yaml
    - Starts full stack (Registry + Varnish + UI)
    - Varnish listens on localhost:8081

EOF
}

###############################################################################
# Argument Parsing
###############################################################################

while [[ $# -gt 0 ]]; do
    case $1 in
        --dev)
            USE_DEV_MODE=true
            COMPOSE_FILE="docker-compose-dev.yaml"
            shift
            ;;
        --compose-file)
            COMPOSE_FILE="$2"
            shift 2
            ;;
        --registry-image)
            REGISTRY_IMAGE="$2"
            shift 2
            ;;
        --ui-image)
            UI_IMAGE="$2"
            shift 2
            ;;
        --skip-cleanup)
            SKIP_CLEANUP=true
            shift
            ;;
        --debug)
            TEST_ARGS="$TEST_ARGS --debug"
            shift
            ;;
        --fail-fast)
            TEST_ARGS="$TEST_ARGS --fail-fast"
            shift
            ;;
        --suite)
            TEST_ARGS="$TEST_ARGS --suite $2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown option: $1${NC}" >&2
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

###############################################################################
# Helper Functions
###############################################################################

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

###############################################################################
# Cleanup Function
###############################################################################

cleanup() {
    local exit_code=$?

    if [ "$SKIP_CLEANUP" = "true" ]; then
        echo ""
        print_header "Skipping Cleanup"
        print_warning "Containers are still running (--skip-cleanup specified)"
        print_info "To stop manually: cd $(dirname "$0")/.. && docker compose -f $COMPOSE_FILE down"
        exit $exit_code
    fi

    echo ""
    print_header "Cleaning Up"

    print_info "Stopping docker-compose stack..."
    # COMPOSE_FILE is already an absolute path, no need to cd
    docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true

    if [ $exit_code -eq 0 ]; then
        print_success "Tests passed and cleanup complete"
    else
        print_error "Tests failed (exit code: $exit_code)"
    fi

    exit $exit_code
}

# Register cleanup trap
trap cleanup EXIT INT TERM

###############################################################################
# Main Script
###############################################################################

print_header "HTTP Caching CI Test Runner"

export VARNISH_URL="http://localhost:8081"
export REGISTRY_URL="http://localhost:8080"

# Export docker image environment variables if specified
if [ -n "$REGISTRY_IMAGE" ]; then
    export REGISTRY_IMAGE
fi
if [ -n "$UI_IMAGE" ]; then
    export UI_IMAGE
fi

# Display configuration
echo "Configuration:"
print_info "Mode: $([ "$USE_DEV_MODE" = "true" ] && echo "DEV" || echo "CI")"
print_info "Compose File: $COMPOSE_FILE"
print_info "Registry URL: $REGISTRY_URL"
print_info "Varnish URL: $VARNISH_URL"
[ -n "$REGISTRY_IMAGE" ] && print_info "Registry Image: $REGISTRY_IMAGE"
[ -n "$UI_IMAGE" ] && print_info "UI Image: $UI_IMAGE"
print_info "Skip Cleanup: $SKIP_CLEANUP"
[ -n "$TEST_ARGS" ] && print_info "Test Args: $TEST_ARGS"
echo ""

# Change to script directory to find docker-compose files
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# Check if docker-compose file exists
if [ ! -f "$COMPOSE_FILE" ]; then
    print_error "Docker compose file not found: $COMPOSE_FILE"
    print_info "Current directory: $(pwd)"
    exit 1
fi

# Convert to absolute path so it works from any directory
COMPOSE_FILE="$(pwd)/$COMPOSE_FILE"

# Check if test script exists
if [ ! -f "test/run-tests.sh" ]; then
    print_error "Test script not found: test/run-tests.sh"
    print_info "Current directory: $(pwd)"
    exit 1
fi

# Make test script executable
chmod +x test/run-tests.sh

# In dev mode, check if Registry is already running
if [ "$USE_DEV_MODE" = "true" ]; then
    print_header "Checking Dev Environment"

    print_info "Checking if Registry is running on $REGISTRY_URL..."
    if ! curl -sf "${REGISTRY_URL}/health/live" > /dev/null 2>&1; then
        print_error "Registry is not running at ${REGISTRY_URL}"
        print_info "In dev mode, you need to start Registry manually:"
        print_info "cd app & mvn quarkus:dev -Dapicurio.rest.mutability.artifact-version-content.enabled=true"
        exit 1
    fi
    print_success "Registry is running"
fi

###############################################################################
# Start Docker Compose Stack
###############################################################################

print_header "Starting Docker Compose Stack"

print_info "Pulling/building images..."
docker compose -f "$COMPOSE_FILE" pull 2>&1 | grep -v "Pulling" || true

print_info "Starting services..."
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

###############################################################################
# Wait for Services to be Ready
###############################################################################

print_header "Waiting for Services"

# In CI mode, wait for Registry
if [ "$USE_DEV_MODE" = "false" ]; then
    print_info "Waiting for Registry to be healthy..."
    start_time=$(date +%s)
    while true; do
        if curl -sf "${REGISTRY_URL}/health/live" > /dev/null 2>&1; then
            print_success "Registry is ready"
            break
        fi

        current_time=$(date +%s)
        elapsed=$((current_time - start_time))

        if [ $elapsed -gt $MAX_WAIT_TIME ]; then
            print_error "Registry failed to start within ${MAX_WAIT_TIME}s"
            print_info "Showing registry logs:"
            docker compose -f "$COMPOSE_FILE" logs registry
            exit 1
        fi

        echo -n "."
        sleep 2
    done
fi

print_info "Waiting for Varnish to be healthy..."
start_time=$(date +%s)
while true; do
    if curl -sf "${VARNISH_URL}/health/live" > /dev/null 2>&1; then
        print_success "Varnish is ready"
        break
    fi

    current_time=$(date +%s)
    elapsed=$((current_time - start_time))

    if [ $elapsed -gt $MAX_WAIT_TIME ]; then
        print_error "Varnish failed to start within ${MAX_WAIT_TIME}s"
        print_info "Showing varnish logs:"
        docker compose -f "$COMPOSE_FILE" logs varnish
        exit 1
    fi

    echo -n "."
    sleep 2
done

# Give services a moment to stabilize
print_info "Waiting for services to stabilize..."
sleep 3

###############################################################################
# Show Service Status
###############################################################################

print_header "Service Status"

echo "Running containers:"
docker compose -f "$COMPOSE_FILE" ps

echo ""
print_info "Registry health check:"
curl -sf "${REGISTRY_URL}/health/live" && echo " ✓" || echo " ✗"

print_info "Varnish health check (via cached endpoint):"
curl -sf "${VARNISH_URL}/health/live" && echo " ✓" || echo " ✗"

echo ""

###############################################################################
# Run Tests
###############################################################################

print_header "Running HTTP Caching Tests"

# Run test script with arguments
# Temporarily disable exit-on-error to capture test failures
cd "$SCRIPT_DIR"
set +e
./run-tests.sh $TEST_ARGS
TEST_EXIT_CODE=$?
set -e

###############################################################################
# Display Results
###############################################################################

echo ""
print_header "Test Results"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    print_success "All tests passed!"
else
    print_error "Tests failed with exit code: $TEST_EXIT_CODE"
fi

# Show some Varnish stats if available
echo ""
print_info "Varnish Statistics:"
VARNISH_CONTAINER=$(docker compose -f "$COMPOSE_FILE" ps -q varnish 2>/dev/null || echo "")
if [ -n "$VARNISH_CONTAINER" ]; then
    docker exec "$VARNISH_CONTAINER" varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss 2>/dev/null || true

    # Calculate hit rate
    CACHE_HITS=$(docker exec "$VARNISH_CONTAINER" varnishstat -1 -f MAIN.cache_hit 2>/dev/null | awk '{print $2}' || echo "0")
    CACHE_MISSES=$(docker exec "$VARNISH_CONTAINER" varnishstat -1 -f MAIN.cache_miss 2>/dev/null | awk '{print $2}' || echo "0")

    if [ "$CACHE_HITS" != "0" ] || [ "$CACHE_MISSES" != "0" ]; then
        TOTAL=$((CACHE_HITS + CACHE_MISSES))
        if [ $TOTAL -gt 0 ]; then
            HIT_RATE=$(awk "BEGIN {printf \"%.2f\", ($CACHE_HITS / $TOTAL) * 100}")
            print_info "Cache hit rate: ${HIT_RATE}%"
        fi
    fi
else
    print_warning "Could not access Varnish container for statistics"
fi

echo ""

# Exit with test exit code (cleanup will run via trap)
exit $TEST_EXIT_CODE
