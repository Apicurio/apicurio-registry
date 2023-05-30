/*
 * Copyright 2023 Red Hat
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

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithHeaderRoles;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.rest.client.config.ApicurioClientConfig.APICURIO_REQUEST_HEADERS_PREFIX;

@QuarkusTest
@TestProfile(AuthTestProfileWithHeaderRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class HeaderRoleSourceTest extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" +
            "    \"type\" : \"record\",\r\n" +
            "    \"name\" : \"userInfo\",\r\n" +
            "    \"namespace\" : \"my.example\",\r\n" +
            "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
            "} ";

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    ApicurioHttpClient httpClient;

    @Override
    protected AdminClient createAdminClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createAdminClient(auth);
    }

    protected RegistryClient createAdminClientRole(Auth auth) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(APICURIO_REQUEST_HEADERS_PREFIX + "x-registry-role", "sr-admin");
        return RegistryClientFactory.create(registryV2ApiUrl, headers, auth);
    }

    protected RegistryClient createDevClientRole(Auth auth) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(APICURIO_REQUEST_HEADERS_PREFIX + "x-registry-role", "sr-developer");
        return RegistryClientFactory.create(registryV2ApiUrl, headers, auth);
    }

    protected RegistryClient createNoRoleClient(Auth auth) {
        return RegistryClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    protected RegistryClient createReadClientRole(Auth auth) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(APICURIO_REQUEST_HEADERS_PREFIX + "x-registry-role", "sr-readonly");
        return RegistryClientFactory.create(registryV2ApiUrl, headers, auth);
    }

    @Test
    public void testLocalRoles() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1");
        RegistryClient noRoleClient = createNoRoleClient(auth);

        Auth authRead = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient readClient = createReadClientRole(authRead);

        Auth authDev = new OidcAuth(httpClient, JWKSMockServer.READONLY_CLIENT_ID, "test1");
        RegistryClient devClient = createDevClientRole(authDev);

        Auth authAdmin = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        RegistryClient adminClient = createAdminClientRole(authAdmin);


        // User is authenticated but no roles assigned - operations should fail.
        Assertions.assertThrows(ForbiddenException.class, () -> {
            noRoleClient.listArtifactsInGroup("default");
        });

        Assertions.assertThrows(ForbiddenException.class, () -> {
            noRoleClient.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        });

        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            noRoleClient.createGlobalRule(rule);
        });


        // Now using the read client user should be able to read but nothing else
        readClient.listArtifactsInGroup("default");
        Assertions.assertThrows(ForbiddenException.class, () -> {
            readClient.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        });
        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            readClient.createGlobalRule(rule);
        });


        // the user can read and write with the developer client but not admin
        devClient.listArtifactsInGroup("default");
        devClient.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            devClient.createGlobalRule(rule);
        });

        // the user can do everything with the admin client
        adminClient.listArtifactsInGroup("default");
        adminClient.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        Rule rule = new Rule();
        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(RuleType.VALIDITY);
        adminClient.createGlobalRule(rule);
    }
}
