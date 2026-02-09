#!/bin/bash
#
# Claude Code + Apicurio Registry - Interactive CLI Demo
#
# This script launches Claude Code CLI with the Apicurio Registry MCP server
# configured, demonstrating the real integration.
#
# Prerequisites:
# 1. docker-compose up -d (all services running)
# 2. ./demo-setup.sh (artifacts populated)
# 3. Claude Code CLI installed
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
}

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}  $1${NC}"
}

print_prompt() {
    echo -e "${CYAN}  > $1${NC}"
}

print_header "Claude Code + Apicurio Registry Demo"

echo ""
echo "This demo shows Claude Code CLI using MCP tools to interact with"
echo "Apicurio Registry and discover agents via A2A protocol."
echo ""

# Check prerequisites
print_step "Checking prerequisites..."

if ! curl -sf "$REGISTRY_URL/health/ready" > /dev/null 2>&1; then
    echo -e "${RED}Error: Registry is not running at $REGISTRY_URL${NC}"
    echo "Please run: docker-compose up -d && ./demo-setup.sh"
    exit 1
fi
print_info "Registry: OK"

if ! command -v claude &> /dev/null; then
    echo -e "${RED}Error: Claude Code CLI not found${NC}"
    echo "Please install: https://docs.anthropic.com/en/docs/claude-code"
    exit 1
fi
print_info "Claude Code CLI: OK"

# Check if MCP JAR exists
MCP_JAR="$PROJECT_ROOT/mcp/target/quarkus-app/quarkus-run.jar"
if [ ! -f "$MCP_JAR" ]; then
    print_step "Building MCP server..."
    cd "$PROJECT_ROOT"
    ./mvnw package -pl mcp -am -DskipTests -q
fi
print_info "MCP Server JAR: OK"

# Create temporary MCP configuration
MCP_CONFIG_FILE=$(mktemp)
trap "rm -f $MCP_CONFIG_FILE" EXIT

cat > "$MCP_CONFIG_FILE" << EOF
{
  "mcpServers": {
    "apicurio-registry": {
      "command": "java",
      "args": ["-jar", "$MCP_JAR"],
      "env": {
        "REGISTRY_URL": "$REGISTRY_URL",
        "APICURIO_MCP_SAFE_MODE": "false",
        "APICURIO_MCP_PAGING_LIMIT": "100"
      }
    }
  }
}
EOF

print_header "MCP Configuration"
echo ""
echo "Claude Code will be configured with the Apicurio Registry MCP server:"
echo ""
cat "$MCP_CONFIG_FILE" | jq .
echo ""

print_header "Demo Scenarios"
echo ""
echo "Once Claude Code starts, try these prompts:"
echo ""

print_step "1. List Registry Groups (MCP)"
print_prompt "List all groups in the Apicurio Registry"
echo ""

print_step "2. Search Artifacts (MCP)"
print_prompt "Search for artifacts containing 'claude' in the registry"
echo ""

print_step "3. Get Prompt Template (MCP)"
print_prompt "Get the code-review-prompt template from the claude-demo.prompts group"
echo ""

print_step "4. A2A Agent Discovery"
print_prompt "Discover all available A2A agents by calling the /.well-known/agents endpoint"
echo ""

print_step "5. Create New Artifact (MCP)"
print_prompt "Create a new prompt template called 'test-prompt' in the claude-demo.prompts group"
echo ""

print_step "6. Full Workflow"
print_prompt "Fetch the code-review-prompt template, then use it to review this code: public void bad() { String sql = \"SELECT * FROM users WHERE id=\" + id; }"
echo ""

print_header "Starting Claude Code"
echo ""
echo "Launching Claude Code with Apicurio Registry MCP server..."
echo "Press Ctrl+C to exit."
echo ""

# Launch Claude Code with MCP configuration
# Merge MCP config into ~/.claude.json (where Claude Code reads MCP servers)

CLAUDE_CONFIG="$HOME/.claude.json"

# Backup existing config
if [ -f "$CLAUDE_CONFIG" ]; then
    cp "$CLAUDE_CONFIG" "$CLAUDE_CONFIG.backup"
fi

# Merge MCP config with existing settings
if [ -f "$CLAUDE_CONFIG" ]; then
    # Merge mcpServers into existing config
    EXISTING=$(cat "$CLAUDE_CONFIG")
    NEW_MCP=$(cat "$MCP_CONFIG_FILE")
    echo "$EXISTING" "$NEW_MCP" | jq -s '.[0] * {mcpServers: ((.[0].mcpServers // {}) * (.[1].mcpServers // {}))}' > "$CLAUDE_CONFIG.tmp"
    mv "$CLAUDE_CONFIG.tmp" "$CLAUDE_CONFIG"
else
    cp "$MCP_CONFIG_FILE" "$CLAUDE_CONFIG"
fi

print_info "MCP configuration added to $CLAUDE_CONFIG"
print_info "Starting Claude Code..."
echo ""

# Find the correct claude binary (check common locations)
if [ -x "$HOME/.claude/local/claude" ]; then
    CLAUDE_BIN="$HOME/.claude/local/claude"
elif command -v claude &> /dev/null; then
    CLAUDE_BIN="claude"
else
    echo -e "${RED}Error: Claude Code CLI not found${NC}"
    exit 1
fi

exec "$CLAUDE_BIN"
