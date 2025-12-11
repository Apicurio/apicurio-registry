"""Tests for Kafka integration wrappers (kafka-python compatible)."""

import json
import pytest
from unittest.mock import MagicMock, patch

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.kafka.avro import (
    KafkaAvroSerializer,
    KafkaAvroDeserializer,
)
from apicurio_registry_serdes.kafka.jsonschema import (
    KafkaJsonSchemaSerializer,
    KafkaJsonSchemaDeserializer,
)


# Test schemas
AVRO_SCHEMA = {
    "type": "record",
    "name": "SimpleRecord",
    "namespace": "com.example",
    "fields": [
        {"name": "name", "type": "string"},
        {"name": "age", "type": "int"},
    ],
}

JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "name": {"type": "string"},
        "age": {"type": "integer"},
    },
    "required": ["name", "age"],
}


class TestKafkaAvroSerializer:
    """Tests for KafkaAvroSerializer."""

    def test_init(self):
        """Test initializing the serializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        assert serializer._is_key is False
        assert serializer._default_topic == "unknown"

    def test_init_with_topic(self):
        """Test initializing the serializer with a default topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA, topic="my-topic")

        assert serializer._default_topic == "my-topic"

    def test_init_for_key(self):
        """Test initializing the serializer for keys."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA, is_key=True)

        assert serializer._is_key is True

    def test_call_with_none_returns_none(self):
        """Test that calling with None returns None."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        result = serializer(None)

        assert result is None

    def test_serialize_method_with_topic(self):
        """Test the serialize method with explicit topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        # Test that serialize method exists and handles None
        result = serializer.serialize("test-topic", None)
        assert result is None


class TestKafkaAvroDeserializer:
    """Tests for KafkaAvroDeserializer."""

    def test_init(self):
        """Test initializing the deserializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        assert deserializer._is_key is False
        assert deserializer._default_topic == "unknown"

    def test_init_with_topic(self):
        """Test initializing the deserializer with a default topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config, topic="my-topic")

        assert deserializer._default_topic == "my-topic"

    def test_init_for_key(self):
        """Test initializing the deserializer for keys."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config, is_key=True)

        assert deserializer._is_key is True

    def test_call_with_none_returns_none(self):
        """Test that calling with None returns None."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        result = deserializer(None)

        assert result is None

    def test_deserialize_method_with_topic(self):
        """Test the deserialize method with explicit topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        # Test that deserialize method exists and handles None
        result = deserializer.deserialize("test-topic", None)
        assert result is None


class TestKafkaJsonSchemaSerializer:
    """Tests for KafkaJsonSchemaSerializer."""

    def test_init(self):
        """Test initializing the serializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA)

        assert serializer._is_key is False
        assert serializer._default_topic == "unknown"

    def test_init_with_topic(self):
        """Test initializing the serializer with a default topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA, topic="json-topic")

        assert serializer._default_topic == "json-topic"

    def test_call_with_none_returns_none(self):
        """Test that calling with None returns None."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA)

        result = serializer(None)

        assert result is None

    def test_serialize_method_with_topic(self):
        """Test the serialize method with explicit topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA)

        # Test that serialize method exists and handles None
        result = serializer.serialize("test-topic", None)
        assert result is None


class TestKafkaJsonSchemaDeserializer:
    """Tests for KafkaJsonSchemaDeserializer."""

    def test_init(self):
        """Test initializing the deserializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaJsonSchemaDeserializer(config)

        assert deserializer._is_key is False
        assert deserializer._default_topic == "unknown"

    def test_init_with_topic(self):
        """Test initializing the deserializer with a default topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaJsonSchemaDeserializer(config, topic="json-topic")

        assert deserializer._default_topic == "json-topic"

    def test_call_with_none_returns_none(self):
        """Test that calling with None returns None."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaJsonSchemaDeserializer(config)

        result = deserializer(None)

        assert result is None

    def test_deserialize_method_with_topic(self):
        """Test the deserialize method with explicit topic."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaJsonSchemaDeserializer(config)

        # Test that deserialize method exists and handles None
        result = deserializer.deserialize("test-topic", None)
        assert result is None


class TestKafkaSerializerClose:
    """Tests for close() method on Kafka serializers."""

    def test_avro_serializer_close(self):
        """Test closing Avro serializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        # Should not raise
        serializer.close()

    def test_avro_deserializer_close(self):
        """Test closing Avro deserializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        # Should not raise
        deserializer.close()

    def test_json_serializer_close(self):
        """Test closing JSON Schema serializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA)

        # Should not raise
        serializer.close()

    def test_json_deserializer_close(self):
        """Test closing JSON Schema deserializer."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaJsonSchemaDeserializer(config)

        # Should not raise
        deserializer.close()


class TestKafkaPythonIntegration:
    """Tests demonstrating kafka-python compatibility."""

    def test_serializer_is_callable(self):
        """Test that serializer can be used as a callable (for value_serializer)."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        # kafka-python expects value_serializer to be a callable
        assert callable(serializer)

    def test_deserializer_is_callable(self):
        """Test that deserializer can be used as a callable (for value_deserializer)."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        # kafka-python expects value_deserializer to be a callable
        assert callable(deserializer)

    def test_serializer_signature_single_arg(self):
        """Test serializer accepts single argument (kafka-python interface)."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

        # kafka-python calls: value_serializer(value)
        # Should not raise TypeError
        import inspect
        sig = inspect.signature(serializer.__call__)
        params = list(sig.parameters.keys())
        assert "obj" in params

    def test_deserializer_signature_single_arg(self):
        """Test deserializer accepts single argument (kafka-python interface)."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")
        deserializer = KafkaAvroDeserializer(config)

        # kafka-python calls: value_deserializer(value)
        # Should not raise TypeError
        import inspect
        sig = inspect.signature(deserializer.__call__)
        params = list(sig.parameters.keys())
        assert "data" in params
