package io.apicurio.registry.events.dto;

/**
 * CloudEvents {@code type} attribute values emitted by the registry, covering artifact lifecycle,
 * version operations, and rule violations. Each constant's {@link #type()} follows the CloudEvents
 * reverse-DNS naming convention.
 */
public enum CloudEventType {

    ARTIFACT_CREATED("io.apicurio.registry.artifact.created"),

    ARTIFACT_UPDATED("io.apicurio.registry.artifact.updated"),

    ARTIFACT_DEPRECATED("io.apicurio.registry.artifact.deprecated"),

    ARTIFACT_DELETED("io.apicurio.registry.artifact.deleted"),

    ARTIFACT_VERSION_PUBLISHED("io.apicurio.registry.artifact.version.published"),

    ARTIFACT_VERSION_STATE_CHANGED("io.apicurio.registry.artifact.version.state-changed"),

    RULE_VIOLATED("io.apicurio.registry.rule.violated");

    private final String type;

    CloudEventType(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
