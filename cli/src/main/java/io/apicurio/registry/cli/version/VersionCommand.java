package io.apicurio.registry.cli.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.artifact.ArtifactUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.CONTENT_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GLOBAL_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.STATE;
import static io.apicurio.registry.cli.utils.Columns.VERSION;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;

/** Lists versions for an artifact with pagination support. */
@Command(
        name = "version",
        aliases = {"versions"},
        description = "Work with artifact versions",
        subcommands = {
                VersionCreateCommand.class,
                VersionGetCommand.class,
                VersionUpdateCommand.class,
                VersionDeleteCommand.class
        }
)
public class VersionCommand extends AbstractCommand {

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

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = VersionUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            ArtifactUtil.validateGroup(registryClient, resolvedGroupId);
            //noinspection ConstantConditions
            final var versions = convert(registryClient
                    .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                    .versions().get(r -> {
                        //noinspection ConstantConditions
                        r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                        r.queryParameters.limit = pagination.getSize();
                        r.queryParameters.orderby = VersionSortBy.Version;
                        r.queryParameters.order = SortOrder.Asc;
                    }));
            output.writeStdOutChunkWithException(out -> {
                switch (outputType.getOutputType()) {
                    case json -> {
                        out.append(Mapper.MAPPER.writeValueAsString(versions));
                        out.append('\n');
                    }
                    case table -> {
                        final var table = new TableBuilder();
                        table.addColumns(
                                GROUP_ID,
                                ARTIFACT_ID,
                                VERSION,
                                NAME,
                                DESCRIPTION,
                                STATE,
                                GLOBAL_ID,
                                CONTENT_ID,
                                CREATED_ON,
                                OWNER
                        );
                        versions.getVersions().forEach(v -> {
                            table.addRow(
                                    v.getGroupId(),
                                    v.getArtifactId(),
                                    v.getVersion(),
                                    v.getName(),
                                    v.getDescription(),
                                    convertToString(v.getState()),
                                    convertToString(v.getGlobalId()),
                                    convertToString(v.getContentId()),
                                    convertToString(v.getCreatedOn()),
                                    v.getOwner()
                            );
                        });
                        table.setPagination(pagination.getPage(), pagination.getSize(), versions.getCount());
                        table.print(out);
                    }
                }
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error listing versions for artifact '")
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
