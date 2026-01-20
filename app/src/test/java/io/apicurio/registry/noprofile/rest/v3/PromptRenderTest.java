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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for the prompt template rendering endpoint.
 * POST /groups/{groupId}/artifacts/{artifactId}/versions/{version}/render
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
    public void testRenderMissingVariablePreserved() throws Exception {
        String group = "missing-var-" + UUID.randomUUID().toString();
        String artifactId = "partial-prompt";

        String content = createPromptContent("Hello {{name}}, your role is {{role}}.");

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of("name", "Bob")
                // 'role' not provided
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
                .body("rendered", equalTo("Hello Bob, your role is {{role}}."));
    }

    @Test
    public void testRenderWithValidation() throws Exception {
        String group = "validation-" + UUID.randomUUID().toString();
        String artifactId = "validated-prompt";

        String variablesSchema = """
            {
              "name": { "type": "string", "required": true },
              "count": { "type": "integer", "minimum": 1, "maximum": 100 },
              "style": { "type": "string", "enum": ["formal", "casual", "technical"] }
            }
            """;

        String content = createPromptWithSchema(
                "Hello {{name}}, please provide {{count}} items in {{style}} style.",
                variablesSchema
        );

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        // Valid request
        Map<String, Object> validRequest = Map.of(
                "variables", Map.of(
                        "name", "Test User",
                        "count", 50,
                        "style", "formal"
                )
        );

        given().when()
                .contentType(CT_JSON)
                .pathParam("groupId", group)
                .pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "branch=latest")
                .body(validRequest)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/render")
                .then()
                .statusCode(200)
                .body("rendered", containsString("Test User"))
                .body("rendered", containsString("50"))
                .body("rendered", containsString("formal"))
                .body("validationErrors", hasSize(0));
    }

    @Test
    public void testRenderValidationRequiredMissing() throws Exception {
        String group = "required-" + UUID.randomUUID().toString();
        String artifactId = "required-prompt";

        String variablesSchema = """
            {
              "name": { "type": "string", "required": true }
            }
            """;

        String content = createPromptWithSchema("Hello {{name}}!", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        // Missing required 'name'
        Map<String, Object> request = Map.of(
                "variables", Map.of()
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
                .body("validationErrors", hasSize(1))
                .body("validationErrors[0].variableName", equalTo("name"))
                .body("validationErrors[0].message", containsString("Required"));
    }

    @Test
    public void testRenderValidationTypeMismatch() throws Exception {
        String group = "type-" + UUID.randomUUID().toString();
        String artifactId = "typed-prompt";

        String variablesSchema = """
            {
              "count": { "type": "integer" }
            }
            """;

        String content = createPromptWithSchema("Count: {{count}}", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        // Wrong type: string instead of integer
        Map<String, Object> request = Map.of(
                "variables", Map.of("count", "not-a-number")
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
                .body("validationErrors", hasSize(1))
                .body("validationErrors[0].variableName", equalTo("count"))
                .body("validationErrors[0].expectedType", equalTo("integer"))
                .body("validationErrors[0].actualType", equalTo("string"));
    }

    @Test
    public void testRenderValidationEnumInvalid() throws Exception {
        String group = "enum-" + UUID.randomUUID().toString();
        String artifactId = "enum-prompt";

        String variablesSchema = """
            {
              "style": { "type": "string", "enum": ["a", "b", "c"] }
            }
            """;

        String content = createPromptWithSchema("Style: {{style}}", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        // Invalid enum value
        Map<String, Object> request = Map.of(
                "variables", Map.of("style", "invalid")
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
                .body("validationErrors", hasSize(1))
                .body("validationErrors[0].variableName", equalTo("style"))
                .body("validationErrors[0].message", containsString("allowed values"));
    }

    @Test
    public void testRenderValidationRangeBelowMinimum() throws Exception {
        String group = "range-min-" + UUID.randomUUID().toString();
        String artifactId = "range-prompt";

        String variablesSchema = """
            {
              "count": { "type": "integer", "minimum": 10 }
            }
            """;

        String content = createPromptWithSchema("Count: {{count}}", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of("count", 5)
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
                .body("validationErrors", hasSize(1))
                .body("validationErrors[0].variableName", equalTo("count"))
                .body("validationErrors[0].message", containsString("less than minimum"));
    }

    @Test
    public void testRenderValidationRangeAboveMaximum() throws Exception {
        String group = "range-max-" + UUID.randomUUID().toString();
        String artifactId = "range-prompt";

        String variablesSchema = """
            {
              "count": { "type": "integer", "maximum": 100 }
            }
            """;

        String content = createPromptWithSchema("Count: {{count}}", variablesSchema);

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of("count", 200)
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
                .body("validationErrors", hasSize(1))
                .body("validationErrors[0].variableName", equalTo("count"))
                .body("validationErrors[0].message", containsString("greater than maximum"));
    }

    @Test
    public void testRenderMultipleValidationErrors() throws Exception {
        String group = "multi-error-" + UUID.randomUUID().toString();
        String artifactId = "multi-error-prompt";

        String variablesSchema = """
            {
              "name": { "type": "string", "required": true },
              "count": { "type": "integer", "minimum": 0 },
              "style": { "type": "string", "enum": ["a", "b"] }
            }
            """;

        String content = createPromptWithSchema(
                "{{name}} wants {{count}} {{style}} items.",
                variablesSchema
        );

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of(
                        // name is missing (required)
                        "count", -5,      // below minimum
                        "style", "invalid" // not in enum
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
                .body("validationErrors", hasSize(3));
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

    @Test
    public void testRenderEmptyVariables() throws Exception {
        String group = "empty-vars-" + UUID.randomUUID().toString();
        String artifactId = "static-prompt";

        String content = createPromptContent("This is a static prompt with no variables.");

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of()
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
                .body("rendered", equalTo("This is a static prompt with no variables."));
    }

    @Test
    public void testRenderSpecialCharacters() throws Exception {
        String group = "special-chars-" + UUID.randomUUID().toString();
        String artifactId = "special-prompt";

        String content = createPromptContent("Query: SELECT * FROM {{table}} WHERE id = {{id}}");

        createArtifact(group, artifactId, PROMPT_TEMPLATE, content, ContentTypes.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
                "variables", Map.of(
                        "table", "users",
                        "id", 123
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
                .body("rendered", equalTo("Query: SELECT * FROM users WHERE id = 123"));
    }
}
