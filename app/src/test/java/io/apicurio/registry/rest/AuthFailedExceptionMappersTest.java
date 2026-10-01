package io.apicurio.registry.rest;

import io.apicurio.registry.rest.v2.beans.AuthError;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v3.beans.ProblemDetails;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFailedExceptionMappersTest {

    private ApiSurfaceResolver resolver;
    private RegistryExceptionMapper registryExceptionMapper;
    private HttpServletRequest request;

    private AuthorizationFailedExceptionMapper authzMapper;
    private AuthenticationFailedExceptionMapper authnMapper;

    @BeforeEach
    void setUp() {
        resolver = new ApiSurfaceResolver();
        registryExceptionMapper = mock(RegistryExceptionMapper.class);
        request = mock(HttpServletRequest.class);

        authzMapper = new AuthorizationFailedExceptionMapper();
        authzMapper.apiSurfaceResolver = resolver;
        authzMapper.exceptionMapperService = registryExceptionMapper;
        authzMapper.request = request;

        authnMapper = new AuthenticationFailedExceptionMapper();
        authnMapper.apiSurfaceResolver = resolver;
        authnMapper.exceptionMapperService = registryExceptionMapper;
        authnMapper.request = request;
    }

    @Test
    void testAuthorizationFailedOnV2WithAuthError() {
        ForbiddenException exception = new ForbiddenException("forbidden");
        AuthError authError = new AuthError();
        Response mockResponse = Response.status(403)
                .entity(authError)
                .type(MediaType.APPLICATION_JSON)
                .build();

        when(request.getRequestURI()).thenReturn("/apis/registry/v2/artifacts");
        when(registryExceptionMapper.toResponse(exception)).thenReturn(mockResponse);

        Response response = authzMapper.toResponse(exception);
        assertEquals(403, response.getStatus());
        assertSame(authError, response.getEntity());
    }

    @Test
    void testAuthorizationFailedOnV3WithProblemDetails() {
        ForbiddenException exception = new ForbiddenException("forbidden");
        ProblemDetails problemDetails = new ProblemDetails();
        Response mockResponse = Response.status(403)
                .entity(problemDetails)
                .type(MediaType.APPLICATION_JSON)
                .build();

        when(request.getRequestURI()).thenReturn("/apis/registry/v3/groups");
        when(registryExceptionMapper.toResponse(exception)).thenReturn(mockResponse);

        Response response = authzMapper.toResponse(exception);
        assertEquals(403, response.getStatus());
        assertSame(problemDetails, response.getEntity());
        assertEquals(403, problemDetails.getStatus());
    }

    @Test
    void testAuthenticationFailedOnV2WithError() {
        UnauthorizedException exception = new UnauthorizedException("unauthorized");
        Error error = new Error();
        Response mockResponse = Response.status(401)
                .entity(error)
                .type(MediaType.APPLICATION_JSON)
                .build();

        when(request.getRequestURI()).thenReturn("/apis/registry/v2/artifacts");
        when(registryExceptionMapper.toResponse(exception)).thenReturn(mockResponse);

        Response response = authnMapper.toResponse(exception);
        assertEquals(401, response.getStatus());
        assertSame(error, response.getEntity());
    }

    @Test
    void testAuthenticationFailedOnV3WithProblemDetails() {
        UnauthorizedException exception = new UnauthorizedException("unauthorized");
        ProblemDetails problemDetails = new ProblemDetails();
        Response mockResponse = Response.status(401)
                .entity(problemDetails)
                .type(MediaType.APPLICATION_JSON)
                .build();

        when(request.getRequestURI()).thenReturn("/apis/registry/v3/groups");
        when(registryExceptionMapper.toResponse(exception)).thenReturn(mockResponse);

        Response response = authnMapper.toResponse(exception);
        assertEquals(401, response.getStatus());
        assertSame(problemDetails, response.getEntity());
        assertEquals(401, problemDetails.getStatus());
    }
}
