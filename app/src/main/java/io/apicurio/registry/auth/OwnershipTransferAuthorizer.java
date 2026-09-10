/*
 * Copyright 2026 Red Hat
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

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;
import java.util.Objects;

/**
 * Authorizes ownership transfers for artifacts and groups.
 *
 * <p>Wired into the only V3 client-writable owner paths:
 * {@code GroupsResourceImpl#updateArtifactMetaData} and
 * {@code GroupsResourceImpl#updateGroupById}. Confirmed by grepping
 * {@code rest/v3/impl} for {@code data.getOwner()} / {@code setOwner} and checking
 * OpenAPI {@code Editable*} schemas in {@code common/.../openapi.json}: only
 * {@code EditableArtifactMetaData} and {@code EditableGroupMetaData} expose
 * {@code owner}; {@code EditableVersionMetaData} / {@code CreateArtifact} /
 * {@code CreateGroup} do not (create stamps owner from the principal); import is
 * Admin-only. V2 uses dedicated {@code updateArtifactOwner} with
 * {@link AuthorizedLevel#AdminOrOwner}.
 *
 * <p>Metadata update endpoints use {@link AuthorizedLevel#Write} so non-owners can still
 * change name/description/labels. Ownership changes require Admin or the current owner
 * (mirrors V2 {@code updateArtifactOwner} / {@link AuthorizedLevel#AdminOrOwner}).
 *
 * <p>Cannot be expressed solely via {@code @Authorized(level = AdminOrOwner)} because that
 * would block non-owners from updating non-owner metadata fields on the same endpoint.
 */
@ApplicationScoped
public class OwnershipTransferAuthorizer {

    private static final String TRANSFER_FORBIDDEN =
            "Only the current owner or an admin can transfer ownership.";

    @Inject
    AuthConfig authConfig;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Ensures the caller may set ownership to {@code requestedOwner}.
     *
     * <ul>
     *   <li>No-op when authentication is disabled</li>
     *   <li>No-op when {@code requestedOwner} equals the current owner</li>
     *   <li>Allowed for admins (admin-override or RBAC admin role)</li>
     *   <li>Allowed when the resource is unowned ({@code currentOwner == null})</li>
     *   <li>Allowed when the caller is the current owner</li>
     *   <li>Otherwise forbidden</li>
     * </ul>
     *
     * @param currentOwner the owner currently stored for the resource (may be null)
     * @param requestedOwner the non-empty owner value from the request
     */
    public void authorizeOwnerChange(String currentOwner, String requestedOwner) {
        if (!authConfig.isAuthenticationEnabled()) {
            return;
        }

        if (Objects.equals(currentOwner, requestedOwner)) {
            return;
        }

        if (isAdmin()) {
            return;
        }

        Principal principal = securityIdentity.getPrincipal();
        String currentUser = principal != null ? principal.getName() : null;

        // Unowned resources may be claimed by any authenticated Write user.
        if (currentOwner == null || currentOwner.equals(currentUser)) {
            return;
        }

        throw new ForbiddenException(TRANSFER_FORBIDDEN);
    }

    private boolean isAdmin() {
        return adminOverride.isAdmin() || (authConfig.isRbacEnabled() && rbac.isAdmin());
    }
}
