/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
@TestProfile(GitopsTestProfile.class)
public class GitOpsStatusTest {

    @Test
    void statusEndpointReturnsInitializingBeforeFirstLoad() {
        given()
                .when()
                .get("/apis/registry/v3/admin/gitops/status")
                .then()
                .statusCode(200)
                .body("syncState", notNullValue());
    }

    @Test
    void statusEndpointReturnsIdleAfterDataLoad() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/smoke01");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"))
                    .body("sources", aMapWithSize(1))
                    .body("lastSuccessfulSync", notNullValue())
                    .body("groupCount", greaterThanOrEqualTo(1))
                    .body("artifactCount", greaterThanOrEqualTo(1))
                    .body("versionCount", greaterThanOrEqualTo(1))
                    .body("errors", empty());
        });
    }

    @Test
    void statusEndpointShowsErrorOnInvalidData() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"));
        });

        testRepository.load("git/invalid-content-ref");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("ERROR"));
        });
    }

    @Test
    void syncEndpointTriggersRefresh() throws Exception {
        given()
                .when()
                .post("/apis/registry/v3/admin/gitops/sync")
                .then()
                .statusCode(204);
    }

    @Test
    void statusEndpointUpdatesMarkerAfterNewCommit() throws Exception {
        var testRepository = GitTestRepositoryManager.getTestRepository();

        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            given()
                    .when()
                    .get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .statusCode(200)
                    .body("syncState", equalTo("IDLE"))
                    .body("sources", aMapWithSize(1));
        });

        Map<String, String> initialSources = RestAssured.get("/apis/registry/v3/admin/gitops/status")
                .then()
                .extract()
                .path("sources");

        testRepository.load("git/smoke02");
        given().post("/apis/registry/v3/admin/gitops/sync");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Map<String, String> currentSources = RestAssured.get("/apis/registry/v3/admin/gitops/status")
                    .then()
                    .extract()
                    .path("sources");
            assertNotEquals(initialSources, currentSources);
        });
    }
}
