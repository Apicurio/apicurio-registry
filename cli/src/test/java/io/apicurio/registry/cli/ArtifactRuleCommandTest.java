package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.UUID;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the artifact rules CLI commands.
 */
@QuarkusTest
@Timeout(120)
public class ArtifactRuleCommandTest extends AbstractCLITest {

    private String testGroup;
    private static final String TEST_ARTIFACT = "artifact-rule-test-artifact";

    @BeforeEach
    @Timeout(120)
    public void setUpArtifact() {
        testGroup = "artifact-rule-test-" + UUID.randomUUID();
        executeAndAssertSuccess("group", "create", testGroup);
        executeAndAssertSuccess("artifact", "create", "-g", testGroup,
                "--type", "JSON",
                TEST_ARTIFACT);
        // Artifact creation sets the current context. Missing-ID tests need an empty one.
        executeAndAssertSuccess("context", "delete", "--all");
        executeAndAssertSuccess("context", "create", "test", registryUrl);
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
    }

    @Test
    public void testArtifactRuleHelp() {
        testHelpCommand("artifact", "rule");
        testHelpCommand("artifact", "rule", "create");
        testHelpCommand("artifact", "rule", "get");
        testHelpCommand("artifact", "rule", "update");
        testHelpCommand("artifact", "rule", "delete");
    }

    @Test
    public void testArtifactRuleCommandEmpty() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "-g", testGroup, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then
        assertThat(rules)
                .as(withCliOutput("There should not be any artifact rules initially."))
                .isEmpty();
    }

    @Test
    public void testArtifactRuleCreateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "create", "--output-type", "json",
                "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "FULL",
                "VALIDITY");
        var rule = MAPPER.readValue(out.toString(), Rule.class);

        // Then
        assertThat(rule.getRuleType())
                .as(withCliOutput("Created rule should have the correct ruleType"))
                .isNotNull();
        assertThat(rule.getRuleType().value())
                .as(withCliOutput("Created rule should have ruleType VALIDITY"))
                .isEqualTo("VALIDITY");
        assertThat(rule.getConfig())
                .as(withCliOutput("Created rule should have the correct config"))
                .isEqualTo("FULL");
    }

    @Test
    public void testArtifactRuleCreateCommandFails() {
        // Missing required --config option
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT, "VALIDITY");
        // Missing required ruleType parameter
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT, "-c", "FULL");
        // Invalid rule type
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT, "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT, "-c", "INVALID_CONFIG", "VALIDITY");
        // Non-existent group
        executeAndAssertFailure("artifact", "rule", "create", "-g", "non-existent-group", "-a", TEST_ARTIFACT, "-c", "FULL", "VALIDITY");
        // Non-existent artifact
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-a", "non-existent-artifact", "-c", "FULL", "VALIDITY");
        // Missing artifact ID (no -a flag and no context)
        executeAndAssertFailure("artifact", "rule", "create", "-g", testGroup, "-c", "FULL", "VALIDITY");
    }

    @Test
    public void testMissingArtifactId() {
        // All commands should fail when artifact ID is not provided and not in context
        executeAndAssertFailure("artifact", "rule", "-g", testGroup);
        executeAndAssertFailure("artifact", "rule", "get", "-g", testGroup, "VALIDITY");
        executeAndAssertFailure("artifact", "rule", "update", "-g", testGroup, "-c", "FULL", "VALIDITY");
        executeAndAssertFailure("artifact", "rule", "delete", "-g", testGroup, "VALIDITY");
        executeAndAssertFailure("artifact", "rule", "delete", "--all", "-g", testGroup);
    }

    @Test
    public void testArtifactRuleGetCommand() throws JsonProcessingException {
        createRule("VALIDITY", "FULL");
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "get", "--output-type", "json",
                "-g", testGroup, "-a", TEST_ARTIFACT,
                "VALIDITY");
        var rule = MAPPER.readValue(out.toString(), Rule.class);

        // Then
        assertThat(rule.getRuleType())
                .as(withCliOutput("Retrieved rule should have the correct ruleType"))
                .isNotNull();
        assertThat(rule.getRuleType().value())
                .as(withCliOutput("Retrieved rule should have ruleType VALIDITY"))
                .isEqualTo("VALIDITY");
        assertThat(rule.getConfig())
                .as(withCliOutput("Retrieved rule should have the correct config"))
                .isEqualTo("FULL");
    }

    @Test
    public void testArtifactRuleGetCommandFails() {
        // Invalid rule type
        executeAndAssertFailure("artifact", "rule", "get", "-g", testGroup, "-a", TEST_ARTIFACT, "INVALID_TYPE");
        // Get a rule that does not exist
        executeAndAssertSuccess("artifact", "rule", "delete", "--all", "-g", testGroup, "-a", TEST_ARTIFACT);
        executeAndAssertFailure("artifact", "rule", "get", "-g", testGroup, "-a", TEST_ARTIFACT, "COMPATIBILITY");
    }

    @Test
    public void testArtifactRuleUpdateCommandFails() {
        createRule("VALIDITY", "FULL");
        // Invalid rule type
        executeAndAssertFailure("artifact", "rule", "update", "-g", testGroup, "-a", TEST_ARTIFACT, "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        err.getBuffer().setLength(0);
        assertThat(cmd.execute("artifact", "rule", "update", "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "INVALID_CONFIG", "VALIDITY")).isEqualTo(VALIDATION_ERROR_RETURN_CODE);
        assertThat(err.toString()).contains("Invalid config 'INVALID_CONFIG' for rule type 'VALIDITY'");
        // Update a rule that does not exist
        executeAndAssertSuccess("artifact", "rule", "delete", "--all", "-g", testGroup, "-a", TEST_ARTIFACT);
        executeAndAssertFailure("artifact", "rule", "update", "-g", testGroup, "-a", TEST_ARTIFACT, "-c", "FULL", "INTEGRITY");
    }

    @Test
    public void testArtifactRuleUpdateCommand() throws JsonProcessingException {
        createRule("VALIDITY", "FULL");
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "update", "--output-type", "json",
                "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "SYNTAX_ONLY",
                "VALIDITY");
        var rule = MAPPER.readValue(out.toString(), Rule.class);

        // Then
        assertThat(rule.getRuleType())
                .as(withCliOutput("Updated rule should have the correct ruleType"))
                .isNotNull();
        assertThat(rule.getRuleType().value())
                .as(withCliOutput("Updated rule should have ruleType VALIDITY"))
                .isEqualTo("VALIDITY");
        assertThat(rule.getConfig())
                .as(withCliOutput("Updated rule should have the updated config"))
                .isEqualTo("SYNTAX_ONLY");
    }

    @Test
    public void testArtifactRuleCreateIntegrity() throws JsonProcessingException {
        // When - create an INTEGRITY rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "create", "--output-type", "json",
                "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "FULL",
                "INTEGRITY");
        var rule = MAPPER.readValue(out.toString(), Rule.class);

        // Then
        assertThat(rule.getRuleType())
                .as(withCliOutput("Created rule should have the correct ruleType"))
                .isNotNull();
        assertThat(rule.getRuleType().value())
                .as(withCliOutput("Created rule should have ruleType INTEGRITY"))
                .isEqualTo("INTEGRITY");
        assertThat(rule.getConfig())
                .as(withCliOutput("Created rule should have the correct config"))
                .isEqualTo("FULL");
    }

    @Test
    public void testArtifactRuleListCommand() throws JsonProcessingException {
        createRule("VALIDITY", "FULL");
        createRule("INTEGRITY", "FULL");
        // Create a third rule
        executeAndAssertSuccess("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "BACKWARD", "COMPATIBILITY");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "-g", testGroup, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then
        assertThat(rules)
                .as(withCliOutput("There should be three artifact rules."))
                .containsExactlyInAnyOrder("VALIDITY", "INTEGRITY", "COMPATIBILITY");
    }

    @Test
    public void testArtifactRuleDeleteCommand() throws JsonProcessingException {
        createRule("VALIDITY", "FULL");
        createRule("INTEGRITY", "FULL");
        createRule("COMPATIBILITY", "BACKWARD");
        // When - delete a single rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "delete", "-g", testGroup, "-a", TEST_ARTIFACT,
                "COMPATIBILITY");

        // Then - verify two rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "-g", testGroup, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be two artifact rules after deleting COMPATIBILITY."))
                .containsExactlyInAnyOrder("VALIDITY", "INTEGRITY");
    }

    @Test
    public void testArtifactRuleDeleteAllCommand() throws JsonProcessingException {
        createRule("VALIDITY", "FULL");
        createRule("INTEGRITY", "FULL");
        // Create another rule so we have more
        executeAndAssertSuccess("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", "BACKWARD", "COMPATIBILITY");

        // When - delete all rules
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "delete", "--all",
                "-g", testGroup, "-a", TEST_ARTIFACT);

        // Then - verify no rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "-g", testGroup, "-a", TEST_ARTIFACT,
                "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be no artifact rules after deleting all."))
                .isEmpty();
    }

    @Test
    public void testArtifactRuleDeleteCommandFails() {
        // Delete without specifying rule type or --all
        executeAndAssertFailure("artifact", "rule", "delete", "-g", testGroup, "-a", TEST_ARTIFACT);
        // Invalid rule type
        executeAndAssertFailure("artifact", "rule", "delete", "-g", testGroup, "-a", TEST_ARTIFACT, "INVALID_TYPE");
        // Mutually exclusive: --all with specific rule type
        executeAndAssertFailure("artifact", "rule", "delete", "--all", "-g", testGroup, "-a", TEST_ARTIFACT, "VALIDITY");
        // Delete a rule that does not exist
        executeAndAssertSuccess("artifact", "rule", "delete", "--all", "-g", testGroup, "-a", TEST_ARTIFACT);
        executeAndAssertFailure("artifact", "rule", "delete", "-g", testGroup, "-a", TEST_ARTIFACT, "INTEGRITY");
    }

    @Test
    public void testArtifactRuleTableOutput() {
        // Verify table output works for list
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("artifact", "rule", "-g", testGroup, "-a", TEST_ARTIFACT);
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Rule Type");
    }

    private void createRule(String type, String config) {
        executeAndAssertSuccess("artifact", "rule", "create", "-g", testGroup, "-a", TEST_ARTIFACT,
                "-c", config, type);
    }
}
