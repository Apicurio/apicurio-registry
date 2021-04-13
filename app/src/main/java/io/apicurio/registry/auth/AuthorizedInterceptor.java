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
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.types.Current;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * @author eric.wittmann@gmail.com
 */
@Interceptor
@Authorized
public class AuthorizedInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizedInterceptor.class);

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "registry.auth.enabled", defaultValue = "false")
    boolean authenticationEnabled;

    @ConfigProperty(name = "registry.auth.owner-only-authorization", defaultValue = "false")
    boolean authorizationEnabled;

    @ConfigProperty(name = "registry.auth.roles.admin", defaultValue = "sr-admin")
    String adminRole;

    @PostConstruct
    public void onConstruct() {
        if (isAuthEnabled()) {
            log.info("*** Only-only authorization is enabled ***");
        }
    }

    private boolean isAuthEnabled() {
        return authenticationEnabled && authorizationEnabled;
    }

    @AroundInvoke
    public Object authorizeMethod(InvocationContext context) throws Exception {
        if (!isAuthEnabled() || isAllowed(context)) {
            return context.proceed();
        } else {
            throw new UnauthorizedException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }
    }

    /**
     * Checks the invocation context for the groupId and artifactId of the artifact being
     * changed.  Checks the createdBy field of the artifact against the principal of the
     * currently authenticated user.  If they are the same, then the operation is allowed.
     * @param context
     */
    private boolean isAllowed(InvocationContext context) {
        if (isAdmin()) {
            return true;
        }

        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedStyle mode = annotation.value();

        if (mode == AuthorizedStyle.GroupAndArtifact) {
            String groupId = getStringParam(context, 0);
            String artifactId = getStringParam(context, 1);
            return verifyArtifactCreatedBy(groupId, artifactId);
        } else if (mode == AuthorizedStyle.GroupOnly) {
            String groupId = getStringParam(context, 0);
            return verifyGroupCreatedBy(groupId);
        } else if (mode == AuthorizedStyle.ArtifactOnly) {
            String artifactId = getStringParam(context, 1);
            return verifyArtifactCreatedBy(null, artifactId);
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

    private boolean isAdmin() {
        return securityIdentity.hasRole(adminRole);
    }

    private static String getStringParam(InvocationContext context, int index) {
        return (String) context.getParameters()[index];
    }

}
