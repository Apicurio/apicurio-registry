package io.apicurio.registry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;

import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;

@Command(
        name = "group",
        aliases = {"groups"},
        description = "Work with groups",
        subcommands = {
                GroupCreateCommand.class,
                GroupUpdateCommand.class,
                GroupGetCommand.class,
                GroupDeleteCommand.class
        }
)
public class GroupCommand extends AbstractCommand {

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @ParentCommand
    @Getter
    private Acr parent;

    @Override
    public void run(OutputBuffer output) throws JsonProcessingException {
        //noinspection ConstantConditions
        var groups = convert(Client.getInstance().getRegistryClient().groups().get(r -> {
            //noinspection ConstantConditions
            r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
            r.queryParameters.limit = pagination.getSize();
            r.queryParameters.orderby = GroupSortBy.GroupId;
            r.queryParameters.order = SortOrder.Asc;
        }));
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(Mapper.MAPPER.writeValueAsString(groups));
                    out.append('\n');
                }
                case table -> {
                    var table = new TableBuilder();
                    table.addColumns(
                            GROUP_ID,
                            DESCRIPTION,
                            CREATED_ON,
                            OWNER,
                            MODIFIED_ON,
                            MODIFIED_BY,
                            LABELS
                    );
                    groups.getGroups().forEach(g -> {
                        table.addRow(
                                g.getGroupId(),
                                g.getDescription(),
                                convertToString(g.getCreatedOn()),
                                g.getOwner(),
                                convertToString(g.getModifiedOn()),
                                g.getModifiedBy(),
                                convertToString(g.getLabels())
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), groups.getCount());
                    table.print(out);
                }
            }
        });
    }
}
