package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

public class ParametersConflictException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public ParametersConflictException(Error error) {
        super(error);
    }
}
