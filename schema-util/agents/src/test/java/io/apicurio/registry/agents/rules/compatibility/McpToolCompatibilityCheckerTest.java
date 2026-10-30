package io.apicurio.registry.agents.rules.compatibility;

import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static void assertHasDifferenceWithContext(CompatibilityExecutionResult result,
            String context) {
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> context.equals(d.asRuleViolation().getContext())),
                "Expected a difference with context '" + context + "'");
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

    // Each row: existing version, proposed version, expected BACKWARD verdict, and the context
    // of the reported difference when one is expected.
    static Stream<Arguments> backwardCases() {
        return Stream.of(
                Arguments.of("testBackwardIncompatibleTypingOpenProperty", """
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

                        """,
                        """
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

                        """,
                        false, null),
                Arguments.of("testBackwardCompatibleRemovingOpenPropertyConstraint", """
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

                        """,
                        """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }

                        """,
                        true, null),
                Arguments.of("testBackwardIncompatibleAddingRequiredParam", """
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

                        """,
                        """
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

                        """,
                        false, "/inputSchema/required/1"),
                Arguments.of("testBackwardCompatibleRemovingRequiredParam", """
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

                        """,
                        """
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

                        """,
                        true, null),
                Arguments.of("testBackwardIncompatibleChangingSchemaType", """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": { "type": "string" }
                        }
                    }
                }

                        """,
                        """
                {
                    "name": "test_tool",
                    "inputSchema": {
                        "type": "array",
                        "items": { "type": "string" }
                    }
                }

                        """,
                        false, "/inputSchema/type"),
                Arguments.of("testBackwardCompatibleChangingNameAndDescription", """
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

                        """,
                        """
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

                        """,
                        true, null),
                Arguments.of("testBackwardCompatibleChangingAnnotations", """
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

                        """,
                        """
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

                        """,
                        true, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backwardCases")
    void testBackwardCompatibility(String name, String existing, String proposed, boolean compatible,
            String expectedContext) {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD, List.of(createMcpTool(existing)),
                createMcpTool(proposed), Map.of());

        assertEquals(compatible, result.isCompatible(), name);
        if (expectedContext != null) {
            assertHasDifferenceWithContext(result, expectedContext);
        }
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
