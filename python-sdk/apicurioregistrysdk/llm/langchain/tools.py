"""
LangChain Tools for Apicurio Registry.

This module provides LangChain Tool implementations that allow LLM agents
to fetch prompt templates and model schemas from Apicurio Registry.
"""

from typing import Any, Dict, List, Optional, Type
import asyncio

try:
    from langchain_core.tools import BaseTool
    from langchain_core.callbacks import CallbackManagerForToolRun, AsyncCallbackManagerForToolRun
    from pydantic import BaseModel, Field
    LANGCHAIN_AVAILABLE = True
except ImportError:
    try:
        from langchain.tools import BaseTool
        from langchain.callbacks.manager import CallbackManagerForToolRun, AsyncCallbackManagerForToolRun
        from pydantic import BaseModel, Field
        LANGCHAIN_AVAILABLE = True
    except ImportError:
        LANGCHAIN_AVAILABLE = False
        BaseTool = object
        BaseModel = object
        Field = lambda **kwargs: None
        CallbackManagerForToolRun = None
        AsyncCallbackManagerForToolRun = None


def _check_langchain():
    """Check if LangChain is available."""
    if not LANGCHAIN_AVAILABLE:
        raise ImportError(
            "LangChain is required for this feature. "
            "Install it with: pip install langchain-core"
        )


class PromptLookupInput(BaseModel if LANGCHAIN_AVAILABLE else object):
    """Input schema for the prompt lookup tool."""
    artifact_id: str = Field(description="The artifact ID of the prompt template to fetch")
    version: Optional[str] = Field(
        default=None,
        description="Specific version to fetch (optional, defaults to latest)"
    )
    group_id: Optional[str] = Field(
        default=None,
        description="Group ID in the registry (optional, defaults to 'default')"
    )


class ApicurioPromptLookupTool(BaseTool if LANGCHAIN_AVAILABLE else object):
    """
    LangChain tool for fetching prompt templates from Apicurio Registry.

    This tool allows LLM agents to retrieve versioned prompt templates
    stored in Apicurio Registry.

    Example:
        >>> from apicurioregistrysdk.llm.langchain import ApicurioPromptLookupTool
        >>> from langchain.agents import initialize_agent, AgentType
        >>> from langchain.llms import OpenAI
        >>>
        >>> tool = ApicurioPromptLookupTool(registry_url="http://localhost:8080")
        >>> agent = initialize_agent(
        ...     tools=[tool],
        ...     llm=OpenAI(),
        ...     agent=AgentType.ZERO_SHOT_REACT_DESCRIPTION
        ... )
        >>> result = agent.run("Fetch the summarization-v1 prompt template")
    """

    name: str = "apicurio_prompt_lookup"
    description: str = (
        "Fetch a versioned prompt template from Apicurio Registry. "
        "Use this tool when you need to retrieve a prompt template by its artifact ID. "
        "Input should include the artifact_id (required) and optionally version and group_id."
    )
    args_schema: Type[BaseModel] = PromptLookupInput

    registry_url: str = Field(description="Base URL of the Apicurio Registry")
    default_group_id: str = Field(default="default", description="Default group ID")

    def __init__(self, registry_url: str, default_group_id: str = "default", **kwargs):
        """
        Initialize the tool.

        Args:
            registry_url: Base URL of the Apicurio Registry
            default_group_id: Default group ID for lookups
        """
        _check_langchain()
        super().__init__(
            registry_url=registry_url,
            default_group_id=default_group_id,
            **kwargs
        )

    def _run(
        self,
        artifact_id: str,
        version: Optional[str] = None,
        group_id: Optional[str] = None,
        run_manager: Optional[CallbackManagerForToolRun] = None,
    ) -> str:
        """
        Fetch a prompt template synchronously.

        Args:
            artifact_id: The artifact ID of the prompt template
            version: Specific version to fetch
            group_id: Group ID in the registry
            run_manager: Callback manager

        Returns:
            The prompt template content as a string
        """
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(
                        asyncio.run,
                        self._arun(artifact_id, version, group_id)
                    )
                    return future.result()
            else:
                return loop.run_until_complete(
                    self._arun(artifact_id, version, group_id)
                )
        except RuntimeError:
            return asyncio.run(self._arun(artifact_id, version, group_id))

    async def _arun(
        self,
        artifact_id: str,
        version: Optional[str] = None,
        group_id: Optional[str] = None,
        run_manager: Optional[AsyncCallbackManagerForToolRun] = None,
    ) -> str:
        """
        Fetch a prompt template asynchronously.

        Args:
            artifact_id: The artifact ID of the prompt template
            version: Specific version to fetch
            group_id: Group ID in the registry
            run_manager: Callback manager

        Returns:
            The prompt template content as a string
        """
        from ..prompt_registry import PromptRegistry

        registry = PromptRegistry(
            self.registry_url,
            group_id or self.default_group_id
        )

        prompt = await registry.get_prompt(artifact_id, version)

        # Return formatted information about the prompt
        result = {
            "template_id": prompt.template_id,
            "name": prompt.name,
            "version": prompt.version,
            "template": prompt.template,
            "variables": prompt.variables,
            "description": prompt.description,
        }

        import json
        return json.dumps(result, indent=2)


class ModelLookupInput(BaseModel if LANGCHAIN_AVAILABLE else object):
    """Input schema for the model lookup tool."""
    provider: Optional[str] = Field(
        default=None,
        description="Filter by provider (e.g., 'openai', 'anthropic')"
    )
    capabilities: Optional[List[str]] = Field(
        default=None,
        description="Filter by required capabilities (e.g., ['vision', 'tool_use'])"
    )
    min_context_window: Optional[int] = Field(
        default=None,
        description="Minimum context window size in tokens"
    )


class ApicurioModelLookupTool(BaseTool if LANGCHAIN_AVAILABLE else object):
    """
    LangChain tool for searching model schemas in Apicurio Registry.

    This tool allows LLM agents to search for AI/ML model schemas
    based on capabilities, provider, and context window requirements.

    Example:
        >>> from apicurioregistrysdk.llm.langchain import ApicurioModelLookupTool
        >>>
        >>> tool = ApicurioModelLookupTool(registry_url="http://localhost:8080")
        >>> result = tool.run({
        ...     "capabilities": ["vision", "tool_use"],
        ...     "min_context_window": 100000
        ... })
    """

    name: str = "apicurio_model_lookup"
    description: str = (
        "Search for AI/ML model schemas in Apicurio Registry. "
        "Use this tool to find models by provider, capabilities, or context window size. "
        "Input can include provider, capabilities (list), and min_context_window."
    )
    args_schema: Type[BaseModel] = ModelLookupInput

    registry_url: str = Field(description="Base URL of the Apicurio Registry")
    default_group_id: str = Field(default="default", description="Default group ID")

    def __init__(self, registry_url: str, default_group_id: str = "default", **kwargs):
        """
        Initialize the tool.

        Args:
            registry_url: Base URL of the Apicurio Registry
            default_group_id: Default group ID for lookups
        """
        _check_langchain()
        super().__init__(
            registry_url=registry_url,
            default_group_id=default_group_id,
            **kwargs
        )

    def _run(
        self,
        provider: Optional[str] = None,
        capabilities: Optional[List[str]] = None,
        min_context_window: Optional[int] = None,
        run_manager: Optional[CallbackManagerForToolRun] = None,
    ) -> str:
        """
        Search for models synchronously.

        Args:
            provider: Filter by provider
            capabilities: Filter by capabilities
            min_context_window: Minimum context window
            run_manager: Callback manager

        Returns:
            JSON string with matching models
        """
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(
                        asyncio.run,
                        self._arun(provider, capabilities, min_context_window)
                    )
                    return future.result()
            else:
                return loop.run_until_complete(
                    self._arun(provider, capabilities, min_context_window)
                )
        except RuntimeError:
            return asyncio.run(
                self._arun(provider, capabilities, min_context_window)
            )

    async def _arun(
        self,
        provider: Optional[str] = None,
        capabilities: Optional[List[str]] = None,
        min_context_window: Optional[int] = None,
        run_manager: Optional[AsyncCallbackManagerForToolRun] = None,
    ) -> str:
        """
        Search for models asynchronously.

        Args:
            provider: Filter by provider
            capabilities: Filter by capabilities
            min_context_window: Minimum context window
            run_manager: Callback manager

        Returns:
            JSON string with matching models
        """
        from ..model_registry import ModelRegistry

        registry = ModelRegistry(
            self.registry_url,
            self.default_group_id
        )

        results = await registry.search(
            provider=provider,
            capabilities=capabilities,
            min_context_window=min_context_window
        )

        # Format results
        models = []
        for model in results.models:
            models.append({
                "model_id": model.model_id,
                "provider": model.provider,
                "version": model.version,
                "context_window": model.context_window,
                "capabilities": model.capabilities,
            })

        import json
        return json.dumps({
            "count": results.count,
            "models": models
        }, indent=2)
