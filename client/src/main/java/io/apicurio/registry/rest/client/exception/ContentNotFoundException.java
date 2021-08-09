package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

public class ContentNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public ContentNotFoundException(Error error) {
        super(error);
    }
}
