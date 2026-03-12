package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentCardCompatibilityChecker.
 */
class AgentCardCompatibilityCheckerTest {

    private AgentCardCompatibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AgentCardCompatibilityChecker();
    }

    private TypedContent createAgentCard(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        String proposed = """
                {
                    "name": "TestAgent",
                    "url": "https://example.com/agent"
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Should be compatible when no existing artifacts");
    }

    @Test
    void testBackwardCompatibleAddingSkill() {
        String existing = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"}
                    ]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"},
                        {"id": "skill2", "name": "Skill 2"}
                    ]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding a skill should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingSkill() {
        String existing = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"},
                        {"id": "skill2", "name": "Skill 2"}
                    ]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"}
                    ]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing a skill should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("skill2")));
    }

    @Test
    void testBackwardIncompatibleUrlChange() {
        String existing = """
                {
                    "name": "TestAgent",
                    "url": "https://old-url.com/agent"
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "url": "https://new-url.com/agent"
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Changing URL should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("URL changed")));
    }

    @Test
    void testBackwardCompatibleAddingCapability() {
        String existing = """
                {
                    "name": "TestAgent",
                    "capabilities": {
                        "streaming": false
                    }
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": true
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding or enabling capabilities should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleDisablingCapability() {
        String existing = """
                {
                    "name": "TestAgent",
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": true
                    }
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": false
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Disabling a capability should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("pushNotifications")));
    }

    @Test
    void testBackwardIncompatibleRemovingAuthScheme() {
        String existing = """
                {
                    "name": "TestAgent",
                    "authentication": {
                        "schemes": ["bearer", "api-key"]
                    }
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "authentication": {
                        "schemes": ["bearer"]
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing auth scheme should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("api-key")));
    }

    @Test
    void testBackwardCompatibleAddingInputMode() {
        String existing = """
                {
                    "name": "TestAgent",
                    "defaultInputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "defaultInputModes": ["text", "image"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding input modes should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingInputMode() {
        String existing = """
                {
                    "name": "TestAgent",
                    "defaultInputModes": ["text", "image"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "defaultInputModes": ["text"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing input modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("image")));
    }

    @Test
    void testBackwardIncompatibleRemovingOutputMode() {
        String existing = """
                {
                    "name": "TestAgent",
                    "defaultOutputModes": ["text", "json"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "defaultOutputModes": ["text"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing output modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("json")));
    }

    @Test
    void testForwardCompatibleRemovingSkill() {
        String existing = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"},
                        {"id": "skill2", "name": "Skill 2"}
                    ]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"}
                    ]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FORWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Removing a skill should be forward compatible");
    }

    @Test
    void testFullCompatibleNameChange() {
        String existing = """
                {
                    "name": "OldName",
                    "url": "https://example.com/agent"
                }
                """;

        String proposed = """
                {
                    "name": "NewName",
                    "url": "https://example.com/agent"
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FULL,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Changing name should be fully compatible");
    }

    @Test
    void testMultipleIncompatibilities() {
        String existing = """
                {
                    "name": "TestAgent",
                    "url": "https://old-url.com",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"},
                        {"id": "skill2", "name": "Skill 2"}
                    ],
                    "capabilities": {
                        "streaming": true
                    }
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "url": "https://new-url.com",
                    "skills": [
                        {"id": "skill1", "name": "Skill 1"}
                    ],
                    "capabilities": {
                        "streaming": false
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(3, result.getIncompatibleDifferences().size(),
                "Should report URL change, skill removal, and capability removal");
    }
}
