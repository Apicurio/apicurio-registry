#!/bin/bash
#
# Scenario 1: Basic Discovery
# Demonstrates basic MCP and REST operations for discovering artifacts
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../scenarios-common.sh" 2>/dev/null || {
    REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
    API_BASE="$REGISTRY_URL/apis/registry/v3"
    BLUE='\033[0;34m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    NC='\033[0m'
    print_header() { echo -e "\n${BLUE}══ $1 ══${NC}"; }
    print_step() { echo -e "${GREEN}▶ $1${NC}"; }
}

print_header "Scenario 1: Basic Discovery"

echo "This scenario demonstrates basic artifact discovery using the Registry API."
echo ""

# Step 1: List all groups
print_step "Step 1: List all groups in the registry"
echo ""
curl -sf "$API_BASE/groups" | jq '.groups[] | {groupId, description}'
echo ""

# Step 2: List artifacts in a specific group
print_step "Step 2: List artifacts in 'claude-demo.agents' group"
echo ""
curl -sf "$API_BASE/groups/claude-demo.agents/artifacts" | jq '.artifacts[] | {artifactId, artifactType}'
echo ""

# Step 3: Get artifact metadata
print_step "Step 3: Get metadata for code-review-agent"
echo ""
curl -sf "$API_BASE/groups/claude-demo.agents/artifacts/code-review-agent" | jq '{artifactId, artifactType, labels, modifiedOn}'
echo ""

# Step 4: A2A Discovery endpoint
print_step "Step 4: Discover agents via A2A protocol"
echo ""
curl -sf "$REGISTRY_URL/.well-known/agents" | jq '.agents[] | {name: .artifactId, group: .groupId}'
echo ""

# Step 5: Search artifacts by name
print_step "Step 5: Search for artifacts containing 'claude'"
echo ""
curl -sf "$API_BASE/search/artifacts?name=claude" | jq '.artifacts[] | "\(.groupId)/\(.artifactId)"'
echo ""

print_header "Scenario Complete"
echo "Basic discovery operations demonstrated successfully."
echo ""
