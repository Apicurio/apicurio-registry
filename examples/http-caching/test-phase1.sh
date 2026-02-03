#!/bin/bash

###############################################################################
# HTTP Caching Test Script - Phase 1: Immutable Content
#
# Tests the Varnish cache implementation for immutable endpoints:
# - Cache headers (Cache-Control, ETag, Vary)
# - Cache hit/miss behavior
# - Query parameter normalization
# - Conditional requests (If-None-Match)
# - Write operations pass-through
###############################################################################

set -o pipefail

###############################################################################
# Configuration
###############################################################################

VARNISH_URL="${VARNISH_URL:-http://localhost:8081}"
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080}"
BASE_PATH="/apis/registry/v3"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

###############################################################################
# Output Helper Functions
###############################################################################

print_header() {
    echo ""
    echo "=========================================="
    echo "$1"
    echo "=========================================="
}

print_test() {
    echo ""
    echo -e "${YELLOW}TEST: $1${NC}"
}

print_pass() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((TESTS_PASSED++))
}

print_fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((TESTS_FAILED++))
}

print_info() {
    echo "  $1"
}

###############################################################################
# HTTP Testing Helper Functions
###############################################################################

# Extract headers from HTTP response
get_headers() {
    echo "$1" | sed '/^\r$/q'
}

# Extract HTTP status code from curl response (with -w "\n%{http_code}")
get_status_code() {
    echo "$1" | tail -1
}

# Extract response body from curl response (with -w "\n%{http_code}")
get_body() {
    echo "$1" | head -n -1
}

# Check if a header matches expected pattern
check_header() {
    local response="$1"
    local header_name="$2"
    local expected_pattern="$3"
    local description="$4"

    if echo "$response" | grep -qi "^${header_name}: ${expected_pattern}"; then
        print_pass "$description"
        return 0
    else
        print_fail "$description (expected: $expected_pattern)"
        print_info "Response headers:"
        echo "$response" | grep -i "^${header_name}:" || print_info "(header not found)"
        return 1
    fi
}

# Purge cache for a given URL
purge_cache() {
    local url="$1"
    local quiet="${2:-true}"

    local response=$(curl -s -w "\n%{http_code}" -X PURGE "$url")
    local status=$(get_status_code "$response")

    if [ "$quiet" = "false" ]; then
        if [ "$status" = "200" ]; then
            print_info "Cache purged successfully"
        elif [ "$status" = "405" ]; then
            print_info "Warning: PURGE not allowed - cache may contain stale data"
        else
            print_info "Note: PURGE returned HTTP $status"
        fi
    fi
}

###############################################################################
# Pre-flight Checks
###############################################################################

check_services() {
    print_header "HTTP Caching Tests - Phase 1"

    echo "Configuration:"
    print_info "Varnish URL: $VARNISH_URL"
    print_info "Registry URL: $REGISTRY_URL"

    # Check Registry
    echo "Checking if services are running..."
    if ! curl -sf "${REGISTRY_URL}/health/live" > /dev/null; then
        echo -e "${RED}ERROR: Registry is not running at ${REGISTRY_URL}${NC}"
        echo "Start it with: docker compose up (or mvn quarkus:dev)"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Registry is running"

    # Check Varnish
    if ! curl -sf "${VARNISH_URL}/health/live" > /dev/null; then
        echo -e "${RED}ERROR: Varnish is not running at ${VARNISH_URL}${NC}"
        echo "Start it with: docker compose up"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Varnish is running"
}

###############################################################################
# Test Data Setup
###############################################################################

setup_test_data() {
    print_header "Setting Up Test Data"

    print_test "Creating test artifact (via Varnish)"

    local group_id="default"
    local artifact_id="test-schema-$(date +%s)"

    # Build request body for v3 API (artifact content must be JSON-escaped)
    local request_body=$(cat <<'EOF'
{
  "artifactId": "ARTIFACT_ID_PLACEHOLDER",
  "artifactType": "AVRO",
  "firstVersion": {
    "content": {
      "content": "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}",
      "contentType": "application/json"
    }
  }
}
EOF
)
    # Replace placeholder with actual artifact ID
    request_body=$(echo "$request_body" | sed "s/ARTIFACT_ID_PLACEHOLDER/${artifact_id}/")

    # Create artifact
    local response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${group_id}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${request_body}")

    local status=$(get_status_code "$response")
    local body=$(get_body "$response")

    if [ "$status" = "200" ]; then
        # Extract IDs
        GLOBAL_ID=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        CONTENT_ID=$(echo "$body" | grep -o '"contentId":[0-9]*' | head -1 | grep -o '[0-9]*')

        if [ -z "$GLOBAL_ID" ] || [ -z "$CONTENT_ID" ]; then
            print_fail "Failed to extract globalId or contentId from response"
            print_info "Response body: $body"
            exit 1
        fi

        print_pass "Artifact created (globalId: $GLOBAL_ID, contentId: $CONTENT_ID)"
    else
        print_fail "Failed to create artifact (HTTP $status)"
        print_info "Response body: $body"
        exit 1
    fi

    # Compute content hash (SHA256 of the artifact content)
    print_test "Computing content hash (SHA256)"
    local artifact_content='{"type":"record","name":"TestRecord","fields":[{"name":"id","type":"int"}]}'
    CONTENT_HASH=$(echo -n "$artifact_content" | sha256sum | cut -d' ' -f1)
    print_pass "Content hash computed: $CONTENT_HASH"
}

###############################################################################
# Test Cases
###############################################################################

test_cache_miss_with_headers() {
    print_header "Test 1: Cache Headers on First Request"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${GLOBAL_ID}"

    print_test "Purging cache to ensure clean state"
    purge_cache "$endpoint" "false"

    print_test "First request to /ids/globalIds/${GLOBAL_ID} (should be cache MISS)"
    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Cache" "MISS" "X-Cache header is MISS"
    check_header "$headers" "Cache-Control" ".*immutable.*" "Cache-Control contains 'immutable'"
    check_header "$headers" "Cache-Control" ".*public.*" "Cache-Control contains 'public'"
    check_header "$headers" "Cache-Control" ".*max-age=31536000.*" "Cache-Control max-age is 1 year"
    check_header "$headers" "ETag" ".*${GLOBAL_ID}.*" "ETag contains globalId"
    check_header "$headers" "Vary" ".*Accept.*" "Vary contains Accept"
}

test_cache_hit_and_performance() {
    print_header "Test 2: Cache Hit on Second Request"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${GLOBAL_ID}"

    print_test "Second request to same endpoint (should be cache HIT)"
    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Cache" "HIT" "X-Cache header is HIT"

    # Performance check
    print_test "Checking response time for cached request"
    local time_total=$(curl -s -o /dev/null -w "%{time_total}" "$endpoint")
    local time_ms=$(echo "$time_total" | awk '{printf "%.0f", $1 * 1000}')

    if [ -n "$time_ms" ] && [ "$time_ms" -eq "$time_ms" ] 2>/dev/null; then
        if [ "$time_ms" -lt 100 ]; then
            print_pass "Response time is fast (${time_ms}ms < 100ms)"
        else
            print_fail "Response time is slow (${time_ms}ms >= 100ms)"
        fi
    else
        print_info "Note: Could not measure response time accurately (got: $time_total)"
    fi
}

test_content_by_id() {
    print_header "Test 3: Content by contentId"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentIds/${CONTENT_ID}"

    print_test "Request to /ids/contentIds/${CONTENT_ID}"
    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "Cache-Control" ".*immutable.*" "Cache-Control contains 'immutable'"
    check_header "$headers" "ETag" ".*${CONTENT_ID}.*" "ETag contains contentId"
}

test_content_by_hash() {
    print_header "Test 4: Content by contentHash"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentHashes/${CONTENT_HASH}"

    print_test "Request to /ids/contentHashes/${CONTENT_HASH}"
    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "Cache-Control" ".*immutable.*" "Cache-Control contains 'immutable'"
    check_header "$headers" "ETag" ".*${CONTENT_HASH}.*" "ETag contains contentHash"
}

test_query_parameter_normalization() {
    print_header "Test 5: Query Parameter Normalization"

    local base_url="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${GLOBAL_ID}"
    local params1="references=PRESERVE&returnArtifactType=true"
    local params2="returnArtifactType=true&references=PRESERVE"

    print_test "Purging cache (including query param variants)"
    purge_cache "$base_url" "true"
    purge_cache "${base_url}?${params1}" "true"
    print_info "Cache purge attempted"

    # First request with params in one order
    print_test "Request with query params: ?${params1}"
    local response1=$(curl -s -D - "${base_url}?${params1}")
    local headers1=$(get_headers "$response1")

    print_info "Headers:"
    echo "$headers1" | grep -E "(X-Cache|Cache-Control)" | sed 's/^/    /'

    if echo "$headers1" | grep -qi "^X-Cache: MISS"; then
        print_pass "First request is cache MISS"
    else
        print_fail "First request should be cache MISS"
    fi

    # Second request with params in different order
    print_test "Request with same params in different order: ?${params2}"
    local response2=$(curl -s -D - "${base_url}?${params2}")
    local headers2=$(get_headers "$response2")

    print_info "Headers:"
    echo "$headers2" | grep -E "(X-Cache|Cache-Control)" | sed 's/^/    /'

    if echo "$headers2" | grep -qi "^X-Cache: HIT"; then
        print_pass "Second request is cache HIT (query params normalized)"
    else
        print_fail "Second request should be cache HIT (query params should be normalized)"
    fi
}

test_conditional_requests() {
    print_header "Test 6: Conditional Request (If-None-Match)"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${GLOBAL_ID}"

    print_test "Getting ETag from globalId endpoint"
    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")
    local etag=$(echo "$headers" | grep -i "^ETag:" | sed 's/^ETag: //i' | tr -d '\r')
    print_info "ETag: $etag"

    print_test "Sending If-None-Match request with ETag"
    local conditional_response=$(curl -s -w "\n%{http_code}" -H "If-None-Match: ${etag}" "$endpoint")
    local status=$(get_status_code "$conditional_response")

    if [ "$status" = "304" ]; then
        print_pass "Received 304 Not Modified"
    else
        print_fail "Expected 304 Not Modified, got $status"
    fi
}

test_write_operations_not_cached() {
    print_header "Test 7: Write Operations Not Cached"

    print_test "POST request should not be cached"
    local response=$(curl -s -D - -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/test/artifacts" \
        -H "Content-Type: application/json" \
        -d '{}' 2>&1 || true)

    local headers=$(get_headers "$response")

    # POST requests should pass through (may fail at backend, but shouldn't be cached)
    if echo "$headers" | grep -qi "^X-Cache: HIT"; then
        print_fail "POST request should not be cached"
    else
        print_pass "POST request not cached (passed through)"
    fi
}

test_varnish_statistics() {
    print_header "Test 8: Varnish Statistics"

    print_test "Checking Varnish cache statistics"

    if ! command -v docker &> /dev/null; then
        print_info "(Docker not available, skipping statistics)"
        return
    fi

    # Try to find Varnish container (try common names)
    local varnish_container=""
    for name in varnish varnish-cache varnish-cache-dev; do
        if docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
            varnish_container="$name"
            break
        fi
    done

    if [ -z "$varnish_container" ]; then
        print_info "(Varnish not running in Docker, skipping statistics)"
        return
    fi

    echo ""
    print_info "Cache Statistics:"
    docker exec "$varnish_container" varnishstat -1 -f MAIN.cache_hit -f MAIN.cache_miss -f MAIN.backend_conn 2>/dev/null
    echo ""

    local cache_hits=$(docker exec "$varnish_container" varnishstat -1 -f MAIN.cache_hit 2>/dev/null | awk '{print $2}')
    local cache_misses=$(docker exec "$varnish_container" varnishstat -1 -f MAIN.cache_miss 2>/dev/null | awk '{print $2}')

    if [ -n "$cache_hits" ] && [ "$cache_hits" -gt 0 ] 2>/dev/null; then
        local total=$((cache_hits + cache_misses))
        local hit_rate=$(awk "BEGIN {printf \"%.2f\", $cache_hits * 100 / $total}")
        echo "Cache Hit Rate: ${hit_rate}%"

        if awk "BEGIN {exit !($hit_rate > 50)}"; then
            print_pass "Cache hit rate is good (${hit_rate}%)"
        else
            print_info "Note: Cache hit rate may be low during initial testing"
        fi
    fi
}

print_summary() {
    print_header "Test Summary"

    echo ""
    echo "Tests Passed: $TESTS_PASSED"
    echo "Tests Failed: $TESTS_FAILED"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        return 1
    fi
}

###############################################################################
# Main Test Execution
###############################################################################

main() {
    # Pre-flight checks
    check_services

    # Setup test data
    setup_test_data

    # Run all tests
    test_cache_miss_with_headers
    test_cache_hit_and_performance
    test_content_by_id
    test_content_by_hash
    test_query_parameter_normalization
    test_conditional_requests
    test_write_operations_not_cached
    test_varnish_statistics

    # Print summary and exit
    if print_summary; then
        exit 0
    else
        exit 1
    fi
}

# Run main function
main "$@"
