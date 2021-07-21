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

import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.ClientRepresentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeycloakRolesSource implements RolesSource {

    private static final String GRANT_TYPE = "client_credentials";

    private final AuthConfig authConfig;
    private final TenantContext tenantContext;
    Keycloak keycloak;

    public KeycloakRolesSource(AuthConfig authConfig, TenantContext tenantContext) {
        this.authConfig = authConfig;
        this.tenantContext = tenantContext;
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(authConfig.getAdminAuthServerUrl())
                .realm(authConfig.getAdminRealm())
                .clientId(authConfig.getAdminClientId())
                .clientSecret(authConfig.getAdminClientSecret())
                .grantType(GRANT_TYPE)
                .build();
    }

    @Override
    public void createRoleMapping(String principalId, String role) throws RegistryStorageException {

        final RoleResource roleResource = keycloak.realm(authConfig.getAdminRealm())
                .clients()
                .get(tenantContext.tenantId())
                .roles()
                .get(role);
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        final List<RoleMappingDto> roleMappings = new ArrayList<>();
        final List<ClientRepresentation> tenantClientRepresentation = keycloak.realm(authConfig.getAdminRealm())
                .clients()
                .findByClientId(tenantContext.tenantId());
        if (tenantClientRepresentation.size() == 1) {
            final ClientResource clientResource = keycloak.realm(authConfig.getAdminRealm()).clients().get(tenantClientRepresentation.get(0).getId());
            clientResource.roles().list().forEach(roleRepresentation -> {
                final RoleResource roleResource = clientResource.roles().get(roleRepresentation.getName());
                roleResource.getRoleUserMembers().forEach(userRepresentation -> {
                    final RoleMappingDto roleMappingDto = new RoleMappingDto();
                    roleMappingDto.setPrincipalId(userRepresentation.getUsername());
                    roleMappingDto.setRole(roleRepresentation.getName());
                    roleMappings.add(roleMappingDto);
                });

            });
            return roleMappings;
        }
        return Collections.emptyList();
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return null;
    }

    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return null;
    }

    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {

    }

    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {

    }
}
