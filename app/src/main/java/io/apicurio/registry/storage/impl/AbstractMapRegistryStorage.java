package io.apicurio.registry.storage.impl;

import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.MetaDataKeys;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

/**
 * @author Ales Justin
 */
public abstract class AbstractMapRegistryStorage implements RegistryStorage {

    private Map<String, Map<Long, Map<String, String>>> storage;
    private Map<Long, Map<String, String>> global;

    @PostConstruct
    public void init() {
        storage = createStorageMap();
        global = createGlobalMap();
        afterInit();
    }

    protected void afterInit() {
    }

    protected abstract long nextGlobalId();
    protected abstract Map<String, Map<Long, Map<String, String>>> createStorageMap();
    protected abstract Map<Long, Map<String, String>> createGlobalMap();

    private Map<Long, Map<String, String>> getVersion2ContentMap(String artifactId) throws ArtifactNotFoundException {
        Map<Long, Map<String, String>> v2c = storage.get(artifactId);
        if (v2c == null) {
            throw new ArtifactNotFoundException(artifactId);
        }
        return v2c;
    }

    private Map<String, String> getContentMap(String artifactId, Long version) throws ArtifactNotFoundException, VersionNotFoundException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        Map<String, String> content = v2c.get(version);
        if (content == null) {
            throw new VersionNotFoundException(artifactId, version);
        }
        return content;
    }

    private Map<String, String> getLatestContentMap(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        return v2c.entrySet()
                  .stream()
                  .max((e1, e2) -> (int) (e1.getKey() - e2.getKey()))
                  .orElseThrow(() -> new RegistryStorageException("Race-condition?!", null))
                  .getValue();

    }

    private StoredArtifact toStoredArtifact(Map<String, String> content) {
        StoredArtifact storedArtifact = new StoredArtifact();
        storedArtifact.content = content.get(MetaDataKeys.CONTENT);
        storedArtifact.version = Long.parseLong(content.get(MetaDataKeys.VERSION));
        storedArtifact.id = Long.parseLong(content.get(MetaDataKeys.GLOBAL_ID));
        return storedArtifact;
    }

    private Map<String, String> createOrUpdateArtifact(String artifactId, String content, boolean create) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        if (artifactId == null) {
            if (!create) {
                throw new ArtifactNotFoundException(artifactId);
            }
            artifactId = UUID.randomUUID().toString();
        }

        Map<Long, Map<String, String>> v2c = storage.compute(artifactId, (id, m) -> {
            if (m == null) {
                m = new ConcurrentHashMap<>();
            }
            return m;
        });

        if (create && v2c.size() > 0) {
            throw new ArtifactAlreadyExistsException(artifactId);
        }

        long version = v2c.keySet().stream().max(Long::compareTo).orElse(1L);
        Map<String, String> contents = new ConcurrentHashMap<>();
        Map<String, String> previous = v2c.putIfAbsent(version, contents);
        // loop, due to possible race-condition
        while (previous != null) {
            version++;
            previous = v2c.putIfAbsent(version, contents);
        }
        storage.put(artifactId, v2c);

        long globalId = nextGlobalId();
        contents.put(MetaDataKeys.CONTENT, content);
        contents.put(MetaDataKeys.VERSION, Long.toString(version));
        contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
        contents.put(MetaDataKeys.ARTIFACT_ID, artifactId);
        // TODO -- creator, etc
        global.put(globalId, contents);

        return Collections.unmodifiableMap(contents);
    }

    @Override
    public Map<String, String> createArtifact(String artifactId, String content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, content, true);
        } catch (ArtifactNotFoundException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        v2c.values().forEach(m -> m.put(MetaDataKeys.DELETED, Boolean.TRUE.toString()));
        return new TreeSet<>(v2c.keySet());
    }

    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return toStoredArtifact(getLatestContentMap(artifactId));
    }

    @Override
    public Map<String, String> updateArtifact(String artifactId, String content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, content, false);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public Set<String> getArtifactIds() {
        return storage.keySet();
    }

    @Override
    public Map<String, String> getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getLatestContentMap(artifactId);
        return MetaDataKeys.toMetaData(content);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, Map<String, String> metaData) throws ArtifactNotFoundException, RegistryStorageException {

    }

    @Override
    public List<String> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return null;
    }

    @Override
    public void createArtifactRule(String artifactId, String ruleName, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {

    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

    }

    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return null;
    }

    @Override
    public void updateArtifactRule(String artifactId, String ruleName, RuleConfigurationDto rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    @Override
    public void deleteArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        // TODO -- always new TreeSet ... optimization?!
        return new TreeSet<>(v2c.keySet());
    }

    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = global.get(id);
        if (content == null) {
            throw new ArtifactNotFoundException(String.valueOf(id));
        }
        return toStoredArtifact(content);
    }

    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        return toStoredArtifact(content);
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        content.put(MetaDataKeys.DELETED, Boolean.TRUE.toString());
    }

    @Override
    public Map<String, String> getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        return MetaDataKeys.toMetaData(content);
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, Map<String, String> metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    @Override
    public List<String> getGlobalRules() throws RegistryStorageException {
        return null;
    }

    @Override
    public void createGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {

    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {

    }

    @Override
    public RuleConfigurationDto getGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {
        return null;
    }

    @Override
    public void updateGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {

    }

    @Override
    public void deleteGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {

    }
}
