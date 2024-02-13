package io.apicurio.registry.noprofile.rest.v3;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SearchResourceTest extends AbstractResourceTestBase {

    @Test
    public void testSearchByGroup() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = UUID.randomUUID().toString();

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
        }
        // Create 3 artifacts in some other group
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact("SearchResourceTest", artifactId, ArtifactType.OPENAPI, artifactContent);
        }

        given()
            .when()
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].groupId", equalTo(group))
                ;
    }

    @Test
    public void testSearchByName() throws Exception {
        String group = UUID.randomUUID().toString();
        String name = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Two with the UUID name
        for (int idx = 0; idx < 2; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", name));
        }
        // Three with a different name
        for (int idx = 2; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent);
        }

        given()
            .when()
                .queryParam("name", name)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2));
    }

    @Test
    public void testSearchByDescription() throws Exception {
        String group = UUID.randomUUID().toString();
        String description = "The description is "+ UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Two with the UUID description
        for (int idx = 0; idx < 2; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("An example API design using OpenAPI.", description));
        }
        // Three with the default description
        for (int idx = 2; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent);
        }

        given()
            .when()
                .queryParam("description", description)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2));
    }

    @Test
    public void testSearchByLabels() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Create 5 artifacts with various labels
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));

            Map<String, String> labels = new HashMap<>();
            labels.put("all-key", "all-value");
            labels.put("key-" + idx, "value-" + idx);
            labels.put("another-key-" + idx, "another-value-" + idx);
            labels.put("a-key-" + idx, "lorem ipsum");
            labels.put("extra-key-" + (idx % 2), "lorem ipsum");
            
            // Update the artifact meta-data
            EditableArtifactMetaData metaData = new EditableArtifactMetaData();
            metaData.setName(title);
            metaData.setDescription("Some description of an API");
            metaData.setLabels(labels);
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", group)
                    .pathParam("artifactId", artifactId)
                    .body(metaData)
                    .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(204);
        }

        given()
            .when()
                .queryParam("labels", "all-key:all-value")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5));

        given()
            .when()
                .queryParam("labels", "key-1:value-1")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(1));

        given()
            .when()
                .queryParam("labels", "key-1:value-1")
                .queryParam("labels", "another-key-1:another-value-1")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(1));

        given()
            .when()
                .queryParam("labels", "key-1:value-1")
                .queryParam("labels", "key-2:value-2")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(0));
        given()
            .when()
                .queryParam("labels", "key-1:value-1:")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(0));
        given()
            .when()
                .queryParam("labels", "key-1:")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(400);
        given()
            .when()
                .queryParam("labels", ":value-1")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(400);
        given()
            .when()
                .queryParam("labels", "all-key")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5));
        given()
            .when()
                .queryParam("labels", "a-key-1")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(1));
        given()
            .when()
                .queryParam("labels", "extra-key-0")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(3));
        given()
            .when()
                .queryParam("labels", "extra-key-2")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(0));
        given()
            .when()
                .queryParam("labels", ":all-key")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(400);
        given()
            .when()
                .queryParam("labels", "all-key:")
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(400);
    }

    @Test
    public void testSearchByPropertyKey() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Create 5 artifacts with various labels
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));

            Map<String, String> labels = new HashMap<>();
            labels.put("all-key", "lorem ipsum");
            labels.put("a-key-" + idx, "lorem ipsum");
            labels.put("an-another-key-" + idx, "lorem ipsum");
            labels.put("extra-key-" + (idx % 2), "lorem ipsum");

            // Update the artifact meta-data
            EditableArtifactMetaData metaData = new EditableArtifactMetaData();
            metaData.setName(title);
            metaData.setDescription("Some description of an API");
            metaData.setLabels(labels);
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", group)
                    .pathParam("artifactId", artifactId)
                    .body(metaData)
                    .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(204);
        }
    }

    @Test
    public void testOrderBy() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            String name = "empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", name));
        }

        given()
            .when()
                .queryParam("orderby", "name")
                .queryParam("order", "asc")
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "name")
                .queryParam("order", "desc")
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-4"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "desc")
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-4"));
    }

    @Test
    public void testLimitAndOffset() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 20; idx++) {
            String artifactId = "Empty-" + idx;
            String name = "empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", name));
        }

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("limit", 5)
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(20))
                .body("artifacts.size()", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("limit", 15)
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(20))
                .body("artifacts.size()", equalTo(15))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("limit", 5)
                .queryParam("offset", 10)
                .queryParam("group", group)
                .get("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(20))
                .body("artifacts.size()", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-10"));

    }

    @Test
    public void testSearchByContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testSearchByContent";
        String searchByContent = artifactContent.replaceAll("Empty API", "testSearchByContent-empty-api-2");
        String searchByCanonicalContent = searchByContent.replaceAll("\\{", "   {\n");

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "testSearchByContent-empty-api-" + idx;
            String artifactId = "Empty-1-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));

            artifactId = "Empty-2-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
        }

        given()
            .when()
                .body(searchByContent)
                .post("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2))
                ;

        // Searching by content that is not the same should yield 0 results.
        given()
            .when()
                .body(searchByCanonicalContent)
                .post("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(0))
                ;
    }


    @Test
    public void testSearchByCanonicalContent() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testSearchByCanonicalContent";
        String searchByContent = artifactContent.replaceAll("Empty API", "testSearchByCanonicalContent-empty-api-2").replaceAll("\\{", "   {\n");

        System.out.println(searchByContent);

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "testSearchByCanonicalContent-empty-api-" + idx;
            String artifactId = "Empty-1-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));

            artifactId = "Empty-2-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
        }

        given()
            .when()
                .queryParam("canonical", "true")
                .queryParam("artifactType", ArtifactType.OPENAPI)
                .body(searchByContent)
                .post("/registry/v3/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2))
                ;
    }


}
