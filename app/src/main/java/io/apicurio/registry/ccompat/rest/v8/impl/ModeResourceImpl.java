package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.ModeResource;
import io.apicurio.registry.ccompat.rest.v8.beans.ModeUpdateRequest;
import io.apicurio.registry.ccompat.rest.v8.beans.ModeUpdateResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

/**
 * v8 implementation of ModeResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ModeResourceImpl implements ModeResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.ModeResourceImpl v7ModeResource;

    @Override
    public ModeUpdateResponse getMode() {
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse v7Response =
                v7ModeResource.getMode();
        return convertResponse(v7Response);
    }

    @Override
    public ModeUpdateResponse updateMode(Boolean force, ModeUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse v7Response =
                v7ModeResource.updateMode(force, v7Request);
        return convertResponse(v7Response);
    }

    @Override
    public ModeUpdateResponse getSubjectMode(String subject, String xRegistryGroupId) {
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse v7Response =
                v7ModeResource.getSubjectMode(subject, xRegistryGroupId);
        return convertResponse(v7Response);
    }

    @Override
    public ModeUpdateResponse updateSubjectMode(String subject, Boolean force, String xRegistryGroupId,
            ModeUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse v7Response =
                v7ModeResource.updateSubjectMode(subject, force, xRegistryGroupId, v7Request);
        return convertResponse(v7Response);
    }

    private io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest convertToV7Request(ModeUpdateRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest v7Request =
                new io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest();
        if (data.getMode() != null) {
            v7Request.setMode(io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest.Mode.valueOf(
                    data.getMode().name()));
        }
        return v7Request;
    }

    private ModeUpdateResponse convertResponse(io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse v7Response) {
        ModeUpdateResponse response = new ModeUpdateResponse();
        if (v7Response.getMode() != null) {
            response.setMode(ModeUpdateResponse.Mode.valueOf(v7Response.getMode().name()));
        }
        return response;
    }
}
