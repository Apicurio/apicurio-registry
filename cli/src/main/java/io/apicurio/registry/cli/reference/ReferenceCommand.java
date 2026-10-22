package io.apicurio.registry.cli.reference;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

@Command(
        name = "reference",
        aliases = {"references", "ref"},
        description = "Manage artifact references",
        subcommands = {
                ReferenceListCommand.class
        }
)
public class ReferenceCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
