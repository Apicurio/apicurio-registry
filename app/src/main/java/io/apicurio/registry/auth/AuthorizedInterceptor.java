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
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.types.Current;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * @author eric.wittmann@gmail.com
 */
@Authorized @Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuthorizedInterceptor {

    @Inject
    Logger log;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "registry.auth.enabled", defaultValue = "false")
    boolean authenticationEnabled;

    @ConfigProperty(name = "registry.auth.role-based-authorization", defaultValue = "false")
    boolean roleBasedAuthorizationEnabled;

    @ConfigProperty(name = "registry.auth.owner-only-authorization", defaultValue = "false")
    boolean ownerOnlyAuthorizationEnabled;

    @ConfigProperty(name = "registry.auth.roles.readonly", defaultValue = "sr-readonly")
    String readOnlyRole;

    @ConfigProperty(name = "registry.auth.roles.developer", defaultValue = "sr-developer")
    String developerRole;

    @ConfigProperty(name = "registry.auth.roles.admin", defaultValue = "sr-admin")
    String adminRole;

    @AroundInvoke
    public Object authorizeMethod(InvocationContext context) throws Exception {
        // If authentication is not enabled, just do it.
        if (!authenticationEnabled) {
            return context.proceed();
        }

        // If authentication is enabled, but the securityIdentity is not set, then we have an authentication failure.
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
            if (annotation.level() != AuthorizedLevel.None) {
                throw new AuthenticationFailedException("User is not authenticated.");
            }
        }

        // If RBAC is enabled, apply role based rules
        if (roleBasedAuthorizationEnabled && !isRoleAllowed(context)) {
            throw new UnauthorizedException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        // If Owner-only is enabled, apply ownership rules
        if (ownerOnlyAuthorizationEnabled && !isOwnerAllowed(context)) {
            throw new UnauthorizedException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        return context.proceed();
    }

    /**
     * Checks whether the user has the role necessary to invoke the operation.  The role names
     * can be configured in application.properties.
     * @param context
     */
    private boolean isRoleAllowed(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedLevel level = annotation.level();

        switch (level) {
            case Admin:
                return isAdmin();
            case None:
                return true;
            case Read:
                return canRead();
            case Write:
                return canWrite();
            default:
                throw new RuntimeException("Unhandled case: " + level);
        }
    }

    /**
     * Checks the invocation context for the groupId and artifactId of the artifact being
     * changed.  Checks the createdBy field of the artifact against the principal of the
     * currently authenticated user.  If they are the same, then the operation is allowed.
     * @param context
     */
    private boolean isOwnerAllowed(InvocationContext context) {
        if (isAdmin()) {
            return true;
        }

        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedStyle mode = annotation.style();
        AuthorizedLevel level = annotation.level();

        // Don't protected 'read-only' or 'none' level operations.
        if (level == AuthorizedLevel.Read || level == AuthorizedLevel.None) {
            return true;
        }

        if (mode == AuthorizedStyle.GroupAndArtifact) {
            String groupId = getStringParam(context, 0);
            String artifactId = getStringParam(context, 1);
            return verifyArtifactCreatedBy(groupId, artifactId);
        } else if (mode == AuthorizedStyle.GroupOnly) {
            String groupId = getStringParam(context, 0);
            return verifyGroupCreatedBy(groupId);
        } else if (mode == AuthorizedStyle.ArtifactOnly) {
            String artifactId = getStringParam(context, 0);
            return verifyArtifactCreatedBy(null, artifactId);
        } else if (mode == AuthorizedStyle.GlobalId) {
            long globalId = getLongParam(context, 0);
            return verifyArtifactCreatedBy(globalId);
        } else {
            return true;
        }
    }

    private boolean verifyGroupCreatedBy(String groupId) {
        try {
            GroupMetaDataDto dto = storage.getGroupMetaData(groupId);
            String createdBy = dto.getCreatedBy();
            return createdBy == null || createdBy.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the group is not found, then return true and let the operation proceed.
            return true;
        }
    }

    private boolean verifyArtifactCreatedBy(String groupId, String artifactId) {
        try {
            ArtifactMetaDataDto dto = storage.getArtifactMetaData(groupId, artifactId);
            String createdBy = dto.getCreatedBy();
            return createdBy == null || createdBy.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the artifact is not found, then return true and let the operation proceed
            // as normal. The result of which will typically be a 404 response, but sometimes
            // will be some other result (e.g. creating an artifact that doesn't exist)
            return true;
        }
    }

    private boolean verifyArtifactCreatedBy(long globalId) {
        try {
            ArtifactMetaDataDto dto = storage.getArtifactMetaData(globalId);
            String createdBy = dto.getCreatedBy();
            return createdBy == null || createdBy.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the artifact is not found, then return true and let the operation proceed
            // as normal. The result of which will typically be a 404 response, but sometimes
            // will be some other result (e.g. creating an artifact that doesn't exist)
            return true;
        }
    }

    private boolean canWrite() {
        return hasRole(developerRole) || hasRole(adminRole);
    }

    private boolean canRead() {
        return hasRole(readOnlyRole) || hasRole(developerRole) || hasRole(adminRole);
    }

    private boolean isAdmin() {
        return hasRole(adminRole);
    }

    private boolean hasRole(String roleName) {
        return securityIdentity.hasRole(roleName);
    }

    private static String getStringParam(InvocationContext context, int index) {
        return (String) context.getParameters()[index];
    }

    private static Long getLongParam(InvocationContext context, int index) {
        return (Long) context.getParameters()[index];
    }

}
