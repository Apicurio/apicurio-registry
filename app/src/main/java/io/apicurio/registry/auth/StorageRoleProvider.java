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
import javax.inject.Inject;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class StorageRoleProvider implements RoleProvider {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    private boolean hasRole(String role) {
        String role4principal = storage.getRoleForPrincipal(securityIdentity.getPrincipal().getName());
        return role.equals(role4principal);
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
