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

package io.apicurio.registry.services;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.RenderPromptResponse;
import io.apicurio.registry.rest.v3.beans.RenderValidationError;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for PromptRenderingService.
 */
@QuarkusTest
public class PromptRenderingServiceTest {

    @Inject
    PromptRenderingService renderingService;

    // ===== Basic Variable Substitution Tests =====

    @Test
    public void testBasicVariableSubstitution() {
        String yamlContent = """
            templateId: greeting
            name: Greeting Template
            template: "Hello, {{name}}! Welcome to {{place}}."
            variables:
              name:
                type: string
              place:
                type: string
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Alice");
        variables.put("place", "Wonderland");

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "greeting", "1.0");

        Assertions.assertEquals("Hello, Alice! Welcome to Wonderland.", response.getRendered());
        Assertions.assertEquals("default", response.getGroupId());
        Assertions.assertEquals("greeting", response.getArtifactId());
        Assertions.assertEquals("1.0", response.getVersion());
        Assertions.assertTrue(response.getValidationErrors().isEmpty());
    }

    @Test
    public void testJsonTemplateFormat() {
        String jsonContent = """
            {
              "templateId": "qa-prompt",
              "name": "Q&A Prompt",
              "template": "Question: {{question}}\\nContext: {{context}}",
              "variables": {
                "question": { "type": "string" },
                "context": { "type": "string" }
              }
            }
            """;

        ContentHandle content = ContentHandle.create(jsonContent);
        Map<String, Object> variables = Map.of(
                "question", "What is AI?",
                "context", "Artificial Intelligence overview"
        );

        RenderPromptResponse response = renderingService.render(content, variables,
                "ai-group", "qa-prompt", "2.0");

        Assertions.assertEquals("Question: What is AI?\nContext: Artificial Intelligence overview",
                response.getRendered());
    }

    @Test
    public void testMissingVariablePreservedAsPlaceholder() {
        String yamlContent = """
            templateId: partial
            template: "Hello, {{name}}! Your role is {{role}}."
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("name", "Bob");

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "partial", "1.0");

        // Missing {{role}} should be preserved
        Assertions.assertEquals("Hello, Bob! Your role is {{role}}.", response.getRendered());
    }

    @Test
    public void testNoVariables() {
        String yamlContent = """
            templateId: static
            template: "This is a static prompt with no variables."
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = new HashMap<>();

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "static", "1.0");

        Assertions.assertEquals("This is a static prompt with no variables.", response.getRendered());
    }

    // ===== Variable Validation Tests =====

    @Test
    public void testRequiredVariableMissing() {
        String yamlContent = """
            templateId: required-test
            template: "Hello, {{name}}!"
            variables:
              name:
                type: string
                required: true
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = new HashMap<>(); // Missing required 'name'

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "required-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("name", errors.get(0).getVariableName());
        Assertions.assertTrue(errors.get(0).getMessage().contains("Required"));
    }

    @Test
    public void testTypeMismatchString() {
        String yamlContent = """
            templateId: type-test
            template: "Value: {{value}}"
            variables:
              value:
                type: string
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("value", 12345); // Integer instead of String

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "type-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("value", errors.get(0).getVariableName());
        Assertions.assertEquals("string", errors.get(0).getExpectedType());
        Assertions.assertEquals("integer", errors.get(0).getActualType());
    }

    @Test
    public void testTypeMismatchInteger() {
        String yamlContent = """
            templateId: type-test
            template: "Count: {{count}}"
            variables:
              count:
                type: integer
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("count", "not-a-number"); // String instead of Integer

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "type-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("count", errors.get(0).getVariableName());
        Assertions.assertEquals("integer", errors.get(0).getExpectedType());
    }

    @Test
    public void testValidIntegerType() {
        String yamlContent = """
            templateId: int-test
            template: "Count: {{count}}"
            variables:
              count:
                type: integer
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("count", 42);

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "int-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Count: 42", response.getRendered());
    }

    @Test
    public void testValidNumberType() {
        String yamlContent = """
            templateId: num-test
            template: "Temperature: {{temp}}"
            variables:
              temp:
                type: number
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("temp", 98.6);

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "num-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Temperature: 98.6", response.getRendered());
    }

    @Test
    public void testValidBooleanType() {
        String yamlContent = """
            templateId: bool-test
            template: "Active: {{active}}"
            variables:
              active:
                type: boolean
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("active", true);

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "bool-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Active: true", response.getRendered());
    }

    @Test
    public void testValidArrayType() {
        String yamlContent = """
            templateId: array-test
            template: "Items: {{items}}"
            variables:
              items:
                type: array
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("items", List.of("apple", "banana", "cherry"));

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "array-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        // Arrays should be serialized as JSON
        Assertions.assertTrue(response.getRendered().contains("["));
    }

    // ===== Enum Validation Tests =====

    @Test
    public void testEnumValidValue() {
        String yamlContent = """
            templateId: enum-test
            template: "Style: {{style}}"
            variables:
              style:
                type: string
                enum:
                  - concise
                  - detailed
                  - bullet
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("style", "concise");

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "enum-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Style: concise", response.getRendered());
    }

    @Test
    public void testEnumInvalidValue() {
        String yamlContent = """
            templateId: enum-test
            template: "Style: {{style}}"
            variables:
              style:
                type: string
                enum:
                  - concise
                  - detailed
                  - bullet
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("style", "verbose"); // Not in enum

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "enum-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("style", errors.get(0).getVariableName());
        Assertions.assertTrue(errors.get(0).getMessage().contains("verbose"));
        Assertions.assertTrue(errors.get(0).getMessage().contains("allowed values"));
    }

    // ===== Range Validation Tests =====

    @Test
    public void testRangeValidValue() {
        String yamlContent = """
            templateId: range-test
            template: "Max words: {{max_words}}"
            variables:
              max_words:
                type: integer
                minimum: 10
                maximum: 1000
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("max_words", 500);

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "range-test", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Max words: 500", response.getRendered());
    }

    @Test
    public void testRangeBelowMinimum() {
        String yamlContent = """
            templateId: range-test
            template: "Max words: {{max_words}}"
            variables:
              max_words:
                type: integer
                minimum: 10
                maximum: 1000
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("max_words", 5); // Below minimum

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "range-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("max_words", errors.get(0).getVariableName());
        Assertions.assertTrue(errors.get(0).getMessage().contains("less than minimum"));
    }

    @Test
    public void testRangeAboveMaximum() {
        String yamlContent = """
            templateId: range-test
            template: "Max words: {{max_words}}"
            variables:
              max_words:
                type: integer
                minimum: 10
                maximum: 1000
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("max_words", 2000); // Above maximum

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "range-test", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals("max_words", errors.get(0).getVariableName());
        Assertions.assertTrue(errors.get(0).getMessage().contains("greater than maximum"));
    }

    // ===== Edge Cases =====

    @Test
    public void testMultipleValidationErrors() {
        String yamlContent = """
            templateId: multi-error
            template: "Name: {{name}}, Age: {{age}}, Style: {{style}}"
            variables:
              name:
                type: string
                required: true
              age:
                type: integer
                minimum: 0
                maximum: 150
              style:
                type: string
                enum:
                  - formal
                  - casual
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of(
                "age", -5,        // Below minimum
                "style", "weird"  // Not in enum
        );
        // name is missing (required)

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "multi-error", "1.0");

        List<RenderValidationError> errors = response.getValidationErrors();
        Assertions.assertEquals(3, errors.size());
    }

    @Test
    public void testSpecialCharactersInTemplate() {
        String yamlContent = """
            templateId: special
            template: "Query: SELECT * FROM {{table}} WHERE id = {{id}}"
            variables:
              table:
                type: string
              id:
                type: integer
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("table", "users", "id", 123);

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "special", "1.0");

        Assertions.assertEquals("Query: SELECT * FROM users WHERE id = 123", response.getRendered());
    }

    @Test
    public void testWhitespaceInVariableName() {
        String yamlContent = """
            templateId: whitespace
            template: "Hello, {{ name }}!"
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("name", "World");

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "whitespace", "1.0");

        Assertions.assertEquals("Hello, World!", response.getRendered());
    }

    @Test
    public void testMultilineTemplate() {
        String yamlContent = """
            templateId: multiline
            template: |
              You are an AI assistant.
              User Question: {{question}}

              Please provide a {{style}} answer.
            variables:
              question:
                type: string
              style:
                type: string
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of(
                "question", "What is machine learning?",
                "style", "detailed"
        );

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "multiline", "1.0");

        Assertions.assertTrue(response.getRendered().contains("What is machine learning?"));
        Assertions.assertTrue(response.getRendered().contains("detailed"));
    }

    @Test
    public void testNoSchemaValidation() {
        // When no 'variables' schema is defined, no validation should occur
        String yamlContent = """
            templateId: no-schema
            template: "Hello, {{name}}!"
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("name", 12345); // Any type should work

        RenderPromptResponse response = renderingService.render(content, variables,
                "default", "no-schema", "1.0");

        Assertions.assertTrue(response.getValidationErrors().isEmpty());
        Assertions.assertEquals("Hello, 12345!", response.getRendered());
    }

    // ===== Error Cases =====

    @Test
    public void testMissingTemplateField() {
        String yamlContent = """
            templateId: no-template
            name: Missing Template
            variables:
              name:
                type: string
            """;

        ContentHandle content = ContentHandle.create(yamlContent);
        Map<String, Object> variables = Map.of("name", "Test");

        Assertions.assertThrows(RuntimeException.class, () -> {
            renderingService.render(content, variables, "default", "no-template", "1.0");
        });
    }

    @Test
    public void testInvalidYamlContent() {
        String invalidContent = "{{{{not valid yaml or json}}}}";

        ContentHandle content = ContentHandle.create(invalidContent);
        Map<String, Object> variables = Map.of();

        Assertions.assertThrows(RuntimeException.class, () -> {
            renderingService.render(content, variables, "default", "invalid", "1.0");
        });
    }
}
