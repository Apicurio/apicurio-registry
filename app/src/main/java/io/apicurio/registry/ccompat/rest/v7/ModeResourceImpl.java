package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.ModeResource;
import io.apicurio.registry.ccompat.v7.beans.Mode;
import io.apicurio.registry.ccompat.v7.beans.ModeUpdateRequest;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ModeResourceImpl implements ModeResource {
    @Override
    public Mode getTopLevelMode() {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ModeUpdateRequest updateTopLevelMode(Boolean force, ModeUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Mode getMode(String subject, Boolean defaultToGlobal) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ModeUpdateRequest updateMode(String subject, Boolean force, ModeUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public Mode deleteSubjectMode(String subject) {
        Errors.operationNotSupported();
        return null;
    }
}
