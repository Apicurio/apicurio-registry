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

import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.RoleMappingDto;

import java.util.List;

public interface RolesSource {

    /**
     * Creates a role mapping for a user.
     * @param principalId
     * @param role
     */
    public void createRoleMapping(String principalId, String role) throws RegistryStorageException;

    /**
     * Gets the list of all the role mappings in the registry.
     */
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException;

    /**
     * Gets the details of a single role mapping.
     * @param principalId
     */
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException;

    /**
     * Gets the role for a single user.  This returns null if there is no role mapped for
     * the given principal.
     * @param principalId
     */
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException;

    /**
     * Updates a single role mapping.
     * @param principalId
     * @param role
     */
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException;

    /**
     * Deletes a single role mapping.
     * @param principalId
     */
    public void deleteRoleMapping(String principalId) throws RegistryStorageException;
}
