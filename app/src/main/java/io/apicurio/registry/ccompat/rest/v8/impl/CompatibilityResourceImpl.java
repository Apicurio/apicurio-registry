package io.apicurio.registry.ccompat.rest.v8.impl;

import io.apicurio.registry.ccompat.rest.v8.CompatibilityResource;
import io.apicurio.registry.ccompat.rest.v8.beans.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.rest.v8.beans.RegisterSchemaRequest;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.util.stream.Collectors;

/**
 * v8 implementation of CompatibilityResource that delegates to v7 implementation.
 */
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class CompatibilityResourceImpl implements CompatibilityResource {

    @Inject
    io.apicurio.registry.ccompat.rest.v7.impl.CompatibilityResourceImpl v7CompatibilityResource;

    @Override
    public CompatibilityCheckResponse checkAllCompatibility(String subject, Boolean verbose,
            Boolean normalize, String xRegistryGroupId, RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.CompatibilityCheckResponse v7Response =
                v7CompatibilityResource.checkAllCompatibility(subject, verbose, normalize, xRegistryGroupId, v7Request);
        return convertResponse(v7Response);
    }

    @Override
    public CompatibilityCheckResponse checkCompatibility(String subject, String version, Boolean verbose,
            Boolean normalize, String xRegistryGroupId, RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request = convertToV7Request(data);
        io.apicurio.registry.ccompat.rest.v7.beans.CompatibilityCheckResponse v7Response =
                v7CompatibilityResource.checkCompatibility(subject, version, verbose, normalize, xRegistryGroupId, v7Request);
        return convertResponse(v7Response);
    }

    private io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest convertToV7Request(RegisterSchemaRequest data) {
        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest v7Request =
                new io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest();
        v7Request.setSchema(data.getSchema());
        v7Request.setSchemaType(data.getSchemaType());
        if (data.getReferences() != null) {
            v7Request.setReferences(data.getReferences().stream()
                    .map(ref -> {
                        io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference v7Ref =
                                new io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference();
                        v7Ref.setName(ref.getName());
                        v7Ref.setSubject(ref.getSubject());
                        v7Ref.setVersion(ref.getVersion());
                        return v7Ref;
                    })
                    .collect(Collectors.toList()));
        }
        return v7Request;
    }

    private CompatibilityCheckResponse convertResponse(io.apicurio.registry.ccompat.rest.v7.beans.CompatibilityCheckResponse v7Response) {
        CompatibilityCheckResponse response = new CompatibilityCheckResponse();
        response.setIsCompatible(v7Response.getIsCompatible());
        return response;
    }
}
