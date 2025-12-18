package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AgentCardCompatibilityChecker.
 */
class AgentCardCompatibilityCheckerTest {

    private final AgentCardCompatibilityChecker checker = new AgentCardCompatibilityChecker();

    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testCompatibleWhenNoChanges() {
        String content = """
                {
                  "name": "TestAgent",
                  "skills": [{"id": "skill1", "name": "Skill 1"}],
                  "capabilities": {"streaming": true}
                }
                """;

        TypedContent existing = toTypedContent(content);
        TypedContent proposed = toTypedContent(content);

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(existing), proposed, Collections.emptyMap());

        assertTrue(result.isCompatible(), "Identical content should be compatible");
    }

    @Test
    void testIncompatibleWhenSkillRemoved() {
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

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(toTypedContent(existing)), toTypedContent(proposed), Collections.emptyMap());

        assertFalse(result.isCompatible(), "Removing a skill should be incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("skill2")));
    }

    @Test
    void testCompatibleWhenSkillAdded() {
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

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(toTypedContent(existing)), toTypedContent(proposed), Collections.emptyMap());

        assertTrue(result.isCompatible(), "Adding a skill should be compatible");
    }

    @Test
    void testIncompatibleWhenCapabilityRemoved() {
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

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(toTypedContent(existing)), toTypedContent(proposed), Collections.emptyMap());

        assertFalse(result.isCompatible(), "Removing a capability should be incompatible");
        assertTrue(result.getIncompatibleDifferences().stream()
                .anyMatch(d -> d.asRuleViolation().getDescription().contains("pushNotifications")));
    }

    @Test
    void testCompatibleWhenCapabilityAdded() {
        String existing = """
                {
                  "name": "TestAgent",
                  "capabilities": {
                    "streaming": true
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

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                List.of(toTypedContent(existing)), toTypedContent(proposed), Collections.emptyMap());

        assertTrue(result.isCompatible(), "Adding a capability should be compatible");
    }

    @Test
    void testCompatibleWhenNoExistingArtifacts() {
        String proposed = """
                {
                  "name": "TestAgent",
                  "skills": [{"id": "skill1", "name": "Skill 1"}]
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD,
                Collections.emptyList(), toTypedContent(proposed), Collections.emptyMap());

        assertTrue(result.isCompatible(), "No existing artifacts should be compatible");
    }

    @Test
    void testCompatibleWhenLevelIsNone() {
        String existing = """
                {
                  "name": "TestAgent",
                  "skills": [{"id": "skill1"}]
                }
                """;

        String proposed = """
                {
                  "name": "TestAgent",
                  "skills": []
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.NONE,
                List.of(toTypedContent(existing)), toTypedContent(proposed), Collections.emptyMap());

        assertTrue(result.isCompatible(), "NONE level should always be compatible");
    }

    @Test
    void testFullCompatibilityChecksBothDirections() {
        String v1 = """
                {
                  "name": "TestAgent",
                  "skills": [{"id": "skill1"}],
                  "capabilities": {"streaming": true}
                }
                """;

        String v2 = """
                {
                  "name": "TestAgent",
                  "skills": [{"id": "skill2"}],
                  "capabilities": {"streaming": true}
                }
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.FULL,
                List.of(toTypedContent(v1)), toTypedContent(v2), Collections.emptyMap());

        assertFalse(result.isCompatible(), "FULL should detect skill changes in both directions");
        assertTrue(result.getIncompatibleDifferences().size() >= 2,
                "Should have differences from both directions");
    }

    @Test
    void testTransitiveChecksAllVersions() {
        String v1 = """
                {"name": "TestAgent", "skills": [{"id": "skill1"}]}
                """;

        String v2 = """
                {"name": "TestAgent", "skills": [{"id": "skill1"}, {"id": "skill2"}]}
                """;

        String v3 = """
                {"name": "TestAgent", "skills": [{"id": "skill2"}]}
                """;

        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD_TRANSITIVE,
                List.of(toTypedContent(v1), toTypedContent(v2)), toTypedContent(v3), Collections.emptyMap());

        assertFalse(result.isCompatible(), "Should be incompatible with v1 (skill1 removed)");
    }
}
