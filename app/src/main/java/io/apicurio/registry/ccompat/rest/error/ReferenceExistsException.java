package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class ReferenceExistsException extends RegistryException {

    public ReferenceExistsException(String message) {
        super(message);
    }

    public ReferenceExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
