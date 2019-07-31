package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ArtifactSequence;
import io.apicurio.registry.storage.ArtifactSequenceStorage;
import io.apicurio.registry.storage.CounterStorage;
import io.apicurio.registry.storage.MetaData;
import io.apicurio.registry.storage.model.Artifact;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactSequenceId;
import io.apicurio.registry.storage.model.MetaValue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_SEQUENCE_VERSION_PREFIX;

// TODO implementation can reuse these if they're heavy objects
public class IMArtifactSequence extends AbstractIMKeyValueStorage<ArtifactId, Artifact>
        implements ArtifactSequence {


    private IMArtifactStorage artifactStorage;

    private final Map<ArtifactId, MetaData> metadataStorage = new ConcurrentHashMap<>();

    private final CounterStorage counterStorage;
    private final ArtifactSequenceStorage parentStorage;
    private final ArtifactSequenceId seqId;

    public IMArtifactSequence(IMCounterStorage counterStorage, IMArtifactStorage artifactStorage, IMArtifactSequenceStorage parentStorage,
                              ArtifactSequenceId seqId) {
        this.counterStorage = counterStorage;
        this.artifactStorage = artifactStorage;
        this.parentStorage = parentStorage;
        this.seqId = seqId;
    }

    @Override
    public MetaData getMetadataStorage(ArtifactId id) {
        MetaData storage = metadataStorage.computeIfAbsent(id, key -> new IMMetadataStorage());
        return storage;
    }

    @Override
    public ArtifactSequenceId getId() {
        return seqId;
    }

    @Override
    public ArtifactId create(Artifact value) {
        long nextId = counterStorage.getAndIncById(ARTIFACT_SEQUENCE_VERSION_PREFIX + seqId.getSequence(), 1);
        ArtifactId key = new ArtifactId(seqId.getSequence(), nextId);
        key = artifactStorage.indexGlobalArtifactId(key);
        // TODO possible race-condition
        storage.put(key, value);
        return key;
    }

    @Override
    public List<ArtifactId> getAllKeys() {
        return storage.keySet().stream()
                .sorted(Comparator.comparingLong(ArtifactId::getVersion))
                .collect(Collectors.toList());
    }

    @Override
    public ArtifactId getLatestVersion() {
        // TODO keep the latest version key in the metadata or just sort?
        MetaValue metaValue = parentStorage.getMetaDataStorage(seqId).getByKey(MetaData.MODIFIED_ON);
        ArtifactId latest = null;
        if (metaValue == null) {
            // fallback
            latest = getAllKeys().stream().findFirst().orElse(null);
        } else {
            latest = new ArtifactId(seqId.getSequence() /* TODO add constructor variant*/, Long.valueOf(metaValue.getContent()));
            // TODO verify it actually exists... It should not be deleted but still...
        }
        return latest;
    }

    @Override
    public void delete(ArtifactId key) {
        artifactStorage.unindexGlobalArtifactId(key);
        super.delete(key);
    }
}
