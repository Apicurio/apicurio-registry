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

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.RoleMappingDto;

import java.util.List;

public class DatabaseRolesSource implements RolesSource {

    private final RegistryStorage registryStorage;

    public DatabaseRolesSource(RegistryStorage registryStorage) {
        this.registryStorage = registryStorage;
    }

    @Override
    public void createRoleMapping(String principalId, String role) throws RegistryStorageException {
        registryStorage.createRoleMapping(principalId, role);
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        return registryStorage.getRoleMappings();
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return registryStorage.getRoleMapping(principalId);
    }

    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return registryStorage.getRoleForPrincipal(principalId);
    }

    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        registryStorage.updateRoleMapping(principalId, role);
    }

    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        registryStorage.deleteRoleMapping(principalId);
    }
}
