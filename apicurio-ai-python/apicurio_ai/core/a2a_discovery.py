import json
from typing import Any, Optional
from urllib.parse import quote

import httpx

from apicurio_ai.core._models import (
    AgentCard,
    AgentSearchRequest,
    AgentSearchResults,
)
from apicurio_ai.core._wellknown import WellKnownClient
from apicurio_ai.core.config import RegistryConfig


class AgentDiscovery:
    def __init__(self, config: RegistryConfig) -> None:
        self._config = config
        self._wellknown = WellKnownClient(config)
        self._api_client = httpx.AsyncClient(
            headers=config.auth_headers(), timeout=config.timeout
        )

    async def close(self) -> None:
        await self._wellknown.close()
        await self._api_client.aclose()

    async def __aenter__(self) -> "AgentDiscovery":
        return self

    async def __aexit__(self, *args: Any) -> None:
        await self.close()

    async def get_registry_agent_card(self) -> AgentCard:
        return await self._wellknown.get_agent_card()

    async def search(
        self,
        name: Optional[str] = None,
        skills: Optional[list[str]] = None,
        capabilities: Optional[list[str]] = None,
        input_modes: Optional[list[str]] = None,
        output_modes: Optional[list[str]] = None,
        offset: int = 0,
        limit: int = 20,
    ) -> AgentSearchResults:
        return await self._wellknown.search_agents(
            name=name,
            skills=skills,
            capabilities=capabilities,
            input_modes=input_modes,
            output_modes=output_modes,
            offset=offset,
            limit=limit,
        )

    async def search_advanced(self, request: AgentSearchRequest) -> AgentSearchResults:
        return await self._wellknown.search_agents_advanced(request)

    async def get_public_agents(
        self, offset: int = 0, limit: int = 20
    ) -> AgentSearchResults:
        return await self._wellknown.get_public_agents(offset=offset, limit=limit)

    async def get_entitled_agents(
        self, offset: int = 0, limit: int = 20
    ) -> AgentSearchResults:
        return await self._wellknown.get_entitled_agents(offset=offset, limit=limit)

    async def get_agent(
        self,
        group_id: str,
        artifact_id: str,
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        return await self._wellknown.get_registered_agent(
            group_id=group_id, artifact_id=artifact_id, version=version
        )

    async def publish_agent(
        self,
        artifact_id: str,
        agent_card: dict[str, Any],
        group_id: Optional[str] = None,
        labels: Optional[dict[str, str]] = None,
    ) -> None:
        gid = group_id or self._config.default_group_id
        url = f"{self._config.api_base_url}/groups/{quote(gid, safe='')}/artifacts"
        body: dict[str, Any] = {
            "artifactId": artifact_id,
            "artifactType": "AGENT_CARD",
            "firstVersion": {
                "content": {
                    "content": json.dumps(agent_card),
                    "contentType": "application/json",
                }
            },
        }
        if labels:
            body["labels"] = labels
        resp = await self._api_client.post(url, json=body)
        resp.raise_for_status()
