package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.BRANCH_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.SYSTEM_DEFINED;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;

/** Lists branches for an artifact with pagination support. */
@Command(
        name = "branch",
        aliases = {"branches"},
        description = "Work with artifact branches",
        subcommands = {
                BranchCreateCommand.class,
                BranchDeleteCommand.class,
                BranchGetCommand.class,
                BranchUpdateCommand.class,
                BranchVersionCommand.class
        }
)
public class BranchCommand extends AbstractBranchCommand {

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        //noinspection ConstantConditions
        final var branchResults = convert(branches(resolvedGroupId, resolvedArtifactId).get(r -> {
            //noinspection ConstantConditions
            r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
            r.queryParameters.limit = pagination.getSize();
        }));
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(Mapper.MAPPER.writeValueAsString(branchResults));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(
                            GROUP_ID,
                            ARTIFACT_ID,
                            BRANCH_ID,
                            DESCRIPTION,
                            SYSTEM_DEFINED,
                            CREATED_ON,
                            OWNER,
                            MODIFIED_ON,
                            MODIFIED_BY
                    );
                    branchResults.getBranches().forEach(b -> {
                        table.addRow(
                                displayGroupId(b.getGroupId()),
                                b.getArtifactId(),
                                b.getBranchId(),
                                b.getDescription(),
                                convertToString(b.getSystemDefined()),
                                convertToString(b.getCreatedOn()),
                                b.getOwner(),
                                convertToString(b.getModifiedOn()),
                                b.getModifiedBy()
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), branchResults.getCount());
                    table.setSelectedColumns(columns.getColumns());
                    table.print(out);
                }
            }
        });
    }
}
