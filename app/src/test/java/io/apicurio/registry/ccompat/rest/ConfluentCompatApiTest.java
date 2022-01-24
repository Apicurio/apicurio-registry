/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.rest.v1.beans.UpdateState;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ResponseBodyExtractionOptions;
import io.restassured.response.ValidatableResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the REST API exposed at endpoint "/ccompat/v6" follows the
 * <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html">Confluent API specification</a>,
 * unless otherwise stated.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@QuarkusTest
public class ConfluentCompatApiTest extends AbstractResourceTestBase {


    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\"}";

    private static final String SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\","
            + "\"schemaType\": \"AVRO\"}";

//    private static final String SCHEMA_INVALID_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"bloop\\\"}\"}";

    private static final String SCHEMA_1_WRAPPED = "{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"test1\\\", " +
            "\\\"fields\\\": [ {\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field1\\\"} ] }\"}\"";

    private static final String SCHEMA_2_WRAPPED = "{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"test1\\\", " +
            "\\\"fields\\\": [ {\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field1\\\"}, " +
            "{\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field2\\\"} ] }\"}\"";

    private static final String CONFIG_BACKWARD = "{\"compatibility\": \"BACKWARD\"}";

    private static final String VALID_AVRO_SCHEMA = "{\"schema\": \"{\\\"type\\\": \\\"record\\\",\\\"name\\\": \\\"myrecord1\\\",\\\"fields\\\": [{\\\"name\\\": \\\"foo1\\\",\\\"type\\\": \\\"string\\\"}]}\"}\"";

    private static final String SCHEMA_INVALID_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"bloop\\\"}\"}";
    /**
     * Endpoint: /subjects/(string: subject)/versions
     */
    @Test
    public void testCreateSubject() throws Exception {
        final String SUBJECT = "subject1";
        // POST
        ValidatableResponse res = given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_SIMPLE_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
        /*int id = */res.extract().jsonPath().getInt("id");

        this.waitForArtifact(SUBJECT);

        // Verify
        given()
            .when()
                .get("/registry/v1/artifacts/{artifactId}", SUBJECT)
            .then()
                .statusCode(200)
                .body("", equalTo(new JsonPath(SCHEMA_SIMPLE).getMap("")));
    }

    /**
     * Endpoint: /subjects/(string: subject)/versions
     */
    @Test
    public void testCreateDuplicateContent() throws Exception {
        final String SUBJECT = "testCreateDuplicateContent";
        // POST content1
        ValidatableResponse res = given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
        int id1 = res.extract().jsonPath().getInt("id");

        this.waitForContentId(id1);

        // POST content2
        res = given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
        int id2 = res.extract().jsonPath().getInt("id");

        this.waitForContentId(id2);

        // POST content3 (duplicate of content1)
        res = given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
        int id3 = res.extract().jsonPath().getInt("id");

        // ID1 and ID3 should be the same because they are the same content within the same subject.
        Assertions.assertEquals(id1, id3);
    }

    /**
     * Endpoint: /compatibility/subjects/{subject}/versions/{version}
     */
    @Test
    public void testCompatibilityCheck() throws Exception {
        final String SUBJECT = "testCompatibilityCheck";
        // Prepare
        given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body(anything());
        this.waitForArtifact(SUBJECT);
        given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .put("/ccompat/v6/config/{subject}", SUBJECT)
            .then()
                .statusCode(200)
                .body(anything());

        // POST
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(SCHEMA_1_WRAPPED)
                    .post("/ccompat/v6/compatibility/subjects/{subject}/versions/{version}", SUBJECT, "latest")
                .then()
                    .statusCode(200)
                    .body("is_compatible", equalTo(true));
            given()
                .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(SCHEMA_SIMPLE_WRAPPED)
                    .post("/ccompat/v6/compatibility/subjects/{subject}/versions/{version}", SUBJECT, "latest")
                .then()
                    .statusCode(200)
                    .body("is_compatible", equalTo(false));
        });
    }

    /**
     * Endpoint: /compatibility/subjects/{subject}/versions/{version}
     */
    @Test
    public void testCompatibilityInvalidSchema() throws Exception {

        final String SUBJECT = "testCompatibilityInvalidSchema";

        // Prepare
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(VALID_AVRO_SCHEMA)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body(anything());

        this.waitForArtifact(SUBJECT);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .put("/ccompat/v6/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .body(anything());

        this.waitForArtifactRule(SUBJECT, RuleType.COMPATIBILITY);

        // POST
        TestUtils.retry(() -> {
            given()
                    .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(SCHEMA_INVALID_WRAPPED)
                    .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                    .then()
                    .statusCode(422)
                    .body("error_code", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(42201)));
        });
    }

    @Test
    public void testDisabledStateCheck() throws Exception {
        final String SUBJECT = "subject3";
        // Prepare
        given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_SIMPLE_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
            .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));

        this.waitForArtifact(SUBJECT);

        //verify
        given()
            .when()
                .get("/registry/v1/artifacts/{artifactId}", SUBJECT)
            .then()
                .statusCode(200)
                .body("", equalTo(new JsonPath(SCHEMA_SIMPLE).getMap("")));

        //Update state
        UpdateState updateState = new UpdateState();
        updateState.setState(ArtifactState.DISABLED);
        given()
           .when()
               .contentType(ContentTypes.JSON)
               .body(updateState)
               .put("/registry/v1/artifacts/{artifactId}/state", SUBJECT)
           .then()
               .statusCode(204);

        // GET - shouldn't return as the state has been changed to DISABLED
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .get("/ccompat/v6/subjects/{subject}/versions/{version}", SUBJECT, "latest")
                .then()
                    .statusCode(404)
                    .body("error_code", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(40402)));
        });

        // GET schema only - shouldn't return as the state has been changed to DISABLED
        TestUtils.retry(() -> {
            given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get("/ccompat/v6/subjects/{subject}/versions/{version}/schema", SUBJECT, "latest")
                .then()
                .statusCode(404)
                    .body("error_code", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(40402)));
        });
    }

    @Test
    public void testSchemaTypes() throws Exception {
        //verify
        String[] types = given()
                .when()
                .get("/ccompat/v6/schemas/types")
                .then()
                .statusCode(200)
        .extract().as(String[].class);

        assertEquals(3, types.length);
        assertEquals("JSON", types[0]);
        assertEquals("PROTOBUF", types[1]);
        assertEquals("AVRO", types[2]);
    }

    /**
     * Endpoint: /subjects/{subject}/versions
     */
    @Test
    public void testRegisterWithType() throws Exception {
        final String SUBJECT = "subjectRegisterWithType";

        // POST
        given()
                .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .body(SCHEMA_SIMPLE_WRAPPED_WITH_TYPE)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                    .statusCode(200)
                    .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
    }


    /**
     * Endpoint: /schemas/ids/{int: id}
     */
    @Test
    public void testGetSchemaById() throws Exception {
        final String SUBJECT = "subjectTestSchema";

        // POST
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_SIMPLE_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));

        this.waitForArtifact(SUBJECT);

        final Integer globalId = given().when().get("/ccompat/v6/subjects/{subject}/versions/latest", SUBJECT).body().jsonPath().get("id");

        //Verify
        Assertions.assertEquals(given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get("/ccompat/v6/schemas/ids/{id}", globalId)
                .then()
                .extract()
                .body().jsonPath().get("schemaType"), "AVRO");
    }

    /**
     * Endpoint: /schemas/ids/{int: id}/versions
     */
    @Test
    public void testGetSchemaVersions() throws Exception {
        final String SUBJECT = "subjectTestSchemaVersions";
        final String SECOND_SUBJECT = "secondSubjectTestSchemaVersions";

        //Create two versions of the same artifact
        // POST
        final Integer contentId1 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");
        Assertions.assertNotNull(contentId1);

        this.waitForArtifact(SUBJECT);

        //Register different schema in second subject
        // POST
        final Integer contentId2 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SECOND_SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        Assertions.assertNotNull(contentId2);

        this.waitForArtifact(SECOND_SUBJECT);
        this.waitForContentId(contentId2);

        //Register again schema 1 in different subject gives same contentId
        // POST
        final Integer contentId3 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SECOND_SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        Assertions.assertNotNull(contentId3);

        this.waitForArtifact(SECOND_SUBJECT);
        this.waitForContentId(contentId3);

        TestUtils.retry(() -> {
            //Verify
            final ResponseBodyExtractionOptions body = given()
                    .when()
                    .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                    .get("/ccompat/v6/schemas/ids/{id}/versions", contentId1)
                    .then()
                    .statusCode(200)
                    .extract().body();

            assertEquals(contentId1, contentId3);

            final List<String> subjects = body.jsonPath().get("subject");
            final List<Integer> versions = body.jsonPath().get("version");
            Assertions.assertTrue(List.of("subjectTestSchemaVersions", "secondSubjectTestSchemaVersions").containsAll(subjects));
            Assertions.assertTrue(List.of(1, 2).containsAll(versions));
        });
    }

    /**
     * Endpoint: /ccompat/v6/subjects/{subject}/versions/{version}/referencedby
     */
    @Test
    public void testGetSchemaReferencedVersions() throws Exception {
        final String SUBJECT = "testGetSchemaReferencedVersions";

        //Create two versions of the same artifact
        // POST
        final Integer globalId1 = given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED).post("/ccompat/v6/subjects/{subject}/versions", SUBJECT).then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");
        Assertions.assertNotNull(globalId1);

        this.waitForArtifact(SUBJECT);

        // POST
        final Integer globalId2 = given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED).post("/ccompat/v6/subjects/{subject}/versions", SUBJECT).then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(1)))
                .extract().body().jsonPath().get("id");
        Assertions.assertNotNull(globalId2);

        this.waitForContentId(globalId2);

        //Verify
        Integer[] versions = given().when()
                .get("/ccompat/v6/subjects/{subject}/versions/{version}/referencedby", SUBJECT, 1L).then().statusCode(200)
                .extract().as(Integer[].class);

        assertEquals(2, versions.length);
    }

    /**
     * Endpoint: /ccompat/v6/config PUT
     * Endpoint: /ccompat/v6/config GET
     */
    @Test
    public void testConfigEndpoints() throws Exception {

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .put("/ccompat/v6/config/")
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelDto.class);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get("/ccompat/v6/config/")
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelParamDto.class);
    }

    /**
     * Endpoint: /ccompat/v6/config/{subject} PUT
     * Endpoint: /ccompat/v6/config/{subject} GET
     */
    @Test
    public void testSubjectConfigEndpoints() throws Exception {

        final String SUBJECT = "subject2";
        // Prepare
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body(anything());
        this.waitForArtifact(SUBJECT);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .put("/ccompat/v6/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelDto.class);


        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .get("/ccompat/v6/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelParamDto.class);
    }

    @Test
    public void validateAcceptHeaders() throws Exception {

        //Test application/json
        String[] types = given()
                .when()
                .accept(ContentTypes.JSON)
                .get("/ccompat/v6/schemas/types")
                .then()
                .statusCode(200)
                .extract().as(String[].class);

        assertTypes(types);

        //Test application/vnd.schemaregistry.v1+jso
        types = given()
                .when()
                .accept(ContentTypes.COMPAT_SCHEMA_REGISTRY_V1)
                .get("/ccompat/v6/schemas/types")
                .then()
                .statusCode(200)
                .extract().as(String[].class);

        assertTypes(types);

        //Test application/vnd.schemaregistry+json
        types = given()
                .when()
                .accept(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get("/ccompat/v6/schemas/types")
                .then()
                .statusCode(200)
                .extract().as(String[].class);

        assertTypes(types);
    }

    public void assertTypes(String[] types) {

        assertEquals(3, types.length);
        assertEquals("JSON", types[0]);
        assertEquals("PROTOBUF", types[1]);
        assertEquals("AVRO", types[2]);
    }


}
