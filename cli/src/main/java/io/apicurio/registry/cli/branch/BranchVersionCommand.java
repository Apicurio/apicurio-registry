package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.version.VersionCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.utils.Conversions.convert;

/** Lists the versions on a branch with pagination support. */
@Command(
        name = "version",
        aliases = {"versions"},
        description = "Work with the versions on a branch",
        subcommands = {
                BranchVersionAddCommand.class,
                BranchVersionReplaceCommand.class
        }
)
public class BranchVersionCommand extends AbstractBranchCommand {

    @Option(
            names = {"-b", "--branch"},
            description = "The branch ID."
    )
    private String branchId;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (branchId == null) {
            throw new CliException("The branch ID is required. Provide it via --branch.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        final var branchVersions = branches(resolvedGroupId, resolvedArtifactId)
                .byBranchId(branchId).versions();
        //noinspection ConstantConditions
        final var versions = convert(branchVersions.get(r -> {
            //noinspection ConstantConditions
            r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
            r.queryParameters.limit = pagination.getSize();
        }));
        VersionCommand.printVersions(output, versions, outputType.getOutputType(),
                pagination.getPage(), pagination.getSize(), columns.getColumns());
    }
}
