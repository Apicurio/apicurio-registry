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

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class AdminOverride {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Instance<JsonWebToken> jsonWebToken;

    @Inject
    TenantContext tenantContext;

    @Inject
    MultitenancyProperties mtProperties;

    public boolean isAdmin() {
        // When multi-tenancy is enabled, the owner of the tenant is always an admin.
        if (mtProperties.isMultitenancyEnabled() && authConfig.isTenantOwnerAdminEnabled() && isTenantOwner()) {
            return true;
        }

        if (!authConfig.adminOverrideEnabled) {
            return false;
        }

        if ("token".equals(authConfig.adminOverrideFrom)) {
            if ("role".equals(authConfig.adminOverrideType)) {
                return hasAdminRole();
            } else if ("claim".equals(authConfig.adminOverrideType)) {
                return hasAdminClaim();
            }
        }
        return false;
    }

    private boolean isTenantOwner() {
        String tOwner = tenantContext.tenantOwner();
        return tOwner != null &&
                securityIdentity != null &&
                securityIdentity.getPrincipal() != null &&
                tOwner.equals(securityIdentity.getPrincipal().getName());
    }

    private boolean hasAdminRole() {
        return securityIdentity.hasRole(authConfig.adminOverrideRole);
    }

    private boolean hasAdminClaim() {
        final Optional<Object> claimValue = jsonWebToken.get().claim(authConfig.adminOverrideClaim);
        if (claimValue.isPresent()) {
            return authConfig.adminOverrideClaimValue.equals(claimValue.get().toString());
        }
        return false;
    }

}
