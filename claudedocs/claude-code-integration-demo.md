# Claude Code + Apicurio Registry Integration Demo

## Design Specification

### Executive Summary

This demo showcases **Claude Code** as an intelligent development assistant that uses **Apicurio Registry** as a central hub for:
- **Agent Discovery** via A2A protocol
- **LLM-powered Code Review** using Ollama (local LLM)
- **Schema Management** for AI models (MODEL_SCHEMA type)
- **Prompt Template Management** with versioning
- **Persistent Storage** with PostgreSQL
- **Multi-Agent Orchestration** through discovered agents

---

## Architecture Overview (Real-World)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REAL-WORLD ARCHITECTURE                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐         MCP Protocol         ┌─────────────────┐          │
│  │              │◄────────────────────────────►│   Apicurio MCP  │          │
│  │  Claude Code │         30+ Tools            │     Server      │          │
│  │     CLI      │                              │                 │          │
│  └──────────────┘                              └────────┬────────┘          │
│         │                                               │                   │
│         │ A2A Discovery                                 │ REST API          │
│         │                                               ▼                   │
│         │                                    ┌─────────────────────┐        │
│         │                                    │  Apicurio Registry  │        │
│         │                                    │    + PostgreSQL     │        │
│         │                                    │                     │        │
│         │    ┌─────────────────────────────► │ ┌─────────────────┐ │        │
│         │    │ /.well-known/agents           │ │  Agent Cards    │ │        │
│         │    │                               │ │  Model Schemas  │ │        │
│         │    │                               │ │  Prompt Tmpl    │ │        │
│         │    │                               │ └─────────────────┘ │        │
│         │    │                               └─────────────────────┘        │
│         │    │                                                              │
│         ▼    │                                                              │
│  ┌───────────────────┐      LLM Inference      ┌─────────────────┐          │
│  │  Code Review      │◄───────────────────────►│     Ollama      │          │
│  │  Agent (A2A)      │       llama3.2          │   (Local LLM)   │          │
│  │  :8081            │                         │   :11434        │          │
│  └───────────────────┘                         └─────────────────┘          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Port | Technology | Purpose |
|-----------|------|------------|---------|
| **Apicurio Registry** | 8080 | Quarkus/Java | Schema & agent registry with A2A support |
| **Registry UI** | 8888 | React | Web interface for artifact management |
| **PostgreSQL** | 5432 | PostgreSQL 15 | Persistent storage for registry data |
| **Ollama** | 11434 | Go | Local LLM server running llama3.2 |
| **Code Review Agent** | 8081 | Python/Flask | LLM-powered A2A agent |
| **MCP Server** | stdio | Java | Model Context Protocol server for Claude |

---

## Component Specifications

### 1. MCP Server Configuration

**File**: `~/.claude/claude_desktop_config.json` (or Claude Code equivalent)

```json
{
  "mcpServers": {
    "apicurio-registry": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/apicurio-registry-mcp-server.jar"
      ],
      "env": {
        "REGISTRY_URL": "http://localhost:8080",
        "APICURIO_MCP_SAFE_MODE": "false",
        "APICURIO_MCP_PAGING_LIMIT": "100"
      }
    }
  }
}
```

**Available MCP Tools** (30+):

| Category | Tools |
|----------|-------|
| System | `get_server_info` |
| Admin | `get_artifact_types`, `list_configuration_properties`, `get_configuration_property`, `update_configuration_property` |
| Groups | `list_groups`, `create_group`, `get_group_metadata`, `update_group_metadata`, `search_groups` |
| Artifacts | `list_artifacts`, `create_artifact`, `get_artifact_metadata`, `update_artifact_metadata`, `search_artifacts` |
| Versions | `list_versions`, `create_version`, `get_version_metadata`, `get_version_content`, `update_version_metadata`, `update_version_content`, `update_version_state`, `search_versions` |

---

### 2. Demo Artifact Structure

```
Apicurio Registry
│
├── Group: claude-demo.agents (AGENT_CARD artifacts)
│   ├── code-review-agent
│   │   └── version: 1.0.0
│   ├── documentation-agent
│   │   └── version: 1.0.0
│   └── security-scanner-agent
│       └── version: 1.0.0
│
├── Group: claude-demo.models (MODEL_SCHEMA artifacts)
│   ├── claude-opus-4-5
│   │   └── version: 2024-01
│   └── claude-sonnet-4
│       └── version: 2024-01
│
└── Group: claude-demo.prompts (PROMPT_TEMPLATE artifacts)
    ├── code-review-prompt
    │   └── version: 1.0.0
    ├── documentation-prompt
    │   └── version: 1.0.0
    └── security-scan-prompt
        └── version: 1.0.0
```

---

### 3. Agent Card Schema (A2A Protocol)

**Artifact Type**: `AGENT_CARD`

```json
{
  "$schema": "https://apicur.io/schemas/agent-card/v1",
  "name": "code-review-agent",
  "description": "AI-powered code review agent that analyzes code quality, patterns, and potential issues",
  "version": "1.0.0",
  "url": "http://localhost:8081/agents/code-review",
  "provider": {
    "organization": "Apicurio Demo",
    "url": "https://www.apicur.io"
  },
  "capabilities": {
    "streaming": true,
    "pushNotifications": false
  },
  "skills": [
    {
      "id": "code-analysis",
      "name": "Code Analysis",
      "description": "Analyzes code for quality, patterns, and anti-patterns",
      "tags": ["code", "analysis", "quality"]
    },
    {
      "id": "bug-detection",
      "name": "Bug Detection",
      "description": "Identifies potential bugs and issues in code",
      "tags": ["bugs", "issues", "detection"]
    },
    {
      "id": "suggestion-generation",
      "name": "Suggestion Generation",
      "description": "Generates improvement suggestions for code",
      "tags": ["suggestions", "improvements"]
    }
  ],
  "authentication": {
    "schemes": ["none"]
  },
  "defaultInputModes": ["text"],
  "defaultOutputModes": ["text", "json"]
}
```

**Auto-Extracted Labels** (for search):
```
a2a.name = code-review-agent
a2a.skill.code-analysis = true
a2a.skill.bug-detection = true
a2a.skill.suggestion-generation = true
a2a.capability.streaming = true
a2a.inputMode.text = true
a2a.outputMode.text = true
a2a.outputMode.json = true
```

---

### 4. Model Schema (MODEL_SCHEMA Type)

**Artifact Type**: `MODEL_SCHEMA`

```json
{
  "$schema": "https://apicur.io/schemas/model-schema/v1",
  "modelId": "claude-opus-4-5",
  "provider": "anthropic",
  "version": "2024-01",
  "input": {
    "type": "object",
    "properties": {
      "messages": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "role": { "type": "string", "enum": ["user", "assistant", "system"] },
            "content": { "type": "string" }
          },
          "required": ["role", "content"]
        }
      },
      "max_tokens": { "type": "integer", "minimum": 1, "maximum": 200000 },
      "temperature": { "type": "number", "minimum": 0, "maximum": 1 },
      "tools": {
        "type": "array",
        "items": { "$ref": "#/$defs/tool" }
      }
    },
    "required": ["messages"]
  },
  "output": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "content": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "type": { "type": "string" },
            "text": { "type": "string" }
          }
        }
      },
      "stop_reason": { "type": "string" },
      "usage": {
        "type": "object",
        "properties": {
          "input_tokens": { "type": "integer" },
          "output_tokens": { "type": "integer" }
        }
      }
    }
  },
  "metadata": {
    "contextWindow": 200000,
    "maxOutputTokens": 128000,
    "capabilities": ["chat", "tool_use", "vision", "extended_thinking"],
    "pricing": {
      "inputPerMillion": 15.00,
      "outputPerMillion": 75.00
    }
  },
  "$defs": {
    "tool": {
      "type": "object",
      "properties": {
        "name": { "type": "string" },
        "description": { "type": "string" },
        "input_schema": { "type": "object" }
      },
      "required": ["name", "input_schema"]
    }
  }
}
```

---

### 5. Prompt Template Schema

**Artifact Type**: `PROMPT_TEMPLATE`

```yaml
$schema: https://apicur.io/schemas/prompt-template/v1
templateId: code-review-prompt
name: Code Review Analysis
version: "1.0.0"

template: |
  You are a senior software engineer performing a code review.

  ## Code to Review
  Language: {{language}}
  File: {{filename}}

  ```{{language}}
  {{code}}
  ```

  ## Review Focus Areas
  {{#if focus_areas}}
  Focus on: {{focus_areas}}
  {{else}}
  Perform a comprehensive review covering:
  - Code quality and readability
  - Potential bugs and edge cases
  - Performance considerations
  - Security vulnerabilities
  - Best practices adherence
  {{/if}}

  ## Output Format
  Provide your review as {{output_format}}.

variables:
  language:
    type: string
    description: "Programming language of the code"
    required: true
  filename:
    type: string
    description: "Name of the file being reviewed"
    required: true
  code:
    type: string
    description: "The code to review"
    required: true
  focus_areas:
    type: string
    description: "Specific areas to focus on (optional)"
    required: false
  output_format:
    type: string
    enum: ["markdown", "json", "inline-comments"]
    default: "markdown"
    description: "Format for the review output"

outputSchema:
  type: object
  properties:
    summary:
      type: string
    issues:
      type: array
      items:
        type: object
        properties:
          severity: { type: string, enum: ["critical", "major", "minor", "info"] }
          line: { type: integer }
          description: { type: string }
          suggestion: { type: string }
    score:
      type: integer
      minimum: 0
      maximum: 100

metadata:
  estimatedTokens: 500
  recommendedModels: ["claude-opus-4-5", "claude-sonnet-4"]
  tags: ["code-review", "analysis", "quality"]
```

---

## Demo Workflow Sequence

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DEMO WORKFLOW SEQUENCE                               │
└─────────────────────────────────────────────────────────────────────────────┘

User: "Find agents that can help with code review"
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Agent Discovery via A2A                                              │
│                                                                              │
│  Claude Code ──► MCP Server ──► GET /.well-known/agents?skill=code-analysis │
│                                                                              │
│  Response:                                                                   │
│  {                                                                          │
│    "agents": [                                                              │
│      {                                                                      │
│        "name": "code-review-agent",                                         │
│        "skills": ["code-analysis", "bug-detection", "suggestion-generation"]│
│        "url": "http://localhost:8081/agents/code-review"                    │
│      }                                                                      │
│    ]                                                                        │
│  }                                                                          │
└──────────────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Fetch Model Schema                                                   │
│                                                                              │
│  Claude Code ──► search_artifacts(type="MODEL_SCHEMA")                       │
│             ──► get_version_content("claude-demo.models", "claude-opus-4-5") │
│                                                                              │
│  Claude now knows the model's input/output schema and capabilities           │
└──────────────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Fetch Prompt Template                                                │
│                                                                              │
│  Claude Code ──► get_version_content("claude-demo.prompts", "code-review")   │
│                                                                              │
│  Claude retrieves the versioned prompt template                              │
└──────────────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: Render Prompt with Context                                           │
│                                                                              │
│  POST /groups/claude-demo.prompts/artifacts/code-review/versions/1.0.0/render│
│  Body: {                                                                    │
│    "language": "java",                                                      │
│    "filename": "UserService.java",                                          │
│    "code": "<actual code from user's codebase>",                            │
│    "output_format": "markdown"                                              │
│  }                                                                          │
│                                                                              │
│  Returns: Fully rendered prompt ready for agent invocation                   │
└──────────────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 5: Invoke Discovered Agent                                              │
│                                                                              │
│  Claude Code ──► POST http://localhost:8081/agents/code-review/invoke        │
│                  (A2A protocol endpoint)                                     │
│                                                                              │
│  Request:                                                                   │
│  {                                                                          │
│    "prompt": "<rendered prompt>",                                           │
│    "context": { "sessionId": "demo-123" }                                   │
│  }                                                                          │
│                                                                              │
│  Agent processes and returns structured review                               │
└──────────────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│ STEP 6: Display Results to User                                              │
│                                                                              │
│  Claude Code formats and presents the code review results                    │
│  with issues, suggestions, and quality score                                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Demo Scripts

### Script 1: Setup Demo Artifacts

```bash
#!/bin/bash
# demo-setup.sh - Populate Registry with demo artifacts

REGISTRY_URL=${REGISTRY_URL:-http://localhost:8080}

# Create groups
curl -X POST "$REGISTRY_URL/apis/registry/v3/groups" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "claude-demo.agents", "description": "Agent cards for Claude Code demo"}'

curl -X POST "$REGISTRY_URL/apis/registry/v3/groups" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "claude-demo.models", "description": "Model schemas for LLMs"}'

curl -X POST "$REGISTRY_URL/apis/registry/v3/groups" \
  -H "Content-Type: application/json" \
  -d '{"groupId": "claude-demo.prompts", "description": "Prompt templates"}'

# Create agent card
curl -X POST "$REGISTRY_URL/apis/registry/v3/groups/claude-demo.agents/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: code-review-agent" \
  -H "X-Registry-ArtifactType: AGENT_CARD" \
  -d @agents/code-review-agent.json

# Create model schema
curl -X POST "$REGISTRY_URL/apis/registry/v3/groups/claude-demo.models/artifacts" \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: claude-opus-4-5" \
  -H "X-Registry-ArtifactType: MODEL_SCHEMA" \
  -d @models/claude-opus-4-5.json

# Create prompt template
curl -X POST "$REGISTRY_URL/apis/registry/v3/groups/claude-demo.prompts/artifacts" \
  -H "Content-Type: application/x-yaml" \
  -H "X-Registry-ArtifactId: code-review-prompt" \
  -H "X-Registry-ArtifactType: PROMPT_TEMPLATE" \
  -d @prompts/code-review-prompt.yaml

echo "Demo artifacts created successfully!"
```

### Script 2: Interactive Demo Session

```
# Demo Session Transcript
# ========================

$ claude

> I want to review some code. Can you find agents in the registry that can help?

Claude: Let me search the registry for agents with code review capabilities.

[Invokes MCP tool: search_artifacts with type=AGENT_CARD, labels containing "code"]

I found these agents in Apicurio Registry:

1. **code-review-agent** (v1.0.0)
   - Skills: code-analysis, bug-detection, suggestion-generation
   - Capabilities: streaming enabled
   - URL: http://localhost:8081/agents/code-review

Would you like me to use this agent to review your code?

> Yes, review the UserService.java file

Claude: Let me fetch the appropriate prompt template and model schema.

[Invokes MCP tool: get_version_content for code-review-prompt]
[Invokes MCP tool: get_version_content for claude-opus-4-5 model schema]
[Reads UserService.java from local filesystem]

I've retrieved:
- The code-review-prompt template (v1.0.0)
- The Claude Opus 4.5 model schema with 200K context window
- Your UserService.java file (127 lines)

Now invoking the code-review-agent...

[Invokes A2A agent endpoint with rendered prompt]

## Code Review Results

**Overall Score: 78/100**

### Issues Found:

| Severity | Line | Description | Suggestion |
|----------|------|-------------|------------|
| Major | 45 | SQL injection vulnerability | Use parameterized queries |
| Minor | 23 | Null pointer risk | Add null check for user object |
| Info | 89 | Magic number | Extract to named constant |

### Summary
The code is generally well-structured but has a critical SQL injection
vulnerability that should be addressed immediately...
```

---

## Docker Compose for Demo Environment

```yaml
# docker-compose.yml
version: '3.8'

services:
  registry:
    image: quay.io/apicurio/apicurio-registry:latest-snapshot
    ports:
      - "8080:8080"
    environment:
      QUARKUS_PROFILE: dev
      APICURIO_A2A_ENABLED: "true"
      APICURIO_A2A_AGENT_NAME: "Apicurio Registry"
      APICURIO_A2A_AGENT_DESCRIPTION: "Schema and API registry with A2A support"
      APICURIO_A2A_AGENT_VERSION: "3.0.0"
      APICURIO_A2A_AGENT_CAPABILITIES_STREAMING: "true"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  registry-ui:
    image: quay.io/apicurio/apicurio-registry-ui:latest-snapshot
    ports:
      - "8888:8080"
    environment:
      REGISTRY_API_URL: http://registry:8080/apis/registry/v3

  mcp-server:
    image: quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot
    environment:
      REGISTRY_URL: http://registry:8080
      APICURIO_MCP_SAFE_MODE: "false"
      APICURIO_MCP_PAGING_LIMIT: "200"
    depends_on:
      registry:
        condition: service_healthy

  # Example A2A agent (code-review)
  code-review-agent:
    build: ./agents/code-review
    ports:
      - "8081:8080"
    environment:
      REGISTRY_URL: http://registry:8080
      AGENT_ID: code-review-agent
```

---

## File Structure for Demo

```
apicurio-claude-demo/
├── docker-compose.yml
├── demo-setup.sh
├── README.md
│
├── artifacts/
│   ├── agents/
│   │   ├── code-review-agent.json
│   │   ├── documentation-agent.json
│   │   └── security-scanner-agent.json
│   │
│   ├── models/
│   │   ├── claude-opus-4-5.json
│   │   └── claude-sonnet-4.json
│   │
│   └── prompts/
│       ├── code-review-prompt.yaml
│       ├── documentation-prompt.yaml
│       └── security-scan-prompt.yaml
│
├── agents/
│   └── code-review/
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/
│           └── main/java/io/apicurio/demo/
│               └── CodeReviewAgent.java
│
└── claude-config/
    └── claude_desktop_config.json
```

---

## Key Integration Points Summary

| Component | Protocol/Type | Purpose |
|-----------|---------------|---------|
| **MCP Server** | Model Context Protocol (stdio) | Exposes 30+ Registry tools to Claude |
| **A2A Discovery** | `/.well-known/agents` endpoint | Discover available agents by skills |
| **AGENT_CARD** | Artifact Type | Store agent metadata with searchable labels |
| **MODEL_SCHEMA** | Artifact Type | Define LLM input/output contracts |
| **PROMPT_TEMPLATE** | Artifact Type | Version-controlled prompts with variables |
| **Prompt Render API** | `POST /render` endpoint | Server-side template rendering |

---

## Next Steps

1. **Create the demo artifacts** in `artifacts/` directory
2. **Build the example code-review-agent** that implements A2A protocol
3. **Configure Claude Code** with the MCP server
4. **Run the demo** using docker-compose
5. **Document the workflow** with screenshots/recordings
