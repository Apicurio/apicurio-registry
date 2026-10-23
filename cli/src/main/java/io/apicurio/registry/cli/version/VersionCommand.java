package io.apicurio.registry.cli.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputType;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.common.VersionOrderMixin;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

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
import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;

/** Lists versions for an artifact with pagination support. */
@Command(
        name = "version",
        aliases = {"versions"},
        description = "Work with artifact versions",
        subcommands = {
                CommentCommand.class,
                VersionCreateCommand.class,
                VersionDeleteCommand.class,
                VersionGetCommand.class,
                VersionUpdateCommand.class
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
    private VersionOrderMixin ordering;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        //noinspection ConstantConditions
        final var versions = convert(registryClient
                .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                .versions().get(r -> {
                    //noinspection ConstantConditions
                    r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                    r.queryParameters.limit = pagination.getSize();
                    r.queryParameters.orderby = ordering.getOrderBy();
                    r.queryParameters.order = ordering.getOrder();
                }));
        printVersions(output, versions, outputType.getOutputType(),
                pagination.getPage(), pagination.getSize(), columns.getColumns());
    }

    /**
     * Renders a page of version search results as JSON or as a table, applying the given
     * pagination footer and column selection. Shared by the artifact and branch version
     * listing commands.
     */
    public static void printVersions(final OutputBuffer output, final VersionSearchResults versions,
                                     final OutputType outputType, final int page, final int size,
                                     final List<String> selectedColumns) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType) {
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
                                displayGroupId(v.getGroupId()),
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
                    table.setPagination(page, size, versions.getCount());
                    table.setSelectedColumns(selectedColumns);
                    table.print(out);
                }
            }
        });
    }
}
