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

package io.apicurio.registry.ccompat.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(CanonicalModeProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class CCompatCanonicalModeTest extends AbstractResourceTestBase {

    /**
     * Endpoint: /schemas/ids/{int: id}/versions
     */
    @Test
    public void canonicalModeEnabled() throws Exception {
        final String SUBJECT = "testSchemaExpanded";
        String testSchemaExpanded = resourceToString("avro-expanded.avsc");

        ObjectMapper objectMapper = new ObjectMapper();
        SchemaContent schemaContent = new SchemaContent(testSchemaExpanded);

        // POST
        final Integer contentId1 = given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(schemaContent))
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)))
                .extract().body().jsonPath().get("id");

        Assertions.assertNotNull(contentId1);

        this.waitForArtifact(SUBJECT);

        SchemaContent minifiedSchemaContent = new SchemaContent(resourceToString("avro-minified.avsc"));

        //With the canonical hash mode enabled, getting the schema by content works
        given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(minifiedSchemaContent))
                .post("/ccompat/v6/subjects/{subject}", SUBJECT)
                .then()
                .statusCode(200);

        // POST
        //Create just returns the id from the existing schema, since the canonical hash is the same.
        Assertions.assertEquals(contentId1, given()
                .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(objectMapper.writeValueAsString(minifiedSchemaContent))
                .post("/ccompat/v6/subjects/{subject}/versions", SUBJECT)
                .then()
                .statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.equalTo(contentId1)))
                .extract().body().jsonPath().get("id"));
    }
}
