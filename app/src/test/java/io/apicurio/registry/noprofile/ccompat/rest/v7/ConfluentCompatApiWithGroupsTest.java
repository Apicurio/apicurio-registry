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

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.CCompatWithGroupsTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.enterprise.inject.Typed;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.apicurio.registry.noprofile.ccompat.rest.CCompatTestConstants.SCHEMA_SIMPLE_WRAPPED;
import static io.restassured.RestAssured.given;

/**
 * Tests that the REST API exposed at endpoint "/ccompat/v7" follows the
 * <a href="https://docs.confluent.io/7.2.1/schema-registry/develop/api.html">Confluent API specification</a>,
 * unless otherwise stated.
 *
 * @author Carles Arnal
 */
@QuarkusTest
@Typed(ConfluentCompatApiWithGroupsTest.class)
@TestProfile(CCompatWithGroupsTestProfile.class)
public class ConfluentCompatApiWithGroupsTest extends AbstractResourceTestBase {

    public static final String BASE_PATH = "/ccompat/v7";

    private static String toSubject(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }

    @NotNull
    public String getBasePath() {
        return BASE_PATH;
    }

    @Test
    public void testCreateSubject() throws Exception {
        final String groupId = "g_testCreateSubject";
        final String artifactId = "a_testCreateSubject";
        final String subject = toSubject(groupId, artifactId);

        // POST
        ValidatableResponse res = given().when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST).body(SCHEMA_SIMPLE_WRAPPED)
                .post(getBasePath() + "/subjects/{subject}/versions", subject).then().statusCode(200)
                .body("id", Matchers.allOf(Matchers.isA(Integer.class), Matchers.greaterThanOrEqualTo(0)));
        /* int id = */
        res.extract().jsonPath().getInt("id");

        // Verify with ccompat
        given().when().get(getBasePath() + "/subjects/").then().body(Matchers.containsString(subject));
        // Verify with core
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        Assertions.assertNotNull(amd);

        // Invalid subject format
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(SCHEMA_SIMPLE_WRAPPED)
                .post(getBasePath() + "/subjects/{subject}/versions", "invalid-subject-format").then()
                .statusCode(400);
    }

    @Test
    public void testDeleteSubject() throws Exception {
        final String groupId = "g_testDeleteSubject";
        final String artifactId = "a_testDeleteSubject";
        final String subject = toSubject(groupId, artifactId);

        // Create using core API
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Verify using ccompat
        given().when().get(getBasePath() + "/subjects/").then().body(Matchers.containsString(subject));

        // DELETE
        given().when().contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .delete(getBasePath() + "/subjects/{subject}", subject).then().statusCode(200)
                .body(Matchers.anything());

        // Verify with ccompat
        given().when().get(getBasePath() + "/subjects/").then()
                .body(Matchers.not(Matchers.containsString(subject)));
        // Verify with core
        VersionMetaData versionMetaData = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1").get();
        Assertions.assertNotNull(versionMetaData);
        Assertions.assertEquals(VersionState.DISABLED, versionMetaData.getState());
    }

    @Test
    public void testListSubjects() throws Exception {
        final String groupId1 = "g_testListSubjects1";
        final String groupId2 = "g_testListSubjects2";
        final String artifactId1 = "a_testListSubjects1";
        final String artifactId2 = "a_testListSubjects2";
        final String artifactId3 = "a_testListSubjects3";
        final String subject1_1 = toSubject(groupId1, artifactId1);
        final String subject1_2 = toSubject(groupId1, artifactId2);
        final String subject1_3 = toSubject(groupId1, artifactId3);
        final String subject2_1 = toSubject(groupId2, artifactId1);
        final String subject2_2 = toSubject(groupId2, artifactId2);

        // Create using core API
        // Group 1
        clientV3.groups().byGroupId(groupId1).artifacts().post(TestUtils.clientCreateArtifact(artifactId1,
                ArtifactType.AVRO, SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON));
        clientV3.groups().byGroupId(groupId1).artifacts().post(TestUtils.clientCreateArtifact(artifactId2,
                ArtifactType.AVRO, SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON));
        clientV3.groups().byGroupId(groupId1).artifacts().post(TestUtils.clientCreateArtifact(artifactId3,
                ArtifactType.AVRO, SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON));
        // Group 2
        clientV3.groups().byGroupId(groupId2).artifacts().post(TestUtils.clientCreateArtifact(artifactId1,
                ArtifactType.AVRO, SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON));
        clientV3.groups().byGroupId(groupId2).artifacts().post(TestUtils.clientCreateArtifact(artifactId2,
                ArtifactType.AVRO, SCHEMA_SIMPLE_WRAPPED, ContentTypes.JSON));

        // Verify with ccompat
        List<?> subjects = given().when().get(getBasePath() + "/subjects/").then().statusCode(200).extract()
                .body().as(List.class);

        Assertions.assertTrue(subjects.contains(subject1_1));
        Assertions.assertTrue(subjects.contains(subject1_2));
        Assertions.assertTrue(subjects.contains(subject1_3));
        Assertions.assertTrue(subjects.contains(subject2_1));
        Assertions.assertTrue(subjects.contains(subject2_2));

        // Get versions
        List<?> versions = given().when().get(getBasePath() + "/subjects/{subject}/versions", subject1_1)
                .then().statusCode(200).extract().body().as(List.class);
        Assertions.assertEquals(1, versions.size());
        Assertions.assertTrue(versions.contains(1));

        // Get one version
        given().when().get(getBasePath() + "/subjects/{subject}/versions/{version}", subject1_1, "1").then()
                .statusCode(200).body(Matchers.anything());
        given().when().get(getBasePath() + "/subjects/{subject}/versions/{version}/schema", subject1_1, "1")
                .then().statusCode(200).body(Matchers.anything());
        given().when()
                .get(getBasePath() + "/subjects/{subject}/versions/{version}/referencedby", subject1_1, "1")
                .then().statusCode(200).body(Matchers.anything());
    }

}