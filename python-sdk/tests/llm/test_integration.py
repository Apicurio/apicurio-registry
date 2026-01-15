"""
Integration tests for the LLM module.

These tests require a running registry server and verify the full workflow
of the PromptRegistry and ModelRegistry classes.

To run these tests:
    cd python-sdk
    pytest tests/llm/test_integration.py -v
"""

import asyncio
import pytest
import json
import os
import sys
import time
import subprocess
import requests
import uuid
from kiota_abstractions.authentication.anonymous_authentication_provider import (
    AnonymousAuthenticationProvider,
)
from kiota_http.httpx_request_adapter import HttpxRequestAdapter
from apicurioregistrysdk.client.registry_client import RegistryClient
from apicurioregistrysdk.client.models.create_artifact import CreateArtifact
from apicurioregistrysdk.client.models.create_version import CreateVersion
from apicurioregistrysdk.client.models.version_content import VersionContent

# Import LLM module components
from apicurioregistrysdk.llm.prompt_registry import PromptRegistry, PromptTemplate
from apicurioregistrysdk.llm.model_registry import ModelRegistry, ModelSchema

REGISTRY_HOST = "localhost"
REGISTRY_PORT = 8080
REGISTRY_URL = f"http://{REGISTRY_HOST}:{REGISTRY_PORT}/apis/registry/v3"
MAX_POLL_TIME = 120
POLL_INTERVAL = 1


def poll_for_ready():
    """Poll until the registry server is ready."""
    start_time = time.time()
    while True:
        elapsed_time = time.time() - start_time
        if elapsed_time >= MAX_POLL_TIME:
            pytest.fail("Registry server did not start in time")
            break

        try:
            response = requests.get(REGISTRY_URL)
            if response.status_code == 200:
                print("Server is up!")
                return True
        except requests.exceptions.ConnectionError:
            pass

        time.sleep(POLL_INTERVAL)
    return False


@pytest.fixture(scope="module")
def registry_server(request):
    """Start the registry server if not already running."""
    # Check if server is already running
    try:
        response = requests.get(REGISTRY_URL)
        if response.status_code == 200:
            print("Registry server is already running")
            yield
            return
    except requests.exceptions.ConnectionError:
        pass

    # Start the registry server
    registry_jar = os.path.join(
        sys.path[0], "..", "..", "app", "target", "apicurio-registry-app-*-runner.jar"
    )
    print(f"Starting Registry from jar {registry_jar}")
    p = subprocess.Popen(f"java -jar {registry_jar}", shell=True)
    request.addfinalizer(p.kill)
    poll_for_ready()
    yield


@pytest.fixture(scope="module")
def event_loop():
    """Create an event loop for async tests."""
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
def kiota_client():
    """Create a Kiota-based RegistryClient."""
    auth_provider = AnonymousAuthenticationProvider()
    request_adapter = HttpxRequestAdapter(auth_provider)
    request_adapter.base_url = REGISTRY_URL
    return RegistryClient(request_adapter)


@pytest.fixture
def prompt_registry():
    """Create a PromptRegistry instance."""
    return PromptRegistry(
        base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
        group_id="llm-test"
    )


@pytest.fixture
def model_registry():
    """Create a ModelRegistry instance."""
    return ModelRegistry(
        base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
        group_id="llm-test"
    )


def unique_id():
    """Generate a unique ID for test artifacts."""
    return f"test-{uuid.uuid4().hex[:8]}"


class TestPromptRegistryIntegration:
    """Integration tests for PromptRegistry."""

    @pytest.mark.asyncio
    async def test_create_and_fetch_prompt(self, registry_server, kiota_client, prompt_registry):
        """Test creating a prompt template and fetching it via PromptRegistry."""
        artifact_id = unique_id()

        # Create a prompt template artifact using Kiota client
        prompt_content = json.dumps({
            "templateId": artifact_id,
            "name": "Test Greeting Prompt",
            "version": "1.0",
            "template": "Hello {{name}}! Welcome to {{place}}.",
            "variables": {
                "name": {"type": "string", "required": True},
                "place": {"type": "string", "default": "Registry"}
            }
        })

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "PROMPT_TEMPLATE"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/json"
        payload.first_version.content.content = prompt_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Fetch the prompt using PromptRegistry
        prompt = await prompt_registry.get_prompt_async(artifact_id)

        assert prompt is not None
        assert prompt.template_id == artifact_id
        assert "Hello {{name}}" in prompt.template
        assert "name" in prompt.variables
        assert "place" in prompt.variables

    @pytest.mark.asyncio
    async def test_render_prompt_locally(self, registry_server, kiota_client, prompt_registry):
        """Test fetching a prompt and rendering it locally."""
        artifact_id = unique_id()

        prompt_content = json.dumps({
            "templateId": artifact_id,
            "name": "Local Render Test",
            "version": "1.0",
            "template": "Summary style: {{style}}, max words: {{max_words}}",
            "variables": {
                "style": {"type": "string", "enum": ["concise", "detailed"]},
                "max_words": {"type": "integer", "minimum": 50, "maximum": 500}
            }
        })

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "PROMPT_TEMPLATE"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/json"
        payload.first_version.content.content = prompt_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Fetch and render
        prompt = await prompt_registry.get_prompt_async(artifact_id)
        rendered = prompt.render(style="concise", max_words=200)

        assert rendered == "Summary style: concise, max words: 200"

    @pytest.mark.asyncio
    async def test_render_prompt_server_side(self, registry_server, kiota_client, prompt_registry):
        """Test server-side rendering via the /render endpoint."""
        artifact_id = unique_id()

        prompt_content = json.dumps({
            "templateId": artifact_id,
            "name": "Server Render Test",
            "version": "1.0",
            "template": "Hello {{name}}, your task is {{task}}.",
            "variables": {
                "name": {"type": "string"},
                "task": {"type": "string"}
            }
        })

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "PROMPT_TEMPLATE"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/json"
        payload.first_version.content.content = prompt_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Server-side render
        result = await prompt_registry.render_server_side_async(
            artifact_id,
            {"name": "Developer", "task": "testing"}
        )

        assert result.rendered == "Hello Developer, your task is testing."
        assert len(result.validation_errors) == 0

    @pytest.mark.asyncio
    async def test_list_prompts(self, registry_server, kiota_client, prompt_registry):
        """Test listing prompt templates."""
        artifact_ids = [unique_id() for _ in range(3)]

        # Create multiple prompts
        for artifact_id in artifact_ids:
            prompt_content = json.dumps({
                "templateId": artifact_id,
                "name": f"List Test {artifact_id}",
                "version": "1.0",
                "template": "Template {{var}}",
                "variables": {"var": {"type": "string"}}
            })

            payload = CreateArtifact()
            payload.artifact_id = artifact_id
            payload.artifact_type = "PROMPT_TEMPLATE"
            payload.first_version = CreateVersion()
            payload.first_version.content = VersionContent()
            payload.first_version.content.content_type = "application/json"
            payload.first_version.content.content = prompt_content

            await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # List prompts
        prompts = await prompt_registry.list_prompts_async()

        # Verify at least the prompts we created are present
        prompt_ids = {p.artifact_id for p in prompts}
        for artifact_id in artifact_ids:
            assert artifact_id in prompt_ids


class TestModelRegistryIntegration:
    """Integration tests for ModelRegistry."""

    @pytest.mark.asyncio
    async def test_create_and_fetch_model(self, registry_server, kiota_client, model_registry):
        """Test creating a model schema and fetching it via ModelRegistry."""
        artifact_id = unique_id()

        model_content = json.dumps({
            "modelId": artifact_id,
            "provider": "test-provider",
            "version": "1.0",
            "metadata": {
                "contextWindow": 100000,
                "capabilities": ["chat", "vision"]
            }
        })

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "MODEL_SCHEMA"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/json"
        payload.first_version.content.content = model_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Fetch the model
        model = await model_registry.get_model_async(artifact_id)

        assert model is not None
        assert model.model_id == artifact_id
        assert model.provider == "test-provider"
        assert model.context_window == 100000
        assert "chat" in model.capabilities
        assert "vision" in model.capabilities

    @pytest.mark.asyncio
    async def test_search_models_by_provider(self, registry_server, kiota_client, model_registry):
        """Test searching models by provider."""
        group = f"provider-test-{unique_id()}"

        # Create models from different providers
        providers = ["openai", "anthropic", "google"]
        for provider in providers:
            artifact_id = f"{provider}-model-{unique_id()}"
            model_content = json.dumps({
                "modelId": artifact_id,
                "provider": provider,
                "version": "1.0",
                "metadata": {
                    "contextWindow": 50000,
                    "capabilities": ["chat"]
                }
            })

            payload = CreateArtifact()
            payload.artifact_id = artifact_id
            payload.artifact_type = "MODEL_SCHEMA"
            payload.first_version = CreateVersion()
            payload.first_version.content = VersionContent()
            payload.first_version.content.content_type = "application/json"
            payload.first_version.content.content = model_content

            await kiota_client.groups.by_group_id(group).artifacts.post(payload)

        # Search for OpenAI models only
        local_registry = ModelRegistry(
            base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
            group_id=group
        )
        results = await local_registry.search_async(provider="openai")

        assert results.count >= 1
        for model in results.models:
            assert model.provider == "openai"

    @pytest.mark.asyncio
    async def test_search_models_by_capabilities(self, registry_server, kiota_client, model_registry):
        """Test searching models by capabilities."""
        group = f"cap-test-{unique_id()}"

        # Create models with different capabilities
        models_data = [
            {"id": "vision-model", "caps": ["chat", "vision"]},
            {"id": "tools-model", "caps": ["chat", "tool_use"]},
            {"id": "both-model", "caps": ["chat", "vision", "tool_use"]},
            {"id": "basic-model", "caps": ["chat"]},
        ]

        for model_data in models_data:
            artifact_id = f"{model_data['id']}-{unique_id()}"
            model_content = json.dumps({
                "modelId": artifact_id,
                "provider": "test",
                "version": "1.0",
                "metadata": {
                    "contextWindow": 50000,
                    "capabilities": model_data["caps"]
                }
            })

            payload = CreateArtifact()
            payload.artifact_id = artifact_id
            payload.artifact_type = "MODEL_SCHEMA"
            payload.first_version = CreateVersion()
            payload.first_version.content = VersionContent()
            payload.first_version.content.content_type = "application/json"
            payload.first_version.content.content = model_content

            await kiota_client.groups.by_group_id(group).artifacts.post(payload)

        # Search for models with vision capability
        local_registry = ModelRegistry(
            base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
            group_id=group
        )

        results = await local_registry.search_async(capabilities=["vision"])

        assert results.count >= 2  # vision-model and both-model
        for model in results.models:
            assert model.has_capability("vision")

    @pytest.mark.asyncio
    async def test_search_models_by_context_window(self, registry_server, kiota_client, model_registry):
        """Test searching models by context window size."""
        group = f"ctx-test-{unique_id()}"

        # Create models with different context windows
        context_sizes = [4096, 32000, 100000, 200000]

        for ctx_size in context_sizes:
            artifact_id = f"ctx-{ctx_size}-{unique_id()}"
            model_content = json.dumps({
                "modelId": artifact_id,
                "provider": "test",
                "version": "1.0",
                "metadata": {
                    "contextWindow": ctx_size,
                    "capabilities": ["chat"]
                }
            })

            payload = CreateArtifact()
            payload.artifact_id = artifact_id
            payload.artifact_type = "MODEL_SCHEMA"
            payload.first_version = CreateVersion()
            payload.first_version.content = VersionContent()
            payload.first_version.content.content_type = "application/json"
            payload.first_version.content.content = model_content

            await kiota_client.groups.by_group_id(group).artifacts.post(payload)

        # Search for models with context window >= 50000
        local_registry = ModelRegistry(
            base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
            group_id=group
        )

        results = await local_registry.search_async(min_context_window=50000)

        assert results.count >= 2  # 100000 and 200000
        for model in results.models:
            assert model.context_window >= 50000

    @pytest.mark.asyncio
    async def test_compare_models(self, registry_server, kiota_client, model_registry):
        """Test comparing multiple models."""
        group = f"compare-test-{unique_id()}"

        # Create two models to compare
        models_data = [
            {
                "id": f"model-a-{unique_id()}",
                "provider": "openai",
                "context_window": 128000,
                "capabilities": ["chat", "vision", "function_calling"]
            },
            {
                "id": f"model-b-{unique_id()}",
                "provider": "anthropic",
                "context_window": 200000,
                "capabilities": ["chat", "vision", "tool_use"]
            }
        ]

        artifact_ids = []
        for model_data in models_data:
            model_content = json.dumps({
                "modelId": model_data["id"],
                "provider": model_data["provider"],
                "version": "1.0",
                "metadata": {
                    "contextWindow": model_data["context_window"],
                    "capabilities": model_data["capabilities"]
                }
            })

            payload = CreateArtifact()
            payload.artifact_id = model_data["id"]
            payload.artifact_type = "MODEL_SCHEMA"
            payload.first_version = CreateVersion()
            payload.first_version.content = VersionContent()
            payload.first_version.content.content_type = "application/json"
            payload.first_version.content.content = model_content

            await kiota_client.groups.by_group_id(group).artifacts.post(payload)
            artifact_ids.append(model_data["id"])

        # Compare models
        local_registry = ModelRegistry(
            base_url=f"http://{REGISTRY_HOST}:{REGISTRY_PORT}",
            group_id=group
        )

        comparison = await local_registry.compare_models_async(artifact_ids)

        assert len(comparison.models) == 2
        assert comparison.common_capabilities is not None
        assert "chat" in comparison.common_capabilities
        assert "vision" in comparison.common_capabilities


class TestValidationIntegration:
    """Integration tests for validation via the /render endpoint."""

    @pytest.mark.asyncio
    async def test_server_side_validation_errors(self, registry_server, kiota_client, prompt_registry):
        """Test that server-side rendering returns validation errors."""
        artifact_id = unique_id()

        prompt_content = json.dumps({
            "templateId": artifact_id,
            "name": "Validation Test",
            "version": "1.0",
            "template": "Name: {{name}}, Count: {{count}}, Style: {{style}}",
            "variables": {
                "name": {"type": "string", "required": True},
                "count": {"type": "integer", "minimum": 10, "maximum": 100},
                "style": {"type": "string", "enum": ["formal", "casual"]}
            }
        })

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "PROMPT_TEMPLATE"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/json"
        payload.first_version.content.content = prompt_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Render with invalid values
        result = await prompt_registry.render_server_side_async(
            artifact_id,
            {
                # name is missing (required)
                "count": 5,  # below minimum
                "style": "weird"  # not in enum
            }
        )

        # Should return validation errors
        assert len(result.validation_errors) == 3

        error_vars = {e.variable_name for e in result.validation_errors}
        assert "name" in error_vars
        assert "count" in error_vars
        assert "style" in error_vars


class TestYamlSupportIntegration:
    """Integration tests for YAML template support."""

    @pytest.mark.asyncio
    async def test_yaml_prompt_template(self, registry_server, kiota_client, prompt_registry):
        """Test fetching and rendering YAML-formatted prompt templates."""
        artifact_id = unique_id()

        yaml_content = f"""
templateId: {artifact_id}
name: YAML Test Prompt
version: "1.0"
template: "Hello {{{{name}}}}! Task: {{{{task}}}}"
variables:
  name:
    type: string
    required: true
  task:
    type: string
    default: "testing"
"""

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "PROMPT_TEMPLATE"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/x-yaml"
        payload.first_version.content.content = yaml_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Fetch and render
        prompt = await prompt_registry.get_prompt_async(artifact_id)
        rendered = prompt.render(name="Developer", task="coding")

        assert "Hello Developer" in rendered
        assert "Task: coding" in rendered

    @pytest.mark.asyncio
    async def test_yaml_model_schema(self, registry_server, kiota_client, model_registry):
        """Test fetching YAML-formatted model schemas."""
        artifact_id = unique_id()

        yaml_content = f"""
modelId: {artifact_id}
provider: yaml-test-provider
version: "2.0"
metadata:
  contextWindow: 75000
  capabilities:
    - chat
    - code_generation
    - function_calling
"""

        payload = CreateArtifact()
        payload.artifact_id = artifact_id
        payload.artifact_type = "MODEL_SCHEMA"
        payload.first_version = CreateVersion()
        payload.first_version.content = VersionContent()
        payload.first_version.content.content_type = "application/x-yaml"
        payload.first_version.content.content = yaml_content

        await kiota_client.groups.by_group_id("llm-test").artifacts.post(payload)

        # Fetch the model
        model = await model_registry.get_model_async(artifact_id)

        assert model.model_id == artifact_id
        assert model.provider == "yaml-test-provider"
        assert model.context_window == 75000
        assert "code_generation" in model.capabilities
