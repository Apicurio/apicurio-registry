package io.apicurio.registry.storage.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import io.apicurio.registry.rest.beans.ArtifactType;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.MetaDataKeys;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;

/**
 * @author Ales Justin
 */
public abstract class AbstractMapRegistryStorage implements RegistryStorage {

    private Map<String, Map<Long, Map<String, String>>> storage;
    private Map<Long, Map<String, String>> global;
    private Map<String, String> globalRules;

    @PostConstruct
    public void init() {
        storage = createStorageMap();
        global = createGlobalMap();
        globalRules = createGlobalRulesMap();
        afterInit();
    }

    protected void afterInit() {
    }

    protected abstract long nextGlobalId();
    protected abstract Map<String, Map<Long, Map<String, String>>> createStorageMap();
    protected abstract Map<Long, Map<String, String>> createGlobalMap();
    protected abstract Map<String, String> createGlobalRulesMap();

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

    private ArtifactMetaDataDto createOrUpdateArtifact(String artifactId, ArtifactType artifactType, String content, boolean create)
            throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
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

        long version = v2c.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        Map<String, String> contents = new ConcurrentHashMap<>();
        long globalId = nextGlobalId();
        // TODO not yet properly handling createdOn vs. modifiedOn for multiple versions
        contents.put(MetaDataKeys.CONTENT, content);
        contents.put(MetaDataKeys.VERSION, Long.toString(version));
        contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
        contents.put(MetaDataKeys.ARTIFACT_ID, artifactId);
        contents.put(MetaDataKeys.CREATED_ON, String.valueOf(System.currentTimeMillis()));
//        contents.put(MetaDataKeys.NAME, null);
//        contents.put(MetaDataKeys.DESCRIPTION, null);
        contents.put(MetaDataKeys.TYPE, artifactType.value());
        // TODO -- createdBy, modifiedBy

        // Store in v2c
        Map<String, String> previous = v2c.putIfAbsent(version, contents);
        // loop, due to possible race-condition
        while (previous != null) {
            version++;
            contents.put(MetaDataKeys.VERSION, Long.toString(version));
            previous = v2c.putIfAbsent(version, contents);
        }
        storage.put(artifactId, v2c);

        // Also store in global
        global.put(globalId, contents);
        
        ArtifactMetaDataDto dto = MetaDataKeys.toArtifactMetaData(contents);
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.rest.beans.ArtifactType, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, artifactType, content, true);
        } catch (ArtifactNotFoundException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = storage.remove(artifactId);
        v2c.values().forEach(m -> {
            m.put(MetaDataKeys.DELETED, Boolean.TRUE.toString());
            long globalId = Long.parseLong(m.get(MetaDataKeys.GLOBAL_ID));
            global.remove(globalId);
        });
        return new TreeSet<>(v2c.keySet());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return toStoredArtifact(getLatestContentMap(artifactId));
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.rest.beans.ArtifactType, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content)
            throws ArtifactNotFoundException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, artifactType, content, false);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds()
     */
    @Override
    public Set<String> getArtifactIds() {
        return storage.keySet();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getLatestContentMap(artifactId);
        return MetaDataKeys.toArtifactMetaData(content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    public List<String> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String artifactId, String ruleName, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, java.lang.String)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, String ruleName, RuleConfigurationDto rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        // TODO -- always new TreeSet ... optimization?!
        return new TreeSet<>(v2c.keySet());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = global.get(id);
        if (content == null) {
            throw new ArtifactNotFoundException(String.valueOf(id));
        }
        return toStoredArtifact(content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        return toStoredArtifact(content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        content.put(MetaDataKeys.DELETED, Boolean.TRUE.toString());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        return MetaDataKeys.toArtifactVersionMetaData(content);
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    public List<String> getGlobalRules() throws RegistryStorageException {
        List<String> ruleNames = new ArrayList<>();
        ruleNames.addAll(globalRules.keySet());
        return ruleNames;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(java.lang.String, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        String cdata = config.getConfiguration();
        String prevValue = globalRules.putIfAbsent(ruleName, cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(ruleName);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        globalRules.clear();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(java.lang.String)
     */
    @Override
    public RuleConfigurationDto getGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {
        String cdata = globalRules.get(ruleName);
        if (cdata == null) {
            throw new RuleNotFoundException(ruleName);
        }
        return new RuleConfigurationDto(cdata);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(java.lang.String, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        if (!globalRules.containsKey(ruleName)) {
            throw new RuleNotFoundException(ruleName);
        }
        String cdata = config.getConfiguration();
        globalRules.put(ruleName, cdata == null ? "" : cdata);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(java.lang.String)
     */
    @Override
    public void deleteGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {
        String prevValue = globalRules.remove(ruleName);
        if (prevValue == null) {
            throw new RuleNotFoundException(ruleName);
        }
    }
}
