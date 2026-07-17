package io.apicurio.registry.cli.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.GroupOrderMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.search.groups.GroupsRequestBuilder;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "group",
        aliases = {"groups"},
        description = "Search for groups"
)
public class SearchGroupsCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Filter by group ID (exact match)."
    )
    private String groupId;

    @Option(
            names = {"--description"},
            description = "Filter by description (substring match)."
    )
    private String description;

    @Option(
            names = {"-l", "--label"},
            description = "Filter by label (format: key=value or key). Exact match on key and value. Can be specified multiple times."
    )
    private List<String> labels;

    @Mixin
    private GroupOrderMixin ordering;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        //noinspection ConstantConditions
        final var results = convert(client.getRegistryClient().search().groups().get(r -> {
            //noinspection ConstantConditions
            applyFilters(r.queryParameters);
        }));
        printResults(output, results);
    }

    private void applyFilters(final GroupsRequestBuilder.GetQueryParameters params) {
        params.offset = (pagination.getPage() - 1) * pagination.getSize();
        params.limit = pagination.getSize();
        params.orderby = ordering.getOrderBy();
        params.order = ordering.getOrder();
        if (groupId != null) {
            params.groupId = groupId;
        }
        if (description != null) {
            params.description = description;
        }
        if (labels != null) {
            params.labels = Conversions.convertLabelsForApi(labels);
        }
    }

    private void printResults(final OutputBuffer output, final GroupSearchResults results) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(results));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(GROUP_ID, DESCRIPTION, CREATED_ON, OWNER, MODIFIED_ON, MODIFIED_BY, LABELS);
                    Optional.ofNullable(results.getGroups()).orElse(List.of()).forEach(g -> {
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
                    table.setPagination(pagination.getPage(), pagination.getSize(), results.getCount());
                    table.setSelectedColumns(columns.getColumns());
                    table.print(out);
                }
            }
        });
    }
}
