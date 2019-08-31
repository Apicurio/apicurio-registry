package io.apicurio.registry.rules;

import io.apicurio.registry.types.RegistryException;

/**
 * Exception thrown when a configured rule is violated, rejecting an artifact content
 * update.
 * @author Ales Justin
 */
public class RuleViolationException extends RegistryException {
    
    private static final long serialVersionUID = 8437151164241883773L;

    /**
     * Constructor.
     * @param message
     */
    public RuleViolationException(String message) {
        super(message);
    }

    /**
     * Constructor.
     * @param message
     * @param cause
     */
    public RuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
