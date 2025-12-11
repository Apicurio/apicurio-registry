"""
Apicurio Registry Serializers and Deserializers for Python.

This package provides serialization and deserialization support for messages
that use schemas stored in Apicurio Registry. It supports multiple schema
formats (Avro, JSON Schema, Protobuf) and messaging systems (Kafka, etc.).

Example usage with kafka-python and Avro:

    from kafka import KafkaProducer, KafkaConsumer
    from apicurio_registry_serdes import SerdeConfig
    from apicurio_registry_serdes.kafka import KafkaAvroSerializer, KafkaAvroDeserializer

    config = SerdeConfig(
        registry_url="http://localhost:8080/apis/registry/v3",
        auto_register=True,
    )

    avro_schema = {
        "type": "record",
        "name": "User",
        "fields": [
            {"name": "name", "type": "string"},
            {"name": "age", "type": "int"},
        ]
    }

    # Producer with Avro serialization
    serializer = KafkaAvroSerializer(config, schema=avro_schema)
    producer = KafkaProducer(
        bootstrap_servers=["localhost:9092"],
        value_serializer=serializer,
    )
    producer.send("my-topic", {"name": "John", "age": 30})

    # Consumer with Avro deserialization
    deserializer = KafkaAvroDeserializer(config)
    consumer = KafkaConsumer(
        "my-topic",
        bootstrap_servers=["localhost:9092"],
        value_deserializer=deserializer,
    )

Example usage with JSON Schema:

    from kafka import KafkaProducer
    from apicurio_registry_serdes import SerdeConfig
    from apicurio_registry_serdes.kafka import KafkaJsonSchemaSerializer

    config = SerdeConfig(
        registry_url="http://localhost:8080/apis/registry/v3",
        auto_register=True,
    )

    schema = {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "age": {"type": "integer"}
        },
        "required": ["name", "age"]
    }

    serializer = KafkaJsonSchemaSerializer(config, schema=schema)
    producer = KafkaProducer(
        bootstrap_servers=["localhost:9092"],
        value_serializer=serializer,
    )
    producer.send("my-topic", {"name": "John", "age": 30})
"""

from apicurio_registry_serdes.config.serde_config import SerdeConfig
from apicurio_registry_serdes.config.id_option import IdOption
from apicurio_registry_serdes._version import __version__

# Base classes (always available)
from apicurio_registry_serdes.serde_base import (
    BaseSerializer,
    BaseDeserializer,
    ArtifactReference,
    SchemaLookupResult,
    IdHandler,
)

from apicurio_registry_serdes.schema_parser import SchemaParser
from apicurio_registry_serdes.schema_resolver import SchemaResolver, RegistryClientError

__all__ = [
    # Configuration
    "SerdeConfig",
    "IdOption",
    "__version__",
    # Base classes
    "BaseSerializer",
    "BaseDeserializer",
    "ArtifactReference",
    "SchemaLookupResult",
    "IdHandler",
    "SchemaParser",
    "SchemaResolver",
    "RegistryClientError",
]


def __getattr__(name: str):
    """Lazy import for optional serializers to avoid import errors when dependencies are missing."""
    # Avro serializers (requires fastavro)
    if name in ("AvroSerializer", "AvroDeserializer", "AvroSchemaParser"):
        from apicurio_registry_serdes import avro
        return getattr(avro, name)

    # JSON Schema serializers
    if name in ("JsonSchemaSerializer", "JsonSchemaDeserializer", "JsonSchemaParser"):
        from apicurio_registry_serdes import jsonschema
        return getattr(jsonschema, name)

    # Protobuf serializers (requires protobuf)
    if name in ("ProtobufSerializer", "ProtobufDeserializer", "ProtobufSchemaParser"):
        from apicurio_registry_serdes import protobuf
        return getattr(protobuf, name)

    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
