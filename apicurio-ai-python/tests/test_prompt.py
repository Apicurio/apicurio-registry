import json

import pytest

from apicurio_ai.core.config import RegistryConfig
from apicurio_ai.core.prompt import PromptGovernance
from tests.conftest import register_artifact

PROMPT_TEMPLATE = {
    "$schema": "https://registry.example.com/.well-known/schemas/prompt-template/v1",
    "templateId": "greeting-prompt",
    "name": "Greeting Prompt",
    "description": "A simple greeting prompt template",
    "version": "1.0.0",
    "template": "Hello, {{name}}! Welcome to {{service}}.",
    "variables": {
        "name": {
            "type": "string",
            "required": True,
            "description": "The user's name",
        },
        "service": {
            "type": "string",
            "required": True,
            "description": "The service name",
        },
    },
}


@pytest.fixture(scope="module", autouse=True)
def seed_prompt_template():
    register_artifact(
        group_id="ai-test",
        artifact_id="greeting-prompt",
        artifact_type="PROMPT_TEMPLATE",
        content=json.dumps(PROMPT_TEMPLATE),
    )


@pytest.mark.asyncio
async def test_render_prompt(registry_config: RegistryConfig):
    async with PromptGovernance(registry_config) as governance:
        result = await governance.render(
            group_id="ai-test",
            artifact_id="greeting-prompt",
            variables={"name": "Alice", "service": "Apicurio"},
        )
        assert result.rendered == "Hello, Alice! Welcome to Apicurio."
        assert result.validation_errors is None or len(result.validation_errors) == 0


@pytest.mark.asyncio
async def test_get_template_content(registry_config: RegistryConfig):
    async with PromptGovernance(registry_config) as governance:
        content = await governance.get_template(
            group_id="ai-test", artifact_id="greeting-prompt"
        )
        assert "{{name}}" in content
        assert "{{service}}" in content


@pytest.mark.asyncio
async def test_get_prompt_schema(registry_config: RegistryConfig):
    async with PromptGovernance(registry_config) as governance:
        schema = await governance.get_schema()
        assert "properties" in schema
        assert "templateId" in schema["properties"]
