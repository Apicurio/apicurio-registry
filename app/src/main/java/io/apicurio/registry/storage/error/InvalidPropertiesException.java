package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;

public class InvalidPropertiesException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidPropertiesException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
