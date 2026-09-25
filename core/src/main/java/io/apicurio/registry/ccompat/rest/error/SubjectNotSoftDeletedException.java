package io.apicurio.registry.ccompat.rest.error;

import io.apicurio.registry.types.RegistryException;

public class SubjectNotSoftDeletedException extends RegistryException {

    public SubjectNotSoftDeletedException(String message) {
        super(message);
    }

    public SubjectNotSoftDeletedException(String message, Throwable cause) {
        super(message, cause);
    }

}
