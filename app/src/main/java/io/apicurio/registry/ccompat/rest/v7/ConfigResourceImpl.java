package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.registry.ccompat.v7.ConfigResource;
import io.apicurio.registry.ccompat.v7.beans.Config;
import io.apicurio.registry.ccompat.v7.beans.ConfigUpdateRequest;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ConfigResourceImpl implements ConfigResource {
    @Override
    public Config getTopLevelConfig() {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ConfigUpdateRequest updateTopLevelConfig(ConfigUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String deleteTopLevelConfig() {
        Errors.operationNotSupported();
        return "";
    }

    @Override
    public Config getSubjectLevelConfig(String subject, Boolean defaultToGlobal) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public ConfigUpdateRequest updateSubjectLevelConfig(String subject, ConfigUpdateRequest data) {
        Errors.operationNotSupported();
        return null;
    }

    @Override
    public String deleteSubjectConfig(String subject) {
        Errors.operationNotSupported();
        return "";
    }
}
