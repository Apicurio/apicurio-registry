package io.apicurio.registry.rest;

import io.apicurio.registry.services.http.CCompatExceptionMapperService;
import io.apicurio.registry.services.http.CoreRegistryExceptionMapperService;
import io.apicurio.registry.services.http.CoreV2RegistryExceptionMapperService;
import io.apicurio.registry.services.http.ExceptionMapperService;
import io.apicurio.registry.services.http.IcebergExceptionMapperService;
import io.apicurio.registry.extensions.ApiExceptionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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
    ApiSurfaceResolver apiSurfaceResolver;

    @Inject
    CoreRegistryExceptionMapperService coreMapper;

    @Inject
    CoreV2RegistryExceptionMapperService coreV2Mapper;

    @Inject
    CCompatExceptionMapperService ccompatMapper;

    @Inject
    IcebergExceptionMapperService icebergMapper;

    @Inject
    Instance<ApiExceptionMapper> apiMappers;

    @Context
    HttpServletRequest request;

    /**
     * @see jakarta.ws.rs.ext.ExceptionMapper#toResponse(java.lang.Throwable)
     */
    @Override
    public Response toResponse(Throwable t) {
        ApiSurface surface = apiSurfaceResolver.resolve(this.request);
        return toResponse(t, surface);
    }

    public Response toResponse(Throwable t, ApiSurface surface) {
        if (surface == ApiSurface.V3) {
            return mapWithExtensionOrCore(t);
        }
        return getMapperService(surface).mapException(t);
    }

    ExceptionMapperService getMapperService(ApiSurface surface) {
        return switch (surface) {
            case CCOMPAT -> ccompatMapper;
            case V2 -> coreV2Mapper;
            case ICEBERG -> icebergMapper;
            case V3 -> coreMapper;
        };
    }

    /**
     * Returns true if the endpoint that caused the error is an extension endpoint (e.g. MCP Registry API endpoint).
     */
    private Response mapWithExtensionOrCore(Throwable t) {
        for (ApiExceptionMapper mapper : apiMappers) {
            if (mapper.handles(this.request)) {
                return mapper.mapException(t);
            }
        }
        return coreMapper.mapException(t);
    }

}
