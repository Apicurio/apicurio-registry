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

import javax.annotation.Priority;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.mt.RegistryTenantContext;
import io.apicurio.registry.mt.TenantNotAuthorizedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;

import io.apicurio.registry.mt.MultitenancyProperties;
import io.apicurio.registry.mt.TenantContext;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.List;
import java.util.Optional;

/**
 * This class implements authorization logic for the registry.  It is driven by a combination of the
 * security identity (authenticated user) and configured security level of the operation the user is
 * attempting to perform. In a multitenant deployment, this authorization interceptor also checks if
 * the user accessing the tenant has the proper permission level. This interceptor will be triggered
 * for any method that is annotated with the {@link Authorized} annotation. Please ensure that all
 * JAX-RS operations are propertly annotated.
 *
 * @author eric.wittmann@gmail.com
 */
@Authorized @Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuthorizedInterceptor {

    @Inject
    Logger log;

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Instance<JsonWebToken> jsonWebToken;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    OwnerBasedAccessController obac;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    TenantContext tenantContext;

    @ConfigProperty(name = "registry.organization-id.claim-name")
    @Info(category = "mt", description = "Organization ID claim name", availableSince = "2.1.0.Final")
    List<String> organizationIdClaims;

    @Inject
    MultitenancyProperties multitenancyProperties;

    @AroundInvoke
    public Object authorizeMethod(InvocationContext context) throws Exception {

        //execute multitenancy related authorization checks
        if (mtProperties.isMultitenancyEnabled()) {

            //if multitenancy is enabled but no tenant context is loaded, because no tenant was resolved from request, reject it
            //this is to avoid access to default tenant "_" when multitenancy is enabled
            if (!tenantContext.isLoaded()) {
                log.warn("Request is rejected because the tenant could not be found, and access to default tenant is disabled in a multitenant deployment");
                throw new ForbiddenException("Default tenant access is not allowed in multitenancy mode.");
            }

            //If multitenancy authorization is enabled, check tenant access.
            if (multitenancyProperties.isMultitenancyAuthorizationEnabled()) {
                checkTenantAuthorization(tenantContext.currentContext());
            }
        }

        // If the user is trying to invoke a role-mapping operation, deny it if
        // database based RBAC is not enabled.
        RoleBasedAccessApiOperation rbacOpAnnotation = context.getMethod().getAnnotation(RoleBasedAccessApiOperation.class);
        if (rbacOpAnnotation != null) {
            if (!authConfig.isApplicationRbacEnabled()) {
                log.warn("Access to /admin/roleMappings denied because application managed RBAC is not enabled.");
                throw new ForbiddenException("Application RBAC not enabled.");
            }
        }

        // If authentication is not enabled, just do it.
        if (!authConfig.authenticationEnabled) {
            return context.proceed();
        }

        log.trace("Authentication enabled, protected resource: " + context.getMethod());

        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);

        // If the securityIdentity is not set (or is anonymous)...
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            // Anonymous users are allowed to perform "None" operations.
            if (annotation.level() == AuthorizedLevel.None) {
                log.trace("Anonymous user is being granted access to unprotected operation.");
                return context.proceed();
            }

            // Anonymous users are allowed to perform read-only operations, but only if
            // registry.auth.anonymous-read-access.enabled is set to 'true'
            if (authConfig.anonymousReadAccessEnabled.get() && annotation.level() == AuthorizedLevel.Read) {
                log.trace("Anonymous user is being granted access to read-only operation.");
                return context.proceed();
            }

            // Otherwise just fail - auth was enabled but no credentials provided.
            log.warn("Authentication credentials missing and required for protected endpoint.");
            throw new UnauthorizedException("User is not authenticated.");
        }

        log.trace("principalId:" + securityIdentity.getPrincipal().getName());

        // If the user is authenticated and the operation auth level is None, allow it
        if (annotation.level() == AuthorizedLevel.None) {
            return context.proceed();
        }

        // If the user is an admin (via the admin-override check) then there's no need to
        // check rbac or obac.
        if (adminOverride.isAdmin()) {
            log.trace("Admin override successful.");
            return context.proceed();
        }

        // If Authenticated read access is enabled, and the operation auth level is Read, allow it.
        if (authConfig.authenticatedReadAccessEnabled.get() && annotation.level() == AuthorizedLevel.Read) {
            return context.proceed();
        }

        // If RBAC is enabled, apply role based rules
        if (authConfig.roleBasedAuthorizationEnabled && !rbac.isAuthorized(context)) {
            log.warn("RBAC enabled and required role missing.");
            throw new ForbiddenException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        // If Owner-only is enabled, apply ownership rules
        if (authConfig.ownerOnlyAuthorizationEnabled.get() && !obac.isAuthorized(context)) {
            log.warn("OBAC enabled and operation not permitted due to wrong owner.");
            throw new ForbiddenException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        return context.proceed();
    }

    private void checkTenantAuthorization(RegistryTenantContext tenant) {
        if (authConfig.isAuthEnabled()) {
            if (!isTokenResolvable()) {
                log.debug("Tenant access attempted without JWT token for tenant {} [allowing because some endpoints allow anonymous access]", tenant.getTenantId());
                return;
            }
            String accessedOrganizationId = null;

            for (String organizationIdClaim : organizationIdClaims) {
                final Optional<Object> claimValue = jsonWebToken.get().claim(organizationIdClaim);
                if (claimValue.isPresent()) {
                    accessedOrganizationId = (String) claimValue.get();
                    break;
                }
            }

            if (null == accessedOrganizationId || !tenantCanAccessOrganization(tenant, accessedOrganizationId)) {
                log.warn("User not authorized to access tenant.");
                throw new TenantNotAuthorizedException("Tenant not authorized");
            }
        }
    }

    private boolean isTokenResolvable() {
        return jsonWebToken.isResolvable() && jsonWebToken.get().getRawToken() != null;
    }

    private boolean tenantCanAccessOrganization(RegistryTenantContext tenant, String accessedOrganizationId) {
        return tenant == null || accessedOrganizationId.equals(tenant.getOrganizationId());
    }

}
