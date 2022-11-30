/*
 * Copyright 2022 Red Hat
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.smallrye.jwt.build.Jwt;

/**
 * @author Carles Arnal
 */
public class JWKSMockServer implements QuarkusTestResourceLifecycleManager {

    static final Logger LOGGER = LoggerFactory.getLogger(JWKSMockServer.class);

    private WireMockServer server;

    public String authServerUrl;
    public String realm = "test";
    public String tokenEndpoint;

    public static String ADMIN_CLIENT_ID = "admin-client";
    public static String DEVELOPER_CLIENT_ID = "developer-client";
    public static String DEVELOPER_2_CLIENT_ID = "developer-2-client";
    public static String READONLY_CLIENT_ID = "readonly-client";

    public static String NO_ROLE_CLIENT_ID = "no-role-client";
    public static String WRONG_CREDS_CLIENT_ID = "wrong-creds-client";

    public static String BASIC_USER = "sr-test-user";
    public static String BASIC_PASSWORD = "sr-test-password";

    public static String BASIC_USER_A = "sr-test-user-a";
    public static String BASIC_USER_B = "sr-test-user-b";


    @Override
    public Map<String, String> start() {

        server = new WireMockServer(
                wireMockConfig()
                        .dynamicPort());
        server.start();

        server.stubFor(
                get(urlMatching("/auth/realms/" + realm + "/.well-known/uma2-configuration"))
                        .willReturn(wellKnownResponse()));
        server.stubFor(
                get(urlMatching("/auth/realms/" + realm + "/.well-known/openid-configuration"))
                        .willReturn(wellKnownResponse()));

        server.stubFor(
                get(urlEqualTo("/auth/realms/" + realm + "/protocol/openid-connect/certs"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "  \"keys\" : [\n" +
                                        "    {\n" +
                                        "      \"kid\": \"1\",\n" +
                                        "      \"kty\":\"RSA\",\n" +
                                        "      \"n\":\"iJw33l1eVAsGoRlSyo-FCimeOc-AaZbzQ2iESA3Nkuo3TFb1zIkmt0kzlnWVGt48dkaIl13Vdefh9hqw_r9yNF8xZqX1fp0PnCWc5M_TX_ht5fm9y0TpbiVmsjeRMWZn4jr3DsFouxQ9aBXUJiu26V0vd2vrECeeAreFT4mtoHY13D2WVeJvboc5mEJcp50JNhxRCJ5UkY8jR_wfUk2Tzz4-fAj5xQaBccXnqJMu_1C6MjoCEiB7G1d13bVPReIeAGRKVJIF6ogoCN8JbrOhc_48lT4uyjbgnd24beatuKWodmWYhactFobRGYo5551cgMe8BoxpVQ4to30cGA0qjQ\",\n"
                                        +
                                        "      \"e\":\"AQAB\"\n" +
                                        "    }\n" +
                                        "  ]\n" +
                                        "}")));

        //Admin user stub
        stubForClient(ADMIN_CLIENT_ID);
        //Developer user stub
        stubForClient(DEVELOPER_CLIENT_ID);
        stubForClient(DEVELOPER_2_CLIENT_ID);
        //Read only user stub
        stubForClient(READONLY_CLIENT_ID);
        //Token without roles stub
        stubForClient(NO_ROLE_CLIENT_ID);

        //Stub for basic user
        server.stubFor(WireMock.post("/auth/realms/" + realm + "/protocol/openid-connect/token/")
                .withRequestBody(WireMock.containing("grant_type=client_credentials"))
                .withRequestBody(WireMock.containing("client_id=" + BASIC_USER))
                .withRequestBody(WireMock.containing("client_secret=" + BASIC_PASSWORD))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"access_token\": \""
                                + generateJwtToken(ADMIN_CLIENT_ID, null) + "\",\n" +
                                "  \"refresh_token\": \"07e08903-1263-4dd1-9fd1-4a59b0db5283\",\n" +
                                "  \"token_type\": \"bearer\"\n" +
                                "}")));

        //Stub for basic user a
        server.stubFor(WireMock.post("/auth/realms/" + realm + "/protocol/openid-connect/token/")
                .withRequestBody(WireMock.containing("grant_type=client_credentials"))
                .withRequestBody(WireMock.containing("client_id=" + BASIC_USER_A))
                .withRequestBody(WireMock.containing("client_secret=" + BASIC_PASSWORD))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"access_token\": \""
                                + generateJwtToken(ADMIN_CLIENT_ID, "aaa") + "\",\n" +
                                "  \"refresh_token\": \"07e08903-1263-4dd1-9fd1-4a59b0db5283\",\n" +
                                "  \"token_type\": \"bearer\"\n" +
                                "}")));

        //Stub for basic user b
        server.stubFor(WireMock.post("/auth/realms/" + realm + "/protocol/openid-connect/token/")
                .withRequestBody(WireMock.containing("grant_type=client_credentials"))
                .withRequestBody(WireMock.containing("client_id=" + BASIC_USER_B))
                .withRequestBody(WireMock.containing("client_secret=" + BASIC_PASSWORD))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"access_token\": \""
                                + generateJwtToken(ADMIN_CLIENT_ID, "bbb") + "\",\n" +
                                "  \"refresh_token\": \"07e08903-1263-4dd1-9fd1-4a59b0db5283\",\n" +
                                "  \"token_type\": \"bearer\"\n" +
                                "}")));


        //Wrong credentials stub
        server.stubFor(WireMock.post("/auth/realms/" + realm + "/protocol/openid-connect/token/")
                .withRequestBody(WireMock.containing("grant_type=client_credentials"))
                .withRequestBody(WireMock.containing("client_id=" + WRONG_CREDS_CLIENT_ID))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(401)));

        this.authServerUrl = server.baseUrl() + "/auth";
        LOGGER.info("Keycloak started in mock mode: {}", authServerUrl);
        this.tokenEndpoint = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        Map<String, String> props = new HashMap<>();

        //Set registry properties
        props.put("registry.keycloak.url", authServerUrl);
        props.put("tenant-manager.keycloak.url.configured", tokenEndpoint);
        props.put("registry.keycloak.realm", realm);
        props.put("registry.auth.enabled", "true");
        props.put("registry.auth.role-based-authorization", "true");
        props.put("registry.auth.owner-only-authorization", "true");
        props.put("registry.auth.admin-override.enabled", "true");
        props.put("registry.auth.basic-auth-client-credentials.enabled", "true");

        return props;
    }

    private ResponseDefinitionBuilder wellKnownResponse() {
        return aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\n" +
                        "    \"jwks_uri\": \"" + server.baseUrl()
                        + "/auth/realms/" + realm + "/protocol/openid-connect/certs\",\n"
                        + " \"token_endpoint\": \"" + server.baseUrl() + "/auth/realms/" + realm + "/protocol/openid-connect/token\" "
                        + "}");
    }

    private String generateJwtToken(String userName, String orgId) {
        var b = Jwt.preferredUserName(userName);

        if (userName.equals(ADMIN_CLIENT_ID)) {
            b.claim("groups", "sr-admin");
        } else if (userName.equals(DEVELOPER_CLIENT_ID)) {
            b.claim("groups", "sr-developer");
        } else if (userName.equals(DEVELOPER_2_CLIENT_ID)) {
            b.claim("groups", "sr-developer");
        } else if (userName.equals(READONLY_CLIENT_ID)) {
            b.claim("groups", "sr-readonly");
        }

        if (orgId != null) {
            b.claim("rh-org-id", orgId);
        }

        return b.jws()
                .keyId("1")
                .sign();
    }

    private void stubForClient(String client) {
        server.stubFor(WireMock.post("/auth/realms/" + realm + "/protocol/openid-connect/token/")
                .withRequestBody(WireMock.containing("grant_type=client_credentials"))
                .withRequestBody(WireMock.containing("client_id=" + client))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"access_token\": \""
                                + generateJwtToken(client, null) + "\",\n" +
                                "  \"refresh_token\": \"07e08903-1263-4dd1-9fd1-4a59b0db5283\",\n" +
                                "  \"token_type\": \"bearer\"\n" +
                                "}")));
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            LOGGER.info("Keycloak was shut down");
            server = null;
        }
    }
}
