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

package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import no.nav.security.mock.oauth2.OAuth2Config;
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback;
import no.nav.security.mock.oauth2.token.OAuth2TokenProvider;
import java.util.List;
import java.util.Set;

/**
 * Test resource manager wrapping {@link MockOAuth2Server}, providing a lightweight, embeddable
 * OIDC provider for tests that need auth but don't need a full Keycloak instance.
 *
 * <p>Starts in milliseconds with no Docker dependency, unlike
 * {@link KeycloakTestContainerManager}. Use this for tests that only need bearer token
 * validation or role-based access checks (roles are JWT claims, not Keycloak-specific). Stay on
 * {@link KeycloakTestContainerManager} for anything needing real Keycloak realm configuration,
 * TLS with Keycloak's certificates, or the Keycloak Admin API.
 *
 * <p>Supports init args for customization:
 * <ul>
 *   <li>{@code issuer.id} - the mock server's issuer identifier (default: "default")</li>
 *   <li>{@code token.expiry} - default token expiry in seconds, exposed to tests via
 *       {@link #getDefaultTokenExpirySeconds()} for convenience when issuing custom tokens
 *       (default: 3600)</li>
 * </ul>
 */
public class MockOAuth2TestResource implements QuarkusTestResourceLifecycleManager {

    static final Logger LOGGER = LoggerFactory.getLogger(MockOAuth2TestResource.class);

    public static final String DEFAULT_ISSUER_ID = "default";
    public static final String DEFAULT_CLIENT_ID = "test-client";
    public static final String DEFAULT_CLIENT_SECRET = "test-secret";
    public static final String ADMIN_CLIENT_ID = "admin-client";
    public static final String DEVELOPER_CLIENT_ID = "developer-client";
    public static final String READONLY_CLIENT_ID = "readonly-client";
    private static final long DEFAULT_TOKEN_EXPIRY_SECONDS = 3600;

    // Mirrors the realm-role-to-client mapping in utils/tests/src/main/resources/realm.json
    // (the Keycloak-based fixture) so mock-issued tokens carry the same "groups" claim
    // Apicurio's role-based authorization expects.
    private static final Map<String, List<String>> CLIENT_ID_TO_ROLES = Map.of(
            ADMIN_CLIENT_ID, List.of("sr-admin"),
            DEVELOPER_CLIENT_ID, List.of("sr-developer"),
            READONLY_CLIENT_ID, List.of("sr-readonly")
    );

    private MockOAuth2Server server;
    private String issuerId = DEFAULT_ISSUER_ID;
    private long defaultTokenExpirySeconds = DEFAULT_TOKEN_EXPIRY_SECONDS;

    @Override
    public void init(Map<String, String> initArgs) {
        if (initArgs == null) {
            return;
        }

        String issuerIdArg = initArgs.get("issuer.id");
        if (issuerIdArg != null && !issuerIdArg.isBlank()) {
            issuerId = issuerIdArg;
        }

        String tokenExpiryArg = initArgs.get("token.expiry");
        if (tokenExpiryArg != null && !tokenExpiryArg.isBlank()) {
            try {
                defaultTokenExpirySeconds = Long.parseLong(tokenExpiryArg);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid token.expiry value '{}', falling back to default of {}s",
                        tokenExpiryArg, DEFAULT_TOKEN_EXPIRY_SECONDS);
            }
        }
    }

    @Override
    public Map<String, String> start() {
        OAuth2TokenCallback roleClaimCallback =
                new RoleClaimTokenCallback(issuerId, CLIENT_ID_TO_ROLES, defaultTokenExpirySeconds);
        server = new MockOAuth2Server(new OAuth2Config(
                false, null, null, false,
                new OAuth2TokenProvider(),
                Set.of(roleClaimCallback)
        ));

        LOGGER.info("Starting mock-oauth2-server...");
        try {
            server.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start mock-oauth2-server", e);
            throw new RuntimeException("Failed to start mock-oauth2-server", e);
        }
        LOGGER.info("mock-oauth2-server started at {}", server.baseUrl());

        String issuerUrl = server.issuerUrl(issuerId).toString();
        String tokenUrl = server.tokenEndpointUrl(issuerId).toString();

        Map<String, String> props = new HashMap<>();
        props.put("quarkus.oidc.auth-server-url", issuerUrl);
        props.put("quarkus.oidc.token-path", tokenUrl);
        props.put("quarkus.oidc.client-id", DEFAULT_CLIENT_ID);
        props.put("quarkus.oidc.credentials.secret", DEFAULT_CLIENT_SECRET);
        props.put("quarkus.oidc.tenant-enabled", "true");
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.owner-only-authorization", "true");
        props.put("apicurio.auth.admin-override.enabled", "true");
        props.put("apicurio.authn.basic-client-credentials.enabled", "true");

        LOGGER.info("mock-oauth2-server properties: {}", props);
        return props;
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(server,
                new TestInjector.AnnotatedAndMatchesType(InjectMockOAuth2Server.class, MockOAuth2Server.class));
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.shutdown();
            LOGGER.info("mock-oauth2-server was shut down");
            server = null;
        }
    }

    public long getDefaultTokenExpirySeconds() {
        return defaultTokenExpirySeconds;
    }
}
