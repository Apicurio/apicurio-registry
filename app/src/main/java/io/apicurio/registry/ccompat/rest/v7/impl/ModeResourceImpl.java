package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.rest.v7.ModeResource;
import io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.interceptor.Interceptors;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ModeResourceImpl extends AbstractResource implements ModeResource {

    @Override
    public ModeUpdateResponse getMode() {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ModeUpdateResponse updateMode(Boolean force, ModeUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ModeUpdateResponse getSubjectMode(String subject, String groupId) {
        return null;
    }

    @Override
    public ModeUpdateResponse updateSubjectMode(String subject, Boolean force, String groupId, ModeUpdateRequest data) {
        return null;
    }
}
