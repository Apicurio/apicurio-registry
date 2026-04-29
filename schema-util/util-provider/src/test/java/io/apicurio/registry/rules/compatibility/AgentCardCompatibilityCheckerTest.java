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
 * Tests for AgentCardCompatibilityChecker (v1.0 format).
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

    // A minimal v1.0 agent card template for embedding in tests
    private static final String MINIMAL_CARD = """
            {
                "name": "TestAgent",
                "description": "Test agent",
                "version": "1.0.0",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {},
                "skills": [
                    { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                createAgentCard(MINIMAL_CARD),
                Map.of());

        assertTrue(result.isCompatible(), "Should be compatible when no existing artifacts");
    }

    @Test
    void testBackwardCompatibleAddingSkill() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] },
                        { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] },
                        { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
    void testBackwardIncompatibleInterfaceRemoval() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" },
                        { "url": "https://example.com/agent", "protocolBinding": "jsonrpc", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing an interface should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Interface")
                        && d.asRuleViolation().getDescription().contains("removed")));
    }

    @Test
    void testBackwardCompatibleAddingCapability() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": false
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": true
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": true
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": false
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
    void testBackwardIncompatibleRemovingSecurityScheme() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"],
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" },
                        "apikey": { "type": "apiKey", "name": "X-API-Key", "location": "header" }
                    }
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"],
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" }
                    }
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing security scheme should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("apikey")));
    }

    @Test
    void testBackwardCompatibleAddingInputMode() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text", "image"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text", "image"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text", "json"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] },
                        { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "NewName",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
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
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" },
                        { "url": "https://example.com/agent", "protocolBinding": "jsonrpc", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": true
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] },
                        { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {
                        "streaming": false
                    },
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(3, result.getIncompatibleDifferences().size(),
                "Should report interface removal, skill removal, and capability removal");
    }

    @Test
    void testBackwardIncompatibleProtocolVersionChange() {
        String existing = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        String proposed = """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.1.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "2.0" }
                    ],
                    "capabilities": {},
                    "skills": [
                        { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Changing protocol version should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("Protocol version changed")));
    }
}
