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

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithLocalRoles;
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
import java.util.UUID;

/**
 * Tests local role mappings (managed in the database via the role-mapping API).
 *
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
@TestProfile(AuthTestProfileWithLocalRoles.class)
@Tag(ApicurioTestTags.SLOW)
public class AuthTestLocalRoles extends AbstractResourceTestBase {

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

    /**
     * @see io.apicurio.registry.AbstractResourceTestBase#createRestClientV2()
     */
    @Override
    protected RegistryClient createRestClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createClient(auth);
    }

    @Test
    public void testLocalRoles() throws Exception {
        Auth authAdmin = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        AdminClient clientAdmin = createAdminClient(authAdmin);

        Auth auth = new OidcAuth(httpClient, JWKSMockServer.NO_ROLE_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);

        // User is authenticated but no roles assigned yet - operations should fail.
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.listArtifactsInGroup("default");
        });
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        });
        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            client.createGlobalRule(rule);
        });

        // Now let's grant read-only access to the user.
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId(JWKSMockServer.NO_ROLE_CLIENT_ID);
        mapping.setRole(RoleType.READ_ONLY);
        clientAdmin.createRoleMapping(mapping);

        // Now the user should be able to read but nothing else
        client.listArtifactsInGroup("default");
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        });
        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            client.createGlobalRule(rule);
        });

        // Now let's update the user's access to Developer
        clientAdmin.updateRoleMapping(JWKSMockServer.NO_ROLE_CLIENT_ID, RoleType.DEVELOPER);

        // Now the user can read and write but not admin
        client.listArtifactsInGroup("default");
        client.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            Rule rule = new Rule();
            rule.setConfig(ValidityLevel.FULL.name());
            rule.setType(RuleType.VALIDITY);
            client.createGlobalRule(rule);
        });

        // Finally let's update the level to Admin
        clientAdmin.updateRoleMapping(JWKSMockServer.NO_ROLE_CLIENT_ID, RoleType.ADMIN);

        // Now the user can do everything
        client.listArtifactsInGroup("default");
        client.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        Rule rule = new Rule();
        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(RuleType.VALIDITY);
        client.createGlobalRule(rule);
    }
}
