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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.client.exception.ForbiddenException;
import io.apicurio.registry.rest.client.exception.NotAuthorizedException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(AuthTestProfile.class)
public class SimpleAuthTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String realm;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    Boolean authEnabled;

    String adminClientId = "registry-api";
    String developerClientId = "registry-api-dev";
    String readOnlyClientId = "registry-api-readonly";

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
    public void testWrongCreds() throws Exception {
        Auth auth = new KeycloakAuth(authServerUrl, realm, readOnlyClientId, "test55");
        RegistryClient client = createClient(auth);
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifactsInGroup(groupId);
        });
    }

    @Test
    public void testReadOnly() throws Exception {
        Auth auth = new KeycloakAuth(authServerUrl, realm, readOnlyClientId, "test1");
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();
        client.listArtifactsInGroup(groupId);
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getArtifactMetaData(groupId, artifactId));
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getLatestArtifact("abc", artifactId));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact("ccc", artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
        {
            Auth devAuth = new KeycloakAuth(authServerUrl, realm, developerClientId, "test1");
            RegistryClient devClient = createClient(devAuth);
            ArtifactMetaData meta = devClient.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> devClient.getArtifactMetaData(groupId, meta.getId()));
        }
        assertNotNull(client.getLatestArtifact(groupId, artifactId));
    }

    @Test
    public void testDevRole() throws Exception {
        Auth auth = new KeycloakAuth(authServerUrl, realm, developerClientId, "test1");
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifactsInGroup(groupId);

            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));

            assertNotNull(client.getLatestArtifact(groupId, artifactId));

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.createArtifactRule(groupId, artifactId, ruleConfig);

            Assertions.assertThrows(ForbiddenException.class, () -> {
                client.createGlobalRule(ruleConfig);
            });
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifactsInGroup(groupId);
            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaData(groupId, artifactId));
            assertNotNull(client.getLatestArtifact(groupId, artifactId));
            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.createArtifactRule(groupId, artifactId, ruleConfig);

            client.createGlobalRule(ruleConfig);
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        Auth authDev = new KeycloakAuth(authServerUrl, realm, developerClientId, "test1");
        RegistryClient clientDev = createClient(authDev);

        Auth authAdmin = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        RegistryClient clientAdmin = createClient(authAdmin);

        // Admin user will create an artifact
        String artifactId = TestUtils.generateArtifactId();
        clientAdmin.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));

        EditableMetaData updatedMetaData = new EditableMetaData();
        updatedMetaData.setName("Updated Name");
        // Dev user cannot edit the same artifact because Dev user is not the owner
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            clientDev.updateArtifactMetaData(groupId, artifactId, updatedMetaData );
        });

        // But the admin user CAN make the change.
        clientAdmin.updateArtifactMetaData(groupId, artifactId, updatedMetaData );



        // Now the Dev user will create an artifact
        String artifactId2 = TestUtils.generateArtifactId();
        clientDev.createArtifact(groupId, artifactId2, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));

        // And the Admin user will modify it (allowed because it's the Admin user)
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientAdmin.createArtifactRule(groupId, artifactId2, rule );
    }
}