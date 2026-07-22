package io.apicurio.registry.cli.search;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.search.artifacts.ArtifactsRequestBuilder;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "artifact",
        aliases = {"artifacts"},
        description = "Search for artifacts"
)
public class SearchArtifactsCommand extends AbstractCommand {

    @Option(
            names = {"--name"},
            description = "Filter by artifact name. Searches both the name and artifactId fields. Use * as prefix/suffix wildcard, otherwise matches exactly."
    )
    private String name;

    @Option(
            names = {"--description"},
            description = "Filter by description (substring match)."
    )
    private String description;

    @Option(
            names = {"-g", "--group"},
            description = "Filter by group ID (exact match)."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Filter by artifact ID (exact match)."
    )
    private String artifactId;

    @Option(
            names = {"--type"},
            description = "Filter by artifact type, exact match (e.g. AVRO, JSON, PROTOBUF, OPENAPI, ASYNCAPI). Use 'acr version' to see all supported types."
    )
    private String artifactType;

    @Option(
            names = {"-l", "--label"},
            description = "Filter by label (format: key=value or key). Exact match on key and value. Can be specified multiple times."
    )
    private List<String> labels;

    @Option(
            names = {"--global-id"},
            description = "Filter by global ID"
    )
    private Long globalId;

    @Option(
            names = {"--content-id"},
            description = "Filter by content ID"
    )
    private Long contentId;

    @Mixin
    private ArtifactOrderMixin ordering;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        //noinspection ConstantConditions
        final var results = convert(client.getRegistryClient().search().artifacts().get(r -> {
            //noinspection ConstantConditions
            applyFilters(r.queryParameters);
        }));
        SearchUtil.printArtifactResults(output, results, outputType, pagination, columns);
    }

    @Override
    public boolean supportsInteractive() {
        return true;
    }

    @Override
    public void runInteractive() throws Exception {
        final var registryClient = client.getRegistryClient();
        //noinspection ConstantConditions
        final var initialResults = convert(registryClient.search().artifacts().get(r -> {
            //noinspection ConstantConditions
            applyFilters(r.queryParameters);
            r.queryParameters.offset = 0;
        }));
        final var initialRows = java.util.Optional.ofNullable(initialResults.getArtifacts()).orElse(List.of());

        var table = new io.apicurio.registry.cli.interactive.InteractiveTable<>(
                initialRows,
                a -> java.util.Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "  " + a.getArtifactType() + "  " + a.getCreatedOn(),
                page -> {
                    //noinspection ConstantConditions
                    final var pageResults = convert(registryClient.search().artifacts().get(r -> {
                        //noinspection ConstantConditions
                        applyFilters(r.queryParameters);
                        r.queryParameters.offset = (page - 1) * pagination.getSize();
                    }));
                    final var pageRows = java.util.Optional.ofNullable(pageResults.getArtifacts()).orElse(List.of());
                    final var hasNext = (page * pagination.getSize()) < pageResults.getCount();
                    return new io.apicurio.registry.cli.interactive.InteractiveTable.PageResult<>(pageRows, hasNext);
                }
        );

        var selected = table.run();
        if (selected == null) {
            return;
        }

        var a = selected.row();
        if (selected.action() == io.apicurio.registry.cli.interactive.InteractiveTable.Action.VIEW) {
            config.getStdOut().print("Group:        " + io.apicurio.registry.cli.common.IdUtil.displayGroupId(a.getGroupId()) + "\n");
            config.getStdOut().print("Artifact ID:  " + a.getArtifactId() + "\n");
            config.getStdOut().print("Name:         " + java.util.Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "\n");
            config.getStdOut().print("Type:         " + a.getArtifactType() + "\n");
            config.getStdOut().print("Description:  " + java.util.Optional.ofNullable(a.getDescription()).orElse("") + "\n");
            config.getStdOut().print("Created:      " + a.getCreatedOn() + "\n");
        } else if (selected.action() == io.apicurio.registry.cli.interactive.InteractiveTable.Action.DELETE) {
            var deleteGroupId = java.util.Optional.ofNullable(a.getGroupId()).orElse("default");
            registryClient.groups().byGroupId(deleteGroupId)
                    .artifacts().byArtifactId(a.getArtifactId()).delete();
            config.getStdOut().print("Artifact '" + a.getArtifactId() + "' in group '"
                    + deleteGroupId + "' deleted successfully.\n");
        }
    }

    private void applyFilters(final ArtifactsRequestBuilder.GetQueryParameters params) {
        params.offset = (pagination.getPage() - 1) * pagination.getSize();
        params.limit = pagination.getSize();
        params.orderby = ordering.getOrderBy();
        params.order = ordering.getOrder();
        if (name != null) {
            params.name = name;
        }
        if (description != null) {
            params.description = description;
        }
        if (groupId != null) {
            params.groupId = groupId;
        }
        if (artifactId != null) {
            params.artifactId = artifactId;
        }
        if (artifactType != null) {
            params.artifactType = artifactType;
        }
        if (labels != null) {
            params.labels = Conversions.convertLabelsForApi(labels);
        }
        if (globalId != null) {
            params.globalId = globalId;
        }
        if (contentId != null) {
            params.contentId = contentId;
        }
    }

}