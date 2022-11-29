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
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.BasicAuth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class SimpleAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    final String groupId = "authTestGroupId";

    ApicurioHttpClient httpClient;

    /**
     * @see io.apicurio.registry.AbstractResourceTestBase#createRestClientV2()
     */
    @Override
    protected RegistryClient createRestClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createClient(auth);
    }

    @Override
    protected AdminClient createAdminClientV2() {
        httpClient = ApicurioHttpClientFactory.create(authServerUrlConfigured, new AuthErrorHandler());
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        return this.createAdminClient(auth);
    }

    @Test
    public void testWrongCreds() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55");
        RegistryClient client = createClient(auth);
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifactsInGroup(groupId);
        });
    }

    @Test
    public void testReadOnly() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.READONLY_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();
        client.listArtifactsInGroup(groupId);
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getArtifactMetaData(groupId, artifactId));
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> client.getLatestArtifact("abc", artifactId));
        Assertions.assertThrows(ForbiddenException.class, () -> {
            client.createArtifact("testReadOnly", artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
        {
            Auth devAuth = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
            RegistryClient devClient = createClient(devAuth);
            ArtifactMetaData meta = devClient.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
            TestUtils.retry(() -> devClient.getArtifactMetaData(groupId, meta.getId()));
        }
        assertNotNull(client.getLatestArtifact(groupId, artifactId));

        UserInfo userInfo = client.getCurrentUserInfo();
        assertNotNull(userInfo);
        Assertions.assertEquals("readonly-client", userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
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

            UserInfo userInfo = client.getCurrentUserInfo();
            assertNotNull(userInfo);
            Assertions.assertEquals("developer-client", userInfo.getUsername());
            Assertions.assertFalse(userInfo.getAdmin());
            Assertions.assertTrue(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);
        AdminClient adminClient = createAdminClient(auth);
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

            adminClient.createGlobalRule(ruleConfig);

            UserInfo userInfo = client.getCurrentUserInfo();
            assertNotNull(userInfo);
            Assertions.assertEquals("admin-client", userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testAdminRoleBasicAuth() throws Exception {

        Auth auth = new BasicAuth(JWKSMockServer.BASIC_USER, JWKSMockServer.BASIC_PASSWORD);

        RegistryClient client = createClient(auth);
        AdminClient adminClient = createAdminClient(auth);
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

            adminClient.createGlobalRule(ruleConfig);
        } finally {
            client.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testAdminRoleBasicAuthWrongCreds() throws Exception {
        Auth auth = new BasicAuth(JWKSMockServer.WRONG_CREDS_CLIENT_ID, UUID.randomUUID().toString());
        RegistryClient client = createClient(auth);
        String artifactId = TestUtils.generateArtifactId();

        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.listArtifactsInGroup(groupId);
        });
        Assertions.assertThrows(NotAuthorizedException.class, () -> {
            client.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        Auth authDev = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient clientDev = createClient(authDev);

        Auth authAdmin = new OidcAuth(httpClient, JWKSMockServer.ADMIN_CLIENT_ID, "test1");
        RegistryClient clientAdmin = createClient(authAdmin);

        // Admin user will create an artifact
        String artifactId = TestUtils.generateArtifactId();
        clientAdmin.createArtifact(groupId, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));

        EditableMetaData updatedMetaData = new EditableMetaData();
        updatedMetaData.setName("Updated Name");
        // Dev user cannot edit the same artifact because Dev user is not the owner
        Assertions.assertThrows(ForbiddenException.class, () -> {
            clientDev.updateArtifactMetaData(groupId, artifactId, updatedMetaData);
        });

        // But the admin user CAN make the change.
        clientAdmin.updateArtifactMetaData(groupId, artifactId, updatedMetaData);


        // Now the Dev user will create an artifact
        String artifactId2 = TestUtils.generateArtifactId();
        clientDev.createArtifact(groupId, artifactId2, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));

        // And the Admin user will modify it (allowed because it's the Admin user)
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientAdmin.createArtifactRule(groupId, artifactId2, rule);
    }

    @Test
    public void testGetArtifactOwner() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);

        //Preparation
        final String groupId = "testGetArtifactOwner";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testGetArtifactOwnerName";
        final String description = "testGetArtifactOwnerDescription";

        //Execution
        final InputStream stream = IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));
        final ArtifactMetaData created = client.createArtifact(groupId, artifactId, version, ArtifactType.JSON, IfExists.FAIL, false, name, description, stream);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.getArtifactOwner(groupId, artifactId);
        assertEquals("developer-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        Auth auth = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient client = createClient(auth);

        //Preparation
        final String groupId = "testUpdateArtifactOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerName";
        final String description = "testUpdateArtifactOwnerDescription";

        //Execution
        final InputStream stream = IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));
        final ArtifactMetaData created = client.createArtifact(groupId, artifactId, version, ArtifactType.JSON, IfExists.FAIL, false, name, description, stream);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.getArtifactOwner(groupId, artifactId);
        assertEquals("developer-client", owner.getOwner());

        //Update the owner
        owner = new ArtifactOwner();
        owner.setOwner("developer-2-client");
        client.updateArtifactOwner(groupId, artifactId, owner);

        //Check that the update worked
        owner = client.getArtifactOwner(groupId, artifactId);
        assertEquals("developer-2-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerOnlyByOwner() throws Exception {
        Auth auth_dev1 = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1");
        RegistryClient client_dev1 = createClient(auth_dev1);
        Auth auth_dev2 = new OidcAuth(httpClient, JWKSMockServer.DEVELOPER_2_CLIENT_ID, "test1");
        RegistryClient client_dev2 = createClient(auth_dev2);

        //Preparation
        final String groupId = "testUpdateArtifactOwnerOnlyByOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerOnlyByOwnerName";
        final String description = "testUpdateArtifactOwnerOnlyByOwnerDescription";

        //Execution
        final InputStream stream = IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));
        final ArtifactMetaData created = client_dev1.createArtifact(groupId, artifactId, version, ArtifactType.JSON, IfExists.FAIL, false, name, description, stream);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client_dev1.getArtifactOwner(groupId, artifactId);
        assertEquals("developer-client", owner.getOwner());

        //Try to update the owner by dev2 (should fail)
        assertThrows(ForbiddenException.class, () -> {
            ArtifactOwner newOwner = new ArtifactOwner();
            newOwner.setOwner("developer-2-client");
            client_dev2.updateArtifactOwner(groupId, artifactId, newOwner);
        });

        //Should still be the original owner
        owner = client_dev1.getArtifactOwner(groupId, artifactId);
        assertEquals("developer-client", owner.getOwner());
    }

}