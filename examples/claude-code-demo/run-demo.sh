#!/bin/bash
#
# Claude Code + Apicurio Registry - End-to-End Demo
#
# This script demonstrates the complete workflow:
# 1. Discover agents via A2A protocol
# 2. Fetch prompt template from registry
# 3. Get model schema
# 4. Invoke the code-review-agent with sample code
# 5. Display results
#

set -e

REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
AGENT_URL=${AGENT_URL:-http://localhost:8081}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}══════════════════════════════════════════════════════════════${NC}"
}

print_step() {
    echo ""
    echo -e "${GREEN}▶ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}  $1${NC}"
}

# Check services are running
print_header "Claude Code + Apicurio Registry Demo"

print_step "Checking services..."

if ! curl -sf "$REGISTRY_URL/health/ready" > /dev/null 2>&1; then
    echo -e "${RED}Error: Registry is not running at $REGISTRY_URL${NC}"
    echo "Please run: docker-compose up -d"
    exit 1
fi
print_info "Registry: OK"

if ! curl -sf "$AGENT_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}Error: Code Review Agent is not running at $AGENT_URL${NC}"
    echo "Please run: docker-compose up -d"
    exit 1
fi
print_info "Code Review Agent: OK"

# Step 1: A2A Discovery - Get Registry's Agent Card
print_header "Step 1: A2A Discovery - Registry Agent Card"

print_step "Fetching registry's agent card..."
REGISTRY_AGENT=$(curl -s "$REGISTRY_URL/.well-known/agent.json")
echo "$REGISTRY_AGENT" | jq '{name, description, skills: [.skills[].id]}'

# Step 2: Search for Code Review Agents
print_header "Step 2: A2A Discovery - Find Code Review Agents"

print_step "Searching for agents with code-analysis skill..."
AGENTS=$(curl -s "$REGISTRY_URL/.well-known/agents")
echo "$AGENTS" | jq '.agents[] | {groupId, artifactId}'

print_step "Getting code-review-agent details from registry..."
AGENT_CARD=$(curl -s "$REGISTRY_URL/.well-known/agents/claude-demo.agents/code-review-agent")
echo "$AGENT_CARD" | jq '{name, skills: [.skills[].id], capabilities}'

# Step 3: Fetch Model Schema
print_header "Step 3: Fetch Model Schema"

print_step "Getting Claude Opus 4.5 model schema..."
MODEL_SCHEMA=$(curl -s "$REGISTRY_URL/apis/registry/v3/groups/claude-demo.models/artifacts/claude-opus-4-5/versions/1.0.0/content")
echo "$MODEL_SCHEMA" | jq '{modelId, provider, "contextWindow": .metadata.contextWindow, capabilities: .metadata.capabilities}'

# Step 4: Fetch Prompt Template
print_header "Step 4: Fetch Prompt Template"

print_step "Getting code-review prompt template..."
PROMPT_TEMPLATE=$(curl -s "$REGISTRY_URL/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/1.0.0/content")
echo "$PROMPT_TEMPLATE" | head -20
echo "..."

# Step 5: Invoke Code Review Agent with Sample Code
print_header "Step 5: Invoke Code Review Agent"

SAMPLE_CODE='public class UserService {
    private Connection conn;

    public User getUser(String id) {
        // SQL Injection vulnerability!
        String sql = "SELECT * FROM users WHERE id = " + id;
        ResultSet rs = conn.createStatement().executeQuery(sql);

        if (rs.next()) {
            User user = new User();
            user.id = rs.getString("id");
            user.name = rs.getString("name");
            user.password = rs.getString("password"); // Exposing password!
            return user;
        }
        return null;
    }

    public void updateUser(User user) {
        // No input validation
        String sql = "UPDATE users SET name=\"" + user.name + "\" WHERE id=" + user.id;
        conn.createStatement().execute(sql);
    }
}'

print_step "Sending code to agent for review..."
print_info "Code sample: UserService.java (Java, 25 lines)"
print_info "Calling: POST $AGENT_URL/agents/code-review/invoke"

REVIEW_RESULT=$(curl -s -X POST "$AGENT_URL/agents/code-review/invoke" \
    -H "Content-Type: application/json" \
    -d "{
        \"code\": $(echo "$SAMPLE_CODE" | jq -Rs .),
        \"language\": \"java\",
        \"filename\": \"UserService.java\"
    }")

# Step 6: Display Results
print_header "Step 6: Code Review Results"

echo "$REVIEW_RESULT" | jq '.'

# Summary
print_header "Demo Complete!"

echo ""
echo "This demo showed the complete Claude Code + Apicurio Registry workflow:"
echo ""
echo "  1. A2A Discovery    - Found registry and agents via /.well-known/agent.json"
echo "  2. Agent Search     - Searched for agents with specific skills"
echo "  3. Model Schema     - Retrieved LLM model definitions from registry"
echo "  4. Prompt Template  - Fetched versioned prompt template"
echo "  5. Agent Invocation - Called LLM-powered code-review-agent"
echo "  6. Results          - Received structured analysis with issues and score"
echo ""
echo "Endpoints used:"
echo "  - Registry API:  $REGISTRY_URL/apis/registry/v3"
echo "  - A2A Discovery: $REGISTRY_URL/.well-known/agent.json"
echo "  - Agent Invoke:  $AGENT_URL/agents/code-review/invoke"
echo ""
