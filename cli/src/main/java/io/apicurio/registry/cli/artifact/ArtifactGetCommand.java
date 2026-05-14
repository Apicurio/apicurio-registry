package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputType;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
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
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

/** Retrieves an existing artifact's metadata or content (latest version). */
@Command(
        name = "get",
        description = "Get an existing artifact"
)
public class ArtifactGetCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The artifact ID."
    )
    private String artifactId;

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @ArgGroup(exclusive = true)
    private OutputOptions outputOptions;

    static class OutputOptions {
        @Option(
                names = {"-c", "--content"},
                description = "Retrieve the artifact content (latest version) instead of metadata."
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
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            if (outputOptions != null && outputOptions.content) {
                fetchContent(registryClient, resolvedGroupId, output);
            } else {
                fetchMetadata(registryClient, resolvedGroupId, output);
            }
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving artifact '")
                        .append(artifactId)
                        .append("' in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }

    // Fetches the raw content of the latest version of the artifact.
    private void fetchContent(final RegistryClient client, final String resolvedGroupId, final OutputBuffer output) {
        // Validates the artifact exists to provide accurate error messages.
        client.groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId).get();
        final String artifactContent;
        try (final var inputStream = client
                .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("branch=latest").content().get()) {
            //noinspection ConstantConditions
            artifactContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CliException("Could not read artifact content.", ex, APPLICATION_ERROR_RETURN_CODE);
        }
        output.writeStdOutChunk(out -> {
            out.append(artifactContent);
            if (!artifactContent.endsWith("\n")) {
                out.append('\n');
            }
        });
    }

    // Fetches and prints the artifact metadata.
    private void fetchMetadata(final RegistryClient client, final String resolvedGroupId, final OutputBuffer output) throws JsonProcessingException {
        //noinspection ConstantConditions
        final var artifact = convert(client
                .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId).get());
        final var type = outputOptions != null && outputOptions.outputType != null
                ? outputOptions.outputType : OutputType.table;
        printArtifact(output, artifact, type);
    }

    static void printArtifact(final OutputBuffer output, final ArtifactMetaData artifact, final OutputType outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(artifact));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(GROUP_ID, artifact.getGroupId());
                    table.addRow(ARTIFACT_ID, artifact.getArtifactId());
                    table.addRow(NAME, artifact.getName());
                    table.addRow(ARTIFACT_TYPE, artifact.getArtifactType());
                    table.addRow(DESCRIPTION, artifact.getDescription());
                    table.addRow(CREATED_ON, convertToString(artifact.getCreatedOn()));
                    table.addRow(OWNER, artifact.getOwner());
                    table.addRow(MODIFIED_ON, convertToString(artifact.getModifiedOn()));
                    table.addRow(MODIFIED_BY, artifact.getModifiedBy());
                    table.addRow(LABELS, convertToString(artifact.getLabels()));
                    table.print(out);
                }
            }
        });
    }
}
