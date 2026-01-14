"""Configuration for Apicurio Registry serializers and deserializers."""

from dataclasses import dataclass, field
from typing import Optional, Dict, Any

from apicurio_registry_serdes.config.id_option import IdOption


@dataclass
class SerdeConfig:
    """
    Configuration for Apicurio Registry serializers and deserializers.

    This class contains all the configuration options needed to connect to
    the registry and control serialization/deserialization behavior.

    Attributes:
        registry_url: The base URL of the Apicurio Registry API (v3).
            Example: "http://localhost:8080/apis/registry/v3"
        auto_register: If True, automatically register schemas that don't exist.
        auto_register_if_exists: Behavior when artifact already exists during auto-registration.
            One of: "FAIL", "CREATE_VERSION", "FIND_OR_CREATE_VERSION"
        find_latest: If True, always use the latest version of the schema.
        use_id: Which ID to use for schema lookup (contentId or globalId).
        artifact_group_id: Explicit group ID for the artifact (overrides strategy).
        artifact_id: Explicit artifact ID (overrides strategy).
        artifact_version: Explicit version (overrides strategy).
        check_period_ms: How often to refresh cached schemas (in milliseconds).
        retry_count: Number of retries for failed registry requests.
        retry_backoff_ms: Delay between retries (in milliseconds).
        auth_username: Username for basic authentication.
        auth_password: Password for basic authentication.
        auth_token: Bearer token for authentication.
        headers: Additional HTTP headers to include in registry requests.
    """

    registry_url: str
    auto_register: bool = False
    auto_register_if_exists: str = "FIND_OR_CREATE_VERSION"
    find_latest: bool = False
    use_id: IdOption = IdOption.CONTENT_ID
    artifact_group_id: Optional[str] = None
    artifact_id: Optional[str] = None
    artifact_version: Optional[str] = None
    check_period_ms: int = 30000
    retry_count: int = 3
    retry_backoff_ms: int = 300
    auth_username: Optional[str] = None
    auth_password: Optional[str] = None
    auth_token: Optional[str] = None
    headers: Dict[str, str] = field(default_factory=dict)
    dereference_schema: bool = False
    validation_enabled: bool = True

    def __post_init__(self) -> None:
        """Validate configuration after initialization."""
        if not self.registry_url:
            raise ValueError("registry_url is required")

        # Normalize URL (remove trailing slash)
        self.registry_url = self.registry_url.rstrip("/")

        # Validate auto_register_if_exists
        valid_if_exists = {"FAIL", "CREATE_VERSION", "FIND_OR_CREATE_VERSION"}
        if self.auto_register_if_exists not in valid_if_exists:
            raise ValueError(
                f"auto_register_if_exists must be one of {valid_if_exists}, "
                f"got {self.auto_register_if_exists}"
            )

        # Convert string to IdOption if needed
        if isinstance(self.use_id, str):
            self.use_id = IdOption(self.use_id)

    @classmethod
    def from_dict(cls, config: Dict[str, Any]) -> "SerdeConfig":
        """
        Create a SerdeConfig from a dictionary.

        This is useful for integration with Kafka configuration where
        settings are passed as dictionaries.

        Args:
            config: Dictionary containing configuration values.

        Returns:
            A new SerdeConfig instance.
        """
        # Map common Kafka-style config keys to our attribute names
        key_mapping = {
            "apicurio.registry.url": "registry_url",
            "apicurio.registry.auto-register": "auto_register",
            "apicurio.registry.auto-register.if-exists": "auto_register_if_exists",
            "apicurio.registry.find-latest": "find_latest",
            "apicurio.registry.use-id": "use_id",
            "apicurio.registry.artifact.group-id": "artifact_group_id",
            "apicurio.registry.artifact.artifact-id": "artifact_id",
            "apicurio.registry.artifact.version": "artifact_version",
            "apicurio.registry.check-period-ms": "check_period_ms",
            "apicurio.registry.retry-count": "retry_count",
            "apicurio.registry.retry-backoff-ms": "retry_backoff_ms",
            "apicurio.registry.auth.username": "auth_username",
            "apicurio.registry.auth.password": "auth_password",
            "apicurio.registry.auth.token": "auth_token",
            "apicurio.registry.dereference-schema": "dereference_schema",
            "apicurio.registry.serde.validation-enabled": "validation_enabled",
        }

        # Build kwargs from mapped config
        kwargs: Dict[str, Any] = {}
        for kafka_key, attr_name in key_mapping.items():
            if kafka_key in config:
                kwargs[attr_name] = config[kafka_key]

        # Also accept direct attribute names
        valid_attrs = {f.name for f in cls.__dataclass_fields__.values()}
        for key, value in config.items():
            if key in valid_attrs and key not in kwargs:
                kwargs[key] = value

        return cls(**kwargs)
