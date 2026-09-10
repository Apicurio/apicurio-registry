package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ContentResourceTest extends AbstractResourceTestBase {

    /**
     * Tests detecting references in an OpenAPI document that uses external $ref values.
     */
    @Test
    public void testDetectOpenApiReferences() {
        String content = resourceToString("openapi-with-external-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("./referenced-types.json#/components/schemas/Widget", refs.get(0).getName());
    }

    /**
     * Tests detecting references in a JSON Schema that uses $ref values pointing to external files.
     */
    @Test
    public void testDetectJsonSchemaReferences() {
        String content = resourceToString("jsonschema-with-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());

        List<String> names = refs.stream().map(ArtifactReference::getName).toList();
        Assertions.assertTrue(names.contains("customer.json#/$defs/Customer"));
        Assertions.assertTrue(names.contains("address.json"));
    }

    /**
     * Tests detecting references in a Protobuf schema that uses import statements.
     */
    @Test
    public void testDetectProtobufReferences() {
        String content = resourceToString("protobuf-with-imports.proto");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_PROTOBUF);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.PROTOBUF;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());

        List<String> names = refs.stream().map(ArtifactReference::getName).toList();
        Assertions.assertTrue(names.contains("common/address.proto"));
        Assertions.assertTrue(names.contains("common/phone_number.proto"));
    }

    /**
     * Tests detecting references in an Avro schema that uses external type references.
     */
    @Test
    public void testDetectAvroReferences() {
        String content = resourceToString(
                "/io/apicurio/registry/noprofile/rest/avro-with-reference.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("com.example.common.Address", refs.get(0).getName());
    }

    /**
     * Tests that content with no external references returns an empty list.
     */
    @Test
    public void testDetectReferencesNone() {
        String content = resourceToString("jsonschema-valid.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertTrue(refs.isEmpty());
    }

    /**
     * Tests that artifact type is auto-detected from the content when not provided.
     */
    @Test
    public void testDetectReferencesAutoDetectType() {
        String content = resourceToString("openapi-with-external-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        // No artifactType — should auto-detect as OpenAPI
        List<ArtifactReference> refs = clientV3.content().references().post(body);

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("./referenced-types.json#/components/schemas/Widget", refs.get(0).getName());
    }

    /**
     * Tests that only the name field is populated in detected references
     * (groupId, artifactId, and version should be null).
     */
    @Test
    public void testDetectReferencesOnlyNamePopulated() {
        String content = resourceToString("jsonschema-with-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());
        for (ArtifactReference ref : refs) {
            Assertions.assertNotNull(ref.getName());
            Assertions.assertNull(ref.getGroupId());
            Assertions.assertNull(ref.getArtifactId());
            Assertions.assertNull(ref.getVersion());
        }
    }

    @Test
    public void testCanonicalizeJsonContent() {
        String content = "{\"z\": 1, \"a\": 2, \"m\": 3}";
        String expected = "{\"a\":2,\"m\":3,\"z\":1}";

        String actual = given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.JSON)
                .body(content)
                .post("/registry/v3/content/canonicalize")
                .then().statusCode(200)
                .extract().body().asString();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testCanonicalizeOpenApiContent() {
        String content = "{\n  \"openapi\": \"3.0.2\",\n  \"info\": {\n    \"title\": \"Empty API\","
                + "\n    \"version\": \"1.0.0\"\n  },\n  \"paths\": {\n    \"/\": {}\n  },"
                + "\n  \"components\": {}\n}";
        String expected = "{\"components\":{},\"info\":{\"title\":\"Empty API\",\"version\":\"1.0.0\"},"
                + "\"openapi\":\"3.0.2\",\"paths\":{\"/\":{}}}";

        String actual = given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.OPENAPI)
                .body(content)
                .post("/registry/v3/content/canonicalize")
                .then().statusCode(200)
                .extract().body().asString();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testCanonicalizeAvroContent() {
        String content = "{\n  \"type\": \"record\",\n  \"namespace\": \"com.example\","
                + "\n  \"name\": \"FullName\",\n  \"fields\": [\n"
                + "    { \"name\": \"first\", \"type\": \"string\" }\n  ]\n}";
        String expected = "{\"type\":\"record\",\"name\":\"FullName\",\"namespace\":\"com.example\","
                + "\"doc\":\"\",\"fields\":[{\"name\":\"first\",\"type\":\"string\",\"doc\":\"\"}]}";

        String actual = given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.AVRO)
                .body(content)
                .post("/registry/v3/content/canonicalize")
                .then().statusCode(200)
                .extract().body().asString();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testCanonicalizeEmptyBody() {
        given().when().contentType(CT_JSON)
                .queryParam("artifactType", ArtifactType.JSON)
                .body("")
                .post("/registry/v3/content/canonicalize")
                .then().statusCode(400);
    }

    @Test
    public void testCanonicalizeUnknownArtifactType() {
        given().when().contentType(CT_JSON)
                .queryParam("artifactType", "GARBAGE")
                .body("{\"key\": \"value\"}")
                .post("/registry/v3/content/canonicalize")
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
        Assertions.assertEquals(expected, actual);
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
        Assertions.assertEquals(content, actual);
    }

    @Test
    public void testCanonicalizeDereferencedContent() throws Exception {
        String groupId = "testCanonicalizeDereferenced";

        String addressSchema = "{\"type\":\"object\",\"title\":\"Address\","
                + "\"properties\":{\"zip\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"}}}";
        createArtifact(groupId, "Address", ArtifactType.JSON, addressSchema,
                ContentTypes.APPLICATION_JSON);

        String orderSchema = "{\"type\":\"object\",\"title\":\"Order\","
                + "\"properties\":{\"orderId\":{\"type\":\"string\"},"
                + "\"shippingAddress\":{\"$ref\":\"address.json\"}}}";
        List<io.apicurio.registry.rest.v3.beans.ArtifactReference> refs = Collections
                .singletonList(io.apicurio.registry.rest.v3.beans.ArtifactReference.builder()
                        .name("address.json").groupId(groupId)
                        .artifactId("Address").version("1").build());
        createArtifactWithReferences(groupId, "Order", ArtifactType.JSON, orderSchema,
                ContentTypes.APPLICATION_JSON, refs);

        String actual = given().when()
                .pathParam("groupId", groupId)
                .pathParam("artifactId", "Order")
                .queryParam("references", "DEREFERENCE")
                .queryParam("canonical", true)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200)
                .extract().body().asString();
        Assertions.assertFalse(actual.contains("$ref"),
                "Dereferenced content should not contain $ref");
        Assertions.assertTrue(actual.contains("\"Address\""),
                "Dereferenced content should inline the Address schema");
    }
}
