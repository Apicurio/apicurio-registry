import json
from typing import Any, Optional
from urllib.parse import quote

import httpx

from apicurio_ai.core._models import RenderPromptResponse
from apicurio_ai.core._wellknown import WellKnownClient
from apicurio_ai.core.config import RegistryConfig


class PromptGovernance:
    def __init__(self, config: RegistryConfig) -> None:
        self._config = config
        self._wellknown = WellKnownClient(config)
        self._api_client = httpx.AsyncClient(
            headers=config.auth_headers(), timeout=config.timeout
        )

    async def close(self) -> None:
        await self._wellknown.close()
        await self._api_client.aclose()

    async def __aenter__(self) -> "PromptGovernance":
        return self

    async def __aexit__(self, *args: Any) -> None:
        await self.close()

    async def render(
        self,
        group_id: str,
        artifact_id: str,
        variables: dict[str, Any],
        version: str = "branch=latest",
    ) -> RenderPromptResponse:
        url = (
            f"{self._config.api_base_url}/groups/{quote(group_id, safe='')}"
            f"/artifacts/{quote(artifact_id, safe='')}"
            f"/versions/{quote(version, safe='')}/render"
        )
        resp = await self._api_client.post(url, json={"variables": variables})
        resp.raise_for_status()
        return RenderPromptResponse.model_validate(resp.json())

    async def get_template(
        self,
        group_id: str,
        artifact_id: str,
        version: str = "branch=latest",
    ) -> str:
        url = (
            f"{self._config.api_base_url}/groups/{quote(group_id, safe='')}"
            f"/artifacts/{quote(artifact_id, safe='')}"
            f"/versions/{quote(version, safe='')}/content"
        )
        resp = await self._api_client.get(url)
        resp.raise_for_status()
        return resp.text

    async def publish_template(
        self,
        artifact_id: str,
        content: str,
        group_id: Optional[str] = None,
        content_type: str = "application/x-yaml",
    ) -> None:
        gid = group_id or self._config.default_group_id
        url = f"{self._config.api_base_url}/groups/{quote(gid, safe='')}/artifacts"
        body: dict[str, Any] = {
            "artifactId": artifact_id,
            "artifactType": "PROMPT_TEMPLATE",
            "firstVersion": {
                "content": {
                    "content": content,
                    "contentType": content_type,
                }
            },
        }
        resp = await self._api_client.post(url, json=body)
        resp.raise_for_status()

    async def get_schema(self, version: str = "v1") -> dict[str, Any]:
        return await self._wellknown.get_schema("prompt-template", version)
