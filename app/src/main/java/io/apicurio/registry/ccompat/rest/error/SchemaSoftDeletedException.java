package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SchemaSoftDeletedException extends RegistryException {

    public SchemaSoftDeletedException(String message) {
        super(message);
    }

    public SchemaSoftDeletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
