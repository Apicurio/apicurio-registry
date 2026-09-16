package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.types.VersionState;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Test
    void bulkStatusRollsBackThroughTheJournalAndPersistsMessages() {
        String namespace = uniqueNamespace();
        String name = namespace + "/bulk";
        for (String version : List.of("1.0.0", "2.0.0")) {
            given().contentType("application/json").body(Map.of("name", name, "version", version,
                    "description", "Bulk test")).post(BASE + "/publish").then().statusCode(200);
        }
        var before = storage.getArtifactVersionMetaData(namespace, "bulk", "1.0.0");
        var second = storage.getArtifactVersionMetaData(namespace, "bulk", "2.0.0");
        String constraint = "mcp_atomic_" + UUID.randomUUID().toString().replace("-", "");
        handles.withHandleNoException(handle -> {
            handle.createUpdate("ALTER TABLE versions ADD CONSTRAINT " + constraint
                    + " CHECK (globalId <> " + second.getGlobalId() + " OR state <> 'DEPRECATED')").execute();
            return null;
        });
        try {
            assertThrows(RuntimeSqlException.class, () -> storage.updateArtifactVersionStates(namespace,
                    "bulk", List.of("1.0.0", "2.0.0"), VersionState.DEPRECATED,
                    "apicurio.mcp-registry.status.", Map.of("apicurio.mcp-registry.status.message", "Rollback")));
            assertEquals(before, storage.getArtifactVersionMetaData(namespace, "bulk", "1.0.0"));
            assertEquals(second, storage.getArtifactVersionMetaData(namespace, "bulk", "2.0.0"));
        } finally {
            handles.withHandleNoException(handle -> {
                handle.createUpdate("ALTER TABLE versions DROP CONSTRAINT " + constraint).execute();
                return null;
            });
        }
        given().contentType("application/json").body(Map.of("status", "deprecated", "statusMessage", "Upgrade"))
                .patch(BASE + "/servers/" + name + "/status").then().statusCode(200)
                .body("updatedCount", equalTo(2));
        for (String version : List.of("1.0.0", "2.0.0")) {
            given().get(BASE + "/servers/" + name + "/versions/" + version).then().statusCode(200)
                    .body("_meta.'io.modelcontextprotocol.registry/official'.statusMessage", equalTo("Upgrade"));
        }
    }

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
                .body("server.name", equalTo(name))
                .body("server.version", equalTo("1.0.0"));

        given()
                .when()
                .contentType("application/json")
                .get(BASE + "/servers/" + namespace + "/weather")
                .then()
                .statusCode(200)
                .body("server.description", equalTo("kafkasql check"));

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
                .statusCode(200);

        given()
                .when()
                .contentType("application/json")
                .get(BASE + "/servers/" + namespace + "/weather/versions/1.0.0")
                .then()
                .statusCode(404);
    }
}
