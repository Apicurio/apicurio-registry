package io.apicurio.registry.cli.version;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

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
public class CommentCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
