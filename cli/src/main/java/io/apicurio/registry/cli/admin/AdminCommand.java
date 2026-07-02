package io.apicurio.registry.cli.admin;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

@Command(
        name = "admin",
        description = "Admin operations: export and import",
        subcommands = {
                ExportCommand.class,
                ImportCommand.class
        }
)
public class AdminCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
