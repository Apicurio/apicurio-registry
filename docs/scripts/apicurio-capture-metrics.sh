#!/bin/bash

################################################################################
# Apicurio Registry Migration Script: Capture Metrics
#
# This script captures metrics from an Apicurio Registry v2 deployment
# before migration to v3. It records:
#   - Total artifact count
#   - Sample of up to 5 artifacts with metadata
#   - Global rule configurations
#
# The output is saved to a text file for comparison after migration.
#
# Environment Variables (optional):
#   REGISTRY_URL              - Registry base URL (e.g., https://registry.example.com)
#   REGISTRY_API_VERSION      - API version (default: v2)
#   AUTH_ENABLED              - Set to "true" to enable OAuth2 authentication
#   AUTH_TOKEN_URL            - OAuth2 token endpoint URL
#   AUTH_CLIENT_ID            - OAuth2 client ID
#   AUTH_CLIENT_SECRET        - OAuth2 client secret
#   OUTPUT_FILE               - Output file path (default: registry-metrics.txt)
#   SKIP_TLS_VERIFY           - Set to "true" to skip TLS certificate verification
#
################################################################################

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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
info "Apicurio Registry Metrics Capture"
echo "================================="
echo ""

# Prompt for required configuration
prompt REGISTRY_URL "Enter Registry URL (e.g., https://registry.example.com)" "http://localhost:8080"
prompt REGISTRY_API_VERSION "Enter API version" "v2"
prompt OUTPUT_FILE "Enter output file path" "registry-metrics.txt"
prompt SKIP_TLS_VERIFY "Skip TLS certificate verification? (true/false)" "false"

# Build full API URL
REGISTRY_API_URL="${REGISTRY_URL}/apis/registry/${REGISTRY_API_VERSION}"

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

# Start capturing metrics
info "Capturing metrics from $REGISTRY_API_URL"
echo ""

# Initialize output file
cat > "$OUTPUT_FILE" << EOF
Apicurio Registry Metrics
===================================
Captured: $(date)
Registry URL: $REGISTRY_API_URL
API Version: $REGISTRY_API_VERSION

EOF

# Check registry accessibility
info "Checking registry accessibility..."
SYSTEM_INFO=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$REGISTRY_API_URL/system/info" || echo "")

if [ -z "$SYSTEM_INFO" ]; then
    error "Registry is not accessible at $REGISTRY_API_URL"
fi

REGISTRY_VERSION=$(echo "$SYSTEM_INFO" | jq -r '.version' 2>/dev/null || echo "unknown")
info "Registry version: $REGISTRY_VERSION"

cat >> "$OUTPUT_FILE" << EOF
Registry Version: $REGISTRY_VERSION

EOF

# Capture total artifact count
info "Capturing total artifact count..."
SEARCH_RESULT=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$REGISTRY_API_URL/search/artifacts?limit=1" || echo "{}")
TOTAL_ARTIFACTS=$(echo "$SEARCH_RESULT" | jq -r '.count' 2>/dev/null || echo "0")

info "Total artifacts: $TOTAL_ARTIFACTS"

cat >> "$OUTPUT_FILE" << EOF
Total Artifacts: $TOTAL_ARTIFACTS

EOF

# Capture sample artifacts (up to 5)
info "Capturing sample artifacts (up to 5)..."

SAMPLE_ARTIFACTS=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$REGISTRY_API_URL/search/artifacts?limit=5" || echo "{}")
ARTIFACT_COUNT=$(echo "$SAMPLE_ARTIFACTS" | jq -r '.artifacts | length' 2>/dev/null || echo "0")

echo $SAMPLE_ARTIFACTS | jq

cat >> "$OUTPUT_FILE" << EOF
Sample Artifacts ($ARTIFACT_COUNT artifacts):
-----------------------------------------
EOF

if [ "$ARTIFACT_COUNT" -gt 0 ]; then
    echo "$SAMPLE_ARTIFACTS" | jq -r '.artifacts[] |
"
Artifact ID: \(.id // .artifactId)
  Group: \(.groupId // "default")
  Type: \(.type // .artifactType)
  Name: \(.name // "N/A")
  Created: \(.createdOn // "N/A")
  Modified: \(.modifiedOn // "N/A")
  Labels: \(.labels // {} | to_entries | map("\(.key)=\(.value)") | join(", ") | if . == "" then "none" else . end)
"' >> "$OUTPUT_FILE"
else
    echo "  (No artifacts found)" >> "$OUTPUT_FILE"
fi

# Capture global rules
info "Capturing global rules configuration..."

GLOBAL_RULES=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$REGISTRY_API_URL/admin/rules" || echo "[]")
RULE_COUNT=$(echo "$GLOBAL_RULES" | jq -r 'length' 2>/dev/null || echo "0")

cat >> "$OUTPUT_FILE" << EOF

Global Rules Configuration ($RULE_COUNT rules):
----------------------------------------
EOF

if [ "$RULE_COUNT" -gt 0 ]; then
    # Iterate through each rule name and fetch its configuration
    echo "$GLOBAL_RULES" | jq -r '.[]' | while read -r RULE_TYPE; do
        RULE_CONFIG=$(curl $CURL_OPTS -s -H "$AUTH_HEADER" "$REGISTRY_API_URL/admin/rules/$RULE_TYPE" || echo "{}")
        RULE_CONFIG_VALUE=$(echo "$RULE_CONFIG" | jq -r '.config' 2>/dev/null || echo "N/A")

        cat >> "$OUTPUT_FILE" << RULE_EOF

Rule Type: $RULE_TYPE
  Configuration: $RULE_CONFIG_VALUE
RULE_EOF
    done
else
    echo "  (No global rules configured)" >> "$OUTPUT_FILE"
fi

# Summary footer
cat >> "$OUTPUT_FILE" << EOF

========================================
Metrics capture complete
Output saved to: $OUTPUT_FILE
EOF

# Display summary to console
echo ""
info "Metrics captured successfully!"
echo ""
echo "Summary:"
echo "  Registry Version: $REGISTRY_VERSION"
echo "  Total Artifacts: $TOTAL_ARTIFACTS"
echo "  Sample Artifacts: $ARTIFACT_COUNT"
echo "  Global Rules: $RULE_COUNT"
echo ""
echo "Full metrics saved to: $OUTPUT_FILE"
echo ""

# Display sample output
info "Sample output (first 20 lines):"
head -20 "$OUTPUT_FILE"
echo ""
info "Use this metrics file to compare against the v3 registry after migration"
