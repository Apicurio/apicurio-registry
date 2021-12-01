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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class StorageRoleProvider implements RoleProvider {

    @Inject
    SecurityIdentity securityIdentity;

    //We need to inject the identityToken so we can check some claims when needed.
    @Inject
    Instance<JsonWebToken> identityToken;

    private static final String AZP_CLAIM = "azp";

    @Inject
    @Current
    RegistryStorage storage;

    private boolean hasRole(String role) {
        String role4principal = storage.getRoleForPrincipal(securityIdentity.getPrincipal().getName());
        boolean hasRole = role.equals(role4principal);
        //Check for Keycloak service accounts since they're prefixed with service-account.
        if (!hasRole && tokenHasAzpClaim()) {
            hasRole = role.equals(storage.getRoleForPrincipal(identityToken.get().getClaim(AZP_CLAIM)));
        }
        return hasRole;
    }

    private boolean tokenHasAzpClaim() {
        return identityToken.isResolvable() && identityToken.get().getClaim(AZP_CLAIM) != null;
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isDeveloper()
     */
    @Override
    public boolean isDeveloper() {
        return hasRole(RoleType.DEVELOPER.name());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return hasRole(RoleType.READ_ONLY.name());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isAdmin()
     */
    @Override
    public boolean isAdmin() {
        return hasRole(RoleType.ADMIN.name());
    }

}
