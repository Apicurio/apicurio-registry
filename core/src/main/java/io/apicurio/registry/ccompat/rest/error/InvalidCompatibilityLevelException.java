package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class InvalidCompatibilityLevelException extends RegistryException {

    private static final long serialVersionUID = -2885498236842555938L;

    public InvalidCompatibilityLevelException(String message) {
        super(message);
    }
}
