package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SchemaNotSoftDeleted extends RegistryException {

    public SchemaNotSoftDeleted(String message) {
        super(message);
    }

    public SchemaNotSoftDeleted(String message, Throwable cause) {
        super(message, cause);
    }
}
