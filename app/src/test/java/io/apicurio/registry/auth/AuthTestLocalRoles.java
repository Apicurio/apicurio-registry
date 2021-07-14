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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ForbiddenException;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfileWithLocalRoles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Tests local role mappings (managed in the database via the role-mapping API).
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
@TestProfile(AuthTestProfileWithLocalRoles.class)
@Tag(ApicurioTestTags.DOCKER)
public class AuthTestLocalRoles extends AbstractResourceTestBase {

    private static final String TEST_CONTENT = "{\r\n" +
            "    \"type\" : \"record\",\r\n" +
            "    \"name\" : \"userInfo\",\r\n" +
            "    \"namespace\" : \"my.example\",\r\n" +
            "    \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\r\n" +
            "} ";

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String realm;

    String noRoleClientId = "registry-api-no-role";
    String noRolePrincipalId = "service-account-registry-api-no-role";
    String adminClientId = "registry-api";

    final String groupId = "authTestGroupId";

    private RegistryClient createClient(Auth auth) {
        return RegistryClientFactory.create(registryV2ApiUrl, Collections.emptyMap(), auth);
    }

    /**
     * @see io.apicurio.registry.AbstractResourceTestBase#createRestClientV2()
     */
    @Override
    protected RegistryClient createRestClientV2() {
        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        return this.createClient(auth);
    }

    @Test
    public void testLocalRoles() throws Exception {
        Auth authAdmin = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        RegistryClient clientAdmin = createClient(authAdmin);

        Auth auth = new KeycloakAuth(authServerUrl, realm, noRoleClientId, "test1");
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

        // But the admin user can still do stuff due to admin-override being enabled
        clientAdmin.listArtifactsInGroup("default");
        clientAdmin.listGlobalRules();

        // Now let's grant read-only access to the user.
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId(noRolePrincipalId);
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
        clientAdmin.updateRoleMapping(noRolePrincipalId, RoleType.DEVELOPER);

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
        clientAdmin.updateRoleMapping(noRolePrincipalId, RoleType.ADMIN);

        // Now the user can do everything
        client.listArtifactsInGroup("default");
        client.createArtifact(getClass().getSimpleName(), UUID.randomUUID().toString(), new ByteArrayInputStream(TEST_CONTENT.getBytes(StandardCharsets.UTF_8)));
        Rule rule = new Rule();
        rule.setConfig(ValidityLevel.FULL.name());
        rule.setType(RuleType.VALIDITY);
        client.createGlobalRule(rule);
    }
//
//    @Test
//    public void testAdminOverride() throws Exception {
//        Auth auth = new KeycloakAuth(authServerUrl, realm, noRoleClientId, "test1");
//        RegistryClient client = createClient(auth);
//        String artifactId = TestUtils.generateArtifactId();
//        try {
//            client.listArtifactsInGroup(groupId);
//            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
//            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));
//            assertNotNull(client.getLatestArtifact(groupId, artifactId));
//            Rule ruleConfig = new Rule();
//            ruleConfig.setType(RuleType.VALIDITY);
//            ruleConfig.setConfig(ValidityLevel.NONE.name());
//            client.createArtifactRule(groupId, artifactId, ruleConfig);
//
//            client.createGlobalRule(ruleConfig);
//        } finally {
//            client.deleteArtifact(groupId, artifactId);
//        }
//    }
}
