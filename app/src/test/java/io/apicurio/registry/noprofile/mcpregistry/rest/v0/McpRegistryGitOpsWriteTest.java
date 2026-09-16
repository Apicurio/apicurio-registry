package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Confirms that a write against a read-only storage backend (gitops here; kubernetesops shares the same
 * {@code AbstractReadOnlyRegistryStorage} base and would behave identically) is rejected with a 501 by
 * {@code requireWritable()}, rather than falling through to an unmapped 500 via
 * {@code UnreachableCodeException}.
 */
@QuarkusTest
@TestProfile(McpRegistryGitOpsWriteProfile.class)
class McpRegistryGitOpsWriteTest {

    private static final String BASE = "/apis/mcp-registry/v0.1";

    @Test
    void testPublishOnReadOnlyStorageReturns501NotAnUnmappedException() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"name\":\"io.github.gitops/weather\",\"version\":\"1.0.0\",\"description\":\"Test\"}")
                .post(BASE + "/publish")
                .then()
                .log().ifValidationFails()
                .statusCode(501)
                .body("error", equalTo("Modifying MCP servers is not supported by this registry: its storage is read-only"))
                .body("name", nullValue());
    }

    @Test
    void testDeleteOnReadOnlyStorageReturns501() {
        given()
                .when()
                .delete(BASE + "/servers/io.github.gitops/weather/versions/1.0.0")
                .then()
                .statusCode(501);
    }

    @Test
    void testStatusUpdateOnReadOnlyStorageReturns501() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/io.github.gitops/weather/versions/1.0.0/status")
                .then()
                .statusCode(501);
    }

    @Test
    void testStatusUpdateAllVersionsOnReadOnlyStorageReturns501() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/io.github.gitops/weather/status")
                .then()
                .statusCode(501);
    }
}
