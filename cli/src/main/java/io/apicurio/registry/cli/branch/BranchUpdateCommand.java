package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableBranchMetaData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Updates a branch's metadata (description). */
@Command(
        name = "update",
        description = "Update a branch"
)
public class BranchUpdateCommand extends AbstractBranchCommand {

    @Parameters(
            index = "0",
            description = "The branch ID."
    )
    private String branchId;

    @Option(
            names = {"--description"},
            description = "Updated branch description."
    )
    private String description;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (description == null) {
            throw new CliException("At least one update option is required (--description).",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        final var updatedBranch = new EditableBranchMetaData();
        updatedBranch.setDescription(description);
        branches(resolvedGroupId, resolvedArtifactId).byBranchId(branchId).put(updatedBranch);
        output.writeStdOutChunk(out -> {
            out.append("Branch '").append(branchId).append("' for artifact '")
                    .append(resolvedArtifactId).append("' in group '")
                    .append(resolvedGroupId).append("' updated successfully.\n");
        });
    }
}
