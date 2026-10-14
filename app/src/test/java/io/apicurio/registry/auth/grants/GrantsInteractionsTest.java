package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

/**
 * How per-resource authorization combines with the other authorization settings:
 * <ul>
 * <li>anonymous and authenticated read access skip RBAC but not grants (anonymous callers get the
 * {@value GrantsAccessController#ANONYMOUS_ROLE} role)</li>
 * <li>RBAC administrators bypass grants even when not listed in the grants file</li>
 * <li>the owner bypass can be disabled</li>
 * <li>registry-wide reports that cannot be filtered are limited to callers not restricted by grants</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(GrantsInteractionsTest.InteractionsProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsInteractionsTest extends AbstractResourceTestBase {

    private static final String GROUP = "grants-interactions";

    /** No admin_roles: RBAC admins must bypass grants on their own. */
    private static final String GRANTS_JSON = """
            {"grants": [
              {"principal_role": "$anonymous", "operation": "read", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "%1$s/public-"},
              {"principal": "eve", "operation": "read", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "%1$s/eve-"},
              {"principal": "bob", "operation": "read", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "%1$s/bob-"}
            ]}""".formatted(GROUP);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-interactions", ".json");
            Files.writeString(file, GRANTS_JSON);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class InteractionsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("apicurio.auth.anonymous-read-access.enabled", "true");
            map.put("apicurio.auth.authenticated-read-access.enabled", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.users.eve", "eve");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            // eve has no RBAC role at all: only authenticated read access lets her read
            map.put("quarkus.security.users.embedded.roles.eve", "no-registry-role");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.grants.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.grants.reload-enabled", "false");
            map.put("apicurio.auth.resource-based-authorization.owner-bypass.enabled", "false");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    private String create(String artifactIdPrefix) {
        String artifactId = artifactIdPrefix + TestUtils.generateArtifactId();
        clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON));
        return artifactId;
    }

    private static String path(String artifactId) {
        return "/registry/v3/groups/" + GROUP + "/artifacts/" + artifactId;
    }

    @Test
    public void anonymousReadAccessIsLimitedByTheAnonymousRole() {
        String visible = create("public-");
        String hidden = create("eve-");

        given().get(path(visible)).then().statusCode(200);
        given().get(path(hidden)).then().statusCode(401);
        given().queryParam("groupId", GROUP).queryParam("limit", 1000).get("/registry/v3/search/artifacts")
                .then().statusCode(200)
                .body("artifacts.artifactId", hasItem(visible))
                .body("artifacts.artifactId", everyItem(startsWith("public-")));
    }

    @Test
    public void authenticatedReadAccessSkipsRbacButNotGrants() {
        String own = create("eve-");
        String other = create("bob-");

        // eve has no registry role, so RBAC alone would deny her any read
        as("eve").get(path(own)).then().statusCode(200);
        as("eve").get(path(other)).then().statusCode(403);
    }

    @Test
    public void rbacAdminsBypassGrants() {
        String artifact = create("bob-");

        as("alice").get(path(artifact)).then().statusCode(200);
    }

    @Test
    public void ownerBypassCanBeDisabled() {
        String artifact = create("owned-");
        EditableArtifactMetaData md = new EditableArtifactMetaData();
        md.setOwner("bob");
        clientV3.groups().byGroupId(GROUP).artifacts().byArtifactId(artifact).put(md);

        as("bob").get(path(artifact)).then().statusCode(403);
    }

    @Test
    public void registryWideReportsAreLimitedToUnrestrictedCallers() {
        as("bob").get("/registry/v3/admin/gitops/status").then().statusCode(403);
        as("bob").get("/registry/v3/admin/gitops/validate").then().statusCode(403);
        // Admins are not restricted by grants; this storage is not GitOps, hence 409
        as("alice").get("/registry/v3/admin/gitops/status").then().statusCode(409);
    }
}
