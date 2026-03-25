package io.apicurio.registry.cli.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.SortOrder;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;

/**
 * Lists artifacts in a group with pagination support.
 * When no group is specified, falls back to the context groupId or "default".
 */
@Command(
        name = "artifact",
        aliases = {"artifacts"},
        description = "Work with artifacts",
        subcommands = {
                ArtifactCreateCommand.class,
                ArtifactUpdateCommand.class,
                ArtifactGetCommand.class,
                ArtifactDeleteCommand.class
        }
)
public class ArtifactCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @ParentCommand
    @Getter
    private Acr parent;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId);
        try {
            final var client = Client.getInstance().getRegistryClient();
            ArtifactUtil.validateGroup(client, resolvedGroupId);
            //noinspection ConstantConditions
            final var artifacts = convert(client
                    .groups().byGroupId(resolvedGroupId).artifacts().get(r -> {
                        //noinspection ConstantConditions
                        r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                        r.queryParameters.limit = pagination.getSize();
                        r.queryParameters.orderby = ArtifactSortBy.ArtifactId;
                        r.queryParameters.order = SortOrder.Asc;
                    }));
            output.writeStdOutChunkWithException(out -> {
                switch (outputType.getOutputType()) {
                    case json -> {
                        out.append(Mapper.MAPPER.writeValueAsString(artifacts));
                        out.append('\n');
                    }
                    case table -> {
                        final var table = new TableBuilder();
                        table.addColumns(
                                GROUP_ID,
                                ARTIFACT_ID,
                                NAME,
                                ARTIFACT_TYPE,
                                DESCRIPTION,
                                CREATED_ON,
                                OWNER,
                                MODIFIED_ON,
                                MODIFIED_BY,
                                LABELS
                        );
                        artifacts.getArtifacts().forEach(a -> {
                            table.addRow(
                                    a.getGroupId(),
                                    a.getArtifactId(),
                                    a.getName(),
                                    a.getArtifactType(),
                                    a.getDescription(),
                                    convertToString(a.getCreatedOn()),
                                    a.getOwner(),
                                    convertToString(a.getModifiedOn()),
                                    a.getModifiedBy(),
                                    convertToString(a.getLabels())
                            );
                        });
                        table.setPagination(pagination.getPage(), pagination.getSize(), artifacts.getCount());
                        table.print(out);
                    }
                }
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error listing artifacts in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
