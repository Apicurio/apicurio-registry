"""
Model Registry module for working with MODEL_SCHEMA artifacts.

This module provides a high-level API for fetching, searching, and managing
AI/ML model schemas stored in Apicurio Registry.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
import json

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
class ModelPricing:
    """Pricing information for a model."""
    input_cost: float
    output_cost: float
    currency: str = "USD"
    unit: str = "1K tokens"


@dataclass
class ModelSchema:
    """
    An AI/ML model schema fetched from Apicurio Registry.

    Attributes:
        model_id: Unique identifier for the model (e.g., "claude-3-opus")
        provider: Model provider (e.g., "anthropic", "openai")
        version: Model version
        input_schema: JSON Schema for model input
        output_schema: JSON Schema for model output
        metadata: Additional metadata including capabilities and pricing
        context_window: Maximum context window size in tokens
        capabilities: List of model capabilities
        raw_content: Original JSON content
        group_id: Registry group ID
        artifact_id: Registry artifact ID
    """
    model_id: str
    provider: str
    version: str
    input_schema: Dict[str, Any] = field(default_factory=dict)
    output_schema: Dict[str, Any] = field(default_factory=dict)
    metadata: Dict[str, Any] = field(default_factory=dict)
    context_window: Optional[int] = None
    capabilities: List[str] = field(default_factory=list)
    raw_content: str = ""
    group_id: str = "default"
    artifact_id: str = ""
    pricing: Optional[ModelPricing] = None

    def has_capability(self, capability: str) -> bool:
        """
        Check if the model has a specific capability.

        Args:
            capability: Capability name to check (e.g., "vision", "tool_use")

        Returns:
            True if the model has the capability
        """
        return capability.lower() in [c.lower() for c in self.capabilities]

    def has_all_capabilities(self, capabilities: List[str]) -> bool:
        """
        Check if the model has all specified capabilities.

        Args:
            capabilities: List of capability names to check

        Returns:
            True if the model has all capabilities
        """
        model_caps = [c.lower() for c in self.capabilities]
        return all(cap.lower() in model_caps for cap in capabilities)

    def has_any_capability(self, capabilities: List[str]) -> bool:
        """
        Check if the model has any of the specified capabilities.

        Args:
            capabilities: List of capability names to check

        Returns:
            True if the model has at least one of the capabilities
        """
        model_caps = [c.lower() for c in self.capabilities]
        return any(cap.lower() in model_caps for cap in capabilities)

    def supports_context_size(self, tokens: int) -> bool:
        """
        Check if the model supports a given context size.

        Args:
            tokens: Number of tokens to check

        Returns:
            True if the model's context window is >= tokens
        """
        if self.context_window is None:
            return False
        return self.context_window >= tokens

    def estimate_cost(self, input_tokens: int, output_tokens: int) -> Optional[float]:
        """
        Estimate the cost for a given number of tokens.

        Args:
            input_tokens: Number of input tokens
            output_tokens: Number of output tokens

        Returns:
            Estimated cost in the pricing currency, or None if pricing unavailable
        """
        if self.pricing is None:
            return None

        # Assuming pricing is per 1K tokens
        input_cost = (input_tokens / 1000) * self.pricing.input_cost
        output_cost = (output_tokens / 1000) * self.pricing.output_cost
        return input_cost + output_cost

    def to_dict(self) -> Dict[str, Any]:
        """
        Convert to a dictionary representation.

        Returns:
            Dictionary with all model information
        """
        return {
            "modelId": self.model_id,
            "provider": self.provider,
            "version": self.version,
            "contextWindow": self.context_window,
            "capabilities": self.capabilities,
            "inputSchema": self.input_schema,
            "outputSchema": self.output_schema,
            "metadata": self.metadata,
            "groupId": self.group_id,
            "artifactId": self.artifact_id,
            "pricing": {
                "input": self.pricing.input_cost,
                "output": self.pricing.output_cost,
                "currency": self.pricing.currency,
                "unit": self.pricing.unit,
            } if self.pricing else None,
        }


@dataclass
class ModelSearchResults:
    """Results from a model search query."""
    models: List[ModelSchema]
    count: int
    offset: int = 0
    limit: int = 20


class ModelRegistry:
    """
    High-level API for managing AI/ML model schemas in Apicurio Registry.

    This class provides methods for fetching, listing, and searching
    model schemas stored as MODEL_SCHEMA artifacts.

    Example:
        >>> from apicurioregistrysdk.llm import ModelRegistry
        >>>
        >>> registry = ModelRegistry("http://localhost:8080")
        >>>
        >>> # Get a specific model
        >>> model = await registry.get_model("claude-3-opus")
        >>>
        >>> # Search by capabilities
        >>> models = await registry.search(
        ...     capabilities=["vision", "tool_use"],
        ...     min_context_window=100000
        ... )
    """

    def __init__(
        self,
        base_url: str,
        group_id: str = "default",
        auth_provider=None,
        **client_options
    ):
        """
        Initialize the ModelRegistry.

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

    async def get_model(
        self,
        artifact_id: str,
        version: Optional[str] = None,
        group_id: Optional[str] = None
    ) -> ModelSchema:
        """
        Fetch a model schema from the registry.

        Args:
            artifact_id: The artifact ID of the model schema
            version: Specific version to fetch (defaults to latest)
            group_id: Group ID (defaults to instance default)

        Returns:
            ModelSchema instance

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

        return self._parse_model(
            content,
            artifact_id,
            actual_version,
            group
        )

    def _parse_model(
        self,
        content: bytes,
        artifact_id: str,
        version: str,
        group_id: str
    ) -> ModelSchema:
        """Parse JSON model schema content."""
        text = content.decode("utf-8")

        # Try YAML first (which also handles JSON)
        try:
            if HAS_YAML:
                data = yaml.safe_load(text)
            else:
                data = json.loads(text)
        except Exception:
            data = json.loads(text)

        metadata = data.get("metadata", {})

        # Extract pricing if available
        pricing = None
        pricing_data = metadata.get("pricing")
        if pricing_data:
            pricing = ModelPricing(
                input_cost=pricing_data.get("input", 0),
                output_cost=pricing_data.get("output", 0),
                currency=pricing_data.get("currency", "USD"),
                unit=pricing_data.get("unit", "1K tokens"),
            )

        return ModelSchema(
            model_id=data.get("modelId", artifact_id),
            provider=data.get("provider", ""),
            version=data.get("version", version),
            input_schema=data.get("input", {}),
            output_schema=data.get("output", {}),
            metadata=metadata,
            context_window=metadata.get("contextWindow"),
            capabilities=metadata.get("capabilities", []),
            raw_content=text,
            group_id=group_id,
            artifact_id=artifact_id,
            pricing=pricing,
        )

    async def search(
        self,
        provider: Optional[str] = None,
        capabilities: Optional[List[str]] = None,
        min_context_window: Optional[int] = None,
        max_context_window: Optional[int] = None,
        group_id: Optional[str] = None,
        name: Optional[str] = None,
        limit: int = 20,
        offset: int = 0
    ) -> ModelSearchResults:
        """
        Search for models matching the given criteria.

        Uses the registry's /search/models endpoint for optimized querying.

        Args:
            provider: Filter by provider (e.g., "openai", "anthropic")
            capabilities: Filter by required capabilities (all must match)
            min_context_window: Minimum context window size
            max_context_window: Maximum context window size
            group_id: Filter by group ID
            name: Filter by model name
            limit: Maximum number of results
            offset: Number of results to skip

        Returns:
            ModelSearchResults containing matching models

        Example:
            >>> results = await registry.search(
            ...     provider="anthropic",
            ...     capabilities=["vision"],
            ...     min_context_window=100000
            ... )
            >>> for model in results.models:
            ...     print(f"{model.model_id}: {model.context_window} tokens")
        """
        import httpx

        url = f"{self.api_url}/search/models"

        params = {
            "offset": offset,
            "limit": limit,
        }

        if provider:
            params["provider"] = provider
        if capabilities:
            params["capability"] = capabilities
        if min_context_window is not None:
            params["minContextWindow"] = min_context_window
        if max_context_window is not None:
            params["maxContextWindow"] = max_context_window
        if group_id:
            params["groupId"] = group_id
        if name:
            params["name"] = name

        async with httpx.AsyncClient() as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            result = response.json()

            models = []
            for model_data in result.get("models", []):
                models.append(ModelSchema(
                    model_id=model_data.get("modelId", ""),
                    provider=model_data.get("provider", ""),
                    version=model_data.get("version", ""),
                    context_window=model_data.get("contextWindow"),
                    capabilities=model_data.get("capabilities", []),
                    group_id=model_data.get("groupId", "default"),
                    artifact_id=model_data.get("artifactId", ""),
                ))

            return ModelSearchResults(
                models=models,
                count=result.get("count", len(models)),
                offset=offset,
                limit=limit,
            )

    async def list_models(
        self,
        group_id: Optional[str] = None,
        limit: int = 20,
        offset: int = 0
    ) -> List[Dict[str, Any]]:
        """
        List available model schemas.

        Args:
            group_id: Filter by group ID (defaults to instance default)
            limit: Maximum number of results
            offset: Number of results to skip

        Returns:
            List of artifact metadata dictionaries
        """
        client = self._get_client()

        # Use the search endpoint with artifactType filter
        results = await client.search.artifacts.get()

        # Filter for MODEL_SCHEMA type
        models = []
        if results and results.artifacts:
            for artifact in results.artifacts:
                if artifact.artifact_type == "MODEL_SCHEMA":
                    models.append({
                        "groupId": artifact.group_id,
                        "artifactId": artifact.artifact_id,
                        "name": artifact.name,
                        "description": artifact.description,
                        "createdOn": artifact.created_on,
                        "modifiedOn": artifact.modified_on,
                    })

        # Apply group filter
        group = group_id or self.group_id
        if group:
            models = [m for m in models if m.get("groupId") == group]

        # Apply pagination
        return models[offset:offset + limit]

    async def compare_models(
        self,
        model_ids: List[str],
        group_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Compare multiple models side by side.

        Args:
            model_ids: List of artifact IDs to compare
            group_id: Group ID (defaults to instance default)

        Returns:
            Comparison dictionary with capabilities, context windows, and pricing
        """
        group = group_id or self.group_id
        models = []

        for model_id in model_ids:
            model = await self.get_model(model_id, group_id=group)
            models.append(model)

        # Build comparison
        all_capabilities = set()
        for model in models:
            all_capabilities.update(model.capabilities)

        comparison = {
            "models": [m.model_id for m in models],
            "providers": {m.model_id: m.provider for m in models},
            "contextWindows": {m.model_id: m.context_window for m in models},
            "capabilities": {},
            "pricing": {},
        }

        for cap in sorted(all_capabilities):
            comparison["capabilities"][cap] = {
                m.model_id: m.has_capability(cap) for m in models
            }

        for model in models:
            if model.pricing:
                comparison["pricing"][model.model_id] = {
                    "input": model.pricing.input_cost,
                    "output": model.pricing.output_cost,
                    "currency": model.pricing.currency,
                }

        return comparison

    async def find_cheapest(
        self,
        capabilities: Optional[List[str]] = None,
        min_context_window: Optional[int] = None,
        group_id: Optional[str] = None
    ) -> Optional[ModelSchema]:
        """
        Find the cheapest model matching the given criteria.

        Args:
            capabilities: Required capabilities
            min_context_window: Minimum context window size
            group_id: Group ID

        Returns:
            The cheapest matching model, or None if no models match
        """
        results = await self.search(
            capabilities=capabilities,
            min_context_window=min_context_window,
            group_id=group_id,
            limit=100
        )

        cheapest = None
        lowest_cost = float('inf')

        for model in results.models:
            if model.pricing:
                # Use average of input and output as cost metric
                avg_cost = (model.pricing.input_cost + model.pricing.output_cost) / 2
                if avg_cost < lowest_cost:
                    lowest_cost = avg_cost
                    cheapest = model

        return cheapest
