package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SubjectSoftDeletedException extends RegistryException {
    public SubjectSoftDeletedException(String message) {
        super(message);
    }

    public SubjectSoftDeletedException(String message, Throwable cause) {
        super(message, cause);
    }

}
