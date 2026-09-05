package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Owner-only authorization tests for the MCP Registry API.
 *
 * Publishing takes the server name from the request body rather than the path, so it cannot rely on
 * {@code AuthorizedStyle.GroupAndArtifact} and has to enforce ownership itself.
 */
@QuarkusTest
@TestProfile(McpRegistryAuthTestProfile.class)
public class McpRegistryAuthTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";

    /**
     * The shared setup in {@link AbstractResourceTestBase} clears global rules before each test, which
     * needs admin credentials once authorization is switched on.
     */
    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl)
                .vertx(vertx)
                .basicAuth("alice", "alice"));
    }

    private String uniqueNamespace() {
        return "io.github.auth" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String serverJson(String name, String version) {
        return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\"}";
    }

    @Test
    public void testOwnerCanPublishNewVersions() {
        String namespace = uniqueNamespace();
        String name = namespace + "/owned";

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "2.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200)
                .body("version", equalTo("2.0.0"));
    }

    @Test
    public void testNonOwnerCannotPublishIntoAnotherUsersServer() {
        String namespace = uniqueNamespace();
        String name = namespace + "/bobs";

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);

        // carol holds the developer role, so RBAC alone would let this through.
        given().auth().preemptive().basic("carol", "carol")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "2.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(403);
    }

    @Test
    public void testNonOwnerCanStillPublishTheirOwnServer() {
        String namespace = uniqueNamespace();

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(namespace + "/bobs", "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);

        // Same namespace, different server - ownership is per artifact, so this must be allowed.
        given().auth().preemptive().basic("carol", "carol")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(namespace + "/carols", "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);
    }

    @Test
    public void testAdminCanPublishIntoAnotherUsersServer() {
        String namespace = uniqueNamespace();
        String name = namespace + "/bobs";

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);

        given().auth().preemptive().basic("alice", "alice")
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "2.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);
    }

    @Test
    public void testAnonymousPublishIsRejected() {
        given()
                .when()
                .contentType(CT_JSON)
                .body(serverJson(uniqueNamespace() + "/anon", "1.0.0"))
                .post(BASE + "/publish")
                .then()
                .statusCode(401);
    }
}
