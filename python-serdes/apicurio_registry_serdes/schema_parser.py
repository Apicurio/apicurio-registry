"""Abstract base class for schema parsers."""

from abc import ABC, abstractmethod
from typing import TypeVar, Generic, Any, Dict, Optional

S = TypeVar("S")  # Schema type
T = TypeVar("T")  # Data type


class SchemaParser(ABC, Generic[S, T]):
    """
    Abstract base class for parsing schemas.

    Each schema type (Avro, JSON Schema, Protobuf) should have its own
    implementation of this class.
    """

    @abstractmethod
    def artifact_type(self) -> str:
        """
        Return the artifact type identifier for this schema type.

        Returns:
            The artifact type string (e.g., "AVRO", "JSON", "PROTOBUF").
        """
        ...

    @abstractmethod
    def parse_schema(
        self,
        raw_schema: bytes,
        references: Optional[Dict[str, S]] = None,
    ) -> S:
        """
        Parse a raw schema into a parsed schema object.

        Args:
            raw_schema: The raw schema bytes.
            references: Optional dict of referenced schemas by name.

        Returns:
            The parsed schema object.
        """
        ...

    @abstractmethod
    def get_schema_from_data(self, data: T) -> S:
        """
        Extract the schema from a data object.

        This is used for auto-registration when the schema can be
        inferred from the data (e.g., Avro SpecificRecord).

        Args:
            data: The data object.

        Returns:
            The schema for the data.
        """
        ...

    @abstractmethod
    def serialize_schema(self, schema: S) -> bytes:
        """
        Serialize a schema to bytes for registration.

        Args:
            schema: The parsed schema.

        Returns:
            The serialized schema bytes.
        """
        ...

    def supports_extract_schema_from_data(self) -> bool:
        """
        Check if this parser supports extracting schema from data.

        Returns:
            True if get_schema_from_data() is supported.
        """
        return True
