package io.apicurio.registry.rest.cache;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps CacheNotModifiedException to 304 Not Modified responses.
 * This allows interceptors to abort request processing when If-None-Match matches
 * without executing the full method body.
 */
@Provider
public class CacheNotModifiedExceptionMapper implements ExceptionMapper<CacheNotModifiedException> {

    @Override
    public Response toResponse(CacheNotModifiedException exception) {
        return Response.notModified().build();
    }
}
