package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

/** Deletes an existing artifact from a group. */
@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete an existing artifact. Apicurio Registry must be configured " +
                "with `apicurio.rest.deletion.artifact.enabled=true` to allow artifact deletions."
)
public class ArtifactDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The artifact ID."
    )
    private String artifactId;

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            registryClient.groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId).delete();
            output.writeStdOutChunk(out -> {
                out.append("Artifact '").append(artifactId).append("' in group '")
                        .append(resolvedGroupId).append("' deleted successfully.\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting artifact '")
                        .append(artifactId)
                        .append("' in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
