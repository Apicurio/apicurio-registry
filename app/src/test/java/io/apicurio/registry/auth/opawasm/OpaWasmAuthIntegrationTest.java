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
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private RegistryClient readonlyClient;
    private RegistryClient noRoleClient;
    private boolean initialized = false;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                .oauth2(tokenUrl, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
    }

    @BeforeAll
    protected void beforeAll() throws Exception {
        super.beforeAll();
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();
        if (!initialized) {
            adminClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
            devClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
            readonlyClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1"));
            noRoleClient = RegistryClientFactory.create(RegistryClientOptions.create(registryV3ApiUrl, vertx)
                    .oauth2(tokenUrl, KeycloakTestContainerManager.NO_ROLE_CLIENT_ID, "test1"));

            ensureGroup("team-a");
            ensureGroup("team-b");
            ensureGroup("shared");
            ensureGroup("public");
            initialized = true;
        }
    }

    private void ensureGroup(String groupId) {
        try {
            adminClient.groups().byGroupId(groupId).get();
        } catch (Exception e) {
            CreateGroup cg = new CreateGroup();
            cg.setGroupId(groupId);
            adminClient.groups().post(cg);
        }
    }

    private String createArtifact(RegistryClient client, String groupId) {
        String id = UUID.randomUUID().toString();
        client.groups().byGroupId(groupId).artifacts().post(createArtifactRequest(id));
        return id;
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

    // ==================== ADMIN BYPASS ====================

    @Test
    void adminCanCreateInAnyGroup() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");
        createArtifact(adminClient, "shared");
        createArtifact(adminClient, "public");
    }

    @Test
    void adminCanReadFromAnyGroup() {
        String id = createArtifact(adminClient, "team-b");
        ArtifactMetaData meta = adminClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void adminSeesAllGroupsInSearch() {
        GroupSearchResults results = adminClient.groups().get(q -> q.queryParameters.limit = 100);
        List<String> groupIds = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(groupIds.contains("team-a"));
        assertTrue(groupIds.contains("team-b"));
        assertTrue(groupIds.contains("shared"));
        assertTrue(groupIds.contains("public"));
    }

    @Test
    void adminSeesAllArtifactsInSearch() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");
        ArtifactSearchResults results = adminClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getArtifacts().stream().map(SearchedArtifact::getGroupId).toList();
        assertTrue(groups.contains("team-a"));
        assertTrue(groups.contains("team-b"));
    }

    // ==================== DEVELOPER POINT ACCESS ====================

    @Test
    void devCanCreateInOwnGroup() {
        String id = createArtifact(devClient, "team-a");
        assertNotNull(id);
    }

    @Test
    void devCanReadOwnArtifact() {
        String id = createArtifact(adminClient, "team-a");
        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertEquals(id, meta.getArtifactId());
    }

    @Test
    void devCanReadSharedArtifact() {
        String id = createArtifact(adminClient, "shared");
        assertNotNull(devClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get());
    }

    @Test
    void devCannotCreateInTeamB() {
        var ex = assertThrows(Exception.class,
                () -> createArtifact(devClient, "team-b"));
        assertForbidden(ex);
    }

    @Test
    void devCannotReadTeamBArtifact() {
        String id = createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void devCannotWriteToShared() {
        var ex = assertThrows(Exception.class,
                () -> createArtifact(devClient, "shared"));
        assertForbidden(ex);
    }

    // ==================== EXACT PATTERN MATCHING ====================

    @Test
    void devCanReadExactMatchedArtifactInTeamB() {
        String id = "public-schema";
        try {
            adminClient.groups().byGroupId("team-b").artifacts().post(createArtifactRequest(id));
        } catch (Exception e) {
            // already exists
        }
        ArtifactMetaData meta = devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void devCannotReadNonMatchedArtifactInTeamB() {
        String id = createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    // ==================== ROLE-BASED GRANTS ====================

    @Test
    void roleBasedGrantAllowsAllDevelopersToReadPublic() {
        String id = createArtifact(adminClient, "public");
        ArtifactMetaData meta = devClient.groups().byGroupId("public").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void readonlyRoleGrantAllowsReadingShared() {
        String id = createArtifact(adminClient, "shared");
        ArtifactMetaData meta = readonlyClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    // ==================== READONLY USER ====================

    @Test
    void readonlyCannotReadTeamA() {
        String id = createArtifact(adminClient, "team-a");
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void readonlyCannotReadTeamB() {
        String id = createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void readonlyCannotCreateAnywhere() {
        var ex = assertThrows(Exception.class,
                () -> createArtifact(readonlyClient, "shared"));
        assertForbidden(ex);
    }

    @Test
    void readonlyCannotReadPublic() {
        String id = createArtifact(adminClient, "public");
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("public").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    // ==================== NO-ROLE USER ====================

    @Test
    void noRoleUserDeniedEverything() {
        var ex = assertThrows(Exception.class,
                () -> noRoleClient.groups().get());
        assertForbidden(ex);
    }

    // ==================== OPERATION HIERARCHY ====================

    @Test
    void writeGrantImpliesRead() {
        String id = createArtifact(devClient, "team-a");
        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertNotNull(meta);
    }

    @Test
    void readGrantDoesNotImplyWrite() {
        var ex = assertThrows(Exception.class,
                () -> createArtifact(readonlyClient, "shared"));
        assertForbidden(ex);
    }

    // ==================== METADATA OPERATIONS ====================

    @Test
    void devCanUpdateMetadataInOwnGroup() {
        String id = createArtifact(devClient, "team-a");
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setName("Updated Name");
        eamd.setDescription("Updated Description");
        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).put(eamd);

        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertEquals("Updated Name", meta.getName());
    }

    @Test
    void devCannotUpdateMetadataInTeamB() {
        String id = createArtifact(adminClient, "team-b");
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setName("Hacked");
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).put(eamd));
        assertForbidden(ex);
    }

    // ==================== ARTIFACT DELETION ====================

    @Test
    void devCanDeleteArtifactInOwnGroup() {
        String id = createArtifact(devClient, "team-a");
        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).delete();
        var ex = assertThrows(Exception.class,
                () -> adminClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
        assertNotFound(ex);
    }

    @Test
    void devCannotDeleteArtifactInTeamB() {
        String id = createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).delete());
        assertForbidden(ex);
    }

    // ==================== VERSION OPERATIONS ====================

    @Test
    void devCanCreateVersionInOwnArtifact() {
        String id = createArtifact(devClient, "team-a");
        CreateVersion cv = new CreateVersion();
        VersionContent vc = new VersionContent();
        cv.setContent(vc);
        vc.setContent("{\"type\":\"string\"}");
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        var result = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).versions().post(cv);
        assertNotNull(result);
    }

    @Test
    void devCannotCreateVersionInTeamBArtifact() {
        String id = createArtifact(adminClient, "team-b");
        CreateVersion cv = new CreateVersion();
        VersionContent vc = new VersionContent();
        cv.setContent(vc);
        vc.setContent("{\"type\":\"string\"}");
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).versions().post(cv));
        assertForbidden(ex);
    }

    @Test
    void devCanListVersionsOfOwnArtifact() {
        String id = createArtifact(devClient, "team-a");
        VersionSearchResults versions = devClient.groups().byGroupId("team-a")
                .artifacts().byArtifactId(id).versions().get();
        assertTrue(versions.getCount() > 0);
    }

    // ==================== SEARCH ARTIFACT FILTERING ====================

    @Test
    void searchArtifactsFilteredByPermissions() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");
        createArtifact(adminClient, "shared");

        ArtifactSearchResults results = devClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getArtifacts().stream().map(SearchedArtifact::getGroupId).toList();
        assertTrue(groups.contains("team-a"), "developer should see team-a");
        assertTrue(groups.contains("shared"), "developer should see shared");
    }

    @Test
    void searchArtifactsDeniedGroupsHidden() {
        createArtifact(adminClient, "team-b");

        ArtifactSearchResults results = readonlyClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getArtifacts().stream().map(SearchedArtifact::getGroupId).toList();
        assertFalse(groups.contains("team-a"), "readonly should NOT see team-a");
        assertFalse(groups.contains("team-b"), "readonly should NOT see team-b");
    }

    @Test
    void searchArtifactsWithNameFilter() {
        String id = "named-schema-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        ArtifactSearchResults results = devClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 100;
            q.queryParameters.artifactId = id;
        });
        assertEquals(1, results.getCount());
        assertEquals(id, results.getArtifacts().get(0).getArtifactId());
    }

    @Test
    void searchArtifactsReadonlyCannotFindTeamAArtifact() {
        String id = "secret-schema-" + UUID.randomUUID();
        adminClient.groups().byGroupId("team-a").artifacts().post(createArtifactRequest(id));

        ArtifactSearchResults results = readonlyClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 100;
            q.queryParameters.artifactId = id;
        });
        assertEquals(0, results.getCount());
    }

    // ==================== SEARCH GROUP FILTERING ====================

    @Test
    void searchGroupsFilteredByPermissions() {
        GroupSearchResults results = devClient.search().groups().get(q -> q.queryParameters.limit = 100);
        List<String> groupIds = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(groupIds.contains("team-a"), "developer sees team-a");
        assertTrue(groupIds.contains("shared"), "developer sees shared");
        assertTrue(groupIds.contains("public"), "developer sees public (role grant)");
        assertTrue(groupIds.contains("team-b"), "developer sees team-b (has exact artifact grant)");
    }

    @Test
    void searchGroupsReadonlySeesOnlyShared() {
        GroupSearchResults results = readonlyClient.search().groups().get(q -> q.queryParameters.limit = 100);
        List<String> groupIds = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(groupIds.contains("shared"), "readonly sees shared");
        assertFalse(groupIds.contains("team-a"), "readonly doesn't see team-a");
        assertFalse(groupIds.contains("team-b"), "readonly doesn't see team-b");
    }

    @Test
    void listGroupsFilteredByPermissions() {
        GroupSearchResults results = devClient.groups().get(q -> q.queryParameters.limit = 100);
        List<String> groupIds = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(groupIds.contains("team-a"));
        assertTrue(groupIds.contains("shared"));
    }

    // ==================== LIST ARTIFACTS IN GROUP ====================

    @Test
    void listArtifactsInAllowedGroup() {
        String id = createArtifact(adminClient, "team-a");
        ArtifactSearchResults results = devClient.groups().byGroupId("team-a").artifacts().get();
        assertTrue(results.getArtifacts().stream().anyMatch(a -> id.equals(a.getArtifactId())));
    }

    @Test
    void listArtifactsInDeniedGroupForbidden() {
        createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-a").artifacts().get());
        assertForbidden(ex);
    }

    // ==================== VERSION SEARCH FILTERING ====================

    @Test
    void searchVersionsFilteredByPermissions() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");

        VersionSearchResults results = devClient.search().versions().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getVersions().stream().map(v -> v.getGroupId()).toList();
        assertTrue(groups.contains("team-a"), "developer sees team-a versions");
    }

    @Test
    void searchVersionsReadonlyFiltered() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "shared");

        VersionSearchResults results = readonlyClient.search().versions().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getVersions().stream().map(v -> v.getGroupId()).toList();
        assertTrue(groups.contains("shared"), "readonly sees shared versions");
        assertFalse(groups.contains("team-a"), "readonly doesn't see team-a versions");
    }

    // ==================== PAGINATION ====================

    @Test
    void paginationWorksWithFiltering() {
        for (int i = 0; i < 5; i++) {
            createArtifact(adminClient, "team-a");
        }

        ArtifactSearchResults page1 = devClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 2;
            q.queryParameters.offset = 0;
            q.queryParameters.groupId = "team-a";
        });
        ArtifactSearchResults page2 = devClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 2;
            q.queryParameters.offset = 2;
            q.queryParameters.groupId = "team-a";
        });

        assertEquals(2, page1.getArtifacts().size());
        assertEquals(2, page2.getArtifacts().size());
        assertTrue(page1.getCount() >= 5);
    }

    @Test
    void paginationCountAccurateForFilteredResults() {
        for (int i = 0; i < 3; i++) {
            createArtifact(adminClient, "shared");
        }

        ArtifactSearchResults results = readonlyClient.search().artifacts().get(q -> {
            q.queryParameters.limit = 100;
            q.queryParameters.groupId = "shared";
        });
        assertTrue(results.getCount() >= 3);
        assertEquals(results.getArtifacts().size(), (int) Math.min(results.getCount(), 100));
    }

    // ==================== MULTIPLE ARTIFACTS SAME GROUP ====================

    @Test
    void multipleArtifactsInAllowedGroupAllAccessible() {
        String id1 = createArtifact(devClient, "team-a");
        String id2 = createArtifact(devClient, "team-a");
        String id3 = createArtifact(devClient, "team-a");

        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id1).get());
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id2).get());
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id3).get());
    }

    @Test
    void multipleArtifactsInDeniedGroupAllDenied() {
        String id1 = createArtifact(adminClient, "team-b");
        String id2 = createArtifact(adminClient, "team-b");

        assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-b").artifacts().byArtifactId(id1).get());
        assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-b").artifacts().byArtifactId(id2).get());
    }
}
