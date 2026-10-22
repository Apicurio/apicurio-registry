package io.apicurio.registry.cli.search;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.utils.ContentTypeDetector;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import java.io.ByteArrayInputStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "content",
        aliases = {"contents"},
        description = "Search for artifacts by content"
)
public class SearchByContentCommand extends AbstractCommand {

    @Option(
            names = {"-f", "--file"},
            description = "Path to the content file. Use '-' to read from stdin.",
            required = true
    )
    private String file;

    @Option(
            names = {"-g", "--group"},
            description = "Filter by group ID."
    )
    private String groupId;

    @Option(
            names = {"--type"},
            description = "Artifact type (e.g. AVRO, JSON, PROTOBUF, OPENAPI, ASYNCAPI)."
                    + " Required when using --canonical. Use 'acr version' to see all supported types."
    )
    private String artifactType;

    @Option(
            names = {"--canonical"},
            description = "Canonicalize content before searching. Requires --type.",
            defaultValue = "false"
    )
    private boolean canonical;

    @Option(
            names = {"--content-type"},
            description = "Content type of the file (e.g. application/json, application/x-protobuf)."
                    + " Auto-detected from file extension if not specified."
    )
    private String contentType;

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
        if (canonical && artifactType == null) {
            throw new CliException("--type is required when using --canonical.",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedContentType = contentType != null
                ? contentType : ContentTypeDetector.detect(file);
        final var inputStream = new ByteArrayInputStream(FileUtils.readContentAsBytes(file));

        //noinspection ConstantConditions
        final var results = convert(client.getRegistryClient().search().artifacts()
                .post(inputStream, resolvedContentType, r -> {
                    //noinspection ConstantConditions
                    r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                    r.queryParameters.limit = pagination.getSize();
                    r.queryParameters.orderby = ordering.getOrderBy();
                    r.queryParameters.order = ordering.getOrder();
                    if (groupId != null) {
                        r.queryParameters.groupId = groupId;
                    }
                    if (artifactType != null) {
                        r.queryParameters.artifactType = artifactType;
                    }
                    if (canonical) {
                        r.queryParameters.canonical = true;
                    }
                }));
        SearchUtil.printArtifactResults(output, results, outputType, pagination, columns);
    }
}
