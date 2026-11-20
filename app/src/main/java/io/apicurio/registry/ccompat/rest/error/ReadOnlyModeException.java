package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

/**
 * This exception covers the following error in the compat API:
 * - Error code 42205 – Operation not permitted (Subject is in read-only mode)
 */
public class ReadOnlyModeException extends RegistryException {

    private static final long serialVersionUID = 1L;

    public ReadOnlyModeException(String message) {
        super(message);
    }
}
