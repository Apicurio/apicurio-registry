package io.apicurio.registry.rest;

import io.apicurio.registry.services.http.CCompatExceptionMapperService;
import io.apicurio.registry.services.http.CoreRegistryExceptionMapperService;
import io.apicurio.registry.services.http.CoreV2RegistryExceptionMapperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * TODO use v1 beans when appropriate (when handling REST API v1 calls)
 */
@ApplicationScoped
@Provider
public class RegistryExceptionMapper implements ExceptionMapper<Throwable> {

    @Inject
    CoreRegistryExceptionMapperService coreMapper;

    @Inject
    CoreV2RegistryExceptionMapperService coreV2Mapper;

    @Inject
    CCompatExceptionMapperService ccompatMapper;

    @Context
    HttpServletRequest request;

    /**
     * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {
        Response res;
        if (isCompatEndpoint()) {
            res = ccompatMapper.mapException(t);
        } else if (isV2Endpoint()) {
            res = coreV2Mapper.mapException(t);
        } else {
            res = coreMapper.mapException(t);
        }

        // Response.ResponseBuilder builder;
        // if (res.getJaxrsResponse() != null) {
        // builder = Response.fromResponse(res.getJaxrsResponse());
        // } else {
        // builder = Response.status(res.getStatus()).entity(res.getError());
        // }
        //
        // return builder.type(res.getContentType()).build();
        return res;
    }

    /**
     * Returns true if the endpoint that caused the error is a "ccompat" endpoint. If so we need to simplify
     * the error we return. The apicurio error structure has at least one additional property.
     */
    private boolean isCompatEndpoint() {
        if (this.request != null) {
            return this.request.getRequestURI().contains("/apis/ccompat");
        }
        return false;
    }

    /**
     * Returns true if the endpoint that caused the error is a Core V2 endpoint.
     */
    private boolean isV2Endpoint() {
        if (this.request != null) {
            return this.request.getRequestURI().contains("/apis/registry/v2");
        }
        return false;
    }

}
