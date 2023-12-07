package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;

/**
 * Similar to "UnprocessableEntityException" but bound to the artifact type utils tools
 *
 */
public class UnprocessableSchemaException extends RegistryException {

    private static final long serialVersionUID = 1791019542026597523L;

    public UnprocessableSchemaException(String message) {
        super(message);
    }

    public UnprocessableSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
