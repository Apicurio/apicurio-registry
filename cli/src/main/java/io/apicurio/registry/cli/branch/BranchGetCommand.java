package io.apicurio.registry.cli.branch;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.OutputType;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.v3.beans.BranchMetaData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.BRANCH_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.SYSTEM_DEFINED;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

/** Retrieves a branch's metadata. */
@Command(
        name = "get",
        description = "Get a branch"
)
public class BranchGetCommand extends AbstractBranchCommand {

    @Parameters(
            index = "0",
            description = "The branch ID."
    )
    private String branchId;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        //noinspection ConstantConditions
        final var branch = convert(branches(resolvedGroupId, resolvedArtifactId)
                .byBranchId(branchId).get());
        printBranch(output, branch, outputType.getOutputType());
    }

    static void printBranch(final OutputBuffer output, final BranchMetaData branch,
                            final OutputType outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(branch));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(GROUP_ID, displayGroupId(branch.getGroupId()));
                    table.addRow(ARTIFACT_ID, branch.getArtifactId());
                    table.addRow(BRANCH_ID, branch.getBranchId());
                    table.addRow(DESCRIPTION, branch.getDescription());
                    table.addRow(SYSTEM_DEFINED, convertToString(branch.getSystemDefined()));
                    table.addRow(CREATED_ON, convertToString(branch.getCreatedOn()));
                    table.addRow(OWNER, branch.getOwner());
                    table.addRow(MODIFIED_ON, convertToString(branch.getModifiedOn()));
                    table.addRow(MODIFIED_BY, branch.getModifiedBy());
                    table.print(out);
                }
            }
        });
    }
}
