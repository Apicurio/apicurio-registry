package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.core.System;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.limits.RegistryLimitsConfiguration;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.Limits;
import io.apicurio.registry.rest.v3.beans.SystemInfo;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfig;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigAuth;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigFeatures;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigUi;
import io.apicurio.registry.ui.UserInterfaceConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SystemResourceImpl implements SystemResource {

    @Inject
    System system;

    @Inject
    AuthConfig authConfig;

    @Inject
    UserInterfaceConfigProperties uiConfig;

    @Inject
    RegistryLimitsConfiguration registryLimitsConfiguration;

    @Inject
    RestConfig restConfig;

    /**
     * @see io.apicurio.registry.rest.v3.SystemResource#getSystemInfo()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        info.setName(system.getName());
        info.setDescription(system.getDescription());
        info.setVersion(system.getVersion());
        info.setBuiltOn(system.getDate());
        return info;
    }

    /**
     * @see io.apicurio.registry.rest.v3.SystemResource#getResourceLimits()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
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

    /**
     * @see io.apicurio.registry.rest.v3.SystemResource#getUIConfig()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public UserInterfaceConfig getUIConfig() {
        return UserInterfaceConfig.builder()
                .ui(UserInterfaceConfigUi.builder().contextPath(uiConfig.contextPath)
                        .navPrefixPath(uiConfig.navPrefixPath).oaiDocsUrl(uiConfig.docsUrl).build())
                .auth(uiAuthConfig())
                .features(UserInterfaceConfigFeatures.builder()
                        .readOnly("true".equals(uiConfig.featureReadOnly))
                        .breadcrumbs("true".equals(uiConfig.featureBreadcrumbs))
                        .roleManagement(authConfig.isRbacEnabled())
                        .deleteGroup(restConfig.isGroupDeletionEnabled())
                        .deleteArtifact(restConfig.isArtifactDeletionEnabled())
                        .deleteVersion(restConfig.isArtifactVersionDeletionEnabled())
                        .draftMutability(restConfig.isArtifactVersionMutabilityEnabled())
                        .settings("true".equals(uiConfig.featureSettings)).build())
                .build();
    }

    private UserInterfaceConfigAuth uiAuthConfig() {
        UserInterfaceConfigAuth rval = new UserInterfaceConfigAuth();
        rval.setObacEnabled(authConfig.isObacEnabled());
        rval.setRbacEnabled(authConfig.isRbacEnabled());
        rval.setType(authConfig.isOidcAuthEnabled() ? UserInterfaceConfigAuth.Type.oidc
            : authConfig.isBasicAuthEnabled() ? UserInterfaceConfigAuth.Type.basic
                : UserInterfaceConfigAuth.Type.none);
        if (authConfig.isOidcAuthEnabled()) {
            Map<String, String> options = new HashMap<>();
            options.put("url", uiConfig.authOidcUrl);
            options.put("redirectUri", uiConfig.authOidcRedirectUri);
            options.put("clientId", uiConfig.authOidcClientId);
            if (!"f5".equals(uiConfig.authOidcLogoutUrl)) {
                options.put("logoutUrl", uiConfig.authOidcLogoutUrl);
            }
            rval.setOptions(options);
        }
        return rval;
    }
}
