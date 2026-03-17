package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.services.Client;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.apicurio.registry.cli.artifact.ArtifactGetCommand.printArtifact;
import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Utils.isBlank;

/**
 * Creates a new artifact with optional content from a file or stdin.
 * Supports specifying artifact type, name, description, labels, and
 * an initial version identifier.
 */
@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new artifact"
)
public class ArtifactCreateCommand extends AbstractCommand {

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

    @Option(
            names = {"-t", "--type"},
            description = "Artifact type (e.g. AVRO, PROTOBUF, JSON, OPENAPI, ASYNCAPI, GRAPHQL, KCONNECT, WSDL, XSD, XML)."
    )
    private String artifactType;

    @Option(
            names = {"-f", "--file"},
            description = "Path to the artifact content file. Use '-' to read from stdin."
    )
    private String file;

    @Option(
            names = {"-n", "--name"},
            description = "Provide artifact name."
    )
    private String name;

    @Option(
            names = {"-d", "--description"},
            description = "Provide artifact description."
    )
    private String description;

    @Option(
            names = {"-l", "--label"},
            description = "Provide a list of artifact labels.",
            mapFallbackValue = ""
    )
    private Map<String, String> labels;

    @Option(
            names = {"--version"},
            description = "Version identifier for the first version."
    )
    private String version;

    @Option(
            names = {"--content-type"},
            description = "Content type of the artifact (e.g. application/json, application/x-protobuf). " +
                    "Defaults to 'application/json' if not specified."
    )
    private String contentType;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    @SuppressWarnings("unchecked")
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId);

        final var newArtifact = new CreateArtifact();
        newArtifact.setArtifactId(artifactId);
        if (!isBlank(artifactType)) {
            newArtifact.setArtifactType(artifactType);
        }
        if (!isBlank(name)) {
            newArtifact.setName(name);
        }
        if (!isBlank(description)) {
            newArtifact.setDescription(description);
        }
        if (labels != null) {
            final var newLabels = new Labels();
            newLabels.setAdditionalData((Map<String, Object>) (Map<String, ?>) labels);
            newArtifact.setLabels(newLabels);
        }

        if (!isBlank(file)) {
            final var content = readContent(file);
            final var firstVersion = new CreateVersion();
            if (!isBlank(version)) {
                firstVersion.setVersion(version);
            }
            final var versionContent = new VersionContent();
            versionContent.setContent(content);
            versionContent.setContentType(!isBlank(contentType) ? contentType : "application/json");
            firstVersion.setContent(versionContent);
            newArtifact.setFirstVersion(firstVersion);
        }

        try {
            final var result = Client.getInstance().getRegistryClient()
                    .groups().byGroupId(resolvedGroupId).artifacts().post(newArtifact);
            //noinspection ConstantConditions
            final var artifact = convert(result.getArtifact());
            switch (outputType.getOutputType()) {
                case json -> output.writeStdErrChunk(out -> successMessage(out, resolvedGroupId, artifact.getArtifactId()));
                case table -> output.writeStdOutChunk(out -> successMessage(out, resolvedGroupId, artifact.getArtifactId()));
            }
            printArtifact(output, artifact, outputType.getOutputType());
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error creating artifact '")
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

    // Reads content from a file path or stdin (when path is "-").
    private static String readContent(final String file) {
        try {
            if ("-".equals(file)) {
                return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                return Files.readString(Path.of(file));
            }
        } catch (IOException ex) {
            throw new CliException("Could not read content from: " + file, ex, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private static void successMessage(final StringBuilder out, final String groupId, final String artifactId) {
        out.append("Artifact '").append(artifactId).append("' created successfully in group '")
                .append(groupId).append("'.\n");
    }
}
