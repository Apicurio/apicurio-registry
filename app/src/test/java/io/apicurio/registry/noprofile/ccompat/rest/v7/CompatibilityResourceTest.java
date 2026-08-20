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

    @Test
    public void testFallbackCompatibilityComparesAllEnabledVersions() throws Exception {
        String subject = io.apicurio.registry.utils.tests.TestUtils.generateSubject();
        String v1Schema = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";
        String v2Schema = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"int\"}]}";
        // vCheck adds optional field 'age' as string. Compatible with v1 (age defaults to ""), but incompatible with v2 ('age' type changed from int to string).
        String vCheckSchema = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":\"string\",\"default\":\"\"}]}";

        ObjectMapper mapper = new ObjectMapper();

        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest r1 = new io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest();
        r1.setSchema(v1Schema);
        r1.setSchemaType("AVRO");

        given().contentType(CT_JSON).body(mapper.writeValueAsString(r1))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200);

        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest r2 = new io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest();
        r2.setSchema(v2Schema);
        r2.setSchemaType("AVRO");

        given().contentType(CT_JSON).body(mapper.writeValueAsString(r2))
                .post("/ccompat/v7/subjects/{subject}/versions", subject)
                .then().statusCode(200);

        io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest checkRequest = new io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest();
        checkRequest.setSchema(vCheckSchema);
        checkRequest.setSchemaType("AVRO");

        // When checking compatibility against version 1, fallback must evaluate against ALL enabled versions (including v2),
        // returning is_compatible=false (because age type changed in v2). If narrowed to v1, it would wrongly return true.
        given().contentType(CT_JSON).body(mapper.writeValueAsString(checkRequest))
                .post("/ccompat/v7/compatibility/subjects/{subject}/versions/1", subject)
                .then()
                .statusCode(200)
                .body("is_compatible", org.hamcrest.CoreMatchers.is(false));
    }
}
