from typing import Any, Optional

import httpx

from apicurio_ai.core._models import (
    AgentCard,
    AgentSearchRequest,
    AgentSearchResults,
    McpToolSearchResults,
)
from apicurio_ai.core.config import RegistryConfig


class WellKnownClient:
    def __init__(self, config: RegistryConfig) -> None:
        self._config = config
        self._client = httpx.AsyncClient(
            base_url=config.registry_url.rstrip("/"),
            headers=config.auth_headers(),
            timeout=config.timeout,
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> "WellKnownClient":
        return self

    async def __aexit__(self, *args: Any) -> None:
        await self.close()

    async def get_agent_card(self) -> AgentCard:
        resp = await self._client.get("/.well-known/agent.json")
        resp.raise_for_status()
        return AgentCard.model_validate(resp.json())

    async def search_agents(
        self,
        name: Optional[str] = None,
        skills: Optional[list[str]] = None,
        capabilities: Optional[list[str]] = None,
        input_modes: Optional[list[str]] = None,
        output_modes: Optional[list[str]] = None,
        offset: int = 0,
        limit: int = 20,
    ) -> AgentSearchResults:
        params: dict[str, Any] = {"offset": offset, "limit": limit}
        if name:
            params["name"] = name
        if skills:
            params["skill"] = skills
        if capabilities:
            params["capability"] = capabilities
        if input_modes:
            params["inputMode"] = input_modes
        if output_modes:
            params["outputMode"] = output_modes
        resp = await self._client.get("/.well-known/agents", params=params)
        resp.raise_for_status()
        return AgentSearchResults.model_validate(resp.json())

    async def search_agents_advanced(
        self, request: AgentSearchRequest
    ) -> AgentSearchResults:
        resp = await self._client.post(
            "/.well-known/agents/search",
            json=request.model_dump(by_alias=True, exclude_none=True),
        )
        resp.raise_for_status()
        return AgentSearchResults.model_validate(resp.json())

    async def get_public_agents(
        self, offset: int = 0, limit: int = 20
    ) -> AgentSearchResults:
        resp = await self._client.get(
            "/.well-known/agents/public",
            params={"offset": offset, "limit": limit},
        )
        resp.raise_for_status()
        return AgentSearchResults.model_validate(resp.json())

    async def get_entitled_agents(
        self, offset: int = 0, limit: int = 20
    ) -> AgentSearchResults:
        resp = await self._client.get(
            "/.well-known/agents/entitled",
            params={"offset": offset, "limit": limit},
        )
        resp.raise_for_status()
        return AgentSearchResults.model_validate(resp.json())

    async def get_registered_agent(
        self,
        group_id: str,
        artifact_id: str,
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        params: dict[str, str] = {}
        if version:
            params["version"] = version
        resp = await self._client.get(
            f"/.well-known/agents/{group_id}/{artifact_id}", params=params
        )
        resp.raise_for_status()
        return resp.json()

    async def search_mcp_tools(
        self,
        name: Optional[str] = None,
        parameters: Optional[list[str]] = None,
        offset: int = 0,
        limit: int = 20,
    ) -> McpToolSearchResults:
        params: dict[str, Any] = {"offset": offset, "limit": limit}
        if name:
            params["name"] = name
        if parameters:
            params["parameter"] = parameters
        resp = await self._client.get("/.well-known/mcp-tools", params=params)
        resp.raise_for_status()
        return McpToolSearchResults.model_validate(resp.json())

    async def get_registered_mcp_tool(
        self,
        group_id: str,
        artifact_id: str,
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        params: dict[str, str] = {}
        if version:
            params["version"] = version
        resp = await self._client.get(
            f"/.well-known/mcp-tools/{group_id}/{artifact_id}", params=params
        )
        resp.raise_for_status()
        return resp.json()

    async def get_schema(self, schema_type: str, version: str = "v1") -> dict[str, Any]:
        resp = await self._client.get(f"/.well-known/schemas/{schema_type}/{version}")
        resp.raise_for_status()
        return resp.json()
