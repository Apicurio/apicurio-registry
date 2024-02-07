package io.apicurio.registry.noprofile.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToObject;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LegacyV2ApiTest extends AbstractResourceTestBase {

    private static final String GROUP = "LegacyV2ApiTest";

    @Test
    public void testLegacyLabels() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyLabels";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Update the artifact meta-data
        List<String> labels = List.of("one", "two", "three");
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setLabels(labels);
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(204);
        
        // Get the (updated) artifact meta-data
        given()
            .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("version", anything())
                .body("labels", equalToObject(labels));
    }

    @Test
    public void testLegacyProperties() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyProperties";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        // Update the artifact meta-data
        Map<String, String> properties = Map.of("one", "one-value", "two", "two-value");
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setProperties(properties);
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(204);
        
        // Get the (updated) artifact meta-data
        given()
            .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("version", anything())
                .body("properties", equalToObject(properties));
    }

    @Test
    public void testLegacyPropertiesWithLabels() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyPropertiesWithLabels";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent);

        List<String> labels = List.of("label-one", "label-two");
        Map<String, String> properties = Map.of("property-one", "property-one-value", "property-two", "property-two-value");

        // Update the artifact meta-data
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setLabels(labels);
        metaData.setProperties(properties);
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(metaData)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(204);
        
        // Get the (updated) artifact meta-data
        given()
            .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("id", equalTo(artifactId))
                .body("version", anything())
                .body("labels", equalToObject(labels))
                .body("properties", equalToObject(properties));
    }

}
