"""Kafka Protobuf serializer and deserializer for kafka-python integration."""

from typing import Optional, Any, Type, TypeVar

from google.protobuf.message import Message

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.protobuf import (
    ProtobufSerializer,
    ProtobufDeserializer,
)


T = TypeVar("T", bound=Message)


class KafkaProtobufSerializer:
    """
    Kafka-compatible Protobuf serializer for use with kafka-python.

    This class wraps the ProtobufSerializer to provide an interface compatible
    with kafka-python's value_serializer/key_serializer parameters.

    Example:
        from kafka import KafkaProducer
        from apicurio_registry_serdes.config import SerdeConfig
        from apicurio_registry_serdes.kafka import KafkaProtobufSerializer
        from my_proto_pb2 import UserMessage

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            auto_register=True,
        )

        serializer = KafkaProtobufSerializer(config, message_type=UserMessage)

        producer = KafkaProducer(
            bootstrap_servers=["localhost:9092"],
            value_serializer=serializer,
        )

        message = UserMessage(name="John", age=30)
        producer.send("my-topic", message)
    """

    def __init__(
        self,
        config: SerdeConfig,
        message_type: Optional[Type[Message]] = None,
        topic: Optional[str] = None,
        is_key: bool = False,
    ):
        """
        Initialize the Kafka Protobuf serializer.

        Args:
            config: The serde configuration.
            message_type: Optional message type for schema registration.
            topic: Default topic name for artifact resolution.
            is_key: If True, this serializer is for message keys.
        """
        self._serializer = ProtobufSerializer(config, message_type)
        self._default_topic = topic or "unknown"
        self._is_key = is_key

    def __call__(self, obj: Optional[Message]) -> Optional[bytes]:
        """
        Serialize a Protobuf message for Kafka.

        This method is called by kafka-python's Producer for each message.

        Args:
            obj: The Protobuf message to serialize.

        Returns:
            The serialized bytes, or None if obj is None.
        """
        if obj is None:
            return None

        return self._serializer.serialize(self._default_topic, obj)

    def serialize(
        self,
        topic: str,
        obj: Optional[Message],
    ) -> Optional[bytes]:
        """
        Serialize a Protobuf message for a specific topic.

        Use this method when you need to specify the topic explicitly.

        Args:
            topic: The Kafka topic name.
            obj: The Protobuf message to serialize.

        Returns:
            The serialized bytes, or None if obj is None.
        """
        if obj is None:
            return None

        return self._serializer.serialize(topic, obj)

    def close(self) -> None:
        """Close the serializer and release resources."""
        self._serializer.close()


class KafkaProtobufDeserializer:
    """
    Kafka-compatible Protobuf deserializer for use with kafka-python.

    This class wraps the ProtobufDeserializer to provide an interface compatible
    with kafka-python's value_deserializer/key_deserializer parameters.

    Example:
        from kafka import KafkaConsumer
        from apicurio_registry_serdes.config import SerdeConfig
        from apicurio_registry_serdes.kafka import KafkaProtobufDeserializer
        from my_proto_pb2 import UserMessage

        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
        )

        deserializer = KafkaProtobufDeserializer(config, message_type=UserMessage)

        consumer = KafkaConsumer(
            "my-topic",
            bootstrap_servers=["localhost:9092"],
            value_deserializer=deserializer,
        )

        for message in consumer:
            print(message.value.name)  # Access Protobuf fields
    """

    def __init__(
        self,
        config: SerdeConfig,
        message_type: Type[T],
        topic: Optional[str] = None,
        is_key: bool = False,
    ):
        """
        Initialize the Kafka Protobuf deserializer.

        Args:
            config: The serde configuration.
            message_type: The Protobuf message class to deserialize to.
            topic: Default topic name (for context).
            is_key: If True, this deserializer is for message keys.
        """
        self._deserializer = ProtobufDeserializer(config, message_type)
        self._default_topic = topic or "unknown"
        self._is_key = is_key

    def __call__(self, data: Optional[bytes]) -> Optional[T]:
        """
        Deserialize Kafka message bytes.

        This method is called by kafka-python's Consumer for each message.

        Args:
            data: The serialized bytes.

        Returns:
            The deserialized Protobuf message, or None if data is None.
        """
        if data is None:
            return None

        return self._deserializer.deserialize(self._default_topic, data)

    def deserialize(
        self,
        topic: str,
        data: Optional[bytes],
    ) -> Optional[T]:
        """
        Deserialize data from a specific topic.

        Use this method when you need to specify the topic explicitly.

        Args:
            topic: The Kafka topic name.
            data: The serialized bytes.

        Returns:
            The deserialized Protobuf message, or None if data is None.
        """
        if data is None:
            return None

        return self._deserializer.deserialize(topic, data)

    def close(self) -> None:
        """Close the deserializer and release resources."""
        self._deserializer.close()
