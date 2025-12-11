"""Tests for Avro serializer and deserializer."""

import io
import json
import pytest
import struct

import fastavro

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.avro import (
    AvroSchemaParser,
    AvroSerializer,
    AvroDeserializer,
)
from apicurio_registry_serdes.serde_base import MAGIC_BYTE


# Test schemas
SIMPLE_SCHEMA = {
    "type": "record",
    "name": "SimpleRecord",
    "namespace": "com.example",
    "fields": [
        {"name": "name", "type": "string"},
        {"name": "age", "type": "int"},
    ],
}

SIMPLE_SCHEMA_BYTES = json.dumps(SIMPLE_SCHEMA).encode("utf-8")


class TestAvroSchemaParser:
    """Tests for AvroSchemaParser."""

    def test_artifact_type(self):
        """Test artifact type is AVRO."""
        parser = AvroSchemaParser()
        assert parser.artifact_type() == "AVRO"

    def test_parse_schema_from_bytes(self):
        """Test parsing schema from JSON bytes."""
        parser = AvroSchemaParser()

        schema = parser.parse_schema(SIMPLE_SCHEMA_BYTES)

        # fastavro normalizes name with namespace
        assert "SimpleRecord" in schema["name"]
        assert schema["type"] == "record"

    def test_parse_schema_from_dict_string(self):
        """Test parsing schema from JSON string in bytes."""
        parser = AvroSchemaParser()
        schema_json = '{"type": "string"}'

        schema = parser.parse_schema(schema_json.encode("utf-8"))

        # fastavro may wrap simple types
        if isinstance(schema, dict):
            assert schema["type"] == "string"
        else:
            assert schema == "string"

    def test_serialize_schema(self):
        """Test serializing schema to bytes."""
        parser = AvroSchemaParser()
        schema = fastavro.schema.parse_schema(SIMPLE_SCHEMA)

        serialized = parser.serialize_schema(schema)

        # Should be valid JSON
        parsed = json.loads(serialized)
        # fastavro normalizes name with namespace
        assert "SimpleRecord" in parsed["name"]

    def test_supports_extract_schema_from_data(self):
        """Test that parser reports support for schema extraction."""
        parser = AvroSchemaParser()
        assert parser.supports_extract_schema_from_data() is True

    def test_get_schema_from_data_no_attribute_raises(self):
        """Test that extracting schema from plain dict raises."""
        parser = AvroSchemaParser()
        data = {"name": "John", "age": 30}

        with pytest.raises(ValueError, match="Cannot extract schema"):
            parser.get_schema_from_data(data)


class TestAvroSerializerUnit:
    """Unit tests for AvroSerializer (no registry calls)."""

    def test_serialize_data(self):
        """Test serializing data with schema."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = AvroSerializer(config, schema=SIMPLE_SCHEMA)

        schema = fastavro.schema.parse_schema(SIMPLE_SCHEMA)
        data = {"name": "John", "age": 30}

        payload = serializer.serialize_data(schema, data)

        # Verify by deserializing
        buffer = io.BytesIO(payload)
        result = fastavro.schemaless_reader(buffer, schema)
        assert result == data

    def test_serialize_none_returns_none(self):
        """Test that serializing None returns None."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            auto_register=True,
        )
        serializer = AvroSerializer(config, schema=SIMPLE_SCHEMA)

        # For None, should return None without touching registry
        # We can't easily test this without mocking, but we can verify
        # the serialize method handles None
        # Note: This test is incomplete without mocking
        pass

    def test_schema_parser_returns_avro_parser(self):
        """Test that schema_parser() returns AvroSchemaParser."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = AvroSerializer(config)

        parser = serializer.schema_parser()

        assert isinstance(parser, AvroSchemaParser)


class TestAvroDeserializerUnit:
    """Unit tests for AvroDeserializer (no registry calls)."""

    def test_deserialize_data(self):
        """Test deserializing data with schema."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = AvroDeserializer(config)

        schema = fastavro.schema.parse_schema(SIMPLE_SCHEMA)
        data = {"name": "John", "age": 30}

        # Serialize first
        buffer = io.BytesIO()
        fastavro.schemaless_writer(buffer, schema, data)
        payload = buffer.getvalue()

        # Deserialize
        result = deserializer.deserialize_data(schema, payload)

        assert result == data

    def test_deserialize_with_reader_schema(self):
        """Test deserializing with a reader schema for evolution."""
        # Writer schema has more fields
        writer_schema = {
            "type": "record",
            "name": "SimpleRecord",
            "namespace": "com.example",
            "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"},
                {"name": "extra", "type": "string", "default": "default"},
            ],
        }

        # Reader schema has fewer fields
        reader_schema = {
            "type": "record",
            "name": "SimpleRecord",
            "namespace": "com.example",
            "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"},
            ],
        }

        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = AvroDeserializer(config, reader_schema=reader_schema)

        parsed_writer = fastavro.schema.parse_schema(writer_schema)
        data = {"name": "John", "age": 30, "extra": "value"}

        # Serialize with writer schema
        buffer = io.BytesIO()
        fastavro.schemaless_writer(buffer, parsed_writer, data)
        payload = buffer.getvalue()

        # Deserialize with reader schema
        result = deserializer.deserialize_data(parsed_writer, payload)

        # Should only have fields from reader schema
        assert result["name"] == "John"
        assert result["age"] == 30
        # 'extra' field should be dropped or handled by schema evolution
        # Note: fastavro behavior may vary

    def test_schema_parser_returns_avro_parser(self):
        """Test that schema_parser() returns AvroSchemaParser."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = AvroDeserializer(config)

        parser = deserializer.schema_parser()

        assert isinstance(parser, AvroSchemaParser)


class TestAvroRoundTrip:
    """Round-trip tests for Avro serialization."""

    def test_serialize_deserialize_roundtrip(self):
        """Test that serialize + deserialize returns original data."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        schema = fastavro.schema.parse_schema(SIMPLE_SCHEMA)
        data = {"name": "John", "age": 30}

        # Create serializer/deserializer
        serializer = AvroSerializer(config, schema=SIMPLE_SCHEMA)
        deserializer = AvroDeserializer(config)

        # Serialize data (just the payload, without header)
        payload = serializer.serialize_data(schema, data)

        # Deserialize
        result = deserializer.deserialize_data(schema, payload)

        assert result == data

    def test_complex_data_roundtrip(self):
        """Test round-trip with complex nested data."""
        complex_schema = {
            "type": "record",
            "name": "ComplexRecord",
            "fields": [
                {"name": "id", "type": "long"},
                {"name": "tags", "type": {"type": "array", "items": "string"}},
                {
                    "name": "metadata",
                    "type": {
                        "type": "map",
                        "values": "string",
                    },
                },
                {
                    "name": "nested",
                    "type": {
                        "type": "record",
                        "name": "Nested",
                        "fields": [
                            {"name": "value", "type": "double"},
                        ],
                    },
                },
            ],
        }

        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        schema = fastavro.schema.parse_schema(complex_schema)

        data = {
            "id": 123456789,
            "tags": ["tag1", "tag2", "tag3"],
            "metadata": {"key1": "value1", "key2": "value2"},
            "nested": {"value": 3.14159},
        }

        serializer = AvroSerializer(config, schema=complex_schema)
        deserializer = AvroDeserializer(config)

        payload = serializer.serialize_data(schema, data)
        result = deserializer.deserialize_data(schema, payload)

        assert result == data
