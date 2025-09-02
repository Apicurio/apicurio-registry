package io.apicurio.registry.cli;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.util.Map;

import static io.apicurio.registry.cli.GroupGetCommand.printGroup;
import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
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
            index = "0"
    )
    private String groupId;

    @Option(
            names = {"-d", "--description"},
            description = "Provide group description."
    )
    private String description;

    @Option(
            names = {"-l", "--label"},
            description = "Provide a list of group labels.",
            mapFallbackValue = ""
    )
    private Map<String, String> labels;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    @SuppressWarnings("unchecked")
    public void run(OutputBuffer output) throws Exception {
        var newGroup = new CreateGroup();
        newGroup.setGroupId(groupId);
        if (!isBlank(description)) {
            newGroup.setDescription(description);
        }
        if (labels != null) {
            var newLabels = new Labels();
            newLabels.setAdditionalData((Map<String, Object>) (Map<String, ?>) labels);
            newGroup.setLabels(newLabels);
        }
        try {
            var group = convert(Client.getInstance().getRegistryClient().groups().post(newGroup));
            output.writeStdErrChunk(out -> {
                out.append("Group '").append(group.getGroupId()).append("' created successfully.\n");
            });
            printGroup(output, group, outputType);
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error creating a new group '")
                        .append(groupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}