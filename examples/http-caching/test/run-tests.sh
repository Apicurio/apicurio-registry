#!/bin/bash

###############################################################################
# HTTP Caching Test Script
#
# Tests the Varnish cache implementation for all cached endpoints:
# - IdsResource endpoints (globalIds, contentIds, contentHashes, references)
# - GroupsResource version content endpoints
# - Cache headers (Cache-Control, ETag, Surrogate-Control, X-Cache-Cacheability)
# - Cache hit/miss behavior
# - HIGH vs MODERATE cacheability scenarios
# - DRAFT artifact versions
# - Query parameter handling (references, returnArtifactType)
# - Conditional requests (If-None-Match)
###############################################################################

set -o pipefail

###############################################################################
# Configuration
###############################################################################

VARNISH_URL="${VARNISH_URL:-http://localhost:8081}"
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080}"
BASE_PATH="/apis/registry/v3"

# Cache TTL configuration (matching HttpCachingConfig defaults)
# These should match the configuration in apicurio.http-caching.* properties
HIGH_CACHEABILITY_TTL="${HIGH_CACHEABILITY_TTL:-864000}"      # 10 days (apicurio.http-caching.high-cacheability.max-age-seconds)
MODERATE_CACHEABILITY_TTL="${MODERATE_CACHEABILITY_TTL:-30}"  # 30 seconds (apicurio.http-caching.moderate-cacheability.max-age-seconds)
LOW_CACHEABILITY_TTL="${LOW_CACHEABILITY_TTL:-10}"            # 10 seconds (apicurio.http-caching.low-cacheability.max-age-seconds)

# Test options
DEBUG="${DEBUG:-false}"
FAIL_FAST="${FAIL_FAST:-false}"
TEST_SUITE="${TEST_SUITE:-all}"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

###############################################################################
# Help Message
###############################################################################

show_help() {
    cat << EOF
HTTP Caching Test Script

Tests Varnish cache implementation for all Registry v3 endpoints with caching enabled.

USAGE:
    $0 [OPTIONS]

OPTIONS:
    --debug              Enable debug output (request/response details)
    --fail-fast          Stop execution on first test failure
    --suite SUITE        Run specific test suite (see below)
    --help, -h           Show this help message

ENVIRONMENT VARIABLES:
    VARNISH_URL                 Varnish cache URL (default: http://localhost:8081)
    REGISTRY_URL                Registry URL (default: http://localhost:8080)
    HIGH_CACHEABILITY_TTL       Expected TTL for HIGH cacheability in seconds (default: 864000 = 10 days)
    MODERATE_CACHEABILITY_TTL   Expected TTL for MODERATE cacheability in seconds (default: 30)
    LOW_CACHEABILITY_TTL        Expected TTL for LOW cacheability in seconds (default: 10)

TEST SUITES:
    all                 Run all test suites (default)
    ids-globalids       Test /ids/globalIds/{globalId} endpoint
    ids-contentids      Test /ids/contentIds/{contentId} endpoint
    ids-contenthashes   Test /ids/contentHashes/{contentHash} endpoint
    ids-refs-globalid   Test /ids/globalIds/{globalId}/references endpoint
    ids-refs-contentid  Test /ids/contentIds/{contentId}/references endpoint
    ids-refs-hash       Test /ids/contentHashes/{contentHash}/references endpoint
    version-content     Test /groups/.../versions/.../content endpoint

EXAMPLES:
    # Run all tests
    $0

    # Run with debug output
    $0 --debug

    # Run specific test suite
    $0 --suite ids-globalids

    # Run with fail-fast mode
    $0 --fail-fast

    # Run against dev environment
    VARNISH_URL=http://localhost:6081 REGISTRY_URL=http://localhost:8080 $0

EXIT CODES:
    0    All tests passed
    1    One or more tests failed

EOF
}

###############################################################################
# Argument Parsing
###############################################################################

while [[ $# -gt 0 ]]; do
    case $1 in
        --debug)
            DEBUG=true
            shift
            ;;
        --fail-fast)
            FAIL_FAST=true
            shift
            ;;
        --suite)
            TEST_SUITE="$2"
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

    if [ "$FAIL_FAST" = "true" ]; then
        echo ""
        echo -e "${RED}Stopping due to --fail-fast${NC}"
        print_summary
        exit 1
    fi
}

print_info() {
    echo "  $1"
}

print_debug() {
    if [ "$DEBUG" = "true" ]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

###############################################################################
# HTTP Testing Helper Functions
###############################################################################

# Extract headers from HTTP response
get_headers() {
    local response="$1"
    local headers=$(echo "$response" | sed '/^\r$/q')
    print_debug "Headers:\n$headers"
    echo "$headers"
}

# Extract response body from HTTP response (with -D -)
get_body() {
    local response="$1"
    local body=$(echo "$response" | sed '1,/^\r$/d')
    print_debug "Body:\n$body"
    echo "$body"
}

# Extract HTTP status code from curl response (with -w "\n%{http_code}")
get_status_code() {
    echo "$1" | tail -1
}

# Extract body from curl response (with -w "\n%{http_code}")
get_response_body() {
    echo "$1" | head -n -1
}

# Extract header value from response
get_header_value() {
    local response="$1"
    local header_name="$2"
    echo "$response" | grep -i "^${header_name}:" | head -1 | sed "s/^${header_name}: //i" | tr -d '\r\n'
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

    print_debug "Purging cache: $url"
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
    print_header "HTTP Caching Tests"

    echo "Configuration:"
    print_info "Varnish URL: $VARNISH_URL"
    print_info "Registry URL: $REGISTRY_URL"
    print_info "Test Suite: $TEST_SUITE"
    print_info "Debug Mode: $DEBUG"
    print_info "Fail Fast: $FAIL_FAST"
    print_info "TTL Config: HIGH=${HIGH_CACHEABILITY_TTL}s, MODERATE=${MODERATE_CACHEABILITY_TTL}s, LOW=${LOW_CACHEABILITY_TTL}s"

    # Check Registry
    echo ""
    echo "Checking if services are running..."
    if ! curl -sf "${REGISTRY_URL}/health/live" > /dev/null; then
        echo -e "${RED}ERROR: Registry is not running at ${REGISTRY_URL}${NC}"
        echo "Start it with: docker compose -f docker-compose.yaml up OR or mvn quarkus:dev"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Registry is running"

    # Check Varnish
    if ! curl -sf "${VARNISH_URL}/health/live" > /dev/null; then
        echo -e "${RED}ERROR: Varnish is not running at ${VARNISH_URL}${NC}"
        echo "Start it with: docker compose -f docker-compose[-dev].yaml up"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Varnish is running"
}

###############################################################################
# Test Data Setup
###############################################################################

setup_test_data() {
    print_header "Setting Up Test Data"

    # Use timestamp for unique artifact names
    local timestamp=$(date +%s)
    GROUP_ID="test"
    ARTIFACT_ID="cache-test-${timestamp}"
    ARTIFACT_ID_WITH_REFS="cache-test-refs-${timestamp}"
    REFERENCED_ARTIFACT_ID="cache-test-address-${timestamp}"

    print_test "Creating test artifact without references"

    # Create simple artifact
    local artifact_content='{"type":"record","name":"TestRecord","fields":[{"name":"id","type":"int"}]}'
    local request_body=$(cat <<EOF
{
  "artifactId": "${ARTIFACT_ID}",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1.0.0",
    "content": {
      "content": $(echo "$artifact_content" | jq -R -s '.'),
      "contentType": "application/json"
    }
  }
}
EOF
)

    local response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${request_body}")

    local status=$(get_status_code "$response")
    local body=$(get_response_body "$response")

    if [ "$status" = "200" ]; then
        GLOBAL_ID=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        CONTENT_ID=$(echo "$body" | grep -o '"contentId":[0-9]*' | head -1 | grep -o '[0-9]*')

        if [ -z "$GLOBAL_ID" ] || [ -z "$CONTENT_ID" ]; then
            print_fail "Failed to extract globalId or contentId from response"
            print_debug "Response body: $body"
            exit 1
        fi

        print_pass "Artifact created (globalId: $GLOBAL_ID, contentId: $CONTENT_ID)"
    else
        print_fail "Failed to create artifact (HTTP $status)"
        print_debug "Response body: $body"
        exit 1
    fi

    # Compute content hash (canonicalized - compact JSON)
    print_test "Computing content hash (SHA256)"
    CONTENT_HASH=$(echo -n "$artifact_content" | jq -c . | sha256sum | cut -d' ' -f1)
    print_pass "Content hash computed: $CONTENT_HASH"

    # Create DRAFT version
    print_test "Creating DRAFT version 2.0.0-draft"

    local draft_content='{"type":"record","name":"TestRecordDraft","fields":[{"name":"id","type":"int"}]}'
    local draft_body=$(cat <<EOF
{
  "version": "2.0.0-draft",
  "content": {
    "content": $(echo "$draft_content" | jq -R -s '.'),
    "contentType": "application/json"
  },
  "isDraft": true
}
EOF
)

    response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions" \
        -H "Content-Type: application/json" \
        -d "${draft_body}")

    status=$(get_status_code "$response")
    if [ "$status" = "200" ]; then
        body=$(get_response_body "$response")
        DRAFT_GLOBAL_ID=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        print_pass "DRAFT version created (globalId: $DRAFT_GLOBAL_ID)"
    else
        print_fail "Failed to create DRAFT version (HTTP $status)"
        exit 1
    fi

    # Create artifact with references
    print_test "Creating referenced artifact (Address)"

    local address_content='{"type":"record","name":"Address","fields":[{"name":"street","type":"string"}]}'
    local address_request=$(cat <<EOF
{
  "artifactId": "${REFERENCED_ARTIFACT_ID}",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1.0.0",
    "content": {
      "content": $(echo "$address_content" | jq -R -s '.'),
      "contentType": "application/json"
    }
  }
}
EOF
)

    response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${address_request}")

    status=$(get_status_code "$response")
    if [ "$status" = "200" ]; then
        body=$(get_response_body "$response")
        REF_GLOBAL_ID=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        REF_CONTENT_ID=$(echo "$body" | grep -o '"contentId":[0-9]*' | head -1 | grep -o '[0-9]*')
        print_pass "Referenced artifact created (globalId: $REF_GLOBAL_ID)"
    else
        print_fail "Failed to create referenced artifact (HTTP $status)"
        exit 1
    fi

    # Compute referenced content hash (canonicalized - compact JSON)
    REF_CONTENT_HASH=$(echo -n "$address_content" | jq -c . | sha256sum | cut -d' ' -f1)

    # Create artifact that references Address
    print_test "Creating artifact with references (Person)"

    local person_content='{"type":"record","name":"Person","fields":[{"name":"name","type":"string"},{"name":"address","type":"Address"}]}'
    local person_request=$(cat <<EOF
{
  "artifactId": "${ARTIFACT_ID_WITH_REFS}",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1.0.0",
    "content": {
      "content": $(echo "$person_content" | jq -R -s '.'),
      "contentType": "application/json",
      "references": [
        {
          "name": "Address",
          "groupId": "${GROUP_ID}",
          "artifactId": "${REFERENCED_ARTIFACT_ID}",
          "version": "1.0.0"
        }
      ]
    }
  }
}
EOF
)

    response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${person_request}")

    status=$(get_status_code "$response")
    if [ "$status" = "200" ]; then
        body=$(get_response_body "$response")
        PERSON_GLOBAL_ID=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        print_pass "Artifact with references created (globalId: $PERSON_GLOBAL_ID)"
    else
        print_fail "Failed to create artifact with references (HTTP $status)"
        exit 1
    fi

    print_info "Test data setup complete"
}

###############################################################################
# Test Suite: /ids/globalIds/{globalId}
###############################################################################

test_suite_ids_globalids() {
    print_header "Test Suite: /ids/globalIds/{globalId}"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${GLOBAL_ID}"

    # Test 1: Cache MISS with HIGH cacheability
    print_test "Cache MISS with HIGH cacheability headers"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"
    check_header "$headers" "ETag" ".*${GLOBAL_ID}.*" "ETag contains globalId"
    check_header "$headers" "Vary" ".*Accept.*" "Vary includes Accept"

    # Test 2: Cache HIT on second request
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"

    # Test 3: Query parameter - references=PRESERVE
    print_test "Query parameter: references=PRESERVE"
    purge_cache "${endpoint}?references=PRESERVE" "true"

    response=$(curl -s -D - "${endpoint}?references=PRESERVE")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with query param is MISS"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "Cacheability is HIGH with references param"

    # Test 4: Query parameter - returnArtifactType=true
    print_test "Query parameter: returnArtifactType=true"
    purge_cache "${endpoint}?returnArtifactType=true" "true"

    response=$(curl -s -D - "${endpoint}?returnArtifactType=true")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Registry-ArtifactType" "AVRO" "X-Registry-ArtifactType header is present"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "Cacheability is HIGH with returnArtifactType param"

    # Test 5: Query parameter - references=REWRITE
    print_test "Query parameter: references=REWRITE"
    purge_cache "${endpoint}?references=REWRITE" "true"

    response=$(curl -s -D - "${endpoint}?references=REWRITE")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with references=REWRITE is MISS"
    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "Cacheability is MODERATE with references=REWRITE param"

    # Test 6: 304 Not Modified
    print_test "Conditional request with If-None-Match"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    local etag=$(get_header_value "$headers" "ETag")

    local conditional_response=$(curl -s -w "\n%{http_code}" -H "If-None-Match: ${etag}" "$endpoint")
    local status=$(get_status_code "$conditional_response")

    if [ "$status" = "304" ]; then
        print_pass "Received 304 Not Modified"
    else
        print_fail "Expected 304 Not Modified, got $status"
    fi
}

###############################################################################
# Test Suite: /ids/contentIds/{contentId}
###############################################################################

test_suite_ids_contentids() {
    print_header "Test Suite: /ids/contentIds/{contentId}"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentIds/${CONTENT_ID}"

    # Test 1: Cache MISS with HIGH cacheability
    print_test "Cache MISS with HIGH cacheability"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"
    check_header "$headers" "ETag" ".*${CONTENT_ID}.*" "ETag contains contentId"

    # Test 2: Cache HIT
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"
}

###############################################################################
# Test Suite: /ids/contentHashes/{contentHash}
###############################################################################

test_suite_ids_contenthashes() {
    print_header "Test Suite: /ids/contentHashes/{contentHash}"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentHashes/${CONTENT_HASH}"

    # Test 1: Cache MISS with HIGH cacheability
    print_test "Cache MISS with HIGH cacheability"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"
    check_header "$headers" "ETag" ".*${CONTENT_HASH}.*" "ETag contains contentHash"

    # Test 2: Cache HIT
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"
}

###############################################################################
# Test Suite: /ids/globalIds/{globalId}/references
###############################################################################

test_suite_ids_refs_globalid() {
    print_header "Test Suite: /ids/globalIds/{globalId}/references"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${PERSON_GLOBAL_ID}/references"

    # Test 1: Cache MISS
    print_test "Cache MISS with HIGH cacheability"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")
    local body=$(get_body "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"

    # Verify body contains references
    if echo "$body" | grep -q "${REFERENCED_ARTIFACT_ID}"; then
        print_pass "Response contains reference to Address artifact"
    else
        print_fail "Response should contain reference"
        print_debug "Body: $body"
    fi

    # Test 2: Cache HIT
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"

    # Test 3: Query parameter refType=OUTBOUND (default behavior)
    print_test "Query parameter: refType=OUTBOUND"
    purge_cache "${endpoint}?refType=OUTBOUND" "true"

    response=$(curl -s -D - "${endpoint}?refType=OUTBOUND")
    headers=$(get_headers "$response")
    body=$(get_body "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with refType=OUTBOUND is MISS"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "Cacheability is HIGH with refType=OUTBOUND (default)"
    check_header "$headers" "ETag" ".*refType.*" "ETag includes refType parameter"

    # Verify body contains outbound references
    if echo "$body" | grep -q "${REFERENCED_ARTIFACT_ID}"; then
        print_pass "Response contains outbound reference to Address artifact"
    else
        print_fail "Response should contain outbound reference"
        print_debug "Body: $body"
    fi

    # Test 4: Query parameter refType=INBOUND
    print_test "Query parameter: refType=INBOUND (LOW cacheability)"
    purge_cache "${endpoint}?refType=INBOUND" "true"

    response=$(curl -s -D - "${endpoint}?refType=INBOUND")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with refType=INBOUND is MISS"
    check_header "$headers" "X-Cache-Cacheability" "LOW" "Cacheability is LOW with refType=INBOUND (new artifacts can reference this)"
    check_header "$headers" "ETag" ".*refType.*" "ETag includes refType parameter"
    check_header "$headers" "Surrogate-Control" ".*max-age=${LOW_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$LOW_CACHEABILITY_TTL for INBOUND"

    # Test 5: Cache HIT for INBOUND references
    print_test "Cache HIT for refType=INBOUND"

    response=$(curl -s -D - "${endpoint}?refType=INBOUND")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request with refType=INBOUND is cache HIT"

    # Test 6: Verify different ETags for OUTBOUND vs INBOUND
    print_test "Different ETags for OUTBOUND vs INBOUND"

    local outbound_response=$(curl -s -D - "${endpoint}?refType=OUTBOUND")
    local outbound_etag=$(get_header_value "$(get_headers "$outbound_response")" "ETag")

    local inbound_response=$(curl -s -D - "${endpoint}?refType=INBOUND")
    local inbound_etag=$(get_header_value "$(get_headers "$inbound_response")" "ETag")

    if [ "$outbound_etag" != "$inbound_etag" ]; then
        print_pass "ETags differ for OUTBOUND vs INBOUND (proper cache separation)"
        print_debug "OUTBOUND ETag: $outbound_etag"
        print_debug "INBOUND ETag: $inbound_etag"
    else
        print_fail "ETags should differ for different refType values"
        print_debug "Both ETags: $outbound_etag"
    fi
}

###############################################################################
# Test Suite: /ids/contentIds/{contentId}/references
###############################################################################

test_suite_ids_refs_contentid() {
    print_header "Test Suite: /ids/contentIds/{contentId}/references"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentIds/${CONTENT_ID}/references"

    # Test 1: Cache MISS
    print_test "Cache MISS with HIGH cacheability"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"

    # Test 2: Cache HIT
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"
}

###############################################################################
# Test Suite: /ids/contentHashes/{contentHash}/references
###############################################################################

test_suite_ids_refs_hash() {
    print_header "Test Suite: /ids/contentHashes/{contentHash}/references"

    local endpoint="${VARNISH_URL}${BASE_PATH}/ids/contentHashes/${REF_CONTENT_HASH}/references"

    # Test 1: Cache MISS
    print_test "Cache MISS with HIGH cacheability"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Surrogate-Control max-age is \$HIGH_CACHEABILITY_TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "X-Cache-Cacheability is HIGH"

    # Test 2: Cache HIT
    print_test "Cache HIT on second request"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"
}

###############################################################################
# Test Suite: /groups/.../versions/.../content
###############################################################################

test_suite_version_content() {
    print_header "Test Suite: /groups/.../versions/.../content"

    # Test 1: Concrete version (HIGH cacheability)
    print_test "Concrete version has HIGH cacheability"

    local endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/1.0.0/content"
    purge_cache "$endpoint" "true"

    local response=$(curl -s -D - "$endpoint")
    local headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request is cache MISS"
    check_header "$headers" "Surrogate-Control" ".*max-age=${HIGH_CACHEABILITY_TTL}.*" "Concrete version has \$HIGH_CACHEABILITY_TTL TTL"
    check_header "$headers" "X-Cache-Cacheability" "HIGH" "Concrete version has HIGH cacheability"
    check_header "$headers" "ETag" ".*" "ETag is present"

    # Test 2: Cache HIT for concrete version
    print_test "Cache HIT for concrete version"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"

    # Test 3: DRAFT version (MODERATE cacheability)
    print_test "DRAFT version has MODERATE cacheability"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/2.0.0-draft/content"
    purge_cache "$endpoint" "true"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "Surrogate-Control" ".*max-age=${MODERATE_CACHEABILITY_TTL}.*" "DRAFT version has \$MODERATE_CACHEABILITY_TTL TTL"
    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "DRAFT version has MODERATE cacheability"

    # Test 4: Version expression 'branch=latest' (MODERATE cacheability)
    print_test "Version expression 'branch=latest' has MODERATE cacheability"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/branch=latest/content"
    purge_cache "$endpoint" "true"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")

    check_header "$headers" "Surrogate-Control" ".*max-age=${MODERATE_CACHEABILITY_TTL}.*" "Version expression has \$MODERATE_CACHEABILITY_TTL TTL"
    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "Version expression has MODERATE cacheability"

    # Test 5: Query parameter references=PRESERVE
    print_test "Query parameter: references=PRESERVE"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/1.0.0/content"
    purge_cache "${endpoint}?references=PRESERVE" "true"

    response=$(curl -s -D - "${endpoint}?references=PRESERVE")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Cache-Cacheability" "HIGH" "Cacheability is HIGH with references param"

    # Test 6: Query parameter references=DEREFERENCE (with artifact that has refs)
    print_test "Query parameter: references=DEREFERENCE"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID_WITH_REFS}/versions/1.0.0/content"
    purge_cache "${endpoint}?references=DEREFERENCE" "true"

    response=$(curl -s -D - "${endpoint}?references=DEREFERENCE")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "Cacheability is MODERATE with DEREFERENCE param"

    # Test 7: Query parameter references=REWRITE
    print_test "Query parameter: references=REWRITE"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/1.0.0/content"
    purge_cache "${endpoint}?references=REWRITE" "true"

    response=$(curl -s -D - "${endpoint}?references=REWRITE")
    headers=$(get_headers "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with references=REWRITE is MISS"
    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "Cacheability is MODERATE with references=REWRITE param"

    # Test 8: DRAFT version references - Verify cacheability behavior
    print_test "DRAFT version references - cacheability with references=DEREFERENCE"

    # Create new artifact that references the DRAFT version (use unique timestamp to avoid conflicts)
    local draft_timestamp=$(date +%s%N | cut -c1-13)  # milliseconds for uniqueness
    local draft_ref_artifact_id="cache-test-draft-ref-${draft_timestamp}"
    local draft_ref_content='{"type":"record","name":"DraftRefRecord","fields":[{"name":"id","type":"int"},{"name":"testRecord","type":"TestRecordDraft"}]}'
    local draft_ref_request=$(cat <<EOF
{
  "artifactId": "${draft_ref_artifact_id}",
  "artifactType": "AVRO",
  "firstVersion": {
    "version": "1.0.0",
    "content": {
      "content": $(echo "$draft_ref_content" | jq -R -s '.'),
      "contentType": "application/json",
      "references": [
        {
          "name": "TestRecordDraft",
          "groupId": "${GROUP_ID}",
          "artifactId": "${ARTIFACT_ID}",
          "version": "2.0.0-draft"
        }
      ]
    }
  }
}
EOF
)

    response=$(curl -s -w "\n%{http_code}" -X POST \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts" \
        -H "Content-Type: application/json" \
        -d "${draft_ref_request}")

    status=$(get_status_code "$response")
    if [ "$status" = "200" ]; then
        body=$(get_response_body "$response")
        local draft_ref_global_id=$(echo "$body" | grep -o '"globalId":[0-9]*' | head -1 | grep -o '[0-9]*')
        print_pass "Artifact with DRAFT reference created (globalId: $draft_ref_global_id)"
    else
        print_fail "Failed to create artifact with DRAFT reference (HTTP $status)"
        print_debug "Response body: $(get_response_body "$response")"
    fi

    # Fetch with references=DEREFERENCE to process DRAFT references (should be MODERATE)
    endpoint="${VARNISH_URL}${BASE_PATH}/ids/globalIds/${draft_ref_global_id}?references=DEREFERENCE"
    purge_cache "$endpoint" "true"

    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    body=$(get_body "$response")

    check_header "$headers" "X-Debug-Cache" "MISS" "First request with DEREFERENCE is MISS"
    check_header "$headers" "X-Cache-Cacheability" "MODERATE" "Cacheability is MODERATE when dereferencing DRAFT references"

    # Verify DRAFT content is present
    if echo "$body" | grep -q "TestRecordDraft"; then
        print_pass "Response contains dereferenced DRAFT content"
    else
        print_fail "Response should contain DRAFT version content"
        print_debug "Body: $body"
    fi

    # Fetch again immediately to populate cache
    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    body=$(get_body "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Second request is cache HIT"

    # Capture the original ETag to verify it changes after DRAFT update
    local original_etag=$(get_header_value "$headers" "ETag")

    # Update the DRAFT version content
    local updated_draft_content='{"type":"record","name":"TestRecordDraft","fields":[{"name":"id","type":"int"},{"name":"newField","type":"string"}]}'
    local update_draft_body=$(cat <<EOF
{
  "content": $(echo "$updated_draft_content" | jq -R -s '.'),
  "contentType": "application/json"
}
EOF
)

    response=$(curl -s -w "\n%{http_code}" -X PUT \
        "${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/2.0.0-draft/content" \
        -H "Content-Type: application/json" \
        -d "${update_draft_body}")

    status=$(get_status_code "$response")
    if [ "$status" = "204" ]; then
        print_pass "DRAFT version content updated"
    else
        print_fail "Failed to update DRAFT version content (HTTP $status)"
    fi

    # Fetch immediately after update - should still serve OLD content from cache (within TTL)
    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    body=$(get_body "$response")

    check_header "$headers" "X-Debug-Cache" "HIT" "Immediately after DRAFT update, cache still serves old content (HIT)"

    # Wait for MODERATE TTL to expire, then fetch again
    print_info "Waiting ${MODERATE_CACHEABILITY_TTL}s for cache to expire..."
    sleep $((MODERATE_CACHEABILITY_TTL + 2))

    # Fetch after TTL expires - cache should refresh with NEW content
    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    body=$(get_body "$response")
    local new_etag=$(get_header_value "$headers" "ETag")

    check_header "$headers" "X-Debug-Cache" "MISS" "After TTL expires, cache refreshes (MISS)"

    # Verify ETag changed (higher quality ETags detect DRAFT reference changes)
    if [ "$original_etag" != "$new_etag" ]; then
        print_pass "ETag changed after DRAFT update (higher quality ETags working)"
    else
        print_fail "ETag should change after DRAFT reference update"
        print_debug "Original ETag: $original_etag"
        print_debug "New ETag: $new_etag"
    fi

    # Verify the updated content is now served
    if echo "$body" | grep -q "newField"; then
        print_pass "Cache now serves updated DRAFT content"
    else
        print_fail "Cache should serve updated DRAFT content after TTL expiration"
        print_debug "Body: $body"
    fi

    # Test 10: 304 Not Modified
    print_test "Conditional request with If-None-Match"

    endpoint="${VARNISH_URL}${BASE_PATH}/groups/${GROUP_ID}/artifacts/${ARTIFACT_ID}/versions/1.0.0/content"
    response=$(curl -s -D - "$endpoint")
    headers=$(get_headers "$response")
    local etag=$(get_header_value "$headers" "ETag")

    local conditional_response=$(curl -s -w "\n%{http_code}" -H "If-None-Match: ${etag}" "$endpoint")
    local status=$(get_status_code "$conditional_response")

    if [ "$status" = "304" ]; then
        print_pass "Received 304 Not Modified"
    else
        print_fail "Expected 304 Not Modified, got $status"
    fi
}

###############################################################################
# Test Summary
###############################################################################

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

    # Run test suites based on selection
    case "$TEST_SUITE" in
        all)
            test_suite_ids_globalids
            test_suite_ids_contentids
            test_suite_ids_contenthashes
            test_suite_ids_refs_globalid
            test_suite_ids_refs_contentid
            test_suite_ids_refs_hash
            test_suite_version_content
            ;;
        ids-globalids)
            test_suite_ids_globalids
            ;;
        ids-contentids)
            test_suite_ids_contentids
            ;;
        ids-contenthashes)
            test_suite_ids_contenthashes
            ;;
        ids-refs-globalid)
            test_suite_ids_refs_globalid
            ;;
        ids-refs-contentid)
            test_suite_ids_refs_contentid
            ;;
        ids-refs-hash)
            test_suite_ids_refs_hash
            ;;
        version-content)
            test_suite_version_content
            ;;
        *)
            echo -e "${RED}Error: Unknown test suite: $TEST_SUITE${NC}"
            echo "Use --help to see available test suites"
            exit 1
            ;;
    esac

    # Print summary and exit
    if print_summary; then
        exit 0
    else
        exit 1
    fi
}

# Run main function
main "$@"
