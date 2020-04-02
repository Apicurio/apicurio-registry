package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following errors in the compat API:
 * - 409 Conflict – Incompatible Avro schema
 */
public class ConflictException extends RegistryException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
