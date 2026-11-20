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
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Decision-matrix coverage for {@link OwnershipTransferAuthorizer}.
 * HTTP end-to-end coverage lives in {@link BasicAuthWithPropertiesTest}.
 */
public class OwnershipTransferAuthorizerTest {

    @Test
    public void ownerCanTransfer() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("bob1", false, false, true);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange("bob1", "bob2"));
    }

    @Test
    public void nonOwnerCannotTransfer() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("bob2", false, false, true);
        assertThrows(ForbiddenException.class, () -> authorizer.authorizeOwnerChange("bob1", "bob2"));
    }

    @Test
    public void adminOverrideCanTransfer() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("alice", true, false, true);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange("bob1", "bob2"));
    }

    @Test
    public void rbacAdminCanTransfer() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("alice", false, true, true);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange("bob1", "bob2"));
    }

    @Test
    public void unownedCanBeClaimed() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("bob2", false, false, true);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange(null, "bob2"));
    }

    @Test
    public void noopSameOwnerAllowed() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("bob2", false, false, true);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange("bob1", "bob1"));
    }

    @Test
    public void skippedWhenAuthDisabled() {
        OwnershipTransferAuthorizer authorizer = newAuthorizer("bob2", false, false, false);
        assertDoesNotThrow(() -> authorizer.authorizeOwnerChange("bob1", "bob2"));
    }

    private OwnershipTransferAuthorizer newAuthorizer(String username, boolean adminOverrideActive,
            boolean rbacAdmin, boolean authEnabled) {
        OwnershipTransferAuthorizer authorizer = new OwnershipTransferAuthorizer();

        AuthConfig authConfig = mock(AuthConfig.class);
        when(authConfig.isAuthenticationEnabled()).thenReturn(authEnabled);
        when(authConfig.isRbacEnabled()).thenReturn(true);
        authorizer.authConfig = authConfig;

        AdminOverride adminOverride = mock(AdminOverride.class);
        when(adminOverride.isAdmin()).thenReturn(adminOverrideActive);
        authorizer.adminOverride = adminOverride;

        RoleBasedAccessController rbac = mock(RoleBasedAccessController.class);
        when(rbac.isAdmin()).thenReturn(rbacAdmin);
        authorizer.rbac = rbac;

        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(username);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.getPrincipal()).thenReturn(principal);
        authorizer.securityIdentity = identity;

        return authorizer;
    }
}
