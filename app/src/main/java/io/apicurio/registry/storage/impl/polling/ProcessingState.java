package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

public class ProcessingState {

    @Getter
    private final PollingStorageConfig config;

    @Getter
    private final RegistryStorage storage;

    @Getter
    @Setter
    private Registry currentRegistry;

    @Getter
    @Setter
    private Instant commitTime;
    private final List<String> errors = new ArrayList<>();

    @Getter
    private final Map<String, PollingDataFile> pathIndex = new HashMap<>();

    private final Map<Type, Set<PollingDataFile>> typeIndex = new HashMap<>();

    // Track content already imported by hash to enable deduplication
    @Getter
    private final Map<String, Long> contentHashToId = new HashMap<>();

    // Track artifact sources for cross-repo conflict detection (groupId:artifactId -> sourceId)
    private final Map<String, String> artifactSources = new HashMap<>();

    // Counters for summary logging
    @Getter
    private int groupCount = 0;
    @Getter
    private int artifactCount = 0;
    @Getter
    private int versionCount = 0;

    public void incrementGroupCount() { groupCount++; }
    public void incrementArtifactCount() { artifactCount++; }
    public void incrementVersionCount() { versionCount++; }

    public ProcessingState(PollingStorageConfig config, RegistryStorage storage) {
        this.config = config;
        this.storage = storage;
    }

    /**
     * Checks whether an artifact from the given source conflicts with a previously
     * registered artifact from a different source. If no conflict, registers the artifact.
     *
     * @return true if there is a conflict (same artifact from a different source)
     */
    public boolean checkArtifactConflict(String groupId, String artifactId, String sourceId) {
        String key = groupId + ":" + artifactId;
        String previousSource = artifactSources.putIfAbsent(key, sourceId);
        return previousSource != null && !previousSource.equals(sourceId);
    }

    /**
     * Returns the source ID that first registered the given artifact.
     * Used for conflict error messages.
     */
    public String getArtifactSource(String groupId, String artifactId) {
        return artifactSources.get(groupId + ":" + artifactId);
    }

    public void recordError(String message, Object... params) {
        errors.add(String.format(message, params));
    }

    public boolean isSuccessful() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return unmodifiableList(errors);
    }

    public PollingProcessingResult getResult() {
        return isSuccessful()
                ? PollingProcessingResult.success(groupCount, artifactCount, versionCount)
                : PollingProcessingResult.failure(errors);
    }

    public Set<PollingDataFile> fromTypeIndex(Type type) {
        return unmodifiableSet(typeIndex.computeIfAbsent(type, k -> new HashSet<>()));
    }

    public void index(PollingDataFile file) {
        pathIndex.put(file.getSourceId() + ":" + file.getPath(), file);
        file.getAny().ifPresent(a -> typeIndex.computeIfAbsent(a.getType(), k -> new HashSet<>()).add(file));
    }
}
