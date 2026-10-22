package io.apicurio.registry.cli.common;

import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import lombok.Getter;
import picocli.CommandLine.Option;

public class VersionOrderMixin {

    @Option(
            names = {"--order-by"},
            description = "Sort field (GroupId, ArtifactId, Version, Name, CreatedOn, ModifiedOn, GlobalId). Default: Version.",
            defaultValue = "Version"
    )
    @Getter
    private VersionSortBy orderBy;

    @Option(
            names = {"--order"},
            description = "Sort order (Asc, Desc). Default: Asc.",
            defaultValue = "Asc"
    )
    @Getter
    private SortOrder order;
}
