#!/bin/bash
#
# Scenario 2: Version Management
# Demonstrates artifact versioning capabilities
#

REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
API_BASE="$REGISTRY_URL/apis/registry/v3"
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() { echo -e "\n${BLUE}══════════════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"; }
print_step() { echo -e "${GREEN}▶ $1${NC}"; }
print_info() { echo -e "${YELLOW}  $1${NC}"; }

print_header "Scenario 2: Version Management"

echo ""
echo "This scenario demonstrates how Apicurio Registry manages multiple"
echo "versions of artifacts, enabling prompt and agent evolution."
echo ""

# Step 1: List all versions of an artifact
print_step "Step 1: List all versions of code-review-prompt"
echo ""
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions" | \
    jq '.versions[] | {version, state, createdOn}'
echo ""

# Step 2: Get content of v1.0.0
print_step "Step 2: Get content of v1.0.0 (original)"
echo ""
echo -e "${CYAN}--- Version 1.0.0 Header ---${NC}"
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/1.0.0/content" | head -15
echo "..."
echo ""

# Step 3: Get content of v2.0.0
print_step "Step 3: Get content of v2.0.0 (enhanced)"
echo ""
echo -e "${CYAN}--- Version 2.0.0 Header ---${NC}"
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/2.0.0/content" | head -20
echo "..."
echo ""

# Step 4: Compare versions (show changelog)
print_step "Step 4: Show v2.0.0 changelog"
echo ""
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/2.0.0/content" | \
    grep -A 20 "^changelog:" | head -15
echo ""

# Step 5: Get latest version
print_step "Step 5: Get latest version (always get the newest)"
echo ""
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=latest/content" | \
    head -10
echo ""

# Step 6: Create a new version (demonstration)
print_step "Step 6: Version creation example (not executed)"
echo ""
echo -e "${YELLOW}To create a new version, POST to:${NC}"
echo "  POST $API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions"
echo ""
echo "With body:"
cat << 'EXAMPLE'
{
  "version": "2.1.0",
  "content": {
    "content": "<yaml content here>",
    "contentType": "application/x-yaml"
  }
}
EXAMPLE
echo ""

print_header "Scenario Complete"
echo ""
echo "Key takeaways:"
echo "  • Artifacts can have multiple versions"
echo "  • Each version has metadata (state, createdOn)"
echo "  • Retrieve specific version: /versions/{version}/content"
echo "  • Retrieve latest: /versions/branch=latest/content"
echo ""
