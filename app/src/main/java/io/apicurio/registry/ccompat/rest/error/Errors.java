package io.apicurio.registry.ccompat.rest.error;

import jakarta.ws.rs.NotFoundException;


public class Errors {

    public static void operationNotSupported() {
        throw new NotFoundException("Operation not supported.");
    }
}
