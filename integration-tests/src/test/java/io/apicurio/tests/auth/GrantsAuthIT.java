package io.apicurio.tests.auth;

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
import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.apicurio.deployment.Constants.AUTH;
import static io.apicurio.registry.client.common.auth.VertXAuthFactory.buildOIDCWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(AUTH)
@QuarkusIntegrationTest
@QuarkusTestResource(GrantsAuthTestResourceManager.class)
public class GrantsAuthIT extends ApicurioRegistryBaseIT {

    private RegistryClient adminClient;
    private RegistryClient devClient;
    private RegistryClient readonlyClient;

    @Override
    protected RegistryClient createRegistryClient(Vertx vertx) {
        var auth = buildOIDCWebClient(vertx, authServerUrlConfigured,
                KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1");
        return createClient(auth);
    }

    private RegistryClient createClient(WebClient auth) {
        return RegistryClientFactory.create(
                RegistryClientOptions.create(getRegistryV3ApiUrl()).customWebClient(auth).retry());
    }

    @BeforeAll
    void setupClients() {
        adminClient = createClient(buildOIDCWebClient(vertx, authServerUrlConfigured,
                KeycloakTestContainerManager.ADMIN_CLIENT_ID, "test1"));
        devClient = createClient(buildOIDCWebClient(vertx, authServerUrlConfigured,
                KeycloakTestContainerManager.DEVELOPER_CLIENT_ID, "test1"));
        readonlyClient = createClient(buildOIDCWebClient(vertx, authServerUrlConfigured,
                KeycloakTestContainerManager.READONLY_CLIENT_ID, "test1"));

        ensureGroup("team-a");
        ensureGroup("team-b");
        ensureGroup("shared");
        ensureGroup("public");
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
        CreateArtifact ca = new CreateArtifact();
        ca.setArtifactId(id);
        ca.setArtifactType(ArtifactType.JSON);
        CreateVersion cv = new CreateVersion();
        ca.setFirstVersion(cv);
        VersionContent vc = new VersionContent();
        cv.setContent(vc);
        vc.setContent("{\"type\":\"object\"}");
        vc.setContentType(ContentTypes.APPLICATION_JSON);
        client.groups().byGroupId(groupId).artifacts().post(ca);
        return id;
    }

    // ==================== ADMIN BYPASS ====================

    @Test
    void adminCanCreateInAnyGroup() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");
        createArtifact(adminClient, "shared");
    }

    @Test
    void adminCanReadFromAnyGroup() {
        String id = createArtifact(adminClient, "team-b");
        assertNotNull(adminClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
    }

    @Test
    void adminSeesAllGroupsInSearch() {
        GroupSearchResults results = adminClient.groups().get(q -> q.queryParameters.limit = 100);
        List<String> ids = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(ids.contains("team-a"));
        assertTrue(ids.contains("team-b"));
    }

    // ==================== DEVELOPER POINT ACCESS ====================

    @Test
    void devCanCreateInOwnGroup() {
        assertNotNull(createArtifact(devClient, "team-a"));
    }

    @Test
    void devCanReadOwnArtifact() {
        String id = createArtifact(adminClient, "team-a");
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
    }

    @Test
    void devCanReadSharedArtifact() {
        String id = createArtifact(adminClient, "shared");
        assertNotNull(devClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get());
    }

    @Test
    void devCannotCreateInTeamB() {
        var ex = assertThrows(Exception.class, () -> createArtifact(devClient, "team-b"));
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
        var ex = assertThrows(Exception.class, () -> createArtifact(devClient, "shared"));
        assertForbidden(ex);
    }

    // ==================== ROLE-BASED GRANTS ====================

    @Test
    void roleBasedGrantAllowsDeveloperToReadPublic() {
        String id = createArtifact(adminClient, "public");
        assertNotNull(devClient.groups().byGroupId("public").artifacts().byArtifactId(id).get());
    }

    @Test
    void readonlyRoleGrantAllowsReadingShared() {
        String id = createArtifact(adminClient, "shared");
        assertNotNull(readonlyClient.groups().byGroupId("shared").artifacts().byArtifactId(id).get());
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
    void readonlyCannotCreateAnywhere() {
        var ex = assertThrows(Exception.class, () -> createArtifact(readonlyClient, "shared"));
        assertForbidden(ex);
    }

    // ==================== OPERATION HIERARCHY ====================

    @Test
    void writeGrantImpliesRead() {
        String id = createArtifact(devClient, "team-a");
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
    }

    @Test
    void readGrantDoesNotImplyWrite() {
        var ex = assertThrows(Exception.class, () -> createArtifact(readonlyClient, "shared"));
        assertForbidden(ex);
    }

    // ==================== METADATA OPERATIONS ====================

    @Test
    void devCanUpdateMetadataInOwnGroup() {
        String id = createArtifact(devClient, "team-a");
        EditableArtifactMetaData eamd = new EditableArtifactMetaData();
        eamd.setName("Updated");
        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).put(eamd);
        ArtifactMetaData meta = devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        assertEquals("Updated", meta.getName());
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

    // ==================== DELETION ====================

    @Test
    void devCanDeleteOwnArtifact() {
        String id = createArtifact(devClient, "team-a");
        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).delete();
    }

    @Test
    void devCannotDeleteTeamBArtifact() {
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
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).versions().post(cv));
    }

    @Test
    void devCannotCreateVersionInTeamB() {
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

    // ==================== SEARCH FILTERING ====================

    @Test
    void searchArtifactsFilteredByPermissions() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");
        createArtifact(adminClient, "shared");

        ArtifactSearchResults results = devClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getArtifacts().stream().map(SearchedArtifact::getGroupId).toList();
        assertTrue(groups.contains("team-a"));
        assertTrue(groups.contains("shared"));
    }

    @Test
    void searchArtifactsDeniedGroupsHiddenForReadonly() {
        createArtifact(adminClient, "team-a");
        ArtifactSearchResults results = readonlyClient.search().artifacts().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getArtifacts().stream().map(SearchedArtifact::getGroupId).toList();
        assertFalse(groups.contains("team-a"));
        assertFalse(groups.contains("team-b"));
    }

    @Test
    void searchGroupsFilteredForDeveloper() {
        GroupSearchResults results = devClient.search().groups().get(q -> q.queryParameters.limit = 100);
        List<String> ids = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(ids.contains("team-a"));
        assertTrue(ids.contains("shared"));
        assertTrue(ids.contains("public"));
    }

    @Test
    void searchGroupsFilteredForReadonly() {
        GroupSearchResults results = readonlyClient.search().groups().get(q -> q.queryParameters.limit = 100);
        List<String> ids = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(ids.contains("shared"));
        assertFalse(ids.contains("team-a"));
    }

    @Test
    void listGroupsFiltered() {
        GroupSearchResults results = devClient.groups().get(q -> q.queryParameters.limit = 100);
        List<String> ids = results.getGroups().stream().map(g -> g.getGroupId()).toList();
        assertTrue(ids.contains("team-a"));
        assertTrue(ids.contains("shared"));
    }

    @Test
    void searchVersionsFiltered() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "team-b");

        VersionSearchResults results = devClient.search().versions().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getVersions().stream().map(v -> v.getGroupId()).toList();
        assertTrue(groups.contains("team-a"));
    }

    @Test
    void readonlySearchVersionsFiltered() {
        createArtifact(adminClient, "team-a");
        createArtifact(adminClient, "shared");

        VersionSearchResults results = readonlyClient.search().versions().get(q -> q.queryParameters.limit = 100);
        List<String> groups = results.getVersions().stream().map(v -> v.getGroupId()).toList();
        assertTrue(groups.contains("shared"));
        assertFalse(groups.contains("team-a"));
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
        assertEquals(2, page1.getArtifacts().size());
        assertTrue(page1.getCount() >= 5);
    }

    // ==================== CROSS-TEAM ISOLATION ====================

    @Test
    void sameRoleDifferentAccess() {
        String id = createArtifact(adminClient, "team-a");
        devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get();
        var ex = assertThrows(Exception.class,
                () -> readonlyClient.groups().byGroupId("team-a").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }

    @Test
    void multipleArtifactsInAllowedGroupAllAccessible() {
        String id1 = createArtifact(devClient, "team-a");
        String id2 = createArtifact(devClient, "team-a");
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id1).get());
        assertNotNull(devClient.groups().byGroupId("team-a").artifacts().byArtifactId(id2).get());
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

    // ==================== EXACT MATCH ====================

    @Test
    void devCanReadExactMatchedArtifactInTeamB() {
        String id = "public-schema";
        try {
            CreateArtifact ca = new CreateArtifact();
            ca.setArtifactId(id);
            ca.setArtifactType(ArtifactType.JSON);
            CreateVersion cv = new CreateVersion();
            ca.setFirstVersion(cv);
            VersionContent vc = new VersionContent();
            cv.setContent(vc);
            vc.setContent("{\"type\":\"object\"}");
            vc.setContentType(ContentTypes.APPLICATION_JSON);
            adminClient.groups().byGroupId("team-b").artifacts().post(ca);
        } catch (Exception e) {
            // already exists
        }
        assertNotNull(devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
    }

    @Test
    void devCannotReadNonMatchedArtifactInTeamB() {
        String id = createArtifact(adminClient, "team-b");
        var ex = assertThrows(Exception.class,
                () -> devClient.groups().byGroupId("team-b").artifacts().byArtifactId(id).get());
        assertForbidden(ex);
    }
}
