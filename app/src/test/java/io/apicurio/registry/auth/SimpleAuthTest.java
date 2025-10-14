package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
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
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
    String authServerUrlConfigured;

    final String groupId = "authTestGroupId";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
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
        Assertions.assertEquals(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                exception.getClass());
        Assertions.assertEquals("ArtifactNotFoundException",
                ((io.apicurio.registry.rest.client.models.ProblemDetails) exception).getName());
        Assertions.assertEquals(404,
                ((io.apicurio.registry.rest.client.models.ProblemDetails) exception).getStatus());
    }

    @Test
    public void testWrongCreds() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.WRONG_CREDS_CLIENT_ID, "test55"));
        var exception = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    public void testNoCreds() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx));
        Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
    }

    @Test
    public void testReadOnly() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1"));
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
        // Try the create again but with dryRun set to true (should work)
        client.groups().byGroupId("testReadOnly").artifacts().post(createArtifact, config -> {
            config.queryParameters.dryRun = true;
        });

        var devClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));

        createArtifact.setArtifactId(artifactId);
        VersionMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(meta.getArtifactId()).get());

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
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(
                    () -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

            var exception = Assertions.assertThrows(Exception.class, () -> {
                client.admin().rules().post(createRule);
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
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(
                    () -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

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
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            createArtifact.setArtifactId(artifactId);
            client.groups().byGroupId(groupId).artifacts().post(createArtifact);
            TestUtils.retry(
                    () -> client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

            assertTrue(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                    .byVersionExpression("branch=latest").content().get().readAllBytes().length > 0);

            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.VALIDITY);
            createRule.setConfig(ValidityLevel.NONE.name());
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);

            client.admin().rules().post(createRule);
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testAdminRoleBasicAuthWrongCreds() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(KeycloakTestContainerManager.WRONG_CREDS_CLIENT_ID, UUID.randomUUID().toString()));
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
        var clientDev = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
        var clientAdmin = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));

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
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.BACKWARD.name());
        clientAdmin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId2).rules()
                .post(createRule);

        // Admin user will create an artifact
        String artifactId1 = TestUtils.generateArtifactId();
        createArtifact.setArtifactId(artifactId1);
        clientAdmin.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Dev user cannot update with ifExists the same artifact because Dev user is not the owner
        Assertions.assertThrows(Exception.class, () -> {
            clientDev.groups().byGroupId(groupId).artifacts().post(createArtifact, config -> {
                config.queryParameters.ifExists = IfArtifactExists.CREATE_VERSION;
            });
        });
    }

    @Test
    public void testGetArtifactOwner() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));

        // Preparation
        final String groupId = "testGetArtifactOwner";
        final String artifactId = generateArtifactId();
        final String version = "1";

        // Execution
        createArtifact.setArtifactId(artifactId);
        final VersionMetaData created = client.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));

        // Preparation
        final String groupId = "testUpdateArtifactOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerName";
        final String description = "testUpdateArtifactOwnerDescription";

        // Execution
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        final VersionMetaData created = client.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());

        // Update the owner
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setOwner("developer-2-client");
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);

        // Check that the update worked
        amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-2-client", amd.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerOnlyByOwner() throws Exception {
        var client_dev1 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
        var client_dev2 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(authServerUrlConfigured, KeycloakTestContainerManager.DEVELOPER_2_CLIENT_ID, "test2"));

        // Preparation
        final String groupId = "testUpdateArtifactOwnerOnlyByOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerOnlyByOwnerName";
        final String description = "testUpdateArtifactOwnerOnlyByOwnerDescription";

        // Execution
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts()
                .post(createArtifact).getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals("developer-client", created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals("developer-client", amd.getOwner());

        // Try to update the owner by dev2 (should fail)
        var exception1 = assertThrows(Exception.class, () -> {
            EditableArtifactMetaData eamd = new EditableArtifactMetaData();
            eamd.setOwner("developer-2-client");
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);
        });
        assertForbidden(exception1);

        // Should still be the original owner
        amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals("developer-client", amd.getOwner());
    }

}
