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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * @author Fabian Martinez
 */
public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestResource.class);

    private KeycloakContainer container;
    String testUsername = "sr-test-user";
    String testPassword = "sr-test-password";

    @SuppressWarnings("resource")
    @Override
    public Map<String, String> start() {
        log.info("Starting Keycloak Test Container");

        container = new KeycloakContainer()
                .withRealmImportFile("test-realm.json");
        container.start();

        Map<String, String> props = new HashMap<>();
        props.put("registry.keycloak.url", container.getAuthServerUrl());
        props.put("registry.keycloak.realm", "registry");
        props.put("registry.auth.enabled", "true");
        props.put("quarkus.oidc.client-secret", "test1");
        props.put("registry.auth.owner-only-authorization", "true");

        createTestUser();

        return props;
    }

    private void createTestUser() {
        Keycloak keycloakAdminClient = KeycloakBuilder.builder()
                .serverUrl(container.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(container.getAdminUsername())
                .password(container.getAdminPassword())
                .build();

        final UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(testUsername);
        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(true);

        final CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setValue(testPassword);
        credentialRepresentation.setTemporary(false);

        userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));

        keycloakAdminClient.realm("registry")
                .users()
                .create(userRepresentation);

        final RoleRepresentation adminRoleRepresentation = keycloakAdminClient.realm("registry")
                .roles()
                .get("sr-admin")
                .toRepresentation();

        final UserRepresentation user = keycloakAdminClient.realm("registry")
                .users()
                .search(testUsername)
                .get(0);

        keycloakAdminClient.realm("registry")
                .users()
                .get(user.getId())
                .roles()
                .realmLevel()
                .add(List.of(adminRoleRepresentation));
    }

    @Override
    public void stop() {
        log.info("Stopping Keycloak Test Container");
        container.stop();
        container.close();
    }

}
