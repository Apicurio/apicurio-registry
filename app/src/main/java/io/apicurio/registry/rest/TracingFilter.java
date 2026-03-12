package io.apicurio.registry.rest;

import io.apicurio.registry.util.Priorities;
import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS filter that enriches OpenTelemetry spans with Apicurio Registry-specific attributes.
 * REST endpoints are automatically traced by Quarkus OpenTelemetry extension.
 * This filter adds custom attributes for better observability and debugging.
 */
@Provider
@Priority(Priorities.RequestResponseFilters.APPLICATION)
public class TracingFilter implements ContainerRequestFilter {

    private static final String HEADER_GROUP_ID = "X-Registry-GroupId";
    private static final String HEADER_ARTIFACT_ID = "X-Registry-ArtifactId";
    private static final String HEADER_VERSION = "X-Registry-Version";
    private static final String HEADER_ARTIFACT_TYPE = "X-Registry-ArtifactType";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            // Add Apicurio-specific trace attributes from headers
            addAttributeIfPresent(currentSpan, requestContext, HEADER_GROUP_ID, "apicurio.groupId");
            addAttributeIfPresent(currentSpan, requestContext, HEADER_ARTIFACT_ID, "apicurio.artifactId");
            addAttributeIfPresent(currentSpan, requestContext, HEADER_VERSION, "apicurio.version");
            addAttributeIfPresent(currentSpan, requestContext, HEADER_ARTIFACT_TYPE, "apicurio.artifactType");

            // Add request path information
            String path = requestContext.getUriInfo().getPath();
            if (path != null) {
                currentSpan.setAttribute("apicurio.request.path", path);
            }

            // Extract group and artifact IDs from path parameters if available
            String groupId = extractPathParam(requestContext, "groupId");
            String artifactId = extractPathParam(requestContext, "artifactId");
            String version = extractPathParam(requestContext, "version");

            if (groupId != null) {
                currentSpan.setAttribute("apicurio.path.groupId", groupId);
            }
            if (artifactId != null) {
                currentSpan.setAttribute("apicurio.path.artifactId", artifactId);
            }
            if (version != null) {
                currentSpan.setAttribute("apicurio.path.version", version);
            }
        }
    }

    private void addAttributeIfPresent(Span span, ContainerRequestContext context,
                                       String headerName, String attributeName) {
        String value = context.getHeaderString(headerName);
        if (value != null && !value.isEmpty()) {
            span.setAttribute(attributeName, value);
        }
    }

    private String extractPathParam(ContainerRequestContext context, String paramName) {
        try {
            return context.getUriInfo().getPathParameters().getFirst(paramName);
        } catch (Exception e) {
            return null;
        }
    }
}
