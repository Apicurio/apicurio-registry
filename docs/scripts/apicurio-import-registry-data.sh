#!/bin/bash

################################################################################
# Apicurio Registry Migration Script: Import Data
#
# This script imports data into an Apicurio Registry v3 deployment from
# an export file created by an Apicurio Registry v2 deployment.
#
# The import preserves:
#   - All artifacts and versions
#   - Artifact metadata (labels, properties, descriptions)
#   - Global and artifact-specific rules
#   - Artifact references
#
# Environment Variables (optional):
#   REGISTRY_URL              - Registry base URL (e.g., https://registry.example.com)
#   REGISTRY_API_VERSION      - API version (default: v3)
#   AUTH_ENABLED              - Set to "true" to enable OAuth2 authentication
#   AUTH_TOKEN_URL            - OAuth2 token endpoint URL
#   AUTH_CLIENT_ID            - OAuth2 client ID
#   AUTH_CLIENT_SECRET        - OAuth2 client secret
#   IMPORT_FILE               - Import file path (default: registry-export.zip)
#   SKIP_TLS_VERIFY           - Set to "true" to skip TLS certificate verification
#
################################################################################

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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
    exit 1
}

success() {
    echo -e "${BLUE}[SUCCESS]${NC} $1"
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
info "Apicurio Registry Data Import"
echo "=============================="
echo ""

# Prompt for required configuration
prompt REGISTRY_URL "Enter Registry URL (e.g., https://registry.example.com)" "http://localhost:8080"
prompt REGISTRY_API_VERSION "Enter API version" "v3"
prompt IMPORT_FILE "Enter import file path" "registry-export.zip"
prompt SKIP_TLS_VERIFY "Skip TLS certificate verification? (true/false)" "false"

# Build full API URL
IMPORT_ENDPOINT="${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/admin/import"

# TLS verification flag
if [ "$SKIP_TLS_VERIFY" = "true" ]; then
    CURL_OPTS="-k"
    warn "TLS certificate verification is disabled"
else
    CURL_OPTS=""
fi

# Verify import file exists
if [ ! -f "$IMPORT_FILE" ]; then
    error "Import file not found: $IMPORT_FILE"
fi

FILE_SIZE_BYTES=$(stat -c%s "$IMPORT_FILE" 2>/dev/null || stat -f%z "$IMPORT_FILE" 2>/dev/null)
FILE_SIZE_HUMAN=$(ls -lh "$IMPORT_FILE" | awk '{print $5}')

info "Import file: $IMPORT_FILE ($FILE_SIZE_HUMAN)"

# Validate ZIP format
if command -v unzip &> /dev/null; then
    if ! unzip -t "$IMPORT_FILE" > /dev/null 2>&1; then
        error "Import file is not a valid ZIP file"
    fi
    info "Import file is valid ZIP format"
else
    warn "unzip command not available, skipping ZIP validation"
fi

echo ""

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
    fi

    info "Access token obtained successfully"
    AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
else
    AUTH_HEADER=""
fi

# Check registry accessibility
info "Checking registry v3 accessibility..."
SYSTEM_INFO=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/system/info" || echo "")

if [ -z "$SYSTEM_INFO" ]; then
    error "Registry is not accessible at ${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}"
fi

REGISTRY_VERSION=$(echo "$SYSTEM_INFO" | jq -r '.version' 2>/dev/null || echo "unknown")
info "Registry version: $REGISTRY_VERSION"

# Verify this is a v3 registry
if [[ ! "$REGISTRY_VERSION" =~ ^3\. ]]; then
    warn "Registry version does not appear to be 3.x: $REGISTRY_VERSION"
    read -p "Continue anyway? (y/n): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        error "Import cancelled by user"
    fi
fi

echo ""

# Check current artifact count
info "Checking current registry state..."
CURRENT_ARTIFACTS=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/search/artifacts?limit=1" | jq -r '.count' 2>/dev/null || echo "0")
info "Current artifacts in registry: $CURRENT_ARTIFACTS"

if [ "$CURRENT_ARTIFACTS" -gt 0 ]; then
    warn "Registry already contains $CURRENT_ARTIFACTS artifacts"
    warn "Import will add to existing data, not replace it"
    read -p "Continue with import? (y/n): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        error "Import cancelled by user"
    fi
fi

echo ""

# Perform import
info "Importing data into registry..."
info "Import endpoint: $IMPORT_ENDPOINT"
echo ""

START_TIME=$(date +%s)

HTTP_CODE=$(curl $CURL_OPTS -w "%{http_code}" -o /tmp/import-response.txt -s \
    -X POST "$IMPORT_ENDPOINT" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/zip" \
    --data-binary "@$IMPORT_FILE")

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 204 ]; then
    success "Import completed successfully (HTTP $HTTP_CODE)"
    info "Import duration: ${DURATION} seconds"
else
    error "Import failed (HTTP $HTTP_CODE). Response:"
    cat /tmp/import-response.txt
    echo ""
    rm -f /tmp/import-response.txt
    exit 1
fi

rm -f /tmp/import-response.txt

# Wait for import to be fully processed
info "Waiting for import to be processed..."
sleep 3

# Verify import
info "Verifying import..."
NEW_ARTIFACTS=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/search/artifacts?limit=1" | jq -r '.count' 2>/dev/null || echo "0")
IMPORTED_COUNT=$((NEW_ARTIFACTS - CURRENT_ARTIFACTS))

info "Artifacts before import: $CURRENT_ARTIFACTS"
info "Artifacts after import:  $NEW_ARTIFACTS"
info "New artifacts imported:  $IMPORTED_COUNT"

if [ "$IMPORTED_COUNT" -lt 1 ]; then
    warn "No new artifacts detected after import"
    warn "Import may have failed or all artifacts already existed"
fi

# Check global rules
info "Checking global rules..."
GLOBAL_RULES=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/admin/rules" | jq -r 'length' 2>/dev/null || echo "0")
info "Global rules configured: $GLOBAL_RULES"

# Summary
echo ""
echo "=========================================="
success "Import process completed!"
echo "=========================================="
echo ""
echo "Import Details:"
echo "  Registry URL:         ${REGISTRY_URL}"
echo "  Registry Version:     ${REGISTRY_VERSION}"
echo "  Import File:          ${IMPORT_FILE}"
echo "  File Size:            ${FILE_SIZE_HUMAN}"
echo "  Duration:             ${DURATION} seconds"
echo ""
echo "Registry State:"
echo "  Artifacts (before):   ${CURRENT_ARTIFACTS}"
echo "  Artifacts (after):    ${NEW_ARTIFACTS}"
echo "  New artifacts:        ${IMPORTED_COUNT}"
echo "  Global rules:         ${GLOBAL_RULES}"
echo ""
