package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.artifact.ArtifactUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

/** Deletes a version from an artifact. */
@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete a version. Apicurio Registry must be configured with " +
                "`apicurio.rest.deletion.artifact-version.enabled=true` to allow version deletions."
)
public class VersionDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The version expression (e.g. '1.0.0')."
    )
    private String versionExpression;

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID. If not provided, uses the artifactId from the current context."
    )
    private String artifactId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = VersionUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            ArtifactUtil.validateGroup(registryClient, resolvedGroupId);
            ArtifactUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
            registryClient.groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                    .versions().byVersionExpression(versionExpression).delete();
            output.writeStdOutChunk(out -> {
                out.append("Version '").append(versionExpression).append("' for artifact '")
                        .append(resolvedArtifactId).append("' in group '")
                        .append(resolvedGroupId).append("' deleted successfully.\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting version '")
                        .append(versionExpression)
                        .append("' for artifact '")
                        .append(resolvedArtifactId)
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
