/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ConfluentCompatApiTest;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.services.DisabledApisMatcherService;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(DisableApisTestProfile.class)
public class DisableApisFlagsTest extends AbstractResourceTestBase {

//    registry.disable.apis

    @Inject
    DisabledApisMatcherService matcherService;

    @Test
    public void testRegexp() {

        assertTrue(matcherService.isDisabled("/apis/ibmcompat/v1/schemas"));

        assertTrue(matcherService.isDisabled("/apis/ccompat/v6/subjects/abcfoo/versions"));

        assertFalse(matcherService.isDisabled("/apis/ccompat/v6/subjects"));

        assertTrue(matcherService.isDisabled("/ui/artifacts"));
    }

    @Test
    public void testRestApi() throws Exception {
        doTestDisabledApis();
    }

    public static void doTestDisabledApis() throws Exception {
        doTestDisabledSubPathRegexp();

        doTestDisabledPathExactMatch();

        doTestDisabledChildPathByParentPath();

        doTestUIDisabled();
    }

    private static void doTestUIDisabled() {
        given()
            .baseUri("http://localhost:8081")
            .when()
                .get("/ui")
            .then()
                .statusCode(404);
    }

    private static void doTestDisabledSubPathRegexp() {
        //this should return http 404, it's disabled
        given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(ConfluentCompatApiTest.SCHEMA_SIMPLE_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", UUID.randomUUID().toString())
            .then()
                .statusCode(404);

        //this should return http 200, it's not disabled
        given()
            .when().contentType(CT_JSON).get("/ccompat/v6/subjects")
            .then()
            .statusCode(200)
            .body(anything());
    }

    private static void doTestDisabledPathExactMatch() {
        String schemaDefinition = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        String schemaName = "testVerifySchema_userInfo";
        String versionName = "testversion_1.0.0";

        //the entire ibmcompat api is disabled
        given()
            .when()
                .queryParam("verify", "true")
                .contentType(CT_JSON)
                .body("{\"name\":\"" + schemaName + "\",\"version\":\"" + versionName + "\",\"definition\":\"" + schemaDefinition + "\"}")
                .post("/ibmcompat/v1/schemas")
            .then()
                .statusCode(404);
    }

    private static void doTestDisabledChildPathByParentPath() throws Exception {
        String artifactContent = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        String schemaId = TestUtils.generateArtifactId();

        given()
            .when()
                .contentType(CT_JSON + "; artifactType=AVRO")
                .pathParam("groupId", "default")
                .header("X-Registry-ArtifactId", schemaId)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);


        //the entire ibmcompat api is disabled
        given()
            .when()
                .get("/ibmcompat/v1/schemas/" + schemaId)
            .then()
                .statusCode(404);
    }


}
