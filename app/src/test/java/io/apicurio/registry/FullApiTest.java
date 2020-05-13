/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;

/** 
 * Tests registry via its jax-rs interface.  This test performs more realistic
 * usage scenarios than the more unit-test focused {@link RulesResourceTest}
 * and {@link ArtifactsResourceTest}.
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class FullApiTest extends AbstractResourceTestBase {
    
    @Test
    public void testGlobalRuleApplicationOpenAPI() {
        ArtifactType artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-invalid-syntax.json");

        // First, create an artifact without the rule installed.  Should work.
        createArtifact("testGlobalRuleApplicationOpenAPI/API", artifactType, artifactContent);
        
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        given()
            .when().contentType(CT_JSON).body(rule).post("/rules")
            .then()
            .statusCode(204)
            .body(anything());

        // Try to create an artifact that is not valid - now it should fail.
        String artifactId = "testGlobalRuleApplicationOpenAPI/InvalidAPI";
        given()
            .when()
                .contentType(CT_JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(400)
                .body("error_code", equalTo(400))
                .body("message", equalTo("Syntax violation for OpenAPI artifact."));

    }

    @Test
    public void testGlobalRuleApplicationProtobuf() {
        ArtifactType artifactType = ArtifactType.PROTOBUF;
        String artifactContent = resourceToString("protobuf-invalid-syntax.proto");

        // First, create an artifact without the rule installed.  Should work.
        createArtifact("testGlobalRuleApplicationProtobuf/API", artifactType, artifactContent);
        
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        given()
            .when().contentType(CT_JSON).body(rule).post("/rules")
            .then()
            .statusCode(204)
            .body(anything());

        // Try to create an artifact that is not valid - now it should fail.
        String artifactId = "testGlobalRuleApplicationProtobuf/InvalidAPI";
        given()
            .config(RestAssured.config().encoderConfig(new EncoderConfig().encodeContentTypeAs(CT_PROTO, ContentType.TEXT)))
            .when()
                .contentType(CT_PROTO)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(400)
                .body("error_code", equalTo(400))
                .body("message", equalTo("Syntax violation for Protobuf artifact."));

    }

}
