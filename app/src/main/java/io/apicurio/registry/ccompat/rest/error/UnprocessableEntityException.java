package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following errors in the compat API:
 * - Error code 42201 – Invalid Avro schema
 * - Error code 42202 – Invalid schema version
 * - Error code 42203 – Invalid compatibility level
 */
public class UnprocessableEntityException extends RegistryException {


    public UnprocessableEntityException(String message) {
        super(message);
    }

    public UnprocessableEntityException(String message, Throwable cause) {
        super(message, cause);
    }
}
