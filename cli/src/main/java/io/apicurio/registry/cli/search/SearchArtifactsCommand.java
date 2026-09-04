package io.apicurio.registry.cli.search;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.InteractiveMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.InteractiveUtil;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.search.artifacts.ArtifactsRequestBuilder;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import java.util.List;
import java.util.Optional;
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
    private InteractiveMixin interactive;

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
        final var results = fetchPage(pagination.getPage());
        SearchUtil.printArtifactResults(output, results, outputType, pagination, columns);
    }

    @Override
    public boolean supportsInteractive() {
        return true;
    }

    @Override
    public void runInteractive(OutputBuffer output) {
        final var registryClient = client.getRegistryClient();

        InteractiveUtil.runInteractive(
                this::fetchPage,
                a -> {
                    // Cross-group safety guard: search results may include default-group artifacts
                    // (groupId == null) without a --group flag. Unlike ArtifactCommand which always
                    // has a resolvedGroupId from its parent scope, search has no implicit group context.
                    if (a.getGroupId() == null && groupId == null) {
                        throw new CliException("Cannot delete artifact '" + a.getArtifactId()
                                + "': artifact has no explicit groupId and no --group was specified.");
                    }
                    var deleteGroupId = Optional.ofNullable(a.getGroupId())
                            .orElseGet(() -> IdUtil.resolveGroupId(groupId, config));
                    registryClient.groups().byGroupId(deleteGroupId)
                            .artifacts().byArtifactId(a.getArtifactId()).delete();
                },
                a -> IdUtil.displayGroupId(a.getGroupId()),
                pagination.getSize(),
                outputType.getOutputType(),
                output
        );
    }

    private void applyFilters(final ArtifactsRequestBuilder.GetQueryParameters params, int page) {
        params.offset = (page - 1) * pagination.getSize();
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

    private ArtifactSearchResults fetchPage(int page) {
        //noinspection ConstantConditions
        return convert(client.getRegistryClient().search().artifacts().get(r -> {
            //noinspection ConstantConditions
            applyFilters(r.queryParameters, page);
        }));
    }
}
