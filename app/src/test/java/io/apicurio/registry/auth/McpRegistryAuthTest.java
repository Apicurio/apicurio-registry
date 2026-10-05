package io.apicurio.registry.auth;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.apicurio.registry.noprofile.mcpregistry.rest.v0.McpRegistryRequests.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Owner-only authorization tests for the MCP Registry API.
 *
 * Publishing takes the server name from the request body rather than the path, so it cannot rely on
 * {@code AuthorizedStyle.McpServerName} and has to enforce ownership itself. Delete and the status updates
 * do rely on it: {@code isOwner()} parses parameter 0 with {@code McpServerName.parse} and checks the owner
 * of the resulting group/artifact, so the tests for those endpoints are what catch a signature change.
 */
@QuarkusTest
@TestProfile(McpRegistryAuthTestProfile.class)
public class McpRegistryAuthTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";
    private static final String DEPRECATE = "{\"status\":\"deprecated\"}";

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
        return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\",\"description\":\"Auth test\"}";
    }

    private void publishAs(String user, String name, String version) {
        given().auth().preemptive().basic(user, user)
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, version))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);
    }

    @Test
    public void testMalformedServerNameIsRejectedWithoutReachingTheEndpoint() {
        // isOwner() parses parameter 0 through McpServerName.parse, so a malformed name is now rejected
        // inside the authorization interceptor. Only an auth-enabled profile exercises that path, and an
        // exception raised there is easy to regress into a 500, so pin the status and the body shape.
        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .get(BASE + "/servers/notaname")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid MCP server name: expected a reverse-DNS namespace and a"
                        + " server id separated by a single slash, for example 'io.github.user/weather'"))
                .body("name", nullValue());

        // Two slashes: decoded by the container, rejected by SERVER_NAME_PATTERN rather than by the router.
        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .urlEncodingEnabled(false)
                .get(BASE + "/servers/io.github.a%2Fb%2Fc/versions/1.0.0")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid MCP server name: expected a reverse-DNS namespace and a"
                        + " server id separated by a single slash, for example 'io.github.user/weather'"));

        // Same on a write endpoint, where the interceptor runs at Write level. RestAssured.given() rather
        // than the shared helper: the helper rewrites a readable 'namespace/serverId' pair into the %2F
        // form, and on a deliberately malformed path it would encode 'notaname/versions' as the name.
        RestAssured.given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .delete(BASE + "/servers/notaname/versions/1.0.0")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid MCP server name: expected a reverse-DNS namespace and a"
                        + " server id separated by a single slash, for example 'io.github.user/weather'"));
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
                .body("server.version", equalTo("2.0.0"));
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
                .statusCode(403)
                .body("error", notNullValue())
                .body("name", nullValue());
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
                .statusCode(401)
                .body("error", notNullValue())
                .body("name", nullValue());
    }

    // === Delete and status updates ===

    @Test
    public void testNonOwnerCannotDeleteAnotherUsersVersion() {
        String namespace = uniqueNamespace();
        String version = BASE + "/servers/" + namespace + "/bobs/versions/1.0.0";
        publishAs("bob1", namespace + "/bobs", "1.0.0");

        given().auth().preemptive().basic("carol", "carol")
                .when()
                .delete(version)
                .then()
                .statusCode(403)
                .body("error", notNullValue())
                .body("name", nullValue());

        // Still there, and its owner can delete it.
        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .get(version)
                .then()
                .statusCode(200);
        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .delete(version)
                .then()
                .statusCode(200);
    }

    @Test
    public void testEncodedServerNamePreservesOwnershipChecks() {
        String namespace = uniqueNamespace();
        publishAs("bob1", namespace + "/encoded", "1.0.0");
        String path = BASE + "/servers/" + namespace + "%2Fencoded/versions/1.0.0/status";
        given().urlEncodingEnabled(false).auth().preemptive().basic("carol", "carol")
                .contentType(CT_JSON).body(DEPRECATE).patch(path).then().statusCode(403);
        given().auth().preemptive().basic("bob1", "bob1")
                .get(BASE + "/servers/" + namespace + "/encoded/versions/1.0.0").then().statusCode(200)
                .body("_meta.'io.modelcontextprotocol.registry/official'.status", equalTo("active"));
        given().urlEncodingEnabled(false).auth().preemptive().basic("bob1", "bob1")
                .contentType(CT_JSON).body(DEPRECATE).patch(path).then().statusCode(200)
                .body("server.name", equalTo(namespace + "/encoded"))
                .body("_meta.'io.modelcontextprotocol.registry/official'.status", equalTo("deprecated"));
    }

    @Test
    public void testNonOwnerCannotChangeAnotherUsersVersionStatus() {
        String namespace = uniqueNamespace();
        String version = BASE + "/servers/" + namespace + "/bobs/versions/1.0.0";
        publishAs("bob1", namespace + "/bobs", "1.0.0");

        given().auth().preemptive().basic("carol", "carol")
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(version + "/status")
                .then()
                .statusCode(403)
                .body("error", notNullValue())
                .body("name", nullValue());

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(version + "/status")
                .then()
                .statusCode(200)
                .body("_meta.'io.modelcontextprotocol.registry/official'.status", equalTo("deprecated"));
    }

    @Test
    public void testNonOwnerCannotChangeAnotherUsersServerStatus() {
        String namespace = uniqueNamespace();
        String server = BASE + "/servers/" + namespace + "/bobs";
        publishAs("bob1", namespace + "/bobs", "1.0.0");

        given().auth().preemptive().basic("carol", "carol")
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(server + "/status")
                .then()
                .statusCode(403)
                .body("error", notNullValue())
                .body("name", nullValue());

        given().auth().preemptive().basic("bob1", "bob1")
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(server + "/status")
                .then()
                .statusCode(200)
                .body("servers[0]._meta.'io.modelcontextprotocol.registry/official'.status", equalTo("deprecated"));
    }

    @Test
    public void testAnonymousCannotDeleteOrChangeStatus() {
        String namespace = uniqueNamespace();
        String server = BASE + "/servers/" + namespace + "/bobs";
        publishAs("bob1", namespace + "/bobs", "1.0.0");

        given()
                .when()
                .delete(server + "/versions/1.0.0")
                .then()
                .statusCode(401);
        given()
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(server + "/versions/1.0.0/status")
                .then()
                .statusCode(401);
        given()
                .when()
                .contentType(CT_JSON)
                .body(DEPRECATE)
                .patch(server + "/status")
                .then()
                .statusCode(401);
    }
}
