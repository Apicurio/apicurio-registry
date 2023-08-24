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

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.noprofile.ccompat.rest.CCompatTestConstants;
import io.apicurio.registry.services.DisabledApisMatcherService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(DisableApisTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class DisableApisFlagsTest extends AbstractResourceTestBase {

    @Inject
    DisabledApisMatcherService matcherService;

    private static final String GROUP = "DisableApisFlagsTest";

    @Test
    public void testRegexp() {
        assertTrue(matcherService.isDisabled("/apis/ccompat/v6/subjects/abcfoo/versions"));

        assertFalse(matcherService.isDisabled("/apis/ccompat/v6/subjects"));

        assertTrue(matcherService.isDisabled("/ui/artifacts"));
    }

    @Test
    public void testRestApi() throws Exception {
        doTestDisabledApis(false);
    }

    public void doTestDisabledApis(boolean disabledDirectAccess) throws Exception {
        doTestDisabledSubPathRegexp(disabledDirectAccess);

        doTestDisabledChildPathByParentPath(disabledDirectAccess);

        doTestUIDisabled();

        doTestArtifactVersionDeletionDisabled();
    }

    private void doTestArtifactVersionDeletionDisabled() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testDeleteArtifactVersion/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Make sure we can get the artifact content
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        //Get the artifact version 1
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // Try to delete artifact version 1. Should return 405 as feature is disabled
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testDeleteArtifactVersion/EmptyAPI")
                .pathParam("version", "1")
                .delete("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then()
                .statusCode(405)
                .body("message", equalTo("Artifact version deletion operation is not enabled."));
    }

    private void doTestUIDisabled() {
        given()
            .baseUri("http://localhost:" + this.testPort )
            .when()
                .get("/ui")
            .then()
                .statusCode(404);
    }

    private static void doTestDisabledSubPathRegexp(boolean disabledDirectAccess) {
        //this should return http 404, it's disabled
        given()
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(CCompatTestConstants.SCHEMA_SIMPLE_WRAPPED)
                .post("/ccompat/v6/subjects/{subject}/versions", UUID.randomUUID().toString())
            .then()
                .statusCode(404);

        var req = given()
            .when().contentType(CT_JSON).get("/ccompat/v6/subjects")
            .then();
        if (disabledDirectAccess) {
            req.statusCode(404);
        } else {
            //this should return http 200, it's not disabled
            req.statusCode(200)
                .body(anything());
        }
    }

    private static void doTestDisabledChildPathByParentPath(boolean disabledDirectAccess) throws Exception {
        String artifactContent = "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}";
        String schemaId = TestUtils.generateArtifactId();

        var req = given()
            .when()
                .contentType(CT_JSON + "; artifactType=AVRO")
                .pathParam("groupId", "default")
                .header("X-Registry-ArtifactId", schemaId)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
            .then();

        if (disabledDirectAccess) {
            req.statusCode(404);
        } else {
            req.statusCode(200);
        }
    }


}
