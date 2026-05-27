package io.apicurio.registry.cli.common;

import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import lombok.Getter;
import picocli.CommandLine.Option;

public class GroupOrderMixin {

    @Option(
            names = {"--order-by"},
            description = "Sort field (GroupId, CreatedOn, ModifiedOn). Default: GroupId.",
            defaultValue = "GroupId"
    )
    @Getter
    private GroupSortBy orderBy;

    @Option(
            names = {"--order"},
            description = "Sort order (Asc, Desc). Default: Asc.",
            defaultValue = "Asc"
    )
    @Getter
    private SortOrder order;
}
