"""Tests for configuration classes."""

import pytest

from apicurio_registry_serdes.config import SerdeConfig, IdOption


class TestSerdeConfig:
    """Tests for SerdeConfig."""

    def test_minimal_config(self):
        """Test creating config with just registry_url."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3")

        assert config.registry_url == "http://localhost:8080/apis/registry/v3"
        assert config.auto_register is False
        assert config.find_latest is False
        assert config.use_id == IdOption.CONTENT_ID

    def test_url_normalization(self):
        """Test that trailing slashes are removed from URL."""
        config = SerdeConfig(registry_url="http://localhost:8080/apis/registry/v3/")

        assert config.registry_url == "http://localhost:8080/apis/registry/v3"

    def test_empty_url_raises(self):
        """Test that empty registry_url raises ValueError."""
        with pytest.raises(ValueError, match="registry_url is required"):
            SerdeConfig(registry_url="")

    def test_invalid_if_exists_raises(self):
        """Test that invalid auto_register_if_exists raises ValueError."""
        with pytest.raises(ValueError, match="auto_register_if_exists must be one of"):
            SerdeConfig(
                registry_url="http://localhost:8080/apis/registry/v3",
                auto_register_if_exists="INVALID",
            )

    def test_full_config(self):
        """Test creating config with all options."""
        config = SerdeConfig(
            registry_url="http://localhost:8080/apis/registry/v3",
            auto_register=True,
            auto_register_if_exists="CREATE_VERSION",
            find_latest=True,
            use_id=IdOption.GLOBAL_ID,
            artifact_group_id="my-group",
            artifact_id="my-artifact",
            artifact_version="1.0.0",
            check_period_ms=60000,
            retry_count=5,
            retry_backoff_ms=500,
            auth_username="user",
            auth_password="password",
            headers={"X-Custom": "value"},
        )

        assert config.auto_register is True
        assert config.auto_register_if_exists == "CREATE_VERSION"
        assert config.find_latest is True
        assert config.use_id == IdOption.GLOBAL_ID
        assert config.artifact_group_id == "my-group"
        assert config.artifact_id == "my-artifact"
        assert config.artifact_version == "1.0.0"
        assert config.check_period_ms == 60000
        assert config.retry_count == 5
        assert config.retry_backoff_ms == 500
        assert config.auth_username == "user"
        assert config.auth_password == "password"
        assert config.headers == {"X-Custom": "value"}

    def test_from_dict_kafka_style(self):
        """Test creating config from Kafka-style dictionary."""
        kafka_config = {
            "apicurio.registry.url": "http://localhost:8080/apis/registry/v3",
            "apicurio.registry.auto-register": True,
            "apicurio.registry.use-id": "globalId",
            "apicurio.registry.artifact.group-id": "my-group",
        }

        config = SerdeConfig.from_dict(kafka_config)

        assert config.registry_url == "http://localhost:8080/apis/registry/v3"
        assert config.auto_register is True
        assert config.use_id == IdOption.GLOBAL_ID
        assert config.artifact_group_id == "my-group"

    def test_from_dict_direct_attrs(self):
        """Test creating config from dict with direct attribute names."""
        direct_config = {
            "registry_url": "http://localhost:8080/apis/registry/v3",
            "auto_register": True,
            "artifact_group_id": "my-group",
        }

        config = SerdeConfig.from_dict(direct_config)

        assert config.registry_url == "http://localhost:8080/apis/registry/v3"
        assert config.auto_register is True
        assert config.artifact_group_id == "my-group"


class TestIdOption:
    """Tests for IdOption enum."""

    def test_content_id_value(self):
        """Test CONTENT_ID value."""
        assert IdOption.CONTENT_ID.value == "contentId"
        assert str(IdOption.CONTENT_ID) == "contentId"

    def test_global_id_value(self):
        """Test GLOBAL_ID value."""
        assert IdOption.GLOBAL_ID.value == "globalId"
        assert str(IdOption.GLOBAL_ID) == "globalId"

    def test_from_string(self):
        """Test creating IdOption from string."""
        assert IdOption("contentId") == IdOption.CONTENT_ID
        assert IdOption("globalId") == IdOption.GLOBAL_ID
