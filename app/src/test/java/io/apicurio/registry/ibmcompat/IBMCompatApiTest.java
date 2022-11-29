/*
 * Copyright 2020 IBM
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

package io.apicurio.registry.ibmcompat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.Optional;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.ibmcompat.model.SchemaState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.common.mapper.TypeRef;


@QuarkusTest
@TestProfile(IBMTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class IBMCompatApiTest extends AbstractResourceTestBase {

    @Test
    public void testCreateSchema() throws Exception {

        // Convert the file contents to a JSON string value
        String schemaDefinition = resourceToString("avro.json")
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n");

        String schemaName = "testCreateSchema_userInfo";
        String schemaId = schemaName.toLowerCase();
        String versionName = "testversion_1.0.0";

        // Create Avro artifact via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + schemaName + "\",\"version\":\"" + versionName + "\",\"definition\":\"" + schemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas")
            .then()
                .statusCode(201)
                .body("name", equalTo(schemaName))
                .body("id", equalTo(schemaId))
                .body("enabled", is(true))
                .body("state.state", equalTo("active"))
                .body("versions.size()", is(1))
                .body("versions[0].name", equalTo(versionName))
                .body("versions[0].id", is(1))
                .body("versions[0].state.state", equalTo("active"))
                .body("versions[0].enabled", is(true))
                .body("versions[0].date", notNullValue());

        waitForArtifact(schemaId);

        // Try to create the same Avro artifact via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + schemaName + "\",\"version\":\"" + versionName + "\",\"definition\":\"" + schemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas")
            .then()
                .statusCode(409);
    }

    @Test
    public void testVerifySchema() throws Exception {

        // Convert the file contents to a JSON string value
        String schemaDefinition = resourceToString("avro.json")
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n");

        String schemaName = "testVerifySchema_userInfo";
        String versionName = "testversion_1.0.0";

        // Verify Avro artifact via ibmcompat API
        given()
            .when()
                .queryParam("verify", "true")
                .contentType(CT_JSON)
                .body("{\"name\":\"" + schemaName + "\",\"version\":\"" + versionName + "\",\"definition\":\"" + schemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas")
            .then()
                .statusCode(200)
                .body(equalTo("\"" + schemaDefinition + "\""));
    }

    @Test
    public void testGetSchemas() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String schemaName = "testGetSchemas_userInfo";

        // Create Avro artifact via the artifact API
        createArtifact(schemaName, ArtifactType.AVRO, artifactContent);

        // Get the list of artifacts via ibmcompat API
        SchemaListItem schema;
        int page = 0;
        while (true) {
            List<SchemaListItem> schemas = given()
                    .when()
                    .get("/ibmcompat/v1/schemas?page=" + page)
                    .then()
                    .statusCode(200)
                    .extract().body().as(new TypeRef<List<SchemaListItem>>() {
                    });

            if (schemas.isEmpty()) {
                Assertions.fail("No such schema present: " + schemaName);
            }

            // Find the schema that was just added
            Optional<SchemaListItem> so = schemas.stream()
                    .filter(item -> schemaName.equals(item.getId()))
                    .findFirst();
            if (so.isPresent()) {
                schema = so.get();
                break;
            } else {
                page++;
            }
        }

        Assertions.assertEquals(schemaName, schema.getName());
        Assertions.assertTrue(schema.isEnabled());
        Assertions.assertEquals(SchemaState.StateEnum.ACTIVE, schema.getState().getState());
        Assertions.assertEquals(1, schema.getLatest().getId());
        Assertions.assertEquals("userInfo", schema.getLatest().getName());
        Assertions.assertEquals(true, schema.getLatest().getEnabled());
        Assertions.assertEquals(SchemaState.StateEnum.ACTIVE, schema.getLatest().getState().getState());
    }

    @Test
    public void testGetSchema() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String schemaName = "testGetSchema_userInfo";
        String schemaId = schemaName.toLowerCase();

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);

        // Get the artifact via ibmcompat API
        given()
            .when()
                .get("/ibmcompat/v1/schemas/" + schemaName)
            .then()
                .statusCode(200)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", is(true))
                .body("state.state", equalTo("active"))
                .body("versions.size()", is(1))
                .body("versions[0].name", equalTo("userInfo"))
                .body("versions[0].id", is(1))
                .body("versions[0].state.state", equalTo("active"))
                .body("versions[0].enabled", is(true))
                .body("versions[0].date", notNullValue());

        // schema ID in path can be the name or the id (which is the lower-cased name)
        given()
            .when()
                .get("/ibmcompat/v1/schemas/" + schemaId)
            .then()
                .statusCode(200)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", is(true))
                .body("state.state", equalTo("active"))
                .body("versions.size()", is(1))
                .body("versions[0].name", equalTo("userInfo"))
                .body("versions[0].id", is(1))
                .body("versions[0].state.state", equalTo("active"))
                .body("versions[0].enabled", is(true))
                .body("versions[0].date", notNullValue());
    }

    @Test
    public void testDeleteSchema() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String schemaName = "testDeleteSchema_userInfo";
        String schemaId = schemaName.toLowerCase();

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);

        // Delete the artifact via ibmcompat API
        given()
            .when()
                .delete("/ibmcompat/v1/schemas/" + schemaName)
            .then()
                .statusCode(204);

        TestUtils.retry(() -> {
            // Try to get the artifact via ibmcompat API
            given()
                .when()
                    .get("/ibmcompat/v1/schemas/" + schemaName)
                .then()
                    .statusCode(404);
        });
    }

    @Test
    public void testPatchSchemaState() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String version2SchemaDefinition = artifactContent
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n");
        String schemaName = "testPatchSchemaState_userInfo";
        String schemaId = schemaName.toLowerCase();
        String version2Name = "testversion_2.0.0";

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);
        // Add the new version via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"version\":\"" + version2Name + "\",\"definition\":\"" + version2SchemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas/" + schemaName + "/versions")
            .then()
                .statusCode(201)
                .body("versions.size()", equalTo(2));

        waitForVersion(schemaId, 2);

        // Patch the schema enabled state via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("[{\"op\":\"replace\",\"path\":\"/enabled\",\"value\":false}]")
                .patch("/ibmcompat/v1/schemas/" + schemaName)
            .then()
                .statusCode(200)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", is(false))
                .body("state.state", equalTo("active"))
                .body("versions.size()", is(2))
                .body("versions[0].id", is(1))
                .body("versions[0].state.state", equalTo("active"))
                .body("versions[0].enabled", is(false))
                .body("versions[1].id", is(2))
                .body("versions[1].state.state", equalTo("active"))
                .body("versions[1].enabled", is(false));


        TestUtils.retry(() -> {
            // Patch the schame deprecated state via ibmcompat API
            given()
                .when()
                    .contentType(CT_JSON)
                    .body("[{\"op\":\"replace\",\"path\":\"/state\",\"value\":{\"state\":\"deprecated\",\"comment\":\"this schema is deprecated\"}}]")
                    .patch("/ibmcompat/v1/schemas/" + schemaName)
                .then()
                    .statusCode(200)
                    .body("name", equalTo(schemaId))
                    .body("id", equalTo(schemaId))
                    .body("enabled", is(true))
                    .body("state.state", equalTo("deprecated"))
                    .body("state.comment", equalTo("this schema is deprecated"))
                    .body("versions.size()", is(2))
                    .body("versions[0].id", is(1))
                    .body("versions[0].state.state", equalTo("deprecated"))
                    .body("versions[0].enabled", is(true))
                    .body("versions[1].id", is(2))
                    .body("versions[1].state.state", equalTo("deprecated"))
                    .body("versions[1].enabled", is(true));
        });
    }


    @Test
    public void testGetSchemaVersion() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String schemaName = "testGetSchemaVersion_userInfo";
        String schemaId = schemaName.toLowerCase();

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);

        // Get the list of artifacts via ibmcompat API
        given()
            .when()
                .get("/ibmcompat/v1/schemas/" + schemaName + "/versions/1")
            .then()
                .statusCode(200)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", is(true))
                .body("state.state", equalTo("active"))
                .body("version.name", equalTo("userInfo"))
                .body("version.id", is(1))
                .body("version.state.state", equalTo("active"))
                .body("version.enabled", is(true))
                .body("version.date", notNullValue())
                .body("definition", equalTo(artifactContent));
    }

    @Test
    public void testCreateSchemaVersion() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String newSchemaDefinition = artifactContent
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n");

        String schemaName = "testCreateSchemaVersion_userInfo";
        String schemaId = schemaName.toLowerCase();
        String newVersionName = "testversion_2.0.0";

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);

        // Add the new version via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"version\":\"" + newVersionName + "\",\"definition\":\"" + newSchemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas/" + schemaName + "/versions")
            .then()
                .statusCode(201)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", equalTo(true))
                .body("state.state", equalTo("active"))
                .body("versions.size()", equalTo(2))
                .body("versions[0].name", equalTo("userInfo"))
                .body("versions[0].id", is(1))
                .body("versions[1].name", equalTo(newVersionName))
                .body("versions[1].id", is(2))
                .body("versions[1].state.state", equalTo("active"))
                .body("versions[1].enabled", equalTo(true))
                .body("versions[1].date", notNullValue());
    }

    @Test
    public void testVerifySchemaVersion() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String newSchemaDefinition = artifactContent
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n");

        String schemaName = "testVerifySchemaVersion_userInfo";
        String schemaId = schemaName.toLowerCase();
        String newVersionName = "testversion_2.0.0";

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);

        // Verify the new version definition via ibmcompat API
        given()
            .when()
                .queryParam("verify", true)
                .contentType(CT_JSON)
                .body("{\"version\":\"" + newVersionName + "\",\"definition\":\"" + newSchemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas/" + schemaName + "/versions")
            .then()
                .statusCode(200)
                .body(equalTo("\"" + newSchemaDefinition + "\""));
    }

    @Test
    public void testDeleteSchemaVersion() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String newSchemaDefinition = artifactContent
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n");
        String schemaName = "testDeleteSchemaVersion_userInfo";
        String schemaId = schemaName.toLowerCase();
        String newVersionName = "testversion_2.0.0";

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);
        // Add the new version via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"version\":\"" + newVersionName + "\",\"definition\":\"" + newSchemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas/" + schemaName + "/versions")
            .then()
                .statusCode(201)
                .body("versions.size()", equalTo(2));

        waitForVersion(schemaId, 2);

        // Delete the artifact via ibmcompat API
        given()
            .when()
                .delete("/ibmcompat/v1/schemas/" + schemaName+ "/versions/2")
            .then()
                .statusCode(204);

        TestUtils.retry(() -> {
            // Try to get the artifact via ibmcompat API
            given()
                .when()
                    .get("/ibmcompat/v1/schemas/" + schemaName + "/versions/2")
                .then()
                   .statusCode(404);
        });
    }


    @Test
    public void testPatchSchemaVersionState() throws Exception {

        String artifactContent = resourceToString("avro.json");
        String version2SchemaDefinition = artifactContent
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n");
        String schemaName = "testPatchSchemaVersionState_userInfo";
        String schemaId = schemaName.toLowerCase();
        String version2Name = "testversion_2.0.0";

        // Create Avro artifact via the artifact API
        createArtifact(schemaId, ArtifactType.AVRO, artifactContent);
        // Add the new version via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("{\"version\":\"" + version2Name + "\",\"definition\":\"" + version2SchemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas/" + schemaName + "/versions")
            .then()
                .statusCode(201)
                .body("versions.size()", equalTo(2));

        waitForVersion(schemaId, 2);

        // Patch the schema enabled state via ibmcompat API
        given()
            .when()
                .contentType(CT_JSON)
                .body("[{\"op\":\"replace\",\"path\":\"/enabled\",\"value\":false}]")
                .patch("/ibmcompat/v1/schemas/" + schemaName + "/versions/2")
            .then()
                .statusCode(200)
                .body("name", equalTo(schemaId))
                .body("id", equalTo(schemaId))
                .body("enabled", is(true))
                .body("state.state", equalTo("active"))
                .body("versions.size()", is(2))
                .body("versions[0].id", is(1))
                .body("versions[0].state.state", equalTo("active"))
                .body("versions[0].enabled", is(true))
                .body("versions[1].id", is(2))
                .body("versions[1].state.state", equalTo("active"))
                .body("versions[1].enabled", is(false));

        TestUtils.retry(() -> {
            // Patch the schame deprecated state via ibmcompat API
            given()
                .when()
                    .contentType(CT_JSON)
                    .body("[{\"op\":\"replace\",\"path\":\"/state\",\"value\":{\"state\":\"deprecated\",\"comment\":\"this version is deprecated\"}}]")
                    .patch("/ibmcompat/v1/schemas/" + schemaName + "/versions/1")
                .then()
                    .statusCode(200)
                    .body("name", equalTo(schemaId))
                    .body("id", equalTo(schemaId))
                    .body("enabled", is(true))
                    .body("state.state", equalTo("active"))
                    .body("versions.size()", is(2))
                    .body("versions[0].id", is(1))
                    .body("versions[0].state.state", equalTo("deprecated"))
                    .body("versions[0].state.comment", equalTo("this version is deprecated"))
                    .body("versions[0].enabled", is(true))
                    .body("versions[1].id", is(2))
                    .body("versions[1].state.state", equalTo("active"))
                    .body("versions[1].enabled", is(false));
        });
    }
}
