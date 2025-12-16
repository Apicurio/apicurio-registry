"""Kafka JSON Schema serializer and deserializer for kafka-python integration."""

from typing import Optional, Any, Dict, Union

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.jsonschema import (
    JsonSchemaSerializer,
    JsonSchemaDeserializer,
    JsonSchema,
)


class KafkaJsonSchemaSerializer:
    """
    Kafka-compatible JSON Schema serializer for use with kafka-python.

    This class wraps the JsonSchemaSerializer to provide an interface compatible
    with kafka-python's value_serializer/key_serializer parameters.

    Example:
        from kafka import KafkaProducer
        from apicurio_registry_serdes.config import SerdeConfig
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

    def __init__(
        self,
        config: SerdeConfig,
        schema: Union[JsonSchema, str, bytes],
        topic: Optional[str] = None,
        is_key: bool = False,
        validate: bool = True,
    ):
        """
        Initialize the Kafka JSON Schema serializer.

        Args:
            config: The serde configuration.
            schema: The JSON Schema to use (required).
            topic: Default topic name for artifact resolution.
            is_key: If True, this serializer is for message keys.
            validate: Whether to validate data against the schema.
        """
        self._serializer = JsonSchemaSerializer(config, schema, validate)
        self._default_topic = topic or "unknown"
        self._is_key = is_key

    def __call__(self, obj: Optional[Dict[str, Any]]) -> Optional[bytes]:
        """
        Serialize an object for Kafka.

        This method is called by kafka-python's Producer for each message.

        Args:
            obj: The object to serialize.

        Returns:
            The serialized bytes, or None if obj is None.
        """
        if obj is None:
            return None

        return self._serializer.serialize(self._default_topic, obj)

    def serialize(
        self,
        topic: str,
        obj: Optional[Dict[str, Any]],
    ) -> Optional[bytes]:
        """
        Serialize an object for a specific topic.

        Use this method when you need to specify the topic explicitly.

        Args:
            topic: The Kafka topic name.
            obj: The object to serialize.

        Returns:
            The serialized bytes, or None if obj is None.
        """
        if obj is None:
            return None

        return self._serializer.serialize(topic, obj)

    def close(self) -> None:
        """Close the serializer and release resources."""
        self._serializer.close()


class KafkaJsonSchemaDeserializer:
    """
    Kafka-compatible JSON Schema deserializer for use with kafka-python.

    This class wraps the JsonSchemaDeserializer to provide an interface compatible
    with kafka-python's value_deserializer/key_deserializer parameters.

    Example:
        from kafka import KafkaConsumer
        from apicurio_registry_serdes.config import SerdeConfig
        from apicurio_registry_serdes.kafka import KafkaJsonSchemaDeserializer

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
        )

        deserializer = KafkaJsonSchemaDeserializer(config)

        consumer = KafkaConsumer(
            "my-topic",
            bootstrap_servers=["localhost:9092"],
            value_deserializer=deserializer,
        )

        for message in consumer:
            print(message.value)  # Deserialized dict
    """

    def __init__(
        self,
        config: SerdeConfig,
        topic: Optional[str] = None,
        is_key: bool = False,
        validate: bool = True,
    ):
        """
        Initialize the Kafka JSON Schema deserializer.

        Args:
            config: The serde configuration.
            topic: Default topic name (for context).
            is_key: If True, this deserializer is for message keys.
            validate: Whether to validate data against the schema.
        """
        self._deserializer = JsonSchemaDeserializer(config, validate)
        self._default_topic = topic or "unknown"
        self._is_key = is_key

    def __call__(self, data: Optional[bytes]) -> Optional[Dict[str, Any]]:
        """
        Deserialize Kafka message bytes.

        This method is called by kafka-python's Consumer for each message.

        Args:
            data: The serialized bytes.

        Returns:
            The deserialized object, or None if data is None.
        """
        if data is None:
            return None

        return self._deserializer.deserialize(self._default_topic, data)

    def deserialize(
        self,
        topic: str,
        data: Optional[bytes],
    ) -> Optional[Dict[str, Any]]:
        """
        Deserialize data from a specific topic.

        Use this method when you need to specify the topic explicitly.

        Args:
            topic: The Kafka topic name.
            data: The serialized bytes.

        Returns:
            The deserialized object, or None if data is None.
        """
        if data is None:
            return None

        return self._deserializer.deserialize(topic, data)

    def close(self) -> None:
        """Close the deserializer and release resources."""
        self._deserializer.close()
