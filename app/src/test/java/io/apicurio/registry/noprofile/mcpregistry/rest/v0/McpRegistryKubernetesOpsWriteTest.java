package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;

/**
 * Confirms that a write against kubernetesops - a read-only storage backend, like gitops - is rejected
 * with a 403 by {@code requireWritable()} rather than falling through to an unmapped 500. Mirrors
 * McpRegistryGitOpsWriteTest; the two backends share the exact same read-only base class, so this closes
 * the "should behave identically, but that's inference, not a run" gap noted throughout the session.
 */
@QuarkusTest
@TestProfile(McpRegistryKubernetesOpsWriteProfile.class)
class McpRegistryKubernetesOpsWriteTest {

    private static final String BASE = "/apis/mcp-registry/v0.1";

    @Test
    void testPublishOnReadOnlyStorageReturns403NotAnUnmappedException() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"name\":\"io.github.k8sops/weather\",\"version\":\"1.0.0\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(403)
                .body("name", not(equalTo("UnreachableCodeException")));
    }

    @Test
    void testDeleteOnReadOnlyStorageReturns403() {
        given()
                .when()
                .delete(BASE + "/servers/io.github.k8sops/weather/versions/1.0.0")
                .then()
                .statusCode(403);
    }

    @Test
    void testStatusUpdateOnReadOnlyStorageReturns403() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/io.github.k8sops/weather/versions/1.0.0/status")
                .then()
                .statusCode(403);
    }

    @Test
    void testStatusUpdateAllVersionsOnReadOnlyStorageReturns403() {
        given()
                .when()
                .contentType("application/json")
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/io.github.k8sops/weather/status")
                .then()
                .statusCode(403);
    }
}
