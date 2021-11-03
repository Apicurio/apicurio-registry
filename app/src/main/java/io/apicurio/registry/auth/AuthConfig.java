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

package io.apicurio.registry.auth;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class AuthConfig {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    @PostConstruct
    void onConstruct() {
        log.debug("===============================");
        log.debug("Auth Enabled: " + this.isAuthEnabled());
        log.debug("Anonymous Read Access Enabled: " + this.isAnonymousReadAccessEnabled());
        log.debug("RBAC Enabled: " + this.isRbacEnabled());
        if (this.isRbacEnabled()) {
            log.debug("   RBAC Roles: " + getReadOnlyRole() + ", " + getDeveloperRole() + ", " + getAdminRole());
            log.debug("   Role Source: " + getRoleSource());
        }
        log.debug("OBAC Enabled: " + isObacEnabled());
        log.debug("Tenant Owner is Admin: " + this.isTenantOwnerAdminEnabled());
        log.debug("Admin Override Enabled: " + this.isAdminOverrideEnabled());
        if (this.isAdminOverrideEnabled()) {
            log.debug("   Admin Override from: " + getAdminOverrideFrom());
            log.debug("   Admin Override type: " + getAdminOverrideType());
            log.debug("   Admin Override role: " + getAdminOverrideRole());
            log.debug("   Admin Override claim: " + getAdminOverrideClaim());
            log.debug("   Admin Override claim-value: " + getAdminOverrideClaimValue());
        }
        log.debug("===============================");
    }

    public boolean isAuthEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ENABLED, Boolean.class);
    }

    public boolean isRbacEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ROLE_BASED_AUTHORIZATION, Boolean.class);
    }

    public boolean isObacEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_OWNER_ONLY_AUTHORIZATION, Boolean.class);
    }

    public boolean isApplicationRbacEnabled() {
        return this.isRbacEnabled() && "application".equals(getRoleSource());
    }

    public String getRoleSource() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ROLE_SOURCE);
    }

    public String getReadOnlyRole() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ROLES_READONLY);
    }

    public String getDeveloperRole() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ROLES_DEVELOPER);
    }

    public String getAdminRole() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ROLES_ADMIN);
    }

    public boolean isAnonymousReadAccessEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ANONYMOUS_READ_ACCESS_ENABLED, Boolean.class);
    }

    public boolean isTenantOwnerAdminEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_TENANT_OWNER_IS_ADMIN, Boolean.class);
    }

    public boolean isAdminOverrideEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_ENABLED, Boolean.class);
    }

    public String getAdminOverrideFrom() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_FROM);
    }

    public String getAdminOverrideType() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_TYPE);
    }

    public String getAdminOverrideRole() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_ROLE);
    }

    public String getAdminOverrideClaim() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM);
    }

    public String getAdminOverrideClaimValue() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_ADMIN_OVERRIDE_CLAIM_VALUE);
    }

    public boolean isOwnerOnlyAuthorizationLimitGroupAccessEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_OBAC_LIMIT_GROUP_ACCESS, Boolean.class);
    }

    public boolean isBasicAuthEnabled() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_BASIC_AUTH_CLIENT_CREDENTIALS_ENABLED, Boolean.class);
    }

    public String getAuthTokenEndpoint() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_TOKEN_ENDPOINT);
    }

    public String getOidcClientId() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_OIDC_CLIENT_ID);
    }

    public String getOidcClientSecret() {
        return configService.get(RegistryConfigProperty.REGISTRY_AUTH_OIDC_CLIENT_SECRECT);
    }

    public boolean hasOidcClientSecret() {
        return getOidcClientSecret() != null;
    }

}
