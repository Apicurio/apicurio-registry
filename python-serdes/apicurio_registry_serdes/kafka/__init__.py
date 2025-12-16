"""
Kafka integration for Apicurio Registry serializers and deserializers.

This module provides Kafka-compatible serializers and deserializers that
integrate with the kafka-python library.

Example usage with Producer:

    from kafka import KafkaProducer
    from apicurio_registry_serdes.config import SerdeConfig
    from apicurio_registry_serdes.kafka import KafkaAvroSerializer

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

    serializer = KafkaAvroSerializer(config, schema=avro_schema)

    producer = KafkaProducer(
        bootstrap_servers=["localhost:9092"],
        value_serializer=serializer,
    )

    producer.send("my-topic", {"name": "John", "age": 30})

Example usage with Consumer:

    from kafka import KafkaConsumer
    from apicurio_registry_serdes.kafka import KafkaAvroDeserializer

    deserializer = KafkaAvroDeserializer(config)

    consumer = KafkaConsumer(
        "my-topic",
        bootstrap_servers=["localhost:9092"],
        value_deserializer=deserializer,
    )

    for message in consumer:
        print(message.value)  # Deserialized Avro data
"""

__all__ = [
    "KafkaAvroSerializer",
    "KafkaAvroDeserializer",
    "KafkaJsonSchemaSerializer",
    "KafkaJsonSchemaDeserializer",
    "KafkaProtobufSerializer",
    "KafkaProtobufDeserializer",
]


def __getattr__(name: str):
    """Lazy import for Kafka serializers to handle optional dependencies."""
    # Avro serializers (requires fastavro)
    if name in ("KafkaAvroSerializer", "KafkaAvroDeserializer"):
        from apicurio_registry_serdes.kafka import avro
        return getattr(avro, name)

    # JSON Schema serializers
    if name in ("KafkaJsonSchemaSerializer", "KafkaJsonSchemaDeserializer"):
        from apicurio_registry_serdes.kafka import jsonschema
        return getattr(jsonschema, name)

    # Protobuf serializers (requires protobuf)
    if name in ("KafkaProtobufSerializer", "KafkaProtobufDeserializer"):
        from apicurio_registry_serdes.kafka import protobuf
        return getattr(protobuf, name)

    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
