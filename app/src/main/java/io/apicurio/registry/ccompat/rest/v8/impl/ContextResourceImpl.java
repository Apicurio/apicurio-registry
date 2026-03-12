package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.ContextsResource;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.util.List;

/**
 * v8 implementation of ContextsResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ContextResourceImpl implements ContextsResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.ContextResourceImpl v7ContextResource;

    @Override
    public List<String> getContexts() {
        return v7ContextResource.getContexts();
    }
}
