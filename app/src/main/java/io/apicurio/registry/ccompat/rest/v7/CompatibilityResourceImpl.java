package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.CompatibilityResource;
import io.apicurio.registry.ccompat.v7.beans.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class CompatibilityResourceImpl implements CompatibilityResource {

    @Override
    public CompatibilityCheckResponse testCompatibilityBySubjectName(String subject, String version, Boolean normalize, Boolean verbose, RegisterSchemaRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public CompatibilityCheckResponse testCompatibilityForSubject(String subject, Boolean normalize, Boolean verbose, RegisterSchemaRequest data) {
        Errors.operationNotSupported();
        return null;
    }

}
