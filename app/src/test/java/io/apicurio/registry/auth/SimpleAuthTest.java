package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactOwner;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.IfExists;
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
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        var adapter =new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    private static final ArtifactContent content = new ArtifactContent();
    static {
        content.setContent("{}");
    }

    protected void assertArtifactNotFound(Exception exception) {
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.Error.class, exception.getClass());
        Assertions.assertEquals("ArtifactNotFoundException", ((io.apicurio.registry.rest.client.models.Error)exception).getName());
        Assertions.assertEquals(404, ((io.apicurio.registry.rest.client.models.Error)exception).getErrorCode());
    }

    @Test
    public void testWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.WRONG_CREDS_CLIENT_ID, "test55"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertTrue(exception.getMessage().contains("Unauthorized"));
    }

    @Test
    public void testReadOnly() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.READONLY_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        client.groups().byGroupId(groupId).artifacts().get();
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get();
        });
        assertArtifactNotFound(exception1);
        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get();
        });
        assertArtifactNotFound(exception2);
        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("testReadOnly").artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            });
        });
        assertForbidden(exception3);

        var devAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient devClient = new RegistryClient(devAdapter);

        ArtifactMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getId()).meta().get());

        assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

        UserInfo userInfo = client.users().me().get();
        assertNotNull(userInfo);
        Assertions.assertEquals("readonly-client", userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            });
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            var exception = Assertions.assertThrows(Exception.class, () -> {
                client.admin().rules().post(ruleConfig);
            });
            assertForbidden(exception);

            UserInfo userInfo = client.users().me().get();
            assertNotNull(userInfo);
            Assertions.assertEquals("developer-client", userInfo.getUsername());
            Assertions.assertFalse(userInfo.getAdmin());
            Assertions.assertTrue(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testAdminRole() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            });
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            client.admin().rules().post(ruleConfig);

            UserInfo userInfo = client.users().me().get();
            assertNotNull(userInfo);
            Assertions.assertEquals("admin-client", userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testAdminRoleBasicAuth() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(JWKSMockServer.BASIC_USER, JWKSMockServer.BASIC_PASSWORD));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            });
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().readAllBytes().length > 0);

            Rule ruleConfig = new Rule();
            ruleConfig.setType(RuleType.VALIDITY);
            ruleConfig.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(ruleConfig);

            client.admin().rules().post(ruleConfig);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testAdminRoleBasicAuthWrongCreds() throws Exception {
        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(JWKSMockServer.WRONG_CREDS_CLIENT_ID, UUID.randomUUID().toString()));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);
        String artifactId = TestUtils.generateArtifactId();

        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertNotAuthorized(exception1);
        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().post(content, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
                config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            });
        });
        assertNotAuthorized(exception2);
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        var devAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientDev = new RegistryClient(devAdapter);

        var adminAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adminAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient clientAdmin = new RegistryClient(adminAdapter);

        // Admin user will create an artifact
        String artifactId = TestUtils.generateArtifactId();
        clientAdmin.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        EditableArtifactMetaData updatedMetaData = new EditableArtifactMetaData();
        updatedMetaData.setName("Updated Name");
        // Dev user cannot edit the same artifact because Dev user is not the owner
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            clientDev.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(updatedMetaData);
        });
        assertForbidden(exception1);

        // But the admin user CAN make the change.
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta().put(updatedMetaData);


        // Now the Dev user will create an artifact
        String artifactId2 = TestUtils.generateArtifactId();
        clientDev.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.headers.add("X-Registry-ArtifactId", artifactId2);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        // And the Admin user will modify it (allowed because it's the Admin user)
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).rules().post(rule);
    }

    @Test
    public void testGetArtifactOwner() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient client = new RegistryClient(adapter);

        //Preparation
        final String groupId = "testGetArtifactOwner";
        final String artifactId = generateArtifactId();
        final String version = "1";

        //Execution
        var artifactContent = new ArtifactContent();
        artifactContent.setContent(ARTIFACT_CONTENT);
        final ArtifactMetaData created = client.groups().byGroupId(groupId).artifacts().post(content, config -> {
            config.queryParameters.ifExists = IfExists.FAIL;
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
        });

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get();
        assertEquals("developer-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        var adapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
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
            config.queryParameters.ifExists = IfExists.FAIL;
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("X-Registry-Version", version);
            config.headers.add("X-Registry-Name-Encoded", Base64.getEncoder().encodeToString(name.getBytes()));
            config.headers.add("X-Registry-Description-Encoded", Base64.getEncoder().encodeToString(description.getBytes()));
        });

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get();
        assertEquals("developer-client", owner.getOwner());

        //Update the owner
        owner = new ArtifactOwner();
        owner.setOwner("developer-2-client");
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().put(owner);

        //Check that the update worked
        owner = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get();
        assertEquals("developer-2-client", owner.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerOnlyByOwner() throws Exception {
        var adapter_dev1 = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        adapter_dev1.setBaseUrl(registryV3ApiUrl);
        RegistryClient client_dev1 = new RegistryClient(adapter_dev1);
        var adapter_dev2 = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_2_CLIENT_ID, "test1"));
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
            config.queryParameters.ifExists = IfExists.FAIL;
            config.headers.add("X-Registry-ArtifactId", artifactId);
            config.headers.add("X-Registry-ArtifactType", ArtifactType.JSON);
            config.headers.add("X-Registry-Version", version);
            config.headers.add("X-Registry-Name-Encoded", Base64.getEncoder().encodeToString(name.getBytes()));
            config.headers.add("X-Registry-Description-Encoded", Base64.getEncoder().encodeToString(description.getBytes()));
        });

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getCreatedBy());

        //Get the artifact owner via the REST API and verify it
        ArtifactOwner owner = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get();
        assertEquals("developer-client", owner.getOwner());

        //Try to update the owner by dev2 (should fail)
        var exception1 = assertThrows(Exception.class, () -> {
            ArtifactOwner newOwner = new ArtifactOwner();
            newOwner.setOwner("developer-2-client");
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().put(newOwner);
        });
        assertForbidden(exception1);

        //Should still be the original owner
        owner = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).owner().get();
        assertEquals("developer-client", owner.getOwner());
    }

}

