package io.apicurio.registry.ccompat.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.v7.beans.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.Schema;
import io.apicurio.registry.ccompat.rest.v7.beans.SchemaId;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(CanonicalModeProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class CCompatCanonicalModeTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Endpoint: /schemas/ids/{int: id}/versions
     */
    @Test
    public void canonicalModeEnabled() throws Exception {
        final String SUBJECT = "testSchemaExpanded";
        String testSchemaExpanded = resourceToString("avro-expanded.avsc");

        RegisterSchemaRequest schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(testSchemaExpanded);

        // POST
        final Integer contentId1 = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        assertNotNull(contentId1);

        RegisterSchemaRequest minifiedSchemaContent = new RegisterSchemaRequest();
        minifiedSchemaContent.setSchema(resourceToString("avro-minified.avsc"));

        // With the canonical hash mode enabled, getting the schema by content works
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(minifiedSchemaContent))
                .post("/ccompat/v7/subjects/{subject}", SUBJECT).then().statusCode(200);

        // POST
        // Create just returns the id from the existing schema, since the canonical hash is the same.
        assertEquals(contentId1,
                given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                        .body(MAPPER.writeValueAsString(minifiedSchemaContent))
                        .post("/ccompat/v7/subjects/{subject}/versions", SUBJECT).then().statusCode(200)
                        .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(contentId1)))
                        .extract().body().jsonPath().get("id"));
    }

    @Test
    public void issue2902() throws Exception {
        final String subject1 = UUID.randomUUID().toString();
        String schemaString1 = resourceToString("avro2-non-canonical.avsc");
        RegisterSchemaRequest schemaContent = new RegisterSchemaRequest();
        schemaContent.setSchema(schemaString1);

        // POST
        SchemaId schemaId1 = given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(MAPPER.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject1).then().statusCode(200).extract()
                .as(SchemaId.class);

        assertNotNull(schemaId1);
        assertNotNull(schemaId1.getId());
        assertTrue(schemaId1.getId() > 0);

        // We are able to get the original content
        Schema schema1R = given().when().contentType(ContentTypes.JSON)
                .get("/ccompat/v7/subjects/{subject}/versions/latest", subject1).then().statusCode(200)
                .extract().as(Schema.class);

        assertEquals(schemaString1, schema1R.getSchema());
        assertEquals(schemaId1.getId(), schema1R.getId());
    }
}
