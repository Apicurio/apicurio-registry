#!/bin/bash
#
# Claude Code + Apicurio Registry Demo Setup Script
#
# This script populates Apicurio Registry with demo artifacts:
# - Agent Cards (A2A protocol)
# - Model Schemas (MODEL_SCHEMA type)
# - Prompt Templates (PROMPT_TEMPLATE type)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
API_BASE="$REGISTRY_URL/apis/registry/v3"

echo "=============================================="
echo "Claude Code + Apicurio Registry Demo Setup"
echo "=============================================="
echo "Registry URL: $REGISTRY_URL"
echo ""

# Wait for registry to be ready
echo "Waiting for registry to be ready..."
until curl -sf "$REGISTRY_URL/health/ready" > /dev/null 2>&1; do
    echo "  Registry not ready yet, waiting..."
    sleep 2
done
echo "Registry is ready!"
echo ""

# Function to create a group
create_group() {
    local group_id=$1
    local description=$2

    echo "Creating group: $group_id"
    curl -sf -X POST "$API_BASE/groups" \
        -H "Content-Type: application/json" \
        -d "{\"groupId\": \"$group_id\", \"description\": \"$description\"}" \
        > /dev/null 2>&1 || echo "  (group may already exist)"
}

# Function to create an artifact using the v3 API format
create_artifact() {
    local group_id=$1
    local artifact_id=$2
    local artifact_type=$3
    local content_type=$4
    local file_path=$5

    echo "  Creating artifact: $artifact_id (type: $artifact_type)"

    # Read the file content and escape it for JSON
    local content
    content=$(cat "$file_path" | jq -Rs .)

    # Build the CreateArtifact JSON body
    local body
    body=$(cat <<EOF
{
  "artifactId": "$artifact_id",
  "artifactType": "$artifact_type",
  "firstVersion": {
    "version": "1.0.0",
    "content": {
      "content": $content,
      "contentType": "$content_type"
    }
  }
}
EOF
)

    curl -sf -X POST "$API_BASE/groups/$group_id/artifacts?ifExists=FIND_OR_CREATE_VERSION" \
        -H "Content-Type: application/json" \
        -d "$body" \
        > /dev/null 2>&1 && echo "    OK" || echo "    (artifact may already exist)"
}

echo "=============================================="
echo "Step 1: Creating Groups"
echo "=============================================="

create_group "claude-demo.agents" "Agent cards for Claude Code demo - A2A protocol"
create_group "claude-demo.models" "Model schemas for LLM definitions"
create_group "claude-demo.prompts" "Prompt templates with versioning"

echo ""
echo "=============================================="
echo "Step 2: Creating Agent Cards (A2A Protocol)"
echo "=============================================="

create_artifact "claude-demo.agents" "code-review-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/code-review-agent.json"

create_artifact "claude-demo.agents" "documentation-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/documentation-agent.json"

create_artifact "claude-demo.agents" "security-scanner-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/security-scanner-agent.json"

echo ""
echo "=============================================="
echo "Step 3: Creating Model Schemas (MODEL_SCHEMA)"
echo "=============================================="

create_artifact "claude-demo.models" "claude-opus-4-5" "MODEL_SCHEMA" \
    "application/json" "$SCRIPT_DIR/artifacts/models/claude-opus-4-5.json"

create_artifact "claude-demo.models" "claude-sonnet-4" "MODEL_SCHEMA" \
    "application/json" "$SCRIPT_DIR/artifacts/models/claude-sonnet-4.json"

echo ""
echo "=============================================="
echo "Step 4: Creating Prompt Templates (PROMPT_TEMPLATE)"
echo "=============================================="

create_artifact "claude-demo.prompts" "code-review-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/code-review-prompt.yaml"

create_artifact "claude-demo.prompts" "documentation-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/documentation-prompt.yaml"

create_artifact "claude-demo.prompts" "security-scan-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/security-scan-prompt.yaml"

echo ""
echo "=============================================="
echo "Setup Complete!"
echo "=============================================="
echo ""
echo "You can now:"
echo "  1. View artifacts in the UI: http://localhost:8888"
echo "  2. Query the A2A endpoint: curl $REGISTRY_URL/.well-known/agent.json"
echo "  3. Search for agents: curl '$REGISTRY_URL/.well-known/agents?skill=code-analysis'"
echo "  4. Use Claude Code with the MCP server configured"
echo ""
echo "Demo artifacts created:"
echo "  - 3 Agent Cards (A2A protocol)"
echo "  - 2 Model Schemas (LLM definitions)"
echo "  - 3 Prompt Templates (versioned prompts)"
echo ""
