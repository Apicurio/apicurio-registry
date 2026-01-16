"""
LangChain PromptTemplate integration for Apicurio Registry.

This module provides a LangChain-compatible PromptTemplate that fetches
its template content from Apicurio Registry.
"""

from typing import Any, Dict, List, Optional
import asyncio

try:
    from langchain_core.prompts import BasePromptTemplate
    from langchain_core.prompts.base import format_document
    from langchain_core.pydantic_v1 import Field, root_validator
    LANGCHAIN_AVAILABLE = True
except ImportError:
    try:
        from langchain.prompts import BasePromptTemplate
        from langchain.pydantic_v1 import Field, root_validator
        LANGCHAIN_AVAILABLE = True
    except ImportError:
        LANGCHAIN_AVAILABLE = False
        BasePromptTemplate = object
        Field = lambda **kwargs: None
        root_validator = lambda **kwargs: lambda f: f


def _check_langchain():
    """Check if LangChain is available."""
    if not LANGCHAIN_AVAILABLE:
        raise ImportError(
            "LangChain is required for this feature. "
            "Install it with: pip install langchain-core"
        )


class ApicurioPromptTemplate(BasePromptTemplate if LANGCHAIN_AVAILABLE else object):
    """
    A LangChain PromptTemplate that fetches templates from Apicurio Registry.

    This class extends LangChain's BasePromptTemplate to provide a seamless
    integration with prompt templates stored in Apicurio Registry.

    The template is fetched lazily on first use and cached for subsequent calls.

    Example:
        >>> from apicurioregistrysdk.llm.langchain import ApicurioPromptTemplate
        >>>
        >>> prompt = ApicurioPromptTemplate(
        ...     registry_url="http://localhost:8080",
        ...     artifact_id="summarization-v1",
        ...     group_id="default"
        ... )
        >>>
        >>> # Use like any LangChain PromptTemplate
        >>> result = prompt.format(document="...", style="concise", max_words=200)
        >>>
        >>> # Or use with chains
        >>> from langchain.chains import LLMChain
        >>> chain = LLMChain(llm=llm, prompt=prompt)
        >>> chain.run(document="...", style="concise", max_words=200)
    """

    registry_url: str = Field(description="Base URL of the Apicurio Registry")
    artifact_id: str = Field(description="Artifact ID of the prompt template")
    group_id: str = Field(default="default", description="Group ID in the registry")
    version: Optional[str] = Field(default=None, description="Specific version to use")

    # Internal fields
    _template_content: Optional[str] = None
    _input_variables: List[str] = []
    _variable_schemas: Dict[str, Any] = {}
    _fetched: bool = False

    class Config:
        """Pydantic config."""
        extra = "allow"

    def __init__(self, **kwargs):
        """Initialize the template."""
        _check_langchain()
        super().__init__(**kwargs)
        self._template_content = None
        self._input_variables = []
        self._variable_schemas = {}
        self._fetched = False

    @property
    def input_variables(self) -> List[str]:
        """Get input variables, fetching template if needed."""
        if not self._fetched:
            self._fetch_template_sync()
        return self._input_variables

    @input_variables.setter
    def input_variables(self, value: List[str]):
        """Set input variables."""
        self._input_variables = value

    def _fetch_template_sync(self):
        """Synchronously fetch the template (for compatibility)."""
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # We're in an async context, use a new loop in a thread
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(asyncio.run, self._fetch_template())
                    future.result()
            else:
                loop.run_until_complete(self._fetch_template())
        except RuntimeError:
            # No event loop, create one
            asyncio.run(self._fetch_template())

    async def _fetch_template(self):
        """Fetch the template from the registry."""
        if self._fetched:
            return

        from ..prompt_registry import PromptRegistry

        registry = PromptRegistry(self.registry_url, self.group_id)
        prompt = await registry.get_prompt(self.artifact_id, self.version)

        self._template_content = prompt.template
        self._input_variables = list(prompt.variables.keys())
        self._variable_schemas = prompt.variables
        self._fetched = True

    def format(self, **kwargs: Any) -> str:
        """
        Format the prompt with the given variables.

        Args:
            **kwargs: Variable values to substitute

        Returns:
            Formatted prompt string
        """
        if not self._fetched:
            self._fetch_template_sync()

        # Validate variables
        self._validate_variables(kwargs)

        # Convert {{var}} to actual values
        result = self._template_content
        for key, value in kwargs.items():
            placeholder = "{{" + key + "}}"
            if isinstance(value, (list, dict)):
                import json
                replacement = json.dumps(value)
            else:
                replacement = str(value)
            result = result.replace(placeholder, replacement)

        return result

    def _validate_variables(self, variables: Dict[str, Any]):
        """Validate variables against schema."""
        for var_name, var_schema in self._variable_schemas.items():
            required = var_schema.get("required", False)
            if required and var_name not in variables:
                raise ValueError(f"Required variable '{var_name}' is missing")

            if var_name in variables:
                value = variables[var_name]
                expected_type = var_schema.get("type", "string")

                type_map = {
                    "string": str,
                    "integer": int,
                    "number": (int, float),
                    "boolean": bool,
                    "array": list,
                    "object": dict,
                }

                expected = type_map.get(expected_type)
                if expected and not isinstance(value, expected):
                    raise TypeError(
                        f"Variable '{var_name}' expected {expected_type}, "
                        f"got {type(value).__name__}"
                    )

                # Check enum
                if "enum" in var_schema:
                    if str(value) not in [str(e) for e in var_schema["enum"]]:
                        raise ValueError(
                            f"Variable '{var_name}' value '{value}' not in "
                            f"allowed values: {var_schema['enum']}"
                        )

    async def aformat(self, **kwargs: Any) -> str:
        """
        Async version of format.

        Args:
            **kwargs: Variable values to substitute

        Returns:
            Formatted prompt string
        """
        await self._fetch_template()
        return self.format(**kwargs)

    def format_prompt(self, **kwargs: Any):
        """Format and return as a PromptValue."""
        from langchain_core.prompt_values import StringPromptValue
        return StringPromptValue(text=self.format(**kwargs))

    async def aformat_prompt(self, **kwargs: Any):
        """Async version of format_prompt."""
        from langchain_core.prompt_values import StringPromptValue
        return StringPromptValue(text=await self.aformat(**kwargs))

    @property
    def _prompt_type(self) -> str:
        """Return the prompt type."""
        return "apicurio-registry"

    def get_variable_schemas(self) -> Dict[str, Any]:
        """
        Get the variable schemas from the template.

        Returns:
            Dictionary of variable names to their schema definitions
        """
        if not self._fetched:
            self._fetch_template_sync()
        return self._variable_schemas

    def get_defaults(self) -> Dict[str, Any]:
        """
        Get default values for variables.

        Returns:
            Dictionary of variable names to their default values
        """
        if not self._fetched:
            self._fetch_template_sync()

        defaults = {}
        for var_name, var_schema in self._variable_schemas.items():
            if "default" in var_schema:
                defaults[var_name] = var_schema["default"]
        return defaults

    def refresh(self):
        """
        Refresh the template from the registry.

        Call this to fetch the latest version of the template.
        """
        self._fetched = False
        self._fetch_template_sync()

    async def arefresh(self):
        """Async version of refresh."""
        self._fetched = False
        await self._fetch_template()
