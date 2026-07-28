package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the CDI-produced {@link CommandLine} (built by
 * {@link BrandedCommandLineProducer}) resolves the dynamic help-text placeholders
 * {@code {{rule-types}}} and {@code {{rule-configs}}} in option and positional descriptions,
 * for rule commands at different nesting depths.
 */
@QuarkusTest
public class DynamicHelpTest {

    @Inject
    CommandLine commandLine;

    @Test
    public void testGlobalRuleHelpResolvesDynamicPlaceholders() {
        // acr rule create
        assertResolved(subcommand("rule", "create"));
    }

    @Test
    public void testArtifactRuleHelpResolvesDynamicPlaceholders() {
        // acr artifact rule create (deeper nesting)
        assertResolved(subcommand("artifact", "rule", "create"));
    }

    private String subcommand(String... path) {
        var current = commandLine;
        for (var name : path) {
            current = current.getSubcommands().get(name);
        }
        return current.getUsageMessage();
    }

    private void assertResolved(String help) {
        // Placeholders must be resolved, not rendered literally.
        assertThat(help)
                .doesNotContain("{{rule-types}}")
                .doesNotContain("{{rule-configs}}");
        // The dynamically resolved rule types and configuration values should be present.
        assertThat(help)
                .contains("VALIDITY").contains("COMPATIBILITY").contains("INTEGRITY")
                .contains("SYNTAX_ONLY").contains("BACKWARD_TRANSITIVE").contains("NO_CIRCULAR_REFERENCES");
    }
}
