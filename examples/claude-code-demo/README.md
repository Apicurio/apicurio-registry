# Claude Code + Apicurio Registry Integration Demo

A **real-world** demonstration of Claude Code using Apicurio Registry for:

- **Agent Discovery** via A2A (Agent-to-Agent) protocol
- **LLM-powered Code Review** using Ollama
- **Schema Management** for AI models (MODEL_SCHEMA type)
- **Prompt Template Management** with versioning
- **Persistent Storage** with PostgreSQL

## Architecture

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
│         │                                    │      + PostgreSQL   │        │
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

## Quick Start

### 1. Start the Environment

```bash
# Start all services (PostgreSQL, Registry, Ollama, Code Review Agent)
docker-compose up -d

# First time: Wait for Ollama to download the model (~2GB)
docker-compose logs -f ollama-init
```

### 2. Populate Demo Artifacts

```bash
./demo-setup.sh
```

### 3. Run the End-to-End Demo

```bash
./run-demo.sh
```

This demonstrates:
1. A2A agent discovery
2. Fetching model schemas and prompt templates
3. Invoking the LLM-powered code review agent
4. Receiving structured analysis results

### 4. Configure Claude Code (Optional)

```bash
./setup-mcp.sh
```

This configures Claude Code to use the Apicurio Registry MCP server.

## Services

| Service | Port | Description |
|---------|------|-------------|
| **Registry** | 8080 | Apicurio Registry API with A2A support |
| **Registry UI** | 8888 | Web interface for managing artifacts |
| **Code Review Agent** | 8081 | LLM-powered A2A agent |
| **Ollama** | 11434 | Local LLM server (llama3.2) |
| **PostgreSQL** | 5432 | Persistent storage |

## Demo Artifacts

### Agent Cards (A2A Protocol)

| Agent | Skills | Description |
|-------|--------|-------------|
| code-review-agent | code-analysis, bug-detection, suggestions | LLM-powered code reviewer |
| documentation-agent | api-docs, readme, code-comments | Documentation generator |
| security-scanner-agent | vulnerability-detection, CVE-audit | Security scanner |

### Model Schemas (MODEL_SCHEMA)

| Model | Context | Capabilities |
|-------|---------|--------------|
| claude-opus-4-5 | 200K | chat, tools, vision, extended_thinking |
| claude-sonnet-4 | 200K | chat, tools, vision |

### Prompt Templates (PROMPT_TEMPLATE)

| Template | Variables | Purpose |
|----------|-----------|---------|
| code-review-prompt | language, filename, code, focus_areas | Code quality analysis |
| documentation-prompt | language, filename, code, doc_type | Generate documentation |
| security-scan-prompt | language, filename, code, severity | Security scanning |

## API Examples

### A2A Discovery

```bash
# Get registry's agent card
curl http://localhost:8080/.well-known/agent.json

# Search for agents
curl http://localhost:8080/.well-known/agents

# Get specific agent
curl http://localhost:8080/.well-known/agents/claude-demo.agents/code-review-agent
```

### Invoke Code Review Agent

```bash
curl -X POST http://localhost:8081/agents/code-review/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public void bad() { String sql = \"SELECT * FROM users WHERE id=\" + id; }",
    "language": "java",
    "filename": "Example.java"
  }'
```

Response:
```json
{
  "summary": "Critical security vulnerability found",
  "score": 25,
  "issues": [
    {
      "severity": "critical",
      "description": "SQL Injection vulnerability",
      "suggestion": "Use parameterized queries"
    }
  ]
}
```

### Registry API

```bash
# List groups
curl http://localhost:8080/apis/registry/v3/groups

# Get prompt template
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/1.0.0/content

# Get model schema
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.models/artifacts/claude-opus-4-5/versions/1.0.0/content
```

## MCP Tools Available

When configured with Claude Code, you have access to 30+ tools:

| Category | Tools |
|----------|-------|
| **System** | `get_server_info` |
| **Groups** | `list_groups`, `create_group`, `get_group_metadata`, `search_groups` |
| **Artifacts** | `list_artifacts`, `create_artifact`, `get_artifact_metadata`, `search_artifacts` |
| **Versions** | `list_versions`, `create_version`, `get_version_content`, `update_version_state` |

Example Claude Code interaction:
```
> List the groups in the registry

I found 3 groups:
- claude-demo.agents: Agent cards for Claude Code demo
- claude-demo.models: Model schemas for LLM definitions
- claude-demo.prompts: Prompt templates with versioning

> Get the code review prompt template

[Fetches and displays the prompt template with variables]
```

## File Structure

```
claude-code-demo/
├── docker-compose.yml          # Full environment (PostgreSQL, Ollama, etc.)
├── demo-setup.sh               # Populate registry with artifacts
├── run-demo.sh                 # End-to-end demonstration
├── setup-mcp.sh                # Configure Claude Code MCP
├── README.md
│
├── agents/
│   └── code-review-agent/      # LLM-powered A2A agent
│       ├── Dockerfile
│       ├── agent.py            # Python Flask agent
│       └── requirements.txt
│
├── artifacts/
│   ├── agents/                 # A2A Agent Cards
│   ├── models/                 # MODEL_SCHEMA definitions
│   └── prompts/                # PROMPT_TEMPLATE files
│
└── claude-config/
    └── claude_desktop_config.json
```

## Cleanup

```bash
# Stop all services
docker-compose down

# Remove all data (PostgreSQL, Ollama models)
docker-compose down -v
```

## Troubleshooting

### Ollama model not loading
```bash
# Check Ollama logs
docker-compose logs ollama

# Manually pull the model
docker exec demo-ollama ollama pull llama3.2
```

### Code Review Agent errors
```bash
# Check agent logs
docker-compose logs code-review-agent

# Test Ollama connectivity
curl http://localhost:11434/api/tags
```

### Registry not starting
```bash
# Check PostgreSQL is healthy
docker-compose ps postgres

# Check registry logs
docker-compose logs registry
```

## Learn More

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [A2A Protocol Specification](https://github.com/google/A2A)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Ollama](https://ollama.ai/)
