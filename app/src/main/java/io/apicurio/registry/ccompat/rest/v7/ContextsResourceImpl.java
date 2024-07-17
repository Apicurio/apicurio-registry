package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.ContextsResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

import java.util.List;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ContextsResourceImpl implements ContextsResource {
    @Override
    public List<String> listContexts() {
        Errors.operationNotSupported();
        return List.of();
    }
}
