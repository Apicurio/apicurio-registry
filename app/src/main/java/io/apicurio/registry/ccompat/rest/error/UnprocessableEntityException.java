package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following errors in the compat API: - Error code 42201 – Invalid schema - Error
 * code 42202 – Invalid schema version - Error code 42203 – Invalid compatibility level
 */
public class UnprocessableEntityException extends RegistryException {

    private static final long serialVersionUID = 1791019542026597523L;

    public UnprocessableEntityException(String message) {
        super(message);
    }

    public UnprocessableEntityException(Throwable cause) {
        super(cause);
    }
}
