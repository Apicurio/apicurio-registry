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

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class MultitenancyProperties {

    @Inject
    @ConfigProperty(name = "registry.enable.multitenancy")
    boolean multitenancyEnabled;

    @Inject
    @ConfigProperty(name = "registry.auth.enabled")
    boolean authEnabled;

    @Inject
    @ConfigProperty(name = "registry.multitenancy.base.path")
    String nameMultitenancyBasePath;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.url")
    Optional<String> tenantManagerUrl;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.auth.url")
    Optional<String> tenantManagerAuthUrl;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.auth.realm")
    Optional<String> tenantManagerAuthRealm;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.auth.client-id")
    Optional<String> tenantManagerClientId;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.auth.client-secret")
    Optional<String> tenantManagerClientSecret;

    /**
     * @return the multitenancyEnabled
     */
    public boolean isMultitenancyEnabled() {
        return multitenancyEnabled;
    }

    /**
     * @return the nameMultitenancyBasePath
     */
    public String getNameMultitenancyBasePath() {
        return nameMultitenancyBasePath;
    }

    /**
     * @return the tenantManagerUrl
     */
    public Optional<String> getTenantManagerUrl() {
        return tenantManagerUrl;
    }

    /**
     * @return if auth is enabled
     */
    public boolean isAuthEnabled() {
        return authEnabled;
    }

    /**
     * @return the tenant manager authentication server url
     */
    public Optional<String> getTenantManagerAuthUrl() {
        return tenantManagerAuthUrl;
    }

    /**
     * @return the tenant manager auth realm
     */
    public Optional<String> getTenantManagerAuthRealm() {
        return tenantManagerAuthRealm;
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
