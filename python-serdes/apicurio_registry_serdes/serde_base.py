"""Base classes for serializers and deserializers."""

from abc import ABC, abstractmethod
from typing import TypeVar, Generic, Optional, Any, Dict
import struct

from apicurio_registry_serdes.config import SerdeConfig, IdOption

# Type variables for schema and data types
S = TypeVar("S")  # Schema type (e.g., avro.schema.Schema)
T = TypeVar("T")  # Data type being serialized/deserialized


# Wire format constants
MAGIC_BYTE = 0x00
ID_SIZE = 4  # 4 bytes for the schema ID


class ArtifactReference:
    """
    Reference to an artifact in the registry.

    This class holds the various identifiers that can be used to look up
    a schema in the registry.
    """

    def __init__(
        self,
        content_id: Optional[int] = None,
        global_id: Optional[int] = None,
        group_id: Optional[str] = None,
        artifact_id: Optional[str] = None,
        version: Optional[str] = None,
    ):
        self.content_id = content_id
        self.global_id = global_id
        self.group_id = group_id
        self.artifact_id = artifact_id
        self.version = version

    def has_value(self) -> bool:
        """Check if this reference has at least one identifier set."""
        return any([
            self.content_id is not None,
            self.global_id is not None,
            (self.group_id is not None and self.artifact_id is not None),
        ])

    def __repr__(self) -> str:
        parts = []
        if self.content_id is not None:
            parts.append(f"contentId={self.content_id}")
        if self.global_id is not None:
            parts.append(f"globalId={self.global_id}")
        if self.group_id is not None:
            parts.append(f"groupId={self.group_id}")
        if self.artifact_id is not None:
            parts.append(f"artifactId={self.artifact_id}")
        if self.version is not None:
            parts.append(f"version={self.version}")
        return f"ArtifactReference({', '.join(parts)})"


class IdHandler:
    """
    Handles reading and writing schema IDs in the message payload.

    The wire format is:
    [1 byte: MAGIC_BYTE (0x00)] [4 bytes: ID (big-endian)] [N bytes: payload]
    """

    def __init__(self, id_option: IdOption = IdOption.CONTENT_ID):
        self.id_option = id_option

    def write_id(self, reference: ArtifactReference) -> bytes:
        """
        Write the schema ID to bytes for inclusion in the message.

        Args:
            reference: The artifact reference containing the IDs.

        Returns:
            5 bytes: magic byte + 4-byte ID.

        Raises:
            ValueError: If the required ID is not set in the reference.
        """
        if self.id_option == IdOption.GLOBAL_ID:
            if reference.global_id is None:
                raise ValueError(
                    "Missing globalId. IdOption is GLOBAL_ID but globalId is not set."
                )
            schema_id = reference.global_id
        else:
            if reference.content_id is None:
                raise ValueError(
                    "Missing contentId. IdOption is CONTENT_ID but contentId is not set."
                )
            schema_id = reference.content_id

        # Pack as: 1 byte magic + 4 byte big-endian unsigned int
        return struct.pack(">BI", MAGIC_BYTE, schema_id)

    def read_id(self, data: bytes) -> ArtifactReference:
        """
        Read the schema ID from message bytes.

        Args:
            data: The message bytes (must start with magic byte).

        Returns:
            An ArtifactReference with the appropriate ID set.

        Raises:
            ValueError: If the magic byte is incorrect or data is too short.
        """
        if len(data) < 1 + ID_SIZE:
            raise ValueError(
                f"Message too short: expected at least {1 + ID_SIZE} bytes, got {len(data)}"
            )

        magic, schema_id = struct.unpack(">BI", data[: 1 + ID_SIZE])

        if magic != MAGIC_BYTE:
            raise ValueError(f"Unknown magic byte: {magic:#x}, expected {MAGIC_BYTE:#x}")

        if self.id_option == IdOption.GLOBAL_ID:
            return ArtifactReference(global_id=schema_id)
        else:
            return ArtifactReference(content_id=schema_id)

    @property
    def id_size(self) -> int:
        """Return the total size of the header (magic + ID)."""
        return 1 + ID_SIZE


class SchemaLookupResult(Generic[S]):
    """
    Result of a schema lookup from the registry.

    Contains the parsed schema and metadata about the artifact.
    """

    def __init__(
        self,
        parsed_schema: S,
        raw_schema: bytes,
        content_id: Optional[int] = None,
        global_id: Optional[int] = None,
        group_id: Optional[str] = None,
        artifact_id: Optional[str] = None,
        version: Optional[str] = None,
    ):
        self.parsed_schema = parsed_schema
        self.raw_schema = raw_schema
        self.content_id = content_id
        self.global_id = global_id
        self.group_id = group_id
        self.artifact_id = artifact_id
        self.version = version

    def to_artifact_reference(self) -> ArtifactReference:
        """Convert to an ArtifactReference."""
        return ArtifactReference(
            content_id=self.content_id,
            global_id=self.global_id,
            group_id=self.group_id,
            artifact_id=self.artifact_id,
            version=self.version,
        )


class BaseSerializer(ABC, Generic[S, T]):
    """
    Abstract base class for serializers.

    Subclasses must implement schema_parser() and serialize_data() methods.
    """

    def __init__(self, config: SerdeConfig):
        self.config = config
        self.id_handler = IdHandler(config.use_id)
        self._schema_resolver: Optional[Any] = None  # Set by configure()

    @property
    def schema_resolver(self) -> Any:
        """Get the schema resolver, initializing lazily if needed."""
        if self._schema_resolver is None:
            from apicurio_registry_serdes.schema_resolver import SchemaResolver
            self._schema_resolver = SchemaResolver(self.config, self.schema_parser())
        return self._schema_resolver

    @abstractmethod
    def schema_parser(self) -> Any:
        """Return the schema parser for this serializer type."""
        ...

    @abstractmethod
    def serialize_data(self, schema: S, data: T) -> bytes:
        """
        Serialize the data using the given schema.

        Args:
            schema: The parsed schema to use for serialization.
            data: The data to serialize.

        Returns:
            The serialized bytes (without the header).
        """
        ...

    def serialize(self, topic: str, data: T) -> Optional[bytes]:
        """
        Serialize data for the given topic.

        This is the main entry point for serialization. It:
        1. Resolves the schema from the registry (or registers it if auto_register is True)
        2. Serializes the data using the schema
        3. Prepends the magic byte and schema ID

        Args:
            topic: The Kafka topic name (used for artifact resolution).
            data: The data to serialize.

        Returns:
            The serialized bytes including header, or None if data is None.
        """
        if data is None:
            return None

        # Resolve schema (may register if auto_register is enabled)
        schema_result = self.schema_resolver.resolve_schema(topic, data)

        # Serialize data
        payload = self.serialize_data(schema_result.parsed_schema, data)

        # Build final message: header + payload
        header = self.id_handler.write_id(schema_result.to_artifact_reference())
        return header + payload

    def close(self) -> None:
        """Release any resources held by the serializer."""
        if self._schema_resolver is not None:
            self._schema_resolver.close()


class BaseDeserializer(ABC, Generic[S, T]):
    """
    Abstract base class for deserializers.

    Subclasses must implement schema_parser() and deserialize_data() methods.
    """

    def __init__(self, config: SerdeConfig):
        self.config = config
        self.id_handler = IdHandler(config.use_id)
        self._schema_resolver: Optional[Any] = None

    @property
    def schema_resolver(self) -> Any:
        """Get the schema resolver, initializing lazily if needed."""
        if self._schema_resolver is None:
            from apicurio_registry_serdes.schema_resolver import SchemaResolver
            self._schema_resolver = SchemaResolver(self.config, self.schema_parser())
        return self._schema_resolver

    @abstractmethod
    def schema_parser(self) -> Any:
        """Return the schema parser for this deserializer type."""
        ...

    @abstractmethod
    def deserialize_data(self, schema: S, data: bytes) -> T:
        """
        Deserialize the data using the given schema.

        Args:
            schema: The parsed schema to use for deserialization.
            data: The data bytes (without header).

        Returns:
            The deserialized object.
        """
        ...

    def deserialize(self, topic: str, data: bytes) -> Optional[T]:
        """
        Deserialize data from the given topic.

        This is the main entry point for deserialization. It:
        1. Reads the schema ID from the message header
        2. Looks up the schema from the registry
        3. Deserializes the data using the schema

        Args:
            topic: The Kafka topic name (for context).
            data: The serialized bytes including header.

        Returns:
            The deserialized object, or None if data is None.
        """
        if data is None:
            return None

        # Read schema reference from header
        artifact_ref = self.id_handler.read_id(data)

        # Look up schema from registry
        schema_result = self.schema_resolver.resolve_schema_by_reference(artifact_ref)

        # Deserialize payload (skip header)
        payload = data[self.id_handler.id_size:]
        return self.deserialize_data(schema_result.parsed_schema, payload)

    def close(self) -> None:
        """Release any resources held by the deserializer."""
        if self._schema_resolver is not None:
            self._schema_resolver.close()
