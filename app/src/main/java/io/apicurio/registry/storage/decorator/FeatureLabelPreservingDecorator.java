package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.extensions.FeatureLabelOwner;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keeps feature-owned labels across metadata updates that replace the whole label map.
 * <p>
 * The v2 and v3 metadata endpoints replace a group's, artifact's or version's labels with the
 * map the caller sends. Features such as data contracts also store their metadata in labels
 * (see {@link FeatureLabelOwner}), so a caller that sends only its own labels used to delete
 * the feature's. This decorator copies the existing feature-owned labels into the new map
 * unless the caller sent the same key.
 * </p>
 * <p>
 * It runs before the storage is written, so with KafkaSQL the journal records the merged map
 * and replaying it gives the same result. The features' own label writes
 * ({@code mergeArtifactLabels}, {@code mergeVersionLabels}, {@code updateArtifactVersionStates})
 * do not go through these methods and are not affected.
 * </p>
 * <p>
 * The current labels are read before the write, so an update running concurrently with a
 * feature's own label write can still lose that write, as before.
 * </p>
 */
@ApplicationScoped
public class FeatureLabelPreservingDecorator extends RegistryStorageDecoratorBase {

    @Inject
    Instance<FeatureLabelOwner> owners;

    private List<String> prefixes;
    private Set<String> keys;

    @PostConstruct
    void init() {
        prefixes = owners.stream().flatMap(owner -> owner.ownedLabelPrefixes().stream()).distinct().toList();
        keys = owners.stream().flatMap(owner -> owner.ownedLabelKeys().stream()).collect(Collectors.toSet());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.FEATURE_LABEL_PRESERVING_DECORATOR;
    }

    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws RegistryStorageException {
        if (metaData != null && metaData.getLabels() != null) {
            Map<String, String> labels = withFeatureLabels(metaData.getLabels(),
                    () -> delegate.getArtifactMetaData(groupId, artifactId).getLabels());
            metaData = EditableArtifactMetaDataDto.builder().name(metaData.getName())
                    .description(metaData.getDescription()).owner(metaData.getOwner()).labels(labels).build();
        }
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }

    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData) throws RegistryStorageException {
        if (metaData != null && metaData.getLabels() != null) {
            Map<String, String> labels = withFeatureLabels(metaData.getLabels(),
                    () -> delegate.getArtifactVersionMetaData(groupId, artifactId, version).getLabels());
            metaData = EditableVersionMetaDataDto.builder().name(metaData.getName())
                    .description(metaData.getDescription()).labels(labels).build();
        }
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) throws RegistryStorageException {
        if (dto != null && dto.getLabels() != null) {
            Map<String, String> labels = withFeatureLabels(dto.getLabels(),
                    () -> delegate.getGroupMetaData(groupId).getLabels());
            dto = EditableGroupMetaDataDto.builder().description(dto.getDescription()).owner(dto.getOwner())
                    .labels(labels).build();
        }
        delegate.updateGroupMetaData(groupId, dto);
    }

    /**
     * The labels to store: the caller's, plus each existing feature-owned label the caller did
     * not send.
     */
    Map<String, String> withFeatureLabels(Map<String, String> requested, Supplier<Map<String, String>> current) {
        if (prefixes.isEmpty() && keys.isEmpty()) {
            return requested;
        }
        Map<String, String> existing = current.get();
        if (existing == null || existing.isEmpty()) {
            return requested;
        }
        Map<String, String> result = new LinkedHashMap<>(requested);
        existing.forEach((key, value) -> {
            if (isFeatureOwned(key)) {
                result.putIfAbsent(key, value);
            }
        });
        return result;
    }

    private boolean isFeatureOwned(String key) {
        return keys.contains(key) || prefixes.stream().anyMatch(key::startsWith);
    }
}
