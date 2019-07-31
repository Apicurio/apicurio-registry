package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ArtifactStorage;
import io.apicurio.registry.storage.model.Artifact;
import io.apicurio.registry.storage.model.ArtifactId;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_ID;
import static io.apicurio.registry.storage.CounterStorage.DEAFULT_INITIAL_VALUE;

@ApplicationScoped
@InMemory
public class IMArtifactStorage implements ArtifactStorage {

    @Inject
    @InMemory
    private IMArtifactSequenceStorage sequenceStorage;

    @Inject
    @InMemory
    private IMCounterStorage sync;

    private final Map<Long, ArtifactId> index = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        sequenceStorage.inject(this);
    }

    /**
     * Package-protected
     */
    ArtifactId indexGlobalArtifactId(ArtifactId baseArtifactId) {
        if (baseArtifactId.getId() != null)
            throw new IllegalStateException();
        long artifactId = sync.getAndIncById(ARTIFACT_ID, DEAFULT_INITIAL_VALUE);
        baseArtifactId.setId(artifactId);
        index.put(artifactId, baseArtifactId);
        return baseArtifactId;
    }

    /**
     * Package-protected
     */
    void unindexGlobalArtifactId(ArtifactId artifactId) {
        index.remove(artifactId.getId());
    }

    @Override
    public Artifact getByKey(Long key) {
        ArtifactId artifactId = index.get(key);
        if (artifactId == null) {
            return null;
        }
        return sequenceStorage.getByKey(artifactId).getByKey(artifactId);
    }
}
