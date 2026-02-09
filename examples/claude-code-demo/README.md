# Apicurio Registry - AI Agent Lifecycle Platform Demo

This demo showcases **Apicurio Registry** as an **AI Agent Lifecycle Management Platform**, demonstrating how to manage AI agents, LLM models, prompt templates, and multi-agent workflows using industry-standard protocols.

## Key Features Demonstrated

| Feature | Description |
|---------|-------------|
| **Agent Registry (A2A)** | Discover and manage AI agents via A2A protocol |
| **Prompt Management** | Version, branch, and manage prompt templates |
| **Model Schemas** | Define and version LLM model configurations |
| **Workflow Orchestration** | Multi-agent pipelines with context chaining |
| **Version Branches** | Stable/experimental branches for safe evolution |
| **Labels & Search** | Rich categorization and advanced filtering |
| **MCP Integration** | Claude Code CLI with 30+ registry tools |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     AI AGENT LIFECYCLE PLATFORM                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐         MCP Protocol         ┌─────────────────┐              │
│  │              │─────────────────────────────►│   Apicurio MCP  │              │
│  │  Claude Code │  "List groups in registry"   │     Server      │              │
│  │     CLI      │◄─────────────────────────────│                 │              │
│  │              │   [Uses list_groups tool]    └────────┬────────┘              │
│  └──────┬───────┘                                       │                       │
│         │                                               │ REST API              │
│         │                                               ▼                       │
│         │                                    ┌─────────────────────┐            │
│         │        A2A Protocol                │  Apicurio Registry  │            │
│         └───────────────────────────────────►│                     │            │
│             [/.well-known/agents]            │  ┌───────────────┐  │            │
│                                              │  │ Agent Cards   │  │            │
│  ┌──────────────┐                            │  │ Model Schemas │  │            │
│  │  Code Review │◄───────────────────────────│  │ Prompts       │  │            │
│  │    Agent     │  Invoke with context       │  │ Workflows     │  │            │
│  └──────────────┘                            │  └───────────────┘  │            │
│         │                                    └─────────────────────┘            │
│         │ LLM                                                                    │
│         ▼                                                                        │
│  ┌──────────────┐                                                               │
│  │   Ollama     │  Local LLM inference                                          │
│  │  (llama3.2)  │                                                               │
│  └──────────────┘                                                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Quick Start

### 1. Start the Environment

```bash
cd examples/claude-code-demo

# Start all services
docker-compose up -d

# Wait for Ollama model download (first time only, ~2GB)
docker-compose logs -f ollama-init
```

### 2. Populate Demo Artifacts

```bash
./demo-setup.sh
```

This creates:
- **4 Groups** (agents, models, prompts, workflows)
- **4 Agents** (code-review, documentation, security, refactoring)
- **3 Models** (opus-4-5, sonnet-4, haiku-3-5)
- **5 Prompts** (with v1.0.0 and v2.0.0 versions)
- **2 Workflows** (code-quality-pipeline, pr-review)
- **2 Branches** (stable, experimental)

### 3. Run the Interactive Demo

```bash
./run-demo.sh
```

This launches an interactive menu with demo scenarios:

```
╔══════════════════════════════════════════════════════════════╗
║              AI Agent Lifecycle Platform Demo                ║
╚══════════════════════════════════════════════════════════════╝

  1) Basic Discovery      - List groups, artifacts, and A2A endpoints
  2) Versioning           - Manage multiple artifact versions
  3) Branching            - Work with stable/experimental branches
  4) Workflow Orchestration - Multi-agent pipelines with context
  5) Search & Labels      - Advanced filtering and categorization

  6) Full Pipeline Demo   - End-to-end code quality workflow
  7) Quick Overview       - Show all artifacts summary

  8) Launch Claude Code   - Interactive MCP session
```

### 4. Run Claude Code with MCP

```bash
./run-claude-code-demo.sh
```

## Demo Scenarios

### Scenario 1: Basic Discovery

Demonstrates fundamental registry operations:

```bash
# List all groups
curl http://localhost:8080/apis/registry/v3/groups

# A2A agent discovery
curl http://localhost:8080/.well-known/agents

# Search artifacts
curl "http://localhost:8080/apis/registry/v3/search/artifacts?name=claude"
```

### Scenario 2: Versioning

Shows artifact version management:

```bash
# List versions of a prompt
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions

# Get specific version
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/2.0.0/content

# Get latest version
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=latest/content
```

### Scenario 3: Branching

Demonstrates parallel development tracks:

```bash
# List branches
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/branches

# Get stable branch (production)
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=stable/content

# Get experimental branch (testing)
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.prompts/artifacts/code-review-prompt/versions/branch=experimental/content
```

### Scenario 4: Workflow Orchestration

Shows multi-agent pipelines:

```bash
# Get workflow definition
curl http://localhost:8080/apis/registry/v3/groups/claude-demo.workflows/artifacts/code-quality-pipeline/versions/branch=latest/content

# Invoke code review agent
curl -X POST http://localhost:8081/agents/code-review/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public void bad() { String sql = \"SELECT * FROM users WHERE id=\" + id; }",
    "language": "java",
    "filename": "Example.java"
  }'
```

### Scenario 5: Search & Labels

Advanced filtering capabilities:

```bash
# Search by artifact type
curl "http://localhost:8080/apis/registry/v3/search/artifacts?artifactType=AGENT_CARD"
curl "http://localhost:8080/apis/registry/v3/search/artifacts?artifactType=AGENT_WORKFLOW"

# Search by name pattern
curl "http://localhost:8080/apis/registry/v3/search/artifacts?name=review"
```

## Demo Artifacts

### Agent Cards (AGENT_CARD)

AI agents discoverable via A2A protocol:

| Agent | Skills | Tier |
|-------|--------|------|
| code-review-agent | code-analysis, bug-detection, suggestions | production |
| documentation-agent | api-docs, readme, code-comments | production |
| security-scanner-agent | vulnerability-detection, CVE-audit | production |
| refactoring-agent | extract-method, simplify-conditionals, patterns | standard |

### Model Schemas (MODEL_SCHEMA)

LLM model configurations:

| Model | Context Window | Tier | Use Case |
|-------|----------------|------|----------|
| claude-opus-4-5 | 200K | premium | Complex analysis, deep reasoning |
| claude-sonnet-4 | 200K | standard | Balanced quality and speed |
| claude-haiku-3-5 | 200K | economy | Quick tasks, high volume |

### Prompt Templates (PROMPT_TEMPLATE)

Versioned prompt templates:

| Template | Version | Features |
|----------|---------|----------|
| code-review-prompt | 1.0.0 | Basic code review |
| code-review-prompt | 2.0.0 | Context chaining, enhanced scoring |
| documentation-prompt | 1.0.0 | API documentation generation |
| security-scan-prompt | 1.0.0 | OWASP vulnerability detection |
| refactoring-prompt | 1.0.0 | Design pattern suggestions |

### Agent Workflows (AGENT_WORKFLOW)

Multi-agent orchestration:

| Workflow | Steps | Features |
|----------|-------|----------|
| code-quality-pipeline | 4 | Security → Review → Refactor → Docs |
| pr-review-workflow | 4 | Categorize → Security + Quality (parallel) → Summary |

## MCP Tools Reference

| Category | Tools |
|----------|-------|
| **System** | `get_server_info`, `get_artifact_types` |
| **Groups** | `list_groups`, `create_group`, `get_group_metadata`, `search_groups` |
| **Artifacts** | `list_artifacts`, `create_artifact`, `get_artifact_metadata`, `search_artifacts` |
| **Versions** | `list_versions`, `create_version`, `get_version_content`, `update_version_state` |
| **Branches** | `list_branches`, `create_branch` |
| **Rules** | `list_artifact_rules`, `create_artifact_rule` |

## Services

| Service | Port | Description |
|---------|------|-------------|
| Registry | 8080 | Apicurio Registry API with A2A support |
| Registry UI | 8888 | Web interface for managing artifacts |
| Code Review Agent | 8081 | LLM-powered A2A agent |
| Ollama | 11434 | Local LLM server (llama3.2) |
| PostgreSQL | 5432 | Persistent storage |

## Project Structure

```
claude-code-demo/
├── artifacts/
│   ├── agents/           # Agent card definitions
│   ├── models/           # LLM model schemas
│   ├── prompts/          # Prompt templates (v1 & v2)
│   └── workflows/        # Multi-agent workflows
├── scenarios/            # Individual demo scripts
│   ├── 01-basic-discovery.sh
│   ├── 02-versioning.sh
│   ├── 03-branching.sh
│   ├── 04-workflow-orchestration.sh
│   └── 05-search-labels.sh
├── agents/
│   └── code-review-agent/  # Python A2A agent implementation
├── demo-setup.sh         # Populates registry with artifacts
├── run-demo.sh           # Interactive demo menu
├── run-claude-code-demo.sh  # Claude Code CLI launcher
├── docker-compose.yml    # Service definitions
└── README.md
```

## Cleanup

```bash
# Stop services
docker-compose down

# Remove all data
docker-compose down -v
```

## Learn More

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Claude Code Documentation](https://docs.anthropic.com/en/docs/claude-code)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [A2A Protocol Specification](https://github.com/google/A2A)
