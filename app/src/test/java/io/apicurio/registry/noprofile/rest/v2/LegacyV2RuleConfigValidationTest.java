package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * The v2 API rejects an invalid rule configuration with 400, like v3 (#11307).
 */
@QuarkusTest
public class LegacyV2RuleConfigValidationTest extends AbstractResourceTestBase {

    private static final String GROUP = "LegacyV2RuleConfigValidationTest";
    private static final String SCHEMA = "{\"type\":\"object\"}";
    private static final String ARTIFACT_RULES = "/registry/v2/groups/{groupId}/artifacts/{artifactId}/rules";
    private static final String VERSIONS = "/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions";

    @Test
    public void testInvalidArtifactRuleConfigIsRejected() throws Exception {
        String artifactId = "invalid-artifact-rule-config";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, SCHEMA, ContentTypes.APPLICATION_JSON);

        // Create with an invalid config: 400, nothing stored
        rejected(given().when().contentType(CT_JSON).body("{\"type\":\"COMPATIBILITY\",\"config\":\"NOT_A_LEVEL\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId).post(ARTIFACT_RULES).then());
        rejected(given().when().contentType(CT_JSON).body("{\"type\":\"INTEGRITY\",\"config\":\"REFS_EXIST,BOGUS\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId).post(ARTIFACT_RULES).then());
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId).get(ARTIFACT_RULES)
                .then().statusCode(200).body("size()", equalTo(0));

        // A valid config is stored
        given().when().contentType(CT_JSON).body("{\"type\":\"VALIDITY\",\"config\":\"FULL\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId).post(ARTIFACT_RULES).then()
                .statusCode(204);

        // Update with an invalid config: 400, the previous config is kept
        rejected(given().when().contentType(CT_JSON).body("{\"type\":\"VALIDITY\",\"config\":\"BOGUS\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .put(ARTIFACT_RULES + "/VALIDITY").then());
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get(ARTIFACT_RULES + "/VALIDITY").then().statusCode(200).body("config", equalTo("FULL"))
                .body("type", equalTo("VALIDITY"));

        // A valid update still works
        given().when().contentType(CT_JSON).body("{\"type\":\"VALIDITY\",\"config\":\"SYNTAX_ONLY\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .put(ARTIFACT_RULES + "/VALIDITY").then().statusCode(200).body("config", equalTo("SYNTAX_ONLY"));

        // Later writes to the artifact still succeed
        given().when().contentType(CT_JSON).body("{\"type\":\"object\",\"title\":\"v2\"}")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId).post(VERSIONS).then()
                .statusCode(200).body("version", equalTo("2"));
    }

    @Test
    public void testInvalidGlobalRuleConfigIsRejected() throws Exception {
        String artifactId = "invalid-global-rule-config";
        createArtifact(GROUP, artifactId, ArtifactType.JSON, SCHEMA, ContentTypes.APPLICATION_JSON);
        try {
            // Create with an invalid config: 400, nothing stored
            rejected(given().when().contentType(CT_JSON)
                    .body("{\"type\":\"COMPATIBILITY\",\"config\":\"NOT_A_LEVEL\"}")
                    .post("/registry/v2/admin/rules").then());
            given().when().get("/registry/v2/admin/rules/COMPATIBILITY").then().statusCode(404);

            // A valid config is stored
            given().when().contentType(CT_JSON).body("{\"type\":\"VALIDITY\",\"config\":\"FULL\"}")
                    .post("/registry/v2/admin/rules").then().statusCode(204);

            // Update with an invalid config: 400, the previous config is kept
            rejected(given().when().contentType(CT_JSON).body("{\"type\":\"VALIDITY\",\"config\":\"BOGUS\"}")
                    .put("/registry/v2/admin/rules/VALIDITY").then());
            given().when().get("/registry/v2/admin/rules/VALIDITY").then().statusCode(200)
                    .body("config", equalTo("FULL"));

            // Later writes still succeed
            given().when().contentType(CT_JSON).body("{\"type\":\"object\",\"title\":\"v2\"}")
                    .pathParam("groupId", GROUP).pathParam("artifactId", artifactId).post(VERSIONS).then()
                    .statusCode(200).body("version", equalTo("2"));
        } finally {
            given().when().delete("/registry/v2/admin/rules").then().statusCode(204);
        }
    }

    private static void rejected(ValidatableResponse response) {
        response.statusCode(400).body("error_code", equalTo(400))
                .body("name", equalTo("InvalidParameterValueException"));
    }
}
