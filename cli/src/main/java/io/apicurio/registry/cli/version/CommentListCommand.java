package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.COMMENT;
import static io.apicurio.registry.cli.utils.Columns.COMMENT_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "list",
        aliases = {"ls"},
        description = "List all comments for an artifact version"
)
public class CommentListCommand extends AbstractCommand {

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

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
            IdUtil.validateVersion(registryClient, resolvedGroupId, resolvedArtifactId, versionExpression);
            final var comments = registryClient.groups().byGroupId(resolvedGroupId)
                    .artifacts().byArtifactId(resolvedArtifactId)
                    .versions().byVersionExpression(versionExpression)
                    .comments().get();
            final List<Comment> convertedComments = comments != null
                    ? comments.stream().map(Conversions::convert).collect(Collectors.toList())
                    : List.of();
            output.writeStdOutChunkWithException(out -> {
                switch (outputType.getOutputType()) {
                    case json -> {
                        out.append(MAPPER.writeValueAsString(convertedComments));
                        out.append('\n');
                    }
                    case table -> {
                        final var table = new TableBuilder();
                        table.addColumns(COMMENT_ID, COMMENT, OWNER, CREATED_ON);
                        convertedComments.forEach(c -> {
                            table.addRow(
                                    c.getCommentId(),
                                    c.getValue(),
                                    c.getOwner(),
                                    convertToString(c.getCreatedOn())
                            );
                        });
                        table.print(out);
                    }
                }
            });
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error listing comments for version '")
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
