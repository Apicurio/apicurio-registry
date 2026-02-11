package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.ConfigResource;
import io.apicurio.registry.ccompat.rest.v8.beans.ConfigUpdateRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.GlobalConfigResponse;
import io.apicurio.registry.ccompat.rest.v8.beans.SubjectConfigResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

/**
 * v8 implementation of ConfigResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ConfigResourceImpl implements ConfigResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.ConfigResourceImpl v7ConfigResource;

    @Override
    public GlobalConfigResponse getGlobalConfig() {
        io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response =
                v7ConfigResource.getGlobalConfig();
        return convertGlobalConfigResponse(v7Response);
    }

    @Override
    public GlobalConfigResponse updateGlobalConfig(ConfigUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response =
                v7ConfigResource.updateGlobalConfig(v7Request);
        return convertGlobalConfigResponse(v7Response);
    }

    @Override
    public GlobalConfigResponse deleteGlobalConfig() {
        io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response =
                v7ConfigResource.deleteGlobalConfig();
        return convertGlobalConfigResponse(v7Response);
    }

    @Override
    public GlobalConfigResponse updateSubjectConfig(String subject, String xRegistryGroupId,
            ConfigUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response =
                v7ConfigResource.updateSubjectConfig(subject, xRegistryGroupId, v7Request);
        return convertGlobalConfigResponse(v7Response);
    }

    @Override
    public SubjectConfigResponse getSubjectConfig(String subject, Boolean defaultToGlobal,
            String xRegistryGroupId) {
        io.apicurio.registry.ccompat.rest.v7.beans.SubjectConfigResponse v7Response =
                v7ConfigResource.getSubjectConfig(subject, defaultToGlobal, xRegistryGroupId);
        return convertSubjectConfigResponse(v7Response);
    }

    @Override
    public GlobalConfigResponse deleteSubjectConfig(String subject, String xRegistryGroupId) {
        io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response =
                v7ConfigResource.deleteSubjectConfig(subject, xRegistryGroupId);
        return convertGlobalConfigResponse(v7Response);
    }

    private io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest convertToV7Request(ConfigUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest v7Request =
                new io.apicurio.registry.ccompat.rest.v7.beans.ConfigUpdateRequest();
        v7Request.setCompatibility(data.getCompatibility());
        return v7Request;
    }

    private GlobalConfigResponse convertGlobalConfigResponse(io.apicurio.registry.ccompat.rest.v7.beans.GlobalConfigResponse v7Response) {
        GlobalConfigResponse response = new GlobalConfigResponse();
        response.setCompatibilityLevel(v7Response.getCompatibilityLevel());
        return response;
    }

    private SubjectConfigResponse convertSubjectConfigResponse(io.apicurio.registry.ccompat.rest.v7.beans.SubjectConfigResponse v7Response) {
        SubjectConfigResponse response = new SubjectConfigResponse();
        if (v7Response.getCompatibilityLevel() != null) {
            response.setCompatibilityLevel(SubjectConfigResponse.CompatibilityLevel.valueOf(
                    v7Response.getCompatibilityLevel().name()));
        }
        return response;
    }
}
