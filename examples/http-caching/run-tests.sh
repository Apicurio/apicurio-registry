#!/bin/bash

###############################################################################
# CI Test Runner for HTTP Caching
#
# Starts docker-compose stack, runs Phase 1 tests, and cleans up.
# Accepts custom registry and UI images for CI builds.
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
REGISTRY_IMAGE="quay.io/apicurio/apicurio-registry:latest-snapshot"
UI_IMAGE="quay.io/apicurio/apicurio-registry-ui:latest-snapshot"
COMPOSE_FILE="docker-compose.yml"
MAX_WAIT_TIME=120  # Maximum seconds to wait for services

###############################################################################
# Help Message
###############################################################################

show_help() {
    cat << EOF
HTTP Caching CI Test Runner

Starts docker-compose stack, runs Phase 1 tests, and cleans up automatically.

USAGE:
    $0 [OPTIONS]

OPTIONS:
    --registry-image IMAGE    Registry image to use
                             Default: quay.io/apicurio/apicurio-registry:latest-snapshot

    --ui-image IMAGE         UI image to use
                             Default: quay.io/apicurio/apicurio-registry-ui:latest-snapshot

    --help, -h               Show this help message

EXAMPLES:
    # Run with default images
    $0

    # Run with custom registry image
    $0 --registry-image quay.io/myorg/apicurio-registry:pr-1234

    # Run with custom registry and UI images
    $0 --registry-image my-registry:tag --ui-image my-ui:tag

EXIT CODES:
    0    All tests passed
    1    Tests failed or error occurred

DESCRIPTION:
    This script automates the full test lifecycle for CI/CD pipelines:
    1. Starts docker-compose stack with specified images
    2. Waits for services to be healthy (max ${MAX_WAIT_TIME}s)
    3. Runs all Phase 1 HTTP caching tests
    4. Displays test results and Varnish statistics
    5. Cleans up (stops and removes containers)

    The script always cleans up on exit, even on failure or interrupt (Ctrl+C).

EOF
}

###############################################################################
# Argument Parsing
###############################################################################

while [[ $# -gt 0 ]]; do
    case $1 in
        --registry-image)
            REGISTRY_IMAGE="$2"
            shift 2
            ;;
        --ui-image)
            UI_IMAGE="$2"
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

    echo ""
    print_header "Cleaning Up"

    print_info "Stopping docker-compose stack..."
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

# Display configuration
echo "Configuration:"
print_info "Registry Image: $REGISTRY_IMAGE"
print_info "UI Image: $UI_IMAGE"
print_info "Compose File: $COMPOSE_FILE"
echo ""

# Export images as environment variables for docker-compose
export REGISTRY_IMAGE
export UI_IMAGE

# Check if docker-compose file exists
if [ ! -f "$COMPOSE_FILE" ]; then
    print_error "Docker compose file not found: $COMPOSE_FILE"
    exit 1
fi

# Check if test script exists
if [ ! -f "test-phase1.sh" ]; then
    print_error "Test script not found: test-phase1.sh"
    exit 1
fi

# Make test script executable
chmod +x test-phase1.sh

###############################################################################
# Start Docker Compose Stack
###############################################################################

print_header "Starting Docker Compose Stack"

print_info "Pulling/building images..."
docker compose -f "$COMPOSE_FILE" pull 2>&1 | grep -v "Pulling" || true

print_info "Starting services..."
docker compose -f "$COMPOSE_FILE" up -d

###############################################################################
# Wait for Services to be Ready
###############################################################################

print_header "Waiting for Services"

print_info "Waiting for Registry to be healthy..."
start_time=$(date +%s)
while true; do
    if curl -sf http://localhost:8080/health/live > /dev/null 2>&1; then
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

print_info "Waiting for Varnish to be healthy..."
start_time=$(date +%s)
while true; do
    if curl -sf http://localhost:8081/health/live > /dev/null 2>&1; then
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
sleep 5

###############################################################################
# Show Service Status
###############################################################################

print_header "Service Status"

echo "Running containers:"
docker compose -f "$COMPOSE_FILE" ps

echo ""
print_info "Registry health check:"
curl -sf http://localhost:8080/health/live && echo " ✓" || echo " ✗"

print_info "Varnish health check (via cached endpoint):"
curl -sf http://localhost:8081/health/live && echo " ✓" || echo " ✗"

echo ""

###############################################################################
# Run Tests
###############################################################################

print_header "Running Phase 1 Tests"

# Run the test script
./test-phase1.sh

# Capture exit code
TEST_EXIT_CODE=$?

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

# Show some Varnish stats
echo ""
print_info "Varnish Statistics:"
docker exec varnish-cache varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss 2>/dev/null || true

# Calculate hit rate
CACHE_HITS=$(docker exec varnish-cache varnishstat -1 -f MAIN.cache_hit 2>/dev/null | awk '{print $2}' || echo "0")
CACHE_MISSES=$(docker exec varnish-cache varnishstat -1 -f MAIN.cache_miss 2>/dev/null | awk '{print $2}' || echo "0")

if [ "$CACHE_HITS" != "0" ] || [ "$CACHE_MISSES" != "0" ]; then
    TOTAL=$((CACHE_HITS + CACHE_MISSES))
    if [ $TOTAL -gt 0 ]; then
        HIT_RATE=$(awk "BEGIN {printf \"%.2f\", ($CACHE_HITS / $TOTAL) * 100}")
        print_info "Cache hit rate: ${HIT_RATE}%"
    fi
fi

echo ""

# Exit with test exit code (cleanup will run via trap)
exit $TEST_EXIT_CODE
