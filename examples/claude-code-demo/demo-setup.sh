#!/bin/bash
#
# Claude Code + Apicurio Registry Demo Setup Script
#
# This script populates Apicurio Registry with demo artifacts demonstrating:
# - Agent Cards (A2A protocol)
# - Model Schemas (MODEL_SCHEMA type)
# - Prompt Templates with versioning (PROMPT_TEMPLATE type)
# - Agent Workflows with cross-references (AGENT_WORKFLOW type)
# - Version management and branching
# - Labels and metadata
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}
API_BASE="$REGISTRY_URL/apis/registry/v3"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
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

print_header "Claude Code + Apicurio Registry Demo Setup"
echo ""
echo "Registry URL: $REGISTRY_URL"
echo ""

# Wait for registry to be ready
print_step "Waiting for registry to be ready..."
until curl -sf "$REGISTRY_URL/health/ready" > /dev/null 2>&1; do
    echo "  Registry not ready yet, waiting..."
    sleep 2
done
echo -e "${GREEN}  Registry is ready!${NC}"

# Function to create a group with labels
create_group() {
    local group_id=$1
    local description=$2
    local labels=${3:-"{}"}

    print_step "Creating group: $group_id"

    local body
    body=$(cat <<EOF
{
  "groupId": "$group_id",
  "description": "$description",
  "labels": $labels
}
EOF
)

    curl -sf -X POST "$API_BASE/groups" \
        -H "Content-Type: application/json" \
        -d "$body" \
        > /dev/null 2>&1 && print_info "OK" || print_info "(group may already exist)"
}

# Function to create an artifact with version using the v3 API format
create_artifact() {
    local group_id=$1
    local artifact_id=$2
    local artifact_type=$3
    local content_type=$4
    local file_path=$5
    local version=${6:-"1.0.0"}
    local labels=${7:-"{}"}

    print_info "Creating artifact: $artifact_id v$version (type: $artifact_type)"

    # Read the file content and escape it for JSON
    local content
    content=$(cat "$file_path" | jq -Rs .)

    # Build the CreateArtifact JSON body
    local body
    body=$(cat <<EOF
{
  "artifactId": "$artifact_id",
  "artifactType": "$artifact_type",
  "labels": $labels,
  "firstVersion": {
    "version": "$version",
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
        > /dev/null 2>&1 && echo "      OK" || echo "      (may already exist)"
}

# Function to create a new version of an existing artifact
create_version() {
    local group_id=$1
    local artifact_id=$2
    local content_type=$3
    local file_path=$4
    local version=$5

    print_info "Creating version: $artifact_id v$version"

    local content
    content=$(cat "$file_path" | jq -Rs .)

    local body
    body=$(cat <<EOF
{
  "version": "$version",
  "content": {
    "content": $content,
    "contentType": "$content_type"
  }
}
EOF
)

    curl -sf -X POST "$API_BASE/groups/$group_id/artifacts/$artifact_id/versions" \
        -H "Content-Type: application/json" \
        -d "$body" \
        > /dev/null 2>&1 && echo "      OK" || echo "      (may already exist)"
}

# Function to create a branch
create_branch() {
    local group_id=$1
    local artifact_id=$2
    local branch_id=$3
    local version=$4

    print_info "Creating branch: $branch_id from v$version"

    local body
    body=$(cat <<EOF
{
  "branchId": "$branch_id",
  "description": "Branch $branch_id for $artifact_id",
  "versions": ["$version"]
}
EOF
)

    curl -sf -X POST "$API_BASE/groups/$group_id/artifacts/$artifact_id/branches" \
        -H "Content-Type: application/json" \
        -d "$body" \
        > /dev/null 2>&1 && echo "      OK" || echo "      (may already exist)"
}

# ============================================
# Phase 1: Create Groups with metadata
# ============================================
print_header "Phase 1: Creating Groups"

create_group "claude-demo.agents" \
    "AI Agent Cards for Claude Code demo - A2A protocol" \
    '{"tier": "production", "domain": "ai-agents"}'

create_group "claude-demo.models" \
    "LLM Model Schemas for model definitions" \
    '{"tier": "production", "domain": "ai-models"}'

create_group "claude-demo.prompts" \
    "Prompt Templates with versioning and branching" \
    '{"tier": "production", "domain": "ai-prompts"}'

create_group "claude-demo.workflows" \
    "Agent Workflows for multi-agent orchestration" \
    '{"tier": "production", "domain": "ai-workflows"}'

# ============================================
# Phase 2: Create Agent Cards
# ============================================
print_header "Phase 2: Creating Agent Cards (A2A Protocol)"

create_artifact "claude-demo.agents" "code-review-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/code-review-agent.json" \
    "1.0.0" '{"capability": "code-analysis", "tier": "production"}'

create_artifact "claude-demo.agents" "documentation-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/documentation-agent.json" \
    "1.0.0" '{"capability": "documentation", "tier": "production"}'

create_artifact "claude-demo.agents" "security-scanner-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/security-scanner-agent.json" \
    "1.0.0" '{"capability": "security", "tier": "production"}'

create_artifact "claude-demo.agents" "refactoring-agent" "AGENT_CARD" \
    "application/json" "$SCRIPT_DIR/artifacts/agents/refactoring-agent.json" \
    "1.0.0" '{"capability": "refactoring", "tier": "standard"}'

# ============================================
# Phase 3: Create Model Schemas
# ============================================
print_header "Phase 3: Creating Model Schemas"

create_artifact "claude-demo.models" "claude-opus-4-5" "MODEL_SCHEMA" \
    "application/json" "$SCRIPT_DIR/artifacts/models/claude-opus-4-5.json" \
    "1.0.0" '{"provider": "anthropic", "tier": "premium"}'

create_artifact "claude-demo.models" "claude-sonnet-4" "MODEL_SCHEMA" \
    "application/json" "$SCRIPT_DIR/artifacts/models/claude-sonnet-4.json" \
    "1.0.0" '{"provider": "anthropic", "tier": "standard"}'

create_artifact "claude-demo.models" "claude-haiku-3-5" "MODEL_SCHEMA" \
    "application/json" "$SCRIPT_DIR/artifacts/models/claude-haiku-3-5.json" \
    "1.0.0" '{"provider": "anthropic", "tier": "economy"}'

# ============================================
# Phase 4: Create Prompt Templates with Versions
# ============================================
print_header "Phase 4: Creating Prompt Templates (with Versioning)"

# Code review prompt v1.0.0
create_artifact "claude-demo.prompts" "code-review-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/code-review-prompt.yaml" \
    "1.0.0" '{"use-case": "code-review", "workflow-compatible": "true"}'

# Code review prompt v2.0.0 (enhanced version)
create_version "claude-demo.prompts" "code-review-prompt" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/code-review-prompt-v2.yaml" \
    "2.0.0"

# Other prompts
create_artifact "claude-demo.prompts" "documentation-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/documentation-prompt.yaml" \
    "1.0.0" '{"use-case": "documentation"}'

create_artifact "claude-demo.prompts" "security-scan-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/security-scan-prompt.yaml" \
    "1.0.0" '{"use-case": "security"}'

create_artifact "claude-demo.prompts" "refactoring-prompt" "PROMPT_TEMPLATE" \
    "application/x-yaml" "$SCRIPT_DIR/artifacts/prompts/refactoring-prompt.yaml" \
    "1.0.0" '{"use-case": "refactoring", "workflow-compatible": "true"}'

# ============================================
# Phase 5: Create Branches
# ============================================
print_header "Phase 5: Creating Version Branches"

print_step "Creating branches for code-review-prompt"
create_branch "claude-demo.prompts" "code-review-prompt" "stable" "1.0.0"
create_branch "claude-demo.prompts" "code-review-prompt" "experimental" "2.0.0"

# ============================================
# Phase 6: Create Workflows with References
# ============================================
print_header "Phase 6: Creating Agent Workflows"

create_artifact "claude-demo.workflows" "code-quality-pipeline" "AGENT_WORKFLOW" \
    "application/json" "$SCRIPT_DIR/artifacts/workflows/code-quality-pipeline.json" \
    "1.0.0" '{"type": "pipeline", "agents": "4", "steps": "4"}'

create_artifact "claude-demo.workflows" "pr-review-workflow" "AGENT_WORKFLOW" \
    "application/json" "$SCRIPT_DIR/artifacts/workflows/pr-review-workflow.json" \
    "1.0.0" '{"type": "parallel", "trigger": "pull-request"}'

# ============================================
# Summary
# ============================================
print_header "Setup Complete!"

echo ""
echo "Demo artifacts created:"
echo ""
echo "  Groups:     4 (agents, models, prompts, workflows)"
echo "  Agents:     4 (code-review, documentation, security, refactoring)"
echo "  Models:     3 (opus-4-5, sonnet-4, haiku-3-5)"
echo "  Prompts:    5 (with v1.0.0 and v2.0.0 versions)"
echo "  Workflows:  2 (code-quality-pipeline, pr-review)"
echo "  Branches:   2 (stable, experimental)"
echo ""
echo "Try these commands:"
echo ""
echo "  # View artifacts in UI"
echo "  open http://localhost:8888"
echo ""
echo "  # A2A Discovery"
echo "  curl $REGISTRY_URL/.well-known/agents"
echo ""
echo "  # Get specific version"
echo "  curl '$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/2.0.0/content'"
echo ""
echo "  # Query by branch"
echo "  curl '$API_BASE/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=stable/content'"
echo ""
echo "  # Search by label"
echo "  curl '$API_BASE/search/artifacts?labels=capability:security'"
echo ""
