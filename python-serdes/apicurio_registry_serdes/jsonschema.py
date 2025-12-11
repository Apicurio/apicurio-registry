"""JSON Schema serializer and deserializer implementations."""

import json
from typing import Dict, Optional, Any, Union

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.schema_parser import SchemaParser
from apicurio_registry_serdes.serde_base import BaseSerializer, BaseDeserializer


# Type alias for JSON Schema (dict representation)
JsonSchema = Dict[str, Any]


class JsonSchemaParser(SchemaParser[JsonSchema, Dict[str, Any]]):
    """
    Schema parser implementation for JSON Schema.

    Parses and validates JSON Schema documents.
    """

    def __init__(self, validate: bool = True):
        """
        Initialize the JSON Schema parser.

        Args:
            validate: Whether to validate data against the schema during
                serialization/deserialization. Defaults to True.
        """
        self._validate = validate
        self._validator_class: Optional[Any] = None

    @property
    def validator_class(self) -> Any:
        """Lazy load the JSON Schema validator class."""
        if self._validator_class is None:
            try:
                from jsonschema import Draft7Validator
                self._validator_class = Draft7Validator
            except ImportError:
                # jsonschema package not installed, validation will be disabled
                self._validator_class = None
        return self._validator_class

    def artifact_type(self) -> str:
        """Return the artifact type for JSON Schema."""
        return "JSON"

    def parse_schema(
        self,
        raw_schema: bytes,
        references: Optional[Dict[str, JsonSchema]] = None,
    ) -> JsonSchema:
        """
        Parse a raw JSON Schema into a parsed schema object.

        Args:
            raw_schema: The raw schema bytes (JSON format).
            references: Optional dict of referenced schemas by name.

        Returns:
            The parsed JSON Schema as a dict.
        """
        schema_dict = json.loads(raw_schema)

        # Optionally validate the schema structure
        if self._validate and self.validator_class is not None:
            # Check if the schema is valid
            self.validator_class.check_schema(schema_dict)

        return schema_dict

    def get_schema_from_data(self, data: Dict[str, Any]) -> JsonSchema:
        """
        Extract the schema from data.

        For JSON Schema, the data typically doesn't contain schema information,
        so this method is not generally supported without explicit configuration.

        Args:
            data: The data object.

        Returns:
            The JSON Schema.

        Raises:
            ValueError: JSON Schema typically cannot be extracted from data.
        """
        # Check for schema attribute (if data is a wrapper object)
        if hasattr(data, "__json_schema__"):
            return data.__json_schema__

        if hasattr(data, "_schema"):
            return data._schema

        raise ValueError(
            "Cannot extract JSON Schema from data. "
            "JSON Schema serialization requires explicit schema configuration. "
            "Use artifact_id/group_id in config or provide schema explicitly."
        )

    def serialize_schema(self, schema: JsonSchema) -> bytes:
        """
        Serialize a JSON Schema to bytes.

        Args:
            schema: The parsed JSON Schema.

        Returns:
            The schema as JSON bytes.
        """
        return json.dumps(schema, separators=(",", ":")).encode("utf-8")

    def supports_extract_schema_from_data(self) -> bool:
        """JSON Schema typically doesn't support extracting schema from data."""
        return False


class JsonSchemaSerializer(BaseSerializer[JsonSchema, Dict[str, Any]]):
    """
    JSON Schema serializer that integrates with Apicurio Registry.

    This serializer:
    1. Resolves the JSON Schema from the registry (or registers it)
    2. Optionally validates the data against the schema
    3. Serializes the data as JSON
    4. Prepends the magic byte and schema ID
    """

    def __init__(
        self,
        config: SerdeConfig,
        schema: Optional[Union[JsonSchema, str, bytes]] = None,
        validate: bool = True,
    ):
        """
        Initialize the JSON Schema serializer.

        Args:
            config: The serde configuration.
            schema: The JSON Schema to use (required for JSON Schema).
            validate: Whether to validate data against the schema.
        """
        super().__init__(config)
        self._validate = validate and config.validation_enabled
        self._parser = JsonSchemaParser(validate=self._validate)
        self._explicit_schema: Optional[JsonSchema] = None
        self._validator: Optional[Any] = None

        if schema is not None:
            self._explicit_schema = self._parse_explicit_schema(schema)
            self._setup_validator()

    def _parse_explicit_schema(
        self, schema: Union[JsonSchema, str, bytes]
    ) -> JsonSchema:
        """Parse an explicit schema into the internal format."""
        if isinstance(schema, dict):
            return schema
        elif isinstance(schema, bytes):
            return self._parser.parse_schema(schema)
        else:
            return json.loads(schema)

    def _setup_validator(self) -> None:
        """Set up the JSON Schema validator if validation is enabled."""
        if self._validate and self._explicit_schema is not None:
            try:
                from jsonschema import Draft7Validator
                self._validator = Draft7Validator(self._explicit_schema)
            except ImportError:
                self._validator = None

    def schema_parser(self) -> SchemaParser[JsonSchema, Dict[str, Any]]:
        """Return the JSON Schema parser."""
        return self._parser

    def serialize_data(self, schema: JsonSchema, data: Dict[str, Any]) -> bytes:
        """
        Serialize data using the JSON Schema.

        Args:
            schema: The parsed JSON Schema.
            data: The data to serialize.

        Returns:
            The serialized bytes.
        """
        # Optionally validate the data
        if self._validate:
            self._validate_data(schema, data)

        return json.dumps(data, separators=(",", ":")).encode("utf-8")

    def _validate_data(self, schema: JsonSchema, data: Dict[str, Any]) -> None:
        """Validate data against the schema."""
        try:
            from jsonschema import validate, ValidationError

            validate(instance=data, schema=schema)
        except ImportError:
            # jsonschema not installed, skip validation
            pass
        except Exception as e:
            raise ValueError(f"Data validation failed: {e}")

    def serialize(
        self,
        topic: str,
        data: Dict[str, Any],
        schema: Optional[Union[JsonSchema, str, bytes]] = None,
    ) -> Optional[bytes]:
        """
        Serialize data for the given topic.

        Args:
            topic: The Kafka topic name.
            data: The data to serialize.
            schema: Optional explicit schema to use for this serialization.

        Returns:
            The serialized bytes including header, or None if data is None.
        """
        if data is None:
            return None

        use_schema = None
        if schema is not None:
            use_schema = self._parse_explicit_schema(schema)
        elif self._explicit_schema is not None:
            use_schema = self._explicit_schema

        if use_schema is None:
            raise ValueError(
                "JSON Schema serialization requires an explicit schema. "
                "Provide schema in constructor or serialize() call."
            )

        return self._serialize_with_explicit_schema(topic, data, use_schema)

    def _serialize_with_explicit_schema(
        self,
        topic: str,
        data: Dict[str, Any],
        schema: JsonSchema,
    ) -> bytes:
        """Serialize with an explicitly provided schema."""
        raw_schema = self._parser.serialize_schema(schema)

        # Determine artifact coordinates
        group_id = self.config.artifact_group_id or "default"
        artifact_id = self.config.artifact_id or f"{topic}-value"

        # Register or lookup schema
        if self.config.auto_register:
            schema_result = self.schema_resolver._register_schema(
                group_id=group_id,
                artifact_id=artifact_id,
                schema=schema,
                raw_schema=raw_schema,
            )
        elif self.config.find_latest:
            schema_result = self.schema_resolver._resolve_by_coordinates(
                group_id=group_id,
                artifact_id=artifact_id,
                version=self.config.artifact_version,
            )
            schema = schema_result.parsed_schema
        else:
            schema_result = self.schema_resolver._find_by_content(
                group_id=group_id,
                artifact_id=artifact_id,
                schema=schema,
                raw_schema=raw_schema,
            )

        # Serialize data
        payload = self.serialize_data(schema, data)

        # Build final message
        header = self.id_handler.write_id(schema_result.to_artifact_reference())
        return header + payload


class JsonSchemaDeserializer(BaseDeserializer[JsonSchema, Dict[str, Any]]):
    """
    JSON Schema deserializer that integrates with Apicurio Registry.

    This deserializer:
    1. Reads the schema ID from the message header
    2. Looks up the schema from the registry
    3. Optionally validates the data against the schema
    4. Deserializes the JSON data
    """

    def __init__(
        self,
        config: SerdeConfig,
        validate: bool = True,
    ):
        """
        Initialize the JSON Schema deserializer.

        Args:
            config: The serde configuration.
            validate: Whether to validate data against the schema.
        """
        super().__init__(config)
        self._validate = validate and config.validation_enabled
        self._parser = JsonSchemaParser(validate=self._validate)

    def schema_parser(self) -> SchemaParser[JsonSchema, Dict[str, Any]]:
        """Return the JSON Schema parser."""
        return self._parser

    def deserialize_data(self, schema: JsonSchema, data: bytes) -> Dict[str, Any]:
        """
        Deserialize data using the JSON Schema.

        Args:
            schema: The parsed JSON Schema.
            data: The data bytes (without header).

        Returns:
            The deserialized data as a dictionary.
        """
        result = json.loads(data)

        # Optionally validate the data
        if self._validate:
            self._validate_data(schema, result)

        return result

    def _validate_data(self, schema: JsonSchema, data: Dict[str, Any]) -> None:
        """Validate data against the schema."""
        try:
            from jsonschema import validate, ValidationError

            validate(instance=data, schema=schema)
        except ImportError:
            # jsonschema not installed, skip validation
            pass
        except Exception as e:
            raise ValueError(f"Data validation failed: {e}")
