package io.apicurio.registry.cli;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPES;
import static io.apicurio.registry.cli.utils.Columns.CLI_VERSION;
import static io.apicurio.registry.cli.utils.Columns.SERVER_BUILT_ON;
import static io.apicurio.registry.cli.utils.Columns.SERVER_NAME;
import static io.apicurio.registry.cli.utils.Columns.SERVER_VERSION;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the version command — verifies CLI version and server info
 * in both JSON and table output, with and without a server connection.
 */
public class VersionCommandTest extends AbstractCLITest {

    @Test
    public void testVersionHelp() {
        testHelpCommand("version");
    }

    @Test
    public void testVersionJsonOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("version", "--output-type", "json");

        final var json = out.toString();
        assertThatJson(json).node(CLI_VERSION).isString().isNotEqualTo("");
        assertThatJson(json).node(SERVER_NAME).isString().isNotEqualTo("");
        assertThatJson(json).node(SERVER_VERSION).isString().isNotEqualTo("");
        assertThatJson(json).node(SERVER_BUILT_ON).isNumber();
        assertThatJson(json).node(ARTIFACT_TYPES).isArray().isNotEmpty();
    }

    @Test
    public void testVersionTableOutput() {
        out.getBuffer().setLength(0);
        executeAndAssertSuccess("version");
        final var output = out.toString();

        assertThat(output).as(withCliOutput("Table should contain CLI version")).contains(CLI_VERSION);
        assertThat(output).as(withCliOutput("Table should contain Server name")).contains(SERVER_NAME);
        assertThat(output).as(withCliOutput("Table should contain Server version")).contains(SERVER_VERSION);
        assertThat(output).as(withCliOutput("Table should contain Server built on")).contains(SERVER_BUILT_ON);
        assertThat(output).as(withCliOutput("Table should contain Artifact types")).contains(ARTIFACT_TYPES);
    }

    @Test
    public void testVersionTableWithoutContext() {
        executeAndAssertSuccess("context", "delete", "--all");

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("version");
            final var output = out.toString();

            assertThat(output)
                    .as(withCliOutput("Should still show CLI version when server is unreachable"))
                    .contains(CLI_VERSION);
            assertThat(output)
                    .as(withCliOutput("Should still show server field labels"))
                    .contains(SERVER_NAME, SERVER_VERSION, SERVER_BUILT_ON, ARTIFACT_TYPES);
        } finally {
            executeAndAssertSuccess("context", "create", "test", registryUrl);
        }
    }

    @Test
    public void testVersionJsonWithoutContext() {
        executeAndAssertSuccess("context", "delete", "--all");

        try {
            out.getBuffer().setLength(0);
            executeAndAssertSuccess("version", "--output-type", "json");

            final var json = out.toString();
            assertThatJson(json).node(CLI_VERSION).isString().isNotEqualTo("");
            assertThatJson(json).isObject()
                    .containsEntry(SERVER_NAME, null)
                    .containsEntry(SERVER_VERSION, null)
                    .containsEntry(SERVER_BUILT_ON, null)
                    .containsEntry(ARTIFACT_TYPES, null);
        } finally {
            executeAndAssertSuccess("context", "create", "test", registryUrl);
        }
    }
}
