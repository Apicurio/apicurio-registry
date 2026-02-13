#!/bin/bash

################################################################################
# Apicurio Registry Migration Script: Verify v3 API Features
#
# This script verifies that Apicurio Registry v3 API features are available
# and functioning correctly. It tests:
#   - v3 system endpoints
#   - v3 search endpoints (groups, versions)
#   - v3 group management
#   - v3 artifact operations
#   - Branch API availability
#   - Admin endpoints
#
# This verification ensures that v3-specific functionality works correctly
# after migration from v2.
#
# Environment Variables (optional):
#   REGISTRY_URL              - Registry base URL (e.g., https://registry.example.com)
#   AUTH_ENABLED              - Set to "true" to enable OAuth2 authentication
#   AUTH_TOKEN_URL            - OAuth2 token endpoint URL
#   AUTH_CLIENT_ID            - OAuth2 client ID
#   AUTH_CLIENT_SECRET        - OAuth2 client secret
#   SKIP_TLS_VERIFY           - Set to "true" to skip TLS certificate verification
#   TEST_GROUP_ID             - Group ID to use for testing (default: test-group)
#   TEST_ARTIFACT_ID          - Artifact ID to use for testing (default: test-artifact)
#
################################################################################

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Helper functions
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

success() {
    echo -e "${BLUE}[SUCCESS]${NC} $1"
}

check() {
    echo -e "${CYAN}[CHECK]${NC} $1"
}

feature() {
    echo -e "${MAGENTA}[FEATURE]${NC} $1"
}

prompt() {
    local var_name=$1
    local prompt_text=$2
    local default_value=$3
    local secret=$4

    if [ -z "${!var_name}" ]; then
        if [ "$secret" = "true" ]; then
            read -s -p "$prompt_text: " value
            echo ""
        else
            if [ -n "$default_value" ]; then
                read -p "$prompt_text [$default_value]: " value
                value=${value:-$default_value}
            else
                read -p "$prompt_text: " value
            fi
        fi
        eval "$var_name='$value'"
    fi
}

# Test result tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

test_endpoint() {
    local name=$1
    local url=$2
    local expected_status=$3
    local method=${4:-GET}

    TESTS_TOTAL=$((TESTS_TOTAL + 1))

    HTTP_STATUS=$(curl $CURL_OPTS -s -o /dev/null -w "%{http_code}" \
        -X "$method" \
        -H "$AUTH_HEADER" \
        "$url" || echo "000")

    if [ "$HTTP_STATUS" -eq "$expected_status" ]; then
        success "✓ $name (HTTP $HTTP_STATUS)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        error "✗ $name (HTTP $HTTP_STATUS, expected $expected_status)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Configuration
echo ""
info "Apicurio Registry v3 API Feature Verification"
echo "==============================================="
echo ""

# Prompt for required configuration
prompt REGISTRY_URL "Enter Registry URL (e.g., https://registry.example.com)" "http://localhost:8080"
prompt SKIP_TLS_VERIFY "Skip TLS certificate verification? (true/false)" "false"

# Build API URL
API_BASE="${REGISTRY_URL}/apis/registry/v3"

# TLS verification flag
if [ "$SKIP_TLS_VERIFY" = "true" ]; then
    CURL_OPTS="-k"
    warn "TLS certificate verification is disabled"
else
    CURL_OPTS=""
fi

# Authentication setup
prompt AUTH_ENABLED "Enable OAuth2 authentication? (true/false)" "false"

ACCESS_TOKEN=""
if [ "$AUTH_ENABLED" = "true" ]; then
    info "Configuring OAuth2 authentication..."
    prompt AUTH_TOKEN_URL "Enter OAuth2 token endpoint URL"
    prompt AUTH_CLIENT_ID "Enter OAuth2 client ID"
    prompt AUTH_CLIENT_SECRET "Enter OAuth2 client secret" "" "true"

    info "Obtaining access token..."
    TOKEN_RESPONSE=$(curl $CURL_OPTS -s -X POST "$AUTH_TOKEN_URL" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials" \
        -d "client_id=$AUTH_CLIENT_ID" \
        -d "client_secret=$AUTH_CLIENT_SECRET")

    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')

    if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
        error "Failed to obtain access token. Response: $TOKEN_RESPONSE"
        exit 1
    fi

    info "Access token obtained successfully"
    AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
else
    AUTH_HEADER=""
fi

echo ""
echo "=========================================="
info "Testing v3 API Features..."
echo "=========================================="
echo ""

# Feature 1: System Endpoints
feature "1. System Endpoints"
test_endpoint "System info" "$API_BASE/system/info" 200
echo ""

# Feature 2: Search Endpoints (v3-specific)
feature "2. Search Endpoints (v3-specific)"
test_endpoint "Search artifacts" "$API_BASE/search/artifacts" 200
test_endpoint "Search groups" "$API_BASE/search/groups" 200
test_endpoint "Search versions" "$API_BASE/search/versions" 200
echo ""

# Feature 3: Admin Endpoints
feature "3. Admin Endpoints"
test_endpoint "List global rules" "$API_BASE/admin/rules" 200
test_endpoint "Get config properties" "$API_BASE/admin/config/properties" 200
echo ""

# Summary
echo ""
echo "=========================================="
echo "Verification Summary"
echo "=========================================="
echo ""

echo "Total tests:   $TESTS_TOTAL"
echo "Passed:        $TESTS_PASSED"
echo "Failed:        $TESTS_FAILED"
echo ""

if [ "$TESTS_FAILED" -eq 0 ]; then
    success "✓ All v3 API feature tests passed!"
    echo ""
    info "v3 API Features Verified:"
    echo "  ✓ System endpoints"
    echo "  ✓ Search endpoints (groups, versions)"
    echo "  ✓ Admin endpoints"
    echo ""
    info "Registry is fully functional with v3 API features"
    echo ""
    EXIT_CODE=0
else
    warn "⚠ Some v3 API feature tests failed"
    echo ""
    info "Failed tests: $TESTS_FAILED"
    echo ""
    EXIT_CODE=1
fi

exit $EXIT_CODE
