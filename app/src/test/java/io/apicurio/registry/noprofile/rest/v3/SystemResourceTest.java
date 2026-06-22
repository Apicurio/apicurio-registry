package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class SystemResourceTest extends AbstractResourceTestBase {

    @Test
    public void testSystemInformation() {
        given().when().contentType(CT_JSON).get("/registry/v3/system/info").then().statusCode(200)
                .body("name", equalTo("Apicurio Registry (SQL)"))
                .body("description",
                        equalTo("High performance, runtime registry for schemas and API designs."))
                .body("version", notNullValue()).body("builtOn", notNullValue());
    }

    @Test
    public void testCanonicalizeJsonContent() {
        String content = "{\"z\": 1, \"a\": 2, \"m\": 3}";
        String expected = "{\"a\":2,\"m\":3,\"z\":1}";

        String actual = given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.JSON)
                .body(content)
                .post("/registry/v3/system/canonicalize")
                .then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testCanonicalizeOpenApiContent() {
        String content = "{\n  \"openapi\": \"3.0.2\",\n  \"info\": {\n    \"title\": \"Empty API\","
                + "\n    \"version\": \"1.0.0\"\n  },\n  \"paths\": {\n    \"/\": {}\n  },"
                + "\n  \"components\": {}\n}";

        given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.OPENAPI)
                .body(content)
                .post("/registry/v3/system/canonicalize")
                .then().statusCode(200)
                .body("components", notNullValue())
                .body("openapi", equalTo("3.0.2"));
    }

    @Test
    public void testCanonicalizeAvroContent() {
        String content = "{\n  \"type\": \"record\",\n  \"namespace\": \"com.example\","
                + "\n  \"name\": \"FullName\",\n  \"fields\": [\n"
                + "    { \"name\": \"first\", \"type\": \"string\" }\n  ]\n}";

        given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.AVRO)
                .body(content)
                .post("/registry/v3/system/canonicalize")
                .then().statusCode(200)
                .body("type", equalTo("record"))
                .body("name", equalTo("FullName"));
    }

    @Test
    public void testCanonicalizeEmptyBody() {
        given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.JSON)
                .body("")
                .post("/registry/v3/system/canonicalize")
                .then().statusCode(400);
    }

    @Test
    public void testCanonicalizeStoredArtifactContent() throws Exception {
        String groupId = "testCanonicalizeStoredContent";
        String artifactId = "testCanonicalizeStoredContent/JsonSchema";
        String content = "{\"z\": 1, \"a\": 2, \"m\": 3}";
        String expected = "{\"a\":2,\"m\":3,\"z\":1}";

        createArtifact(groupId, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        String actual = given().when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .queryParam("canonical", true)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testGetStoredContentWithoutCanonical() throws Exception {
        String groupId = "testGetStoredContentWithoutCanonical";
        String artifactId = "testGetStoredContentWithoutCanonical/JsonSchema";
        String content = "{\"z\": 1, \"a\": 2, \"m\": 3}";

        createArtifact(groupId, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        String actual = given().when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertEquals(content, actual);
    }

}
