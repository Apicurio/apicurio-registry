"""ID option enumeration for schema identification."""

from enum import Enum


class IdOption(Enum):
    """
    Defines which ID to use for schema identification in the wire format.

    The serializer writes this ID to the message payload, and the deserializer
    uses it to look up the schema from the registry.
    """

    CONTENT_ID = "contentId"
    """Use the content ID (hash-based, same content = same ID across versions)."""

    GLOBAL_ID = "globalId"
    """Use the global ID (unique per artifact version)."""

    def __str__(self) -> str:
        return self.value
