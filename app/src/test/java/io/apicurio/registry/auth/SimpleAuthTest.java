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

import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactOwner;
import io.apicurio.registry.rest.client.models.EditableMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInfo;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.AuthTestProfile;
import io.apicurio.registry.utils.tests.JWKSMockServer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    private static final ArtifactContent content = new ArtifactContent();
    static {
        content.setContent("{}");
    }

    protected void assertArtifactNotFound(ExecutionException executionException) {
        Assertions.assertNotNull(executionException.getCause());
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, executionException.getCause().getClass());
        Assertions.assertEquals("ArtifactNotFoundException", ((io.apicurio.registry.rest.client.models.Error)executionException.getCause()).getName());
        Assertions.assertEquals(404, ((io.apicurio.registry.rest.client.models.Error)executionException.getCause()).getErrorCode());
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException);
    }

    @Test
    public void testReadOnly() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS);
        });
        assertArtifactNotFound(executionException1);
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
        });
        assertArtifactNotFound(executionException2);
        var executionException3 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId("testReadOnly").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException3);

        var devAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient devClient = new RegistryClient(devAdapter);

        ArtifactMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        }).get(3, TimeUnit.SECONDS);

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getId()).meta().get().get(3, TimeUnit.SECONDS));

        assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS));

        UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
        assertNotNull(userInfo);
        Assertions.assertEquals("readonly-client", userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            var executionException = Assertions.assertThrows(ExecutionException.class, () -> {
                client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
            });
            assertForbidden(executionException);

            UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
            assertNotNull(userInfo);
            Assertions.assertEquals("developer-client", userInfo.getUsername());
            Assertions.assertFalse(userInfo.getAdmin());
            Assertions.assertTrue(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            UserInfo userInfo = client.users().me().get().get(3, TimeUnit.SECONDS);
            assertNotNull(userInfo);
            Assertions.assertEquals("admin-client", userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdminRoleBasicAuth() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BasicAuthenticationProvider(JWKSMockServer.BASIC_USER, JWKSMockServer.BASIC_PASSWORD));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get().get(3, TimeUnit.SECONDS));

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS).readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig).get(3, TimeUnit.SECONDS);

            client.admin().rules().post(ruleConfig).get(3, TimeUnit.SECONDS);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdminRoleBasicAuthWrongCreds() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BasicAuthenticationProvider(JWKSMockServer.WRONG_CREDS_CLIENT_ID, UUID.randomUUID().toString()));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();

        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException1);
        var executionException2 = Assertions.assertThrows(ExecutionException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            }).get(3, TimeUnit.SECONDS);
        });
        assertNotAuthorized(executionException2);
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        var devAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientDev = new RegistryClient(devAdapter);

        var adminAdapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1")));
        adminAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientAdmin = new RegistryClient(adminAdapter);

        // Admin user will create an artifact
        String artifactId = TestUtils.generateArtifactId();
        clientAdmin.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        }).get(3, TimeUnit.SECONDS);

        EditableMetaData updatedMetaData = new EditableMetaData();
        updatedMetaData.setName("Updated Name");
        // Dev user cannot edit the same artifact because Dev user is not the owner
        var executionException1 = Assertions.assertThrows(ExecutionException.class, () -> {
            clientDev.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(updatedMetaData).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException1);

        // But the admin user CAN make the change.
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(updatedMetaData).get(3, TimeUnit.SECONDS);


        // Now the Dev user will create an artifact
        String artifactId2 = TestUtils.generateArtifactId();
        clientDev.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId2);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        }).get(3, TimeUnit.SECONDS);

        // And the Admin user will modify it (allowed because it's the Admin user)
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).rules().post(rule);
    }

    @Test
    public void testGetArtifactOwner() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);

        //Preparation
        final String groupId = "testGetArtifactOwner";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testGetArtifactOwnerName";
        final String description = "testGetArtifactOwnerDescription";

        //Execution
        var artifactContent = new ArtifactContent();
        artifactContent.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = client.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.queryParameters.ifExists = "FAIL";
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        }).get(3, TimeUnit.SECONDS);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get().get(3, TimeUnit.SECONDS);
        assertEquals("developer-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        var adapter = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);

        //Preparation
        final String groupId = "testUpdateArtifactOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerName";
        final String description = "testUpdateArtifactOwnerDescription";

        //Execution
        var artifactContent = new ArtifactContent();
        artifactContent.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = client.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.queryParameters.ifExists = "FAIL";
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("X-Registry-Version", version);
            config.headers.add("X-Registry-Name-Encoded", Base64.getEncoder().encodeToString(name.getBytes()));
            config.headers.add("X-Registry-Description-Encoded", Base64.getEncoder().encodeToString(description.getBytes()));
        }).get(3, TimeUnit.SECONDS);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get().get(3, TimeUnit.SECONDS);
        assertEquals("developer-client", owner.getOwner());

        //Update the owner
        owner = new ArtifactOwner();
        owner.setOwner("developer-2-client");
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().put(owner).get(3, TimeUnit.SECONDS);

        //Check that the update worked
        owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get().get(3, TimeUnit.SECONDS);
        assertEquals("developer-2-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerOnlyByOwner() throws Exception {
        var adapter_dev1 = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1")));
        adapter_dev1.setBaseUrl(registryV3ApiUrl);
        RegistryClient client_dev1 = new RegistryClient(adapter_dev1);
        var adapter_dev2 = new OkHttpRequestAdapter(
                new BaseBearerTokenAuthenticationProvider(
                        new OidcAccessTokenProvider(authServerUrlConfigured, JWKSMockServer.DEVELOPER_2_CLIENT_ID, "test1")));
        adapter_dev2.setBaseUrl(registryV3ApiUrl);
        RegistryClient client_dev2 = new RegistryClient(adapter_dev2);

        //Preparation
        final String groupId = "testUpdateArtifactOwnerOnlyByOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerOnlyByOwnerName";
        final String description = "testUpdateArtifactOwnerOnlyByOwnerDescription";

        //Execution
        var artifactContent = new ArtifactContent();
        artifactContent.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = client_dev1.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.queryParameters.ifExists = "FAIL";
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("X-Registry-Version", version);
            config.headers.add("X-Registry-Name-Encoded", Base64.getEncoder().encodeToString(name.getBytes()));
            config.headers.add("X-Registry-Description-Encoded", Base64.getEncoder().encodeToString(description.getBytes()));
        }).get(3, TimeUnit.SECONDS);

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get().get(3, TimeUnit.SECONDS);
        assertEquals("developer-client", owner.getOwner());

        //Try to update the owner by dev2 (should fail)
        var executionException1 = assertThrows(ExecutionException.class, () -> {
            ArtifactOwner newOwner = new ArtifactOwner();
            newOwner.setOwner("developer-2-client");
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().put(newOwner).get(3, TimeUnit.SECONDS);
        });
        assertForbidden(executionException1);

        //Should still be the original owner
        owner = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get().get(3, TimeUnit.SECONDS);
        assertEquals("developer-client", owner.getOwner());
    }

}

