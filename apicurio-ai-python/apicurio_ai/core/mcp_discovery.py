from typing import Any, Optional

import httpx

from apicurio_ai.core._models import McpToolSearchResults
from apicurio_ai.core._wellknown import WellKnownClient
from apicurio_ai.core.config import RegistryConfig


class McpToolDiscovery:
    def __init__(self, config: RegistryConfig) -> None:
        self._config = config
        self._wellknown = WellKnownClient(config)

    async def close(self) -> None:
        await self._wellknown.close()

    async def __aenter__(self) -> "McpToolDiscovery":
        return self

    async def __aexit__(self, *args: Any) -> None:
        await self.close()

    async def search(
        self,
        name: Optional[str] = None,
        parameters: Optional[list[str]] = None,
        offset: int = 0,
        limit: int = 20,
    ) -> McpToolSearchResults:
        return await self._wellknown.search_mcp_tools(
            name=name, parameters=parameters, offset=offset, limit=limit
        )

    async def get_tool(
        self,
        group_id: str,
        artifact_id: str,
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        return await self._wellknown.get_registered_mcp_tool(
            group_id=group_id, artifact_id=artifact_id, version=version
        )

    async def list_all_tools(self) -> list[dict[str, Any]]:
        results: list[dict[str, Any]] = []
        offset = 0
        limit = 100
        while True:
            page = await self.search(offset=offset, limit=limit)
            results.extend(
                t.model_dump(by_alias=True, exclude_none=True) for t in page.tools
            )
            if offset + limit >= page.count:
                break
            offset += limit
        return results

    async def publish_tool(
        self,
        artifact_id: str,
        tool_definition: dict[str, Any],
        group_id: Optional[str] = None,
    ) -> None:
        gid = group_id or self._config.default_group_id
        url = f"{self._config.api_base_url}/groups/{gid}/artifacts"
        import json

        async with httpx.AsyncClient(
            headers=self._config.auth_headers(), timeout=self._config.timeout
        ) as client:
            resp = await client.post(
                url,
                json={
                    "artifactId": artifact_id,
                    "artifactType": "MCP_TOOL",
                    "firstVersion": {
                        "content": {
                            "content": json.dumps(tool_definition),
                            "contentType": "application/json",
                        }
                    },
                },
            )
            resp.raise_for_status()
