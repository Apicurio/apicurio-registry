package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.rest.v3.SystemResource;

import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.core.System;
import io.apicurio.registry.limits.RegistryLimitsConfiguration;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.SystemInfo;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfig;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigAuth;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigFeatures;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfigUi;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.ui.UserInterfaceConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
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

    @Inject
    ElasticsearchSearchConfig esSearchConfig;

    @Inject
    RegistryStorageContentUtils contentUtils;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Context
    HttpHeaders httpHeaders;

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

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response canonicalizeContent(String artifactType, InputStream data) {
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException("Empty content is not allowed.");
        }
        if (!factory.getAllArtifactTypes().contains(artifactType)) {
            throw new BadRequestException("Unknown artifact type: " + artifactType);
        }
        String ct = httpHeaders.getMediaType() != null ? httpHeaders.getMediaType().toString() : null;
        TypedContent typedContent = TypedContent.create(content, ct);
        TypedContent canonicalized = contentUtils.canonicalizeContent(artifactType, typedContent,
                Map.of());
        return Response.ok(canonicalized.getContent()).type(canonicalized.getContentType()).build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.SystemResource#getUIConfig()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public UserInterfaceConfig getUIConfig() {
        return UserInterfaceConfig.builder()
                .ui(UserInterfaceConfigUi.builder().contextPath(uiConfig.contextPath)
                        .navPrefixPath(uiConfig.navPrefixPath)
                        .oaiDocsUrl(uiConfig.docsUrl)
                        .editorsUrl(uiConfig.editorsUrl)
                        .build())
                .auth(uiAuthConfig())
                .features(UserInterfaceConfigFeatures.builder()
                        .readOnly("true".equals(uiConfig.featureReadOnly))
                        .breadcrumbs("true".equals(uiConfig.featureBreadcrumbs))
                        .roleManagement(authConfig.isRbacEnabled() && "application".equals(authConfig.getRoleSource()))
                        .deleteGroup(restConfig.isGroupDeletionEnabled())
                        .deleteArtifact(restConfig.isArtifactDeletionEnabled())
                        .deleteVersion(restConfig.isArtifactVersionDeletionEnabled())
                        .draftMutability(restConfig.isArtifactVersionMutabilityEnabled())
                        .agents(uiConfig.featureAgents.get())
                        .searchIndex(esSearchConfig.isEnabled())
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
            // Only include redirectUri if explicitly configured
            uiConfig.authOidcRedirectUri.ifPresent(uri -> options.put("redirectUri", uri));
            options.put("clientId", uiConfig.authOidcClientId);
            options.put("scope", uiConfig.scope);
            if (!"f5".equals(uiConfig.authOidcLogoutUrl)) {
                options.put("logoutUrl", uiConfig.authOidcLogoutUrl);
            }
            // Only include loadUserInfo if explicitly configured
            uiConfig.loadUserInfo.ifPresent(loadUserInfo -> options.put("loadUserInfo", String.valueOf(loadUserInfo)));
            rval.setOptions(options);
        }
        return rval;
    }
}
