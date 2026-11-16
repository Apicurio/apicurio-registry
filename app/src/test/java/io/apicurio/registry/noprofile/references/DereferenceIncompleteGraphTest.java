package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEREFERENCE on a reference graph with a missing target must fail with a 4xx naming the missing
 * references, never return partial content or a 500 (#11059).
 */
@QuarkusTest
public class DereferenceIncompleteGraphTest extends AbstractResourceTestBase {

    private static final String AVRO_ORDER = """
            {"type": "record", "name": "Order", "namespace": "com.example",
             "fields": [{"name": "total", "type": "com.example.common.Money"}]}
            """;

    private static final String JSON_ORDER = """
            {"$schema": "http://json-schema.org/draft-07/schema#", "type": "object",
             "properties": {"total": {"$ref": "money.json"}}}
            """;

    @Test
    public void dereferenceAvroWithMissingReferenceFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "order", ArtifactType.AVRO, AVRO_ORDER, ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "money", "com.example.common.Money")));

        Response response = dereference(groupId, "order");

        assertEquals(422, response.getStatusCode(), response.asString());
        assertTrue(response.asString().contains(
                "these references point to versions that do not exist: 'com.example.common.Money' ("
                        + groupId + "/money@1)"), response.asString());
    }

    @Test
    public void dereferenceJsonSchemaWithMissingReferenceFails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifactWithReferences(groupId, "order", ArtifactType.JSON, JSON_ORDER, ContentTypes.APPLICATION_JSON,
                List.of(ref(groupId, "money", "money.json")));

        Response response = dereference(groupId, "order");

        assertEquals(422, response.getStatusCode(), response.asString());
        assertTrue(response.asString().contains("'money.json' (" + groupId + "/money@1)"), response.asString());
    }

    private Response dereference(String groupId, String artifactId) {
        return given().when().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1/content");
    }

    private static ArtifactReference ref(String groupId, String artifactId, String name) {
        return ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version("1").name(name).build();
    }
}
