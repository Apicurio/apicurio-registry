package io.apicurio.registry;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;

/**
 * Created by aohana
 */
@QuarkusTest
public class TestResourceTest extends AbstractResourceTestBase {

    @Test
    public void testTestArtifactNoViolations() throws Exception {
        String artifactContent = resourceToString("rules/validity/jsonschema-valid.json");
        String artifactContentValid = resourceToString("rules/validity/jsonschema-valid-compatible.json");
        String artifactId = "testCreateArtifact/TestNoViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
                .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("artifactId", artifactId)
                    .get("/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Test a new version with valid change
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON.name())
                .pathParam("artifactId", artifactId)
                .body(artifactContentValid)
                .put("/artifacts/{artifactId}/test")
                .then()
                .statusCode(204);
    }

    @Test
    public void testTestArtifactCompatibilityViolation() throws Exception {
        String artifactContent = resourceToString("rules/validity/jsonschema-valid.json");
        String artifactContentIncompatible = resourceToString("rules/validity/jsonschema-valid-incompatible.json");
        String artifactId = "testCreateArtifact/TestCompatibilityViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
                .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("artifactId", artifactId)
                    .get("/artifacts/{artifactId}/rules/COMPATIBILITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Test a new version with valid change
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON.name())
                .pathParam("artifactId", artifactId)
                .body(artifactContentIncompatible)
                .put("/artifacts/{artifactId}/test")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Incompatible artifact: testCreateArtifact/TestCompatibilityViolation [JSON], num of incompatible diffs: {1}"))
                .body("causes[0].description", equalTo(DiffType.SUBSCHEMA_TYPE_CHANGED.getDescription()))
                .body("causes[0].context", equalTo("/properties/age"));
    }

    @Test
    public void testTestArtifactValidityViolation() throws Exception {
        String artifactContent = resourceToString("rules/validity/jsonschema-valid.json");
        String artifactContentInvalidSyntax = resourceToString("rules/validity/jsonschema-invalid.json");
        String artifactId = "testCreateArtifact/TestValidityViolation";
        createArtifact(artifactId, ArtifactType.JSON, artifactContent);

        // Add a rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
                .when()
                .contentType(CT_JSON)
                .body(rule)
                .pathParam("artifactId", artifactId)
                .post("/artifacts/{artifactId}/rules")
                .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added
        TestUtils.retry(() -> {
            given()
                    .when()
                    .pathParam("artifactId", artifactId)
                    .get("/artifacts/{artifactId}/rules/VALIDITY")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Create a new version of the artifact with invalid syntax
        given()
                .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactType", ArtifactType.JSON.name())
                .pathParam("artifactId", artifactId)
                .body(artifactContentInvalidSyntax)
                .put("/artifacts/{artifactId}/test")
                .then()
                .statusCode(409)
                .body("error_code", equalTo(409))
                .body("message", equalTo("Syntax violation for JSON Schema artifact."))
                .body("causes[0].description", equalTo("Syntax violation for JSON Schema artifact."))
                .body("causes[0].context", equalTo("FULL"));
    }
}
