"""Schema resolver for looking up and registering schemas with the registry."""

import logging
import time
from typing import TypeVar, Generic, Any, Dict, Optional, Callable
from threading import Lock

import httpx
from cachetools import TTLCache

from apicurio_registry_serdes.config import SerdeConfig, IdOption
from apicurio_registry_serdes.serde_base import (
    ArtifactReference,
    SchemaLookupResult,
)
from apicurio_registry_serdes.schema_parser import SchemaParser

logger = logging.getLogger(__name__)

S = TypeVar("S")  # Schema type
T = TypeVar("T")  # Data type


class RegistryClientError(Exception):
    """Exception raised when a registry operation fails."""

    def __init__(self, message: str, status_code: Optional[int] = None):
        super().__init__(message)
        self.status_code = status_code


class SchemaResolver(Generic[S, T]):
    """
    Resolves schemas from the Apicurio Registry.

    This class handles:
    - Looking up schemas by ID (content ID or global ID)
    - Looking up schemas by artifact coordinates (group/artifact/version)
    - Auto-registering schemas when enabled
    - Caching schemas to reduce registry calls
    """

    def __init__(self, config: SerdeConfig, schema_parser: SchemaParser[S, T]):
        self.config = config
        self.schema_parser = schema_parser

        # HTTP client for registry calls
        self._client = self._create_client()

        # Caches with TTL based on config
        cache_ttl = config.check_period_ms / 1000.0
        max_cache_size = 1000

        self._cache_by_content_id: TTLCache[int, SchemaLookupResult[S]] = TTLCache(
            maxsize=max_cache_size, ttl=cache_ttl
        )
        self._cache_by_global_id: TTLCache[int, SchemaLookupResult[S]] = TTLCache(
            maxsize=max_cache_size, ttl=cache_ttl
        )
        self._cache_by_gav: TTLCache[str, SchemaLookupResult[S]] = TTLCache(
            maxsize=max_cache_size, ttl=cache_ttl
        )
        self._cache_lock = Lock()

    def _create_client(self) -> httpx.Client:
        """Create the HTTP client for registry communication."""
        headers = dict(self.config.headers)
        headers["Accept"] = "application/json"

        # Set up authentication
        auth = None
        if self.config.auth_username and self.config.auth_password:
            auth = (self.config.auth_username, self.config.auth_password)
        elif self.config.auth_token:
            headers["Authorization"] = f"Bearer {self.config.auth_token}"

        return httpx.Client(
            base_url=self.config.registry_url,
            headers=headers,
            auth=auth,
            timeout=30.0,
        )

    def _retry_request(
        self,
        method: str,
        url: str,
        **kwargs: Any,
    ) -> httpx.Response:
        """Execute a request with retry logic."""
        last_error: Optional[Exception] = None

        for attempt in range(self.config.retry_count + 1):
            try:
                response = self._client.request(method, url, **kwargs)
                response.raise_for_status()
                return response
            except httpx.HTTPStatusError as e:
                # Don't retry client errors (4xx) except 429
                if 400 <= e.response.status_code < 500 and e.response.status_code != 429:
                    raise RegistryClientError(
                        f"Registry request failed: {e}",
                        status_code=e.response.status_code,
                    )
                last_error = e
            except httpx.RequestError as e:
                last_error = e

            if attempt < self.config.retry_count:
                time.sleep(self.config.retry_backoff_ms / 1000.0)
                logger.debug(f"Retrying request (attempt {attempt + 2})")

        raise RegistryClientError(f"Registry request failed after retries: {last_error}")

    def resolve_schema(self, topic: str, data: T) -> SchemaLookupResult[S]:
        """
        Resolve a schema for the given data and topic.

        This method:
        1. Extracts the schema from the data (if supported)
        2. Determines the artifact coordinates
        3. Registers the schema if auto_register is enabled
        4. Returns the schema lookup result with IDs

        Args:
            topic: The Kafka topic name.
            data: The data being serialized.

        Returns:
            The schema lookup result.
        """
        # Extract schema from data
        if not self.schema_parser.supports_extract_schema_from_data():
            raise ValueError(
                f"Schema parser {type(self.schema_parser).__name__} does not support "
                "extracting schema from data. Configure explicit artifact coordinates."
            )

        schema = self.schema_parser.get_schema_from_data(data)
        raw_schema = self.schema_parser.serialize_schema(schema)

        # Determine artifact coordinates
        group_id = self.config.artifact_group_id or "default"
        artifact_id = self.config.artifact_id or f"{topic}-value"

        if self.config.auto_register:
            return self._register_schema(
                group_id=group_id,
                artifact_id=artifact_id,
                schema=schema,
                raw_schema=raw_schema,
            )
        elif self.config.find_latest:
            return self._resolve_by_coordinates(
                group_id=group_id,
                artifact_id=artifact_id,
                version=self.config.artifact_version,
            )
        else:
            # Try to find by content
            return self._find_by_content(
                group_id=group_id,
                artifact_id=artifact_id,
                schema=schema,
                raw_schema=raw_schema,
            )

    def resolve_schema_by_reference(
        self, reference: ArtifactReference
    ) -> SchemaLookupResult[S]:
        """
        Resolve a schema by its artifact reference.

        Args:
            reference: The artifact reference (with content_id, global_id, or GAV).

        Returns:
            The schema lookup result.
        """
        if reference.content_id is not None:
            return self._resolve_by_content_id(reference.content_id)
        elif reference.global_id is not None:
            return self._resolve_by_global_id(reference.global_id)
        elif reference.group_id and reference.artifact_id:
            return self._resolve_by_coordinates(
                reference.group_id,
                reference.artifact_id,
                reference.version,
            )
        else:
            raise ValueError(f"Invalid artifact reference: {reference}")

    def _resolve_by_content_id(self, content_id: int) -> SchemaLookupResult[S]:
        """Resolve schema by content ID."""
        with self._cache_lock:
            if content_id in self._cache_by_content_id:
                return self._cache_by_content_id[content_id]

        # Fetch from registry
        response = self._retry_request("GET", f"/ids/contentIds/{content_id}")
        raw_schema = response.content

        # Parse schema
        parsed = self.schema_parser.parse_schema(raw_schema)

        result = SchemaLookupResult(
            parsed_schema=parsed,
            raw_schema=raw_schema,
            content_id=content_id,
        )

        with self._cache_lock:
            self._cache_by_content_id[content_id] = result

        return result

    def _resolve_by_global_id(self, global_id: int) -> SchemaLookupResult[S]:
        """Resolve schema by global ID."""
        with self._cache_lock:
            if global_id in self._cache_by_global_id:
                return self._cache_by_global_id[global_id]

        # Fetch from registry
        dereference = "true" if self.config.dereference_schema else "false"
        response = self._retry_request(
            "GET",
            f"/ids/globalIds/{global_id}",
            params={"dereference": dereference},
        )
        raw_schema = response.content

        # Parse schema
        parsed = self.schema_parser.parse_schema(raw_schema)

        result = SchemaLookupResult(
            parsed_schema=parsed,
            raw_schema=raw_schema,
            global_id=global_id,
        )

        with self._cache_lock:
            self._cache_by_global_id[global_id] = result

        return result

    def _resolve_by_coordinates(
        self,
        group_id: str,
        artifact_id: str,
        version: Optional[str] = None,
    ) -> SchemaLookupResult[S]:
        """Resolve schema by group/artifact/version coordinates."""
        cache_key = f"{group_id}/{artifact_id}/{version or 'latest'}"

        with self._cache_lock:
            if cache_key in self._cache_by_gav:
                return self._cache_by_gav[cache_key]

        # Determine version expression
        if version:
            version_expr = version
        else:
            version_expr = "branch=latest"

        # Fetch content
        dereference = "true" if self.config.dereference_schema else "false"
        content_response = self._retry_request(
            "GET",
            f"/groups/{group_id}/artifacts/{artifact_id}/versions/{version_expr}/content",
            params={"dereference": dereference},
        )
        raw_schema = content_response.content

        # Fetch metadata to get IDs
        meta_response = self._retry_request(
            "GET",
            f"/groups/{group_id}/artifacts/{artifact_id}/versions/{version_expr}",
        )
        metadata = meta_response.json()

        # Parse schema
        parsed = self.schema_parser.parse_schema(raw_schema)

        result = SchemaLookupResult(
            parsed_schema=parsed,
            raw_schema=raw_schema,
            content_id=metadata.get("contentId"),
            global_id=metadata.get("globalId"),
            group_id=group_id,
            artifact_id=artifact_id,
            version=metadata.get("version"),
        )

        with self._cache_lock:
            self._cache_by_gav[cache_key] = result
            if result.content_id:
                self._cache_by_content_id[result.content_id] = result
            if result.global_id:
                self._cache_by_global_id[result.global_id] = result

        return result

    def _register_schema(
        self,
        group_id: str,
        artifact_id: str,
        schema: S,
        raw_schema: bytes,
    ) -> SchemaLookupResult[S]:
        """Register a schema with the registry."""
        # Build the request body
        body = {
            "artifactId": artifact_id,
            "artifactType": self.schema_parser.artifact_type(),
            "firstVersion": {
                "content": {
                    "content": raw_schema.decode("utf-8"),
                    "contentType": "application/json",
                }
            },
        }

        # Set if-exists behavior
        params = {"ifExists": self.config.auto_register_if_exists}

        try:
            response = self._retry_request(
                "POST",
                f"/groups/{group_id}/artifacts",
                json=body,
                params=params,
            )
            data = response.json()

            # Extract version info from response
            version_info = data.get("version", {})

            result = SchemaLookupResult(
                parsed_schema=schema,
                raw_schema=raw_schema,
                content_id=version_info.get("contentId"),
                global_id=version_info.get("globalId"),
                group_id=group_id,
                artifact_id=data.get("artifact", {}).get("artifactId", artifact_id),
                version=version_info.get("version"),
            )

            # Cache the result
            with self._cache_lock:
                if result.content_id:
                    self._cache_by_content_id[result.content_id] = result
                if result.global_id:
                    self._cache_by_global_id[result.global_id] = result

            return result

        except RegistryClientError as e:
            # If it already exists and we got 409, try to find by content
            if e.status_code == 409:
                return self._find_by_content(group_id, artifact_id, schema, raw_schema)
            raise

    def _find_by_content(
        self,
        group_id: str,
        artifact_id: str,
        schema: S,
        raw_schema: bytes,
    ) -> SchemaLookupResult[S]:
        """Find a schema version by its content."""
        body = {
            "content": raw_schema.decode("utf-8"),
            "artifactType": self.schema_parser.artifact_type(),
        }

        response = self._retry_request(
            "POST",
            f"/groups/{group_id}/artifacts/{artifact_id}/versions",
            json=body,
            params={"canonical": "true", "ifExists": "FIND_OR_CREATE_VERSION"},
        )
        data = response.json()

        version_info = data.get("version", data)

        result = SchemaLookupResult(
            parsed_schema=schema,
            raw_schema=raw_schema,
            content_id=version_info.get("contentId"),
            global_id=version_info.get("globalId"),
            group_id=group_id,
            artifact_id=artifact_id,
            version=version_info.get("version"),
        )

        # Cache the result
        with self._cache_lock:
            if result.content_id:
                self._cache_by_content_id[result.content_id] = result
            if result.global_id:
                self._cache_by_global_id[result.global_id] = result

        return result

    def reset(self) -> None:
        """Clear all cached schemas."""
        with self._cache_lock:
            self._cache_by_content_id.clear()
            self._cache_by_global_id.clear()
            self._cache_by_gav.clear()

    def close(self) -> None:
        """Close the HTTP client and release resources."""
        self._client.close()
