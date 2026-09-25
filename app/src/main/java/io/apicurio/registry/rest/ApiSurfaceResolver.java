package io.apicurio.registry.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves which API surface (v3, v2, ccompat, iceberg) a given request targets.
 * Centralizes the URI matching logic so exception mappers and other components
 * do not need to duplicate endpoint detection.
 */
@ApplicationScoped
public class ApiSurfaceResolver {

    public ApiSurface resolve(HttpServletRequest request) {
        if (request != null) {
            return resolve(request.getRequestURI());
        }
        return ApiSurface.V3;
    }

    public ApiSurface resolve(String requestUri) {
        if (requestUri != null) {
            if (requestUri.contains("/apis/ccompat")) {
                return ApiSurface.CCOMPAT;
            }
            if (requestUri.contains("/apis/registry/v2")) {
                return ApiSurface.V2;
            }
            if (requestUri.contains("/apis/iceberg")) {
                return ApiSurface.ICEBERG;
            }
        }
        return ApiSurface.V3;
    }
}
