/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.v2;

import io.apicurio.common.apps.core.System;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.rest.v2.beans.Limits;
import io.apicurio.registry.rest.v2.beans.SystemInfo;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SystemResourceImpl implements SystemResource {

    @Inject
    System system;

    @Inject
    TenantContext tctx;

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
        var limitsConfig = tctx.limitsConfig();
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
