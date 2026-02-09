#!/bin/bash
#
# Claude Code + Apicurio Registry - Interactive Demo
#
# This script provides an interactive menu to explore all demo scenarios
# showcasing Apicurio Registry as an AI Agent Lifecycle Platform.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
AGENT_URL=${AGENT_URL:-http://localhost:8081}
API_BASE="$REGISTRY_URL/apis/registry/v3"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
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

check_services() {
    local all_ok=true

    if ! curl -sf "$REGISTRY_URL/health/ready" > /dev/null 2>&1; then
        echo -e "${RED}✗ Registry not available at $REGISTRY_URL${NC}"
        all_ok=false
    else
        echo -e "${GREEN}✓ Registry OK${NC}"
    fi

    if ! curl -sf "$AGENT_URL/health" > /dev/null 2>&1; then
        echo -e "${YELLOW}○ Code Review Agent not available (some demos limited)${NC}"
    else
        echo -e "${GREEN}✓ Code Review Agent OK${NC}"
    fi

    if [ "$all_ok" = false ]; then
        echo ""
        echo "Please run: docker-compose up -d && ./demo-setup.sh"
        exit 1
    fi
}

show_menu() {
    clear
    echo -e "${BOLD}${BLUE}"
    cat << 'BANNER'
    ╔══════════════════════════════════════════════════════════════╗
    ║                                                              ║
    ║   █████╗ ██████╗ ██╗ ██████╗██╗   ██╗██████╗ ██╗ ██████╗    ║
    ║  ██╔══██╗██╔══██╗██║██╔════╝██║   ██║██╔══██╗██║██╔═══██╗   ║
    ║  ███████║██████╔╝██║██║     ██║   ██║██████╔╝██║██║   ██║   ║
    ║  ██╔══██║██╔═══╝ ██║██║     ██║   ██║██╔══██╗██║██║   ██║   ║
    ║  ██║  ██║██║     ██║╚██████╗╚██████╔╝██║  ██║██║╚██████╔╝   ║
    ║  ╚═╝  ╚═╝╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝ ╚═════╝    ║
    ║                                                              ║
    ║              AI Agent Lifecycle Platform Demo                ║
    ║                                                              ║
    ╚══════════════════════════════════════════════════════════════╝
BANNER
    echo -e "${NC}"
    echo ""
    echo -e "${BOLD}Select a Demo Scenario:${NC}"
    echo ""
    echo -e "  ${GREEN}1${NC}) Basic Discovery      - List groups, artifacts, and A2A endpoints"
    echo -e "  ${GREEN}2${NC}) Versioning           - Manage multiple artifact versions"
    echo -e "  ${GREEN}3${NC}) Branching            - Work with stable/experimental branches"
    echo -e "  ${GREEN}4${NC}) Workflow Orchestration - Multi-agent pipelines with context"
    echo -e "  ${GREEN}5${NC}) Search & Labels      - Advanced filtering and categorization"
    echo ""
    echo -e "  ${CYAN}6${NC}) Full Pipeline Demo   - End-to-end code quality workflow"
    echo -e "  ${CYAN}7${NC}) Quick Overview       - Show all artifacts summary"
    echo ""
    echo -e "  ${YELLOW}8${NC}) Launch Claude Code   - Interactive MCP session"
    echo ""
    echo -e "  ${RED}0${NC}) Exit"
    echo ""
    echo -n "Enter choice [0-8]: "
}

run_scenario() {
    local script="$SCRIPT_DIR/scenarios/$1"
    if [ -f "$script" ]; then
        bash "$script"
    else
        echo -e "${RED}Scenario not found: $script${NC}"
    fi
    echo ""
    read -p "Press Enter to continue..."
}

quick_overview() {
    print_header "Quick Overview - All Artifacts"

    echo ""
    print_step "Groups"
    curl -sf "$API_BASE/groups" | jq -r '.groups[] | "  • \(.groupId)"'

    echo ""
    print_step "Agent Cards (A2A Protocol)"
    curl -sf "$API_BASE/search/artifacts?artifactType=AGENT_CARD" | \
        jq -r '.artifacts[] | "  • \(.artifactId) - \(.groupId)"'

    echo ""
    print_step "Model Schemas"
    curl -sf "$API_BASE/search/artifacts?artifactType=MODEL_SCHEMA" | \
        jq -r '.artifacts[] | "  • \(.artifactId)"'

    echo ""
    print_step "Prompt Templates"
    curl -sf "$API_BASE/search/artifacts?artifactType=PROMPT_TEMPLATE" | \
        jq -r '.artifacts[] | "  • \(.artifactId)"'

    echo ""
    print_step "Agent Workflows"
    curl -sf "$API_BASE/search/artifacts?artifactType=AGENT_WORKFLOW" 2>/dev/null | \
        jq -r '.artifacts[] | "  • \(.artifactId)"' 2>/dev/null || echo "  (none found)"

    echo ""
    print_step "Version Branches (code-review-prompt)"
    curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/branches" 2>/dev/null | \
        jq -r '.branches[] | "  • \(.branchId)"' 2>/dev/null || echo "  (none found)"

    echo ""
    read -p "Press Enter to continue..."
}

full_pipeline_demo() {
    print_header "Full Pipeline Demo - Code Quality Workflow"

    echo ""
    echo "This demonstrates the complete workflow from the design document:"
    echo ""
    echo "  1. Fetch workflow definition from registry"
    echo "  2. Resolve artifact references"
    echo "  3. Execute multi-agent pipeline"
    echo "  4. Show accumulated context and results"
    echo ""

    print_step "Step 1: Fetch workflow definition"
    echo ""
    WORKFLOW=$(curl -sf "$API_BASE/groups/claude-demo.workflows/artifacts/code-quality-pipeline/versions/branch=latest/content")
    echo "$WORKFLOW" | jq '{name, steps: (.steps | length), triggers}'
    echo ""

    print_step "Step 2: Resolve referenced artifacts"
    echo ""
    echo -e "${CYAN}Agents referenced:${NC}"
    echo "$WORKFLOW" | jq -r '.steps[].agent' | sort -u | while read urn; do
        echo "  • $urn"
    done
    echo ""
    echo -e "${CYAN}Prompt templates referenced:${NC}"
    echo "$WORKFLOW" | jq -r '.steps[].promptTemplate // empty' | sort -u | while read urn; do
        echo "  • $urn"
    done
    echo ""

    print_step "Step 3: Fetch prompt template (v2.0.0 - enhanced)"
    echo ""
    curl -sf "$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/2.0.0/content" | \
        head -15
    echo "..."
    echo ""

    print_step "Step 4: Execute code review (simulated)"
    echo ""

    SAMPLE_CODE='public class UserService {
    public User getUser(String id) {
        String sql = "SELECT * FROM users WHERE id=" + id;
        return connection.execute(sql);
    }
}'

    echo -e "${CYAN}Code to analyze:${NC}"
    echo "$SAMPLE_CODE"
    echo ""

    if curl -sf "$AGENT_URL/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Calling code-review-agent...${NC}"
        RESULT=$(curl -sf -X POST "$AGENT_URL/agents/code-review/invoke" \
            -H "Content-Type: application/json" \
            -d "{
                \"code\": $(echo "$SAMPLE_CODE" | jq -Rs .),
                \"language\": \"java\",
                \"filename\": \"UserService.java\",
                \"focus_areas\": \"security, performance\"
            }" 2>/dev/null)
        echo ""
        echo -e "${CYAN}Review Result:${NC}"
        echo "$RESULT" | jq '.'
    else
        echo -e "${YELLOW}Agent not available. Expected result:${NC}"
        cat << 'EXPECTED'
{
  "summary": "Critical security vulnerability - SQL injection",
  "score": 20,
  "issues": [
    {
      "severity": "critical",
      "category": "security",
      "line": 3,
      "description": "SQL Injection - User input directly concatenated into query",
      "suggestion": "Use PreparedStatement with parameterized queries"
    }
  ],
  "agent": "code-review-agent",
  "provider": "ollama",
  "model": "llama3.2"
}
EXPECTED
    fi

    echo ""
    print_step "Step 5: Workflow complete"
    echo ""
    echo "In a full workflow execution:"
    echo "  ✓ Security scan would run first"
    echo "  ✓ Code review receives security context"
    echo "  ✓ Refactoring suggestions if score < 80"
    echo "  ✓ Documentation check if score >= 50"
    echo ""

    read -p "Press Enter to continue..."
}

launch_claude_code() {
    print_header "Launching Claude Code with MCP"
    echo ""
    echo "Starting Claude Code CLI with Apicurio Registry MCP server..."
    echo ""
    "$SCRIPT_DIR/run-claude-code-demo.sh"
}

# Main
print_header "Apicurio Registry - AI Agent Lifecycle Platform"
echo ""
print_step "Checking services..."
check_services
echo ""
read -p "Press Enter to continue to menu..."

while true; do
    show_menu
    read choice

    case $choice in
        1) run_scenario "01-basic-discovery.sh" ;;
        2) run_scenario "02-versioning.sh" ;;
        3) run_scenario "03-branching.sh" ;;
        4) run_scenario "04-workflow-orchestration.sh" ;;
        5) run_scenario "05-search-labels.sh" ;;
        6) full_pipeline_demo ;;
        7) quick_overview ;;
        8) launch_claude_code ;;
        0)
            echo ""
            echo "Goodbye!"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid choice. Please try again.${NC}"
            sleep 1
            ;;
    esac
done
