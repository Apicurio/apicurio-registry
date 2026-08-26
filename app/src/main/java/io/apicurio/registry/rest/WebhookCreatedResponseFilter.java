package io.apicurio.registry.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebhookCreatedResponseFilter implements ContainerResponseFilter {

    private static final String WEBHOOKS_PATH = "/admin/webhooks";

    @Override
    public void filter(ContainerRequestContext requestContext,
            ContainerResponseContext responseContext) {

        if ("POST".equalsIgnoreCase(requestContext.getMethod())
                && requestContext.getUriInfo().getPath().endsWith(WEBHOOKS_PATH)
                && responseContext.getStatus() == 200) {
            responseContext.setStatus(201);
        }
    }
}
