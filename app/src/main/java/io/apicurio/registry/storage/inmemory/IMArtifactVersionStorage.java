package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ArtifactStorage;
import io.apicurio.registry.storage.ArtifactVersionStorage;
import io.apicurio.registry.storage.CounterStorage;
import io.apicurio.registry.storage.MetaDataStorage;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.apicurio.registry.storage.model.ArtifactVersionId;
import io.apicurio.registry.storage.model.MetaValue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_VERSION_ID_PREFIX;
import static io.apicurio.registry.storage.MetaDataStorage.KEY_LATEST_ARTIFACT_VERSION;

// TODO implementation can reuse these if they're heavy objects
public class IMArtifactVersionStorage implements ArtifactVersionStorage {

    private final Map<ArtifactVersionId, Long> storage = new ConcurrentHashMap<>();
    private final Map<ArtifactVersionId, MetaDataStorage> metadataStorage = new ConcurrentHashMap<>();

    private final CounterStorage counterStorage;
    private final IMGlobalArtifactStorage globalArtifactStorage;
    private final ArtifactStorage artifactStorage;
    private final ArtifactId artifactId;

    public IMArtifactVersionStorage(IMCounterStorage counterStorage, IMGlobalArtifactStorage globalArtifactStorage,
                                    IMArtifactStorage artifactStorage, ArtifactId artifactId) {
        this.counterStorage = counterStorage;
        this.globalArtifactStorage = globalArtifactStorage;
        this.artifactStorage = artifactStorage;
        this.artifactId = artifactId;
    }

    @Override
    public MetaDataStorage getMetadataStorage(ArtifactVersionId id) {
        return metadataStorage.computeIfAbsent(id, key -> new IMMetadataStorage());
    }

    @Override
    public ArtifactId getArtifactId() {
        return artifactId;
    }

    @Override
    public ArtifactVersion create(ArtifactVersion baseArtifactVersion) {
        if (baseArtifactVersion.getGlobalId() != null) {
            throw new StorageException("Can't create " + baseArtifactVersion + " with a non-null global ID.");
        }
        long nextId = counterStorage.incrementAndGet(ARTIFACT_VERSION_ID_PREFIX + artifactId.getArtifactId());
        ArtifactVersionId key = new ArtifactVersionId(artifactId.getArtifactId(), nextId);
        ArtifactVersion copy = new ArtifactVersion(
                key,
                baseArtifactVersion.getGlobalId() /* null */,
                baseArtifactVersion.getContent());
        copy = globalArtifactStorage.create(copy);
        storage.put(key, copy.getGlobalId());
        MetaValue mv = new MetaValue(KEY_LATEST_ARTIFACT_VERSION,
                String.valueOf(nextId), true);
        getMetadataStorage(key).put(mv.getKey(), mv);
        return copy;
    }

    @Override
    public List<ArtifactVersionId> getAllKeys() {
        return storage.keySet().stream()
                .sorted(Comparator.comparingLong(ArtifactVersionId::getVersionId))
                .collect(Collectors.toList());
    }

    @Override
    public ArtifactVersion getLatestVersion() {
        MetaValue metaValue = artifactStorage.getMetaDataStorage(artifactId)
                .get(KEY_LATEST_ARTIFACT_VERSION);
        ArtifactVersionId latest = null;
        if (metaValue != null) {
            latest = new ArtifactVersionId(artifactId.getArtifactId(),
                    Long.valueOf(metaValue.getValue()));
        } else {
            // fallback
            latest = getAllKeys().stream().findFirst().orElse(null);
        }
        if (latest == null) {
            return null;
        }
        return get(latest);
    }

    @Override
    public void put(ArtifactVersionId key, ArtifactVersion value) {
        // check the key is correct
        if (key != null && value != null
                && key.equals(value.getId())
                && value.getGlobalId() != null
                && value.equals(globalArtifactStorage.get(value.getGlobalId()))) {
            globalArtifactStorage.put(value.getGlobalId(), value);
        } else {
            throw new StorageException("Key " + key + " is not valid for value " + value);
        }
    }

    @Override
    public void delete(ArtifactVersionId key) {
        Long globalId = storage.get(key);
        if (globalId != null) {
            storage.remove(key);
            globalArtifactStorage.delete(globalId);
        }
    }

    @Override
    public ArtifactVersion get(ArtifactVersionId key) {
        Long globalId = storage.get(key);
        return globalArtifactStorage.get(globalId);
    }
}
