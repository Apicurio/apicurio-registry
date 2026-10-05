package io.apicurio.registry.rest;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpCompressionWriterInterceptor#aroundWriteTo}, in particular that cheap,
 * purely local checks (Accept-Encoding, already-encoded body, compressible media type) are
 * evaluated before the dynamic {@code apicurio.rest.compression.enabled} property is resolved, so
 * that property is never consulted for requests/responses that could never be compressed anyway.
 */
class HttpCompressionWriterInterceptorAroundWriteToTest {

    @BeforeAll
    static void setCompressMediaTypes() {
        System.setProperty("quarkus.http.compress-media-types", "application/json");
    }

    @AfterAll
    static void clearCompressMediaTypes() {
        System.clearProperty("quarkus.http.compress-media-types");
    }

    private static WriterInterceptorContext mockContext(MediaType mediaType) throws Exception {
        WriterInterceptorContext context = mock(WriterInterceptorContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(context.getHeaders()).thenReturn(headers);
        when(context.getMediaType()).thenReturn(mediaType);
        when(context.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        return context;
    }

    private static HttpCompressionWriterInterceptor interceptorWithAcceptEncoding(String acceptEncoding,
            RestConfig restConfig) {
        HttpCompressionWriterInterceptor interceptor = new HttpCompressionWriterInterceptor();
        HttpHeaders httpHeaders = mock(HttpHeaders.class);
        when(httpHeaders.getHeaderString(HttpHeaders.ACCEPT_ENCODING)).thenReturn(acceptEncoding);
        interceptor.httpHeaders = httpHeaders;
        interceptor.restConfig = restConfig;
        return interceptor;
    }

    @Test
    void doesNotResolveCompressionConfig_whenClientDoesNotAcceptGzip() throws Exception {
        RestConfig restConfig = mock(RestConfig.class);
        HttpCompressionWriterInterceptor interceptor = interceptorWithAcceptEncoding(null, restConfig);
        WriterInterceptorContext context = mockContext(MediaType.APPLICATION_JSON_TYPE);

        interceptor.aroundWriteTo(context);

        verify(context).proceed();
        verify(restConfig, never()).isCompressionEnabled();
        assertNull(context.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    void doesNotResolveCompressionConfig_whenAlreadyEncoded() throws Exception {
        RestConfig restConfig = mock(RestConfig.class);
        HttpCompressionWriterInterceptor interceptor = interceptorWithAcceptEncoding("gzip", restConfig);
        WriterInterceptorContext context = mockContext(MediaType.APPLICATION_JSON_TYPE);
        context.getHeaders().putSingle(HttpHeaders.CONTENT_ENCODING, "br");

        interceptor.aroundWriteTo(context);

        verify(context).proceed();
        verify(restConfig, never()).isCompressionEnabled();
    }

    @Test
    void doesNotResolveCompressionConfig_whenMediaTypeNotCompressible() throws Exception {
        RestConfig restConfig = mock(RestConfig.class);
        HttpCompressionWriterInterceptor interceptor = interceptorWithAcceptEncoding("gzip", restConfig);
        WriterInterceptorContext context = mockContext(MediaType.TEXT_PLAIN_TYPE);

        interceptor.aroundWriteTo(context);

        verify(context).proceed();
        verify(restConfig, never()).isCompressionEnabled();
    }

    @Test
    void resolvesCompressionConfig_andCompresses_whenEligibleAndEnabled() throws Exception {
        RestConfig restConfig = mock(RestConfig.class);
        when(restConfig.isCompressionEnabled()).thenReturn(true);
        HttpCompressionWriterInterceptor interceptor = interceptorWithAcceptEncoding("gzip", restConfig);
        WriterInterceptorContext context = mockContext(MediaType.APPLICATION_JSON_TYPE);

        interceptor.aroundWriteTo(context);

        verify(context).proceed();
        verify(restConfig).isCompressionEnabled();
        assertEquals("gzip", context.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    void resolvesCompressionConfig_butSkipsCompression_whenDisabled() throws Exception {
        RestConfig restConfig = mock(RestConfig.class);
        when(restConfig.isCompressionEnabled()).thenReturn(false);
        HttpCompressionWriterInterceptor interceptor = interceptorWithAcceptEncoding("gzip", restConfig);
        WriterInterceptorContext context = mockContext(MediaType.APPLICATION_JSON_TYPE);

        interceptor.aroundWriteTo(context);

        verify(context).proceed();
        verify(restConfig).isCompressionEnabled();
        assertNull(context.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
    }
}
