package io.apicurio.registry.rest;

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.RequestResponseFilters.APPLICATION)
public class CreatedResourceResponseFilter implements ContainerResponseFilter {

    @Inject
    CreatedResourceContext createdResourceContext;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String createdId = createdResourceContext.getCreatedId();
        if (createdId == null || responseContext.getStatus() != Response.Status.OK.getStatusCode()) {
            return;
        }
        responseContext.setStatus(Response.Status.CREATED.getStatusCode());
        responseContext.getHeaders().putSingle(HttpHeaders.LOCATION,
                requestContext.getUriInfo().getAbsolutePathBuilder().path(createdId).build());
    }
}
