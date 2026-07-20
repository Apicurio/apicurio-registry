package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.Conversions;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.artifact.ArtifactGetCommand.printArtifact;
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
            description = "Provide a list of artifact labels (format: key=value or key). Use \= to include = in a key."
    )
    private List<String> labels;

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
    public void run(final OutputBuffer output) throws Exception {
        final var supportedTypesList = Arrays.stream(ArtifactType.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers())
                        && field.getType().equals(String.class))
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException e) {
                        return field.getName();
                    }
                })
                .sorted()
                .collect(Collectors.joining(", "));

        // 1. Validate missing --type
        if (isBlank(artifactType)) {
            throw new IllegalArgumentException(
                "Missing required option '--type'. Supported artifact types are: " + supportedTypesList
            );
        }

        // 2. Validate missing content/file
        if (isBlank(file)) {
            throw new IllegalArgumentException(
                "Missing required option '--file' (or '-' for stdin) to provide artifact content."
            );
        }

        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);

        final var newArtifact = new CreateArtifact();
        newArtifact.setArtifactId(artifactId);
        newArtifact.setArtifactType(artifactType);

        if (!isBlank(name)) {
            newArtifact.setName(name);
        }
        if (!isBlank(description)) {
            newArtifact.setDescription(description);
        }
        if (labels != null) {
            final var newLabels = new Labels();
            newLabels.setAdditionalData(new HashMap<>(Conversions.parseLabels(labels)));
            newArtifact.setLabels(newLabels);
        }

        final var content = FileUtils.readContent(file);
        final var firstVersion = new CreateVersion();
        if (!isBlank(version)) {
            firstVersion.setVersion(version);
        }
        final var versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(!isBlank(contentType) ? contentType : "application/json");
        firstVersion.setContent(versionContent);
        newArtifact.setFirstVersion(firstVersion);

        final var result = client.getRegistryClient()
                .groups().byGroupId(resolvedGroupId).artifacts().post(newArtifact);
        //noinspection ConstantConditions
        final var artifact = convert(result.getArtifact());
        switch (outputType.getOutputType()) {
            case json -> output.writeStdErrChunk(out -> successMessage(out, resolvedGroupId, artifact.getArtifactId()));
            case table -> output.writeStdOutChunk(out -> successMessage(out, resolvedGroupId, artifact.getArtifactId()));
        }
        printArtifact(output, artifact, outputType.getOutputType());
    }

    private static void successMessage(final StringBuilder out, final String groupId, final String artifactId) {
        out.append("Artifact '").append(artifactId).append("' created successfully in group '")
                .append(groupId).append("'.\n");
    }
}
