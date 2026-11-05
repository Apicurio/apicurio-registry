package io.apicurio.registry.cli.mcp;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.McpToolImportRequest;
import io.apicurio.registry.rest.client.models.McpToolImportRequestHeaders;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.McpToolImportResult;
import io.apicurio.registry.rest.v3.beans.McpToolImportStatus;
import io.apicurio.registry.rest.v3.beans.McpToolImportToolResult;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.apicurio.registry.cli.common.CliException.SERVER_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.VERSION;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Imports the tools of a live MCP server into an {@code MCP_SERVER} artifact, through the
 * {@code mcp-import} endpoint.
 */
@Command(
        name = "import",
        description = "[Experimental] Import the tools of a live MCP server into an MCP_SERVER artifact. "
                + "Each tool becomes an MCP_TOOL artifact, subject to the configured rules. "
                + "Requires apicurio.mcp-import.enabled on the server. "
                + "Subject to backwards-incompatible change or removal."
)
public class McpImportCommand extends AbstractCommand {

    static final String TOOL = "Tool";
    static final String STATUS = "Status";

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "The ID of the MCP_SERVER artifact to import into. "
                    + "If not provided, uses the artifactId from the current context."
    )
    private String artifactId;

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Option(
            names = {"--url"},
            required = true,
            description = "The MCP server endpoint (Streamable HTTP). The server must allow its host."
    )
    private String url;

    @Option(
            names = {"--tool-group"},
            description = "Group to publish the MCP_TOOL artifacts in. Defaults to the server artifact's group."
    )
    private String toolGroupId;

    @Option(
            names = {"--dry-run"},
            description = "Contact the MCP server and report what would change, without writing anything."
    )
    private boolean dryRun;

    @Option(
            names = {"--header-file"},
            description = "File with HTTP headers to send to the MCP server, one 'Name: value' per line. "
                    + "Use '-' to read from stdin. Lines starting with '#' are ignored."
    )
    private String headerFile;

    @Option(
            names = {"--header-env"},
            description = "HTTP header to send to the MCP server, taking its value from an environment "
                    + "variable (format: Name=VARIABLE). Can be repeated."
    )
    private List<String> headerEnv;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        final var headers = McpHeaders.read(isBlank(headerFile) ? null : FileUtils.readContent(headerFile),
                headerEnv, config::getEnv);

        final var request = new McpToolImportRequest();
        request.setUrl(url);
        if (!isBlank(toolGroupId)) {
            request.setToolGroupId(toolGroupId);
        }
        request.setDryRun(dryRun);
        if (!headers.isEmpty()) {
            final var requestHeaders = new McpToolImportRequestHeaders();
            requestHeaders.setAdditionalData(new HashMap<>(headers));
            request.setHeaders(requestHeaders);
        }

        if (!headers.isEmpty() && client.isHttpLoggingEnabled()) {
            // The headers travel in the request body, which --verbose would otherwise log.
            output.writeStdErrChunk(out -> out.append(
                    "Note: HTTP logging is disabled for this request because it carries MCP server headers.\n"));
        }

        final io.apicurio.registry.rest.client.models.McpToolImportResult result;
        try {
            result = client.getRegistryClientWithoutHttpLogging()
                    .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                    .mcpImport().post(request);
        } catch (ProblemDetails ex) {
            throw explainDisabled(ex);
        }
        //noinspection ConstantConditions
        print(output, convert(result));
    }

    /**
     * A 409 because the feature is off says only that it is not enabled; name the setting.
     */
    private static Exception explainDisabled(final ProblemDetails ex) {
        final var title = ex.getTitle();
        if (ex.getResponseStatusCode() == 409 && title != null
                && title.startsWith("Importing tools from MCP servers is not")) {
            return new CliException(title + " Ask the Registry administrator to set "
                    + "apicurio.mcp-import.enabled=true (with apicurio.features.experimental.enabled=true).",
                    SERVER_ERROR_RETURN_CODE);
        }
        return ex;
    }

    private void print(final OutputBuffer output, final McpToolImportResult result) throws Exception {
        switch (outputType.getOutputType()) {
            case json -> output.writeStdOutChunkWithException(out -> {
                out.append(MAPPER.writeValueAsString(result));
                out.append('\n');
            });
            case table -> output.writeStdOutChunk(out -> {
                final var table = new TableBuilder();
                table.addColumns(TOOL, GROUP_ID, ARTIFACT_ID, VERSION, STATUS);
                result.getTools().forEach(tool -> table.addRow(
                        tool.getName(),
                        displayGroupId(tool.getGroupId()),
                        tool.getArtifactId(),
                        tool.getVersion(),
                        statusName(tool.getStatus())));
                table.print(out);
                result.getTools().stream()
                        .filter(tool -> tool.getCauses() != null && !tool.getCauses().isEmpty())
                        .forEach(tool -> {
                            out.append("\nRejected '").append(tool.getName()).append("':\n");
                            tool.getCauses().forEach(cause -> out.append("  -> ")
                                    .append(cause.getContext()).append(": ")
                                    .append(cause.getDescription()).append('\n'));
                        });
                out.append('\n').append(summary(result)).append('\n');
            });
        }
    }

    private String summary(final McpToolImportResult result) {
        final var server = displayGroupId(result.getGroupId()) + "/" + result.getArtifactId();
        if (result.getStatus() == McpToolImportStatus.UNCHANGED) {
            return "MCP server '" + server + "' is unchanged at version " + result.getVersion() + ".";
        }
        if (dryRun) {
            return "Dry run: a new version of MCP server '" + server + "' would be created. Nothing was written.";
        }
        return "MCP server '" + server + "' now at version " + result.getVersion() + ".";
    }

    private static String statusName(final McpToolImportStatus status) {
        return status == null ? null : status.value();
    }

    static McpToolImportResult convert(final io.apicurio.registry.rest.client.models.McpToolImportResult result) {
        return McpToolImportResult.builder()
                .groupId(result.getGroupId())
                .artifactId(result.getArtifactId())
                .version(result.getVersion())
                .status(convert(result.getStatus()))
                .tools(result.getTools() == null ? List.of() : result.getTools().stream()
                        .map(McpImportCommand::convert)
                        .toList())
                .build();
    }

    private static McpToolImportToolResult convert(
            final io.apicurio.registry.rest.client.models.McpToolImportToolResult tool) {
        return McpToolImportToolResult.builder()
                .name(tool.getName())
                .groupId(tool.getGroupId())
                .artifactId(tool.getArtifactId())
                .version(tool.getVersion())
                .status(convert(tool.getStatus()))
                .causes(convert(tool.getCauses()))
                .build();
    }

    private static List<RuleViolationCause> convert(
            final List<io.apicurio.registry.rest.client.models.RuleViolationCause> causes) {
        if (causes == null) {
            return null;
        }
        final List<RuleViolationCause> converted = new ArrayList<>();
        for (final var cause : causes) {
            final RuleViolationCause bean = RuleViolationCause.builder()
                    .context(cause.getContext())
                    .description(cause.getDescription())
                    .code(cause.getCode())
                    .build();
            converted.add(bean);
        }
        return converted;
    }

    private static McpToolImportStatus convert(
            final io.apicurio.registry.rest.client.models.McpToolImportStatus status) {
        return status == null ? null : McpToolImportStatus.fromValue(status.getValue());
    }
}
