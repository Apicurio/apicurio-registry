package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RegistryException;


public class InvalidGroupIdException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public InvalidGroupIdException(String message) {
        super(message);
    }

}