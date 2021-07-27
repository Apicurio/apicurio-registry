package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

public class LogConfigurationNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public LogConfigurationNotFoundException(Error error) {
        super(error);
    }
}
