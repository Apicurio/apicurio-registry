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

package io.apicurio.registry.mt;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.RegistryTenantList;
import io.apicurio.multitenant.api.datamodel.SortBy;
import io.apicurio.multitenant.api.datamodel.SortOrder;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.multitenant.client.exception.RegistryTenantForbiddenException;
import io.apicurio.multitenant.client.exception.RegistryTenantNotAuthorizedException;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.registry.services.auth.IdentityServerResolver;
import io.apicurio.registry.services.auth.WrappedValue;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.config.ApicurioClientConfig;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.faulttolerance.FaultToleranceConstants.TIMEOUT_MS;
import static io.apicurio.registry.services.auth.IdentityServerResolver.getSSOProviders;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class TenantMetadataService {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    MultitenancyProperties properties;

    @ConfigProperty(name = "registry.identity.server.resolver.enabled")
    Boolean resolveIdentityServer;

    @ConfigProperty(name = "registry.identity.server.resolver.request-base-path")
    String resolverRequestBasePath;

    @ConfigProperty(name = "registry.identity.server.resolver.request-path")
    String resolverRequestPath;

    @ConfigProperty(name = "registry.identity.server.resolver.cache-expiration", defaultValue = "10")
    Integer resolverCacheExpiration;

    private TenantManagerClient tenantManagerClient;

    private ApicurioHttpClient resolverHttpClient;

    private WrappedValue<IdentityServerResolver.SsoProviders> cachedSsoProviders;

    @PostConstruct
    public void init() {
        if (properties.isMultitenancyEnabled()) {
            this.tenantManagerClient = buildClient();
        }
    }

    private TenantManagerClient buildClient() {
        //we check if profile is prod, because some tests will fail to start quarkus app.
        //Those tests checks if multitenancy is supported and abort the test if needed
        if ("prod".equals(ProfileManager.getActiveProfile()) && !storage.supportsMultiTenancy()) {
            throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                    "but the storage implementation being used (" + storage.storageName() + ") does not support multitenancy");
        }

        if (properties.getTenantManagerUrl().isEmpty()) {
            throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                    "but the no \"registry.tenant.manager.url\" is provided");
        }

        Map<String, Object> clientConfigs = new HashMap<>();
        if (properties.getTenantManagerCAFilePath().isPresent() && !properties.getTenantManagerCAFilePath().get().isBlank()) {
            clientConfigs.put(ApicurioClientConfig.APICURIO_REQUEST_CA_BUNDLE_LOCATION, properties.getTenantManagerCAFilePath().get());
        }

        Auth auth = null;
        if (properties.isTenantManagerAuthEnabled()) {

            if (properties.getTenantManagerAuthUrl().isEmpty() ||
                    properties.getTenantManagerClientId().isEmpty() ||
                    properties.getTenantManagerClientSecret().isEmpty()) {
                throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                        "\"registry.enable.auth\" is enabled " +
                        "but the no auth properties aren't properly configured");
            }

            String tenantManagerAuthServerUrl = properties.getTenantManagerAuthUrl().get();

            if (resolveIdentityServer) {
                resolverHttpClient = new JdkHttpClientProvider().create(resolverRequestBasePath, Collections.emptyMap(), null, new AuthErrorHandler());
                final IdentityServerResolver.SsoProviders ssoProviders = resolverHttpClient.sendRequest(getSSOProviders(resolverRequestPath));
                cachedSsoProviders = new WrappedValue<>(Duration.ofMinutes(resolverCacheExpiration), Instant.now(), ssoProviders);
                if (!tenantManagerAuthServerUrl.equals(ssoProviders.getTokenUrl())) {
                    tenantManagerAuthServerUrl = ssoProviders.getTokenUrl();
                }
            }

            ApicurioHttpClient httpClient = new JdkHttpClientProvider().create(tenantManagerAuthServerUrl, Collections.emptyMap(), null, new AuthErrorHandler());

            Duration tokenExpirationReduction = null;
            if (properties.getTenantManagerAuthTokenExpirationReductionMs().isPresent()) {
                log.info("Using configured tenant-manager auth token expiration reduction {}", properties.getTenantManagerAuthTokenExpirationReductionMs().get());
                tokenExpirationReduction = Duration.ofMillis(properties.getTenantManagerAuthTokenExpirationReductionMs().get());
            }

            auth = new OidcAuth(httpClient,
                    properties.getTenantManagerClientId().get(),
                    properties.getTenantManagerClientSecret().get(),
                    tokenExpirationReduction);
        }

        return new TenantManagerClientImpl(properties.getTenantManagerUrl().get(), clientConfigs, auth);
    }

    private TenantManagerClient getClient() {

        if (resolveIdentityServer && cachedSsoProviders.isExpired()) {

            final IdentityServerResolver.SsoProviders ssoProviders = resolverHttpClient.sendRequest(getSSOProviders(resolverRequestPath));

            if (properties.getTenantManagerAuthUrl().isPresent() && !ssoProviders.getTokenUrl().equals(properties.getTenantManagerAuthUrl().get())) {
                this.tenantManagerClient = buildClient();
            }
        }
        return tenantManagerClient;
    }

    @Retry(abortOn = {
            UnsupportedOperationException.class, TenantNotFoundException.class,
            TenantNotAuthorizedException.class, TenantForbiddenException.class
    }) // 3 retries, 200ms jitter
    @Timeout(TIMEOUT_MS)
    public RegistryTenant getTenant(String tenantId) throws TenantNotFoundException {
        if (tenantManagerClient == null) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }
        try {
            return getClient().getTenant(tenantId);
        } catch (RegistryTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        } catch (RegistryTenantNotAuthorizedException e) {
            throw new TenantNotAuthorizedException(e.getMessage());
        } catch (RegistryTenantForbiddenException e) {
            throw new TenantForbiddenException(e.getMessage());
        }
    }

    @Retry(abortOn = {
            UnsupportedOperationException.class, TenantNotFoundException.class,
            TenantNotAuthorizedException.class, TenantForbiddenException.class
    }) // 3 retries, 200ms jitter
    @Timeout(TIMEOUT_MS)
    public void markTenantAsDeleted(String tenantId) {
        if (tenantManagerClient == null) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }
        try {
            UpdateRegistryTenantRequest ureq = new UpdateRegistryTenantRequest();
            ureq.setStatus(TenantStatusValue.DELETED);
            getClient().updateTenant(tenantId, ureq);
        } catch (RegistryTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        } catch (RegistryTenantNotAuthorizedException e) {
            throw new TenantNotAuthorizedException(e.getMessage());
        } catch (RegistryTenantForbiddenException e) {
            throw new TenantForbiddenException(e.getMessage());
        }
    }

    public RegistryTenantList listTenants(TenantStatusValue status, int offset, int limit, SortOrder order, SortBy orderBy) {
        if (tenantManagerClient == null) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }
        try {
            return getClient().listTenants(status, offset, limit, order, orderBy);
        } catch (RegistryTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        } catch (RegistryTenantNotAuthorizedException e) {
            throw new TenantNotAuthorizedException(e.getMessage());
        } catch (RegistryTenantForbiddenException e) {
            throw new TenantForbiddenException(e.getMessage());
        }
    }
}
