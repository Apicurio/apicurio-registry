package io.apicurio.registry.services.http;

import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v3.beans.ProblemDetails;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.liveness.LivenessUtil;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

public class ExceptionMapperTest {

    private CCompatExceptionMapperService ccompatMapper;
    private CoreRegistryExceptionMapperService coreMapper;
    private CoreV2RegistryExceptionMapperService coreV2Mapper;

    private HttpStatusCodeMap codeMap;
    private ResponseErrorLivenessCheck liveness;
    private LivenessUtil livenessUtil;
    private Logger log;

    @BeforeEach
    public void setup() {
        codeMap = Mockito.mock(HttpStatusCodeMap.class);
        liveness = Mockito.mock(ResponseErrorLivenessCheck.class);
        livenessUtil = Mockito.mock(LivenessUtil.class);
        log = Mockito.mock(Logger.class);

        // Standard setup: unmapped exception should return HTTP_INTERNAL_ERROR from codeMap
        Mockito.when(codeMap.getCode(Mockito.any())).thenReturn(HTTP_INTERNAL_ERROR);

        ccompatMapper = new CCompatExceptionMapperService();
        ccompatMapper.codeMap = codeMap;
        ccompatMapper.liveness = liveness;
        ccompatMapper.livenessUtil = livenessUtil;
        ccompatMapper.log = log;
        ccompatMapper.includeStackTrace = false;

        coreMapper = new CoreRegistryExceptionMapperService();
        coreMapper.codeMap = codeMap;
        coreMapper.liveness = liveness;
        coreMapper.livenessUtil = livenessUtil;
        coreMapper.log = log;
        coreMapper.includeStackTrace = false;

        coreV2Mapper = new CoreV2RegistryExceptionMapperService();
        coreV2Mapper.codeMap = codeMap;
        coreV2Mapper.liveness = liveness;
        coreV2Mapper.livenessUtil = livenessUtil;
        coreV2Mapper.log = log;
        coreV2Mapper.includeStackTrace = false;
    }

    @Test
    public void testCCompatMapperSanitization() {
        // Test with unmapped exception (which results in HTTP_INTERNAL_ERROR)
        RuntimeException unmappedException = new RuntimeException("Sensitive database syntax error info");
        Response response = ccompatMapper.mapException(unmappedException);

        Assertions.assertEquals(500, response.getStatus());
        Error error = (Error) response.getEntity();
        Assertions.assertNotNull(error);
        Assertions.assertEquals("An unexpected error occurred.", error.getMessage());
    }

    @Test
    public void testCCompatMapperNormalization() {
        // Test code normalization where code <= 0 (e.g. code is 0 because map returned 0 or uninitialized)
        Mockito.when(codeMap.getCode(Mockito.any())).thenReturn(0);

        RuntimeException exception = new RuntimeException("Another sensitive DB message");
        Response response = ccompatMapper.mapException(exception);

        // Verifies status code normalized to 500
        Assertions.assertEquals(500, response.getStatus());
        Error error = (Error) response.getEntity();
        Assertions.assertNotNull(error);
        Assertions.assertEquals("An unexpected error occurred.", error.getMessage());
    }

    @Test
    public void testCoreMapperSanitization() {
        RuntimeException unmappedException = new RuntimeException("Sensitive DB columns");
        Response response = coreMapper.mapException(unmappedException);

        Assertions.assertEquals(500, response.getStatus());
        ProblemDetails details = (ProblemDetails) response.getEntity();
        Assertions.assertNotNull(details);
        Assertions.assertEquals("Internal Server Error", details.getTitle());
        Assertions.assertEquals("An unexpected error occurred.", details.getDetail());
        Assertions.assertEquals("InternalError", details.getName());
    }
}
