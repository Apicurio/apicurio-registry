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

package io.apicurio.registry.rest.v3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Tests that the GitOps admin endpoints return 409 when GitOps storage is not enabled.
 */
@QuarkusTest
public class AdminGitOpsEndpointTest {

    @Test
    void statusEndpointReturns409WhenGitOpsNotEnabled() {
        String body = given()
                .when()
                .get("/apis/registry/v3/admin/gitops/status")
                .then()
                .statusCode(409)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("GitOps storage is not enabled"),
                "Expected error about GitOps not enabled, got: " + body);
    }

    @Test
    void syncEndpointReturns409WhenGitOpsNotEnabled() {
        String body = given()
                .when()
                .post("/apis/registry/v3/admin/gitops/sync")
                .then()
                .statusCode(409)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("GitOps storage is not enabled"),
                "Expected error about GitOps not enabled, got: " + body);
    }
}
