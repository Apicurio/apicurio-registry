package io.apicurio.registry.noprofile.ccompat.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class SubjectVersionStringTest extends AbstractResourceTestBase {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Test
    public void testSubjectVersionString() throws Exception {
        var SUBJECT = "test-cecc8b42-5ef5-4eff-82a5-96c1889839f9";
        var schema1 = "{\"type\" : \"string\"}";
        var schema2 = "{\"type\" : \"int\"}";

        var objectMapper = new ObjectMapper();
        var schemaContent1 = new RegisterSchemaRequest();
        schemaContent1.setSchema(schema1);
        var schemaContent2 = new RegisterSchemaRequest();
        schemaContent2.setSchema(schema2);

        // Create first
        var cid1 = given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent1))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200).extract()
                .as(Schema.class);

        Assertions.assertNotNull(cid1);

        var versions1 = given().log().all().when().get("/ccompat/v7/subjects/{subject}/versions", SUBJECT)
                .then().statusCode(200).extract().as(new TypeRef<List<Integer>>() {
                });

        Assertions.assertEquals(1, versions1.size());
        var version1 = versions1.get(0);

        // Create second
        var cid2 = given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent2))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200).extract()
                .as(Schema.class);

        Assertions.assertNotNull(cid2);

        var versions2 = given().log().all().when().get("/ccompat/v7/subjects/{subject}/versions", SUBJECT)
                .then().statusCode(200).extract().as(new TypeRef<List<Integer>>() {
                });

        Assertions.assertEquals(2, versions2.size());
        versions2.removeAll(versions1);
        var version2 = versions2.get(0);

        // Get latest and compare
        Assertions.assertEquals(schema1, getSubjectVersion(SUBJECT, String.valueOf(version1)).getSchema());
        Assertions.assertEquals(schema2, getSubjectVersion(SUBJECT, String.valueOf(version2)).getSchema());
        Assertions.assertEquals(schema2, getSubjectVersion(SUBJECT, "latest").getSchema());
        Assertions.assertEquals(schema2, getSubjectVersion(SUBJECT, "-1").getSchema());
        getSubjectVersionFail(SUBJECT, "-2", 404);
        getSubjectVersionFail(SUBJECT, "foo", 404);
    }

    private Schema getSubjectVersion(String subject, String version) {
        var response = given().log().all().when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", subject, version).then().extract()
                .asString();

        log.info("Response to get version {} of subject {} is: {}", version, subject, response);

        return given().log().all().when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", subject, version).then()
                .statusCode(200).extract().as(Schema.class);
    }

    private void getSubjectVersionFail(String subject, String version, int expectedStatusCode) {
        given().when().get("/ccompat/v7/subjects/{subject}/versions/{version}", subject, version).then()
                .statusCode(expectedStatusCode);
    }
}
