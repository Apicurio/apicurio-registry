"""Protobuf serializer and deserializer implementations."""

from typing import Dict, Optional, Any, Type, TypeVar, Union

from google.protobuf.message import Message
from google.protobuf.descriptor import Descriptor, FileDescriptor
from google.protobuf.descriptor_pool import DescriptorPool
from google.protobuf.message_factory import MessageFactory

from apicurio_registry_serdes.config import SerdeConfig
from apicurio_registry_serdes.schema_parser import SchemaParser
from apicurio_registry_serdes.serde_base import BaseSerializer, BaseDeserializer


# Type variable for Protobuf message types
T = TypeVar("T", bound=Message)


class ProtobufSchemaParser(SchemaParser[Descriptor, Message]):
    """
    Schema parser implementation for Protobuf schemas.

    Handles parsing of .proto file content and Protobuf message descriptors.
    """

    def __init__(self, message_type: Optional[Type[Message]] = None):
        """
        Initialize the Protobuf schema parser.

        Args:
            message_type: Optional message type class for deserialization.
                If not provided, schema must be provided from registry.
        """
        self._message_type = message_type
        self._descriptor_pool = DescriptorPool()
        self._message_factory = MessageFactory(pool=self._descriptor_pool)

    def artifact_type(self) -> str:
        """Return the artifact type for Protobuf schemas."""
        return "PROTOBUF"

    def parse_schema(
        self,
        raw_schema: bytes,
        references: Optional[Dict[str, Descriptor]] = None,
    ) -> Descriptor:
        """
        Parse a raw Protobuf schema.

        For Protobuf, the schema can be:
        1. A .proto file content (text)
        2. A FileDescriptorSet (binary)

        Args:
            raw_schema: The raw schema bytes.
            references: Optional dict of referenced schemas by name.

        Returns:
            The Protobuf message Descriptor.
        """
        # Try to detect format based on content
        try:
            # Try parsing as FileDescriptorSet (binary)
            from google.protobuf.descriptor_pb2 import FileDescriptorSet

            fds = FileDescriptorSet()
            fds.ParseFromString(raw_schema)

            if fds.file:
                # Add all file descriptors to the pool
                for fd_proto in fds.file:
                    self._descriptor_pool.Add(fd_proto)

                # Return the descriptor for the first message type
                last_file = fds.file[-1]
                if last_file.message_type:
                    file_desc = self._descriptor_pool.FindFileByName(last_file.name)
                    msg_name = last_file.message_type[0].name
                    return file_desc.message_types_by_name[msg_name]

                raise ValueError("No message types found in FileDescriptorSet")

        except Exception:
            pass

        # Try parsing as .proto text format
        try:
            proto_content = raw_schema.decode("utf-8")
            return self._parse_proto_text(proto_content)
        except Exception as e:
            raise ValueError(
                f"Failed to parse Protobuf schema. "
                f"Schema should be FileDescriptorSet (binary) or .proto text. "
                f"Error: {e}"
            )

    def _parse_proto_text(self, proto_content: str) -> Descriptor:
        """
        Parse .proto file text content.

        Note: This requires the grpcio-tools package for dynamic parsing.
        For production use, it's recommended to use pre-compiled descriptors.
        """
        # For simplicity, if we have a message_type, return its descriptor
        if self._message_type is not None:
            return self._message_type.DESCRIPTOR

        raise ValueError(
            "Parsing .proto text files dynamically requires grpcio-tools. "
            "Consider using pre-compiled message types or FileDescriptorSet format."
        )

    def get_schema_from_data(self, data: Message) -> Descriptor:
        """
        Extract the schema (Descriptor) from a Protobuf message.

        Args:
            data: The Protobuf message.

        Returns:
            The message's Descriptor.
        """
        if isinstance(data, Message):
            return data.DESCRIPTOR
        raise ValueError(
            f"Expected a Protobuf Message, got {type(data).__name__}"
        )

    def serialize_schema(self, schema: Descriptor) -> bytes:
        """
        Serialize a Protobuf schema to bytes.

        Serializes the FileDescriptor containing this message as a
        FileDescriptorSet.

        Args:
            schema: The Protobuf message Descriptor.

        Returns:
            The FileDescriptorSet as bytes.
        """
        from google.protobuf.descriptor_pb2 import FileDescriptorSet, FileDescriptorProto

        fds = FileDescriptorSet()

        # Get the file descriptor containing this message
        file_desc = schema.file

        # Serialize the file descriptor
        fd_proto = FileDescriptorProto()
        file_desc.CopyToProto(fd_proto)
        fds.file.append(fd_proto)

        return fds.SerializeToString()

    def supports_extract_schema_from_data(self) -> bool:
        """Protobuf supports extracting schema from Message objects."""
        return True


class ProtobufSerializer(BaseSerializer[Descriptor, Message]):
    """
    Protobuf serializer that integrates with Apicurio Registry.

    This serializer:
    1. Extracts the schema (Descriptor) from the Protobuf message
    2. Registers the schema with the registry (if auto_register is enabled)
    3. Serializes the message using Protobuf binary format
    4. Prepends the magic byte and schema ID
    """

    def __init__(
        self,
        config: SerdeConfig,
        message_type: Optional[Type[Message]] = None,
    ):
        """
        Initialize the Protobuf serializer.

        Args:
            config: The serde configuration.
            message_type: Optional message type for schema registration.
        """
        super().__init__(config)
        self._message_type = message_type
        self._parser = ProtobufSchemaParser(message_type=message_type)

    def schema_parser(self) -> SchemaParser[Descriptor, Message]:
        """Return the Protobuf schema parser."""
        return self._parser

    def serialize_data(self, schema: Descriptor, data: Message) -> bytes:
        """
        Serialize a Protobuf message.

        Args:
            schema: The message Descriptor (used for validation).
            data: The Protobuf message to serialize.

        Returns:
            The serialized bytes.
        """
        return data.SerializeToString()


class ProtobufDeserializer(BaseDeserializer[Descriptor, Message]):
    """
    Protobuf deserializer that integrates with Apicurio Registry.

    This deserializer:
    1. Reads the schema ID from the message header
    2. Looks up the schema (Descriptor) from the registry
    3. Deserializes the Protobuf message using the appropriate message type
    """

    def __init__(
        self,
        config: SerdeConfig,
        message_type: Type[T],
    ):
        """
        Initialize the Protobuf deserializer.

        Args:
            config: The serde configuration.
            message_type: The Protobuf message class to deserialize to.
                This is required because Protobuf needs the concrete type
                to deserialize properly.
        """
        super().__init__(config)
        self._message_type = message_type
        self._parser = ProtobufSchemaParser(message_type=message_type)

    def schema_parser(self) -> SchemaParser[Descriptor, Message]:
        """Return the Protobuf schema parser."""
        return self._parser

    def deserialize_data(self, schema: Descriptor, data: bytes) -> T:
        """
        Deserialize Protobuf data.

        Args:
            schema: The message Descriptor from the registry.
            data: The serialized bytes (without header).

        Returns:
            The deserialized Protobuf message.
        """
        message = self._message_type()
        message.ParseFromString(data)
        return message

    def deserialize(
        self,
        topic: str,
        data: bytes,
        message_type: Optional[Type[T]] = None,
    ) -> Optional[T]:
        """
        Deserialize data from the given topic.

        Args:
            topic: The Kafka topic name.
            data: The serialized bytes including header.
            message_type: Optional override message type for deserialization.

        Returns:
            The deserialized Protobuf message, or None if data is None.
        """
        if data is None:
            return None

        if message_type is not None:
            # Use the provided message type
            original_type = self._message_type
            self._message_type = message_type
            try:
                return super().deserialize(topic, data)
            finally:
                self._message_type = original_type

        return super().deserialize(topic, data)


class DynamicProtobufDeserializer(BaseDeserializer[Descriptor, Message]):
    """
    Dynamic Protobuf deserializer that can deserialize any message type.

    This deserializer uses the schema from the registry to dynamically
    create message instances without requiring a pre-compiled message class.
    """

    def __init__(self, config: SerdeConfig):
        """
        Initialize the dynamic Protobuf deserializer.

        Args:
            config: The serde configuration.
        """
        super().__init__(config)
        self._parser = ProtobufSchemaParser()
        self._message_factory = MessageFactory()

    def schema_parser(self) -> SchemaParser[Descriptor, Message]:
        """Return the Protobuf schema parser."""
        return self._parser

    def deserialize_data(self, schema: Descriptor, data: bytes) -> Message:
        """
        Deserialize Protobuf data using dynamic message creation.

        Args:
            schema: The message Descriptor from the registry.
            data: The serialized bytes (without header).

        Returns:
            The deserialized Protobuf message.
        """
        # Create a message instance from the descriptor
        message_class = self._message_factory.GetPrototype(schema)
        message = message_class()
        message.ParseFromString(data)
        return message
