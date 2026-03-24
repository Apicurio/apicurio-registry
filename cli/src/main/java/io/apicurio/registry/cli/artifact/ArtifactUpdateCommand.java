package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;

import io.apicurio.registry.cli.common.CliException;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;

/**
 * Updates an existing artifact's metadata (name, description, labels).
 * Supports adding/updating and deleting individual labels.
 */
@Command(
        name = "update",
        description = "Update an existing artifact"
)
public class ArtifactUpdateCommand extends AbstractCommand {

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
            names = {"-n", "--name"},
            description = "Updated artifact name."
    )
    private String name;

    @Option(
            names = {"-d", "--description"},
            description = "Updated artifact description."
    )
    private String description;

    @Option(
            names = {"-l", "--sl", "--set-label"},
            description = "Add or update an artifact label.",
            mapFallbackValue = ""
    )
    private Map<String, String> setLabels;

    @Option(
            names = {"--dl", "--delete-label"},
            description = "Delete an existing artifact label."
    )
    private List<String> deleteLabels;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        if (name == null && description == null && setLabels == null && deleteLabels == null) {
            throw new CliException("At least one update option is required (--name, --description, --set-label, or --delete-label).",
                    CliException.VALIDATION_ERROR_RETURN_CODE);
        }
        final var resolvedGroupId = ArtifactUtil.resolveGroupId(groupId, config);
        try {
            final var registryClient = client.getRegistryClient();
            ArtifactUtil.validateGroup(registryClient, resolvedGroupId);
            final var existing = registryClient
                    .groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId).get();
            final var updatedArtifact = new EditableArtifactMetaData();
            //noinspection ConstantConditions
            updatedArtifact.setName(existing.getName());
            updatedArtifact.setDescription(existing.getDescription());
            updatedArtifact.setLabels(existing.getLabels());
            if (name != null) {
                updatedArtifact.setName(name);
            }
            if (description != null) {
                updatedArtifact.setDescription(description);
            }
            if (setLabels != null) {
                if (updatedArtifact.getLabels() == null) {
                    updatedArtifact.setLabels(new Labels());
                }
                updatedArtifact.getLabels().getAdditionalData().putAll(setLabels);
            }
            if (deleteLabels != null) {
                if (updatedArtifact.getLabels() != null) {
                    deleteLabels.forEach(key -> {
                        updatedArtifact.getLabels().getAdditionalData().remove(key);
                    });
                }
            }
            registryClient.groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId).put(updatedArtifact);
            output.writeStdOutChunk(out -> {
                out.append("Artifact '").append(artifactId).append("' in group '")
                        .append(resolvedGroupId).append("' updated successfully.\n");
            });
        } catch (ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error updating artifact '")
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
}
