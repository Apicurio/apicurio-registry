#!/bin/bash
#
# Scenario 5: Search and Labels
# Demonstrates advanced search capabilities and label-based filtering
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

print_header "Scenario 5: Search and Labels"

echo ""
echo "Labels enable rich categorization and filtering of artifacts."
echo ""

# Step 1: Show artifact with labels
print_step "Step 1: View labels on code-review-agent"
echo ""
curl -sf "$API_BASE/groups/claude-demo.agents/artifacts/code-review-agent" | \
    jq '{artifactId, labels}'
echo ""

# Step 2: Search by artifact type
print_step "Step 2: Search by artifact type (AGENT_CARD)"
echo ""
curl -sf "$API_BASE/search/artifacts?artifactType=AGENT_CARD" | \
    jq '.artifacts[] | "\(.groupId)/\(.artifactId)"'
echo ""

# Step 3: Search by artifact type (MODEL_SCHEMA)
print_step "Step 3: Search by artifact type (MODEL_SCHEMA)"
echo ""
curl -sf "$API_BASE/search/artifacts?artifactType=MODEL_SCHEMA" | \
    jq '.artifacts[] | "\(.groupId)/\(.artifactId)"'
echo ""

# Step 4: Search by artifact type (AGENT_WORKFLOW)
print_step "Step 4: Search by artifact type (AGENT_WORKFLOW)"
echo ""
curl -sf "$API_BASE/search/artifacts?artifactType=AGENT_WORKFLOW" | \
    jq '.artifacts[] | "\(.groupId)/\(.artifactId)"'
echo ""

# Step 5: Search by name pattern
print_step "Step 5: Search by name pattern"
echo ""
echo -e "${CYAN}Searching for 'review':${NC}"
curl -sf "$API_BASE/search/artifacts?name=review" | \
    jq '.artifacts[] | "\(.groupId)/\(.artifactId) (\(.artifactType))"'
echo ""

# Step 6: Search by label
print_step "Step 6: Search by label (capability=security)"
echo ""
curl -sf "$API_BASE/search/artifacts?labels=capability:security" 2>/dev/null | \
    jq '.artifacts[] | "\(.groupId)/\(.artifactId)"' 2>/dev/null || \
    echo "  (Label search syntax may vary by registry version)"
echo ""

# Step 7: Combine filters
print_step "Step 7: Combined search (type + name)"
echo ""
echo -e "${CYAN}Prompt templates containing 'code':${NC}"
curl -sf "$API_BASE/search/artifacts?artifactType=PROMPT_TEMPLATE&name=code" | \
    jq '.artifacts[] | "\(.artifactId) v\(.version)"'
echo ""

# Step 8: Search within groups
print_step "Step 8: List artifacts in workflow group"
echo ""
curl -sf "$API_BASE/groups/claude-demo.workflows/artifacts" | \
    jq '.artifacts[] | {artifactId, labels}'
echo ""

print_header "Scenario Complete"
echo ""
echo "Search and labels enable:"
echo "  • Finding artifacts by type (AGENT_CARD, PROMPT_TEMPLATE, etc.)"
echo "  • Filtering by name patterns"
echo "  • Label-based categorization and filtering"
echo "  • Combining multiple search criteria"
echo ""
