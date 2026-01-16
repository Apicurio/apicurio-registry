"""
Prompt Registry module for working with PROMPT_TEMPLATE artifacts.

This module provides a high-level API for fetching, rendering, and managing
prompt templates stored in Apicurio Registry.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Union
import json
import re

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False

from kiota_abstractions.authentication.anonymous_authentication_provider import (
    AnonymousAuthenticationProvider,
)
from kiota_http.httpx_request_adapter import HttpxRequestAdapter


@dataclass
class ValidationError:
    """Represents a variable validation error."""
    variable_name: str
    message: str
    expected_type: Optional[str] = None
    actual_type: Optional[str] = None


@dataclass
class PromptTemplate:
    """
    A prompt template fetched from Apicurio Registry.

    Attributes:
        template_id: Unique identifier for the template
        name: Human-readable name
        version: Version string
        template: The raw template text with {{variable}} placeholders
        variables: Schema definitions for template variables
        metadata: Additional metadata (author, tags, etc.)
        raw_content: Original YAML/JSON content
        group_id: Registry group ID
        artifact_id: Registry artifact ID
    """
    template_id: str
    name: str
    version: str
    template: str
    variables: Dict[str, Any] = field(default_factory=dict)
    metadata: Dict[str, Any] = field(default_factory=dict)
    raw_content: str = ""
    group_id: str = "default"
    artifact_id: str = ""
    description: Optional[str] = None
    output_schema: Optional[Dict[str, Any]] = None

    def render(self, **variables: Any) -> str:
        """
        Render the template with provided variables.

        Args:
            **variables: Variable values to substitute into the template

        Returns:
            The rendered template string

        Raises:
            ValueError: If a required variable is missing
            TypeError: If a variable has the wrong type

        Example:
            >>> prompt = PromptTemplate(template="Hello {{name}}!", ...)
            >>> prompt.render(name="World")
            'Hello World!'
        """
        errors = self.validate_variables(variables)
        if errors:
            error_msgs = [f"{e.variable_name}: {e.message}" for e in errors]
            raise ValueError(f"Variable validation failed: {'; '.join(error_msgs)}")

        result = self.template
        for key, value in variables.items():
            placeholder = "{{" + key + "}}"
            replacement = self._format_value(value)
            result = result.replace(placeholder, replacement)

        return result

    def validate_variables(self, variables: Dict[str, Any]) -> List[ValidationError]:
        """
        Validate variables against the template's variable schema.

        Args:
            variables: Dictionary of variable values to validate

        Returns:
            List of validation errors (empty if all valid)
        """
        errors: List[ValidationError] = []

        for var_name, var_schema in self.variables.items():
            # Check required
            required = var_schema.get("required", False)
            if required and var_name not in variables:
                errors.append(ValidationError(
                    variable_name=var_name,
                    message="Required variable is missing"
                ))
                continue

            if var_name not in variables:
                continue

            value = variables[var_name]
            expected_type = var_schema.get("type", "string")

            # Type validation
            type_error = self._validate_type(var_name, value, expected_type)
            if type_error:
                errors.append(type_error)
                continue

            # Enum validation
            if "enum" in var_schema:
                if str(value) not in [str(e) for e in var_schema["enum"]]:
                    errors.append(ValidationError(
                        variable_name=var_name,
                        message=f"Value '{value}' not in allowed values: {var_schema['enum']}"
                    ))

            # Range validation for numeric types
            if expected_type in ("integer", "number"):
                if "minimum" in var_schema and value < var_schema["minimum"]:
                    errors.append(ValidationError(
                        variable_name=var_name,
                        message=f"Value {value} is less than minimum {var_schema['minimum']}"
                    ))
                if "maximum" in var_schema and value > var_schema["maximum"]:
                    errors.append(ValidationError(
                        variable_name=var_name,
                        message=f"Value {value} is greater than maximum {var_schema['maximum']}"
                    ))

        return errors

    def _validate_type(self, var_name: str, value: Any, expected_type: str) -> Optional[ValidationError]:
        """Validate that a value matches the expected type."""
        type_map = {
            "string": str,
            "integer": int,
            "number": (int, float),
            "boolean": bool,
            "array": list,
            "object": dict,
        }

        expected_python_type = type_map.get(expected_type)
        if expected_python_type and not isinstance(value, expected_python_type):
            return ValidationError(
                variable_name=var_name,
                message=f"Type mismatch: expected {expected_type} but got {type(value).__name__}",
                expected_type=expected_type,
                actual_type=type(value).__name__
            )
        return None

    def _format_value(self, value: Any) -> str:
        """Format a value for template substitution."""
        if isinstance(value, (list, dict)):
            return json.dumps(value)
        return str(value)

    def get_variable_names(self) -> List[str]:
        """
        Extract all variable names from the template.

        Returns:
            List of variable names found in {{...}} placeholders
        """
        pattern = r"\{\{([^}]+)\}\}"
        matches = re.findall(pattern, self.template)
        return [m.strip() for m in matches]

    def get_defaults(self) -> Dict[str, Any]:
        """
        Get default values for all variables that have them.

        Returns:
            Dictionary of variable names to their default values
        """
        defaults = {}
        for var_name, var_schema in self.variables.items():
            if "default" in var_schema:
                defaults[var_name] = var_schema["default"]
        return defaults

    def to_langchain(self):
        """
        Convert to a LangChain PromptTemplate.

        Returns:
            langchain.prompts.PromptTemplate instance

        Raises:
            ImportError: If langchain is not installed

        Example:
            >>> from apicurioregistrysdk.llm import PromptRegistry
            >>> registry = PromptRegistry("http://localhost:8080")
            >>> prompt = await registry.get_prompt("my-prompt")
            >>> lc_prompt = prompt.to_langchain()
            >>> result = lc_prompt.format(name="World")
        """
        try:
            from langchain_core.prompts import PromptTemplate as LCPromptTemplate
        except ImportError:
            try:
                from langchain.prompts import PromptTemplate as LCPromptTemplate
            except ImportError:
                raise ImportError(
                    "LangChain is required for this feature. "
                    "Install it with: pip install langchain-core"
                )

        # Convert {{var}} to {var} for LangChain format
        lc_template = self.template.replace("{{", "{").replace("}}", "}")

        # Extract input variables from schema
        input_variables = list(self.variables.keys())

        return LCPromptTemplate(
            template=lc_template,
            input_variables=input_variables
        )

    def to_llama_index(self):
        """
        Convert to a LlamaIndex PromptTemplate.

        Returns:
            llama_index.core.prompts.PromptTemplate instance

        Raises:
            ImportError: If llama-index is not installed
        """
        try:
            from llama_index.core.prompts import PromptTemplate as LIPromptTemplate
        except ImportError:
            raise ImportError(
                "LlamaIndex is required for this feature. "
                "Install it with: pip install llama-index-core"
            )

        return LIPromptTemplate(self.template)


class PromptRegistry:
    """
    High-level API for managing prompts in Apicurio Registry.

    This class provides methods for fetching, listing, and rendering
    prompt templates stored as PROMPT_TEMPLATE artifacts.

    Example:
        >>> from apicurioregistrysdk.llm import PromptRegistry
        >>>
        >>> registry = PromptRegistry("http://localhost:8080")
        >>> prompt = await registry.get_prompt("summarization-v1")
        >>> rendered = prompt.render(document="...", style="concise")
    """

    def __init__(
        self,
        base_url: str,
        group_id: str = "default",
        auth_provider=None,
        **client_options
    ):
        """
        Initialize the PromptRegistry.

        Args:
            base_url: Base URL of the Apicurio Registry (e.g., "http://localhost:8080")
            group_id: Default group ID for artifact lookups
            auth_provider: Optional authentication provider (defaults to anonymous)
            **client_options: Additional options passed to the HTTP adapter
        """
        self.base_url = base_url.rstrip("/")
        self.api_url = f"{self.base_url}/apis/registry/v3"
        self.group_id = group_id

        if auth_provider is None:
            auth_provider = AnonymousAuthenticationProvider()

        self._request_adapter = HttpxRequestAdapter(auth_provider)
        self._request_adapter.base_url = self.api_url
        self._client = None  # Lazy initialization

    def _get_client(self):
        """Get or create the registry client."""
        if self._client is None:
            from apicurioregistrysdk.client.registry_client import RegistryClient
            self._client = RegistryClient(self._request_adapter)
        return self._client

    async def get_prompt(
        self,
        artifact_id: str,
        version: Optional[str] = None,
        group_id: Optional[str] = None
    ) -> PromptTemplate:
        """
        Fetch a prompt template from the registry.

        Args:
            artifact_id: The artifact ID of the prompt template
            version: Specific version to fetch (defaults to latest)
            group_id: Group ID (defaults to instance default)

        Returns:
            PromptTemplate instance

        Raises:
            Exception: If the artifact is not found or cannot be parsed
        """
        group = group_id or self.group_id
        version_expr = version if version else "branch=latest"

        client = self._get_client()
        content = await (
            client.groups.by_group_id(group)
            .artifacts.by_artifact_id(artifact_id)
            .versions.by_version_expression(version_expr)
            .content.get()
        )

        # Get version metadata
        version_meta = await (
            client.groups.by_group_id(group)
            .artifacts.by_artifact_id(artifact_id)
            .versions.by_version_expression(version_expr)
            .get()
        )

        actual_version = version_meta.version if version_meta else version_expr

        return self._parse_prompt(
            content,
            artifact_id,
            actual_version,
            group
        )

    def _parse_prompt(
        self,
        content: bytes,
        artifact_id: str,
        version: str,
        group_id: str
    ) -> PromptTemplate:
        """Parse YAML/JSON prompt template content."""
        text = content.decode("utf-8")

        # Try YAML first (which also handles JSON)
        try:
            if HAS_YAML:
                data = yaml.safe_load(text)
            else:
                data = json.loads(text)
        except Exception:
            data = json.loads(text)

        return PromptTemplate(
            template_id=data.get("templateId", artifact_id),
            name=data.get("name", artifact_id),
            version=data.get("version", version),
            template=data.get("template", ""),
            variables=data.get("variables", {}),
            metadata=data.get("metadata", {}),
            raw_content=text,
            group_id=group_id,
            artifact_id=artifact_id,
            description=data.get("description"),
            output_schema=data.get("outputSchema")
        )

    async def list_prompts(
        self,
        group_id: Optional[str] = None,
        name: Optional[str] = None,
        limit: int = 20,
        offset: int = 0
    ) -> List[Dict[str, Any]]:
        """
        List available prompt templates.

        Args:
            group_id: Filter by group ID (defaults to instance default)
            name: Filter by name (partial match)
            limit: Maximum number of results
            offset: Number of results to skip

        Returns:
            List of artifact metadata dictionaries
        """
        client = self._get_client()

        # Use the search endpoint with artifactType filter
        results = await client.search.artifacts.get()

        # Filter for PROMPT_TEMPLATE type
        prompts = []
        if results and results.artifacts:
            for artifact in results.artifacts:
                if artifact.artifact_type == "PROMPT_TEMPLATE":
                    prompts.append({
                        "groupId": artifact.group_id,
                        "artifactId": artifact.artifact_id,
                        "name": artifact.name,
                        "description": artifact.description,
                        "createdOn": artifact.created_on,
                        "modifiedOn": artifact.modified_on,
                    })

        # Apply additional filters
        group = group_id or self.group_id
        if group:
            prompts = [p for p in prompts if p.get("groupId") == group]
        if name:
            prompts = [p for p in prompts if name.lower() in (p.get("name") or "").lower()]

        # Apply pagination
        return prompts[offset:offset + limit]

    async def render_server_side(
        self,
        artifact_id: str,
        variables: Dict[str, Any],
        version: Optional[str] = None,
        group_id: Optional[str] = None
    ) -> str:
        """
        Render a prompt on the server with validation.

        This uses the registry's /render endpoint for server-side
        template rendering and variable validation.

        Args:
            artifact_id: The artifact ID of the prompt template
            variables: Variables to substitute
            version: Specific version (defaults to latest)
            group_id: Group ID (defaults to instance default)

        Returns:
            Rendered prompt string

        Raises:
            Exception: If rendering fails or validation errors occur
        """
        import httpx

        group = group_id or self.group_id
        version_expr = version if version else "branch=latest"

        url = f"{self.api_url}/groups/{group}/artifacts/{artifact_id}/versions/{version_expr}/render"

        async with httpx.AsyncClient() as client:
            response = await client.post(
                url,
                json={"variables": variables},
                headers={"Content-Type": "application/json"}
            )
            response.raise_for_status()
            result = response.json()

            if result.get("validationErrors"):
                errors = result["validationErrors"]
                error_msgs = [f"{e['variableName']}: {e['message']}" for e in errors]
                raise ValueError(f"Validation errors: {'; '.join(error_msgs)}")

            return result.get("rendered", "")
