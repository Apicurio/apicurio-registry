package io.apicurio.registry.cli.branch;

import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.AddVersionToBranch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Adds a version to the tip of a branch. */
@Command(
        name = "add",
        description = "Add a version to a branch"
)
public class BranchVersionAddCommand extends AbstractBranchVersionCommand {

    @Parameters(
            index = "0",
            description = "The version to add to the tip of the branch."
    )
    private String version;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = resolvedGroupId();
        final var resolvedArtifactId = resolvedArtifactId();
        final var body = new AddVersionToBranch();
        body.setVersion(version);
        branchVersions(resolvedGroupId, resolvedArtifactId).post(body);
        output.writeStdOutChunk(out -> {
            out.append("Version '").append(version).append("' added to branch '")
                    .append(branchId).append("' for artifact '").append(resolvedArtifactId)
                    .append("' in group '").append(resolvedGroupId).append("'.\n");
        });
    }
}
