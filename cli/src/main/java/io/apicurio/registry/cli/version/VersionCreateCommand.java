package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.artifact.ArtifactUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Utils.isBlank;
import static io.apicurio.registry.cli.version.VersionGetCommand.printVersion;

/** Creates a new version for an artifact with content from a file or stdin. */
@Command(
        name = "create",
        aliases = {"add"},
        description = "Create a new version"
)
public class VersionCreateCommand extends AbstractCommand {

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

    @Parameters(
            index = "0",
            arity = "0..1",
            description = "Version identifier. If not provided, the server will generate one."
    )
    private String version;

    @Option(
            names = {"-f", "--file"},
            description = "Path to the version content file. Use '-' to read from stdin.",
            required = true
    )
    private String file;

    @Option(
            names = {"-n", "--name"},
            description = "Provide version name."
    )
    private String name;

    @Option(
            names = {"-d", "--description"},
            description = "Provide version description."
    )
    private String description;

    @Option(
            names = {"-l", "--label"},
            description = "Provide a list of version labels.",
            mapFallbackValue = ""
    )
    private Map<String, String> labels;

    @Option(
            names = {"--content-type"},
            description = "Content type of the version (e.g. application/json, application/x-protobuf). " +
                    "Defaults to 'application/json' if not specified."
    )
    private String contentType;

    @Option(
            names = {"--draft"},
            description = "Create the version as a draft.",
            defaultValue = "false"
    )
    private boolean draft;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = VersionUtil.resolveArtifactId(artifactId, config);

        final var newVersion = new CreateVersion();
        if (!isBlank(version)) {
            newVersion.setVersion(version);
        }
        if (!isBlank(name)) {
            newVersion.setName(name);
        }
        if (!isBlank(description)) {
            newVersion.setDescription(description);
        }
        if (labels != null) {
            final var newLabels = new Labels();
            newLabels.setAdditionalData(new HashMap<>(labels));
            newVersion.setLabels(newLabels);
        }
        if (draft) {
            newVersion.setIsDraft(true);
        }

        final var content = FileUtils.readContent(file);
        final var versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(!isBlank(contentType) ? contentType : "application/json");
        newVersion.setContent(versionContent);

        try {
            final var registryClient = client.getRegistryClient();
            ArtifactUtil.validateGroup(registryClient, resolvedGroupId);
            //noinspection ConstantConditions
            final var result = convert(registryClient
                    .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                    .versions().post(newVersion));
            switch (outputType.getOutputType()) {
                case json -> output.writeStdErrChunk(out -> successMessage(out, resolvedGroupId, resolvedArtifactId, result.getVersion()));
                case table -> output.writeStdOutChunk(out -> successMessage(out, resolvedGroupId, resolvedArtifactId, result.getVersion()));
            }
            printVersion(output, result, outputType.getOutputType());
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error creating version for artifact '")
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

    private static void successMessage(final StringBuilder out, final String groupId,
                                       final String artifactId, final String version) {
        out.append("Version '").append(version).append("' created successfully for artifact '")
                .append(artifactId).append("' in group '").append(groupId).append("'.\n");
    }
}
