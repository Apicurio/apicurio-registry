package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * The agent registry part of {@link GrantsAuthorizationTest}: same profile and access rules, applied to
 * the MCP Registry API and to the artifacts written by the agent write hooks.
 */
@QuarkusTest
@TestProfile(GrantsAuthorizationTest.GrantsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsAgentsAuthorizationTest extends AbstractResourceTestBase {

    private static final String TEAM_A = "grants-team-a";

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx)
                .basicAuth("alice", "alice"));
    }

    /** Test users use their username as password. */
    private static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
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

    // ==================== Artifacts written by write hooks ====================

    @Test
    public void extractedSchemasAreSubjectToOwnerOnlyAuthorization() {
        String model = "owner-model-" + UUID.randomUUID().toString().substring(0, 8);
        String schema = model + "-input-schema";
        String path = "/registry/v3/groups/" + TEAM_A + "/artifacts";
        // alice owns the schema artifact the model's embedded input schema would be written to
        as("alice").contentType(CT_JSON).body(Map.of("artifactId", schema, "artifactType", "JSON", "firstVersion",
                Map.of("content", Map.of("content", "{\"type\":\"string\",\"description\":\"ADMIN\"}",
                        "contentType", CT_JSON))))
                .post(path).then().statusCode(200);
        as("bob1").contentType(CT_JSON).body(Map.of("artifactId", model, "artifactType", "MODEL_SCHEMA",
                "firstVersion", Map.of("content", Map.of("content", "{\"name\":\"M\"}", "contentType", CT_JSON))))
                .post(path).then().statusCode(200);

        String withInput = "{\"name\":\"M\",\"input\":{\"type\":\"object\","
                + "\"properties\":{\"p\":{\"type\":\"integer\",\"description\":\"OWNER-BYPASS\"}}}}";
        as("bob1").contentType(CT_JSON).body(Map.of("content", Map.of("content", withInput, "contentType", CT_JSON)))
                .post(path + "/" + schema + "/versions").then().statusCode(403);
        as("bob1").contentType(CT_JSON).body(Map.of("content", Map.of("content", withInput, "contentType", CT_JSON)))
                .post(path + "/" + model + "/versions").then().statusCode(403);

        as("alice").get(path + "/" + schema + "/versions").then().statusCode(200).body("count", equalTo(1));
        as("alice").get(path + "/" + schema + "/versions/branch=latest/content").then().statusCode(200)
                .body("description", equalTo("ADMIN"));
        as("alice").get(path + "/" + model + "/versions").then().statusCode(200).body("count", equalTo(1));
    }
}
