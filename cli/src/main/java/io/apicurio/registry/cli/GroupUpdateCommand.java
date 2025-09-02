package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static picocli.CommandLine.Option;

@Command(
        name = "update",
        description = "Update an existing group"
)
public class GroupUpdateCommand extends AbstractCommand {

    @Parameters(
            index = "0"
    )
    private String groupId;

    @Option(
            names = {"-d", "--description"},
            description = "Updated group description."
    )
    private String description;

    @Option(
            names = {"-l", "--sl", "--set-label"},
            description = "Add or update a group label.",
            mapFallbackValue = ""
    )
    private Map<String, String> setLabels;

    @Option(
            names = {"--dl", "--delete-label"},
            description = "Delete an existing group label."
    )
    private List<String> deleteLabels;

    @Override
    public void run(OutputBuffer output) throws Exception {
        try {
            var group = Client.getInstance().getRegistryClient().groups().byGroupId(groupId).get();
            var updatedGroup = new EditableGroupMetaData();
            updatedGroup.setDescription(group.getDescription());
            updatedGroup.setLabels(group.getLabels());
            if (!isBlank(description)) {
                updatedGroup.setDescription(description);
            }
            if (setLabels != null) {
                updatedGroup.getLabels().getAdditionalData().putAll(setLabels);
            }
            if (deleteLabels != null) {
                deleteLabels.forEach(key -> {
                    updatedGroup.getLabels().getAdditionalData().remove(key);
                });
            }
            Client.getInstance().getRegistryClient().groups().byGroupId(groupId).put(updatedGroup);
            output.writeStdOutChunk(out -> {
                out.append("Group '").append(group.getGroupId()).append("' updated successfully.\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error updating group '")
                        .append(groupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}