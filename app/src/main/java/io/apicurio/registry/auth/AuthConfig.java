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

import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class AuthConfig {

    @Inject
    Logger log;

    @ConfigProperty(name = "registry.auth.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Enable authentication", availableSince = "2.0.0.Final")
    boolean authenticationEnabled;

    @ConfigProperty(name = "registry.auth.role-based-authorization", defaultValue = "false")
    @Info(category = "auth", description = "Enable role based authorization", availableSince = "2.1.0.Final")
    boolean roleBasedAuthorizationEnabled;

    @Dynamic(label = "Artifact owner-only authorization", description = "When selected, Service Registry allows only the artifact owner (creator) to modify an artifact.", requires = "registry.auth.enabled=true")
    @ConfigProperty(name = "registry.auth.owner-only-authorization", defaultValue = "false")
    @Info(category = "auth", description = "Artifact owner-only authorization", availableSince = "2.0.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationEnabled;

    @Dynamic(label = "Artifact group owner-only authorization", description = "When selected, Service Registry allows only the artifact group owner (creator) to modify an artifact group.", requires = {
            "registry.auth.enabled=true",
            "registry.auth.owner-only-authorization=true"
    })
    @ConfigProperty(name = "registry.auth.owner-only-authorization.limit-group-access", defaultValue = "false")
    @Info(category = "auth", description = "Artifact group owner-only authorization", availableSince = "2.1.0.Final")
    Supplier<Boolean> ownerOnlyAuthorizationLimitGroupAccess;

    @Dynamic(label = "Anonymous read access", description = "When selected, requests from anonymous users (requests without any credentials) are granted read-only access.", requires = "registry.auth.enabled=true")
    @ConfigProperty(name = "registry.auth.anonymous-read-access.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Anonymous read access", availableSince = "2.1.0.Final")
    Supplier<Boolean> anonymousReadAccessEnabled;

    @Dynamic(label = "Authenticated read access", description = "When selected, requests from any authenticated user are granted at least read-only access.", requires = {
            "registry.auth.enabled=true",
            "registry.auth.role-based-authorization=true"
    })
    @ConfigProperty(name = "registry.auth.authenticated-read-access.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Authenticated read access", availableSince = "2.1.4.Final")
    Supplier<Boolean> authenticatedReadAccessEnabled;

    @ConfigProperty(name = "registry.auth.roles.readonly", defaultValue = "sr-readonly")
    @Info(category = "auth", description = "Auth roles readonly", availableSince = "2.1.0.Final")
    String readOnlyRole;

    @ConfigProperty(name = "registry.auth.roles.developer", defaultValue = "sr-developer")
    @Info(category = "auth", description = "Auth roles developer", availableSince = "2.1.0.Final")
    String developerRole;

    @ConfigProperty(name = "registry.auth.roles.admin", defaultValue = "sr-admin")
    @Info(category = "auth", description = "Auth roles admin", availableSince = "2.0.0.Final")
    String adminRole;

    @ConfigProperty(name = "registry.auth.role-source", defaultValue = "token")
    @Info(category = "auth", description = "Auth roles source", availableSince = "2.1.0.Final")
    String roleSource;

    @ConfigProperty(name = "registry.auth.tenant-owner-is-admin.enabled", defaultValue = "true")
    @Info(category = "auth", description = "Auth tenant owner admin enabled", availableSince = "2.1.0.Final")
    boolean tenantOwnerIsAdminEnabled;

    @ConfigProperty(name = "registry.auth.admin-override.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Auth admin override enabled", availableSince = "2.1.0.Final")
    boolean adminOverrideEnabled;

    @ConfigProperty(name = "registry.auth.admin-override.from", defaultValue = "token")
    @Info(category = "auth", description = "Auth admin override from", availableSince = "2.1.0.Final")
    String adminOverrideFrom;

    @ConfigProperty(name = "registry.auth.admin-override.type", defaultValue = "role")
    @Info(category = "auth", description = "Auth admin override type", availableSince = "2.1.0.Final")
    String adminOverrideType;

    @ConfigProperty(name = "registry.auth.admin-override.role", defaultValue = "sr-admin")
    @Info(category = "auth", description = "Auth admin override role", availableSince = "2.1.0.Final")
    String adminOverrideRole;

    @ConfigProperty(name = "registry.auth.admin-override.claim", defaultValue = "org-admin")
    @Info(category = "auth", description = "Auth admin override claim", availableSince = "2.1.0.Final")
    String adminOverrideClaim;

    @ConfigProperty(name = "registry.auth.admin-override.claim-value", defaultValue = "true")
    @Info(category = "auth", description = "Auth admin override claim value", availableSince = "2.1.0.Final")
    String adminOverrideClaimValue;

    @PostConstruct
    void onConstruct() {
        log.debug("===============================");
        log.debug("Auth Enabled: " + authenticationEnabled);
        log.debug("Anonymous Read Access Enabled: " + anonymousReadAccessEnabled);
        log.debug("Authenticated Read Access Enabled: " + authenticatedReadAccessEnabled);
        log.debug("RBAC Enabled: " + roleBasedAuthorizationEnabled);
        if (roleBasedAuthorizationEnabled) {
            log.debug("   RBAC Roles: " + readOnlyRole + ", " + developerRole + ", " + adminRole);
            log.debug("   Role Source: " + roleSource);
        }
        log.debug("OBAC Enabled: " + ownerOnlyAuthorizationEnabled);
        log.debug("Tenant Owner is Admin: " + tenantOwnerIsAdminEnabled);
        log.debug("Admin Override Enabled: " + adminOverrideEnabled);
        if (adminOverrideEnabled) {
            log.debug("   Admin Override from: " + adminOverrideFrom);
            log.debug("   Admin Override type: " + adminOverrideType);
            log.debug("   Admin Override role: " + adminOverrideRole);
            log.debug("   Admin Override claim: " + adminOverrideClaim);
            log.debug("   Admin Override claim-value: " + adminOverrideClaimValue);
        }
        log.debug("===============================");
    }

    public boolean isAuthEnabled() {
        return this.authenticationEnabled;
    }

    public boolean isRbacEnabled() {
        return this.roleBasedAuthorizationEnabled;
    }

    public boolean isObacEnabled() {
        return this.ownerOnlyAuthorizationEnabled.get();
    }

    public boolean isTenantOwnerAdminEnabled() {
        return this.tenantOwnerIsAdminEnabled;
    }

    public boolean isAdminOverrideEnabled() {
        return this.adminOverrideEnabled;
    }

    public String getRoleSource() {
        return this.roleSource;
    }

    public boolean isApplicationRbacEnabled() {
        return this.roleBasedAuthorizationEnabled && "application".equals(getRoleSource());
    }

    public boolean isAnonymousReadsEnabled() {
        return anonymousReadAccessEnabled.get();
    }

    public boolean isAuthenticatedReadsEnabled() {
        return authenticatedReadAccessEnabled.get();
    }

}
