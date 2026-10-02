package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ReplaceBranchVersions;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Replaces the full list of versions on a branch. */
@Command(
        name = "replace",
        description = "Replace the list of versions on a branch"
)
public class BranchVersionReplaceCommand extends AbstractBranchVersionCommand {

    @Parameters(
            index = "0",
            arity = "1..*",
            paramLabel = "<version>",
            description = "The new, ordered list of versions for the branch, from the tip to the oldest."
    )
    private List<String> versions;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        final var body = new ReplaceBranchVersions();
        body.setVersions(versions);
        branchVersions(resolvedGroupId, resolvedArtifactId).put(body);
        output.writeStdOutChunk(out -> {
            out.append("Versions on branch '").append(branchId).append("' for artifact '")
                    .append(resolvedArtifactId).append("' in group '").append(resolvedGroupId)
                    .append("' replaced successfully.\n");
        });
    }
}
