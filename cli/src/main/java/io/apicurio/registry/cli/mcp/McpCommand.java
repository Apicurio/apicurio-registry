package io.apicurio.registry.cli.mcp;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

/**
 * Commands for MCP (Model Context Protocol) servers registered in Registry.
 */
@Command(
        name = "mcp",
        description = "Work with MCP servers",
        subcommands = {
                McpImportCommand.class
        }
)
public class McpCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
