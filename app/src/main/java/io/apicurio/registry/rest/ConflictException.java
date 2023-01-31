package io.apicurio.registry.rest;

import io.apicurio.registry.types.RegistryException;

public class ConflictException extends RegistryException {

    private static final long serialVersionUID = -210884502298134398L;

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
