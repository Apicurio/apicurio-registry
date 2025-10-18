package io.apicurio.registry.auth;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.UserInfo;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.BasicAuthWithPropertiesTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(BasicAuthWithPropertiesTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class BasicAuthWithPropertiesTest extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    final String groupId = "authTestGroupId";

    public static final String ADMIN_USERNAME = "alice";
    public static final String ADMIN_PASSWORD = "alice";
    public static final String DEVELOPER_USERNAME = "bob1";
    public static final String DEVELOPER_PASSWORD = "bob1";
    public static final String DEVELOPER_2_USERNAME = "bob2";
    public static final String DEVELOPER_2_PASSWORD = "bob2";
    public static final String READONLY_USERNAME = "duncan";
    public static final String READONLY_PASSWORD = "duncan";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    private static CreateArtifact clientCreateArtifact() {
        return TestUtils.clientCreateArtifact(AuthTestLocalRoles.class.getSimpleName(),
                ArtifactType.JSON, ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON);
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
        var client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        var exception = Assertions.assertThrows(ApiException.class, () -> {
            client.groups().byGroupId(groupId).artifacts().get();
        });
        assertEquals(401, exception.getResponseStatusCode());
    }

    @Test
    public void testReadOnly() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(READONLY_USERNAME, READONLY_PASSWORD));
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

        CreateArtifact createArtifact = clientCreateArtifact();
        createArtifact.setArtifactId(artifactId);
        var exception3 = Assertions.assertThrows(Exception.class, () -> {
            client.groups().byGroupId("testReadOnly").artifacts().post(createArtifact);
        });
        assertForbidden(exception3);

        var devClient = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));

        VersionMetaData meta = devClient.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        TestUtils.retry(() -> devClient.groups().byGroupId(groupId).artifacts()
                .byArtifactId(meta.getArtifactId()).get());

        assertNotNull(client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());

        UserInfo userInfo = client.users().me().get();
        assertNotNull(userInfo);
        Assertions.assertEquals(READONLY_USERNAME, userInfo.getUsername());
        Assertions.assertFalse(userInfo.getAdmin());
        Assertions.assertFalse(userInfo.getDeveloper());
        Assertions.assertTrue(userInfo.getViewer());
    }

    @Test
    public void testDevRole() throws Exception {
        var client = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            CreateArtifact createArtifact = clientCreateArtifact();
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
            Assertions.assertEquals(DEVELOPER_USERNAME, userInfo.getUsername());
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
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));
        String artifactId = TestUtils.generateArtifactId();
        try {
            client.groups().byGroupId(groupId).artifacts().get();

            CreateArtifact createArtifact = clientCreateArtifact();
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

            UserInfo userInfo = client.users().me().get();
            assertNotNull(userInfo);
            Assertions.assertEquals(ADMIN_USERNAME, userInfo.getUsername());
            Assertions.assertTrue(userInfo.getAdmin());
            Assertions.assertFalse(userInfo.getDeveloper());
            Assertions.assertFalse(userInfo.getViewer());
        } finally {
            client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        }
    }

    @Test
    public void testOwnerOnlyAuthorization() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var clientDev = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));

        var clientAdmin = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));

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
    }

    @Test
    public void testGetArtifactOwner() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));

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
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());
    }

    @Test
    public void testUpdateArtifactOwner() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));

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
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        final VersionMetaData created = client.groups().byGroupId(groupId).artifacts().post(createArtifact)
                .getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());

        // Update the owner
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setOwner(DEVELOPER_2_USERNAME);
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);

        // Check that the update worked
        amd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals(DEVELOPER_2_USERNAME, amd.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerOnlyByOwner() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client_dev1 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
        var client_dev2 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_2_USERNAME, DEVELOPER_2_PASSWORD));

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
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts()
                .post(createArtifact).getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());

        // Try to update the owner by dev2 (should fail)
        var exception1 = assertThrows(Exception.class, () -> {
            EditableArtifactMetaData eamd = new EditableArtifactMetaData();
            eamd.setOwner(DEVELOPER_2_USERNAME);
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);
        });
        assertForbidden(exception1);

        // Should still be the original owner
        amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());
    }

    @Test
    public void testUpdateArtifactOwnerByAdmin() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client_dev1 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
        var client_admin = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));

        // Preparation
        final String groupId = "testUpdateArtifactOwnerByAdmin";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testUpdateArtifactOwnerByAdminName";
        final String description = "testUpdateArtifactOwnerByAdminDescription";

        // Execution
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts()
                .post(createArtifact).getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());

        // Update the owner by admin (should work!)
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setOwner(DEVELOPER_2_USERNAME);
        client_admin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(eamd);

        // Should be the new owner
        amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertEquals(DEVELOPER_2_USERNAME, amd.getOwner());
    }

    @Test
    public void testDeleteArtifactOnlyIfOwner() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client_dev1 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
        var client_dev2 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_2_USERNAME, DEVELOPER_2_PASSWORD));

        // Preparation
        final String groupId = "testDeleteArtifactOnlyIfOwner";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testDeleteArtifactOnlyIfOwnerName";
        final String description = "testDeleteArtifactOnlyIfOwnerDescription";

        // Execution - create artifact
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts()
                .post(createArtifact).getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());

        // Try to delete the artifact by dev2 (should fail)
        var exception1 = assertThrows(Exception.class, () -> {
            client_dev2.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
        });
        assertForbidden(exception1);

        // Should still exist
        amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertNotNull(amd);

        // Delete the artifact by dev1 (should work)
        client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();

        // Should not exist
        var exception2 = assertThrows(Exception.class, () -> {
            client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        });
        assertNotFound(exception2);
    }

    @Test
    public void testDeleteArtifactIfAdmin() throws Exception {
        CreateArtifact createArtifact = clientCreateArtifact();

        var client_dev1 = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(DEVELOPER_USERNAME, DEVELOPER_PASSWORD));
        var client_admin = RegistryClientFactory.create(
                RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD));

        // Preparation
        final String groupId = "testDeleteArtifactIfAdmin";
        final String artifactId = generateArtifactId();

        final String version = "1.0";
        final String name = "testDeleteArtifactIfAdminName";
        final String description = "testDeleteArtifactIfAdminDescription";

        // Execution - create artifact
        createArtifact.setArtifactId(artifactId);
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setName(name);
        createArtifact.getFirstVersion().setDescription(description);
        createArtifact.setName(name);
        createArtifact.setDescription(description);
        final VersionMetaData created = client_dev1.groups().byGroupId(groupId).artifacts()
                .post(createArtifact).getVersion();

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getArtifactId());
        assertEquals(version, created.getVersion());
        assertEquals(DEVELOPER_USERNAME, created.getOwner());

        // Get the artifact owner via the REST API and verify it
        ArtifactMetaData amd = client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals(DEVELOPER_USERNAME, amd.getOwner());

        // Try to delete the artifact by admin (should work)
        client_admin.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();

        // Should not exist
        var exception2 = assertThrows(Exception.class, () -> {
            client_dev1.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        });
        assertNotFound(exception2);
    }

}
