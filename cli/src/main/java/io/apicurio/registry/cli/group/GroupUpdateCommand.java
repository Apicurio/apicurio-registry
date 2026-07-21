package io.apicurio.registry.cli.group;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.IdUtil.isDefaultGroup;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static picocli.CommandLine.Option;

@Command(
        name = "update",
        description = "Update an existing group"
)
public class GroupUpdateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The group ID."
    )
    private String groupId;

    @Option(
            names = {"--description"},
            description = "Updated group description."
    )
    private String description;

    @Option(
            names = {"-l", "--sl", "--set-label"},
            description = "Add or update a group label (format: key=value or key). Use \\= to include = in a key."
    )
    private List<String> setLabels;

    @Option(
            names = {"--dl", "--delete-label"},
            description = "Delete an existing group label."
    )
    private List<String> deleteLabels;

    @Override
    public void run(OutputBuffer output) throws Exception {
        if (isDefaultGroup(groupId)) {
            throw new CliException("The group 'default' is implicit and cannot be updated.", VALIDATION_ERROR_RETURN_CODE);
        }
        var group = client.getRegistryClient().groups().byGroupId(groupId).get();
        var updatedGroup = new EditableGroupMetaData();
        updatedGroup.setDescription(group.getDescription());
        updatedGroup.setLabels(group.getLabels());
        if (!isBlank(description)) {
            updatedGroup.setDescription(description);
        }
        if (setLabels != null) {
            if (updatedGroup.getLabels() == null) {
                updatedGroup.setLabels(new Labels());
            }
            updatedGroup.getLabels().getAdditionalData().putAll(Conversions.parseLabels(setLabels));
        }
        if (deleteLabels != null && updatedGroup.getLabels() != null) {
            deleteLabels.forEach(key -> {
                updatedGroup.getLabels().getAdditionalData().remove(key);
            });
        }
        client.getRegistryClient().groups().byGroupId(groupId).put(updatedGroup);
        output.writeStdOutChunk(out -> {
            out.append("Group '").append(group.getGroupId()).append("' updated successfully.\n");
        });
    }
}