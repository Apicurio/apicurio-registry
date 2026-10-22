package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the group rules CLI commands.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class GroupRuleCommandTest extends AbstractCLITest {

    private static final String TEST_GROUP = "rule-test-group";

    @Test
    @Order(0)
    public void testSetup() {
        // Create a test group for all rule operations
        executeAndAssertSuccess("group", "create", TEST_GROUP);
    }

    @Test
    public void testGroupRuleHelp() {
        testHelpCommand("group", "rule");
        testHelpCommand("group", "rule", "create");
        testHelpCommand("group", "rule", "get");
        testHelpCommand("group", "rule", "update");
        testHelpCommand("group", "rule", "delete");
    }

    @Test
    @Order(1)
    public void testGroupRuleCommandEmpty() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "-g", TEST_GROUP, "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then
        assertThat(rules)
                .as(withCliOutput("There should not be any group rules initially."))
                .isEmpty();
    }

    @Test
    @Order(2)
    public void testGroupRuleCreateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "create", "--output-type", "json",
                "-g", TEST_GROUP,
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
    public void testGroupRuleCreateCommandFails() {
        // Missing required --config option
        executeAndAssertFailure("group", "rule", "create", "-g", TEST_GROUP, "VALIDITY");
        // Missing required ruleType parameter
        executeAndAssertFailure("group", "rule", "create", "-g", TEST_GROUP, "-c", "FULL");
        // Invalid rule type
        executeAndAssertFailure("group", "rule", "create", "-g", TEST_GROUP, "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        executeAndAssertFailure("group", "rule", "create", "-g", TEST_GROUP, "-c", "INVALID_CONFIG", "VALIDITY");
        // Non-existent group
        executeAndAssertFailure("group", "rule", "create", "-g", "non-existent-group", "-c", "FULL", "VALIDITY");
        // Default group not supported
        executeAndAssertFailure("group", "rule", "create", "-g", "default", "-c", "FULL", "VALIDITY");
    }

    @Test
    public void testDefaultGroupRejected() {
        // All group rule operations should reject the 'default' group
        executeAndAssertFailure("group", "rule", "-g", "default");
        executeAndAssertFailure("group", "rule", "get", "-g", "default", "VALIDITY");
        executeAndAssertFailure("group", "rule", "update", "-g", "default", "-c", "FULL", "VALIDITY");
        executeAndAssertFailure("group", "rule", "delete", "-g", "default", "VALIDITY");
        executeAndAssertFailure("group", "rule", "delete", "--all", "-g", "default");
    }

    @Test
    @Order(3)
    public void testGroupRuleGetCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "get", "--output-type", "json",
                "-g", TEST_GROUP,
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
    public void testGroupRuleGetCommandFails() {
        // Invalid rule type
        executeAndAssertFailure("group", "rule", "get", "-g", TEST_GROUP, "INVALID_TYPE");
        // Get a rule that does not exist
        executeAndAssertSuccess("group", "rule", "delete", "--all", "-g", TEST_GROUP);
        executeAndAssertFailure("group", "rule", "get", "-g", TEST_GROUP, "COMPATIBILITY");
    }

    @Test
    public void testGroupRuleUpdateCommandFails() {
        // Invalid rule type
        executeAndAssertFailure("group", "rule", "update", "-g", TEST_GROUP, "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        executeAndAssertFailure("group", "rule", "update", "-g", TEST_GROUP, "-c", "INVALID_CONFIG", "VALIDITY");
        // Update a rule that does not exist
        executeAndAssertSuccess("group", "rule", "delete", "--all", "-g", TEST_GROUP);
        executeAndAssertFailure("group", "rule", "update", "-g", TEST_GROUP, "-c", "FULL", "INTEGRITY");
    }

    @Test
    @Order(4)
    public void testGroupRuleUpdateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "update", "--output-type", "json",
                "-g", TEST_GROUP,
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
    @Order(5)
    public void testGroupRuleCreateIntegrity() throws JsonProcessingException {
        // When - create an INTEGRITY rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "create", "--output-type", "json",
                "-g", TEST_GROUP,
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
    @Order(6)
    public void testGroupRuleListCommand() throws JsonProcessingException {
        // Create a third rule
        executeAndAssertSuccess("group", "rule", "create", "-g", TEST_GROUP, "-c", "BACKWARD", "COMPATIBILITY");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "-g", TEST_GROUP, "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then
        assertThat(rules)
                .as(withCliOutput("There should be three group rules."))
                .hasSize(3);
    }

    @Test
    @Order(7)
    public void testGroupRuleDeleteCommand() throws JsonProcessingException {
        // When - delete a single rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "delete", "-g", TEST_GROUP, "COMPATIBILITY");

        // Then - verify two rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "-g", TEST_GROUP, "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be two group rules after deleting COMPATIBILITY."))
                .hasSize(2);
    }

    @Test
    @Order(8)
    public void testGroupRuleDeleteAllCommand() throws JsonProcessingException {
        // Create another rule so we have more
        executeAndAssertSuccess("group", "rule", "create", "-g", TEST_GROUP, "-c", "BACKWARD", "COMPATIBILITY");

        // When - delete all rules
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "delete", "--all", "-g", TEST_GROUP);

        // Then - verify no rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "-g", TEST_GROUP, "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be no group rules after deleting all."))
                .isEmpty();
    }

    @Test
    public void testGroupRuleDeleteCommandFails() {
        // Delete without specifying rule type or --all
        executeAndAssertFailure("group", "rule", "delete", "-g", TEST_GROUP);
        // Invalid rule type
        executeAndAssertFailure("group", "rule", "delete", "-g", TEST_GROUP, "INVALID_TYPE");
        // Mutually exclusive: --all with specific rule type
        executeAndAssertFailure("group", "rule", "delete", "--all", "-g", TEST_GROUP, "VALIDITY");
        // Delete a rule that does not exist
        executeAndAssertSuccess("group", "rule", "delete", "--all", "-g", TEST_GROUP);
        executeAndAssertFailure("group", "rule", "delete", "-g", TEST_GROUP, "INTEGRITY");
    }

    @Test
    public void testGroupRuleTableOutput() {
        // Verify table output works for list
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("group", "rule", "-g", TEST_GROUP);
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Rule Type");
    }
}
