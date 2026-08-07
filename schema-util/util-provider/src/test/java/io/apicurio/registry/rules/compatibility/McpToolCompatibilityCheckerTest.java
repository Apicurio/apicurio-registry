package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.McpToolCompatibilityChecker;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for McpToolCompatibilityChecker.
 */
@SuppressWarnings("java:S5976")
class McpToolCompatibilityCheckerTest {

    private McpToolCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new McpToolCompatibilityChecker();
    }

    private TypedContent createMcpTool(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, Collections.emptyList(), createMcpTool(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Should be compatible when no existing artifacts");
    }

    @Test
    void testBackwardIncompatibleAddingOptionalPropertyToOpenSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "limit": { "type": "integer" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Adding a typed optional property to an open schema (additionalProperties defaults to true) is backward incompatible because it narrows previously accepted values");
    }

    @Test
    void testBackwardCompatibleAddingOptionalPropertyToClosedSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"],
                        "additionalProperties": false
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "limit": { "type": "integer" }
                        },
                        "required": ["query"],
                        "additionalProperties": false
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Adding an optional property to a closed schema (additionalProperties: false) is backward compatible");
    }

    @Test
    void testBackwardCompatibleRemovingProperty() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "limit": { "type": "integer" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        // False positive in old checker; JsonSchemaDiffLibrary correctly identifies as compatible (property removal without additionalProperties: false = widening)
        assertTrue(result.isCompatible(),
                "Removing a property when additionalProperties is not false is backward compatible");
    }

    @Test
    void testBackwardCompatibleRemovingInputSchemaType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        // False positive in old checker; JsonSchemaDiffLibrary correctly identifies as compatible (type removal = widening)
        assertTrue(result.isCompatible(),
                "Removing inputSchema type makes schema accept anything (widening) and is compatible");
    }

    @Test
    void testBackwardCompatibleRemovingPropertyType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": {}
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        // False positive in old checker; JsonSchemaDiffLibrary correctly identifies as compatible (removing property type field widens accepted values)
        assertTrue(result.isCompatible(),
                "Removing a property's type field widens accepted values and is compatible");
    }

    @Test
    void testBackwardIncompatibleAddingRequiredParam() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "format": { "type": "string" }
                        },
                        "required": ["query", "format"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Adding a required parameter should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleRemovingRequiredParam() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "format": { "type": "string" }
                        },
                        "required": ["query", "format"]
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" },
                            "format": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        // False positive in old checker; JsonSchemaDiffLibrary correctly identifies as compatible (removing required parameter makes it optional)
        assertTrue(result.isCompatible(),
                "Removing a required parameter makes it optional and is backward compatible");
    }

    @Test
    void testBackwardIncompatibleChangingSchemaType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "array",
                        "items": { "type": "string" }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Changing inputSchema type should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleChangingNameAndDescription() {
        String existing = """
                {
                    "name": "test_tool",
                    "description": "Old description",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "renamed_tool",
                    "description": "New description",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Changing name and description should be compatible");
    }

    @Test
    void testBackwardCompatibleChangingAnnotations() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    },
                    "annotations": {
                        "audience": ["user"],
                        "priority": 0.5
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    },
                    "annotations": {
                        "audience": ["user", "assistant"],
                        "priority": 0.9
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Changing annotations should be compatible");
    }

    @Test
    void testFullCompatibilityBothDirections() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        // Same schema, just description change — should be FULL compatible
        String proposed = """
                {
                    "name": "test_tool",
                    "description": "Updated description",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        },
                        "required": ["query"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FULL, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(), "Identical schema with description change should be fully compatible");
    }

    @Test
    void testBackwardCompatibleNonObjectInputSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": true
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Adding a structured inputSchema where none existed before should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleRemovingInputSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool"
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Removing inputSchema entirely is widening (accepts any input) and should be backward compatible");
    }

    @Test
    void testBackwardCompatibleUnchangedInputSchemaType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Unchanged inputSchema type should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleChangingPropertyType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "integer" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Changing a property type from integer to string should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleUnchangedPropertyType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "integer" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "integer" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Unchanged property type should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleChangingPropertyTypeToUnionArray() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "integer" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": ["string", "null"] }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Changing a property's type to a union array should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleReorderedUnionTypeArray() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "val": { "type": ["string", "number"] }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "val": { "type": ["number", "string"] }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Reordering members in a union type array should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleAddingTypeToUntypedProperty() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": {}
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "age": { "type": "integer" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(),
                "Adding a type constraint to a previously untyped property should be backward incompatible");
    }

    @Test
    void testBackwardCompatibleSingleElementUnionType() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": ["string"] }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(),
                "Single element union type ['string'] should be equivalent to scalar 'string'");
    }

    @Test
    void testUnsupportedSchemaDraftInExisting() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "$schema": "https://json-schema.org/draft/2020-12/schema",
                        "type": "object"
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object"
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(), "Unsupported draft in existing schema should be incompatible");
        assertTrue(result.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription().contains("Unsupported JSON Schema draft in existing inputSchema"));
    }

    @Test
    void testUnsupportedSchemaDraftInProposed() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object"
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "$schema": "https://json-schema.org/draft/2020-12/schema",
                        "type": "object"
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(), "Unsupported draft in proposed schema should be incompatible");
        assertTrue(result.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription().contains("Unsupported JSON Schema draft in proposed inputSchema"));
    }

    @Test
    void testSupportedSchemaDraft() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "$schema": "http://json-schema.org/draft-07/schema#",
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertTrue(result.isCompatible(), "Supported Draft-07 schema should be compatible");
    }

    @Test
    void testFailedToParseExistingInputSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": 123
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object"
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(), "Invalid existing schema should fail compatibility");
        assertTrue(result.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription().startsWith("Failed to parse existing inputSchema"));
    }

    @Test
    void testFailedToParseProposedInputSchema() {
        String existing = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object"
                    }
                }
                """;

        String proposed = """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": 123
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertFalse(result.isCompatible(), "Invalid proposed schema should fail compatibility");
        assertTrue(result.getIncompatibleDifferences().iterator().next().asRuleViolation().getDescription().startsWith("Failed to parse proposed inputSchema"));
    }
}
