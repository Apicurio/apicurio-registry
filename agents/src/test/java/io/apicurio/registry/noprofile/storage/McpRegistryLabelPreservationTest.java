package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.noprofile.storage.FeatureLabelPreservationTest.labelsAt;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FeatureLabelPreservationTest} for the label namespace the MCP Registry declares: its labels
 * survive a version metadata update that omits them (issue #10030).
 */
@QuarkusTest
public class McpRegistryLabelPreservationTest extends AbstractResourceTestBase {

    private static final String MCP_STATUS_MESSAGE = "apicurio.mcp-registry.status.message";

    private String group;

    private String newArtifact() throws Exception {
        group = "labels-" + UUID.randomUUID();
        String artifactId = "a-" + UUID.randomUUID();
        createArtifact(group, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);
        return artifactId;
    }

    @Test
    void versionUpdateKeepsAnOmittedFeatureLabel() throws Exception {
        String artifactId = newArtifact();
        String path = "/registry/v3/groups/{g}/artifacts/{a}/versions/1";
        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(MCP_STATUS_MESSAGE, "moved", "x", "1")))
                .put(path, group, artifactId).then().statusCode(204);

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of("x", "2")))
                .put(path, group, artifactId).then().statusCode(204);

        assertEquals(Map.of(MCP_STATUS_MESSAGE, "moved", "x", "2"),
                labelsAt("/registry/v3/groups/" + group + "/artifacts/" + artifactId + "/versions/1"));

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(MCP_STATUS_MESSAGE, "back")))
                .put(path, group, artifactId).then().statusCode(204);

        assertEquals(Map.of(MCP_STATUS_MESSAGE, "back"),
                labelsAt("/registry/v3/groups/" + group + "/artifacts/" + artifactId + "/versions/1"));
    }
}
