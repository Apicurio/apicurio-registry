package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.FileUtils;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

/**
 * Updates a version's metadata (name, description, labels), state, and content.
 * Supports state transitions: ENABLED, DISABLED, DEPRECATED.
 * Content update is only supported for draft versions.
 */
@Command(
        name = "update",
        description = "Update a version"
)
public class VersionUpdateCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The version expression (e.g. '1.0.0')."
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

    @Option(
            names = {"-n", "--name"},
            description = "Updated version name."
    )
    private String name;

    @Option(
            names = {"-d", "--description"},
            description = "Updated version description."
    )
    private String description;

    @Option(
            names = {"--state"},
            description = "Set the version state. Valid values: ${COMPLETION-CANDIDATES}."
    )
    private VersionState state;

    @Option(
            names = {"-l", "--sl", "--set-label"},
            description = "Add or update a version label.",
            mapFallbackValue = ""
    )
    private Map<String, String> setLabels;

    @Option(
            names = {"--dl", "--delete-label"},
            description = "Delete an existing version label."
    )
    private List<String> deleteLabels;

    @Option(
            names = {"-f", "--file"},
            description = "Path to the updated content file. Use '-' to read from stdin. " +
                    "Only draft versions can have their content updated."
    )
    private String file;

    @Option(
            names = {"--content-type"},
            description = "Content type of the version (e.g. application/json, application/x-protobuf). " +
                    "Defaults to 'application/json' if not specified."
    )
    private String contentType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (name == null && description == null && state == null && setLabels == null && deleteLabels == null && file == null) {
            throw new CliException("At least one update option is required (--name, --description, --state, --set-label, --delete-label, or --file).",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
            final var versionPath = registryClient
                    .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(resolvedArtifactId)
                    .versions().byVersionExpression(versionExpression);

            // Update metadata if any metadata options are provided
            if (name != null || description != null || setLabels != null || deleteLabels != null) {
                final var existing = versionPath.get();
                final var updatedVersion = new EditableVersionMetaData();
                //noinspection ConstantConditions
                updatedVersion.setName(existing.getName());
                updatedVersion.setDescription(existing.getDescription());
                updatedVersion.setLabels(existing.getLabels());
                if (name != null) {
                    updatedVersion.setName(name);
                }
                if (description != null) {
                    updatedVersion.setDescription(description);
                }
                if (setLabels != null) {
                    if (updatedVersion.getLabels() == null) {
                        updatedVersion.setLabels(new Labels());
                    }
                    updatedVersion.getLabels().getAdditionalData().putAll(setLabels);
                }
                if (deleteLabels != null) {
                    if (updatedVersion.getLabels() != null) {
                        deleteLabels.forEach(key -> {
                            updatedVersion.getLabels().getAdditionalData().remove(key);
                        });
                    }
                }
                versionPath.put(updatedVersion);
            }

            // Update state if provided (separate API endpoint)
            if (state != null) {
                final var wrappedState = new WrappedVersionState();
                wrappedState.setState(state);
                versionPath.state().put(wrappedState);
            }

            // Update content if provided (only works for draft versions)
            if (file != null) {
                final var content = FileUtils.readContent(file);
                final var versionContent = new VersionContent();
                versionContent.setContent(content);
                versionContent.setContentType(contentType != null ? contentType : "application/json");
                versionPath.content().put(versionContent);
            }

            output.writeStdOutChunk(out -> {
                out.append("Version '").append(versionExpression).append("' for artifact '")
                        .append(resolvedArtifactId).append("' in group '")
                        .append(resolvedGroupId).append("' updated successfully.\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error updating version '")
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
}
