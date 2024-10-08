package io.apicurio.registry.rest;

import io.quarkus.security.UnauthorizedException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFailedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Inject
    RegistryExceptionMapper exceptionMapperService;

    @Override
    public Response toResponse(UnauthorizedException exception) {
        Response errorHttpResponse = exceptionMapperService.toResponse(exception);
        return Response.status(401).entity(errorHttpResponse).build();
    }
}
