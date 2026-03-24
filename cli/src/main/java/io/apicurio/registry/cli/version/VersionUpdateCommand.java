package io.apicurio.registry.cli.version;

import io.apicurio.registry.cli.artifact.ArtifactUtil;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

import io.apicurio.registry.cli.common.CliException;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

/**
 * Updates a version's metadata (name, description, labels) and state.
 * Supports state transitions: ENABLED, DISABLED, DEPRECATED.
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

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (name == null && description == null && state == null && setLabels == null && deleteLabels == null) {
            throw new CliException("At least one update option is required (--name, --description, --state, --set-label, or --delete-label).",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = VersionUtil.resolveArtifactId(artifactId, config);
        try {
            final var registryClient = client.getRegistryClient();
            ArtifactUtil.validateGroup(registryClient, resolvedGroupId);
            ArtifactUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);
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
