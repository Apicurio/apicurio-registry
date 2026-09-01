package io.apicurio.registry.cli.gitops;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
        name = "status",
        description = "Show GitOps synchronization status"
)
public class GitOpsStatusCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var status = client.getRegistryClient().admin().gitops().status().get();
        GitOpsUtil.printStatus(output, status, outputType);
    }
}
