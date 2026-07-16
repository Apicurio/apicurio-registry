package io.apicurio.registry.cli.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.common.VersionOrderMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.search.versions.VersionsRequestBuilder;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CONTENT_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GLOBAL_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.STATE;
import static io.apicurio.registry.cli.utils.Columns.VERSION;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "version",
        aliases = {"versions"},
        description = "Search for versions"
)
public class SearchVersionsCommand extends AbstractCommand {

    @Option(
            names = {"--name"},
            description = "Filter by version name. Searches both the name and artifactId fields. Use * as prefix/suffix wildcard, otherwise matches exactly."
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
            names = {"-v", "--version"},
            description = "Filter by version expression (exact match)."
    )
    private String version;

    @Option(
            names = {"--type"},
            description = "Filter by artifact type, exact match (e.g. AVRO, JSON, PROTOBUF, OPENAPI, ASYNCAPI). Use 'acr version' to see all supported types."
    )
    private String artifactType;

    @Option(
            names = {"--state"},
            description = "Filter by version state (ENABLED, DISABLED, DEPRECATED)."
    )
    private VersionState state;

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
    private VersionOrderMixin ordering;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Mixin
    private ColumnsMixin columns;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        //noinspection ConstantConditions
        final var results = convert(client.getRegistryClient().search().versions().get(r -> {
            //noinspection ConstantConditions
            applyFilters(r.queryParameters);
        }));
        printResults(output, results);
    }

    private void applyFilters(final VersionsRequestBuilder.GetQueryParameters params) {
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
        if (version != null) {
            params.version = version;
        }
        if (artifactType != null) {
            params.artifactType = artifactType;
        }
        if (state != null) {
            params.state = state;
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

    private void printResults(final OutputBuffer output, final VersionSearchResults results) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(results));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(GROUP_ID, ARTIFACT_ID, VERSION, NAME, ARTIFACT_TYPE,
                            STATE, GLOBAL_ID, CONTENT_ID, DESCRIPTION, CREATED_ON, OWNER);
                    Optional.ofNullable(results.getVersions()).orElse(List.of()).forEach(v -> {
                        table.addRow(
                                v.getGroupId(),
                                v.getArtifactId(),
                                v.getVersion(),
                                v.getName(),
                                v.getArtifactType(),
                                convertToString(v.getState()),
                                convertToString(v.getGlobalId()),
                                convertToString(v.getContentId()),
                                v.getDescription(),
                                convertToString(v.getCreatedOn()),
                                v.getOwner()
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), results.getCount());
                    table.selectColumns(columns.getColumns());
                    table.print(out);
                }
            }
        });
    }
}
