package io.apicurio.registry.rest;

import io.apicurio.registry.services.http.CCompatExceptionMapperService;
import io.apicurio.registry.services.http.CoreRegistryExceptionMapperService;
import io.apicurio.registry.services.http.CoreV2RegistryExceptionMapperService;
import io.apicurio.registry.services.http.IcebergExceptionMapperService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class RegistryExceptionMapperTest {

    private RegistryExceptionMapper mapper;
    private ApiSurfaceResolver resolver;
    private CoreRegistryExceptionMapperService coreMapper;
    private CoreV2RegistryExceptionMapperService coreV2Mapper;
    private CCompatExceptionMapperService ccompatMapper;
    private IcebergExceptionMapperService icebergMapper;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        mapper = new RegistryExceptionMapper();
        resolver = new ApiSurfaceResolver();
        coreMapper = Mockito.mock(CoreRegistryExceptionMapperService.class);
        coreV2Mapper = Mockito.mock(CoreV2RegistryExceptionMapperService.class);
        ccompatMapper = Mockito.mock(CCompatExceptionMapperService.class);
        icebergMapper = Mockito.mock(IcebergExceptionMapperService.class);
        request = Mockito.mock(HttpServletRequest.class);

        mapper.apiSurfaceResolver = resolver;
        mapper.coreMapper = coreMapper;
        mapper.coreV2Mapper = coreV2Mapper;
        mapper.ccompatMapper = ccompatMapper;
        mapper.icebergMapper = icebergMapper;
        mapper.request = request;
    }

    @Test
    void testGetMapperService() {
        assertSame(coreMapper, mapper.getMapperService(ApiSurface.V3));
        assertSame(coreV2Mapper, mapper.getMapperService(ApiSurface.V2));
        assertSame(ccompatMapper, mapper.getMapperService(ApiSurface.CCOMPAT));
        assertSame(icebergMapper, mapper.getMapperService(ApiSurface.ICEBERG));
    }

    @Test
    void testToResponseDispatchesToV3ByDefault() {
        Throwable exception = new RuntimeException("test v3 error");
        Response expectedResponse = Response.status(500).build();
        when(request.getRequestURI()).thenReturn("/apis/registry/v3/groups");
        when(coreMapper.mapException(exception)).thenReturn(expectedResponse);

        Response actualResponse = mapper.toResponse(exception);
        assertSame(expectedResponse, actualResponse);
    }

    @Test
    void testToResponseDispatchesToV2() {
        Throwable exception = new RuntimeException("test v2 error");
        Response expectedResponse = Response.status(500).build();
        when(request.getRequestURI()).thenReturn("/apis/registry/v2/artifacts");
        when(coreV2Mapper.mapException(exception)).thenReturn(expectedResponse);

        Response actualResponse = mapper.toResponse(exception);
        assertSame(expectedResponse, actualResponse);
    }

    @Test
    void testToResponseDispatchesToCCompat() {
        Throwable exception = new RuntimeException("test ccompat error");
        Response expectedResponse = Response.status(500).build();
        when(request.getRequestURI()).thenReturn("/apis/ccompat/v7/subjects");
        when(ccompatMapper.mapException(exception)).thenReturn(expectedResponse);

        Response actualResponse = mapper.toResponse(exception);
        assertSame(expectedResponse, actualResponse);
    }

    @Test
    void testToResponseDispatchesToIceberg() {
        Throwable exception = new RuntimeException("test iceberg error");
        Response expectedResponse = Response.status(500).build();
        when(request.getRequestURI()).thenReturn("/apis/iceberg/v1/config");
        when(icebergMapper.mapException(exception)).thenReturn(expectedResponse);

        Response actualResponse = mapper.toResponse(exception);
        assertSame(expectedResponse, actualResponse);
    }
}
