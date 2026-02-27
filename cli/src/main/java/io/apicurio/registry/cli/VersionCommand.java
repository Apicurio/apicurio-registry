package io.apicurio.registry.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import lombok.Builder;
import lombok.Getter;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.util.Date;
import java.util.List;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPES;
import static io.apicurio.registry.cli.utils.Columns.CLI_VERSION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.SERVER_BUILT_ON;
import static io.apicurio.registry.cli.utils.Columns.SERVER_NAME;
import static io.apicurio.registry.cli.utils.Columns.SERVER_VERSION;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

/**
 * Displays CLI version and, when a server is reachable, server name, version,
 * build timestamp, and supported artifact types. Always succeeds — server
 * failures are reported to stderr while CLI version is still shown.
 */
@Command(
        name = "version",
        description = "Prints version information"
)
public class VersionCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var builder = VersionOutput.builder()
                .cliVersion(ConfigProvider.getConfig().getValue("version", String.class));

        try {
            final var client = Client.getInstance().getRegistryClient();
            fetchSystemInfo(client, builder, output);
            fetchArtifactTypes(client, builder, output);
        } catch (Exception ex) {
            output.writeStdErrChunk(err ->
                    err.append("Could not connect to server: ").append(ex.getMessage()).append('\n'));
        }

        printVersion(output, builder.build(), outputType);
    }

    // Fetches server name, version, and build timestamp from /system/info.
    private void fetchSystemInfo(final RegistryClient client, final VersionOutput.VersionOutputBuilder builder, final OutputBuffer output) {
        try {
            final var systemInfo = client.system().info().get();
            builder.serverName(systemInfo.getName());
            builder.serverVersion(systemInfo.getVersion());
            builder.serverBuiltOn(systemInfo.getBuiltOn() != null ? convert(systemInfo.getBuiltOn()) : null);
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err ->
                    err.append("Error retrieving system info: ").append(ex.getDetail()).append('\n'));
        }
    }

    // Fetches supported artifact types from /admin/config/artifactTypes.
    private void fetchArtifactTypes(final RegistryClient client, final VersionOutput.VersionOutputBuilder builder, final OutputBuffer output) {
        try {
            final var artifactTypes = client.admin().config().artifactTypes().get();
            builder.artifactTypes(artifactTypes != null ? artifactTypes.stream()
                    .map(ArtifactTypeInfo::getName)
                    .toList() : null);
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err ->
                    err.append("Error retrieving artifact types: ").append(ex.getDetail()).append('\n'));
        }
    }

    private static void printVersion(final OutputBuffer output, final VersionOutput versionOutput, final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(versionOutput));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(CLI_VERSION, versionOutput.getCliVersion());
                    table.addRow(SERVER_NAME, versionOutput.getServerName());
                    table.addRow(SERVER_VERSION, versionOutput.getServerVersion());
                    table.addRow(SERVER_BUILT_ON, versionOutput.getServerBuiltOn() != null ? convertToString(versionOutput.getServerBuiltOn()) : null);
                    table.addRow(ARTIFACT_TYPES, versionOutput.getArtifactTypes() != null ? String.join(", ", versionOutput.getArtifactTypes()) : null);
                    table.print(out);
                }
            }
        });
    }

    /** Output model for the version command, serialized as JSON or rendered as a table. */
    @Builder
    @Getter
    public static class VersionOutput {

        @JsonProperty(CLI_VERSION)
        private String cliVersion;

        @JsonProperty(SERVER_NAME)
        private String serverName;

        @JsonProperty(SERVER_VERSION)
        private String serverVersion;

        @JsonProperty(SERVER_BUILT_ON)
        private Date serverBuiltOn;

        @JsonProperty(ARTIFACT_TYPES)
        private List<String> artifactTypes;
    }
}
