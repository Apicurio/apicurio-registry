package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToObject;

@QuarkusTest
public class LegacyV2ApiTest extends AbstractResourceTestBase {

    private static final String GROUP = "LegacyV2ApiTest";

    @Test
    public void testLegacyLabels() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyLabels";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update the artifact meta-data
        List<String> labels = List.of("one", "two", "three");
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setLabels(labels);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metaData).put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then().statusCode(200)
                .body("id", equalTo(artifactId)).body("version", anything())
                .body("labels", equalToObject(labels));
    }

    @Test
    public void testLegacyProperties() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyProperties";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        // Update the artifact meta-data
        Map<String, String> properties = Map.of("one", "one-value", "two", "two-value");
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setProperties(properties);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metaData).put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then().statusCode(200)
                .body("id", equalTo(artifactId)).body("version", anything())
                .body("properties", equalToObject(properties));
    }

    @Test
    public void testLegacyPropertiesWithLabels() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        String artifactId = "testLegacyPropertiesWithLabels";
        this.createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);

        List<String> labels = List.of("label-one", "label-two");
        Map<String, String> properties = Map.of("property-one", "property-one-value", "property-two",
                "property-two-value");

        // Update the artifact meta-data
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName(artifactId);
        metaData.setLabels(labels);
        metaData.setProperties(properties);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metaData).put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then()
                .statusCode(204);

        // Get the (updated) artifact meta-data
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then().statusCode(200)
                .body("id", equalTo(artifactId)).body("version", anything())
                .body("labels", equalToObject(labels)).body("properties", equalToObject(properties));
    }

    /**
     * Test for issue #6848 - Scenario 1: POST /groups/{groupId}/artifacts (create artifact)
     * Verifies that creating an artifact properly sets the artifact name and description, and that
     * listing artifacts returns the correct values.
     */
    @Test
    public void testCreateArtifactSetsName() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testCreateArtifactSetsName";

        // Create artifact with name "1.0.0" and description
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI).header("X-Registry-Name", "1.0.0")
                .header("X-Registry-Description", "Initial version").pathParam("groupId", GROUP)
                .body(artifactContent).post("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("name", equalTo("1.0.0"))
                .body("description", equalTo("Initial version"));

        // List artifacts - should return name "1.0.0" and description
        given().when().pathParam("groupId", GROUP).get("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("artifacts.find { it.id == '" + artifactId + "' }.name",
                        equalTo("1.0.0"))
                .body("artifacts.find { it.id == '" + artifactId + "' }.description",
                        equalTo("Initial version"));
    }

    /**
     * Test for issue #6848 - Scenario 2: PUT /groups/{groupId}/artifacts/{artifactId} (update artifact
     * = create new version) Verifies that updating artifact content creates a new version and updates
     * the artifact name and description.
     */
    @Test
    public void testUpdateArtifactContentUpdatesName() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testUpdateArtifactContentUpdatesName";

        // Create artifact with name "1.0.0" and description
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI).header("X-Registry-Name", "1.0.0")
                .header("X-Registry-Description", "Initial version").pathParam("groupId", GROUP)
                .body(artifactContent).post("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200);

        // Update artifact content with name "2.0.0" and new description (creates new version)
        given().when().contentType(CT_JSON).header("X-Registry-Name", "2.0.0")
                .header("X-Registry-Description", "Second version").pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(artifactContent)
                .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("name", equalTo("2.0.0")).body("description", equalTo("Second version"));

        // List artifacts - should return name "2.0.0" and description from latest version
        given().when().pathParam("groupId", GROUP).get("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("artifacts.find { it.id == '" + artifactId + "' }.name",
                        equalTo("2.0.0"))
                .body("artifacts.find { it.id == '" + artifactId + "' }.description",
                        equalTo("Second version"));
    }

    /**
     * Test for issue #6848 - Scenario 3: PUT /groups/{groupId}/artifacts/{artifactId}/meta (update
     * artifact metadata) Verifies that updating artifact metadata updates both the version metadata
     * AND the artifact-level metadata.
     */
    @Test
    public void testUpdateArtifactMetaDataUpdatesName() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testUpdateArtifactMetaDataUpdatesName";

        // Create artifact with name "1.0.0"
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI).header("X-Registry-Name", "1.0.0")
                .pathParam("groupId", GROUP).body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200);

        // Update artifact metadata with name "2.0.0"
        EditableMetaData metaData = new EditableMetaData();
        metaData.setName("2.0.0");
        metaData.setDescription("Updated description");
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metaData).put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then()
                .statusCode(204);

        // Get artifact metadata - should return name "2.0.0"
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then().statusCode(200)
                .body("name", equalTo("2.0.0")).body("description", equalTo("Updated description"));

        // List artifacts - should return name "2.0.0" and description
        given().when().pathParam("groupId", GROUP).get("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("artifacts.find { it.id == '" + artifactId + "' }.name",
                        equalTo("2.0.0"))
                .body("artifacts.find { it.id == '" + artifactId + "' }.description",
                        equalTo("Updated description"));
    }

    /**
     * Test for issue #6848 - Scenario 4: POST /groups/{groupId}/artifacts/{artifactId}/versions
     * (create new version) Verifies that creating a new version updates the artifact-level metadata to
     * reflect the latest version's name and description.
     */
    @Test
    public void testCreateVersionUpdatesArtifactName() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testCreateVersionUpdatesArtifactName";

        // Create artifact with name "1.0.0" and description
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI).header("X-Registry-Name", "1.0.0")
                .header("X-Registry-Description", "First version").pathParam("groupId", GROUP)
                .body(artifactContent).post("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("name", equalTo("1.0.0"))
                .body("description", equalTo("First version"));

        // Create a second version with name "2.0.0" and new description
        given().when().contentType(CT_JSON).header("X-Registry-Name", "2.0.0")
                .header("X-Registry-Description", "Second version").pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("name", equalTo("2.0.0"))
                .body("description", equalTo("Second version"));

        // Get artifact metadata - should return name "2.0.0" and description from latest version
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta").then().statusCode(200)
                .body("name", equalTo("2.0.0")).body("description", equalTo("Second version"));

        // List artifacts - should return name "2.0.0" and description from latest version
        given().when().pathParam("groupId", GROUP).get("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("artifacts.find { it.id == '" + artifactId + "' }.name",
                        equalTo("2.0.0"))
                .body("artifacts.find { it.id == '" + artifactId + "' }.description",
                        equalTo("Second version"));
    }

}
