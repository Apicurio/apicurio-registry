package io.apicurio.registry.cli.gitops;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.GitOpsValidateRequest;
import io.apicurio.registry.rest.client.models.GitOpsValidateRequestType;
import io.apicurio.registry.rest.client.models.GitOpsValidateTaskResult;
import io.apicurio.registry.rest.client.models.GitOpsValidateTaskState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

@Command(
        name = "validate",
        description = "Validate a Git ref without affecting the live registry"
)
public class GitOpsValidateCommand extends AbstractCommand {

    @Option(names = "--repo", required = true, description = "Repository ID to validate against.")
    private String repoId;

    @Option(names = "--ref", required = true, description = "Git ref to validate (branch, tag, or PR ref).")
    private String ref;

    @Option(names = "--no-wait", description = "Return immediately with the task ID instead of waiting.")
    private boolean noWait;

    @Option(names = "--timeout", description = "Timeout in seconds when waiting for validation.",
            defaultValue = "300")
    private int timeout;

    @Option(names = "--cleanup", negatable = true, defaultValue = "true",
            description = "Delete the validation task after completion.")
    private boolean cleanup;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var request = new GitOpsValidateRequest();
        request.setType(GitOpsValidateRequestType.Pull);
        request.setRepoId(repoId);
        request.setRef(ref);

        var task = client.getRegistryClient().admin().gitops().validate().post(request);
        final var taskId = task.getTaskId();

        if (noWait) {
            GitOpsUtil.printValidateTask(output, task, outputType);
            return;
        }

        try {
            final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeout);

            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(GitOpsUtil.POLL_INTERVAL_MS);
                task = client.getRegistryClient().admin().gitops().validate().byTaskId(taskId).get();
                final var state = task.getState();

                if (state == GitOpsValidateTaskState.Completed || state == GitOpsValidateTaskState.Failed) {
                    break;
                }
            }

            final var state = task.getState();
            if (state != GitOpsValidateTaskState.Completed && state != GitOpsValidateTaskState.Failed) {
                throw new CliException("Validation timed out after " + timeout + " seconds. Task ID: " + taskId,
                        APPLICATION_ERROR_RETURN_CODE);
            }

            GitOpsUtil.printValidateTask(output, task, outputType);

            if (state == GitOpsValidateTaskState.Failed) {
                CliException.exitQuietError(APPLICATION_ERROR_RETURN_CODE);
            }
            if (task.getResult() == GitOpsValidateTaskResult.Failure) {
                CliException.exitQuietError(APPLICATION_ERROR_RETURN_CODE);
            }
        } finally {
            if (cleanup) {
                try {
                    client.getRegistryClient().admin().gitops().validate().byTaskId(taskId).delete();
                } catch (Exception ex) {
                    // Cleanup is best-effort; the server will expire the task eventually.
                }
            }
        }
    }
}
