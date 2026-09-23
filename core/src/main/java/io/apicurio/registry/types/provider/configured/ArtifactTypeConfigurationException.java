package io.apicurio.registry.types.provider.configured;

public class ArtifactTypeConfigurationException extends RuntimeException {
    public ArtifactTypeConfigurationException(String message) {
        super(message);
    }

    public ArtifactTypeConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
