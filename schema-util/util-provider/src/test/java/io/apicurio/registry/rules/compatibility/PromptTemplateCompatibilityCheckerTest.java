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
 * Tests for PromptTemplateCompatibilityChecker.
 */
class PromptTemplateCompatibilityCheckerTest {

    private PromptTemplateCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PromptTemplateCompatibilityChecker();
    }

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private static String template(String templateText, String variables, String extras) {
        return """
                {
                    "templateId": "test-template",
                    "template": "%s",
                    "variables": {%s}%s
                }
                """.formatted(templateText, variables, extras);
    }

    private static final String BASE_VARS = """
            "name": { "type": "string", "required": true },
            "style": { "type": "string", "required": false, "enum": ["formal", "casual", "technical"] }
            """;

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                create(template("Hello {{name}}", BASE_VARS, "")),
                Map.of());
        assertTrue(result.isCompatible());
    }

    @Test
    void testBackwardCompatibleAddingOptionalVariable() {
        String existing = template("Hello {{name}}", BASE_VARS, "");
        String proposedVars = BASE_VARS + """
                , "greeting": { "type": "string", "required": false }
                """;
        String proposed = template("{{greeting}} {{name}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertTrue(result.isCompatible(), "Adding an optional variable should be compatible");
    }

    @Test
    void testIncompatibleVariableTypeChange() {
        String existing = template("Hello {{name}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "integer", "required": true },
                "style": { "type": "string", "required": false, "enum": ["formal", "casual", "technical"] }
                """;
        String proposed = template("Hello {{name}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Changing variable type should be incompatible");
    }

    @Test
    void testIncompatibleVariableBecameRequired() {
        String existing = template("Hello {{name}} {{style}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "string", "required": true },
                "style": { "type": "string", "required": true, "enum": ["formal", "casual", "technical"] }
                """;
        String proposed = template("Hello {{name}} {{style}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Making variable required should be incompatible");
    }

    @Test
    void testIncompatibleEnumValueRemoved() {
        String existing = template("Hello {{name}} {{style}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "string", "required": true },
                "style": { "type": "string", "required": false, "enum": ["formal", "casual"] }
                """;
        String proposed = template("Hello {{name}} {{style}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing enum value should be incompatible");
    }

    @Test
    void testIncompatibleVariableRemovedButUsed() {
        String existing = template("Hello {{name}} {{style}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "string", "required": true }
                """;
        String proposed = template("Hello {{name}} {{style}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing variable still used in template should be incompatible");
    }

    @Test
    void testCompatibleVariableRemovedAndNotUsed() {
        String existing = template("Hello {{name}} {{style}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "string", "required": true }
                """;
        String proposed = template("Hello {{name}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertTrue(result.isCompatible(),
                "Removing variable not used in proposed template should be compatible");
    }

    @Test
    void testIncompatibleOutputSchemaPropertyRemoved() {
        String outputSchema = """
                , "outputSchema": {
                    "type": "object",
                    "properties": {
                        "result": { "type": "string" },
                        "confidence": { "type": "number" }
                    }
                }
                """;
        String proposedOutputSchema = """
                , "outputSchema": {
                    "type": "object",
                    "properties": {
                        "result": { "type": "string" }
                    }
                }
                """;
        String existing = template("Hello {{name}}", BASE_VARS, outputSchema);
        String proposed = template("Hello {{name}}", BASE_VARS, proposedOutputSchema);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing output schema property should be incompatible");
    }

    @Test
    void testIncompatibleOutputSchemaRemoved() {
        String outputSchema = """
                , "outputSchema": {
                    "type": "object",
                    "properties": {
                        "result": { "type": "string" }
                    }
                }
                """;
        String existing = template("Hello {{name}}", BASE_VARS, outputSchema);
        String proposed = template("Hello {{name}}", BASE_VARS, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertFalse(result.isCompatible(), "Removing output schema should be incompatible");
    }

    @Test
    void testBackwardCompatibleAddingEnumValue() {
        String existing = template("Hello {{name}} {{style}}", BASE_VARS, "");
        String proposedVars = """
                "name": { "type": "string", "required": true },
                "style": { "type": "string", "required": false, "enum": ["formal", "casual", "technical", "creative"] }
                """;
        String proposed = template("Hello {{name}} {{style}}", proposedVars, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(create(existing)),
                create(proposed),
                Map.of());
        assertTrue(result.isCompatible(), "Adding an enum value should be compatible");
    }
}
