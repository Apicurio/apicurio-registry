package io.apicurio.registry.services.http;

import io.apicurio.registry.iceberg.metrics.IcebergMetricsService;
import io.apicurio.registry.iceberg.rest.v1.beans.ErrorModel;
import io.apicurio.registry.iceberg.rest.v1.beans.IcebergErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private static void assertError(Response response, int status, String type, String message) {
        assertEquals(status, response.getStatus());
        ErrorModel error = ((IcebergErrorResponse) response.getEntity()).getError();
        assertEquals(status, (int) error.getCode());
        assertEquals(type, error.getType());
        assertEquals(message, error.getMessage());
    }
}
