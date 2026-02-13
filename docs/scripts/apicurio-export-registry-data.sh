#!/bin/bash

################################################################################
# Apicurio Registry Migration Script: Export Data
#
# This script exports all data from an Apicurio Registry v2 deployment.
# The export includes:
#   - All artifacts and versions
#   - Artifact metadata (labels, properties, descriptions)
#   - Global and artifact-specific rules
#   - Artifact references
#
# The export is saved as a ZIP file that can be imported into a v3 deployment.
#
# Environment Variables (optional):
#   REGISTRY_URL              - Registry base URL (e.g., https://registry.example.com)
#   REGISTRY_API_VERSION      - API version (default: v2)
#   AUTH_ENABLED              - Set to "true" to enable OAuth2 authentication
#   AUTH_TOKEN_URL            - OAuth2 token endpoint URL
#   AUTH_CLIENT_ID            - OAuth2 client ID
#   AUTH_CLIENT_SECRET        - OAuth2 client secret
#   EXPORT_FILE               - Export file path (default: registry-export.zip)
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
info "Apicurio Registry Data Export"
echo "=============================="
echo ""

# Prompt for required configuration
prompt REGISTRY_URL "Enter Registry URL (e.g., https://registry.example.com)" "http://localhost:8080"
prompt REGISTRY_API_VERSION "Enter API version" "v2"
prompt EXPORT_FILE "Enter export file path" "registry-export.zip"
prompt SKIP_TLS_VERIFY "Skip TLS certificate verification? (true/false)" "false"

# Build full API URL
EXPORT_ENDPOINT="${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/admin/export"

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
    fi

    info "Access token obtained successfully"
    AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
else
    AUTH_HEADER=""
fi

# Check if export file already exists
if [ -f "$EXPORT_FILE" ]; then
    warn "Export file already exists: $EXPORT_FILE"
    read -p "Overwrite? (y/n): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        error "Export cancelled by user"
    fi
    rm -f "$EXPORT_FILE"
    info "Removed existing export file"
fi

# Check registry accessibility
info "Checking registry accessibility..."
SYSTEM_INFO=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}/system/info" || echo "")

if [ -z "$SYSTEM_INFO" ]; then
    error "Registry is not accessible at ${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}"
fi

REGISTRY_VERSION=$(echo "$SYSTEM_INFO" | jq -r '.version' 2>/dev/null || echo "unknown")
info "Registry version: $REGISTRY_VERSION"
echo ""

# Perform export
info "Exporting data from registry..."
info "Export endpoint: $EXPORT_ENDPOINT"
echo ""

START_TIME=$(date +%s)

HTTP_CODE=$(curl $CURL_OPTS -w "%{http_code}" -o "$EXPORT_FILE" -s \
    -X GET "$EXPORT_ENDPOINT" \
    -H "$AUTH_HEADER")

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""

if [ "$HTTP_CODE" -eq 200 ]; then
    success "Export completed successfully (HTTP $HTTP_CODE)"
    info "Export duration: ${DURATION} seconds"
else
    error "Export failed (HTTP $HTTP_CODE). Check file for error message: $EXPORT_FILE"
fi

# Verify export file
info "Verifying export file..."

if [ ! -f "$EXPORT_FILE" ]; then
    error "Export file not found: $EXPORT_FILE"
fi

FILE_SIZE_BYTES=$(stat -c%s "$EXPORT_FILE" 2>/dev/null || stat -f%z "$EXPORT_FILE" 2>/dev/null)
FILE_SIZE_HUMAN=$(ls -lh "$EXPORT_FILE" | awk '{print $5}')

if [ "$FILE_SIZE_BYTES" -lt 100 ]; then
    error "Export file is too small ($FILE_SIZE_HUMAN). Contents:"
    cat "$EXPORT_FILE"
    echo ""
    exit 1
fi

success "Export file created: $EXPORT_FILE"
info "File size: $FILE_SIZE_HUMAN ($FILE_SIZE_BYTES bytes)"

# Validate ZIP format
if command -v unzip &> /dev/null; then
    info "Validating ZIP format..."
    if unzip -t "$EXPORT_FILE" > /dev/null 2>&1; then
        success "Valid ZIP file format"
        echo ""
        info "ZIP file contents:"
        unzip -l "$EXPORT_FILE" | head -20
        ENTRY_COUNT=$(unzip -l "$EXPORT_FILE" | tail -1 | awk '{print $2}')
        info "Total entries in ZIP: $ENTRY_COUNT"
    else
        error "Invalid ZIP file format"
    fi
else
    warn "unzip command not available, skipping ZIP validation"
fi

# Summary
echo ""
echo "=========================================="
success "Export completed successfully!"
echo "=========================================="
echo ""
echo "Export Details:"
echo "  Registry URL:     ${REGISTRY_URL}"
echo "  Registry Version: ${REGISTRY_VERSION}"
echo "  Export File:      ${EXPORT_FILE}"
echo "  File Size:        ${FILE_SIZE_HUMAN}"
echo "  Duration:         ${DURATION} seconds"
echo ""
info "This export file can now be imported into an Apicurio Registry v3 deployment"
info "Use script: 03-import-registry-data.sh"
echo ""
