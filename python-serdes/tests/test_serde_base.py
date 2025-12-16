"""Tests for base serialization/deserialization classes."""

import pytest
import struct

from apicurio_registry_serdes.config import SerdeConfig, IdOption
from apicurio_registry_serdes.serde_base import (
    ArtifactReference,
    IdHandler,
    SchemaLookupResult,
    MAGIC_BYTE,
    ID_SIZE,
)


class TestArtifactReference:
    """Tests for ArtifactReference."""

    def test_empty_reference(self):
        """Test creating an empty reference."""
        ref = ArtifactReference()

        assert ref.content_id is None
        assert ref.global_id is None
        assert ref.group_id is None
        assert ref.artifact_id is None
        assert ref.version is None
        assert not ref.has_value()

    def test_content_id_reference(self):
        """Test creating a reference with content_id."""
        ref = ArtifactReference(content_id=12345)

        assert ref.content_id == 12345
        assert ref.has_value()

    def test_global_id_reference(self):
        """Test creating a reference with global_id."""
        ref = ArtifactReference(global_id=67890)

        assert ref.global_id == 67890
        assert ref.has_value()

    def test_gav_reference(self):
        """Test creating a reference with group/artifact/version."""
        ref = ArtifactReference(
            group_id="my-group",
            artifact_id="my-artifact",
            version="1.0.0",
        )

        assert ref.group_id == "my-group"
        assert ref.artifact_id == "my-artifact"
        assert ref.version == "1.0.0"
        assert ref.has_value()

    def test_partial_gav_no_value(self):
        """Test that partial GAV (only group or only artifact) doesn't have value."""
        ref_group_only = ArtifactReference(group_id="my-group")
        ref_artifact_only = ArtifactReference(artifact_id="my-artifact")

        assert not ref_group_only.has_value()
        assert not ref_artifact_only.has_value()

    def test_repr(self):
        """Test string representation."""
        ref = ArtifactReference(content_id=123, group_id="grp", artifact_id="art")

        repr_str = repr(ref)
        assert "contentId=123" in repr_str
        assert "groupId=grp" in repr_str
        assert "artifactId=art" in repr_str


class TestIdHandler:
    """Tests for IdHandler."""

    def test_write_content_id(self):
        """Test writing content ID to bytes."""
        handler = IdHandler(IdOption.CONTENT_ID)
        ref = ArtifactReference(content_id=12345)

        data = handler.write_id(ref)

        assert len(data) == 5
        assert data[0] == MAGIC_BYTE
        magic, schema_id = struct.unpack(">BI", data)
        assert magic == MAGIC_BYTE
        assert schema_id == 12345

    def test_write_global_id(self):
        """Test writing global ID to bytes."""
        handler = IdHandler(IdOption.GLOBAL_ID)
        ref = ArtifactReference(global_id=67890)

        data = handler.write_id(ref)

        assert len(data) == 5
        magic, schema_id = struct.unpack(">BI", data)
        assert magic == MAGIC_BYTE
        assert schema_id == 67890

    def test_write_content_id_missing_raises(self):
        """Test that missing content_id raises ValueError."""
        handler = IdHandler(IdOption.CONTENT_ID)
        ref = ArtifactReference(global_id=12345)  # Wrong ID type

        with pytest.raises(ValueError, match="Missing contentId"):
            handler.write_id(ref)

    def test_write_global_id_missing_raises(self):
        """Test that missing global_id raises ValueError."""
        handler = IdHandler(IdOption.GLOBAL_ID)
        ref = ArtifactReference(content_id=12345)  # Wrong ID type

        with pytest.raises(ValueError, match="Missing globalId"):
            handler.write_id(ref)

    def test_read_content_id(self):
        """Test reading content ID from bytes."""
        handler = IdHandler(IdOption.CONTENT_ID)
        data = struct.pack(">BI", MAGIC_BYTE, 12345) + b"payload"

        ref = handler.read_id(data)

        assert ref.content_id == 12345
        assert ref.global_id is None

    def test_read_global_id(self):
        """Test reading global ID from bytes."""
        handler = IdHandler(IdOption.GLOBAL_ID)
        data = struct.pack(">BI", MAGIC_BYTE, 67890) + b"payload"

        ref = handler.read_id(data)

        assert ref.global_id == 67890
        assert ref.content_id is None

    def test_read_short_data_raises(self):
        """Test that reading from too-short data raises ValueError."""
        handler = IdHandler(IdOption.CONTENT_ID)
        data = b"\x00\x01"  # Only 2 bytes

        with pytest.raises(ValueError, match="Message too short"):
            handler.read_id(data)

    def test_read_wrong_magic_raises(self):
        """Test that wrong magic byte raises ValueError."""
        handler = IdHandler(IdOption.CONTENT_ID)
        data = struct.pack(">BI", 0xFF, 12345)  # Wrong magic byte

        with pytest.raises(ValueError, match="Unknown magic byte"):
            handler.read_id(data)

    def test_id_size(self):
        """Test id_size property."""
        handler = IdHandler(IdOption.CONTENT_ID)

        assert handler.id_size == 5  # 1 magic + 4 ID


class TestSchemaLookupResult:
    """Tests for SchemaLookupResult."""

    def test_minimal_result(self):
        """Test creating a minimal result."""
        result = SchemaLookupResult(
            parsed_schema={"type": "record"},
            raw_schema=b'{"type":"record"}',
        )

        assert result.parsed_schema == {"type": "record"}
        assert result.raw_schema == b'{"type":"record"}'
        assert result.content_id is None
        assert result.global_id is None

    def test_full_result(self):
        """Test creating a result with all fields."""
        result = SchemaLookupResult(
            parsed_schema={"type": "record"},
            raw_schema=b'{"type":"record"}',
            content_id=123,
            global_id=456,
            group_id="my-group",
            artifact_id="my-artifact",
            version="1.0.0",
        )

        assert result.content_id == 123
        assert result.global_id == 456
        assert result.group_id == "my-group"
        assert result.artifact_id == "my-artifact"
        assert result.version == "1.0.0"

    def test_to_artifact_reference(self):
        """Test converting to ArtifactReference."""
        result = SchemaLookupResult(
            parsed_schema={"type": "record"},
            raw_schema=b'{"type":"record"}',
            content_id=123,
            global_id=456,
            group_id="my-group",
            artifact_id="my-artifact",
            version="1.0.0",
        )

        ref = result.to_artifact_reference()

        assert ref.content_id == 123
        assert ref.global_id == 456
        assert ref.group_id == "my-group"
        assert ref.artifact_id == "my-artifact"
        assert ref.version == "1.0.0"
