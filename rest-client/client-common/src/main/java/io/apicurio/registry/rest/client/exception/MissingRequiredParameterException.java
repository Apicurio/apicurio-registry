package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

public class MissingRequiredParameterException extends BadRequestException {

    private static final long serialVersionUID = 1L;

    public MissingRequiredParameterException(Error error) {
        super(error);
    }
}
