from typing import Any, Optional

from apicurio_ai.core.mcp_discovery import McpToolDiscovery

try:
    from langchain_core.tools import StructuredTool
except ImportError as e:
    raise ImportError(
        "langchain-core is required for the LangChain adapter. "
        "Install with: pip install apicurio-ai[langchain]"
    ) from e


def _build_tool_from_definition(tool_def: dict[str, Any]) -> StructuredTool:
    name = tool_def.get("name", "unknown_tool")
    description = tool_def.get("description", "")

    def _not_implemented(**kwargs: Any) -> str:
        raise NotImplementedError(
            f"MCP tool '{name}' was loaded from the registry for schema/metadata only. "
            "Connect to the actual MCP server to execute this tool."
        )

    return StructuredTool.from_function(
        func=_not_implemented,
        name=name,
        description=description,
        args_schema=None,
    )


class ApicurioMcpToolkit:
    def __init__(self, mcp_discovery: McpToolDiscovery) -> None:
        self._discovery = mcp_discovery

    async def get_tools(
        self,
        name: Optional[str] = None,
        parameters: Optional[list[str]] = None,
    ) -> list[StructuredTool]:
        results = await self._discovery.search(name=name, parameters=parameters)
        tools: list[StructuredTool] = []
        for result in results.tools:
            tool_def = await self._discovery.get_tool(
                group_id=result.group_id or "default",
                artifact_id=result.artifact_id or "",
            )
            tools.append(_build_tool_from_definition(tool_def))
        return tools

    async def get_tool(self, group_id: str, artifact_id: str) -> StructuredTool:
        tool_def = await self._discovery.get_tool(
            group_id=group_id, artifact_id=artifact_id
        )
        return _build_tool_from_definition(tool_def)
