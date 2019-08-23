package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;

/**
 * @author Ales Justin
 */
public class RulesException extends RegistryException {
    public RulesException(String message) {
        super(message);
    }

    public RulesException(String message, Throwable cause) {
        super(message, cause);
    }
}
