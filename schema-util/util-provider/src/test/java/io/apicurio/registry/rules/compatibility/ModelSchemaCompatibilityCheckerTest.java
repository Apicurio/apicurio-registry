package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ModelSchemaCompatibilityChecker.
 */
class ModelSchemaCompatibilityCheckerTest {

    private ModelSchemaCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ModelSchemaCompatibilityChecker();
    }

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private static void assertHasDifferenceWithContext(CompatibilityExecutionResult result,
            String context) {
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> context.equals(d.asRuleViolation().getContext())),
                "Expected a difference with context '" + context + "'");
    }

    private static String modelSchema(String input, String output) {
        return """
                {
                    "modelId": "test-model",
                    "input": %s,
                    "output": %s
                }
                """.formatted(input, output);
    }

    private static final String INPUT_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "prompt": { "type": "string" },
                    "temperature": { "type": "number" }
                },
                "required": ["prompt"]
            }
            """;

    private static final String OUTPUT_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "text": { "type": "string" },
                    "usage": { "type": "object" }
                }
            }
            """;

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA)),
                Map.of());
        assertTrue(result.isCompatible());
    }

    @Test
    void testBackwardCompatibleAddingOptionalInputField() {
        String proposedInput = """
                {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" },
                        "temperature": { "type": "number" },
                        "maxTokens": { "type": "integer" }
                    },
                    "required": ["prompt"]
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(proposedInput, OUTPUT_SCHEMA)),
                Map.of());
        assertTrue(result.isCompatible(), "Adding an optional input field should be compatible");
    }

    @Test
    void testIncompatibleAddingRequiredInputField() {
        String proposedInput = """
                {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" },
                        "temperature": { "type": "number" }
                    },
                    "required": ["prompt", "temperature"]
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(proposedInput, OUTPUT_SCHEMA)),
                Map.of());
        assertFalse(result.isCompatible(), "Adding a required input field should be incompatible");
        assertHasDifferenceWithContext(result, "/input/required");
    }

    @Test
    void testIncompatibleRemovingInputProperty() {
        String proposedInput = """
                {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" }
                    },
                    "required": ["prompt"]
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(proposedInput, OUTPUT_SCHEMA)),
                Map.of());
        assertFalse(result.isCompatible(), "Removing an input property should be incompatible");
        assertHasDifferenceWithContext(result, "/input/properties");
    }

    @Test
    void testIncompatibleChangingInputPropertyType() {
        String proposedInput = """
                {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" },
                        "temperature": { "type": "string" }
                    },
                    "required": ["prompt"]
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(proposedInput, OUTPUT_SCHEMA)),
                Map.of());
        assertFalse(result.isCompatible(), "Changing input property type should be incompatible");
        assertHasDifferenceWithContext(result, "/input/properties");
    }

    @Test
    void testIncompatibleRemovingInputSchema() {
        String proposed = """
                {
                    "modelId": "test-model",
                    "output": %s
                }
                """.formatted(OUTPUT_SCHEMA);
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing input schema should be incompatible");
        assertHasDifferenceWithContext(result, "/input");
    }

    @Test
    void testIncompatibleRemovingOutputProperty() {
        String proposedOutput = """
                {
                    "type": "object",
                    "properties": {
                        "text": { "type": "string" }
                    }
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(INPUT_SCHEMA, proposedOutput)),
                Map.of());
        assertFalse(result.isCompatible(), "Removing an output property should be incompatible");
        assertHasDifferenceWithContext(result, "/output/properties");
    }

    @Test
    void testIncompatibleRemovingOutputSchema() {
        String proposed = """
                {
                    "modelId": "test-model",
                    "input": %s
                }
                """.formatted(INPUT_SCHEMA);
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing output schema should be incompatible");
        assertHasDifferenceWithContext(result, "/output");
    }

    @Test
    void testBackwardCompatibleAddingOutputProperty() {
        String proposedOutput = """
                {
                    "type": "object",
                    "properties": {
                        "text": { "type": "string" },
                        "usage": { "type": "object" },
                        "finishReason": { "type": "string" }
                    }
                }
                """;
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(modelSchema(INPUT_SCHEMA, OUTPUT_SCHEMA))),
                create(modelSchema(INPUT_SCHEMA, proposedOutput)),
                Map.of());
        assertTrue(result.isCompatible(), "Adding an output property should be compatible");
    }
}
