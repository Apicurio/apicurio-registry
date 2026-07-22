import json

import pytest

from apicurio_ai.core.a2a_discovery import AgentDiscovery
from apicurio_ai.core.config import RegistryConfig
from tests.conftest import register_artifact

AGENT_CARD = {
    "name": "test-weather-agent",
    "description": "An agent that provides weather information",
    "version": "1.0.0",
    "protocolVersion": "1.0",
    "provider": {"organization": "Test Org", "url": "https://example.com"},
    "capabilities": {"streaming": False, "pushNotifications": False},
    "skills": [
        {
            "id": "get-weather",
            "name": "Get Weather",
            "description": "Get current weather for a location",
            "tags": ["weather"],
            "inputModes": ["text"],
            "outputModes": ["text"],
        }
    ],
    "defaultInputModes": ["text"],
    "defaultOutputModes": ["text"],
    "supportedInterfaces": [
        {
            "url": "https://example.com/a2a",
            "protocolBinding": "jsonrpc/http",
            "protocolVersion": "1.0",
        }
    ],
}


@pytest.fixture(scope="module", autouse=True)
def seed_agent_card():
    register_artifact(
        group_id="ai-test",
        artifact_id="weather-agent",
        artifact_type="AGENT_CARD",
        content=json.dumps(AGENT_CARD),
    )


@pytest.mark.asyncio
async def test_get_registry_agent_card(registry_config: RegistryConfig):
    async with AgentDiscovery(registry_config) as discovery:
        card = await discovery.get_registry_agent_card()
        assert card.name is not None
        assert card.skills is not None


@pytest.mark.asyncio
async def test_search_agents(registry_config: RegistryConfig):
    async with AgentDiscovery(registry_config) as discovery:
        results = await discovery.search(name="weather")
        assert results.count >= 1
        agent = next(
            (a for a in results.agents if a.artifact_id == "weather-agent"), None
        )
        assert agent is not None


@pytest.mark.asyncio
async def test_get_registered_agent(registry_config: RegistryConfig):
    async with AgentDiscovery(registry_config) as discovery:
        card = await discovery.get_agent(
            group_id="ai-test", artifact_id="weather-agent"
        )
        assert card["name"] == "test-weather-agent"
        assert len(card["skills"]) == 1
