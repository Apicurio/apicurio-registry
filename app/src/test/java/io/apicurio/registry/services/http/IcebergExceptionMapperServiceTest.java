package io.apicurio.registry.services.http;

import io.apicurio.registry.iceberg.metrics.IcebergMetricsService;
import io.apicurio.registry.iceberg.rest.v1.beans.ErrorModel;
import io.apicurio.registry.iceberg.rest.v1.beans.IcebergErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class IcebergExceptionMapperServiceTest {

    private IcebergExceptionMapperService mapper;

    private IcebergMetricsService metricsService;

    @BeforeEach
    void setUp() {
        mapper = new IcebergExceptionMapperService();
        metricsService = mock(IcebergMetricsService.class);
        mapper.metricsService = metricsService;
    }

    @Test
    void badRequestExceptionKeepsItsStatus() {
        Response response = mapper.mapException(new BadRequestException("bad input"));

        assertError(response, 400, "BadRequestException", "bad input");
        verify(metricsService).recordIcebergError("BadRequestException");
    }

    @Test
    void forbiddenExceptionKeepsItsStatus() {
        Response response = mapper.mapException(new ForbiddenException("not allowed"));

        assertError(response, 403, "ForbiddenException", "not allowed");
    }

    @Test
    void serverSideWebApplicationExceptionKeepsItsStatus() {
        Response response = mapper.mapException(new ServiceUnavailableException("storage busy"));

        assertError(response, 503, "ServiceUnavailableException", "storage busy");
    }

    @Test
    void unrecognisedExceptionStillFallsBackToInternalServerError() {
        Response response = mapper.mapException(new IllegalStateException("boom"));

        assertError(response, 500, "InternalServerError", "boom");
    }

    @ParameterizedTest
    @CsvSource({
            "409, ConflictException",
            "404, NotFoundException",
            "429, TooManyRequestsException",
            "418, ClientErrorException",
            "503, InternalServerError"
    })
    void plainWebApplicationExceptionGetsATypeFromItsStatus(int status, String expectedType) {
        WebApplicationException exception = new WebApplicationException(status);

        Response response = mapper.mapException(exception);

        assertError(response, status, expectedType, exception.getMessage());
        verify(metricsService).recordIcebergError(expectedType);
    }

    @Test
    void anonymousSubclassGetsATypeFromItsStatus() {
        Response response = mapper.mapException(new ClientErrorException("clash", Response.Status.CONFLICT) {
        });

        assertError(response, 409, "ConflictException", "clash");
    }

    @Test
    void redirectionIsReturnedAsThrown() {
        URI location = URI.create("http://example.test/next");
        RedirectionException redirection = new RedirectionException(Response.Status.SEE_OTHER, location);

        Response response = mapper.mapException(redirection);

        assertSame(redirection.getResponse(), response);
        assertEquals(303, response.getStatus());
        assertEquals(location, response.getLocation());
        verifyNoInteractions(metricsService);
    }

    @Test
    void notAllowedExceptionKeepsItsAllowHeader() {
        Response response = mapper.mapException(new NotAllowedException("GET"));

        assertError(response, 405, "NotAllowedException", "HTTP 405 Method Not Allowed");
        assertEquals("GET", response.getHeaderString(HttpHeaders.ALLOW));
    }

    @Test
    void serviceUnavailableExceptionKeepsItsRetryAfterHeader() {
        Response response = mapper.mapException(new ServiceUnavailableException(7L));

        assertEquals(503, response.getStatus());
        assertEquals("7", response.getHeaderString(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void headersDescribingTheOriginalBodyAreNotForwarded() {
        Response original = Response.status(400)
                .type(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CONTENT_LENGTH, "99")
                .header(HttpHeaders.ETAG, "\"v1\"")
                .header(HttpHeaders.LAST_MODIFIED, "Wed, 21 Oct 2015 07:28:00 GMT")
                .header("X-Request-Id", "abc-123")
                .entity("a plain text body")
                .build();

        Response response = mapper.mapException(new WebApplicationException(original));

        assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
        assertNull(response.getHeaderString(HttpHeaders.CONTENT_ENCODING));
        assertNull(response.getHeaderString(HttpHeaders.CONTENT_LENGTH));
        assertNull(response.getHeaderString(HttpHeaders.ETAG));
        assertNull(response.getHeaderString(HttpHeaders.LAST_MODIFIED));
        assertEquals("abc-123", response.getHeaderString("X-Request-Id"));
    }

    private static void assertError(Response response, int status, String type, String message) {
        assertEquals(status, response.getStatus());
        ErrorModel error = ((IcebergErrorResponse) response.getEntity()).getError();
        assertEquals(status, (int) error.getCode());
        assertEquals(type, error.getType());
        assertEquals(message, error.getMessage());
    }
}
