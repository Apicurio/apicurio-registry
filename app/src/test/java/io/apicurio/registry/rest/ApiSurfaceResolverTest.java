package io.apicurio.registry.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ApiSurfaceResolverTest {

    private final ApiSurfaceResolver resolver = new ApiSurfaceResolver();

    @Test
    void testResolveFromUriString() {
        assertEquals(ApiSurface.V3, resolver.resolve((String) null));
        assertEquals(ApiSurface.V3, resolver.resolve(""));
        assertEquals(ApiSurface.V3, resolver.resolve("/apis/registry/v3/groups"));
        assertEquals(ApiSurface.V3, resolver.resolve("/apis/registry/v3/artifacts/someArtifactId"));
        assertEquals(ApiSurface.V3, resolver.resolve("/ui/artifacts"));

        assertEquals(ApiSurface.V2, resolver.resolve("/apis/registry/v2/artifacts"));
        assertEquals(ApiSurface.V2, resolver.resolve("/apis/registry/v2/ids/globalIds/1"));

        assertEquals(ApiSurface.CCOMPAT, resolver.resolve("/apis/ccompat/v7/subjects"));
        assertEquals(ApiSurface.CCOMPAT, resolver.resolve("/apis/ccompat/v8/schemas"));

        assertEquals(ApiSurface.ICEBERG, resolver.resolve("/apis/iceberg/v1/config"));
        assertEquals(ApiSurface.ICEBERG, resolver.resolve("/apis/iceberg/v1/namespaces"));
    }

    @Test
    void testResolveFromHttpServletRequest() {
        assertEquals(ApiSurface.V3, resolver.resolve((HttpServletRequest) null));

        HttpServletRequest v3Request = Mockito.mock(HttpServletRequest.class);
        when(v3Request.getRequestURI()).thenReturn("/apis/registry/v3/groups");
        assertEquals(ApiSurface.V3, resolver.resolve(v3Request));

        HttpServletRequest v2Request = Mockito.mock(HttpServletRequest.class);
        when(v2Request.getRequestURI()).thenReturn("/apis/registry/v2/artifacts");
        assertEquals(ApiSurface.V2, resolver.resolve(v2Request));

        HttpServletRequest ccompatRequest = Mockito.mock(HttpServletRequest.class);
        when(ccompatRequest.getRequestURI()).thenReturn("/apis/ccompat/v7/subjects");
        assertEquals(ApiSurface.CCOMPAT, resolver.resolve(ccompatRequest));

        HttpServletRequest icebergRequest = Mockito.mock(HttpServletRequest.class);
        when(icebergRequest.getRequestURI()).thenReturn("/apis/iceberg/v1/config");
        assertEquals(ApiSurface.ICEBERG, resolver.resolve(icebergRequest));
    }
}
