/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * Base class for all map-based registry storage implementation.  Examples of 
 * subclasses of this might be an in-memory impl as well as an Infinispan impl.
 * @author Ales Justin
 */
public abstract class AbstractMapRegistryStorage implements RegistryStorage {

    private Map<String, Map<Long, Map<String, String>>> storage;
    private Map<Long, Map<String, String>> global;
    private Map<String, Map<String, String>> artifactRules;
    private Map<String, String> globalRules;

    @PostConstruct
    public void init() {
        storage = createStorageMap();
        global = createGlobalMap();
        globalRules = createGlobalRulesMap();
        artifactRules = createArtifactRulesMap();
        afterInit();
    }

    protected void afterInit() {
    }

    protected abstract long nextGlobalId();
    protected abstract Map<String, Map<Long, Map<String, String>>> createStorageMap();
    protected abstract Map<Long, Map<String, String>> createGlobalMap();
    protected abstract Map<String, String> createGlobalRulesMap();
    protected abstract Map<String, Map<String, String>> createArtifactRulesMap();

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
        return StoredArtifact.builder()
            .content(content.get(MetaDataKeys.CONTENT))
            .version(Long.parseLong(content.get(MetaDataKeys.VERSION)))
            .id(Long.parseLong(content.get(MetaDataKeys.GLOBAL_ID)))
            .build();
    }

    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        return (id, m) -> (m == null) ? new ConcurrentHashMap<>() : m;
    }
    protected BiFunction<String, Map<String, String>, Map<String, String>> rulesLookupFn() {
        return (id, m) -> (m == null) ? new ConcurrentHashMap<>() : m;
    }

    protected ArtifactMetaDataDto createOrUpdateArtifact(String artifactId, ArtifactType artifactType, String content, boolean create, long globalId)
            throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        if (artifactId == null) {
            if (!create) {
                throw new ArtifactNotFoundException(artifactId);
            }
            artifactId = UUID.randomUUID().toString();
        }

        Map<Long, Map<String, String>> v2c = storage.compute(artifactId, lookupFn());

        if (create && v2c.size() > 0) {
            throw new ArtifactAlreadyExistsException(artifactId);
        }
        
        if (!create && v2c.size() == 0) {
            throw new ArtifactNotFoundException(artifactId);
        }

        long version = v2c.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        Map<String, String> contents = new ConcurrentHashMap<>();
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
        
        return MetaDataKeys.toArtifactMetaData(contents);
    }

    protected Map<String, String> getContentMap(long id) {
        Map<String, String> content = global.get(id);
        if (content == null) {
            throw new ArtifactNotFoundException(String.valueOf(id));
        }
        return content;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, ArtifactType, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, artifactType, content, true, nextGlobalId());
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
        if (v2c == null) {
            throw new ArtifactNotFoundException(artifactId);
        }
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
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, ArtifactType, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content)
            throws ArtifactNotFoundException, RegistryStorageException {
        try {
            return createOrUpdateArtifact(artifactId, artifactType, content, false, nextGlobalId());
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
        Map<String, String> content = getLatestContentMap(artifactId);
        if (metaData.getName() != null) {
            content.put(MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            content.put(MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        // check if the artifact exists
        getVersion2ContentMap(artifactId);
        // get the rules
        @SuppressWarnings("unchecked")
        Map<String, String> arules = artifactRules.getOrDefault(artifactId, Collections.EMPTY_MAP);
        return arules.keySet().stream().map(key -> RuleType.fromValue(key)).collect(Collectors.toList());
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // create a rule for the artifact
        String cdata = config.getConfiguration();
        Map<String, String> rules = artifactRules.compute(artifactId, rulesLookupFn());
        String prevValue = rules.putIfAbsent(rule.name(), cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(rule);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // delete rules
        artifactRules.remove(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @SuppressWarnings("unchecked")
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // get artifact rule
        Map<String, String> rules = artifactRules.getOrDefault(artifactId, Collections.EMPTY_MAP);
        String config = rules.get(rule.name());
        if (config == null) {
            throw new RuleNotFoundException(rule);
        }
        return new RuleConfigurationDto(config);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // update a rule for the artifact
        String cdata = config.getConfiguration();
        Map<String, String> rules = artifactRules.get(artifactId);
        if (rules == null) {
            throw new RuleNotFoundException(rule);
        }
        String prevValue = rules.put(rule.name(), cdata == null ? "" : cdata);
        if (prevValue == null) {
            rules.remove(rule.name());
            throw new RuleNotFoundException(rule);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // delete a rule for the artifact
        Map<String, String> rules = artifactRules.get(artifactId);
        if (rules == null) {
            throw new RuleNotFoundException(rule);
        }
        String prevValue = rules.remove(rule.name());
        if (prevValue == null) {
            throw new RuleNotFoundException(rule);
        }
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
        Map<String, String> content = getContentMap(id);
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
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        Map<String, String> removed = v2c.remove(version);
        if (removed == null) {
            throw new VersionNotFoundException(artifactId, version);
        }
        removed.put(MetaDataKeys.DELETED, Boolean.TRUE.toString());
        // remove from global as well
        long globalId = Long.parseLong(removed.get(MetaDataKeys.GLOBAL_ID));
        global.remove(globalId);
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
        Map<String, String> content = getContentMap(artifactId, version);
        if (metaData.getName() != null) {
            content.put(MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            content.put(MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version);
        content.remove(MetaDataKeys.NAME);
        content.remove(MetaDataKeys.DESCRIPTION);

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return globalRules.keySet().stream().map(key -> RuleType.fromValue(key)).collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        String cdata = config.getConfiguration();
        String prevValue = globalRules.putIfAbsent(rule.name(), cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(rule);
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
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        String cdata = globalRules.get(rule.name());
        if (cdata == null) {
            throw new RuleNotFoundException(rule);
        }
        return new RuleConfigurationDto(cdata);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        String rname = rule.name();
        if (!globalRules.containsKey(rname)) {
            throw new RuleNotFoundException(rule);
        }
        String cdata = config.getConfiguration();
        globalRules.put(rname, cdata == null ? "" : cdata);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        String prevValue = globalRules.remove(rule.name());
        if (prevValue == null) {
            throw new RuleNotFoundException(rule);
        }
    }
}
