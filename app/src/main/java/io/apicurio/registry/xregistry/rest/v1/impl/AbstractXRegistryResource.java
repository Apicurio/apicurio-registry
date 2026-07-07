package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;

public abstract class AbstractXRegistryResource {

    private static final String XREGISTRY_BASE_PATH = "/apis/xregistry/v1";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Context
    HttpServletRequest request;

    /**
     * Builds a self URI for the given relative path, taking into account X-Forwarded headers.
     */
    protected URI buildSelfUri(String relativePath) {
        try {
            URI baseHref = getBaseHref();
            String path = XREGISTRY_BASE_PATH + relativePath;
            return baseHref.resolve(path);
        } catch (URISyntaxException e) {
            log.warn("Failed to build self URI for path: {}", relativePath, e);
            return null;
        }
    }

    /**
     * Checks that the provided epoch matches the actual epoch, throwing a 409 Conflict if they differ.
     */
    protected void checkEpochConflict(Integer expectedEpoch, long actualEpoch) {
        if (expectedEpoch != null && expectedEpoch != actualEpoch) {
            throw new WebApplicationException(
                    "Epoch conflict: expected " + expectedEpoch + " but found " + actualEpoch,
                    Response.Status.CONFLICT);
        }
    }

    /**
     * Throws a 501 Not Implemented response.
     */
    protected WebApplicationException notImplemented() {
        return new WebApplicationException("Not implemented", Response.Status.NOT_IMPLEMENTED);
    }

    /**
     * Throws a 405 Method Not Allowed response.
     */
    protected WebApplicationException methodNotAllowed() {
        return new WebApplicationException("Method not allowed",
                Response.Status.METHOD_NOT_ALLOWED);
    }

    private URI getBaseHref() throws URISyntaxException {
        URI forwarded = getBaseHrefFromXForwarded();
        if (forwarded != null) {
            return forwarded;
        }
        return getBaseHrefFromRequest();
    }

    private URI getBaseHrefFromXForwarded() throws URISyntaxException {
        String fproto = request.getHeader("X-Forwarded-Proto");
        String fhost = request.getHeader("X-Forwarded-Host");
        if (fproto != null && !fproto.isEmpty() && fhost != null && !fhost.isEmpty()) {
            return new URI(fproto + "://" + fhost);
        }
        return null;
    }

    private URI getBaseHrefFromRequest() throws URISyntaxException {
        String requestUrl = request.getRequestURL().toString();
        URI requestUri = new URI(requestUrl);
        return requestUri.resolve("/");
    }
}
