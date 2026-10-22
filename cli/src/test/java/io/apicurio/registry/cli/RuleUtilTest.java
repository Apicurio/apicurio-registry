package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.RuleUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the dynamic help-text rendering in {@link RuleUtil}, which backs the
 * {@code {{rule-types}}} and {@code {{rule-configs}}} placeholders resolved at runtime.
 */
public class RuleUtilTest {

    @Test
    public void testRenderRuleTypes() {
        assertThat(RuleUtil.renderRuleTypes()).isEqualTo("VALIDITY, COMPATIBILITY, INTEGRITY");
    }

    @Test
    public void testRenderRuleConfigs() {
        // Lines are separated by the picocli newline token '%n', one line per rule type.
        var lines = RuleUtil.renderRuleConfigs().split("%n");

        assertThat(lines).hasSize(3);
        assertThat(lines[0]).startsWith("  VALIDITY: ")
                .contains("NONE").contains("SYNTAX_ONLY").contains("FULL");
        assertThat(lines[1]).startsWith("  COMPATIBILITY: ")
                .contains("BACKWARD").contains("BACKWARD_TRANSITIVE").contains("FORWARD")
                .contains("FORWARD_TRANSITIVE").contains("FULL").contains("FULL_TRANSITIVE").contains("NONE");
        assertThat(lines[2]).startsWith("  INTEGRITY: ")
                .contains("NONE").contains("REFS_EXIST").contains("ALL_REFS_MAPPED")
                .contains("NO_DUPLICATES").contains("NO_CIRCULAR_REFERENCES").contains("FULL");
    }
}
