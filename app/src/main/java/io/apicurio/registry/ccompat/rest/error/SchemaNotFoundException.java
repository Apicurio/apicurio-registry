package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SchemaNotFoundException extends RegistryException {
    public SchemaNotFoundException(String message) {
        super(message);
    }

    public SchemaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
