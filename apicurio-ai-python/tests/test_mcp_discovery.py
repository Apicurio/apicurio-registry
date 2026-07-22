import json

import pytest

from apicurio_ai.core.config import RegistryConfig
from apicurio_ai.core.mcp_discovery import McpToolDiscovery
from tests.conftest import register_artifact

MCP_TOOL_DEF = {
    "name": "test-calculator",
    "description": "A simple calculator tool",
    "inputSchema": {
        "type": "object",
        "properties": {
            "operation": {"type": "string", "enum": ["add", "subtract"]},
            "a": {"type": "number"},
            "b": {"type": "number"},
        },
        "required": ["operation", "a", "b"],
    },
}


@pytest.fixture(scope="module", autouse=True)
def seed_mcp_tool():
    register_artifact(
        group_id="ai-test",
        artifact_id="calculator-tool",
        artifact_type="MCP_TOOL",
        content=json.dumps(MCP_TOOL_DEF),
    )


@pytest.mark.asyncio
async def test_search_mcp_tools(registry_config: RegistryConfig):
    async with McpToolDiscovery(registry_config) as discovery:
        results = await discovery.search(name="calculator")
        assert results.count >= 1
        tool = next(
            (t for t in results.tools if t.artifact_id == "calculator-tool"), None
        )
        assert tool is not None
        assert "operation" in (tool.parameters or [])


@pytest.mark.asyncio
async def test_get_mcp_tool(registry_config: RegistryConfig):
    async with McpToolDiscovery(registry_config) as discovery:
        tool_def = await discovery.get_tool(
            group_id="ai-test", artifact_id="calculator-tool"
        )
        assert tool_def["name"] == "test-calculator"
        assert "inputSchema" in tool_def
