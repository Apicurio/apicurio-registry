package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.dto.ModeDto;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.rest.v7.ModeResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

import jakarta.interceptor.Interceptors;


@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ModeResourceImpl extends AbstractResource implements ModeResource {

    @Override
    public ModeDto getGlobalMode() {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ModeDto updateGlobalMode(ModeDto request) {
        Errors.operationNotSupported();
        return null;
    }
}
