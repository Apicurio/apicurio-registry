package io.apicurio.registry.cli.gitops;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;

@Command(
        name = "gitops",
        description = "Manage GitOps synchronization and validation",
        subcommands = {
                GitOpsStatusCommand.class,
                GitOpsSyncCommand.class,
                GitOpsValidateCommand.class
        }
)
public class GitOpsCommand extends AbstractCommand {

    @Override
    public void run(final OutputBuffer output) {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
