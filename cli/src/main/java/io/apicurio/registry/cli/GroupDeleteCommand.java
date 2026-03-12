package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.SortOrder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static java.util.Optional.ofNullable;

@Command(
        name = "delete",
        aliases = {"remove", "rm"},
        description = "Delete an existing group. Apicurio Registry must be configured " +
                "with `apicurio.rest.deletion.group.enabled=true` to allow group deletions."
)
public class GroupDeleteCommand extends AbstractCommand {

    @Parameters(
            index = "0"
    )
    private String groupId;

    @Option(
            names = {"--force"},
            description = "Force deletion even if the group is not empty (contains artifacts).",
            defaultValue = "false"
    )
    private boolean force;

    @Override
    public void run(OutputBuffer output) throws Exception {
        try {
            // Check if the group exists
            Client.getInstance().getRegistryClient().groups().byGroupId(groupId).get();

            // Check if the group has artifacts
            var artifacts = Client.getInstance().getRegistryClient().groups().byGroupId(groupId).artifacts().get(r -> {
                //noinspection ConstantConditions
                r.queryParameters.offset = 0;
                r.queryParameters.limit = 1;
                r.queryParameters.orderby = ArtifactSortBy.ArtifactId;
                r.queryParameters.order = SortOrder.Asc;
            });
            //noinspection ConstantConditions
            var artifactCount = ofNullable(artifacts.getCount()).orElseThrow(
                    () -> new CliException(
                            "Invalid response from server. Unable to determine artifact count for group '" + groupId + "'.",
                            APPLICATION_ERROR_RETURN_CODE
                    )
            );
            if (artifactCount > 0 && !force) {
                throw new CliException(
                        "Group '" + groupId + "' is not empty (contains " + artifactCount + " artifact(s)). " +
                                "Use --force to delete the group and all its artifacts.",
                        VALIDATION_ERROR_RETURN_CODE
                );
            }

            // Delete the group
            Client.getInstance().getRegistryClient().groups().byGroupId(groupId).delete();

            output.writeStdOutChunk(out -> {
                out.append("Group '").append(groupId).append("' deleted successfully");
                if (artifactCount > 0) {
                    out.append(" (including ").append(artifactCount).append(" artifact(s))");
                }
                out.append(".\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err ->
                    err.append("Error deleting group '")
                            .append(groupId)
                            .append("': ")
                            .append(ex.getDetail())
                            .append('\n')
            );
            exitQuietServerError();
        }
    }
}
