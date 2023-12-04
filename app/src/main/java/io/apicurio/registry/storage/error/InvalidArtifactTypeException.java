package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;


public class InvalidArtifactTypeException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidArtifactTypeException(String message) {
        super(message);
    }

}
