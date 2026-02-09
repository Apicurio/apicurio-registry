#!/bin/bash
#
# Scenario 4: Workflow Orchestration
# Demonstrates multi-agent workflows with cross-artifact references
#

REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
AGENT_URL=${AGENT_URL:-http://localhost:8081}
API_BASE="$REGISTRY_URL/apis/registry/v3"
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

print_header() { echo -e "\n${BLUE}══════════════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"; }
print_step() { echo -e "${GREEN}▶ $1${NC}"; }

print_header "Scenario 4: Workflow Orchestration"

echo ""
echo "This scenario demonstrates multi-agent workflows with"
echo "cross-artifact references and context chaining."
echo ""

# Step 1: Show workflow artifact
print_step "Step 1: View code-quality-pipeline workflow"
echo ""
curl -sf "$API_BASE/groups/claude-demo.workflows/artifacts/code-quality-pipeline/versions/branch=latest/content" | \
    jq '{workflowId, name, steps: [.steps[] | {id, name, agent, dependsOn}]}'
echo ""

# Step 2: Show cross-artifact references
print_step "Step 2: Examine artifact references in workflow"
echo ""
echo -e "${CYAN}The workflow references these artifacts:${NC}"
echo ""
curl -sf "$API_BASE/groups/claude-demo.workflows/artifacts/code-quality-pipeline/versions/branch=latest/content" | \
    jq -r '.steps[] | "  Agent: \(.agent)\n  Prompt: \(.promptTemplate // "inline")\n  Model: \(.model)\n"'
echo ""

# Step 3: Show workflow steps and dependencies
print_step "Step 3: Workflow execution order"
echo ""
echo -e "${CYAN}Execution Flow:${NC}"
echo ""
echo "  ┌─────────────────┐"
echo "  │  security-scan  │  ← Step 1: Runs first"
echo "  └────────┬────────┘"
echo "           │"
echo "           ▼"
echo "  ┌─────────────────┐"
echo "  │   code-review   │  ← Step 2: Gets security context"
echo "  └────────┬────────┘"
echo "           │"
echo "     ┌─────┴─────┐"
echo "     ▼           ▼"
echo "┌──────────┐ ┌───────────────┐"
echo "│refactor  │ │documentation  │  ← Step 3-4: Conditional"
echo "│(if <80)  │ │ (if >=50)     │"
echo "└──────────┘ └───────────────┘"
echo ""

# Step 4: Simulate workflow execution
print_step "Step 4: Simulate workflow execution"
echo ""

SAMPLE_CODE='public void getUser(String id) {
    String sql = "SELECT * FROM users WHERE id=" + id;
    return connection.execute(sql);
}'

echo -e "${CYAN}Sample code to analyze:${NC}"
echo "$SAMPLE_CODE"
echo ""

# Check if agent is available
if curl -sf "$AGENT_URL/health" > /dev/null 2>&1; then
    echo -e "${GREEN}Code review agent is available. Running analysis...${NC}"
    echo ""
    
    RESULT=$(curl -sf -X POST "$AGENT_URL/agents/code-review/invoke" \
        -H "Content-Type: application/json" \
        -d "{
            \"code\": $(echo "$SAMPLE_CODE" | jq -Rs .),
            \"language\": \"java\",
            \"filename\": \"UserService.java\"
        }" 2>/dev/null)
    
    echo -e "${CYAN}Analysis Result:${NC}"
    echo "$RESULT" | jq '{summary, score, issues: [.issues[]? | {severity, description}]}'
else
    echo -e "${YELLOW}Code review agent not available. Showing expected output:${NC}"
    echo ""
    cat << 'EXPECTED'
{
  "summary": "Critical security vulnerability detected",
  "score": 25,
  "issues": [
    {
      "severity": "critical",
      "description": "SQL Injection vulnerability - user input directly concatenated into SQL query"
    }
  ]
}
EXPECTED
fi
echo ""

# Step 5: Show context chaining
print_step "Step 5: Context chaining between steps"
echo ""
echo "In a multi-step workflow, context is passed between agents:"
echo ""
echo -e "${CYAN}Step 1 (security-scan) output:${NC}"
echo '  { "vulnerabilities": [{"severity": "critical", "type": "SQL Injection"}] }'
echo ""
echo -e "${CYAN}Step 2 (code-review) receives:${NC}"
echo '  { "context_from_security_scan": { "vulnerabilities": [...] } }'
echo ""
echo "This enables the code reviewer to build upon security findings!"
echo ""

print_header "Scenario Complete"
echo ""
echo "Workflow orchestration enables:"
echo "  • Multi-agent pipelines with dependencies"
echo "  • Context chaining between steps"
echo "  • Conditional execution based on results"
echo "  • Artifact references via URN format"
echo ""
