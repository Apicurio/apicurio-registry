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

    boolean multitenancyEnabled;
    boolean mtAuthorizationEnabled;
    boolean mtContextPathEnabled;
    boolean mtSubdomainEnabled;
    boolean mtRequestHeaderEnabled;
    String nameMultitenancyBasePath;
    String subdomainMultitenancyLocation;
    String subdomainMultitenancyHeaderName;
    String subdomainMultitenancyPattern;
    String tenantIdRequestHeader;
    Optional<String> reaperEvery;
    Long reaperPeriodSeconds;
    Optional<String> tenantManagerUrl;
    Optional<Boolean> tenantManagerAuthEnabled;
    Optional<String> tenantManagerAuthUrl;
    Optional<String> tenantManagerClientId;
    Optional<String> tenantManagerClientSecret;

    @PostConstruct
    void init() {
        multitenancyEnabled = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_ENABLED, Boolean.class);
        mtAuthorizationEnabled = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_AUTHORIZATION_ENABLED, Boolean.class);
        mtContextPathEnabled = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_CONTEXT_PATH_ENABLED, Boolean.class);
        mtSubdomainEnabled = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_ENABLED, Boolean.class);
        mtRequestHeaderEnabled = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REQUEST_HEADER_ENABLED, Boolean.class);
        nameMultitenancyBasePath = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_CONTEXT_PATH_BASE_PATH);
        subdomainMultitenancyLocation = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_LOCATION);
        subdomainMultitenancyHeaderName = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_HEADER_NAME);
        subdomainMultitenancyPattern = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_SUBDOMAIN_PATTERN);
        tenantIdRequestHeader = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REQUEST_HEADER_NAME);
        reaperEvery = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_REAPER_EVERY);
        reaperPeriodSeconds = configService.get(RegistryConfigProperty.REGISTRY_MULTITENANCY_REAPER_PERIOD, Long.class);
        tenantManagerUrl = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_URL);
        tenantManagerAuthEnabled = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_ENABLED, Boolean.class);
        tenantManagerAuthUrl = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_URL);
        tenantManagerClientId = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_ID);
        tenantManagerClientSecret = configService.getOptional(RegistryConfigProperty.REGISTRY_MULTITENANCY_TENANT_MANAGER_AUTH_CLIENT_SECRET);

        this.reaperEvery.orElseThrow(() -> new IllegalArgumentException("Missing required configuration property 'registry.multitenancy.reaper.every'"));
    }

    /**
     * @return the multitenancyEnabled
     */
    public boolean isMultitenancyEnabled() {
        return multitenancyEnabled;
    }

    /**
     * @return true if multitenancy authorization is enabled
     */
    public boolean isMultitenancyAuthorizationEnabled() {
        return mtAuthorizationEnabled;
    }

    /**
     * @return true if multitenancy context paths are enabled
     */
    public boolean isMultitenancyContextPathEnabled() {
        return mtContextPathEnabled;
    }

    /**
     * @return true if multitenancy subdomains are enabled
     */
    public boolean isMultitenancySubdomainEnabled() {
        return mtSubdomainEnabled;
    }

    /**
     * @return true if multitenancy request headers are enabled
     */
    public boolean isMultitenancyRequestHeaderEnabled() {
        return mtRequestHeaderEnabled;
    }

    /**
     * @return the nameMultitenancyBasePath
     */
    public String getNameMultitenancyBasePath() {
        return nameMultitenancyBasePath;
    }

    /**
     * @return the subdomain location (e.g. "header" or "serverName")
     */
    public String getSubdomainMultitenancyLocation() {
        return subdomainMultitenancyLocation;
    }

    /**
     * @return the subdomain header name (when the location is "header")
     */
    public String getSubdomainMultitenancyHeaderName() {
        return subdomainMultitenancyHeaderName;
    }

    /**
     * @return the subdomain pattern
     */
    public String getSubdomainMultitenancyPattern() {
        return subdomainMultitenancyPattern;
    }

    /**
     * @return the HTTP request header containing a tenant ID
     */
    public String getTenantIdRequestHeader() {
        return tenantIdRequestHeader;
    }

    public Duration getReaperPeriod() {
        return Duration.ofSeconds(reaperPeriodSeconds);
    }

    /**
     * @return the tenantManagerUrl
     */
    public Optional<String> getTenantManagerUrl() {
        return tenantManagerUrl;
    }

    /**
     * @return true if tenant management authentication is enabled
     */
    public boolean isTenantManagerAuthEnabled() {
        return tenantManagerAuthEnabled.orElse(Boolean.FALSE);
    }

    /**
     * @return the tenant manager authentication server url
     */
    public Optional<String> getTenantManagerAuthUrl() {
        return tenantManagerAuthUrl;
    }

    /**
     * @return the tenant manager auth client id
     */
    public Optional<String> getTenantManagerClientId() {
        return tenantManagerClientId;
    }

    /**
     * @return the tenant manager auth client secret
     */
    public Optional<String> getTenantManagerClientSecret() {
        return tenantManagerClientSecret;
    }
}
