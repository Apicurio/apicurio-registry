#!/bin/bash

################################################################################
# Apicurio Registry Migration Script: Verify Registry Version
#
# This script verifies the version and health of an Apicurio Registry deployment.
# It checks:
#   - Registry accessibility
#   - Version information
#   - Health status
#   - Basic system information
#
# Environment Variables (optional):
#   REGISTRY_URL              - Registry base URL (e.g., https://registry.example.com)
#   REGISTRY_VERSION          - API version to test (default: v3)
#   AUTH_ENABLED              - Set to "true" to enable OAuth2 authentication
#   AUTH_TOKEN_URL            - OAuth2 token endpoint URL
#   AUTH_CLIENT_ID            - OAuth2 client ID
#   AUTH_CLIENT_SECRET        - OAuth2 client secret
#   SKIP_TLS_VERIFY           - Set to "true" to skip TLS certificate verification
#
################################################################################

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

# Configuration
echo ""
info "Apicurio Registry Version Verification"
echo "========================================"
echo ""

# Prompt for required configuration
prompt REGISTRY_URL "Enter Registry URL (e.g., https://registry.example.com)" "http://localhost:8080"
prompt REGISTRY_VERSION "Enter API version to test" "v3"
prompt SKIP_TLS_VERIFY "Skip TLS certificate verification? (true/false)" "false"

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
info "Starting verification checks..."
echo "=========================================="
echo ""

# Check 1: Health endpoint
check "1. Checking health endpoint..."
HEALTH_URL="${REGISTRY_URL}/health/live"
HEALTH_STATUS=$(curl $CURL_OPTS -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" || echo "000")

if [ "$HEALTH_STATUS" -eq 200 ]; then
    success "✓ Health check passed (HTTP $HEALTH_STATUS)"
else
    error "✗ Health check failed (HTTP $HEALTH_STATUS)"
    exit 1
fi

# Check 2: System info endpoint
check "2. Checking system info endpoint..."
SYSTEM_INFO_URL="${REGISTRY_URL}/apis/registry/${REGISTRY_VERSION}/system/info"
SYSTEM_INFO=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$SYSTEM_INFO_URL" || echo "")

if [ -z "$SYSTEM_INFO" ]; then
    error "✗ Failed to retrieve system info from: $SYSTEM_INFO_URL"
    exit 1
fi

success "✓ System info retrieved successfully"

# Parse system info
REGISTRY_NAME=$(echo "$SYSTEM_INFO" | jq -r '.name' 2>/dev/null || echo "unknown")
REGISTRY_VERSION_ACTUAL=$(echo "$SYSTEM_INFO" | jq -r '.version' 2>/dev/null || echo "unknown")
REGISTRY_BUILT=$(echo "$SYSTEM_INFO" | jq -r '.builtOn' 2>/dev/null || echo "unknown")

echo ""
info "Registry Information:"
echo "  Name:       $REGISTRY_NAME"
echo "  Version:    $REGISTRY_VERSION_ACTUAL"
echo "  Built:      $REGISTRY_BUILT"
echo ""

# Check 3: Verify version
check "3. Verifying registry version..."
if [[ "$REGISTRY_VERSION" == "v2" ]]; then
  if [[ "$REGISTRY_VERSION_ACTUAL" =~ ^3\. ]]; then
      warn "✗ Registry is version 3.x: $REGISTRY_VERSION_ACTUAL"
      info "Expected version 2.x"
      VERSION_VERIFIED=false
  elif [[ "$REGISTRY_VERSION_ACTUAL" =~ ^2\. ]]; then
      success "✓ Registry is version 2.x: $REGISTRY_VERSION_ACTUAL"
      VERSION_VERIFIED=true
  else
      warn "✗ Unknown registry version: $REGISTRY_VERSION_ACTUAL"
      VERSION_VERIFIED=false
  fi
else
  if [[ "$REGISTRY_VERSION_ACTUAL" =~ ^3\. ]]; then
      success "✓ Registry is version 3.x: $REGISTRY_VERSION_ACTUAL"
      VERSION_VERIFIED=true
  elif [[ "$REGISTRY_VERSION_ACTUAL" =~ ^2\. ]]; then
      warn "✗ Registry is version 2.x: $REGISTRY_VERSION_ACTUAL"
      info "Expected version 3.x"
      VERSION_VERIFIED=false
  else
      warn "✗ Unknown registry version: $REGISTRY_VERSION_ACTUAL"
      VERSION_VERIFIED=false
  fi
fi


# Summary
echo ""
echo "=========================================="
echo "Verification Summary"
echo "=========================================="
echo ""

CHECKS_PASSED=0
CHECKS_TOTAL=3

if [[ "$HEALTH_STATUS" -eq 200 ]]; then
  CHECKS_PASSED=$((CHECKS_PASSED + 1))
fi
if [[ -n "$SYSTEM_INFO" ]]; then
  CHECKS_PASSED=$((CHECKS_PASSED + 1))
fi
if [[ "$VERSION_VERIFIED" = true ]]; then
  CHECKS_PASSED=$((CHECKS_PASSED + 1))
fi

echo "Checks passed: $CHECKS_PASSED / $CHECKS_TOTAL"
echo ""

if [ "$CHECKS_PASSED" -eq "$CHECKS_TOTAL" ]; then
    success "✓ All verification checks passed!"
    echo ""
    info "Registry Details:"
    echo "  URL:        $REGISTRY_URL"
    echo "  Version:    $REGISTRY_VERSION_ACTUAL"
    echo ""
    EXIT_CODE=0
else
    warn "⚠ Some verification checks failed"
    echo ""
    info "Review the output above for details"
    echo ""
    EXIT_CODE=1
fi

exit $EXIT_CODE
