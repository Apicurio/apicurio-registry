package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * With a data contract compatibility group, a new version is checked for compatibility only against the
 * versions created in the same group.
 */
@QuarkusTest
public class CompatibilityGroupRuleTest extends AbstractResourceTestBase {

    private static final String GROUP = "CompatibilityGroupRuleTest";
    private static final String STRING_ID = schema("string");
    private static final String INT_ID = schema("int");
    private static final String BOOLEAN_ID = schema("boolean");

    private static String schema(String idType) {
        return "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"" + idType
                + "\"}]}";
    }

    private static String incompatible(String artifactId, String readerType, String writerType) {
        return "Incompatible artifact: " + artifactId + " [AVRO], num of incompatible diffs: {1}, list of diff "
                + "types: [reader type: " + readerType + " not compatible with writer type: " + writerType
                + " at /fields/0/type]";
    }

    private void enableBackwardCompatibility(String artifactId) {
        given().when().contentType(CT_JSON).body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/rules").then().statusCode(204);
    }

    private void setCompatibilityGroup(String artifactId, String compatibilityGroup) {
        given().when().contentType(CT_JSON)
                .body(Map.of("contractId", "orders", "compatibilityGroup", compatibilityGroup))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group").then()
                .statusCode(204);
    }

    private ValidatableResponse createVersion(String artifactId, String schema) {
        return given().when().contentType(CT_JSON)
                .body(Map.of("content", Map.of("content", schema, "contentType", ContentTypes.APPLICATION_JSON)))
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then();
    }

    @Test
    public void testWithoutCompatibilityGroupAllVersionsAreCompared() throws Exception {
        String artifactId = "no-compatibility-group";
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, STRING_ID, ContentTypes.APPLICATION_JSON);
        enableBackwardCompatibility(artifactId);

        createVersion(artifactId, INT_ID).statusCode(400)
                .body("title", equalTo(incompatible(artifactId, "INT", "STRING")));
    }

    @Test
    public void testVersionsOfAnotherCompatibilityGroupAreNotCompared() throws Exception {
        String artifactId = "with-compatibility-group";
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, STRING_ID, ContentTypes.APPLICATION_JSON);
        enableBackwardCompatibility(artifactId);
        setCompatibilityGroup(artifactId, "v2");

        // Version 1 predates the group, so the first version of group v2 is not compared with it
        createVersion(artifactId, INT_ID).statusCode(200).body("version", equalTo("2"))
                .body("labels.'contract.orders.compatibility.group'", equalTo("v2"));
        // Version 3 is compared with version 2, the only version of group v2
        createVersion(artifactId, BOOLEAN_ID).statusCode(400)
                .body("title", equalTo(incompatible(artifactId, "BOOLEAN", "INT")));

        // A new group starts a new compatibility line
        setCompatibilityGroup(artifactId, "v3");
        createVersion(artifactId, BOOLEAN_ID).statusCode(200).body("version", equalTo("3"));
    }
}
