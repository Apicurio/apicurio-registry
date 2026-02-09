#!/bin/bash
#
# Scenario 3: Branching
# Demonstrates version branching for managing incompatible changes
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

print_header "Scenario 3: Version Branching"

echo ""
echo "Branching enables parallel development tracks for artifacts."
echo "Use 'stable' for production and 'experimental' for testing."
echo ""

# Step 1: List branches
print_step "Step 1: List branches for code-review-prompt"
echo ""
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/branches" | \
    jq '.branches[] | {branchId, description}'
echo ""

# Step 2: Get content from stable branch
print_step "Step 2: Get content from 'stable' branch"
echo ""
echo -e "${CYAN}--- Stable Branch (v1.0.0) ---${NC}"
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=stable/content" | \
    head -8
echo "..."
echo ""

# Step 3: Get content from experimental branch
print_step "Step 3: Get content from 'experimental' branch"
echo ""
echo -e "${CYAN}--- Experimental Branch (v2.0.0) ---${NC}"
curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=experimental/content" | \
    head -8
echo "..."
echo ""

# Step 4: Show use case
print_step "Step 4: Branching Use Cases"
echo ""
echo "  ${YELLOW}Production Workflow:${NC}"
echo "    Always use: branch=stable"
echo "    curl '\$API/versions/branch=stable/content'"
echo ""
echo "  ${YELLOW}Testing Workflow:${NC}"
echo "    Test with: branch=experimental"
echo "    curl '\$API/versions/branch=experimental/content'"
echo ""
echo "  ${YELLOW}Migration Strategy:${NC}"
echo "    1. Create new version on experimental"
echo "    2. Test with experimental branch"
echo "    3. Promote to stable when ready"
echo ""

print_header "Scenario Complete"
echo ""
echo "Branches enable:"
echo "  • Parallel development without breaking production"
echo "  • Easy A/B testing of prompts and agents"
echo "  • Controlled rollout of new versions"
echo ""
