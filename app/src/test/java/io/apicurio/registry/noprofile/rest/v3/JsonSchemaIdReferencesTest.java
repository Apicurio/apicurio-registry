package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end tests for JSON Schema references in schemas that declare a base URI with $id.
 */
@QuarkusTest
public class JsonSchemaIdReferencesTest extends AbstractResourceTestBase {

    private static final String CUSTOMER_WITH_ID = """
            {"$id": "https://example.com/schemas/customer/", "type": "object",
             "properties": {"address": {"$ref": "address.json#/definitions/Address"}}}
            """;

    private static final String ADDRESS = """
            {"definitions": {"Address": {"type": "object", "properties": {"street": {"type": "string"}}}}}
            """;

    /**
     * References are detected under their literal $ref names, and registering them under those names
     * makes the schema dereferenceable.
     */
    @Test
    public void testDetectedReferencesDereference() throws Exception {
        VersionContent body = new VersionContent();
        body.setContent(CUSTOMER_WITH_ID);
        body.setContentType(ContentTypes.APPLICATION_JSON);
        var detected = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });
        List<String> names = detected.stream().map(ref -> ref.getName()).toList();
        Assertions.assertEquals(List.of("address.json#/definitions/Address"), names);

        assertDereferencesAddress(names.get(0));
    }

    /**
     * A reference registered under the $ref resolved against the $id also makes the schema
     * dereferenceable.
     */
    @Test
    public void testReferenceRegisteredUnderResolvedIdDereferences() throws Exception {
        assertDereferencesAddress("https://example.com/schemas/customer/address.json#/definitions/Address");
    }

    private void assertDereferencesAddress(String referenceName) throws Exception {
        String groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "Address", ArtifactType.JSON, ADDRESS, ContentTypes.APPLICATION_JSON);
        createArtifactWithReferences(groupId, "Customer", ArtifactType.JSON, CUSTOMER_WITH_ID,
                ContentTypes.APPLICATION_JSON, List.of(ArtifactReference.builder().groupId(groupId)
                        .artifactId("Address").version("1").name(referenceName).build()));

        given().when().pathParam("groupId", groupId).pathParam("artifactId", "Customer")
                .queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("properties.address.properties.street.type", equalTo("string"));
    }
}
