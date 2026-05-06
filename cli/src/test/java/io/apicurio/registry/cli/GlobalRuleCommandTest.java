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
 * Tests for the global rules CLI commands.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class GlobalRuleCommandTest extends AbstractCLITest {

    @Test
    public void testRuleHelp() {
        testHelpCommand("rule");
        testHelpCommand("rule", "create");
        testHelpCommand("rule", "get");
        testHelpCommand("rule", "update");
        testHelpCommand("rule", "delete");
    }

    @Test
    @Order(0)
    public void testRuleCommandEmpty() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then
        assertThat(rules)
                .as(withCliOutput("There should not be any global rules initially."))
                .isEmpty();
    }

    @Test
    @Order(1)
    public void testRuleCreateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "create", "--output-type", "json",
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
    public void testRuleCreateCommandFails() {
        // Missing required --config option
        executeAndAssertFailure("rule", "create", "VALIDITY");
        // Missing required ruleType parameter
        executeAndAssertFailure("rule", "create", "-c", "FULL");
        // Invalid rule type
        executeAndAssertFailure("rule", "create", "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        executeAndAssertFailure("rule", "create", "-c", "INVALID_CONFIG", "VALIDITY");
    }

    @Test
    @Order(2)
    public void testRuleGetCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "get", "--output-type", "json", "VALIDITY");
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
    public void testRuleGetCommandFails() {
        // Invalid rule type
        executeAndAssertFailure("rule", "get", "INVALID_TYPE");
        // Get a rule that does not exist
        executeAndAssertSuccess("rule", "delete", "--all");
        executeAndAssertFailure("rule", "get", "COMPATIBILITY");
    }

    @Test
    public void testRuleUpdateCommandFails() {
        // Invalid rule type
        executeAndAssertFailure("rule", "update", "-c", "FULL", "INVALID_TYPE");
        // Invalid config for VALIDITY
        executeAndAssertFailure("rule", "update", "-c", "INVALID_CONFIG", "VALIDITY");
        // Update a rule that does not exist
        executeAndAssertSuccess("rule", "delete", "--all");
        executeAndAssertFailure("rule", "update", "-c", "FULL", "INTEGRITY");
    }

    @Test
    @Order(3)
    public void testRuleUpdateCommand() throws JsonProcessingException {
        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "update", "--output-type", "json",
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
    @Order(4)
    public void testRuleCreateIntegrity() throws JsonProcessingException {
        // When - create an INTEGRITY rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "create", "--output-type", "json",
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
    @Order(5)
    public void testRuleListCommand() throws JsonProcessingException {
        // Create a third rule
        executeAndAssertSuccess("rule", "create", "-c", "BACKWARD", "COMPATIBILITY");

        // When
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });

        // Then - VALIDITY (Order 1) + INTEGRITY (Order 4) + COMPATIBILITY (just created)
        assertThat(rules)
                .as(withCliOutput("There should be three global rules."))
                .hasSize(3);
    }

    @Test
    @Order(6)
    public void testRuleDeleteCommand() throws JsonProcessingException {
        // When - delete a single rule
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "delete", "COMPATIBILITY");

        // Then - verify two rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be two global rules after deleting COMPATIBILITY."))
                .hasSize(2);
    }

    @Test
    @Order(7)
    public void testRuleDeleteAllCommand() throws JsonProcessingException {
        // Create another rule so we have two
        executeAndAssertSuccess("rule", "create", "-c", "BACKWARD", "COMPATIBILITY");

        // When - delete all rules
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "delete", "--all");

        // Then - verify no rules remain
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule", "--output-type", "json");
        var rules = MAPPER.readValue(out.toString(), new TypeReference<List<String>>() {
        });
        assertThat(rules)
                .as(withCliOutput("There should be no global rules after deleting all."))
                .isEmpty();
    }

    @Test
    public void testRuleDeleteCommandFails() {
        // Delete without specifying rule type or --all
        executeAndAssertFailure("rule", "delete");
        // Invalid rule type
        executeAndAssertFailure("rule", "delete", "INVALID_TYPE");
        // Mutually exclusive: --all with specific rule type
        executeAndAssertFailure("rule", "delete", "--all", "VALIDITY");
        // Delete a rule that does not exist
        executeAndAssertSuccess("rule", "delete", "--all");
        executeAndAssertFailure("rule", "delete", "INTEGRITY");
    }

    @Test
    public void testRuleTableOutput() {
        // Verify table output works for list
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("rule");
        assertThat(out.toString())
                .as(withCliOutput("Table output should contain column headers"))
                .contains("Rule Type");
    }
}
