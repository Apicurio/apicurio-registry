package io.apicurio.registry.services.http;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.storage.error.AlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ContentAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class HttpStatusCodeMapTest {

    private HttpStatusCodeMap httpStatusCodeMap;

    @BeforeEach
    void setUp() {
        httpStatusCodeMap = new HttpStatusCodeMap();
        httpStatusCodeMap.restConfig = mock(RestConfig.class);
        httpStatusCodeMap.init();
    }

    @Test
    void contentAlreadyExistsExceptionMapsToConflict() {
        assertEquals(HTTP_CONFLICT, httpStatusCodeMap.getCode(ContentAlreadyExistsException.class));
        assertEquals(RegistryErrorCode.CONTENT_ALREADY_EXISTS,
                httpStatusCodeMap.getErrorCode(ContentAlreadyExistsException.class, HTTP_CONFLICT));
    }

    @Test
    void siblingAlreadyExistsExceptionStillMapsToConflict() {
        assertEquals(HTTP_CONFLICT, httpStatusCodeMap.getCode(ArtifactAlreadyExistsException.class));
    }

    @Test
    void unmappedExceptionDefaultsToInternalError() {
        assertEquals(HTTP_INTERNAL_ERROR, httpStatusCodeMap.getCode(RuntimeException.class));
        assertEquals(RegistryErrorCode.INTERNAL_SERVER_ERROR,
                httpStatusCodeMap.getErrorCode(RuntimeException.class, HTTP_INTERNAL_ERROR));
        assertEquals(RegistryErrorCode.NOT_FOUND,
                httpStatusCodeMap.getErrorCode(RuntimeException.class, HTTP_NOT_FOUND));
    }

    @Test
    void unregisteredAlreadyExistsSubclassInheritsParentMapping() {
        assertEquals(HTTP_CONFLICT, httpStatusCodeMap.getCode(UnregisteredAlreadyExistsException.class));
        assertEquals(RegistryErrorCode.ALREADY_EXISTS,
                httpStatusCodeMap.getErrorCode(UnregisteredAlreadyExistsException.class, HTTP_CONFLICT));
    }

    private static final class UnregisteredAlreadyExistsException extends AlreadyExistsException {
        private static final long serialVersionUID = 1L;

        private UnregisteredAlreadyExistsException(String reason) {
            super(reason);
        }
    }
}
