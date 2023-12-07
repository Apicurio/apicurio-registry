package io.apicurio.registry.rest.v2;

import io.apicurio.common.apps.core.System;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.limits.RegistryLimitsConfiguration;
import io.apicurio.registry.rest.v2.beans.Limits;
import io.apicurio.registry.rest.v2.beans.SystemInfo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SystemResourceImpl implements SystemResource {

    @Inject
    System system;

    @Inject
    RegistryLimitsConfiguration registryLimitsConfiguration;

    /**
     * @see io.apicurio.registry.rest.v2.SystemResource#getSystemInfo()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.None)
    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        info.setName(system.getName());
        info.setDescription(system.getDescription());
        info.setVersion(system.getVersion());
        info.setBuiltOn(system.getDate());
        return info;
    }

    /**
     * @see io.apicurio.registry.rest.v2.SystemResource#getResourceLimits()
     */
    @Override
    public Limits getResourceLimits() {
        var limitsConfig = registryLimitsConfiguration;
        var limits = new Limits();
        limits.setMaxTotalSchemasCount(limitsConfig.getMaxTotalSchemasCount());
        limits.setMaxSchemaSizeBytes(limitsConfig.getMaxSchemaSizeBytes());
        limits.setMaxArtifactsCount(limitsConfig.getMaxArtifactsCount());
        limits.setMaxVersionsPerArtifactCount(limitsConfig.getMaxVersionsPerArtifactCount());
        limits.setMaxArtifactPropertiesCount(limitsConfig.getMaxArtifactPropertiesCount());
        limits.setMaxPropertyKeySizeBytes(limitsConfig.getMaxPropertyKeySizeBytes());
        limits.setMaxPropertyValueSizeBytes(limitsConfig.getMaxPropertyValueSizeBytes());
        limits.setMaxArtifactLabelsCount(limitsConfig.getMaxArtifactLabelsCount());
        limits.setMaxLabelSizeBytes(limitsConfig.getMaxLabelSizeBytes());
        limits.setMaxArtifactNameLengthChars(limitsConfig.getMaxArtifactNameLengthChars());
        limits.setMaxArtifactDescriptionLengthChars(limitsConfig.getMaxArtifactDescriptionLengthChars());
        limits.setMaxRequestsPerSecondCount(limitsConfig.getMaxRequestsPerSecondCount());
        return limits;
    }
}
