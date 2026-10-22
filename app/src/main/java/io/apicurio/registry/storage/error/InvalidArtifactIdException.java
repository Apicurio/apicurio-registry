package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

public class InvalidArtifactIdException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidArtifactIdException(String message) {
        super(message);
    }
}