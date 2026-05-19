package io.apicurio.registry.cli.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.search.artifacts.ArtifactsRequestBuilder;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

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

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        try {
            //noinspection ConstantConditions
            final var results = convert(client.getRegistryClient().search().artifacts().get(r -> {
                //noinspection ConstantConditions
                applyFilters(r.queryParameters);
            }));
            printResults(output, results);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error searching for artifacts: ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
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

    private void printResults(final OutputBuffer output, final ArtifactSearchResults results) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(results));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(GROUP_ID, ARTIFACT_ID, NAME, ARTIFACT_TYPE, DESCRIPTION,
                            CREATED_ON, OWNER, MODIFIED_ON, MODIFIED_BY, LABELS);
                    results.getArtifacts().forEach(a -> {
                        table.addRow(
                                a.getGroupId(),
                                a.getArtifactId(),
                                a.getName(),
                                a.getArtifactType(),
                                a.getDescription(),
                                convertToString(a.getCreatedOn()),
                                a.getOwner(),
                                convertToString(a.getModifiedOn()),
                                a.getModifiedBy(),
                                convertToString(a.getLabels())
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), results.getCount());
                    table.print(out);
                }
            }
        });
    }
}
