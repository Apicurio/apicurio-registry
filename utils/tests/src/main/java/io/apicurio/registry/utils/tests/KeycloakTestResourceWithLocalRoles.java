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

package io.apicurio.registry.utils.tests;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class KeycloakTestResourceWithLocalRoles implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestResource.class);

    private KeycloakContainer container;

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
        props.put("registry.auth.role-based-authorization", "true");
        props.put("registry.auth.role-source", "application");

        return props;
    }

    @Override
    public void stop() {
        log.info("Stopping Keycloak Test Container");
        container.stop();
        container.close();
    }
}
