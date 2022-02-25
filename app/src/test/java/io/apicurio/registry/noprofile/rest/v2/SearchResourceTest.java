/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.noprofile.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
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
            waitForArtifact(group, artifactId);
        }
        // Create 3 artifacts in some other group
        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact("SearchResourceTest", artifactId, ArtifactType.OPENAPI, artifactContent);
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);
        }
        // Three with a different name
        for (int idx = 2; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent);
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("name", name)
                .get("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);
        }
        // Three with the default description
        for (int idx = 2; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent);
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("description", description)
                .get("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);

            List<String> labels = new ArrayList<>(2);
            labels.add("testSearchByLabels");
            labels.add("testSearchByLabels-" + idx);

            // Update the artifact meta-data
            EditableMetaData metaData = new EditableMetaData();
            metaData.setName(title);
            metaData.setDescription("Some description of an API");
            metaData.setLabels(labels);
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", group)
                    .pathParam("artifactId", artifactId)
                    .body(metaData)
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(204);
        }

        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("labels", "testSearchByLabels")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(5));
        });

        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("labels", "testSearchByLabels-2")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(1));
        });
    }

    @Test
    public void testSearchByProperties() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Create 5 artifacts with various properties
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
            waitForArtifact(group, artifactId);

            Map<String, String> props = new HashMap<>();
            props.put("all-key", "all-value");
            props.put("key-" + idx, "value-" + idx);
            props.put("another-key-" + idx, "another-value-" + idx);

            // Update the artifact meta-data
            EditableMetaData metaData = new EditableMetaData();
            metaData.setName(title);
            metaData.setDescription("Some description of an API");
            metaData.setProperties(props);
            given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", group)
                    .pathParam("artifactId", artifactId)
                    .body(metaData)
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                    .statusCode(204);
        }

        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "all-key:all-value")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(5));
        });

        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "key-1:value-1")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(1));
        });

        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "key-1:value-1")
                    .queryParam("properties", "another-key-1:another-value-1")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(1));
        });

        //negative test cases
        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "key-1:value-1")
                    .queryParam("properties", "key-2:value-2")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(0));
        });
        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "key-1:value-1:")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(200)
                    .body("count", equalTo(0));
        });
        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", "key-1:")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(400);
        });
        TestUtils.retry(() -> {
            given()
                .when()
                    .queryParam("properties", ":value-1")
                    .get("/registry/v2/search/artifacts")
                .then()
                    .statusCode(400);
        });
    }

    @Test
    public void testSearchByPropertyKey() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        // Create 5 artifacts with various properties
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
            waitForArtifact(group, artifactId);

            Map<String, String> props = new HashMap<>();
            props.put("all-key", "lorem ipsum");
            props.put("a-key-" + idx, "lorem ipsum");
            props.put("an-another-key-" + idx, "lorem ipsum");
            props.put("extra-key-" + (idx % 2), "lorem ipsum");

            // Update the artifact meta-data
            EditableMetaData metaData = new EditableMetaData();
            metaData.setName(title);
            metaData.setDescription("Some description of an API");
            metaData.setProperties(props);
            given()
                    .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", group)
                    .pathParam("artifactId", artifactId)
                    .body(metaData)
                    .put("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                    .then()
                    .statusCode(204);
        }
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", "all-key")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(5));
        });
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", "a-key-1")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(1));
        });
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", "extra-key-0")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(3));
        });
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", "extra-key-2")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(200)
                    .body("count", equalTo(0));
        });
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", ":all-key")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(400);
        });
        TestUtils.retry(() -> {
            given()
                    .when()
                    .queryParam("properties", "all-key:")
                    .get("/registry/v2/search/artifacts")
                    .then()
                    .statusCode(400);
        });
    }

    @Test
    public void testOrderBy() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 5; idx++) {
            String artifactId = "Empty-" + idx;
            String name = "empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", name));
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("orderby", "name")
                .queryParam("order", "asc")
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "name")
                .queryParam("order", "desc")
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-4"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(5))
                .body("artifacts[0].name", equalTo("empty-0"));

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "desc")
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("orderby", "createdOn")
                .queryParam("order", "asc")
                .queryParam("limit", 5)
                .queryParam("group", group)
                .get("/registry/v2/search/artifacts")
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
                .get("/registry/v2/search/artifacts")
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
                .get("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);

            artifactId = "Empty-2-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .body(searchByContent)
                .post("/registry/v2/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2))
                ;

        // Searching by content that is not the same should yield 0 results.
        given()
            .when()
                .body(searchByCanonicalContent)
                .post("/registry/v2/search/artifacts")
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
            waitForArtifact(group, artifactId);

            artifactId = "Empty-2-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
            waitForArtifact(group, artifactId);
        }

        given()
            .when()
                .queryParam("canonical", "true")
                .queryParam("artifactType", ArtifactType.OPENAPI)
                .body(searchByContent)
                .post("/registry/v2/search/artifacts")
            .then()
                .statusCode(200)
                .body("count", equalTo(2))
                ;
    }


}
