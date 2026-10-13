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

    private static final String GRANTS_JSON = """
            {"config": {"admin_roles": ["sr-admin"]}, "grants": [
              {"principal": "bob", "operation": "write", "resource_type": "group",
               "resource_pattern_type": "exact", "resource_pattern": "%1$s"},
              {"principal": "bob", "operation": "write", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "%1$s/"},
              {"principal": "bob", "operation": "write", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "%1$s/%2$s", "deny": true}
            ]}""".formatted(GROUP, DENIED_ARTIFACT);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-artifact-scope", ".json");
            Files.writeString(file, GRANTS_JSON);
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
            map.put("apicurio.auth.resource-based-authorization.grants.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.grants.reload-enabled", "false");
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
}
