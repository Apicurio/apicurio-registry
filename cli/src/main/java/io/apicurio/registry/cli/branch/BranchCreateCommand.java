package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateBranch;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.branch.BranchGetCommand.printBranch;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/** Creates a new branch for an artifact, optionally with an initial list of versions. */
@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new branch"
)
public class BranchCreateCommand extends AbstractBranchCommand {

    @Parameters(
            index = "0",
            description = "The branch ID."
    )
    private String branchId;

    @Option(
            names = {"--description"},
            description = "Provide branch description."
    )
    private String description;

    @Option(
            names = {"--version"},
            paramLabel = "<version>",
            split = ",",
            description = "A version to place on the branch initially, ordered from the tip to the oldest. "
                    + "Repeat the option or use a comma-separated list to add several."
    )
    private List<String> versions;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();

        final var newBranch = new CreateBranch();
        newBranch.setBranchId(branchId);
        if (!isBlank(description)) {
            newBranch.setDescription(description);
        }
        if (versions != null) {
            newBranch.setVersions(versions);
        }

        //noinspection ConstantConditions
        final var result = convert(branches(resolvedGroupId, resolvedArtifactId).post(newBranch));
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, resolvedGroupId, resolvedArtifactId, branchId));
            case table -> output.writeStdOutChunk(out -> successMessage(out, resolvedGroupId, resolvedArtifactId, branchId));
        }
        printBranch(output, result, outputType.getOutputType());
    }

    private static void successMessage(final StringBuilder out, final String groupId,
                                       final String artifactId, final String branchId) {
        out.append("Branch '").append(branchId).append("' created successfully for artifact '")
                .append(artifactId).append("' in group '").append(groupId).append("'.\n");
    }
}
