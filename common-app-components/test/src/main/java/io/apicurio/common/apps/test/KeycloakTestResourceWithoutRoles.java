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

package io.apicurio.common.apps.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeycloakTestResourceWithoutRoles implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestResourceWithoutRoles.class);

    private final JWKSMockServer server = new JWKSMockServer();

    @SuppressWarnings("resource")
    @Override
    public Map<String, String> start() {
        log.info("Starting Keycloak Test Server");

        server.start();

        Map<String, String> props = new HashMap<>();
        props.put("apicurio.keycloak.url", server.authServerUrl);
        props.put("apicurio.keycloak.realm", "test");
        props.put("apicurio.authn.enabled", "true");
        props.put("apicurio.authn.client-secret", "test1");

        // set tenant manager properties
        props.put("tenant-manager.auth.enabled", "true");
        props.put("tenant-manager.keycloak.url", server.authServerUrl);
        props.put("tenant-manager.keycloak.realm", "test");
        props.put("tenant-manager.authz.enabled", "true");
        props.put("smallrye.jwt.sign.key.location", "privateKey.jwk");

        return props;
    }

    @Override
    public void stop() {
        log.info("Stopping Keycloak Test Server");
        server.stop();
    }
}
