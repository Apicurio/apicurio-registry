package io.apicurio.registry.rest;

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Classic RESTEasy (quarkus-resteasy) dispatches its responses through its own writer pipeline,
 * outside of the Vert.x route chain that quarkus.http.enable-compression / compress-media-types
 * act on, so those properties have no effect on REST API responses. This interceptor gzip-compresses
 * response bodies whose media type is in the same compress-media-types list when the client
 * advertises gzip support via Accept-Encoding.
 */
@Provider
@Priority(Priorities.WriterInterceptors.COMPRESSION)
public class HttpCompressionWriterInterceptor implements WriterInterceptor {

    @Context
    HttpHeaders httpHeaders;

    @ConfigProperty(name = "quarkus.http.compress-media-types")
    List<String> compressMediaTypes;

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        if (!isCompressible(context)) {
            context.proceed();
            return;
        }

        context.getHeaders().putSingle(HttpHeaders.CONTENT_ENCODING, "gzip");
        context.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);

        OutputStream originalStream = context.getOutputStream();
        GZIPOutputStream gzipStream = new GZIPOutputStream(originalStream);
        context.setOutputStream(gzipStream);
        try {
            context.proceed();
        } finally {
            gzipStream.finish();
        }
    }

    private boolean isCompressible(WriterInterceptorContext context) {
        String acceptEncoding = httpHeaders.getHeaderString(HttpHeaders.ACCEPT_ENCODING);
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            return false;
        }
        if (context.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING) != null) {
            return false;
        }
        MediaType mediaType = context.getMediaType();
        if (mediaType == null) {
            return false;
        }
        return compressMediaTypes.contains(mediaType.getType() + "/" + mediaType.getSubtype());
    }
}
