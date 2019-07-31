package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ArtifactSequence;
import io.apicurio.registry.storage.ArtifactSequenceStorage;
import io.apicurio.registry.storage.MetaData;
import io.apicurio.registry.storage.RuleInstanceStorage;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.model.ArtifactSequenceId;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_SEQUENCE_ID;
import static io.apicurio.registry.storage.CounterStorage.DEAFULT_INITIAL_VALUE;

@ApplicationScoped
@InMemory
public class IMArtifactSequenceStorage extends AbstractROIMKeyValueStorage<ArtifactSequenceId, ArtifactSequence>
        implements ArtifactSequenceStorage {

    @Inject
    @InMemory
    private IMCounterStorage counterStorage;

    //@Inject
    private IMArtifactStorage artifactStorage;

    private final Map<ArtifactSequenceId, RuleInstanceStorage> ruleInstanceStorage = new ConcurrentHashMap<>();
    private final Map<ArtifactSequenceId, MetaData> metadataStorage = new ConcurrentHashMap<>();

    @Override
    public ArtifactSequence create() {
        long nextId = counterStorage.getAndIncById(ARTIFACT_SEQUENCE_ID, DEAFULT_INITIAL_VALUE);
        ArtifactSequenceId key = new ArtifactSequenceId(String.valueOf(nextId));

        ArtifactSequence value = new IMArtifactSequence(counterStorage, artifactStorage, this, key);
        storage.put(key, value);
        return value;
    }

    @Override
    public ArtifactSequence create(String sequenceId) {
        ArtifactSequenceId key = new ArtifactSequenceId(sequenceId);

        ArtifactSequence value = storage.get(key);
        if (value != null)
            throw new StorageException("A Sequence with key " + key + " already exists.");

        value = new IMArtifactSequence(counterStorage, artifactStorage, this, key);
        storage.put(key, value);
        return value;
    }

    @Override
    public Set<ArtifactSequenceId> getAllKeys() {
        return storage.keySet();
    }

    @Override
    public RuleInstanceStorage getRuleStorage(ArtifactSequenceId id) {
        RuleInstanceStorage storage = ruleInstanceStorage.computeIfAbsent(id, key -> new IMRuleInstanceStorage());
        return storage;
    }

    @Override
    public MetaData getMetaDataStorage(ArtifactSequenceId id) {
        MetaData storage = metadataStorage.computeIfAbsent(id, key -> new IMMetadataStorage());
        return storage;
    }

    @Override
    public void delete(ArtifactSequenceId key) {
        storage.remove(key);
    }

    public void inject(IMArtifactStorage imArtifactStorage) {
        this.artifactStorage = imArtifactStorage;
    }
}
