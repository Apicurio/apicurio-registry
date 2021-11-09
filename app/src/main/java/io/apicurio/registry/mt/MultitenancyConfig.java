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

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class MultitenancyConfig {

    @Inject
    RegistryConfigService configService;


    @PostConstruct
    void init() {
        Optional<String> reaperEvery = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_REAPER_EVERY);

        reaperEvery.orElseThrow(() -> new IllegalArgumentException("Missing required configuration property 'registry.multitenancy.reaper.every'"));
    }

    /**
     * @return the multitenancyEnabled
     */
    public boolean isMultitenancyEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_ENABLED, Boolean.class);
    }

    /**
     * @return true if multitenancy authorization is enabled
     */
    public boolean isMultitenancyAuthorizationEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_AUTHORIZATION_ENABLED, Boolean.class);
    }

    /**
     * @return true if multitenancy context paths are enabled
     */
    public boolean isMultitenancyContextPathEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_CONTEXT_PATH_ENABLED, Boolean.class);
    }

    /**
     * @return true if multitenancy subdomains are enabled
     */
    public boolean isMultitenancySubdomainEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_ENABLED, Boolean.class);
    }

    /**
     * @return true if multitenancy request headers are enabled
     */
    public boolean isMultitenancyRequestHeaderEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REQUEST_HEADER_ENABLED, Boolean.class);
    }

    /**
     * @return the nameMultitenancyBasePath
     */
    public String getNameMultitenancyBasePath() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_CONTEXT_PATH_BASE_PATH);
    }

    /**
     * @return the subdomain location (e.g. "header" or "serverName")
     */
    public String getSubdomainMultitenancyLocation() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_LOCATION);
    }

    /**
     * @return the subdomain header name (when the location is "header")
     */
    public String getSubdomainMultitenancyHeaderName() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_HEADER_NAME);
    }

    /**
     * @return the subdomain pattern
     */
    public String getSubdomainMultitenancyPattern() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_PATTERN);
    }

    /**
     * @return the HTTP request header containing a tenant ID
     */
    public String getTenantIdRequestHeader() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REQUEST_HEADER_NAME);
    }

    public Duration getReaperPeriod() {
        return Duration.ofSeconds(configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REAPER_PERIOD, Long.class));
    }

    /**
     * @return the tenantManagerUrl
     */
    public Optional<String> getTenantManagerUrl() {
        return configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_URL);
    }

    /**
     * @return true if tenant management authentication is enabled
     */
    public boolean isTenantManagerAuthEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_ENABLED, Boolean.class);
    }

    /**
     * @return the tenant manager authentication server url
     */
    public Optional<String> getTenantManagerAuthUrl() {
        return configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_URL);
    }

    /**
     * @return the tenant manager auth client id
     */
    public Optional<String> getTenantManagerClientId() {
        return configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_ID);
    }

    /**
     * @return the tenant manager auth client secret
     */
    public Optional<String> getTenantManagerClientSecret() {
        return configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_SECRET);
    }
}
