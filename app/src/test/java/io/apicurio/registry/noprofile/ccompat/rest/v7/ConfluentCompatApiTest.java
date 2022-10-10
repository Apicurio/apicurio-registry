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

package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Typed;

import static io.apicurio.registry.noprofile.ccompat.rest.CCompatTestConstants.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anything;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the REST API exposed at endpoint "/ccompat/v7" follows the
 * <a href="https://docs.confluent.io/7.2.1/schema-registry/develop/api.html">Confluent API specification</a>,
 * unless otherwise stated.
 *
 * @author Carles Arnal
 */
@QuarkusTest
@Typed(ConfluentCompatApiTest.class)
public class ConfluentCompatApiTest extends AbstractResourceTestBase {

    public static final String BASE_PATH = "/ccompat/v7";

    @NotNull
    public String getBasePath() {
        return BASE_PATH;
    }

    @Test
    public void testSubjectConfigEndpoints() throws Exception {
        final String SUBJECT = "subject2";
        // Prepare
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post(getBasePath() + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body(anything());
        this.waitForArtifact(SUBJECT);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .put(getBasePath() + "/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelDto.class);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CONFIG_BACKWARD)
                .get(getBasePath() + "/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelParamDto.class);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .delete(getBasePath() + "/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelParamDto.class);

        final CompatibilityLevelParamDto compatibilityLevel = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .delete(getBasePath() + "/config/{subject}", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(CompatibilityLevelParamDto.class);

        assertEquals(compatibilityLevel.getCompatibilityLevel(), CompatibilityLevelDto.Level.NONE.name());
    }

    @Test
    public void testDeleteSchemaVersion() throws Exception {
        final String SUBJECT = "testDeleteSchemaVersion";
        final Integer contentId1 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_1_WRAPPED)
                .post(getBasePath() + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");
        Assertions.assertNotNull(contentId1);

        this.waitForArtifact(SUBJECT);

        final Integer contentId2 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_2_WRAPPED)
                .post(getBasePath() + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        Assertions.assertNotNull(contentId2);

        this.waitForContentId(contentId2);

        //check versions list
        var versionsConfluent = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getBasePath() + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(Integer[].class);

        assertEquals(2, versionsConfluent.length);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getBasePath() + "/subjects/{subject}/versions/{version}", SUBJECT, "1")
                .then()
                .statusCode(200);
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getBasePath() + "/subjects/{subject}/versions/{version}", SUBJECT, "2")
                .then()
                .statusCode(200);


        var versionsApicurio = given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(VersionSearchResults.class)
                .getVersions();

        assertEquals(2, versionsApicurio.size());

        //delete version 2, permanent is required so the version is hard deleted.
        given()
                .when()
                .queryParam("permanent", true)
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .delete(getBasePath() + "/subjects/{subject}/versions/{version}", SUBJECT, "2")
                .then()
                .statusCode(200);

        versionsConfluent = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getBasePath() + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(Integer[].class);

        assertEquals(1, versionsConfluent.length);

        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .get(getBasePath() + "/subjects/{subject}/versions/{version}", SUBJECT, "1")
                .then()
                .statusCode(200);

        versionsApicurio = given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .extract().as(VersionSearchResults.class)
                .getVersions();

        assertEquals(1, versionsApicurio.size());

        given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}/versions/1", SUBJECT)
                .then()
                .statusCode(200);
        given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}/versions/1/meta", SUBJECT)
                .then()
                .statusCode(200);
        given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}/meta", SUBJECT)
                .then()
                .statusCode(200);
        given()
                .when()
                .get("/registry/v2/groups/default/artifacts/{subject}", SUBJECT)
                .then()
                .statusCode(200);
    }

    /**
     * This is the same test case as in
     * @see io.apicurio.registry.ccompat.rest.CCompatCanonicalModeTest
     * in v7, there is no need for the configuration option since the param is available at the API level.
     */
    @Test
    public void normalizedSchemasTest() throws Exception {
        final String SUBJECT = "testSchemaExpanded";
        String testSchemaExpanded = resourceToString("../avro-expanded.avsc");

        ObjectMapper objectMapper = new ObjectMapper();
        SchemaContent schemaContent = new SchemaContent(testSchemaExpanded);

        // POST
        // creating the content normalized so it can be found later
        final Integer contentId1 = given()
                .when()
                .queryParam("normalize", true)
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post(V7_BASE_PATH + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        Assertions.assertNotNull(contentId1);

        this.waitForArtifact(SUBJECT);

        SchemaContent minifiedSchemaContent = new SchemaContent(resourceToString("../avro-minified.avsc"));

        //without normalize the content, getting the schema by content does not work
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(minifiedSchemaContent))
                .post(V7_BASE_PATH + "/subjects/{subject}", SUBJECT)
                .then()
                .statusCode(404);

        //normalizing the content, getting the schema by content works
        given()
                .when()
                .queryParam("normalize", true)
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(minifiedSchemaContent))
                .post(V7_BASE_PATH + "/subjects/{subject}", SUBJECT)
                .then()
                .statusCode(200);

        // POST
        //Create just returns the id from the existing schema, since the canonical hash is the same.
        Assertions.assertEquals(contentId1, given()
                .when()
                .queryParam("normalize", true)
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(minifiedSchemaContent))
                .post(V7_BASE_PATH + "/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(contentId1)))
                .extract().body().jsonPath().get("id"));
    }
}
