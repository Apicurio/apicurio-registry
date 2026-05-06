package io.apicurio.registry.cli.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputType;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CONTENT_ID;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.GLOBAL_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.STATE;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Columns.VERSION;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

/** Retrieves a version's metadata or content. */
@Command(
        name = "get",
        description = "Get a version"
)
public class VersionGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The version expression (e.g. '1.0.0', 'branch=latest')."
    )
    private String versionExpression;

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID. If not provided, uses the artifactId from the current context."
    )
    private String artifactId;

    @ArgGroup(exclusive = true)
    private OutputOptions outputOptions;

    static class OutputOptions {
        @Option(
                names = {"-c", "--content"},
                description = "Retrieve the version content instead of metadata."
        )
        boolean content;

        @Option(
                names = {"-o", "--output-type"},
                description = "Write the output in the given format. Valid values: ${COMPLETION-CANDIDATES}. Default is 'table'."
        )
        OutputType outputType;
    }

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
            if (outputOptions != null && outputOptions.content) {
                fetchContent(registryClient, resolvedGroupId, resolvedArtifactId, output);
            } else {
                fetchMetadata(registryClient, resolvedGroupId, resolvedArtifactId, output);
            }
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving version '")
                        .append(versionExpression)
                        .append("' for artifact '")
                        .append(resolvedArtifactId)
                        .append("' in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }

    private void fetchContent(final RegistryClient client, final String resolvedGroupId,
                              final String resolvedArtifactId, final OutputBuffer output) {
        final String versionContent;
        try (final var inputStream = client
                .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                .versions().byVersionExpression(versionExpression).content().get()) {
            //noinspection ConstantConditions
            versionContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CliException("Could not read version content.", ex, APPLICATION_ERROR_RETURN_CODE);
        }
        output.writeStdOutChunk(out -> {
            out.append(versionContent);
            if (!versionContent.endsWith("\n")) {
                out.append('\n');
            }
        });
    }

    private void fetchMetadata(final RegistryClient client, final String resolvedGroupId,
                               final String resolvedArtifactId, final OutputBuffer output) throws JsonProcessingException {
        //noinspection ConstantConditions
        final var version = convert(client
                .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                .versions().byVersionExpression(versionExpression).get());
        final var type = outputOptions != null && outputOptions.outputType != null
                ? outputOptions.outputType : OutputType.table;
        printVersion(output, version, type);
    }

    static void printVersion(final OutputBuffer output, final VersionMetaData version,
                             final OutputType outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(version));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(GROUP_ID, version.getGroupId());
                    table.addRow(ARTIFACT_ID, version.getArtifactId());
                    table.addRow(VERSION, version.getVersion());
                    table.addRow(NAME, version.getName());
                    table.addRow(ARTIFACT_TYPE, version.getArtifactType());
                    table.addRow(DESCRIPTION, version.getDescription());
                    table.addRow(STATE, convertToString(version.getState()));
                    table.addRow(GLOBAL_ID, convertToString(version.getGlobalId()));
                    table.addRow(CONTENT_ID, convertToString(version.getContentId()));
                    table.addRow(CREATED_ON, convertToString(version.getCreatedOn()));
                    table.addRow(OWNER, version.getOwner());
                    table.addRow(MODIFIED_ON, convertToString(version.getModifiedOn()));
                    table.addRow(MODIFIED_BY, version.getModifiedBy());
                    table.addRow(LABELS, convertToString(version.getLabels()));
                    table.print(out);
                }
            }
        });
    }
}
