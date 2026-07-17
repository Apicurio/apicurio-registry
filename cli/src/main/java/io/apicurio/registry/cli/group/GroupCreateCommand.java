package io.apicurio.registry.cli.group;

import io.apicurio.registry.cli.common.AbstractCommand;
<<<<<<< HEAD
import io.apicurio.registry.cli.common.IdUtil;
=======
import io.apicurio.registry.cli.common.CliException;
>>>>>>> main
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.Labels;
import java.util.HashMap;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.IdUtil.isDefaultGroup;
import static io.apicurio.registry.cli.group.GroupGetCommand.printGroup;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static picocli.CommandLine.Option;

@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new group"
)
public class GroupCreateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The group ID."
    )
    private String groupId;

    @Option(
            names = {"-d", "--description"},
            description = "Provide group description."
    )
    private String description;

    @Option(
            names = {"-l", "--label"},
            description = "Provide a list of group labels (format: key=value or key). Use \\= to include = in a key."
    )
    private List<String> labels;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(OutputBuffer output) throws Exception {
        if (isDefaultGroup(groupId)) {
            throw new CliException("The group 'default' is reserved and cannot be created.", VALIDATION_ERROR_RETURN_CODE);
        }
        var newGroup = new CreateGroup();
        newGroup.setGroupId(groupId);
        if (!isBlank(description)) {
            newGroup.setDescription(description);
        }
        if (labels != null) {
            var newLabels = new Labels();
            newLabels.setAdditionalData(new HashMap<>(Conversions.parseLabels(labels)));
            newGroup.setLabels(newLabels);
        }
        var group = convert(client.getRegistryClient().groups().post(newGroup));
        IdUtil.updateGroupContext(group.getGroupId(), config);
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, group.getGroupId()));
            case table -> output.writeStdOutChunk(out -> successMessage(out, group.getGroupId()));
        }
        printGroup(output, group, outputType);
    }

    private static void successMessage(StringBuilder out, String groupId) {
        out.append("Group '").append(groupId).append("' created successfully.\n");
    }
}