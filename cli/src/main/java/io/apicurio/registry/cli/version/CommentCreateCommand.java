package io.apicurio.registry.cli.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.NewComment;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.Comment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.COMMENT;
import static io.apicurio.registry.cli.utils.Columns.COMMENT_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Add a comment to an artifact version"
)
public class CommentCreateCommand extends AbstractCommand {

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

    @Option(
            names = {"-m", "--message"},
            description = "The comment text. Use '-' to read from stdin.",
            required = true
    )
    private String commentText;

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
            final var resolvedText = "-".equals(commentText)
                    ? new String(System.in.readAllBytes()).trim()
                    : commentText;
            final var newComment = new NewComment();
            newComment.setValue(resolvedText);
            //noinspection ConstantConditions
            final var created = convert(registryClient.groups().byGroupId(resolvedGroupId)
                    .artifacts().byArtifactId(resolvedArtifactId)
                    .versions().byVersionExpression(versionExpression)
                    .comments().post(newComment));
            switch (outputType.getOutputType()) {
                case json -> output.writeStdErrChunk(out -> out.append("Comment added successfully.\n"));
                case table -> output.writeStdOutChunk(out -> out.append("Comment added successfully.\n"));
            }
            printComment(output, created, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error adding comment to version '")
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

    static void printComment(final OutputBuffer output, final Comment comment,
                             final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(comment));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(COMMENT_ID, comment.getCommentId());
                    table.addRow(COMMENT, comment.getValue());
                    table.addRow(OWNER, comment.getOwner());
                    table.addRow(CREATED_ON, convertToString(comment.getCreatedOn()));
                    table.print(out);
                }
            }
        });
    }
}
