package io.apicurio.registry.noprofile.otel;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for OpenTelemetry tracing in Apicurio Registry.
 * This test verifies that the application works correctly with OpenTelemetry enabled
 * and that the TracingFilter is applied to REST requests.
 *
 * Note: The actual span data is captured by Quarkus's OpenTelemetry SDK. These tests
 * verify the application functions correctly with OTel enabled and can be monitored
 * via the logs which include trace context (traceId, spanId) in the MDC.
 */
@QuarkusTest
@TestProfile(OpenTelemetryTracingTest.OTelTestProfile.class)
class OpenTelemetryTracingTest extends AbstractResourceTestBase {

    private static final String SIMPLE_AVRO_SCHEMA = """
            {
                "type": "record",
                "name": "TestRecord",
                "fields": [
                    {"name": "field1", "type": "string"}
                ]
            }
            """;

    /**
     * Test profile that enables OpenTelemetry
     */
    public static class OTelTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.enabled", "true",
                    "quarkus.otel.traces.enabled", "true",
                    "quarkus.otel.metrics.enabled", "false",
                    "quarkus.otel.logs.enabled", "false",
                    "quarkus.otel.traces.sampler", "always_on",
                    // Use NONE exporter for tests - spans are still created but not exported
                    "quarkus.otel.traces.exporter", "none"
            );
        }
    }

    @Test
    void testSystemInfoEndpointWithOTelEnabled() {
        // Verify the system info endpoint works with OTel enabled
        given()
                .when()
                .get("/registry/v3/system/info")
                .then()
                .statusCode(200);
    }

    @Test
    void testGroupsEndpointWithOTelEnabled() {
        // Verify the groups endpoint works with OTel enabled
        given()
                .when()
                .get("/registry/v3/groups")
                .then()
                .statusCode(200);
    }

    @Test
    void testSearchEndpointWithOTelEnabled() {
        // Verify search endpoint works with OTel enabled
        given()
                .queryParam("name", "test-search")
                .when()
                .get("/registry/v3/search/artifacts")
                .then()
                .statusCode(200);
    }

    @Test
    void testTracingFilterWithHeaders() {
        // Make a REST call with headers that should be captured by TracingFilter
        // The filter adds apicurio.groupId and other attributes to the span
        given()
                .header("X-Registry-GroupId", "header-test-group")
                .header("X-Registry-ArtifactId", "header-test-artifact")
                .when()
                .get("/registry/v3/groups")
                .then()
                .statusCode(200);
    }

    @Test
    void testArtifactCreationWithOTelEnabled() {
        String groupId = "test-otel-group";
        String artifactId = TestUtils.generateArtifactId();

        // Create an artifact - this exercises the storage tracing interceptor
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);

        CreateVersion createVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(SIMPLE_AVRO_SCHEMA);
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        createArtifact.setFirstVersion(createVersion);

        CreateArtifactResponse response = clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        assertNotNull(response);
        assertNotNull(response.getVersion());
        assertNotNull(response.getVersion().getVersion());
    }

    @Test
    void testArtifactRetrievalWithOTelEnabled() {
        String groupId = "test-otel-retrieval-group";
        String artifactId = TestUtils.generateArtifactId();

        // First create an artifact
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);

        CreateVersion createVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent("{\"type\":\"object\"}");
        content.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(content);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Then retrieve it - this exercises the storage tracing interceptor for read operations
        var artifact = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
        assertNotNull(artifact);
        assertNotNull(artifact.getArtifactId());
    }
}
