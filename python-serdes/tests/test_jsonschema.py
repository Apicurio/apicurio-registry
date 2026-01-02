"""Tests for JSON Schema serializer and deserializer."""

import json
import pytest

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.jsonschema import (
    JsonSchemaParser,
    JsonSchemaSerializer,
    JsonSchemaDeserializer,
)


# Test schemas
SIMPLE_SCHEMA = {
    "type": "object",
    "properties": {
        "name": {"type": "string"},
        "age": {"type": "integer"},
    },
    "required": ["name", "age"],
}

SIMPLE_SCHEMA_BYTES = json.dumps(SIMPLE_SCHEMA).encode("utf-8")


class TestJsonSchemaParser:
    """Tests for JsonSchemaParser."""

    def test_artifact_type(self):
        """Test artifact type is JSON."""
        parser = JsonSchemaParser()
        assert parser.artifact_type() == "JSON"

    def test_parse_schema_from_bytes(self):
        """Test parsing schema from JSON bytes."""
        parser = JsonSchemaParser()

        schema = parser.parse_schema(SIMPLE_SCHEMA_BYTES)

        assert schema["type"] == "object"
        assert "properties" in schema

    def test_parse_invalid_schema_raises(self):
        """Test that parsing invalid JSON raises."""
        parser = JsonSchemaParser()

        with pytest.raises(Exception):
            parser.parse_schema(b"not valid json")

    def test_serialize_schema(self):
        """Test serializing schema to bytes."""
        parser = JsonSchemaParser()

        serialized = parser.serialize_schema(SIMPLE_SCHEMA)

        # Should be valid JSON
        parsed = json.loads(serialized)
        assert parsed["type"] == "object"

    def test_supports_extract_schema_from_data(self):
        """Test that parser reports no support for schema extraction."""
        parser = JsonSchemaParser()
        # JSON Schema typically cannot extract schema from data
        assert parser.supports_extract_schema_from_data() is False

    def test_get_schema_from_data_raises(self):
        """Test that extracting schema from plain dict raises."""
        parser = JsonSchemaParser()
        data = {"name": "John", "age": 30}

        with pytest.raises(ValueError, match="Cannot extract JSON Schema"):
            parser.get_schema_from_data(data)


class TestJsonSchemaSerializerUnit:
    """Unit tests for JsonSchemaSerializer (no registry calls)."""

    def test_serialize_data(self):
        """Test serializing data with schema."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,  # Disable for unit test
        )
        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA, validate=False)

        data = {"name": "John", "age": 30}

        payload = serializer.serialize_data(SIMPLE_SCHEMA, data)

        # Verify by deserializing
        result = json.loads(payload)
        assert result == data

    def test_serialize_data_compact_json(self):
        """Test that serialized JSON is compact (no extra whitespace)."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )
        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA, validate=False)

        data = {"name": "John", "age": 30}

        payload = serializer.serialize_data(SIMPLE_SCHEMA, data)

        # Should not contain newlines or extra spaces
        assert b"\n" not in payload
        assert b" " not in payload or payload == b'{"name":"John","age":30}'

    def test_schema_required(self):
        """Test that schema is required for serialization."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = JsonSchemaSerializer(config, validate=False)

        data = {"name": "John", "age": 30}

        with pytest.raises(ValueError, match="requires an explicit schema"):
            serializer.serialize("test-topic", data)

    def test_schema_parser_returns_json_parser(self):
        """Test that schema_parser() returns JsonSchemaParser."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA)

        parser = serializer.schema_parser()

        assert isinstance(parser, JsonSchemaParser)


class TestJsonSchemaDeserializerUnit:
    """Unit tests for JsonSchemaDeserializer (no registry calls)."""

    def test_deserialize_data(self):
        """Test deserializing data with schema."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )
        deserializer = JsonSchemaDeserializer(config, validate=False)

        data = {"name": "John", "age": 30}
        payload = json.dumps(data).encode("utf-8")

        result = deserializer.deserialize_data(SIMPLE_SCHEMA, payload)

        assert result == data

    def test_deserialize_unicode(self):
        """Test deserializing data with Unicode characters."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )
        deserializer = JsonSchemaDeserializer(config, validate=False)

        data = {"name": "日本語", "age": 25}
        payload = json.dumps(data, ensure_ascii=False).encode("utf-8")

        result = deserializer.deserialize_data(SIMPLE_SCHEMA, payload)

        assert result == data

    def test_schema_parser_returns_json_parser(self):
        """Test that schema_parser() returns JsonSchemaParser."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = JsonSchemaDeserializer(config)

        parser = deserializer.schema_parser()

        assert isinstance(parser, JsonSchemaParser)


class TestJsonSchemaRoundTrip:
    """Round-trip tests for JSON Schema serialization."""

    def test_serialize_deserialize_roundtrip(self):
        """Test that serialize + deserialize returns original data."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )
        data = {"name": "John", "age": 30}

        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA, validate=False)
        deserializer = JsonSchemaDeserializer(config, validate=False)

        payload = serializer.serialize_data(SIMPLE_SCHEMA, data)
        result = deserializer.deserialize_data(SIMPLE_SCHEMA, payload)

        assert result == data

    def test_complex_data_roundtrip(self):
        """Test round-trip with complex nested data."""
        complex_schema = {
            "type": "object",
            "properties": {
                "id": {"type": "integer"},
                "tags": {"type": "array", "items": {"type": "string"}},
                "metadata": {
                    "type": "object",
                    "additionalProperties": {"type": "string"},
                },
                "nested": {
                    "type": "object",
                    "properties": {
                        "value": {"type": "number"},
                    },
                },
            },
        }

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )

        data = {
            "id": 123456789,
            "tags": ["tag1", "tag2", "tag3"],
            "metadata": {"key1": "value1", "key2": "value2"},
            "nested": {"value": 3.14159},
        }

        serializer = JsonSchemaSerializer(config, schema=complex_schema, validate=False)
        deserializer = JsonSchemaDeserializer(config, validate=False)

        payload = serializer.serialize_data(complex_schema, data)
        result = deserializer.deserialize_data(complex_schema, payload)

        assert result == data

    def test_null_values_roundtrip(self):
        """Test round-trip with null values."""
        schema = {
            "type": "object",
            "properties": {
                "name": {"type": ["string", "null"]},
                "value": {"type": ["integer", "null"]},
            },
        }

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=False,
        )

        data = {"name": None, "value": 42}

        serializer = JsonSchemaSerializer(config, schema=schema, validate=False)
        deserializer = JsonSchemaDeserializer(config, validate=False)

        payload = serializer.serialize_data(schema, data)
        result = deserializer.deserialize_data(schema, payload)

        assert result == data


class TestJsonSchemaValidation:
    """Tests for JSON Schema validation (requires jsonschema package)."""

    def test_validation_enabled_valid_data(self):
        """Test validation passes for valid data."""
        try:
            import jsonschema
        except ImportError:
            pytest.skip("jsonschema package not installed")

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=True,
        )
        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA, validate=True)

        data = {"name": "John", "age": 30}

        # Should not raise
        payload = serializer.serialize_data(SIMPLE_SCHEMA, data)
        assert payload is not None

    def test_validation_enabled_invalid_data(self):
        """Test validation fails for invalid data."""
        try:
            import jsonschema
        except ImportError:
            pytest.skip("jsonschema package not installed")

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            validation_enabled=True,
        )
        serializer = JsonSchemaSerializer(config, schema=SIMPLE_SCHEMA, validate=True)

        # Invalid: missing required field 'name'
        data = {"age": 30}

        with pytest.raises(ValueError, match="validation failed"):
            serializer.serialize_data(SIMPLE_SCHEMA, data)
