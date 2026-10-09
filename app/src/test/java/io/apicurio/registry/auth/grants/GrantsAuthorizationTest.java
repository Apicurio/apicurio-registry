package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end per-resource authorization with the grants-based Kroxylicious Authorizer enabled.
 * Every API surface that addresses or lists resources is covered with an allowed and a denied
 * (403 or filtered) case. See {@code grants/grants-auth-test.json} for the grants.
 */
@QuarkusTest
@TestProfile(GrantsAuthorizationTest.GrantsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsAuthorizationTest extends AbstractResourceTestBase {

    private static final String TEAM_A = "grants-team-a";
    private static final String TEAM_B = "grants-team-b";
    private static final String TEAM_C = "grants-team-c";

    public static class GrantsTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("apicurio.auth.owner-only-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob1", "bob1");
            map.put("quarkus.security.users.embedded.users.bob2", "bob2");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob1", "sr-developer");
            map.put("quarkus.security.users.embedded.roles.bob2", "sr-developer");
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.iceberg.enabled", "true");
            map.put("apicurio.mcp-registry.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.grants.path", grantsFilePath());
            map.put("apicurio.auth.resource-based-authorization.grants.reload-enabled", "false");
            return map;
        }

        private static String grantsFilePath() {
            try {
                return Path.of(GrantsAuthorizationTest.class.getClassLoader()
                        .getResource("grants/grants-auth-test.json").toURI()).toString();
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth("alice", "alice"));
    }

    /** Test users use their username as password. */
    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    /** Creates an artifact as admin, with unique content so content IDs are never shared. */
    private String createAsAdmin(String groupId, String artifactIdPrefix) throws Exception {
        String artifactId = artifactIdPrefix + TestUtils.generateArtifactId();
        String content = "{\"type\":\"object\",\"title\":\"" + UUID.randomUUID() + "\"}";
        CreateArtifact create = TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, content,
                ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(create);
        return artifactId;
    }

    private long contentIdOf(String groupId, String artifactId) {
        return clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").get().getContentId();
    }

    private static String artifactPath(String groupId, String artifactId) {
        return "/registry/v3/groups/" + groupId + "/artifacts/" + artifactId;
    }

    // ==================== v3 point access ====================

    @Test
    public void prefixGrantAllowsArtifactRead() throws Exception {
        String artifactId = createAsAdmin(TEAM_A, "a-");

        as("bob1").get(artifactPath(TEAM_A, artifactId)).then().statusCode(200)
                .body("artifactId", equalTo(artifactId));
    }

    @Test
    public void artifactWithoutMatchingGrantIsForbidden() throws Exception {
        String artifactId = createAsAdmin(TEAM_B, "hidden-");

        as("bob1").get(artifactPath(TEAM_B, artifactId)).then().statusCode(403);
        as("bob2").get(artifactPath(TEAM_B, artifactId)).then().statusCode(403);
    }

    @Test
    public void subPathPrefixGrantAllowsOnlyMatchingArtifacts() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        as("bob2").get(artifactPath(TEAM_B, visible)).then().statusCode(200);
        as("bob2").get(artifactPath(TEAM_B, hidden)).then().statusCode(403);
    }

    @Test
    public void groupEndpointsUseGroupGrants() throws Exception {
        createAsAdmin(TEAM_A, "a-");

        as("bob1").get("/registry/v3/groups/" + TEAM_A).then().statusCode(200);
        // Artifact grants inside a group do not grant the group itself
        as("bob2").get("/registry/v3/groups/" + TEAM_A).then().statusCode(403);
    }

    @Test
    public void adminRoleBypassesGrants() throws Exception {
        String artifactId = createAsAdmin(TEAM_B, "hidden-");

        as("alice").get(artifactPath(TEAM_B, artifactId)).then().statusCode(200);
    }

    @Test
    public void ownerBypassesGrantsOnlyForOwnedArtifacts() throws Exception {
        String owned = createAsAdmin(TEAM_C, "owned-");
        String other = createAsAdmin(TEAM_C, "other-");
        EditableArtifactMetaData md = new EditableArtifactMetaData();
        md.setOwner("bob2");
        clientV3.groups().byGroupId(TEAM_C).artifacts().byArtifactId(owned).put(md);

        as("bob2").get(artifactPath(TEAM_C, owned)).then().statusCode(200);
        as("bob2").get(artifactPath(TEAM_C, other)).then().statusCode(403);
        // Owning an artifact also grants access to its content
        as("bob2").get("/registry/v3/ids/contentIds/" + contentIdOf(TEAM_C, owned)).then().statusCode(200);
        as("bob2").get("/registry/v3/ids/contentIds/" + contentIdOf(TEAM_C, other)).then().statusCode(403);
    }

    @Test
    public void groupCreationRequiresGroupWriteGrant() {
        String allowed = "grants-new-" + UUID.randomUUID();
        String denied = "grants-other-" + UUID.randomUUID();

        as("bob1").contentType(CT_JSON).body("{\"groupId\":\"" + allowed + "\"}")
                .post("/registry/v3/groups").then().statusCode(200);
        as("bob1").contentType(CT_JSON).body("{\"groupId\":\"" + denied + "\"}")
                .post("/registry/v3/groups").then().statusCode(403);
        as("bob1").contentType(CT_JSON).body("{\"id\":\"" + denied + "\"}")
                .post("/registry/v2/groups").then().statusCode(403);
    }

    // ==================== Content IDs ====================

    @Test
    public void contentIdRequiresAccessToAnArtifactUsingIt() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");
        long visibleContent = contentIdOf(TEAM_B, visible);
        long hiddenContent = contentIdOf(TEAM_B, hidden);

        as("bob2").get("/registry/v3/ids/contentIds/" + visibleContent).then().statusCode(200);
        as("bob2").get("/registry/v3/ids/contentIds/" + hiddenContent).then().statusCode(403);
        as("bob2").get("/registry/v2/ids/contentIds/" + hiddenContent).then().statusCode(403);
    }

    @Test
    public void unknownIdsFailClosed() {
        String unknownHash = "0".repeat(64);
        // Unknown and unreadable IDs are indistinguishable to a caller without grants
        Map<String, Integer> statuses = new LinkedHashMap<>();
        for (String path : List.of("/registry/v3/ids/contentHashes/" + unknownHash,
                "/registry/v3/ids/globalIds/" + Long.MAX_VALUE,
                "/registry/v3/ids/contentIds/" + Long.MAX_VALUE)) {
            statuses.put(path, as("bob2").get(path).statusCode());
        }
        assertEquals(List.of(403, 403, 403), List.copyOf(statuses.values()), statuses.toString());
        // Admins are not subject to grants, so they get the endpoint's own answer
        as("alice").get("/registry/v3/ids/globalIds/" + Long.MAX_VALUE).then().statusCode(404);
    }

    // ==================== Search and list filtering ====================

    @Test
    public void v3SearchOnlyReturnsArtifactsMatchingSubPathPrefixGrant() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        as("bob2").queryParam("groupId", TEAM_B).queryParam("limit", 1000)
                .get("/registry/v3/search/artifacts").then().statusCode(200)
                .body("artifacts.artifactId", hasItem(visible))
                .body("artifacts.artifactId", not(hasItem(hidden)))
                .body("artifacts.artifactId", everyItem(startsWith("visible-")));
    }

    @Test
    public void v3SearchExcludesGroupsWithoutGrants() throws Exception {
        String teamA = createAsAdmin(TEAM_A, "a-");
        String teamB = createAsAdmin(TEAM_B, "visible-");

        as("bob1").queryParam("limit", 1000).get("/registry/v3/search/artifacts").then().statusCode(200)
                .body("artifacts.artifactId", hasItem(teamA))
                .body("artifacts.artifactId", not(hasItem(teamB)));
    }

    @Test
    public void searchCountMatchesFilteredResults() throws Exception {
        createAsAdmin(TEAM_B, "visible-");
        createAsAdmin(TEAM_B, "hidden-");

        List<String> ids = as("bob2").queryParam("groupId", TEAM_B).queryParam("limit", 1000)
                .get("/registry/v3/search/artifacts").then().statusCode(200)
                .extract().path("artifacts.artifactId");
        int count = as("bob2").queryParam("groupId", TEAM_B).queryParam("limit", 1000)
                .get("/registry/v3/search/artifacts").then().extract().path("count");

        assertEquals(ids.size(), count);
    }

    @Test
    public void v2SearchAndListAreFiltered() throws Exception {
        String teamA = createAsAdmin(TEAM_A, "a-");
        String teamB = createAsAdmin(TEAM_B, "visible-");

        as("bob1").queryParam("limit", 1000).get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("artifacts.id", hasItem(teamA))
                .body("artifacts.id", not(hasItem(teamB)));
        as("bob1").queryParam("limit", 1000).get("/registry/v2/groups").then().statusCode(200)
                .body("groups.id", hasItem(TEAM_A))
                .body("groups.id", not(hasItem(TEAM_B)));
    }

    @Test
    public void v3GroupListIsFiltered() throws Exception {
        createAsAdmin(TEAM_A, "a-");
        createAsAdmin(TEAM_B, "visible-");

        as("bob1").queryParam("limit", 1000).get("/registry/v3/groups").then().statusCode(200)
                .body("groups.groupId", hasItem(TEAM_A))
                .body("groups.groupId", not(hasItem(TEAM_B)));
    }

    // ==================== Confluent compatibility API ====================

    @Test
    public void ccompatResolvesGroupFromHeader() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        as("bob2").header("X-Registry-GroupId", TEAM_B).get("/ccompat/v7/subjects/" + visible + "/versions")
                .then().statusCode(200);
        // The header group must be authorized, not the default group
        as("bob2").header("X-Registry-GroupId", TEAM_B).get("/ccompat/v7/subjects/" + hidden + "/versions")
                .then().statusCode(403);
    }

    @Test
    public void ccompatSubjectListIsFiltered() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        as("bob2").header("X-Registry-GroupId", TEAM_B).get("/ccompat/v7/subjects").then().statusCode(200)
                .body("$", hasItem(visible))
                .body("$", not(hasItem(hidden)));
    }

    @Test
    public void ccompatSchemaIdIsAContentId() throws Exception {
        String visible = createAsAdmin(TEAM_B, "visible-");
        String hidden = createAsAdmin(TEAM_B, "hidden-");

        as("bob2").get("/ccompat/v7/schemas/ids/" + contentIdOf(TEAM_B, visible)).then().statusCode(200);
        as("bob2").get("/ccompat/v7/schemas/ids/" + contentIdOf(TEAM_B, hidden)).then().statusCode(403);
    }

    // ==================== Iceberg REST catalog ====================

    @Test
    public void icebergAuthorizesTheNamespaceNotTheCatalogPrefix() throws Exception {
        createAsAdmin(TEAM_A, "a-");
        createAsAdmin(TEAM_B, "visible-");

        // The prefix is caller-controlled and must not influence authorization
        as("bob1").get("/iceberg/v1/" + TEAM_A + "/namespaces/" + TEAM_B).then().statusCode(403);
        as("bob1").get("/iceberg/v1/" + TEAM_A + "/namespaces/" + TEAM_A).then().statusCode(200);
    }

    @Test
    public void icebergNamespaceCreationAndListingUseGrants() {
        String allowed = "grants-new-" + UUID.randomUUID().toString().substring(0, 8);
        String denied = "grants-other-" + UUID.randomUUID().toString().substring(0, 8);

        as("bob1").contentType(CT_JSON).body(Map.of("namespace", List.of(allowed)))
                .post("/iceberg/v1/default/namespaces").then().statusCode(200);
        as("bob1").contentType(CT_JSON).body(Map.of("namespace", List.of(denied)))
                .post("/iceberg/v1/default/namespaces").then().statusCode(403);
        as("bob1").get("/iceberg/v1/default/namespaces").then().statusCode(200)
                .body("namespaces.flatten()", hasItem(allowed))
                .body("namespaces.flatten()", not(hasItem(TEAM_B)));
    }

    // ==================== MCP Registry API ====================

    @Test
    public void mcpPublishRequiresWriteGrantOnTheServerName() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String allowed = "io.github.grantsa" + suffix + "/weather";
        String denied = "io.github.grantsb" + suffix + "/weather";

        as("bob1").contentType(CT_JSON).body(serverJson(allowed)).post("/mcp-registry/v0.1/publish")
                .then().statusCode(200);
        as("bob1").contentType(CT_JSON).body(serverJson(denied)).post("/mcp-registry/v0.1/publish")
                .then().statusCode(403);
        as("bob2").get("/mcp-registry/v0.1/servers").then().statusCode(200)
                .body("servers.server.name", not(hasItem(allowed)));
    }

    @Test
    public void ownerOnlyAuthorizationAppliesToBodyAddressedTargets() {
        // bob1 and bob2 both hold write grants on io.github.shared*, but only the owner may publish
        String name = "io.github.shared" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "/w";

        as("bob1").contentType(CT_JSON).body(serverJson(name, "1.0.0")).post("/mcp-registry/v0.1/publish")
                .then().statusCode(200);
        as("bob2").contentType(CT_JSON).body(serverJson(name, "2.0.0")).post("/mcp-registry/v0.1/publish")
                .then().statusCode(403);
        as("bob1").contentType(CT_JSON).body(serverJson(name, "2.0.0")).post("/mcp-registry/v0.1/publish")
                .then().statusCode(200);
    }

    private static String serverJson(String name) {
        return serverJson(name, "1.0.0");
    }

    private static String serverJson(String name, String version) {
        return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\",\"description\":\"Grants test\"}";
    }
}
