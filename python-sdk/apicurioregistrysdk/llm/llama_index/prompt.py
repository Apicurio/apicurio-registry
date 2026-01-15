"""
LlamaIndex PromptTemplate integration for Apicurio Registry.

This module provides a LlamaIndex-compatible PromptTemplate that fetches
its template content from Apicurio Registry.
"""

from typing import Any, Dict, List, Optional
import asyncio

try:
    from llama_index.core.prompts import PromptTemplate as LIPromptTemplate
    from llama_index.core.prompts.base import PromptType
    LLAMA_INDEX_AVAILABLE = True
except ImportError:
    LLAMA_INDEX_AVAILABLE = False
    LIPromptTemplate = object
    PromptType = None


def _check_llama_index():
    """Check if LlamaIndex is available."""
    if not LLAMA_INDEX_AVAILABLE:
        raise ImportError(
            "LlamaIndex is required for this feature. "
            "Install it with: pip install llama-index-core"
        )


class ApicurioPromptTemplate(LIPromptTemplate if LLAMA_INDEX_AVAILABLE else object):
    """
    A LlamaIndex PromptTemplate that fetches templates from Apicurio Registry.

    This class extends LlamaIndex's PromptTemplate to provide seamless
    integration with prompt templates stored in Apicurio Registry.

    The template is fetched lazily on first use and cached for subsequent calls.

    Example:
        >>> from apicurioregistrysdk.llm.llama_index import ApicurioPromptTemplate
        >>> from llama_index.core.llms import OpenAI
        >>>
        >>> prompt = ApicurioPromptTemplate(
        ...     registry_url="http://localhost:8080",
        ...     artifact_id="summarization-v1",
        ... )
        >>>
        >>> # Use like any LlamaIndex PromptTemplate
        >>> formatted = prompt.format(document="...", style="concise")
        >>>
        >>> # Or use with query engines
        >>> llm = OpenAI()
        >>> response = llm.complete(prompt.format(document="..."))
    """

    def __init__(
        self,
        registry_url: str,
        artifact_id: str,
        group_id: str = "default",
        version: Optional[str] = None,
        prompt_type: Optional[str] = None,
        **kwargs
    ):
        """
        Initialize the template.

        Args:
            registry_url: Base URL of the Apicurio Registry
            artifact_id: Artifact ID of the prompt template
            group_id: Group ID in the registry
            version: Specific version to use
            prompt_type: LlamaIndex prompt type (optional)
        """
        _check_llama_index()

        self._registry_url = registry_url
        self._artifact_id = artifact_id
        self._group_id = group_id
        self._version = version
        self._prompt_type = prompt_type

        # Internal state
        self._template_content: Optional[str] = None
        self._variable_schemas: Dict[str, Any] = {}
        self._metadata: Dict[str, Any] = {}
        self._fetched: bool = False

        # Initialize with a placeholder template
        # The actual template will be fetched lazily
        super().__init__(
            template="",
            prompt_type=PromptType.CUSTOM if prompt_type is None else prompt_type,
            **kwargs
        )

    def _fetch_template_sync(self):
        """Synchronously fetch the template."""
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(asyncio.run, self._fetch_template())
                    future.result()
            else:
                loop.run_until_complete(self._fetch_template())
        except RuntimeError:
            asyncio.run(self._fetch_template())

    async def _fetch_template(self):
        """Fetch the template from the registry."""
        if self._fetched:
            return

        from ..prompt_registry import PromptRegistry

        registry = PromptRegistry(self._registry_url, self._group_id)
        prompt = await registry.get_prompt(self._artifact_id, self._version)

        self._template_content = prompt.template
        self._variable_schemas = prompt.variables
        self._metadata = prompt.metadata

        # Update the template in the parent class
        self.template = prompt.template
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

        # Validate required variables
        self._validate_variables(kwargs)

        # Use {{var}} substitution pattern (same as original template)
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

    def partial_format(self, **kwargs: Any) -> "ApicurioPromptTemplate":
        """
        Return a new prompt with some variables filled in.

        Args:
            **kwargs: Variable values to pre-fill

        Returns:
            New ApicurioPromptTemplate with partial substitution
        """
        if not self._fetched:
            self._fetch_template_sync()

        # Create partial template
        result = self._template_content
        for key, value in kwargs.items():
            placeholder = "{{" + key + "}}"
            if isinstance(value, (list, dict)):
                import json
                replacement = json.dumps(value)
            else:
                replacement = str(value)
            result = result.replace(placeholder, replacement)

        # Create a new template instance with the partial result
        new_template = ApicurioPromptTemplate.__new__(ApicurioPromptTemplate)
        new_template._registry_url = self._registry_url
        new_template._artifact_id = self._artifact_id
        new_template._group_id = self._group_id
        new_template._version = self._version
        new_template._prompt_type = self._prompt_type
        new_template._template_content = result
        new_template._fetched = True

        # Remove the filled variables from the schema
        new_template._variable_schemas = {
            k: v for k, v in self._variable_schemas.items()
            if k not in kwargs
        }
        new_template._metadata = self._metadata.copy()

        # Initialize parent with the partial template
        LIPromptTemplate.__init__(
            new_template,
            template=result,
            prompt_type=PromptType.CUSTOM if self._prompt_type is None else self._prompt_type
        )

        return new_template

    def get_template_vars(self) -> List[str]:
        """
        Get the list of variable names in the template.

        Returns:
            List of variable names
        """
        if not self._fetched:
            self._fetch_template_sync()
        return list(self._variable_schemas.keys())

    def get_variable_schemas(self) -> Dict[str, Any]:
        """
        Get the variable schemas from the template.

        Returns:
            Dictionary of variable names to their schema definitions
        """
        if not self._fetched:
            self._fetch_template_sync()
        return self._variable_schemas

    def get_metadata(self) -> Dict[str, Any]:
        """
        Get the template metadata.

        Returns:
            Dictionary of metadata
        """
        if not self._fetched:
            self._fetch_template_sync()
        return self._metadata

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
