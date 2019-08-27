package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;

/**
 * @author Ales Justin
 */
public class RuleViolationException extends RegistryException {
    
    private static final long serialVersionUID = 8437151164241883773L;

    public RuleViolationException(String message) {
        super(message);
    }

    public RuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
