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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class IdsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "IdsResourceTest";

    @Test
    public void testIdsAfterCreate() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");

        // Create a throwaway artifact so that contentId for future artifacts with different
        // content will need to be greater than 0.
        this.createArtifact(GROUP + "-foo", "Empty-0", ArtifactType.WSDL, resourceToString("sample.wsdl"));

        String artifactId1 = "testIdsAfterCreate/Empty-1";
        String artifactId2 = "testIdsAfterCreate/Empty-2";

        // Create artifact 1
        ArtifactMetaData amd1 = given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactId", artifactId1)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                    .statusCode(200)
                .extract()
                    .as(ArtifactMetaData.class);
        waitForArtifact(GROUP, artifactId1);
        // Create artifact 2
        ArtifactMetaData amd2 = given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactId", artifactId2)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                    .statusCode(200)
                .extract()
                    .as(ArtifactMetaData.class);
        waitForArtifact(GROUP, artifactId2);

        Assertions.assertNotNull(amd1.getGlobalId());
        Assertions.assertNotNull(amd1.getContentId());
        Assertions.assertNotEquals(0, amd1.getContentId());

        Assertions.assertNotEquals(amd1.getGlobalId(), amd2.getGlobalId());
        Assertions.assertEquals(amd1.getContentId(), amd2.getContentId());

        // Get artifact1 meta data and check the contentId
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId1)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("groupId", equalTo(GROUP))
                .body("contentId", equalTo(amd1.getContentId().intValue()));


        // Get artifact2 meta data and check the contentId
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId2)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo(ArtifactType.OPENAPI))
                .body("groupId", equalTo(GROUP))
                .body("contentId", equalTo(amd2.getContentId().intValue()));

        // List versions in artifact, make sure contentId is returned.
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", amd1.getId())
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions")
            .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("versions[0].contentId", notNullValue())
                .body("versions[0].contentId", not(equalTo(0)))
                .body("versions[0].contentId", equalTo(amd1.getContentId().intValue()));

        // Get artifact version meta-data, make sure contentId is returned
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", amd1.getId())
                .pathParam("version", amd1.getVersion())
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("globalId", equalTo(amd1.getGlobalId().intValue()))
                .body("contentId", equalTo(amd1.getContentId().intValue()));


    }

    @Test
    public void testGetByGlobalId() throws Exception {
        String title = "Test By Global ID API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String artifactId = "testGetByGlobalId/Empty";

        // Create the artifact.
        ArtifactMetaData amd = given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                .statusCode(200)
                .extract()
                .as(ArtifactMetaData.class);
        waitForArtifact(GROUP, artifactId);

        long globalId = amd.getGlobalId();

        this.waitForGlobalId(globalId);

        // Get by globalId
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("globalId", globalId)
                .get("/registry/v2/ids/globalIds/{globalId}")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo(title));

    }

    @Test
    public void testGetByGlobalIdIssue1501() throws Exception {
        String title = "Test By Global ID API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String group1 = TestUtils.generateGroupId();
        String group2 = TestUtils.generateGroupId();
        String artifactId = "testIssue1501";

        // Create two artifacts with same artifactId but with different groupId

        long globalId1 = createArtifact(group1, artifactId, ArtifactType.OPENAPI, artifactContent);
        this.waitForGlobalId(globalId1);

        long globalId2 = createArtifact(group2, artifactId, ArtifactType.OPENAPI, artifactContent);
        this.waitForGlobalId(globalId2);

        // Get by globalId should not fail
        clientV2.getContentByGlobalId(globalId1);
        clientV2.getContentByGlobalId(globalId2);

    }


    @Test
    public void testGetByContentId() throws Exception {
        String title = "Test By Content ID API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String artifactId = "testGetByContentId/Empty";

        // Create the artifact.
        ArtifactMetaData amd = given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactId", artifactId)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                    .body(artifactContent)
                .post("/registry/v2/groups/{groupId}/artifacts")
                .then()
                    .statusCode(200)
                .extract()
                    .as(ArtifactMetaData.class);
        waitForArtifact(GROUP, artifactId);

        long contentId = amd.getContentId();

        // Get by contentId
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("contentId", contentId)
                .get("/registry/v2/ids/contentIds/{contentId}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo(title));

        // Get by contentId (not found)
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("contentId", Integer.MAX_VALUE)
                .get("/registry/v2/ids/contentIds/{contentId}")
            .then()
                .statusCode(404);
    }

    @Test
    public void testGetByContentHash() throws Exception {
        String title = "Test By Content Hash API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String contentHash = DigestUtils.sha256Hex(artifactContent);

        String artifactId = "testGetByContentHash/Empty";

        // Create the artifact.
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", ArtifactType.OPENAPI)
                .body(artifactContent)
            .post("/registry/v2/groups/{groupId}/artifacts")
            .then()
                .statusCode(200);
        waitForArtifact(GROUP, artifactId);

        // Get by contentHash
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("contentHash", contentHash)
                .get("/registry/v2/ids/contentHashes/{contentHash}")
            .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo(title));


        // Get by contentHash (not found)
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("contentHash", "CONTENT-HASH-NOT-VALID")
                .get("/registry/v2/ids/contentHashes/{contentHash}")
            .then()
                .statusCode(404);
    }

}
