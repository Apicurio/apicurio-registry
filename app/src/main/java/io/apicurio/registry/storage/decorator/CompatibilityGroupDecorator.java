package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.contracts.compatibility.CompatibilityGroupService;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records the compatibility group of a data contract on each new version.
 * <p>
 * The compatibility group is an artifact label ({@code contract.{contractId}.compatibility.group}).
 * Compatibility checks compare a new version only with the versions of the same group, so the group
 * the artifact is in when a version is created is copied to the version's labels. It runs before the
 * storage is written, so with KafkaSQL the journal records the label.
 * </p>
 */
@ApplicationScoped
public class CompatibilityGroupDecorator extends RegistryStorageDecoratorBase {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.COMPATIBILITY_GROUP_DECORATOR;
    }

    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, boolean dryRun, String owner)
            throws ArtifactNotFoundException, VersionAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifactVersion(groupId, artifactId, version, artifactType, content,
                withCompatibilityGroup(groupId, artifactId, metaData), branches, isDraft, dryRun, owner);
    }

    public ArtifactVersionMetaDataDto createArtifactVersionIfLatest(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, String owner, int expectedBaseVersionOrder,
            EditableArtifactMetaDataDto artifactMetaData) throws RegistryStorageException {
        return delegate.createArtifactVersionIfLatest(groupId, artifactId, version, artifactType, content,
                withCompatibilityGroup(groupId, artifactId, metaData), branches, isDraft, owner,
                expectedBaseVersionOrder, artifactMetaData);
    }

    private EditableVersionMetaDataDto withCompatibilityGroup(String groupId, String artifactId,
            EditableVersionMetaDataDto metaData) {
        Optional<Map.Entry<String, String>> group;
        try {
            group = CompatibilityGroupService
                    .findCompatibilityGroup(delegate.getArtifactMetaData(groupId, artifactId).getLabels());
        } catch (ArtifactNotFoundException e) {
            return metaData;
        }
        if (group.isEmpty()) {
            return metaData;
        }
        EditableVersionMetaDataDto result = metaData != null ? metaData : new EditableVersionMetaDataDto();
        Map<String, String> labels = result.getLabels() != null ? new HashMap<>(result.getLabels())
            : new HashMap<>();
        labels.putIfAbsent(group.get().getKey(), group.get().getValue());
        return EditableVersionMetaDataDto.builder().name(result.getName()).description(result.getDescription())
                .labels(labels).build();
    }
}
