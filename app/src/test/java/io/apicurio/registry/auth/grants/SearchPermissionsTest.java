package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code permissions} reported in v3 search and list results must agree with what point access
 * actually allows: for every caller and every resource it returns, each flag is true exactly when
 * the corresponding request on that resource is authorized (read: get metadata, write: update
 * metadata, admin: an Admin-level operation, dropping the Iceberg table or namespace).
 */
@QuarkusTest
@TestProfile(SearchPermissionsTest.PermissionsProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class SearchPermissionsTest extends AbstractResourceTestBase {

    private static final String GROUP = "search-permissions";

    /** dev1 has a grant per level; dev2 may read everything; reader has write grants but a read-only role. */
    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group, Role;
            allow User with name = "dev1" to ADMIN Artifact with name like "%1$s/admin-*";
            allow User with name = "dev1" to WRITE Artifact with name like "%1$s/write-*";
            allow User with name = "dev1" to READ Artifact with name like "%1$s/read-*";
            allow User with name = "dev1" to READ Group with name = "%1$s";
            allow User with name = "dev2" to READ Artifact with name like "%1$s/*";
            allow User with name = "reader" to WRITE Artifact with name like "%1$s/*";
            allow User with name = "reader" to READ Group with name = "%1$s";
            allow Role with name = "$anonymous" to READ Artifact with name like "%1$s/read-*";
            otherwise deny;
            """.formatted(GROUP);

    private static final Path RULES_FILE = createRulesFile();

    private static final List<String> FIXTURE_PREFIXES = List.of("read-", "write-", "admin-", "hidden-", "owned-");

    private static Path createRulesFile() {
        try {
            Path file = Files.createTempFile("search-permissions", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class PermissionsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("apicurio.auth.owner-only-authorization", "true");
            map.put("apicurio.auth.anonymous-read-access.enabled", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.dev1", "dev1");
            map.put("quarkus.security.users.embedded.users.dev2", "dev2");
            map.put("quarkus.security.users.embedded.users.reader", "reader");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.dev1", "sr-developer");
            map.put("quarkus.security.users.embedded.roles.dev2", "sr-developer");
            map.put("quarkus.security.users.embedded.roles.reader", "sr-readonly");
            // The admin probe is an Iceberg drop, the Admin-level operation on a single resource
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.iceberg.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", RULES_FILE.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice")
                .requestTimeout(CLIENT_CONNECT_TIMEOUT_MS, CLIENT_READ_IDLE_TIMEOUT_MS));
    }

    @BeforeAll
    void createGroup() {
        CreateGroup group = new CreateGroup();
        group.setGroupId(GROUP);
        clientV3.groups().post(group);
    }

    /** null means an anonymous request; test users use their username as password. */
    private static RequestSpecification as(String user) {
        return user == null ? given() : given().auth().preemptive().basic(user, user);
    }

    private static final Set<Integer> DENIED = Set.of(401, 403);

    /**
     * @return whether the request was authorized: {@code allowedStatus} if it was, 401 or 403 if
     *         not; any other status fails the test rather than being mistaken for either
     */
    private static boolean authorized(int status, int allowedStatus) {
        assertTrue(status == allowedStatus || DENIED.contains(status), "unexpected status " + status);
        return status == allowedStatus;
    }

    private static Map<String, Boolean> permissions(boolean read, boolean write, boolean admin) {
        return Map.of("read", read, "write", write, "admin", admin);
    }

    private static final Map<String, Boolean> READ_ONLY = permissions(true, false, false);
    private static final Map<String, Boolean> READ_WRITE = permissions(true, true, false);
    private static final Map<String, Boolean> ALL = permissions(true, true, true);

    /**
     * Creates one artifact per fixture prefix, as alice. The "owned-" artifact is then transferred
     * to {@code owner}; its version keeps alice as owner, so versions must use the artifact owner.
     */
    private Map<String, String> createFixtures(String owner) {
        String run = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> fixtures = new LinkedHashMap<>();
        for (String prefix : FIXTURE_PREFIXES) {
            String artifactId = prefix + run;
            clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                    ArtifactType.JSON, "{\"title\":\"" + artifactId + "\"}", ContentTypes.APPLICATION_JSON));
            fixtures.put(prefix, artifactId);
        }
        if (owner != null) {
            EditableArtifactMetaData md = new EditableArtifactMetaData();
            md.setOwner(owner);
            clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(fixtures.get("owned-")).put(md);
        }
        return fixtures;
    }

    private static Map<String, Boolean> flags(JsonPath json, String path) {
        Map<String, Boolean> permissions = json.getMap(path);
        assertNotNull(permissions, "no permissions at " + path);
        return permissions;
    }

    private static String artifactPath(String artifactId) {
        return "/registry/v3/groups/" + GROUP + "/artifacts/" + artifactId;
    }

    /**
     * Checks that the reported flags are the expected ones, and that each matches the outcome of
     * the corresponding request.
     *
     * @param expected the expected permissions of each listed fixture, by prefix; the others must
     *        not be listed nor readable
     */
    private void assertArtifactParity(String user, Map<String, Map<String, Boolean>> expected) {
        Map<String, String> fixtures = createFixtures(user);
        JsonPath search = as(user).queryParam("groupId", GROUP).queryParam("limit", 1000)
                .get("/registry/v3/search/artifacts").then().statusCode(200).extract().jsonPath();
        List<String> found = search.getList("artifacts.artifactId");

        for (Map.Entry<String, String> fixture : fixtures.entrySet()) {
            String artifactId = fixture.getValue();
            boolean read = authorized(as(user).get(artifactPath(artifactId)).statusCode(), 200);
            assertEquals(expected.containsKey(fixture.getKey()), found.contains(artifactId),
                    user + " listing of " + artifactId);
            assertEquals(read, found.contains(artifactId), user + " read of " + artifactId);
            if (!read) {
                continue;
            }
            Map<String, Boolean> reported = flags(search,
                    "artifacts.find { it.artifactId == '" + artifactId + "' }.permissions");
            assertEquals(expected.get(fixture.getKey()), reported, user + " permissions on " + artifactId);

            // A version is reported with its artifact's permissions, though its own owner is alice
            JsonPath versions = as(user).get(artifactPath(artifactId) + "/versions").then().statusCode(200)
                    .extract().jsonPath();
            assertEquals(reported, flags(versions, "versions[0].permissions"), user + " version of " + artifactId);

            boolean write = authorized(as(user).contentType(CT_JSON).body("{\"name\":\"renamed\"}")
                    .put(artifactPath(artifactId)).statusCode(), 204);
            // Last: when allowed, this deletes the artifact
            boolean admin = authorized(as(user).delete("/iceberg/v1/default/namespaces/" + GROUP + "/tables/"
                    + artifactId).statusCode(), 204);
            assertEquals(permissions(read, write, admin), reported, user + " point access to " + artifactId);
        }
    }

    @Test
    public void adminPermissionsMatchPointAccess() {
        assertArtifactParity("alice", Map.of("read-", ALL, "write-", ALL, "admin-", ALL, "hidden-", ALL,
                "owned-", ALL));
    }

    @Test
    public void developerPermissionsMatchPointAccess() {
        // Owner-only authorization: dev1 may write only what dev1 owns, whatever the grants
        assertArtifactParity("dev1", Map.of("read-", READ_ONLY, "write-", READ_ONLY, "admin-", READ_ONLY,
                "owned-", READ_WRITE));
    }

    @Test
    public void ownerPermissionsMatchPointAccess() {
        assertArtifactParity("dev2", Map.of("read-", READ_ONLY, "write-", READ_ONLY, "admin-", READ_ONLY,
                "hidden-", READ_ONLY, "owned-", READ_WRITE));
    }

    @Test
    public void readOnlyPermissionsMatchPointAccess() {
        // Write grants do not make a read-only role writable, even for the owner
        assertArtifactParity("reader", Map.of("read-", READ_ONLY, "write-", READ_ONLY, "admin-", READ_ONLY,
                "hidden-", READ_ONLY, "owned-", READ_ONLY));
    }

    @Test
    public void anonymousPermissionsMatchPointAccess() {
        assertArtifactParity(null, Map.of("read-", READ_ONLY));
    }

    /**
     * The edge case: an owner with no grant beyond read, with the owner bypass on, may read and
     * write, but a developer may not administer.
     */
    @Test
    public void ownersWithoutAWriteGrantMayWriteButNotAdminister() {
        Map<String, String> fixtures = createFixtures("dev2");
        String owned = fixtures.get("owned-");

        JsonPath search = as("dev2").queryParam("groupId", GROUP).queryParam("limit", 1000)
                .get("/registry/v3/search/artifacts").then().statusCode(200).extract().jsonPath();

        assertEquals(READ_WRITE, flags(search, "artifacts.find { it.artifactId == '" + owned + "' }.permissions"));
        assertEquals(READ_ONLY, flags(search, "artifacts.find { it.artifactId == '" + fixtures.get("write-") + "' }.permissions"));
    }

    @Test
    public void groupPermissionsMatchPointAccess() {
        createFixtures(null);
        Map<String, Map<String, Boolean>> expected = new HashMap<>();
        expected.put("alice", ALL);
        expected.put("dev1", READ_ONLY);
        expected.put("dev2", null);
        expected.put("reader", READ_ONLY);
        expected.put(null, null);
        expected.forEach((user, permissions) -> {
            JsonPath groups = as(user).queryParam("groupId", GROUP).get("/registry/v3/search/groups").then()
                    .statusCode(200).extract().jsonPath();
            boolean listed = groups.getList("groups.groupId").contains(GROUP);
            boolean read = authorized(as(user).get("/registry/v3/groups/" + GROUP).statusCode(), 200);
            assertEquals(permissions != null, listed, user + " listing of the group");
            assertEquals(read, listed, user + " read of the group");
            if (!listed) {
                return;
            }
            Map<String, Boolean> reported = flags(groups, "groups.find { it.groupId == '" + GROUP + "' }.permissions");
            assertEquals(permissions, reported, user + " group permissions");
            boolean write = authorized(as(user).contentType(CT_JSON).body("{\"description\":\"updated\"}")
                    .put("/registry/v3/groups/" + GROUP).statusCode(), 204);
            // The namespace holds tables, so an authorized drop is refused as a conflict
            boolean admin = authorized(as(user).delete("/iceberg/v1/default/namespaces/" + GROUP).statusCode(), 409);
            assertEquals(permissions(read, write, admin), reported, user + " point access to the group");
        });
        clientV3.groups().byGroupId(GROUP).get();
    }

    /**
     * The v3 operations returning searched artifacts, versions or groups report permissions, on
     * every item ({@code searchContracts} is wired the same way but needs contract data).
     */
    @Test
    public void everySearchAndListOperationReportsPermissions() throws Exception {
        String readable = createFixtures(null).get("read-");
        createArtifactVersion(GROUP, readable, "{\"title\":\"v2\"}", ContentTypes.APPLICATION_JSON);
        Map<String, Boolean> expected = READ_ONLY;
        String query = "?groupId=" + GROUP + "&artifactId=" + readable + "&limit=1000";

        Map<String, String> artifactResults = Map.of(
                "/registry/v3/search/artifacts" + query, "artifacts",
                "/registry/v3/groups/" + GROUP + "/artifacts?limit=1000", "artifacts");
        artifactResults.forEach((url, list) -> {
            JsonPath json = as("dev1").get(url).then().statusCode(200).extract().jsonPath();
            assertEquals(expected, flags(json, list + ".find { it.artifactId == '" + readable + "' }.permissions"),
                    url);
        });

        for (String url : List.of("/registry/v3/search/versions" + query, artifactPath(readable) + "/versions",
                artifactPath(readable) + "/branches/latest/versions")) {
            JsonPath json = as("dev1").get(url).then().statusCode(200).extract().jsonPath();
            assertEquals(2, json.getList("versions").size(), url);
            assertEquals(expected, flags(json, "versions[0].permissions"), url);
            assertEquals(expected, flags(json, "versions[1].permissions"), url);
        }

        String content = "{\"title\":\"" + readable + "\"}";
        JsonPath byContent = as("dev1").contentType(CT_JSON).body(content)
                .post("/registry/v3/search/artifacts?limit=1000").then().statusCode(200).extract().jsonPath();
        assertEquals(expected, flags(byContent, "artifacts.find { it.artifactId == '" + readable + "' }.permissions"));
        JsonPath versionsByContent = as("dev1").contentType(CT_JSON).body(content)
                .post("/registry/v3/search/versions?limit=1000").then().statusCode(200).extract().jsonPath();
        assertEquals(expected, flags(versionsByContent, "versions.find { it.artifactId == '" + readable
                + "' }.permissions"));

        JsonPath groups = as("dev1").queryParam("groupId", GROUP).get("/registry/v3/search/groups").then()
                .statusCode(200).extract().jsonPath();
        assertTrue(groups.getList("groups.groupId").contains(GROUP));
        assertEquals(READ_ONLY, flags(groups, "groups.find { it.groupId == '" + GROUP + "' }.permissions"));
    }
}
