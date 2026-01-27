/*
 * Copyright 2024 Red Hat
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

package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for the prompt template rendering endpoint.
 * POST /groups/{groupId}/artifacts/{artifactId}/versions/{version}/render
 *
 * Note: Detailed validation logic tests are in PromptRenderingServiceTest.
 * These tests focus on API contract, response format, and error handling.
 */
@QuarkusTest
public class PromptRenderTest extends AbstractResourceTestBase {

    private static final String PROMPT_TEMPLATE = "PROMPT_TEMPLATE";

    /**
     * Creates a simple prompt template content.
     */
    private String createPromptContent(String template) {
        return String.format("""
            {
              "templateId": "test-prompt",
              "name": "Test Prompt",
              "template": "%s"
            }
            """, template.replace("\"", "\\\""));
    }

    /**
     * Creates a prompt template with variable schema.
     */
    private String createPromptWithSchema(String template, String variablesJson) {
        return String.format("""
            {
              "templateId": "test-prompt",
              "name": "Test Prompt",
              "template": "%s",
              "variables": %s
            }
            """, template.replace("\"", "\\\""), variablesJson);
    }

    @Test
    public void testBasicRender() throws Exception {
        String group = "render-test-" + UUID.randomUUID().toString();
        String artifactId = "greeting-prompt";

        String content = createPromptContent("Hello, {{name}}! Welcome to {{place}}.");

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = new HashMap<>();
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Alice");
        variables.put("place", "Wonderland");
        request.put("variables", variables);

        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(200)
                .body("rendered", equalTo("Hello, Alice! Welcome to Wonderland."))
                .body("groupId", equalTo(group))
                .body("artifactId", equalTo(artifactId))
                .body("version", notNullValue())
                .body("validationErrors", hasSize(0));
    }

    @Test
    public void testRenderWithVersionNumber() throws Exception {
        String group = "version-render-" + UUID.randomUUID().toString();
        String artifactId = "versioned-prompt";

        String content = createPromptContent("Message: {{message}}");

        var response = createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);
        String version = response.getVersion().getVersion();

        Map<String, Object> request = Map.of(
                "variables", Map.of("message", "Hello World")
        );

        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", version)
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(200)
                .body("rendered", equalTo("Message: Hello World"))
                .body("version", equalTo(version));
    }

    @Test
    public void testRenderValidationErrorResponseFormat() throws Exception {
        String group = "validation-format-" + UUID.randomUUID().toString();
        String artifactId = "validated-prompt";

        String variablesSchema = """
            {
              "name": { "type": "string", "required": true },
              "count": { "type": "integer", "minimum": 1 }
            }
            """;

        String content = createPromptWithSchema("Hello {{name}}, count: {{count}}", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        // Missing required 'name' and 'count' below minimum
        Map<String, Object> request = Map.of(
                "variables", Map.of("count", 0)
        );

        // Verify the API returns validation errors in the expected format
        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(200)
                .body("validationErrors", hasSize(2))
                .body("validationErrors[0].variableName", notNullValue())
                .body("validationErrors[0].message", notNullValue());
    }

    @Test
    public void testRenderYamlTemplate() throws Exception {
        String group = "yaml-render-" + UUID.randomUUID().toString();
        String artifactId = "yaml-prompt";

        String yamlContent = """
            templateId: yaml-test
            name: YAML Prompt
            template: "Hello {{name}}, your task is {{task}}."
            variables:
              name:
                type: string
              task:
                type: string
            """;

        createArtifact(group, artifactId, PROMPT_TEMPLATE, yamlContent, ContentTypes.APPLICATION_YAML);

        Map<String, Object> request = Map.of(
                "variables", Map.of(
                        "name", "Developer",
                        "task", "testing"
                )
        );

        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(200)
                .body("rendered", equalTo("Hello Developer, your task is testing."));
    }

    @Test
    public void testRenderNonPromptTemplateArtifact() throws Exception {
        String group = "wrong-type-" + UUID.randomUUID().toString();
        String artifactId = "openapi-artifact";

        String openApiContent = resourceToString("openapi-empty.json");

        // Create an OpenAPI artifact (not a PROMPT_TEMPLATE)
        createArtifact(group, artifactId, "OPENAPI", openApiContent, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of("name", "test")
        );

        // Should fail because artifact type is not PROMPT_TEMPLATE
        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(400);
    }

    @Test
    public void testRenderNonExistentArtifact() throws Exception {
        String group = "nonexistent-" + UUID.randomUUID().toString();
        String artifactId = "does-not-exist";

        Map<String, Object> request = Map.of(
                "variables", Map.of("name", "test")
        );

        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(request)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(404);
    }
}
