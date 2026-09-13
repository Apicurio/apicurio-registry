package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.utils.OutputBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Deletes a branch from an artifact. System-defined branches cannot be deleted. */
@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete a branch"
)
public class BranchDeleteCommand extends AbstractBranchCommand {

    @Parameters(
            index = "0",
            description = "The branch ID."
    )
    private String branchId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        branches(resolvedGroupId, resolvedArtifactId).byBranchId(branchId).delete();
        output.writeStdOutChunk(out -> {
            out.append("Branch '").append(branchId).append("' for artifact '")
                    .append(resolvedArtifactId).append("' in group '")
                    .append(resolvedGroupId).append("' deleted successfully.\n");
        });
    }
}
