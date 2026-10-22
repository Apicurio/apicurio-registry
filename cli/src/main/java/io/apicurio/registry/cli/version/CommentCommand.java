package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

@Command(
        name = "comment",
        aliases = {"comments"},
        description = "Work with version comments",
        subcommands = {
                CommentCreateCommand.class,
                CommentDeleteCommand.class,
                CommentListCommand.class,
                CommentUpdateCommand.class
        }
)
public class CommentCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
