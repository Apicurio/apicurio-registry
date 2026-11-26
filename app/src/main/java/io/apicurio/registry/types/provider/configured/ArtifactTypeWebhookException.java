package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.types.RegistryException;

/**
 * Thrown when an artifact type webhook cannot be invoked. The message is generic on purpose: it reaches
 * API clients, so the cause (host names, exception classes) is logged where the failure happens and is not
 * attached to this exception.
 */
public class ArtifactTypeWebhookException extends RegistryException {

    private static final long serialVersionUID = -2236472960713405718L;

    public ArtifactTypeWebhookException(String provider, String artifactType) {
        super("Error invoking " + provider + " webhook for '" + artifactType + "'.");
    }
}
