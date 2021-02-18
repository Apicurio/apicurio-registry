/*
 * Copyright 2020 Red Hat
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
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.client.exception.ForbiddenException;
import io.apicurio.registry.client.exception.NotAuthorizedException;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
public class SimpleAuthTest extends AbstractResourceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleAuthTest.class);

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String realm;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    Boolean authEnabled;

    String adminClientId = "registry-api";
    String developerClientId = "registry-api-dev";
    String readOnlyClientId = "registry-api-readonly";

    String clientSecret = "test1";

    @Override
    @BeforeAll
    protected void beforeAll() throws Exception {
        System.out.println("Auth is " + authEnabled);
        registryUrl = "http://localhost:8081/api";
        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);
    }

    @AfterEach
    void cleanArtifacts() throws Exception {
        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");
        RegistryRestClient client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);
        List<String> artifacts = client.listArtifacts();
        for (String artifactId : artifacts) {
            try {
                client.deleteArtifact(artifactId);
            } catch (AssertionError e) {
                //because of async storage artifact may be already deleted but listed anyway
                LOGGER.info(e.getMessage());
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
    }

    @Test
    public void testWrongCreds() throws Exception {

        Auth auth = new KeycloakAuth(authServerUrl, realm, readOnlyClientId, "test55");

        RegistryRestClient client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);

        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifacts();
        });

    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testReadOnly() throws Exception {

        Auth auth = new KeycloakAuth(authServerUrl, realm, readOnlyClientId, "test1");

        RegistryRestClient client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);

        client.listArtifacts();

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            client.getArtifactByGlobalId(2);
        });

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> {
            client.getLatestArtifact("abc");
        });

        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact("ccc", ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });

        String artifactId = TestUtils.generateArtifactId();
        {
            Auth devAuth = new KeycloakAuth(authServerUrl, realm, developerClientId, "test1");
            RegistryRestClient devClient = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), devAuth);
            ArtifactMetaData meta = devClient.createArtifact(artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> devClient.getArtifactMetaDataByGlobalId(meta.getGlobalId()));
        }

        assertNotNull(client.getLatestArtifact(artifactId));

    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testDevRole() throws Exception {

        Auth auth = new KeycloakAuth(authServerUrl, realm, developerClientId, "test1");

        RegistryRestClient client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);

        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifacts();

            ArtifactMetaData meta = client.createArtifact(artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(meta.getGlobalId()));

            assertNotNull(client.getLatestArtifact(meta.getId()));

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.createArtifactRule(meta.getId(), ruleConfig);

            Assertions.assertThrows(ForbiddenException.class, () -> {
                client.createGlobalRule(ruleConfig);
            });
        } finally {
            client.deleteArtifact(artifactId);
        }



    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testAdminRole() throws Exception {

        Auth auth = new KeycloakAuth(authServerUrl, realm, adminClientId, "test1");

        RegistryRestClient client = RegistryRestClientFactory.create(registryUrl, Collections.emptyMap(), auth);

        String artifactId = TestUtils.generateArtifactId();
        try {
            client.listArtifacts();

            ArtifactMetaData meta = client.createArtifact(artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> client.getArtifactMetaDataByGlobalId(meta.getGlobalId()));

            assertNotNull(client.getLatestArtifact(meta.getId()));

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.createArtifactRule(meta.getId(), ruleConfig);

            client.createGlobalRule(ruleConfig);
        } finally {
            client.deleteArtifact(artifactId);
        }

    }

}
