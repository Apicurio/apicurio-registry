package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A group-level write grant must not authorize operations on a specific artifact within that
 * group when the artifact itself has a grants-level deny. Regression test for the
 * {@code GroupOnly}/{@code GroupAndArtifact} style mix-up found in
 * {@code addArtifactVersionComment}/{@code deleteArtifactVersionComment}/
 * {@code updateArtifactVersionComment} and for {@code createArtifact}'s {@code ifExists} path,
 * which both resolve an artifact-scoped resource.
 *
 * <p>Owner-only authorization is disabled here (unlike {@link GrantsAuthorizationTest}), so that
 * the owner bypass cannot mask whether the grants layer itself evaluates the right resource: with
 * OBAC on, a non-owner is already blocked for every write regardless of grants, and an owner
 * unconditionally bypasses grants, so neither state can isolate this check.</p>
 */
@QuarkusTest
@TestProfile(GrantsArtifactScopeTest.ArtifactScopeProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsArtifactScopeTest extends AbstractResourceTestBase {

    private static final String GROUP = "grants-artifact-scope";
    private static final String DENIED_ARTIFACT = "no-write";
    private static final String DELETE_GROUP_PREFIX = "grants-delete";

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            deny User with name = "bob" to WRITE Artifact with name like "%1$s/%2$s*";
            deny User with name = "bob" to * Artifact with name like "%1$s/hidden-*";
            deny User with name = "bob" to WRITE Artifact with name = "%3$s-locked/locked";
            allow User with name = "bob" to WRITE Group with name = "%1$s";
            allow User with name = "bob" to WRITE Artifact with name like "%1$s/*";
            allow User with name = "bob" to WRITE Group with name like "%3$s*";
            allow User with name = "bob" to WRITE Artifact with name like "%3$s*";
            otherwise deny;
            """.formatted(GROUP, DENIED_ARTIFACT, DELETE_GROUP_PREFIX);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-artifact-scope", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class ArtifactScopeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            map.put("apicurio.rest.deletion.group.enabled", "true");
            map.put("apicurio.rest.deletion.artifact.enabled", "true");
            // ccompat schema IDs are global IDs in legacy mode
            map.put("apicurio.ccompat.legacy-id-mode.enabled", "true");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    private static String asBob() {
        return "bob";
    }

    private void createAsAdmin(String artifactId) {
        clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON));
    }

    @Test
    public void commentMutationIsAuthorizedAgainstTheArtifactNotJustTheGroup() {
        createAsAdmin(DENIED_ARTIFACT);
        String allowedArtifact = "with-write";
        createAsAdmin(allowedArtifact);

        String deniedComments = path(DENIED_ARTIFACT) + "/versions/branch=latest/comments";
        given().auth().preemptive().basic(asBob(), "bob").contentType(CT_JSON).body("{\"value\":\"hi\"}")
                .post(deniedComments).then().statusCode(403);
        // Editing or deleting an existing comment on the denied artifact is refused too, and the
        // comment is left unchanged
        String adminCommentId = given().auth().preemptive().basic("alice", "alice").contentType(CT_JSON)
                .body("{\"value\":\"by admin\"}").post(deniedComments).then().statusCode(200)
                .extract().path("commentId");
        given().auth().preemptive().basic(asBob(), "bob").contentType(CT_JSON).body("{\"value\":\"edited\"}")
                .put(deniedComments + "/" + adminCommentId).then().statusCode(403);
        given().auth().preemptive().basic(asBob(), "bob").delete(deniedComments + "/" + adminCommentId)
                .then().statusCode(403);
        given().auth().preemptive().basic("alice", "alice").get(deniedComments).then().statusCode(200)
                .body("size()", equalTo(1)).body("[0].commentId", equalTo(adminCommentId))
                .body("[0].value", equalTo("by admin"));

        String allowedComments = path(allowedArtifact) + "/versions/branch=latest/comments";
        String commentId = given().auth().preemptive().basic(asBob(), "bob").contentType(CT_JSON)
                .body("{\"value\":\"hi\"}").post(allowedComments).then().statusCode(200)
                .extract().path("commentId");
        given().auth().preemptive().basic(asBob(), "bob").contentType(CT_JSON).body("{\"value\":\"edited\"}")
                .put(allowedComments + "/" + commentId).then().statusCode(204);
        given().auth().preemptive().basic(asBob(), "bob").delete(allowedComments + "/" + commentId)
                .then().statusCode(204);
    }

    @Test
    public void createArtifactIfExistsIsAuthorizedAgainstTheArtifact() {
        String artifactId = "no-write-if-exists";
        createAsAdmin(artifactId);

        String body = "{\"artifactId\":\"" + artifactId + "\",\"artifactType\":\"JSON\",\"firstVersion\":"
                + "{\"content\":{\"content\":\"{\\\"type\\\":\\\"object\\\",\\\"v\\\":2}\",\"contentType\":\""
                + ContentTypes.APPLICATION_JSON + "\"}}}";
        for (String ifExists : List.of("CREATE_VERSION", "FIND_OR_CREATE_VERSION")) {
            given().auth().preemptive().basic(asBob(), "bob").contentType(CT_JSON).body(body)
                    .post("/registry/v3/groups/" + GROUP + "/artifacts?ifExists=" + ifExists)
                    .then().statusCode(403);
        }
        assertEquals(1, clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId)
                .versions().get().getCount());
    }

    private static String path(String artifactId) {
        return "/registry/v3/groups/" + GROUP + "/artifacts/" + artifactId;
    }

    private static String createBody(String artifactId, String referenceJson) {
        return "{\"artifactId\":\"" + artifactId + "\",\"artifactType\":\"JSON\",\"firstVersion\":"
                + "{\"content\":{\"content\":\"{\\\"type\\\":\\\"object\\\"}\",\"contentType\":\""
                + ContentTypes.APPLICATION_JSON + "\",\"references\":[" + referenceJson + "]}}}";
    }

    private static RequestSpecification bob() {
        return given().auth().preemptive().basic("bob", "bob");
    }

    @Test
    public void creatingAnArtifactRequiresWriteOnItsName() {
        // Group write alone is not enough: a deny on the name must stop creation, or the creator
        // would own the artifact and bypass the deny for good
        bob().contentType(CT_JSON).body(createBody(DENIED_ARTIFACT + "-new", ""))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(403);
        given().auth().preemptive().basic("alice", "alice")
                .get(path(DENIED_ARTIFACT + "-new")).then().statusCode(404);
        bob().contentType(CT_JSON).header("X-Registry-ArtifactId", DENIED_ARTIFACT + "-v2")
                .header("X-Registry-ArtifactType", "JSON").body("{\"type\":\"object\"}")
                .post("/registry/v2/groups/" + GROUP + "/artifacts").then().statusCode(403);

        bob().contentType(CT_JSON).body(createBody("created-by-bob", ""))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(200);
    }

    @Test
    public void groupDeletesRequireWriteOnEveryArtifact() {
        String locked = DELETE_GROUP_PREFIX + "-locked";
        String open = DELETE_GROUP_PREFIX + "-open";
        clientV3.groups().byGroupId(locked).artifacts().post(TestUtils.clientCreateArtifact("locked",
                ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON));
        clientV3.groups().byGroupId(open).artifacts().post(TestUtils.clientCreateArtifact("deletable",
                ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON));

        bob().delete("/registry/v3/groups/" + locked + "/artifacts").then().statusCode(403);
        bob().delete("/registry/v3/groups/" + locked).then().statusCode(403);
        bob().delete("/registry/v2/groups/" + locked + "/artifacts").then().statusCode(403);
        assertEquals(1, clientV3.groups().byGroupId(locked).artifacts().get().getCount().intValue());

        bob().delete("/registry/v3/groups/" + open + "/artifacts").then().statusCode(204);
        assertEquals(0, clientV3.groups().byGroupId(open).artifacts().get().getCount().intValue());
    }

    @Test
    public void outboundReferenceGraphHidesUnreadableArtifacts() {
        createAsAdmin("hidden-target");
        String reference = "{\"groupId\":\"" + GROUP + "\",\"artifactId\":\"hidden-target\","
                + "\"version\":\"1\",\"name\":\"target.json\"}";
        given().auth().preemptive().basic("alice", "alice").contentType(CT_JSON)
                .body(createBody("ref-source", reference))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(200);
        String graph = path("ref-source") + "/versions/1/references/graph?direction=OUTBOUND";

        // bob may read the source, so sees that it references hidden-target, but not its metadata
        bob().get(graph).then().statusCode(200)
                .body("nodes.find { it.artifactId == 'hidden-target' }.artifactType", nullValue())
                .body("nodes.find { it.artifactId == 'hidden-target' }.name", nullValue());
        given().auth().preemptive().basic("alice", "alice").get(graph).then().statusCode(200)
                .body("nodes.find { it.artifactId == 'hidden-target' }.artifactType", equalTo("JSON"));
    }

    private long globalIdOf(String artifactId) {
        return clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("branch=latest").get().getGlobalId();
    }

    @Test
    public void legacySchemaIdIsResolvedAsAGlobalIdForSubjects() {
        // Same content under two artifacts: one content ID, two global IDs
        String content = "{\"type\":\"object\",\"title\":\"legacy-shared\"}";
        for (String artifactId : List.of("legacy-first", "legacy-second", "hidden-legacy")) {
            clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                    ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON));
        }

        // The global ID names one version, so exactly its subject is returned
        bob().get("/ccompat/v7/schemas/ids/" + globalIdOf("legacy-second") + "/subjects").then().statusCode(200)
                .body("$", equalTo(List.of("legacy-second")));
        // And it is authorized as that artifact, which bob may not read
        bob().get("/ccompat/v7/schemas/ids/" + globalIdOf("hidden-legacy") + "/subjects").then().statusCode(403);
    }
}
