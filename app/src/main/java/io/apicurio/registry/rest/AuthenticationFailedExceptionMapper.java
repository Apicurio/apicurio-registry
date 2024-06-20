package io.apicurio.registry.rest;

import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public class AuthenticationFailedExceptionMapper implements ExceptionMapper<AuthenticationFailedException> {
    @Override
    public Response toResponse(AuthenticationFailedException exception) {
        return Response.status(401).build();
    }
}
