"""Avro serializer and deserializer implementations."""

import io
from typing import Dict, Optional, Any, Union

import fastavro

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.schema_parser import SchemaParser
from apicurio_registry_serdes.serde_base import BaseSerializer, BaseDeserializer


# Type alias for Avro schema (fastavro uses dict representation)
AvroSchema = Dict[str, Any]


class AvroSchemaParser(SchemaParser[AvroSchema, Dict[str, Any]]):
    """
    Schema parser implementation for Avro schemas.

    Uses fastavro for schema parsing and validation.
    """

    def artifact_type(self) -> str:
        """Return the artifact type for Avro schemas."""
        return "AVRO"

    def parse_schema(
        self,
        raw_schema: bytes,
        references: Optional[Dict[str, AvroSchema]] = None,
    ) -> AvroSchema:
        """
        Parse a raw Avro schema into a parsed schema object.

        Args:
            raw_schema: The raw schema bytes (JSON format).
            references: Optional dict of referenced schemas by name.

        Returns:
            The parsed Avro schema as a dict.
        """
        import json

        schema_dict = json.loads(raw_schema)
        # Validate and normalize the schema using fastavro
        parsed = fastavro.schema.parse_schema(schema_dict)
        return parsed

    def get_schema_from_data(self, data: Dict[str, Any]) -> AvroSchema:
        """
        Extract the schema from data.

        For Avro, if the data has a __schema__ attribute (like generated
        classes), we use that. Otherwise, we infer from the data structure.

        Args:
            data: The data object.

        Returns:
            The Avro schema.

        Raises:
            ValueError: If schema cannot be extracted from data.
        """
        # Check for schema attribute (common in generated classes)
        if hasattr(data, "__schema__"):
            return data.__schema__

        # Check for _schema attribute
        if hasattr(data, "_schema"):
            return data._schema

        # Check for SCHEMA attribute (used by some Avro libraries)
        if hasattr(data, "SCHEMA"):
            schema = data.SCHEMA
            if isinstance(schema, str):
                import json
                return fastavro.schema.parse_schema(json.loads(schema))
            return schema

        raise ValueError(
            "Cannot extract schema from data. "
            "Data must have a __schema__, _schema, or SCHEMA attribute, "
            "or use explicit schema configuration."
        )

    def serialize_schema(self, schema: AvroSchema) -> bytes:
        """
        Serialize an Avro schema to bytes.

        Args:
            schema: The parsed Avro schema.

        Returns:
            The schema as JSON bytes.
        """
        import json

        # Handle fastavro parsed schema (which may have been normalized)
        if isinstance(schema, dict):
            return json.dumps(schema, separators=(",", ":")).encode("utf-8")
        else:
            return json.dumps(schema, separators=(",", ":")).encode("utf-8")

    def supports_extract_schema_from_data(self) -> bool:
        """Avro supports extracting schema from generated classes."""
        return True


class AvroSerializer(BaseSerializer[AvroSchema, Dict[str, Any]]):
    """
    Avro serializer that integrates with Apicurio Registry.

    This serializer:
    1. Resolves the Avro schema from the registry (or registers it)
    2. Serializes the data using fastavro
    3. Prepends the magic byte and schema ID
    """

    def __init__(
        self,
        config: SerdeConfig,
        schema: Optional[Union[AvroSchema, str, bytes]] = None,
    ):
        """
        Initialize the Avro serializer.

        Args:
            config: The serde configuration.
            schema: Optional explicit schema to use (dict, JSON string, or bytes).
        """
        super().__init__(config)
        self._explicit_schema: Optional[AvroSchema] = None
        self._parser = AvroSchemaParser()

        if schema is not None:
            self._explicit_schema = self._parse_explicit_schema(schema)

    def _parse_explicit_schema(
        self, schema: Union[AvroSchema, str, bytes]
    ) -> AvroSchema:
        """Parse an explicit schema into the internal format."""
        if isinstance(schema, dict):
            return fastavro.schema.parse_schema(schema)
        elif isinstance(schema, bytes):
            return self._parser.parse_schema(schema)
        else:
            import json
            return fastavro.schema.parse_schema(json.loads(schema))

    def schema_parser(self) -> SchemaParser[AvroSchema, Dict[str, Any]]:
        """Return the Avro schema parser."""
        return self._parser

    def serialize_data(self, schema: AvroSchema, data: Dict[str, Any]) -> bytes:
        """
        Serialize data using the Avro schema.

        Args:
            schema: The parsed Avro schema.
            data: The data to serialize.

        Returns:
            The serialized bytes.
        """
        buffer = io.BytesIO()
        fastavro.schemaless_writer(buffer, schema, data)
        return buffer.getvalue()

    def serialize(
        self,
        topic: str,
        data: Union[Dict[str, Any], Any],
        schema: Optional[Union[AvroSchema, str, bytes]] = None,
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

        # If explicit schema provided, use a temporary schema resolver
        if schema is not None or self._explicit_schema is not None:
            use_schema = (
                self._parse_explicit_schema(schema)
                if schema is not None
                else self._explicit_schema
            )
            return self._serialize_with_explicit_schema(topic, data, use_schema)

        # Use default flow (extracts schema from data)
        return super().serialize(topic, data)

    def _serialize_with_explicit_schema(
        self,
        topic: str,
        data: Dict[str, Any],
        schema: AvroSchema,
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
            # Use the fetched schema
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


class AvroDeserializer(BaseDeserializer[AvroSchema, Dict[str, Any]]):
    """
    Avro deserializer that integrates with Apicurio Registry.

    This deserializer:
    1. Reads the schema ID from the message header
    2. Looks up the schema from the registry
    3. Deserializes the data using fastavro
    """

    def __init__(
        self,
        config: SerdeConfig,
        reader_schema: Optional[Union[AvroSchema, str, bytes]] = None,
    ):
        """
        Initialize the Avro deserializer.

        Args:
            config: The serde configuration.
            reader_schema: Optional reader schema for schema evolution.
        """
        super().__init__(config)
        self._parser = AvroSchemaParser()
        self._reader_schema: Optional[AvroSchema] = None

        if reader_schema is not None:
            if isinstance(reader_schema, dict):
                self._reader_schema = fastavro.schema.parse_schema(reader_schema)
            elif isinstance(reader_schema, bytes):
                self._reader_schema = self._parser.parse_schema(reader_schema)
            else:
                import json
                self._reader_schema = fastavro.schema.parse_schema(
                    json.loads(reader_schema)
                )

    def schema_parser(self) -> SchemaParser[AvroSchema, Dict[str, Any]]:
        """Return the Avro schema parser."""
        return self._parser

    def deserialize_data(self, schema: AvroSchema, data: bytes) -> Dict[str, Any]:
        """
        Deserialize data using the Avro schema.

        Args:
            schema: The parsed Avro schema (writer schema).
            data: The data bytes (without header).

        Returns:
            The deserialized data as a dictionary.
        """
        buffer = io.BytesIO(data)

        if self._reader_schema is not None:
            # Use schema evolution with reader/writer schemas
            return fastavro.schemaless_reader(
                buffer,
                schema,
                self._reader_schema,
            )
        else:
            return fastavro.schemaless_reader(buffer, schema)
