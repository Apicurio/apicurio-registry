package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following errors in the compat API:
 * - 409 Conflict – Incompatible schema
 */
public class ConflictException extends RegistryException {

    private static final long serialVersionUID = 5511072429790259605L;


    public ConflictException(Throwable cause) {
        super(cause);
    }
}
