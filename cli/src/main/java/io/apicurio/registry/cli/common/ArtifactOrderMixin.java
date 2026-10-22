package io.apicurio.registry.cli.common;

import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.SortOrder;
import lombok.Getter;
import picocli.CommandLine.Option;

public class ArtifactOrderMixin {

    @Option(
            names = {"--order-by"},
            description = "Sort field (GroupId, ArtifactId, CreatedOn, ModifiedOn, ArtifactType, Name). Default: ArtifactId.",
            defaultValue = "ArtifactId"
    )
    @Getter
    private ArtifactSortBy orderBy;

    @Option(
            names = {"--order"},
            description = "Sort order (Asc, Desc). Default: Asc.",
            defaultValue = "Asc"
    )
    @Getter
    private SortOrder order;
}
