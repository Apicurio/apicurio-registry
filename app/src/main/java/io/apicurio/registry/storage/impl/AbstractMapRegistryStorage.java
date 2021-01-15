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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.storage.*;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.SearchUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.apicurio.registry.storage.MetaDataKeys.VERSION;
import static io.apicurio.registry.utils.StringUtil.isEmpty;

/**
 * Base class for all map-based registry storage implementation.  Examples of 
 * subclasses of this might be an in-memory impl as well as an Infinispan impl.
 *
 * @author Ales Justin
 */
public abstract class AbstractMapRegistryStorage implements RegistryStorage {

    private static final int ARTIFACT_FIRST_VERSION = 1;

    @Inject
    protected ArtifactTypeUtilProviderFactory factory;

    protected StorageMap storage;
    protected Map<Long, TupleId> global;
    protected MultiMap<String, String, String> artifactRules;
    protected Map<String, String> globalRules;
    

    protected void beforeInit() {
    }

    @PostConstruct
    public void init() {
        beforeInit();
        storage = createStorageMap();
        global = createGlobalMap();
        globalRules = createGlobalRulesMap();
        artifactRules = createArtifactRulesMap();
        afterInit();
    }

    protected void afterInit() {
    }

    protected abstract long nextGlobalId();

    protected abstract StorageMap createStorageMap();

    protected abstract Map<Long, TupleId> createGlobalMap();

    protected abstract Map<String, String> createGlobalRulesMap();

    protected abstract MultiMap<String, String, String> createArtifactRulesMap();

    private Map<Long, Map<String, String>> getVersion2ContentMap(String artifactId) throws ArtifactNotFoundException {
        Map<Long, Map<String, String>> v2c = storage.get(artifactId);
        if (v2c == null || v2c.isEmpty()) {
            throw new ArtifactNotFoundException(artifactId);
        }
        return Collections.unmodifiableMap(v2c);
    }

    private Map<String, String> getContentMap(String artifactId, Long version, EnumSet<ArtifactState> states) throws ArtifactNotFoundException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        Map<String, String> content = v2c.get(version);
        if (content == null) {
            throw new VersionNotFoundException(artifactId, version);
        }

        ArtifactState state = ArtifactStateExt.getState(content);
        ArtifactStateExt.validateState(states, state, artifactId, version);

        return Collections.unmodifiableMap(content);
    }

    public static Predicate<Map.Entry<Long, Map<String, String>>> statesFilter(EnumSet<ArtifactState> states) {
        return e -> states.contains(ArtifactStateExt.getState(e.getValue()));
    }

    private Map<String, String> getLatestContentMap(String artifactId, EnumSet<ArtifactState> states) throws ArtifactNotFoundException, RegistryStorageException {
        Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        Stream<Map.Entry<Long, Map<String, String>>> stream = v2c.entrySet().stream();
        if (states != null) {
            stream = stream.filter(statesFilter(states));
        }
        Map<String, String> latest = stream.max((e1, e2) -> (int) (e1.getKey() - e2.getKey()))
                                           .orElseThrow(() -> new ArtifactNotFoundException(artifactId))
                                           .getValue();

        ArtifactStateExt.logIfDeprecated(artifactId, ArtifactStateExt.getState(latest), latest.get(VERSION));

        return Collections.unmodifiableMap(latest);
    }

    private boolean filterSearchResult(String search, String artifactId, SearchOver searchOver) {
        if (search == null || search.trim().isEmpty()) {
            return true;
        }
        try {
            switch (searchOver) {
                case name:
                    return valueContainsSearch(search, artifactId, searchOver.name()) || valueContainsSearch(search, artifactId, MetaDataKeys.ARTIFACT_ID);
                case description:
                case labels:
                    return valueContainsSearch(search, artifactId, searchOver.name());
                default:
                    return getLatestContentMap(artifactId, ArtifactStateExt.ACTIVE_STATES)
                        .values()
                        .stream()
                        .anyMatch(v -> v != null && StringUtils.containsIgnoreCase(v, search));
            }
        } catch (ArtifactNotFoundException notFound) {
            return false;
        }
    }

    private boolean valueContainsSearch(String search, String artifactId, String metaDataKey) {
        String value = getLatestContentMap(artifactId, ArtifactStateExt.ACTIVE_STATES).get(metaDataKey);
        return value != null && StringUtils.containsIgnoreCase(value, search.toLowerCase());
    }

    @Nullable
    private ArtifactMetaDataDto getArtifactMetadataOrNull(String artifactId) {
        try {
            return getArtifactMetaData(artifactId);
        } catch (ArtifactNotFoundException ex) {
            return null;
        }
    }

    public static StoredArtifact toStoredArtifact(Map<String, String> content) {
        return StoredArtifact.builder()
                             .content(ContentHandle.create(MetaDataKeys.getContent(content)))
                             .version(Long.parseLong(content.get(VERSION)))
                             .id(Long.parseLong(content.get(MetaDataKeys.GLOBAL_ID)))
                             .build();
    }

    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        return (id, m) -> (m == null) ? new ConcurrentHashMap<>() : m;
    }

    protected ArtifactMetaDataDto createOrUpdateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content, boolean create, long globalId)
            throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        if (artifactId == null) {
            if (!create) {
                throw new ArtifactNotFoundException("Null artifactId!");
            }
            artifactId = UUID.randomUUID().toString();
        }

        Map<Long, Map<String, String>> v2c = storage.compute(artifactId);

        if (create && v2c.size() > 0) {
            throw new ArtifactAlreadyExistsException(artifactId);
        }
        
        if (!create && v2c.size() == 0) {
            storage.remove(artifactId); // remove, as we just "computed" empty map
            throw new ArtifactNotFoundException(artifactId);
        }

        long version = v2c.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
        long prevVersion = version - 1;

        Map<String, String> contents = new ConcurrentHashMap<>();
        MetaDataKeys.putContent(contents, content.bytes());
        contents.put(VERSION, Long.toString(version));
        contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
        contents.put(MetaDataKeys.ARTIFACT_ID, artifactId);

        String currentTimeMillis = String.valueOf(System.currentTimeMillis());
        contents.put(MetaDataKeys.CREATED_ON, currentTimeMillis);
        contents.put(MetaDataKeys.MODIFIED_ON, currentTimeMillis);

        contents.put(MetaDataKeys.TYPE, artifactType.value());
        ArtifactStateExt.applyState(contents, ArtifactState.ENABLED);
        // TODO -- createdBy, modifiedBy

        // Carry over some meta-data from the previous version on an update.
        if (!create) {
            Map<String, String> prevContents = v2c.get(prevVersion);
            if (prevContents != null) {
                if (prevContents.containsKey(MetaDataKeys.NAME)) {
                    contents.put(MetaDataKeys.NAME, prevContents.get(MetaDataKeys.NAME));
                }
                if (prevContents.containsKey(MetaDataKeys.DESCRIPTION)) {
                    contents.put(MetaDataKeys.DESCRIPTION, prevContents.get(MetaDataKeys.DESCRIPTION));
                }
            }
        }

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        EditableMetaData emd = extractor.extract(content);
        if (extractor.isExtracted(emd)) {
            if (!isEmpty(emd.getName())) {
                contents.put(MetaDataKeys.NAME, emd.getName());
            }
            if (!isEmpty(emd.getDescription())) {
                contents.put(MetaDataKeys.DESCRIPTION, emd.getDescription());
            }
        }

        // Store in v2c -- make sure version is unique!!
        storage.createVersion(artifactId, version, contents);

        // Also store in global
        global.put(globalId, new TupleId(artifactId, version));
        
        return MetaDataKeys.toArtifactMetaData(contents);
    }

    protected Map<String, String> getContentMap(long id) {
        TupleId mapping = global.get(id);
        if (mapping == null) {
            throw new ArtifactNotFoundException(String.valueOf(id));
        }
        Map<String, String> content = getContentMap(mapping.getId(), mapping.getVersion(), ArtifactStateExt.ACTIVE_STATES);
        if (content == null) {
            throw new ArtifactNotFoundException(String.valueOf(id));
        }
        ArtifactStateExt.logIfDeprecated(id, ArtifactStateExt.getState(content), content.get(VERSION));
        return content; // already unmodifiable
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        updateArtifactState(artifactId, state, null);
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        Map<String, String> content = null;
        if (version == null) {
            content = getLatestContentMap(artifactId, null);
            version = Integer.parseInt(content.get(VERSION));
        }
        int fVersion = version;
        if (state == ArtifactState.DELETED) {
            deleteArtifactVersionInternal(artifactId, version);
        } else {
            if (content == null) {
                content = getContentMap(artifactId, version.longValue(), null);
            }
            ArtifactStateExt.applyState(s -> storage.put(artifactId, fVersion, MetaDataKeys.STATE, s.name()), content, state);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, ArtifactType, ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content)
    throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(artifactId, artifactType, content, true, nextGlobalId());
            return CompletableFuture.completedFuture(amdd);
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
            long globalId = Long.parseLong(m.get(MetaDataKeys.GLOBAL_ID));
            global.remove(globalId);
        });
        this.deleteArtifactRulesInternal(artifactId);
        return new TreeSet<>(v2c.keySet());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return toStoredArtifact(getLatestContentMap(artifactId, ArtifactStateExt.ACTIVE_STATES));
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, ArtifactType, ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(artifactId, artifactType, content, false, nextGlobalId());
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(Integer limit)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        if (limit != null) {
            return storage.keySet()
                    .stream()
                    .limit(limit)
                    .collect(Collectors.toSet());
        } else {
            return storage.keySet();
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(String, int, int, SearchOver, SortOrder) ()
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver over, SortOrder order) {
        final LongAdder itemsCount = new LongAdder();
        final List<SearchedArtifact> matchedArtifacts = getArtifactIds(null)
                .stream()
                .filter(artifactId -> filterSearchResult(search, artifactId, over))
                .peek(artifactId -> itemsCount.increment())
                .map(this::getArtifactMetadataOrNull)
                .filter(Objects::nonNull)
                .sorted(SearchUtil.comparator(order))
                .skip(offset)
                .limit(limit)
                .map(SearchUtil::buildSearchedArtifact)
                .collect(Collectors.toList());

        final ArtifactSearchResults artifactSearchResults = new ArtifactSearchResults();
        artifactSearchResults.setArtifacts(matchedArtifacts);
        artifactSearchResults.setCount(itemsCount.intValue());

        return artifactSearchResults;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        final Map<String, String> content = getLatestContentMap(artifactId, ArtifactStateExt.ACTIVE_STATES);
        final HashMap<String, String> artifactContent = new HashMap<>(content);

        final ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(content);
        if (artifactMetaDataDto.getVersion() != ARTIFACT_FIRST_VERSION) {
            ArtifactVersionMetaDataDto firstVersionContent = getArtifactVersionMetaData(artifactId, ARTIFACT_FIRST_VERSION);
            artifactMetaDataDto.setCreatedOn(firstVersionContent.getCreatedOn());
        }
        return MetaDataKeys.toArtifactMetaData(artifactContent);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto metaData = getArtifactMetaData(artifactId);
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(metaData.getType());
        ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
        ContentHandle canonicalContent = canonicalizer.canonicalize(content);
        byte[] canonicalBytes = canonicalContent.bytes();
        Map<Long, Map<String, String>> map = getVersion2ContentMap(artifactId);
        for (Map<String, String> cMap : map.values()) {
            ContentHandle candidateContent = ContentHandle.create(MetaDataKeys.getContent(cMap));
            ContentHandle canonicalCandidateContent = canonicalizer.canonicalize(candidateContent);
            byte[] candidateBytes = canonicalCandidateContent.bytes();
            if (Arrays.equals(canonicalBytes, candidateBytes)) {
                ArtifactStateExt.logIfDeprecated(artifactId, ArtifactStateExt.getState(cMap), cMap.get(VERSION));
                final ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(cMap);
                artifactMetaDataDto.setCreatedOn(metaData.getCreatedOn());
                return artifactMetaDataDto;
            }
        }
        throw new ArtifactNotFoundException(artifactId);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(id);
        return MetaDataKeys.toArtifactMetaData(content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        if (metaData.getName() != null) {
            storage.put(artifactId, MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            storage.put(artifactId, MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
        if (metaData.getLabels() != null && !metaData.getLabels().isEmpty()) {
            storage.put(artifactId, MetaDataKeys.LABELS, String.join(",", metaData.getLabels()));
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
        Set<String> arules = artifactRules.keys(artifactId);
        return arules.stream().map(RuleType::fromValue).collect(Collectors.toList());
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        // create a rule for the artifact
        String cdata = config.getConfiguration();
        String prevValue = artifactRules.putIfAbsent(artifactId, rule.name(), cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(rule);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(artifactId);
        this.deleteArtifactRulesInternal(artifactId);
    }
    
    /**
     * Internal delete of artifact rules without checking for existence of artifact first.
     * @param artifactId
     * @throws RegistryStorageException
     */
    protected void deleteArtifactRulesInternal(String artifactId) throws RegistryStorageException {
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
        String config = artifactRules.get(artifactId, rule.name());
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
        String prevValue = artifactRules.putIfPresent(artifactId, rule.name(), cdata == null ? "" : cdata);
        if (prevValue == null) {
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
        String prevValue = artifactRules.remove(artifactId, rule.name());
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
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(String, int, int) (java.lang.String)
     */
    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) throws ArtifactNotFoundException, RegistryStorageException {

        final VersionSearchResults versionSearchResults = new VersionSearchResults();
        final Map<Long, Map<String, String>> v2c = getVersion2ContentMap(artifactId);
        final LongAdder itemsCount = new LongAdder();
        final List<SearchedVersion> artifactVersions = v2c.keySet().stream()
                .peek(version -> itemsCount.increment())
                .sorted(Long::compareTo)
                .skip(offset)
                .limit(limit)
                .map(version -> MetaDataKeys.toArtifactVersionMetaData(v2c.get(version)))
                .map(SearchUtil::buildSearchedVersion)
                .collect(Collectors.toList());

        versionSearchResults.setVersions(artifactVersions);
        versionSearchResults.setCount(itemsCount.intValue());

        return versionSearchResults;
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
        Map<String, String> content = getContentMap(artifactId, version, ArtifactStateExt.ACTIVE_STATES);
        return toStoredArtifact(content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        deleteArtifactVersionInternal(artifactId, version);
    }

    // internal - so we don't call sub-classes method
    private void deleteArtifactVersionInternal(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Long globalId = storage.remove(artifactId, version);
        if (globalId == null) {
            throw new VersionNotFoundException(artifactId, version);
        }
        // remove from global as well
        global.remove(globalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(artifactId, version, null);
        return MetaDataKeys.toArtifactVersionMetaData(content);
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        if (metaData.getName() != null) {
            storage.put(artifactId, version, MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            storage.put(artifactId, version, MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.remove(artifactId, version, MetaDataKeys.NAME);
        storage.remove(artifactId, version, MetaDataKeys.DESCRIPTION);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return globalRules.keySet().stream().map(RuleType::fromValue).collect(Collectors.toList());
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
