# Apicurio AI Python SDK

Apicurio Registry AI integration for Python — MCP discovery, A2A agent discovery, and Prompt template governance.

## Installation

```bash
pip install apicurio-ai
```

With optional extras:

```bash
pip install apicurio-ai[langchain]    # LangChain adapter
pip install apicurio-ai[fastapi]      # FastAPI integration
pip install apicurio-ai[sdk]          # Full SDK for publish/render
pip install apicurio-ai[all]          # Everything
```

## Quick Start

```python
from apicurio_ai import AgentDiscovery, McpToolDiscovery, PromptGovernance
from apicurio_ai.core.config import RegistryConfig

config = RegistryConfig(registry_url="http://localhost:8080")

# Discover A2A agents
discovery = AgentDiscovery(config)
agents = await discovery.search(name="weather")

# Search MCP tools
mcp = McpToolDiscovery(config)
tools = await mcp.search(name="calculator")

# Render prompt templates
prompts = PromptGovernance(config)
result = await prompts.render("default", "my-prompt", {"name": "World"})
```
