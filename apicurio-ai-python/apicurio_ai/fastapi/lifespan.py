import logging
from contextlib import asynccontextmanager
from typing import Any, AsyncGenerator

import httpx

from apicurio_ai.core.a2a_discovery import AgentDiscovery
from apicurio_ai.core.config import RegistryConfig
from apicurio_ai.core.mcp_discovery import McpToolDiscovery
from apicurio_ai.core.prompt import PromptGovernance

try:
    from fastapi import FastAPI
except ImportError as e:
    raise ImportError(
        "fastapi is required for the FastAPI adapter. "
        "Install with: pip install apicurio-ai[fastapi]"
    ) from e

logger = logging.getLogger(__name__)


@asynccontextmanager
async def apicurio_lifespan(
    app: FastAPI, config: RegistryConfig
) -> AsyncGenerator[None, None]:
    mcp = McpToolDiscovery(config)
    agents = AgentDiscovery(config)
    prompts = PromptGovernance(config)

    try:
        await agents.get_registry_agent_card()
    except httpx.HTTPError as e:
        logger.warning("Registry connectivity check failed: %s", e)

    app.state.apicurio_mcp_discovery = mcp
    app.state.apicurio_agent_discovery = agents
    app.state.apicurio_prompt_governance = prompts

    yield

    await mcp.close()
    await agents.close()
    await prompts.close()
