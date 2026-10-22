package io.apicurio.registry.ccompat.rest.v8;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * JAX-RS filter that processes the X-Confluent-Accept-Unknown-Properties header.
 *
 * When this header is set to "true", unknown JSON properties in request bodies should be
 * ignored (forward compatibility feature introduced in Confluent Platform v8).
 *
 * This filter stores the header value in a request context property that can be accessed
 * by message body readers/providers to configure JSON deserialization behavior.
 */
@Provider
@PreMatching
public class AcceptUnknownPropertiesFilter implements ContainerRequestFilter {

    public static final String HEADER_NAME = "X-Confluent-Accept-Unknown-Properties";
    public static final String CONTEXT_PROPERTY = "io.apicurio.registry.ccompat.accept-unknown-properties";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String headerValue = requestContext.getHeaderString(HEADER_NAME);
        boolean acceptUnknownProperties = "true".equalsIgnoreCase(headerValue);
        requestContext.setProperty(CONTEXT_PROPERTY, acceptUnknownProperties);
    }
}
