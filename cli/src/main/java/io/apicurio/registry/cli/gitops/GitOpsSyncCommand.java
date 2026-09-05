package io.apicurio.registry.cli.gitops;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

@Command(
        name = "sync",
        description = "Trigger an immediate GitOps synchronization"
)
public class GitOpsSyncCommand extends AbstractCommand {

    @Option(names = "--wait", description = "Wait for synchronization to complete.")
    private boolean wait;

    @Option(names = "--timeout", description = "Timeout in seconds when waiting.", defaultValue = "300")
    private int timeout;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        client.getRegistryClient().admin().gitops().sync().post();

        if (!wait) {
            output.writeStdOutChunk(out -> out.append("Synchronization requested.\n"));
            return;
        }

        final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeout);
        Thread.sleep(GitOpsUtil.POLL_INITIAL_DELAY_MS);

        while (System.currentTimeMillis() < deadline) {
            final var status = client.getRegistryClient().admin().gitops().status().get();
            final var syncState = status.getSyncState();

            if (GitOpsUtil.SYNC_STATE_IDLE.equals(syncState)) {
                GitOpsUtil.printStatus(output, status, outputType);
                return;
            }
            if (GitOpsUtil.SYNC_STATE_ERROR.equals(syncState)) {
                GitOpsUtil.printStatus(output, status, outputType);
                throw new CliException("Synchronization failed.", APPLICATION_ERROR_RETURN_CODE);
            }

            Thread.sleep(GitOpsUtil.POLL_INTERVAL_MS);
        }

        throw new CliException("Synchronization timed out after " + timeout + " seconds.",
                APPLICATION_ERROR_RETURN_CODE);
    }
}
