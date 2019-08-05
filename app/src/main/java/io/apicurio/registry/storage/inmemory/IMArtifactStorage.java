package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.ArtifactStorage;
import io.apicurio.registry.storage.ArtifactVersionStorage;
import io.apicurio.registry.storage.MetaDataStorage;
import io.apicurio.registry.storage.RuleInstanceStorage;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.model.ArtifactId;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.storage.CounterStorage.ARTIFACT_ID;

@ApplicationScoped
@InMemory
public class IMArtifactStorage implements ArtifactStorage {

    @Inject
    @InMemory
    private IMCounterStorage counterStorage;

    @Inject
    @InMemory
    private IMGlobalArtifactStorage globalArtifactStorage;

    private final Map<ArtifactId, ArtifactVersionStorage> storage = new ConcurrentHashMap<>();
    private final Map<ArtifactId, RuleInstanceStorage> ruleInstanceStorage = new ConcurrentHashMap<>();
    private final Map<ArtifactId, MetaDataStorage> metadataStorage = new ConcurrentHashMap<>();

    @Override
    public ArtifactVersionStorage create() {
        long nextId = counterStorage.incrementAndGet(ARTIFACT_ID);
        ArtifactId key = new ArtifactId(String.valueOf(nextId));
        ArtifactVersionStorage value = new IMArtifactVersionStorage(counterStorage, globalArtifactStorage, this, key);
        storage.put(key, value);
        return value;
    }

    @Override
    public ArtifactVersionStorage create(String artifactId) {
        try {
            Long.valueOf(artifactId);
            throw new StorageException("Creating artifact by specifying numeric artifact ID explicitly " +
                    "('" + artifactId + "') is not allowed.");
        } catch (NumberFormatException ex) {
            // ok
        }
        ArtifactId key = new ArtifactId(artifactId);
        ArtifactVersionStorage value = storage.get(key);
        if (value != null)
            throw new StorageException("An artifact with ID '" + key + "' " +
                    "already exists.");
        value = new IMArtifactVersionStorage(counterStorage, globalArtifactStorage, this, key);
        storage.put(key, value);
        return value;
    }

    @Override
    public ArtifactVersionStorage get(ArtifactId key) {
        return storage.get(key);
    }

    @Override
    public Set<ArtifactId> getAllKeys() {
        return storage.keySet();
    }

    @Override
    public RuleInstanceStorage getRuleInstanceStorage(ArtifactId id) {
        RuleInstanceStorage storage = ruleInstanceStorage.computeIfAbsent(id, key -> new IMRuleInstanceStorage());
        return storage;
    }

    @Override
    public MetaDataStorage getMetaDataStorage(ArtifactId id) {
        MetaDataStorage storage = metadataStorage.computeIfAbsent(id, key -> new IMMetadataStorage());
        return storage;
    }

    @Override
    public void delete(ArtifactId key) {
        storage.remove(key);
    }
}
