import re
from typing import Any, Optional

from apicurio_ai.core.prompt import PromptGovernance

try:
    from langchain_core.prompts import ChatPromptTemplate
except ImportError as e:
    raise ImportError(
        "langchain-core is required for the LangChain adapter. "
        "Install with: pip install apicurio-ai[langchain]"
    ) from e


class ApicurioPromptTemplate:
    def __init__(
        self,
        prompt_governance: PromptGovernance,
        group_id: str,
        artifact_id: str,
        version: str = "branch=latest",
    ) -> None:
        self._governance = prompt_governance
        self._group_id = group_id
        self._artifact_id = artifact_id
        self._version = version

    async def format(self, **variables: Any) -> str:
        result = await self._governance.render(
            group_id=self._group_id,
            artifact_id=self._artifact_id,
            variables=variables,
            version=self._version,
        )
        return result.rendered

    async def to_langchain(self) -> ChatPromptTemplate:
        raw = await self._governance.get_template(
            group_id=self._group_id,
            artifact_id=self._artifact_id,
            version=self._version,
        )
        template_text = _extract_template_text(raw)
        langchain_template = re.sub(r"\{\{(\w+)\}\}", r"{\1}", template_text)
        return ChatPromptTemplate.from_template(langchain_template)


def _extract_template_text(raw_content: str) -> str:
    try:
        import json

        data = json.loads(raw_content)
        return data.get("template", raw_content)
    except (json.JSONDecodeError, ValueError):
        pass
    try:
        import yaml  # type: ignore[import-untyped]

        data = yaml.safe_load(raw_content)
        if isinstance(data, dict):
            return data.get("template", raw_content)
    except Exception:
        pass
    return raw_content
