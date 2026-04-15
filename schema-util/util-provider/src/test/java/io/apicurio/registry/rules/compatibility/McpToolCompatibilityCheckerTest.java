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
 * Tests for McpToolCompatibilityChecker.
 */
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
    void testBackwardCompatibleAddingOptionalProperty() {
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

        assertTrue(result.isCompatible(),
                "Adding an optional property should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingProperty() {
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

        assertFalse(result.isCompatible(),
                "Removing a property should be backward incompatible");
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
    void testBackwardIncompatibleRemovingRequiredParam() {
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

        assertFalse(result.isCompatible(),
                "Removing a required parameter should be backward incompatible");
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
                    "version": "2.0.0",
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
                "Changing name, description, and version should be compatible");
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
                        "category": "database",
                        "provider": "internal"
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
                        "category": "search",
                        "provider": "external",
                        "requiresAuth": true
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
}
