package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "get",
        description = "Get an existing group"
)
public class GroupGetCommand extends AbstractCommand {

    @Parameters(
            index = "0"
    )
    private String groupId;

    @Mixin
    private OutputTypeMixin outputType;

    @ParentCommand
    @Getter
    private GroupCommand parent;

    @Override
    public void run(OutputBuffer output) throws JsonProcessingException {
        try {
            //noinspection ConstantConditions
            var group = convert(Client.getInstance().getRegistryClient().groups().byGroupId(groupId).get());
            // TODO: Should we include the `default` group in the list?
            printGroup(output, group, outputType);
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving group '")
                        .append(groupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }

    static void printGroup(OutputBuffer output, GroupMetaData group, OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(group));
                    out.append('\n');
                }
                case table -> {
                    var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(GROUP_ID, group.getGroupId());
                    table.addRow(DESCRIPTION, group.getDescription());
                    table.addRow(CREATED_ON, convertToString(group.getCreatedOn()));
                    table.addRow(OWNER, group.getOwner());
                    table.addRow(MODIFIED_ON, convertToString(group.getModifiedOn()));
                    table.addRow(MODIFIED_BY, group.getModifiedBy());
                    table.addRow(LABELS, convertToString(group.getLabels()));
                    table.print(out);
                }
            }
        });
    }
}