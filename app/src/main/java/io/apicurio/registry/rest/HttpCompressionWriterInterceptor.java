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
import java.util.Locale;
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
        if (!clientAcceptsGzip(acceptEncoding)) {
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

    /**
     * Parses the Accept-Encoding header per RFC 9110 §12.5.3, tokenizing on commas and respecting
     * quality values. A {@code gzip;q=0} token is an explicit refusal and returns {@code false}.
     */
    static boolean clientAcceptsGzip(String acceptEncoding) {
        if (acceptEncoding == null) {
            return false;
        }
        for (String token : acceptEncoding.split(",")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("gzip")) {
                String remainder = trimmed.substring(4).trim();
                if (remainder.isEmpty()) {
                    return true;
                }
                if (remainder.startsWith(";")) {
                    return isQValueAcceptable(remainder.substring(1));
                }
                // "gzip" followed by non-semicolon, non-empty — not a match (e.g., "gzipx")
            }
        }
        return false;
    }

    /**
     * Parses parameters — looks for q=<value>.
     * Returns true if q is greater than 0.0 or malformed, false if q is 0.0 or 0.
     */
    private static boolean isQValueAcceptable(String parameters) {
        for (String param : parameters.split(";")) {
            String p = param.trim();
            if (p.startsWith("q=")) {
                try {
                    return Double.parseDouble(p.substring(2).trim()) > 0.0;
                } catch (NumberFormatException e) {
                    return true; // Malformed q-value, treat as acceptance
                }
            }
        }
        return true; // Parameters present but no q-value, defaults to q=1
    }
}
