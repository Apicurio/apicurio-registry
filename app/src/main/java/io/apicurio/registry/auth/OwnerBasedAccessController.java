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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.interceptor.InvocationContext;

import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.types.Current;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class OwnerBasedAccessController implements IAccessController {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see io.apicurio.registry.auth.IAccessController#isAuthorized(javax.interceptor.InvocationContext)
     */
    @Override
    public boolean isAuthorized(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedStyle style = annotation.style();
        AuthorizedLevel level = annotation.level();

        // Only protect level == Write operations
        if (level != AuthorizedLevel.Write) {
            return true;
        }

        if (style == AuthorizedStyle.GroupAndArtifact) {
            String groupId = getStringParam(context, 0);
            String artifactId = getStringParam(context, 1);
            return verifyArtifactCreatedBy(groupId, artifactId);
        } else if (style == AuthorizedStyle.GroupOnly && authConfig.ownerOnlyAuthorizationLimitGroupAccess) {
            String groupId = getStringParam(context, 0);
            return verifyGroupCreatedBy(groupId);
        } else if (style == AuthorizedStyle.ArtifactOnly) {
            String artifactId = getStringParam(context, 0);
            return verifyArtifactCreatedBy(null, artifactId);
        } else if (style == AuthorizedStyle.GlobalId) {
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

    private static String getStringParam(InvocationContext context, int index) {
        return (String) context.getParameters()[index];
    }

    private static Long getLongParam(InvocationContext context, int index) {
        return (Long) context.getParameters()[index];
    }

}
