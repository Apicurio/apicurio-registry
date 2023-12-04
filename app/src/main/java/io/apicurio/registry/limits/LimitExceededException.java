package io.apicurio.registry.limits;

import io.apicurio.registry.types.RegistryException;


public class LimitExceededException extends RegistryException {

    private static final long serialVersionUID = -8689268705454834808L;

    public LimitExceededException(String message) {
        super(message);
    }

    public LimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
