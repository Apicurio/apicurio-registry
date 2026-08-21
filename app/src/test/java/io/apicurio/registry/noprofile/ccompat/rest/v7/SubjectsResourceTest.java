package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class SubjectsResourceTest extends AbstractResourceTestBase {
    @Test
    public void testListSubjectsEndpoint() {
        given().when().contentType(CT_JSON).get("/ccompat/v7/subjects").then().statusCode(200)
                .body(anything());
    }

    @Test
    public void testGetSubjectMetadata() throws Exception {
        final String artifactId = TestUtils.generateArtifactId();
        final String content = "{\"type\":\"record\",\"name\":\"TestRecord\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\"}]}";

        createArtifact("default", artifactId, ArtifactType.AVRO, content, ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON).get("/ccompat/v7/subjects/" + artifactId + "/metadata")
                .then().statusCode(200)
                .body("subject", equalTo(artifactId))
                .body("version", equalTo(1))
                .body("id", notNullValue())
                .body("schema", notNullValue());
    }
}
