package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Confirms the MCP Registry API's full write path - publish, read, status update, delete - actually works
 * end to end through the kafkasql storage variant's journal, not just against SQL. Validation and cursor
 * logic are storage-independent and already covered by McpServerContentValidatorTest and
 * McpRegistryCursorTest; this test is specifically about the write path surviving the Kafka round-trip.
 */
@QuarkusTest
@TestProfile(McpRegistryKafkaSqlProfile.class)
class McpRegistryKafkaSqlLifecycleTest {

    private static final String BASE = "/apis/mcp-registry/v0.1";

    private String uniqueNamespace() {
        return "io.github.kafkasql" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void testPublishReadStatusAndDeleteRoundTripThroughKafka() {
        String namespace = uniqueNamespace();
        String name = namespace + "/weather";

        given()
                .when()
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\",\"version\":\"1.0.0\",\"description\":\"kafkasql check\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("version", equalTo("1.0.0"));

        given()
                .when()
                .contentType("application/json")
                .get(BASE + "/servers/" + namespace + "/weather")
                .then()
                .statusCode(200)
                .body("description", equalTo("kafkasql check"));

        given()
                .when()
                .contentType("application/json")
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/" + namespace + "/weather/versions/1.0.0/status")
                .then()
                .statusCode(200)
                .body("_meta.'io.modelcontextprotocol.registry/official'.status", equalTo("deprecated"));

        given()
                .when()
                .delete(BASE + "/servers/" + namespace + "/weather/versions/1.0.0")
                .then()
                .statusCode(204);

        given()
                .when()
                .contentType("application/json")
                .get(BASE + "/servers/" + namespace + "/weather/versions/1.0.0")
                .then()
                .statusCode(404);
    }
}
