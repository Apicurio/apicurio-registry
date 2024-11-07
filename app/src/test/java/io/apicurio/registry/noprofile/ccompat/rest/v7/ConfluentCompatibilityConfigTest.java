package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
public class ConfluentCompatibilityConfigTest extends AbstractResourceTestBase {

    @Test
    public void testGlobalConfig() {
        CompatibilityLevelDto backward = CompatibilityLevelDto
                .create(Optional.of(CompatibilityLevel.BACKWARD));
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200).body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        given().when().get("/ccompat/v7/config").then().statusCode(200).body("compatibilityLevel",
                equalTo(CompatibilityLevel.BACKWARD.toString()));
    }

    @Test
    public void testSubjectConfig() throws Exception {
        CompatibilityLevelDto backward = CompatibilityLevelDto
                .create(Optional.of(CompatibilityLevel.BACKWARD));

        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";

        var objectMapper = new ObjectMapper();
        var schemaContent = new SchemaContent(schema);

        given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(Schema.class);

        given().when().contentType(ContentTypes.JSON).body(backward)
                .put("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        given().when().get("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));
    }

    @Test
    public void testDefaultToGlobalConfig() throws Exception {
        CompatibilityLevelDto backward = CompatibilityLevelDto
                .create(Optional.of(CompatibilityLevel.BACKWARD));

        // Set global config to BACKWARD
        given().when().contentType(ContentTypes.JSON).body(backward).put("/ccompat/v7/config").then()
                .statusCode(200).body("compatibility", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Create a subject
        var subject = TestUtils.generateSubject();
        var schema = "{\"type\" : \"string\"}";
        var objectMapper = new ObjectMapper();
        var schemaContent = new SchemaContent(schema);
        given().log().all().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v7/subjects/{subject}/versions", subject).then().statusCode(200).extract()
                .as(Schema.class);

        // Query the subject's config with defaultToGlobal=true (should be BACKWARD)
        given().when().queryParam("defaultToGlobal", true).get("/ccompat/v7/config/{subject}", subject).then()
                .statusCode(200).body("compatibilityLevel", equalTo(CompatibilityLevel.BACKWARD.toString()));

        // Query the subject's config with defaultToGlobal=false (should be NONE)
        given().when().queryParam("defaultToGlobal", false).get("/ccompat/v7/config/{subject}", subject)
                .then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));

        // Query the subject's config with defaultToGlobal not set at all (should be NONE)
        given().when().get("/ccompat/v7/config/{subject}", subject).then().statusCode(200)
                .body("compatibilityLevel", equalTo(CompatibilityLevel.NONE.toString()));
    }

}
