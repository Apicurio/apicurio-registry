package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.interceptor.Interceptors;

import java.util.List;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ContextResourceImpl extends AbstractResource implements ContextResource {

    @Override
    public List<String> getContexts() throws Exception {
        return List.of(":.:");
    }
}
