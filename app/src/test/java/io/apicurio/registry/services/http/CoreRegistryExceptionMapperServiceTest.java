package io.apicurio.registry.services.http;

import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.ProblemDetails;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

public class CoreRegistryExceptionMapperServiceTest {

    private CoreRegistryExceptionMapperService mapper;

    @BeforeEach
    void setUp() {
        HttpStatusCodeMap httpStatusCodeMap = new HttpStatusCodeMap();
        httpStatusCodeMap.restConfig = mock(RestConfig.class);
        httpStatusCodeMap.init();

        mapper = new CoreRegistryExceptionMapperService();
        mapper.log = mock(Logger.class);
        mapper.liveness = mock(ResponseErrorLivenessCheck.class);
        mapper.livenessUtil = mock(LivenessUtil.class);
        mapper.codeMap = httpStatusCodeMap;
    }

    @Test
    void knownExceptionDoesNotExposeItsClassName() {
        ArtifactNotFoundException exception = new ArtifactNotFoundException("default", "orders");

        try (Response response = mapper.mapException(exception)) {
            ProblemDetails details = (ProblemDetails) response.getEntity();

            assertEquals(HTTP_NOT_FOUND, response.getStatus());
            assertEquals("ARTIFACT_NOT_FOUND", details.getName());
            assertEquals(exception.getMessage(), details.getDetail());
            assertFalse(details.getDetail().contains(exception.getClass().getSimpleName()));
        }
    }

    @Test
    void unexpectedExceptionUsesGenericPublicResponse() {
        RuntimeException exception = new RuntimeException("Database host db.internal:5432 is unavailable");

        try (Response response = mapper.mapException(exception)) {
            ProblemDetails details = (ProblemDetails) response.getEntity();

            assertEquals(HTTP_INTERNAL_ERROR, response.getStatus());
            assertEquals("INTERNAL_SERVER_ERROR", details.getName());
            assertEquals("Internal server error", details.getTitle());
            assertEquals("An unexpected server error occurred.", details.getDetail());
            assertFalse(details.getDetail().contains(exception.getMessage()));
        }
    }

    @Test
    void exceptionWithoutMessageUsesPublicFallbackDetail() {
        try (Response response = mapper.mapException(new SilentBadRequestException())) {
            ProblemDetails details = (ProblemDetails) response.getEntity();

            assertEquals(HTTP_BAD_REQUEST, response.getStatus());
            assertEquals("BAD_REQUEST", details.getName());
            assertEquals("The request could not be completed.", details.getTitle());
            assertEquals("The request could not be completed.", details.getDetail());
        }
    }

    @Test
    void wrappedClientErrorDoesNotExposeRootCause() {
        String internalMessage = "com.fasterxml.jackson.databind.exc.InvalidFormatException at db.internal:5432";
        BadRequestException exception = new BadRequestException("The request payload is invalid.",
                new IllegalStateException(internalMessage));

        try (Response response = mapper.mapException(exception)) {
            ProblemDetails details = (ProblemDetails) response.getEntity();

            assertEquals(HTTP_BAD_REQUEST, response.getStatus());
            assertEquals("BAD_REQUEST", details.getName());
            assertEquals("The request payload is invalid.", details.getTitle());
            assertEquals("The request payload is invalid.", details.getDetail());
            assertFalse(details.getDetail().contains(internalMessage));
        }
    }

    private static final class SilentBadRequestException extends BadRequestException {

        @Override
        public String getLocalizedMessage() {
            return null;
        }
    }
}
