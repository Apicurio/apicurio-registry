package io.apicurio.registry.cli;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Help and input validation for {@code acr mcp}. These fail before any request is sent, so they
 * run against any Registry image; {@link McpImportCommandTest} covers the import itself.
 */
@QuarkusTest
public class McpCommandTest extends AbstractCLITest {

    @Test
    void help() {
        testHelpCommand("mcp");
        testHelpCommand("mcp", "import");
        assertThat(out.toString()).contains("[Experimental]").contains("--header-file").contains("--header-env");
    }

    @Test
    void urlIsRequired() {
        int code = cmd.execute("mcp", "import", "srv");

        assertThat(code).as(withCliOutput("a missing --url is a usage error")).isEqualTo(2);
        assertThat(err.toString()).contains("Missing required option: '--url");
    }

    @Test
    void unsetHeaderVariableIsRefusedBeforeAnyRequest() {
        config.setEnvOverride("MCP_TOKEN_NOT_SET", null);
        try {
            int code = cmd.execute("mcp", "import", "srv", "--url", "https://mcp.example.com/mcp",
                    "--header-env", "Authorization=MCP_TOKEN_NOT_SET");

            // A request would have failed with the server error code (3): srv does not exist.
            assertThat(code).as(withCliOutput("an unset variable is a validation error")).isEqualTo(2);
            assertThat(err.toString())
                    .contains("Environment variable 'MCP_TOKEN_NOT_SET' for header 'Authorization' is not set.");
        } finally {
            config.clearEnvOverrides();
        }
    }

    /**
     * The base container does not enable MCP import. On a Registry with the endpoint (3.4 or
     * later), the CLI then names the setting to enable.
     */
    @Test
    void disabledFeatureNamesTheSetting() {
        assumeTrue(McpImportCommandTest.isAtLeast34(registryUrl), "needs a Registry with the mcp-import endpoint");

        int code = cmd.execute("mcp", "import", "srv", "--url", "https://mcp.example.com/mcp");

        assertThat(code).as(withCliOutput("a disabled feature is a server error")).isEqualTo(3);
        assertThat(err.toString())
                .contains("Importing tools from MCP servers is not enabled.")
                .contains("apicurio.mcp-import.enabled=true");
    }
}
