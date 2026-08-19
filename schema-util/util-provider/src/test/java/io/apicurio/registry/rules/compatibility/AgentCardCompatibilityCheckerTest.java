package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static String baseCard(String skills, String extras) {
        return """
                {
                    "name": "TestAgent",
                    "description": "Test agent",
                    "version": "1.0.0",
                    "supportedInterfaces": [
                        { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                    ],
                    "capabilities": {},
                    "skills": [%s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]%s
                }
                """.formatted(skills, extras);
    }

    private static final String SKILL1 =
            """
            { "id": "skill1", "name": "Skill 1", "description": "A skill", "tags": ["test"] }""";
    private static final String SKILL2 =
            """
            { "id": "skill2", "name": "Skill 2", "description": "Another skill", "tags": ["test"] }""";

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                Collections.emptyList(),
                createAgentCard(baseCard(SKILL1, "")),
                Map.of());

        assertTrue(result.isCompatible(), "Should be compatible when no existing artifacts");
    }

    @Test
    void testBackwardCompatibleAddingSkill() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1 + "," + SKILL2, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding a skill should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingSkill() {
        String existing = baseCard(SKILL1 + "," + SKILL2, "");
        String proposed = baseCard(SKILL1, "");

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
                    "skills": [%s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(SKILL1);

        String proposed = baseCard(SKILL1, "");

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
        String existing = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}", "\"capabilities\": { \"streaming\": false }");
        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": true }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Adding or enabling capabilities should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleDisablingCapability() {
        String existing = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": true }");
        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"pushNotifications\": false }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Disabling a capability should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("pushNotifications")));
    }

    @Test
    void testBackwardIncompatibleRemovingSecurityScheme() {
        String twoSchemes = """
                ,
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" },
                        "apikey": { "type": "apiKey", "name": "X-API-Key", "location": "header" }
                    }""";
        String oneScheme = """
                ,
                    "securitySchemes": {
                        "bearer": { "type": "httpAuth", "scheme": "Bearer" }
                    }""";

        String existing = baseCard(SKILL1, twoSchemes);
        String proposed = baseCard(SKILL1, oneScheme);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing security scheme should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("apikey")));
    }

    @Test
    void testBackwardCompatibleAddingInputMode() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace(
                "\"defaultInputModes\": [\"text\"]",
                "\"defaultInputModes\": [\"text\", \"image\"]");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding input modes should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleRemovingInputMode() {
        String existing = baseCard(SKILL1, "").replace(
                "\"defaultInputModes\": [\"text\"]",
                "\"defaultInputModes\": [\"text\", \"image\"]");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing input modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("image")));
    }

    @Test
    void testBackwardIncompatibleRemovingOutputMode() {
        String existing = baseCard(SKILL1, "").replace(
                "\"defaultOutputModes\": [\"text\"]",
                "\"defaultOutputModes\": [\"text\", \"json\"]");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing output modes should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("json")));
    }

    @Test
    void testForwardCompatibleRemovingSkill() {
        String existing = baseCard(SKILL1 + "," + SKILL2, "");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.FORWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Removing a skill should be forward compatible");
    }

    @Test
    void testFullCompatibleNameChange() {
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace("\"name\": \"TestAgent\"",
                "\"name\": \"NewName\"");

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
                    "capabilities": { "streaming": true },
                    "skills": [%s, %s],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """.formatted(SKILL1, SKILL2);

        String proposed = baseCard(SKILL1, "").replace(
                "\"capabilities\": {}", "\"capabilities\": { \"streaming\": false }");

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
        String existing = baseCard(SKILL1, "");
        String proposed = baseCard(SKILL1, "").replace(
                "\"protocolVersion\": \"1.0\"",
                "\"protocolVersion\": \"2.0\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Changing protocol version should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("Protocol version changed")));
    }

    private static final String TRANSLATE_EXTENSION =
            """
            { "uri": "https://example.com/ext/translate", "description": "Translation" }""";
    private static final String SUMMARISE_EXTENSION =
            """
            { "uri": "https://example.com/ext/summarise", "description": "Summarisation" }""";

    private static String cardWithExtensions(String extensions) {
        return baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"extensions\": [" + extensions + "] }");
    }

    @Test
    void testBackwardIncompatibleRemovingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing a declared capability extension should be backward incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/translate")),
                "The violation should name the removed extension uri");
    }

    @Test
    void testBackwardIncompatibleRemovingOneOfSeveralCapabilityExtensions() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION + "," + SUMMARISE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Removing one of several capability extensions should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "Only the removed extension should be reported");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/summarise")));
    }

    @Test
    void testCapabilityExtensionViolationIsReportedAgainstExtensionsPath() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertEquals("/capabilities/extensions",
                result.getIncompatibleDifferences().iterator().next().asRuleViolation().getContext());
    }

    @Test
    void testBackwardCompatibleAddingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION + "," + SUMMARISE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Adding a capability extension should be backward compatible");
    }

    @Test
    void testBackwardCompatibleRetainingCapabilityExtension() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(TRANSLATE_EXTENSION).replace(
                "\"description\": \"Translation\"", "\"description\": \"Translation v2\"");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "Keeping an extension uri while editing its description should stay compatible");
    }

    @Test
    void testCapabilityExtensionWithoutUriIsIgnored() {
        String existing = cardWithExtensions("{ \"description\": \"Anonymous extension\" }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "An extension with no uri cannot be tracked, so it should not be reported");
    }

    @Test
    void testCapabilityExtensionWithNonTextualUriIsIgnored() {
        String existing = cardWithExtensions("{ \"uri\": 42 }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "A non-textual uri is not an identity, so it should not be reported");
    }

    @Test
    void testCapabilityExtensionWithBlankUriIsIgnored() {
        String existing = cardWithExtensions("{ \"uri\": \"   \" }, { \"uri\": \"\" }");
        String proposed = baseCard(SKILL1, "");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertTrue(result.isCompatible(),
                "A blank uri is not an identity, so it should not be reported as a removal");
    }

    @Test
    void testBackwardIncompatibleRenamingCapabilityExtensionUri() {
        String existing = cardWithExtensions(TRANSLATE_EXTENSION);
        String proposed = cardWithExtensions(SUMMARISE_EXTENSION);

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible(),
                "Changing an extension uri drops the old one, so it is backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size(),
                "The new uri counts as an add, so only the old uri is a removal");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription()
                        .contains("https://example.com/ext/translate")));
    }

    @Test
    void testBooleanCapabilitiesStillCheckedAlongsideExtensions() {
        String existing = baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": true, \"extensions\": [" + TRANSLATE_EXTENSION
                        + "] }");
        String proposed = baseCard(SKILL1, "").replace("\"capabilities\": {}",
                "\"capabilities\": { \"streaming\": false }");

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCard(proposed),
                Map.of());

        assertFalse(result.isCompatible());
        assertEquals(2, result.getIncompatibleDifferences().size(),
                "Both the disabled boolean capability and the removed extension should be reported");
    }

    private TypedContent createAgentCardYaml(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.APPLICATION_YAML);
    }

    @Test
    void testBackwardCompatibleYamlFormat() {
        String existing = """
                name: TestAgent
                description: Test agent
                version: 1.0.0
                supportedInterfaces:
                  - url: https://example.com/agent
                    protocolBinding: http+json
                    protocolVersion: "1.0"
                capabilities: {}
                skills:
                  - id: skill1
                    name: Skill 1
                    description: A skill
                defaultInputModes:
                  - text
                defaultOutputModes:
                  - text
                """;

        String proposed = """
                name: TestAgent
                description: Test agent updated
                version: 1.1.0
                supportedInterfaces:
                  - url: https://example.com/agent
                    protocolBinding: http+json
                    protocolVersion: "1.0"
                capabilities: {}
                skills:
                  - id: skill1
                    name: Skill 1
                    description: A skill
                  - id: skill2
                    name: Skill 2
                    description: Added skill
                defaultInputModes:
                  - text
                defaultOutputModes:
                  - text
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCardYaml(existing)),
                createAgentCardYaml(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Adding a skill in YAML format should be backward compatible");
    }

    @Test
    void testBackwardIncompatibleYamlFormat() {
        String existing = """
                name: TestAgent
                description: Test agent
                version: 1.0.0
                supportedInterfaces:
                  - url: https://example.com/agent
                    protocolBinding: http+json
                    protocolVersion: "1.0"
                capabilities: {}
                skills:
                  - id: skill1
                    name: Skill 1
                    description: A skill
                  - id: skill2
                    name: Skill 2
                    description: Skill 2
                defaultInputModes:
                  - text
                defaultOutputModes:
                  - text
                """;

        String proposed = """
                name: TestAgent
                description: Test agent
                version: 1.1.0
                supportedInterfaces:
                  - url: https://example.com/agent
                    protocolBinding: http+json
                    protocolVersion: "1.0"
                capabilities: {}
                skills:
                  - id: skill1
                    name: Skill 1
                    description: A skill
                defaultInputModes:
                  - text
                defaultOutputModes:
                  - text
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCardYaml(existing)),
                createAgentCardYaml(proposed),
                Map.of());

        assertFalse(result.isCompatible(), "Removing a skill in YAML format should be backward incompatible");
        assertEquals(1, result.getIncompatibleDifferences().size());
    }

    @Test
    void testCrossFormatCompatibilityJsonToYaml() {
        String existing = baseCard(SKILL1, "");
        String proposed = """
                name: TestAgent
                description: Test agent
                version: 1.1.0
                supportedInterfaces:
                  - url: https://example.com/agent
                    protocolBinding: http+json
                    protocolVersion: "1.0"
                capabilities: {}
                skills:
                  - id: skill1
                    name: Skill 1
                    description: A skill
                  - id: skill2
                    name: Skill 2
                    description: Another skill
                defaultInputModes:
                  - text
                defaultOutputModes:
                  - text
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCard(existing)),
                createAgentCardYaml(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "Updating from JSON to YAML should be backward compatible");
    }

    @Test
    void testBackwardCompatibleYamlFlowMapping() {
        String existing = "{name: TestAgent, description: Test agent, version: 1.0.0, supportedInterfaces: [{url: \"https://example.com/agent\", protocolBinding: http+json, protocolVersion: \"1.0\"}], capabilities: {}, skills: [{id: skill1, name: Skill 1, description: A skill}], defaultInputModes: [text], defaultOutputModes: [text]}";
        String proposed = "{name: TestAgent, description: Test agent, version: 1.1.0, supportedInterfaces: [{url: \"https://example.com/agent\", protocolBinding: http+json, protocolVersion: \"1.0\"}], capabilities: {}, skills: [{id: skill1, name: Skill 1, description: A skill}, {id: skill2, name: Skill 2, description: Added skill}], defaultInputModes: [text], defaultOutputModes: [text]}";

        CompatibilityExecutionResult result = checker.testCompatibility(
                CompatibilityLevel.BACKWARD,
                List.of(createAgentCardYaml(existing)),
                createAgentCardYaml(proposed),
                Map.of());

        assertTrue(result.isCompatible(), "YAML flow mapping syntax should be parsed and backward compatible");
    }
}
