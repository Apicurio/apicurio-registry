package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

@QuarkusTest
public class CompatibilityResourceTest extends AbstractResourceTestBase {
    @Test
    public void testUnknownLatestSchemaForSubject() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", "{}");
        requestBody.put("schemaType", "AVRO");

        String subject = "unknown";
        String version = "1";
        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions/{version}", subject, version)
                .then()
                    .statusCode(404)
                    .body(anything());

        subject = "unknown";
        version = "latest";
        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions/{version}", subject, version)
                .then()
                    .statusCode(200)
                    .body(anything());
    }
}
