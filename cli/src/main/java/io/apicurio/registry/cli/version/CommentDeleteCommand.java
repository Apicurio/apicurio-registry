package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete a comment"
)
public class CommentDeleteCommand extends AbstractCommand {

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

    @Option(
            names = {"-v", "--version"},
            description = "The version expression (e.g. 1.0.0, latest)",
            required = true
    )
    private String versionExpression;

    @Parameters(
            index = "0",
            description = "The comment ID to delete"
    )
    private String commentId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
            registryClient.groups().byGroupId(resolvedGroupId)
                    .artifacts().byArtifactId(resolvedArtifactId)
                    .versions().byVersionExpression(versionExpression)
                    .comments().byCommentId(commentId)
                    .delete();
            output.writeStdOutChunk(out -> {
                out.append("Comment '").append(commentId).append("' deleted successfully.\n");
            });
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error deleting comment '")
                        .append(commentId)
                        .append("' on version '")
                        .append(versionExpression)
                        .append("' of artifact '")
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
