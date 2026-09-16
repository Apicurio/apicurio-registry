package io.apicurio.registry.rest;

import io.apicurio.registry.rest.v2.beans.AuthError;
import io.apicurio.registry.rest.v3.beans.ProblemDetails;
import io.quarkus.security.ForbiddenException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Exception mapper for authorization failures (403 Forbidden).
 * Handles both V2 API (Error beans) and V3 API (ProblemDetails beans).
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFailedExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Inject
    RegistryExceptionMapper exceptionMapperService;

    @Inject
    ApiSurfaceResolver apiSurfaceResolver;

    @Context
    HttpServletRequest request;

    @Override
    public Response toResponse(ForbiddenException exception) {
        Response errorHttpResponse = exceptionMapperService.toResponse(exception);
        Object entity = errorHttpResponse.getEntity();

        // Check if this is a V2 API endpoint - if so, we need to handle V2 Error beans
        if (apiSurfaceResolver.resolve(request) == ApiSurface.V2 && entity instanceof AuthError) {
            return Response.status(403).entity(entity).type(errorHttpResponse.getMediaType()).build();
        } else if (entity instanceof ProblemDetails) {
            // V3 API - use ProblemDetails
            ProblemDetails problemDetails = (ProblemDetails) entity;
            problemDetails.setStatus(403);
            return Response.status(403).entity(problemDetails).type(errorHttpResponse.getMediaType()).build();
        } else {
            // Fallback - just set status to 403 and return the entity as-is
            return Response.status(403).entity(entity).type(errorHttpResponse.getMediaType()).build();
        }
    }
}
