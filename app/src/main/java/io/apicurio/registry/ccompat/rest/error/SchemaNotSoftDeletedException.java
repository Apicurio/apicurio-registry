package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SchemaNotSoftDeletedException extends RegistryException {

    public SchemaNotSoftDeletedException(String message) {
        super(message);
    }

    public SchemaNotSoftDeletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
