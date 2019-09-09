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

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rules.validation.ValidationLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;

/** 
 * Tests registry via its jax-rs interface.  This test performs more realistic
 * usage scenarios than the more unit-test focused {@link RulesResourceTest}
 * and {@link ArtifactsResourceTest}.
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class FullApiTest extends AbstractResourceTest {
    
    @Test
    public void testGlobalRuleApplication() {
        ArtifactType artifactType = ArtifactType.OPENAPI;
        String artifactContent = resourceToString("openapi-invalid-syntax.json");

        // First, create an artifact without the rule installed.  Should work.
        createArtifact("testGlobalRuleApplication/API", artifactType, artifactContent);
        
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDATION);
        rule.setConfig(ValidationLevel.SYNTAX_ONLY.name());
        given()
            .when().contentType(RestConstants.JSON).body(rule).post("/rules")
            .then()
            .statusCode(204)
            .body(anything());

        // Try to create an artifact that is not valid - now it should fail.
        String artifactId = "testGlobalRuleApplication/InvalidAPI";
        given()
            .when()
                .contentType(RestConstants.JSON)
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                .body(artifactContent)
                .post("/artifacts")
            .then()
                .statusCode(400)
                .body("code", equalTo(400))
                .body("message", equalTo("Syntax violation for OpenAPI artifact."));

    }

}
