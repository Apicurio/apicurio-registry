package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.V1Resource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class V1ResourceImpl implements V1Resource {

    @Override
    public void getClusterId() {
        Errors.operationNotSupported();
    }

    @Override
    public void getSchemaRegistryVersion() {
        Errors.operationNotSupported();
    }
}
