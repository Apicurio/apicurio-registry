/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
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

        String artifactId1 = "Empty-1";
        String artifactId2 = "Empty-2";
        
        // Create artifact 1
        ArtifactMetaData amd1 = given()
                .when()
                    .contentType(CT_JSON)
                    .pathParam("groupId", GROUP)
                    .header("X-Registry-ArtifactId", artifactId1)
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                    .body(artifactContent)
                .post("/v2/groups/{groupId}/artifacts")
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
                    .header("X-Registry-ArtifactType", ArtifactType.OPENAPI.name())
                    .body(artifactContent)
                .post("/v2/groups/{groupId}/artifacts")
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
                .get("/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo("OPENAPI"))
                .body("groupId", equalTo(GROUP))
                .body("contentId", equalTo(amd1.getContentId().intValue()));

        
        // Get artifact2 meta data and check the contentId
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId2)
                .get("/v2/groups/{groupId}/artifacts/{artifactId}/meta")
            .then()
                .statusCode(200)
                .body("type", equalTo("OPENAPI"))
                .body("groupId", equalTo(GROUP))
                .body("contentId", equalTo(amd2.getContentId().intValue()));
        
        // List versions in artifact, make sure contentId is returned.
        given()
            .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", amd1.getId())
                .get("/v2/groups/{groupId}/artifacts/{artifactId}/versions")
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
                .get("/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}/meta")
            .then()
                .statusCode(200)
                .body("globalId", equalTo(amd1.getGlobalId().intValue()))
                .body("contentId", equalTo(amd1.getContentId().intValue()));
        
        
    }

}
