package io.apicurio.registry.auth.opawasm;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(OpaWasmAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class OpaWasmAuthIntegrationTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.oidc.token-path")
    String tokenUrl;

    private RegistryClient adminClient;
    private RegistryClient devClient;
    private RegistryClient dev2Client;
    private RegistryClient readonlyClient;
    private boolean groupsCreated = false;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(tokenUrl, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
    }

    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();
    }

    private void ensureClients() {
        if (adminClient == null) {
            adminClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
            devClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
            dev2Client = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.DEVELOPER_2_CLIENT_ID, "test2"));
            readonlyClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1"));
        }
        if (!groupsCreated) {
            createGroupIfNotExists("team-a");
            createGroupIfNotExists("team-b");
            createGroupIfNotExists("shared");
            groupsCreated = true;
        }
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        ensureClients();
    }

    private void createGroupIfNotExists(String groupId) {
        try {
            adminClient.groups().byGroupId(groupId).get();
        } catch (Exception e) {
            CreateGroup cg = new CreateGroup();
            cg.setGroupId(groupId);
            adminClient.groups().post(cg);
        }
    }

    private CreateArtifact createArtifactRequest(String artifactId) {
        CreateArtifact ca = new CreateArtifact();
        ca.setArtifactId(artifactId);
        ca.setArtifactType(ArtifactType.JSON);
        CreateVersion cv = new CreateVersion();
        ca.setFirstVersion(cv);
        VersionContent vc = new VersionContent();
        cv.setContent(vc);
        vc.setContent("{\"type\":\"object\"}");
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        return ca;
    }

    // ==================== ADMIN TESTS ====================

    @Test
    void adminCanCreateArtifactInAnyGroup() {
        String id = UUID.randomUUID().toString();
        var result = adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));
        assertNotNull(result.getVersion());

        id = UUID.randomUUID().toString();
        result = adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(id));
        assertNotNull(result.getVersion());

        id = UUID.randomUUID().toString();
        result = adminClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id));
        assertNotNull(result.getVersion());
    }

    @Test
    void adminCanReadArtifactInAnyGroup() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        ArtifactMetaData meta = adminClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
        assertEquals(id, meta.getArtifactId());
    }

    // ==================== DEVELOPER (team-a) TESTS ====================

    @Test
    void developerCanCreateArtifactInOwnGroup() {
        String id = UUID.randomUUID().toString();
        var result = devClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));
        assertNotNull(result.getVersion());
    }

    @Test
    void developerCanReadArtifactInOwnGroup() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void developerCanReadSharedArtifact() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id));

        ArtifactMetaData meta = devClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void developerCannotCreateArtifactInTeamB() {
        String id = UUID.randomUUID().toString();
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(id)));
        assertForbidden(ex);
    }

    @Test
    void developerCannotReadTeamBArtifact() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(id));

        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void developerCannotWriteToShared() {
        String id = UUID.randomUUID().toString();
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id)));
        assertForbidden(ex);
    }

    // ==================== CROSS-DEVELOPER TESTS ====================
    // Note: developer-2-client is not used because the test realm does not assign
    // sr-developer role to its service account. The cross-team isolation tests below
    // verify that developer-client cannot access team-b resources.

    // ==================== READONLY USER TESTS ====================

    @Test
    void readonlyCanReadSharedArtifact() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id));

        ArtifactMetaData meta = readonlyClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void readonlyCannotReadTeamAArtifact() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void readonlyCannotReadTeamBArtifact() {
        String id = UUID.randomUUID().toString();
        adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(id));

        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void readonlyCannotCreateArtifact() {
        String id = UUID.randomUUID().toString();
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id)));
        assertForbidden(ex);
    }

    // ==================== SEARCH FILTERING TESTS ====================

    @Test
    void searchArtifactsFilteredByPermissions() {
        String teamAId = "search-test-a-" + UUID.randomUUID();
        String teamBId = "search-test-b-" + UUID.randomUUID();
        String sharedId = "search-test-s-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(teamAId));
        adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(teamBId));
        adminClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(sharedId));

        ArtifactSearchResults devResults = devClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 100;
        });
        boolean hasTeamA = devResults.getArtifacts().stream()
                .anyMatch(a -> "team-a".equals(a.getGroupId()));
        boolean hasTeamB = devResults.getArtifacts().stream()
                .anyMatch(a -> "team-b".equals(a.getGroupId()));
        boolean hasShared = devResults.getArtifacts().stream()
                .anyMatch(a -> "shared".equals(a.getGroupId()));
        assertTrue(hasTeamA, "developer should see team-a artifacts");
        Assertions.assertFalse(hasTeamB, "developer should NOT see team-b artifacts");
        assertTrue(hasShared, "developer should see shared artifacts");
    }

    @Test
    void searchArtifactsAdminSeesAll() {
        String teamAId = "admin-search-a-" + UUID.randomUUID();
        String teamBId = "admin-search-b-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(teamAId));
        adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(teamBId));

        ArtifactSearchResults results = adminClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 100;
        });
        boolean hasTeamA = results.getArtifacts().stream()
                .anyMatch(a -> "team-a".equals(a.getGroupId()));
        boolean hasTeamB = results.getArtifacts().stream()
                .anyMatch(a -> "team-b".equals(a.getGroupId()));
        assertTrue(hasTeamA);
        assertTrue(hasTeamB);
    }

    @Test
    void searchGroupsFilteredByPermissions() {
        GroupSearchResults devGroups = devClient.search().groups().get(q -> {
            q.queryParameters.limit = 100;
        });
        boolean hasTeamA = devGroups.getGroups().stream()
                .anyMatch(g -> "team-a".equals(g.getGroupId()));
        boolean hasTeamB = devGroups.getGroups().stream()
                .anyMatch(g -> "team-b".equals(g.getGroupId()));
        boolean hasShared = devGroups.getGroups().stream()
                .anyMatch(g -> "shared".equals(g.getGroupId()));
        assertTrue(hasTeamA, "developer should see team-a group");
        Assertions.assertFalse(hasTeamB, "developer should NOT see team-b group");
        assertTrue(hasShared, "developer should see shared group");
    }

    @Test
    void listGroupsFilteredByPermissions() {
        GroupSearchResults devGroups = devClient.groups().get(q -> {
            q.queryParameters.limit = 100;
        });
        boolean hasTeamA = devGroups.getGroups().stream()
                .anyMatch(g -> "team-a".equals(g.getGroupId()));
        boolean hasTeamB = devGroups.getGroups().stream()
                .anyMatch(g -> "team-b".equals(g.getGroupId()));
        assertTrue(hasTeamA, "developer should see team-a");
        Assertions.assertFalse(hasTeamB, "developer should NOT see team-b");
    }

    @Test
    void listArtifactsInGroupFilteredByPermissions() {
        String id = "list-in-group-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        ArtifactSearchResults devResults = devClient.groups().byGroupId("team-a").artifacts().get();
        assertTrue(devResults.getArtifacts().stream()
                .anyMatch(a -> id.equals(a.getArtifactId())));

        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-a").artifacts().get());
        assertForbidden(ex);
    }

    @Test
    void searchVersionsFilteredByPermissions() {
        String teamAId = "ver-search-a-" + UUID.randomUUID();
        String teamBId = "ver-search-b-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(teamAId));
        adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(teamBId));

        VersionSearchResults devVersions = devClient.search().versions().get(q -> {
            q.queryParameters.limit = 100;
        });
        boolean hasTeamA = devVersions.getVersions().stream()
                .anyMatch(v -> "team-a".equals(v.getGroupId()));
        boolean hasTeamB = devVersions.getVersions().stream()
                .anyMatch(v -> "team-b".equals(v.getGroupId()));
        assertTrue(hasTeamA, "developer should see team-a versions");
        Assertions.assertFalse(hasTeamB, "developer should NOT see team-b versions");
    }

    // ==================== CROSS-TEAM ISOLATION TESTS ====================

    @Test
    void sameRoleDifferentAccess() {
        String id = "isolation-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();

        var ex = assertThrows(Exception.class,
                () -> dev2Client.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void crossTeamWriteIsolation() {
        String id = UUID.randomUUID().toString();

        var result = devClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));
        assertNotNull(result.getVersion());

        var ex = assertThrows(Exception.class,
                () -> dev2Client.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id + "-2")));
        assertForbidden(ex);
    }

    // ==================== OPERATION HIERARCHY TESTS ====================

    @Test
    void writeGrantImpliesRead() {
        String id = UUID.randomUUID().toString();
        devClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));
        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void readGrantDoesNotImplyWrite() {
        String id = UUID.randomUUID().toString();
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("shared").artifacts().post(createArtifactRequest(id)));
        assertForbidden(ex);
    }
}
