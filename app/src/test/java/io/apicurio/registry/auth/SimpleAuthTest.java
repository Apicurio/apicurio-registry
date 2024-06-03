package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInfo;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
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

import java.util.UUID;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class SimpleAuthTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @ConfigProperty(name = "quarkus.oidc.token-path")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrlConfigured;

    final String groupId = "authTestGroupId";

    @Override
    protected RegistryClient createRestClientV3() {
        var adapter =new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.ADMIN_CLIENT_ID, "test1"));
        adapter.setBaseUrl(registryV3ApiUrl);
        return new RegistryClient(adapter);
    }

    private static final CreateArtifact createArtifact = new CreateArtifact();
    static {
        createArtifact.setArtifactType(ArtifactType.JSON);
        CreateVersion createVersion = new CreateVersion();
        createArtifact.setFirstVersion(createVersion);
        VersionContent versionContent = new VersionContent();
        createVersion.setContent(versionContent);
        versionContent.setContent(ARTIFACT_CONTENT);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
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
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        });
        assertArtifactNotFound(exception1);
        var exception2 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("abc").artifacts().byArtifactId(artifactId).get();
        });
        assertArtifactNotFound(exception2);
        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId("testReadOnly").artifacts().post(createArtifact);
        });
        assertForbidden(exception3);

        var devAdapter = new VertXRequestAdapter(buildOIDCWebClient(authServerUrlConfigured, JWKSMockServer.DEVELOPER_CLIENT_ID, "test1"));
        devAdapter.setBaseUrl(registryV3ApiUrl);
        RegistryClient devClient = new RegistryClient(devAdapter);

        createArtifact.setArtifactId(artifactId);
        VersionMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts().byArtifactId(meta.getArtifactId()).get());

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

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

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

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

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

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(() -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

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
            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
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
        createArtifact.setArtifactId(artifactId);
        clientAdmin.groups().byGroupId(groupId).artifacts().post(createArtifact);

        EditableArtifactMetaData updatedMetaData = new EditableArtifactMetaData();
        updatedMetaData.setName("Updated Name");
        // Dev user cannot edit the same artifact because Dev user is not the owner
        var exception1 = Assertions.assertThrows(Exception.class, () -> {
            clientDev.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(updatedMetaData);
        });
        assertForbidden(exception1);

        // But the admin user CAN make the change.
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(updatedMetaData);


        // Now the Dev user will create an artifact
        String artifactId2 = TestUtils.generateArtifactId();
        createArtifact.setArtifactId(artifactId2);
        clientDev.groups().byGroupId(groupId).artifacts().post(createArtifact);

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
        createArtifact.setArtifactId(artifactId);
        final VersionMetaData created = client.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        //Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());
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
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        final VersionMetaData created = client.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        //Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());

        //Update the owner
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setOwner("developer-2-client");
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);

        //Check that the update worked
        amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-2-client", amd.getOwner());
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
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts().post(createArtifact).getVersion();

        //Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        //Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());

        //Try to update the owner by dev2 (should fail)
        var exception1 = assertThrows(Exception.class, () -> {
            EditableArtifactMetaData eamd = new EditableArtifactMetaData();
            eamd.setOwner("developer-2-client");
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);
        });
        assertForbidden(exception1);

        //Should still be the original owner
        amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());
    }

}

