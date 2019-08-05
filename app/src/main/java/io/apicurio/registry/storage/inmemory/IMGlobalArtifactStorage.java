package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.GlobalArtifactStorage;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.apicurio.registry.storage.model.ArtifactVersionId;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_ID;

@ApplicationScoped
@InMemory
public class IMGlobalArtifactStorage implements GlobalArtifactStorage {

    @Inject
    @InMemory
    private IMCounterStorage counterStorage;

    private final Map<Long, ArtifactVersion> storage = new ConcurrentHashMap<>();

    // package-protected
    ArtifactVersion create(ArtifactVersion baseArtifactVersion) {
        if (baseArtifactVersion.getGlobalId() != null) {
            throw new StorageException("Can't create " + baseArtifactVersion + " with a non-null global ID.");
        }
        long nextGlobalId = counterStorage.incrementAndGet(ARTIFACT_ID);
        ArtifactVersion copy = new ArtifactVersion(
                baseArtifactVersion.getId(),
                nextGlobalId,
                baseArtifactVersion.getContent());
        storage.put(nextGlobalId, copy);
        return copy;
    }

    @Override
    public ArtifactVersion get(Long key) {
        return storage.get(key);
    }

    // package-protected
    void put(Long key, ArtifactVersion value) {
        storage.put(key, value);
    }

    public void delete(Long key) {
        storage.remove(key);
    }
}
