package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(DataContractsEnabledProfile.class)
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
}
