#!/bin/bash

# Apicurio Registry Envoy+OPA Integration Test Script
# This script tests authentication and authorization through Envoy Proxy and OPA

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
KEYCLOAK_URL="http://localhost:8080"
REGISTRY_URL="http://localhost:8081"
REALM="registry"
CLIENT_ID="apicurio-registry"

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TOTAL_TESTS=0

# Helper functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_test() {
    echo -e "${YELLOW}TEST $TOTAL_TESTS:${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓ PASS:${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

print_failure() {
    echo -e "${RED}✗ FAIL:${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

print_info() {
    echo -e "${BLUE}INFO:${NC} $1"
}

# Wait for services to be ready
wait_for_service() {
    local url=$1
    local name=$2
    local max_attempts=30
    local attempt=1

    print_info "Waiting for $name to be ready..."

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f -o /dev/null "$url"; then
            print_success "$name is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    print_failure "$name failed to start after $max_attempts attempts"
    return 1
}

# Get JWT token for a user
get_token() {
    local username=$1
    local password=$2

    local response=$(curl -s -X POST \
        "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=${CLIENT_ID}" \
        -d "username=${username}" \
        -d "password=${password}")

    echo "$response" | jq -r '.access_token'
}

# Test HTTP request
test_request() {
    local method=$1
    local path=$2
    local token=$3
    local expected_status=$4
    local description=$5
    local data=$6

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    print_test "$description"

    local auth_header=""
    if [ -n "$token" ]; then
        auth_header="-H \"Authorization: Bearer $token\""
    fi

    local data_param=""
    if [ -n "$data" ]; then
        data_param="-d '$data' -H 'Content-Type: application/json'"
    fi

    local cmd="curl -s -w '\n%{http_code}' -X $method ${REGISTRY_URL}${path} $auth_header $data_param"
    local response=$(eval $cmd)

    local status_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$status_code" = "$expected_status" ]; then
        print_success "Expected $expected_status, got $status_code"
        return 0
    else
        print_failure "Expected $expected_status, got $status_code"
        if [ -n "$body" ]; then
            echo -e "  Response body: $body"
        fi
        return 1
    fi
}

# Main test execution
print_header "Apicurio Registry Envoy+OPA Integration Tests"

# Check for required tools
print_info "Checking for required tools..."
command -v curl >/dev/null 2>&1 || { echo "curl is required but not installed. Aborting." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq is required but not installed. Aborting." >&2; exit 1; }
print_success "All required tools are available"

# Wait for services
print_header "Waiting for Services to Start"
wait_for_service "${KEYCLOAK_URL}/realms/${REALM}" "Keycloak"

# Check if Envoy is responding (401 is expected without auth)
print_info "Waiting for Envoy+Registry to be ready..."
max_attempts=30
attempt=1
envoy_ready=false
while [ $attempt -le $max_attempts ]; do
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "${REGISTRY_URL}/apis/registry/v3/system/info")
    if [ "$status_code" = "401" ] || [ "$status_code" = "200" ]; then
        print_success "Envoy+Registry is ready"
        envoy_ready=true
        break
    fi
    echo -n "."
    sleep 2
    ((attempt++))
done

if [ "$envoy_ready" = "false" ]; then
    print_failure "Envoy+Registry failed to start after $max_attempts attempts"
    echo "Please check: docker compose logs envoy"
    exit 1
fi

sleep 5  # Additional grace period

# Obtain tokens for different users
print_header "Obtaining JWT Tokens"

print_info "Getting token for admin user..."
ADMIN_TOKEN=$(get_token "admin" "admin")
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    print_failure "Failed to obtain admin token"
    exit 1
fi
print_success "Admin token obtained"

print_info "Getting token for developer user..."
DEVELOPER_TOKEN=$(get_token "developer" "developer")
if [ -z "$DEVELOPER_TOKEN" ] || [ "$DEVELOPER_TOKEN" = "null" ]; then
    print_failure "Failed to obtain developer token"
    exit 1
fi
print_success "Developer token obtained"

print_info "Getting token for readonly user..."
READONLY_TOKEN=$(get_token "user" "user")
if [ -z "$READONLY_TOKEN" ] || [ "$READONLY_TOKEN" = "null" ]; then
    print_failure "Failed to obtain readonly token"
    exit 1
fi
print_success "Readonly token obtained"

# Test Suite 1: Unauthenticated Access
print_header "Test Suite 1: Unauthenticated Access"

test_request "GET" "/apis/registry/v3/groups" "" "401" \
    "Unauthenticated GET should return 401"

test_request "POST" "/apis/registry/v3/groups" "" "401" \
    "Unauthenticated POST should return 401" \
    '{"groupId":"test","description":"test"}'

# Test Suite 2: Admin User (Full Access)
print_header "Test Suite 2: Admin User Access (sr-admin role)"

test_request "GET" "/apis/registry/v3/groups" "$ADMIN_TOKEN" "200" \
    "Admin GET /groups should succeed"

test_request "POST" "/apis/registry/v3/groups" "$ADMIN_TOKEN" "200" \
    "Admin POST /groups should succeed" \
    '{"groupId":"admin-group","description":"Created by admin"}'

test_request "GET" "/apis/registry/v3/groups/admin-group" "$ADMIN_TOKEN" "200" \
    "Admin GET specific group should succeed"

test_request "DELETE" "/apis/registry/v3/groups/admin-group" "$ADMIN_TOKEN" "204" \
    "Admin DELETE /groups should succeed"

# Test Suite 3: Developer User (Read + Write)
print_header "Test Suite 3: Developer User Access (sr-developer role)"

test_request "GET" "/apis/registry/v3/groups" "$DEVELOPER_TOKEN" "200" \
    "Developer GET /groups should succeed"

test_request "POST" "/apis/registry/v3/groups" "$DEVELOPER_TOKEN" "200" \
    "Developer POST /groups should succeed" \
    '{"groupId":"dev-group","description":"Created by developer"}'

test_request "GET" "/apis/registry/v3/groups/dev-group" "$DEVELOPER_TOKEN" "200" \
    "Developer GET specific group should succeed"

test_request "DELETE" "/apis/registry/v3/groups/dev-group" "$DEVELOPER_TOKEN" "204" \
    "Developer DELETE /groups should succeed"

# Test Suite 4: Read-Only User (Read Only)
print_header "Test Suite 4: Read-Only User Access (sr-readonly role)"

test_request "GET" "/apis/registry/v3/groups" "$READONLY_TOKEN" "200" \
    "Read-only GET /groups should succeed"

test_request "POST" "/apis/registry/v3/groups" "$READONLY_TOKEN" "403" \
    "Read-only POST /groups should be denied (403)" \
    '{"groupId":"readonly-group","description":"Should fail"}'

test_request "PUT" "/apis/registry/v3/groups/test" "$READONLY_TOKEN" "403" \
    "Read-only PUT should be denied (403)" \
    '{"description":"Should fail"}'

test_request "DELETE" "/apis/registry/v3/groups/test" "$READONLY_TOKEN" "403" \
    "Read-only DELETE should be denied (403)"

# Test Suite 5: Invalid/Expired Tokens
print_header "Test Suite 5: Invalid Tokens"

INVALID_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

test_request "GET" "/apis/registry/v3/groups" "$INVALID_TOKEN" "401" \
    "Invalid token should return 401"

# Test Suite 6: System Endpoints
print_header "Test Suite 6: System Endpoints"

test_request "GET" "/apis/registry/v3/system/info" "$ADMIN_TOKEN" "200" \
    "Admin GET /system/info should succeed"

test_request "GET" "/apis/registry/v3/system/info" "$READONLY_TOKEN" "200" \
    "Read-only GET /system/info should succeed"

# Test Suite 7: Anonymous Access to System Endpoints
print_header "Test Suite 7: Anonymous System Endpoint Access"

test_request "GET" "/apis/registry/v3/system/info" "" "200" \
    "Anonymous GET /v3/system/info should succeed"

test_request "GET" "/apis/registry/v2/system/info" "" "200" \
    "Anonymous GET /v2/system/info should succeed"

test_request "GET" "/apis/registry/v3/groups" "" "401" \
    "Anonymous GET /groups should be denied (401)"

# Print summary
print_header "Test Summary"

echo -e "Total Tests:  $TOTAL_TESTS"
echo -e "${GREEN}Passed:       $TESTS_PASSED${NC}"
echo -e "${RED}Failed:       $TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed! ✓${NC}\n"
    exit 0
else
    echo -e "\n${RED}Some tests failed! ✗${NC}\n"
    exit 1
fi
