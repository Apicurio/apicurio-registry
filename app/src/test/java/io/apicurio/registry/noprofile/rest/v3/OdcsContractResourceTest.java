package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class OdcsContractResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "OdcsContractResourceTest";

    private static final String AVRO_SCHEMA = """
            {
              "type": "record",
              "name": "OrderEvent",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string", "tags": ["PII", "EMAIL"]},
                {"name": "totalAmount", "type": "double"}
              ]
            }
            """;

    private String odcsContract(String schemaGroupId, String schemaArtifactId) {
        return "apiVersion: v3.1.0\n"
                + "kind: DataContract\n"
                + "id: test-contract-" + UUID.randomUUID() + "\n"
                + "info:\n"
                + "  title: Test Contract\n"
                + "  version: 1.0.0\n"
                + "  status: active\n"
                + "  dataClassification: confidential\n"
                + "team:\n"
                + "  name: test-team\n"
                + "  domain: testing\n"
                + "  contact: test@example.com\n"
                + "schemas:\n"
                + "  - name: OrderEvent\n"
                + "    type: avro\n"
                + "    location: " + schemaGroupId + "/" + schemaArtifactId + ":latest\n"
                + "    fields:\n"
                + "      customerEmail:\n"
                + "        pii: true\n"
                + "        tags:\n"
                + "          - PII\n"
                + "          - EMAIL\n"
                + "quality:\n"
                + "  accuracy:\n"
                + "    - name: positive-amount\n"
                + "      expression: totalAmount > 0\n"
                + "      threshold: 1.0\n"
                + "  freshness:\n"
                + "    maxStaleness: PT5M\n"
                + "serviceLevel:\n"
                + "  availability: 0.999\n";
    }

    @Test
    public void testSubmitAndGetContract() throws Exception {
        String artifactId = "testSubmitAndGet-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        String contract = odcsContract(GROUP, artifactId);

        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contract.getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200)
                .body("contractId", notNullValue())
                .body("projection.rulesApplied", equalTo(1))
                .body("projection.labelsApplied", greaterThanOrEqualTo(1));
    }

    @Test
    public void testListContracts() throws Exception {
        String artifactId = "testListContracts-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        String contract = odcsContract(GROUP, artifactId);

        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contract.getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .get("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    public void testSubmitInvalidYaml() {
        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body("not valid yaml {{{".getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(400);
    }

    @Test
    public void testExportContractAsOdcs() throws Exception {
        String artifactId = "testExport-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        String contract = odcsContract(GROUP, artifactId);

        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contract.getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/export")
                .then()
                .statusCode(200);
    }

    @Test
    public void testGetContractQuality() throws Exception {
        String artifactId = "testQuality-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        String contract = odcsContract(GROUP, artifactId);
        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contract.getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .queryParam("contractId", "test-contract-quality")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/quality")
                .then()
                .statusCode(200)
                .body("overall", notNullValue());
    }

    @Test
    public void testPromoteContract() throws Exception {
        String artifactId = "testPromote-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        String contract = odcsContract(GROUP, artifactId);
        given()
                .when()
                .header("Content-Type", "application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contract.getBytes())
                .post("/registry/v3/groups/{groupId}/contracts")
                .then()
                .statusCode(200);

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("{\"contractId\":\"test-promote\",\"targetStage\":\"DEV\"}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/promote")
                .then()
                .statusCode(200)
                .body("stage", equalTo("DEV"));
    }

    @Test
    public void testPromoteInvalidStage() throws Exception {
        String artifactId = "testPromoteInvalid-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA,
                ContentTypes.APPLICATION_JSON);

        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("{\"contractId\":\"test\",\"targetStage\":\"INVALID\"}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/promote")
                .then()
                .statusCode(400);
    }

    // -- #7977 ODCS Contract Update and Delete --

    @Test
    public void testUpdateContract_ReProjects() throws Exception {
        String artifactId = "testUpdateContract-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"T\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        String contractYaml = "apiVersion: v3.1.0\nkind: DataContract\nid: update-test\n"
                + "info:\n  title: Test\n  version: 1.0.0\n  status: active\n"
                + "team:\n  name: team-1\n  domain: test\n  contact: t@t.com\n"
                + "schemas:\n  - name: T\n    type: avro\n    location: " + GROUP + "/" + artifactId + ":latest\n";

        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contractYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .post("/registry/v3/groups/{groupId}/contracts")
                .then().statusCode(200);

        String updatedYaml = contractYaml.replace("team-1", "team-2")
                .replace("1.0.0", "2.0.0");

        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .pathParam("contractId", "update-test")
                .body(updatedYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .put("/registry/v3/groups/{groupId}/contracts/{contractId}")
                .then().statusCode(200);

        given().when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200)
                .body("ownerTeam", equalTo("team-2"));
    }

    @Test
    public void testDeleteContract_RemovesArtifact() throws Exception {
        String artifactId = "testDeleteContract-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"T\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        String contractYaml = "apiVersion: v3.1.0\nkind: DataContract\nid: delete-test\n"
                + "info:\n  title: Test\n  version: 1.0.0\n  status: active\n"
                + "schemas:\n  - name: T\n    type: avro\n    location: " + GROUP + "/" + artifactId + ":latest\n";

        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contractYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .post("/registry/v3/groups/{groupId}/contracts")
                .then().statusCode(200);

        given().when()
                .pathParam("groupId", GROUP)
                .pathParam("contractId", "delete-test")
                .delete("/registry/v3/groups/{groupId}/contracts/{contractId}")
                .then().statusCode(204);

        given().when()
                .pathParam("groupId", GROUP)
                .pathParam("contractId", "delete-test")
                .get("/registry/v3/groups/{groupId}/contracts/{contractId}")
                .then().statusCode(404);
    }

    // -- #7976 ODCS Projection Preservation --

    @Test
    public void testOdcsProjection_ManualRulesPreserved() throws Exception {
        String artifactId = "testManualPreserved-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"T\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        String contractId = "preserve-" + UUID.randomUUID();
        String contractYaml = "apiVersion: v3.1.0\nkind: DataContract\nid: " + contractId + "\n"
                + "info:\n  title: Test\n  version: 1.0.0\n  status: active\n"
                + "schemas:\n  - name: T\n    type: avro\n    location: " + GROUP + "/" + artifactId + ":latest\n"
                + "quality:\n  accuracy:\n    - name: odcs-rule\n      expression: x > 0\n      threshold: 1.0\n";

        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contractYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .post("/registry/v3/groups/{groupId}/contracts")
                .then().statusCode(200);

        // Add a manual rule alongside the ODCS-projected one
        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"domainRules\":[" +
                        "{\"name\":\"odcs:" + contractId + ":odcs-rule\",\"kind\":\"CONDITION\"," +
                        "\"type\":\"CEL\",\"mode\":\"WRITE\",\"expr\":\"x > 0\"," +
                        "\"onFailure\":\"ERROR\",\"disabled\":false}," +
                        "{\"name\":\"manual-rule\",\"kind\":\"CONDITION\"," +
                        "\"type\":\"CEL\",\"mode\":\"WRITE\",\"expr\":\"x < 999\"," +
                        "\"onFailure\":\"ERROR\",\"disabled\":false}" +
                        "],\"migrationRules\":[]}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        // Re-submit the contract with a new version to trigger re-projection
        String updatedYaml = contractYaml.replace("1.0.0", "2.0.0");
        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .pathParam("contractId", contractId)
                .body(updatedYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .put("/registry/v3/groups/{groupId}/contracts/{contractId}")
                .then().statusCode(200);

        // Verify manual rule survived the re-projection
        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200)
                .body("domainRules.findAll { it.name == 'manual-rule' }", hasSize(1));
    }
}
