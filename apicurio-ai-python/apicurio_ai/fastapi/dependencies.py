from apicurio_ai.core.a2a_discovery import AgentDiscovery
from apicurio_ai.core.mcp_discovery import McpToolDiscovery
from apicurio_ai.core.prompt import PromptGovernance

try:
    from fastapi import Request
except ImportError as e:
    raise ImportError(
        "fastapi is required for the FastAPI adapter. "
        "Install with: pip install apicurio-ai[fastapi]"
    ) from e


def get_mcp_discovery(request: Request) -> McpToolDiscovery:
    return request.app.state.apicurio_mcp_discovery


def get_agent_discovery(request: Request) -> AgentDiscovery:
    return request.app.state.apicurio_agent_discovery


def get_prompt_governance(request: Request) -> PromptGovernance:
    return request.app.state.apicurio_prompt_governance
