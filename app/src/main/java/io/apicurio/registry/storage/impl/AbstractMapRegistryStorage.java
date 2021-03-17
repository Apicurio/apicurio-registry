/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

import static io.apicurio.registry.utils.StringUtil.isEmpty;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.mt.metadata.TenantMetadataDto;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.InvalidPropertiesException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.util.VersionUtil;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Base class for all map-based registry storage implementation.  Examples of
 * subclasses of this might be an in-memory impl as well as an Infinispan impl.
 *
 * @author Ales Justin
 */
public abstract class AbstractMapRegistryStorage extends AbstractRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(AbstractMapRegistryStorage.class);

    private static final int ARTIFACT_FIRST_VERSION = 1;

    @Inject
    protected ArtifactTypeUtilProviderFactory factory;

    @Inject
    protected SecurityIdentity securityIdentity;

    protected StorageMap storage;
    protected Map<Long, TupleId> global;
    // Map of contentHash -> StoredContent (SHA256 hash of content)
    protected Map<String, StoredContent> content;
    // Map of storage generated content id -> contentHash (to provide fast lookup of content by contentId)
    protected Map<Long, String> contentHash;
    protected MultiMap<ArtifactKey, String, String> artifactRules;
    protected Map<String, String> globalRules;
    protected Map<String, String> logConfigurations;
    protected Map<String, GroupMetaDataDto> groups;

    protected void beforeInit() {
    }

    @PostConstruct
    public void init() {
        beforeInit();
        content = createContentMap();
        contentHash = createContentHashMap();
        storage = createStorageMap();
        global = createGlobalMap();
        globalRules = createGlobalRulesMap();
        artifactRules = createArtifactRulesMap();
        logConfigurations = createLogConfigurationMap();
        groups = createGroupsMap();
        afterInit();
    }

    protected void afterInit() {
    }

    protected abstract long nextGlobalId();

    protected abstract long nextContentId();

    protected abstract Map<String, StoredContent> createContentMap();

    protected abstract Map<Long, String> createContentHashMap();

    protected abstract StorageMap createStorageMap();

    protected abstract Map<Long, TupleId> createGlobalMap();

    protected abstract Map<String, String> createGlobalRulesMap();

    protected abstract Map<String, GroupMetaDataDto> createGroupsMap();

    protected abstract MultiMap<ArtifactKey, String, String> createArtifactRulesMap();

    protected abstract Map<String, String> createLogConfigurationMap();

    private Map<String, Map<String, String>> getVersion2ContentMap(String groupId, String artifactId) throws ArtifactNotFoundException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);

        Map<String, Map<String, String>> v2c = storage.get(akey);
        if (v2c == null || v2c.isEmpty()) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        return Collections.unmodifiableMap(v2c);
    }

    private Map<String, String> getContentMap(String groupId, String artifactId, String version, EnumSet<ArtifactState> states) throws ArtifactNotFoundException {
        Map<String, Map<String, String>> v2c = getVersion2ContentMap(groupId, artifactId);
        Map<String, String> content = v2c.get(version);
        if (content == null) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }

        ArtifactState state = ArtifactStateExt.getState(content);
        ArtifactStateExt.validateState(states, state, groupId, artifactId, version);

        return Collections.unmodifiableMap(content);
    }

    public static Predicate<Map.Entry<String, Map<String, String>>> statesFilter(EnumSet<ArtifactState> states) {
        return e -> states.contains(ArtifactStateExt.getState(e.getValue()));
    }

    private static int versionId(Map<String, String> cmap) {
        return VersionUtil.toInteger(cmap.get(MetaDataKeys.VERSION_ID));
    }

    private Map<String, String> getLatestContentMap(String groupId, String artifactId, EnumSet<ArtifactState> states) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, Map<String, String>> v2c = getVersion2ContentMap(groupId, artifactId);
        Stream<Map.Entry<String, Map<String, String>>> stream = v2c.entrySet().stream();
        if (states != null) {
            stream = stream.filter(statesFilter(states));
        }
        Map<String, String> latest = stream.max((e1, e2) -> (versionId(e1.getValue()) - versionId(e2.getValue())))
                                           .orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId))
                                           .getValue();

        ArtifactStateExt.logIfDeprecated(groupId, artifactId, latest.get(MetaDataKeys.VERSION), ArtifactStateExt.getState(latest));

        return Collections.unmodifiableMap(latest);
    }

    private Map<String, String> getFirstContentMap(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, Map<String, String>> v2c = getVersion2ContentMap(groupId, artifactId);
        Stream<Map.Entry<String, Map<String, String>>> stream = v2c.entrySet().stream();
        Map<String, String> first = stream.min((e1, e2) -> (versionId(e1.getValue()) - versionId(e2.getValue())))
                                           .orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId))
                                           .getValue();
        return Collections.unmodifiableMap(first);
    }

    private boolean artifactMatchesFilters(ArtifactMetaDataDto artifactMetaData, Set<SearchFilter> filters) {
        boolean accepted = true;
        for (SearchFilter filter : filters) {
            accepted &= artifactMatchesFilter(artifactMetaData, filter);
        }
        return accepted;
    }

    private boolean artifactMatchesFilter(ArtifactMetaDataDto artifactMetaData, SearchFilter filter) {
        SearchFilterType type = filter.getType();
        String search = filter.getValue();
        switch (type) {
            case description:
                return valueContainsSearch(search, artifactMetaData.getDescription());
            case everything:
                return valueContainsSearch(search, artifactMetaData.getDescription()) ||
                       valueIsSearch(search, artifactMetaData.getGroupId()) ||
                       valueContainsSearch(search, artifactMetaData.getLabels()) ||
                       valueContainsSearch(search, artifactMetaData.getName()) ||
                       valueContainsSearch(search, artifactMetaData.getId());// ||
//                       valueContainsSearch(search, artifactMetaData.getProperties());
            case group:
                return valueIsSearchExact(search, artifactMetaData.getGroupId());
            case labels:
                return valueContainsSearch(search, artifactMetaData.getLabels());
            case name:
                return valueContainsSearch(search, artifactMetaData.getName()) || valueContainsSearch(search, artifactMetaData.getId());
            case properties:
                return valueContainsSearch(search, artifactMetaData.getProperties());
            case contentHash: {
                String contentHash = this.contentHash.get(artifactMetaData.getContentId());
                return valueIsSearchExact(search, contentHash);
            }
            case canonicalHash: {
                String contentHash = this.contentHash.get(artifactMetaData.getContentId());
                StoredContent storedContent = this.content.get(contentHash);
                return valueIsSearchExact(search, storedContent.getCanonicalHash());
            }
        }
        return false;
    }

    private boolean valueContainsSearch(String search, Map<String, String> values) {
        // TODO add searching over properties support!
        throw new RuntimeException("Searching over properties is not yet implemented.");
    }

    private boolean valueContainsSearch(String search, List<String> values) {
        if (values != null) {
            for (String value : values) {
                if (valueIsSearch(search, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean valueContainsSearch(String search, String value) {
        return value != null && StringUtils.containsIgnoreCase(value, search.toLowerCase());
    }

    private boolean valueIsSearch(String search, String value) {
        return value != null && StringUtils.equalsIgnoreCase(value, search.toLowerCase());
    }

    private boolean valueIsSearchExact(String search, String value) {
        return (search == null && value == null) || (value != null && StringUtils.equalsIgnoreCase(value, search));
    }

    private Optional<ArtifactMetaDataDto> getOptionalArtifactMetadata(ArtifactKey artifactKey) {
        try {
            return Optional.of(getArtifactMetaData(artifactKey.getGroupId(), artifactKey.getArtifactId()));
        } catch (ArtifactNotFoundException ex) {
            return Optional.empty();
        }
    }

    public StoredArtifactDto toStoredArtifact(Map<String, String> content) {
        String contentHash = content.get(MetaDataKeys.CONTENT_HASH);
        StoredContent storedContent = this.content.get(contentHash);
        return StoredArtifactDto.builder()
                             .content(ContentHandle.create(storedContent.getContent()))
                             .contentId(storedContent.getContentId())
                             .versionId(VersionUtil.toInteger(content.get(MetaDataKeys.VERSION_ID)))
                             .version(content.get(MetaDataKeys.VERSION))
                             .globalId(Long.parseLong(content.get(MetaDataKeys.GLOBAL_ID)))
                             .build();
    }

    protected BiFunction<String, Map<Long, Map<String, String>>, Map<Long, Map<String, String>>> lookupFn() {
        return (id, m) -> (m == null) ? new ConcurrentHashMap<>() : m;
    }

    public static String sha256Hash(ContentHandle chandle) {
        return DigestUtils.sha256Hex(chandle.bytes());
    }

    public ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            return canonicalizer.canonicalize(content);
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType.name());
            return content;
        }
    }

    protected StoredContent ensureStoredContent(ArtifactType artifactType, ContentHandle chandle) {
        String contentHash = sha256Hash(chandle);
        // Store the content inside the content store if not already there.
        StoredContent storedContent = this.content.computeIfAbsent(contentHash, contentFn(contentHash, artifactType, chandle.bytes()));
        // Create a mapping from contentId to contentHash if not already present.
        this.contentHash.putIfAbsent(storedContent.getContentId(), contentHash);
        return storedContent;
    }

    protected Function<String, StoredContent> contentFn(String contentHash, ArtifactType artifactType, byte[] bytes) {
        return (key) -> {
            long contentId = nextContentId();
            String canonicalHash = sha256Hash(canonicalizeContent(artifactType, ContentHandle.create(bytes)));
            StoredContent content = new StoredContent();
            content.setContentId(contentId);
            content.setContentHash(contentHash);
            content.setContent(bytes);
            content.setCanonicalHash(canonicalHash);
            return content;
        };
    }

    protected ArtifactMetaDataDto createOrUpdateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, ContentHandle contentHandle, boolean create, long globalId) {
        return createOrUpdateArtifact(groupId, artifactId, version, artifactType, contentHandle, create, globalId, System.currentTimeMillis());
    }

    protected ArtifactMetaDataDto createOrUpdateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType,
            ContentHandle content, boolean create, long globalId, long creationTime)
            throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {

        if (artifactId == null) {
            if (!create) {
                throw new ArtifactNotFoundException(groupId, "Null artifactId!");
            }
            artifactId = UUID.randomUUID().toString();
        }

        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        Map<String, Map<String, String>> v2c = storage.compute(akey);

        if (create && v2c.size() > 0) {
            throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }

        if (!create && v2c.size() == 0) {
            storage.remove(akey); // remove, as we just "computed" empty map
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        Map<String, String> prevVersionContentMap;
        int versionId;
        if (create) {
            prevVersionContentMap = new HashMap<String, String>();
            versionId = ARTIFACT_FIRST_VERSION;
        } else {
            prevVersionContentMap = getLatestContentMap(groupId, artifactId, null);
            versionId = versionId(prevVersionContentMap) + 1;
        }
        // Note: if no version is specified, the version will be equal to the versionId we just calculated.
        if (version == null) {
            version = VersionUtil.toString(versionId);
        }

        StoredContent storedContent = ensureStoredContent(artifactType, content);

        Map<String, String> contents = new ConcurrentHashMap<>();
        contents.put(MetaDataKeys.CONTENT_HASH, storedContent.getContentHash());
        contents.put(MetaDataKeys.VERSION_ID, VersionUtil.toString(versionId));
        contents.put(MetaDataKeys.VERSION, version);
        contents.put(MetaDataKeys.GLOBAL_ID, String.valueOf(globalId));
        contents.put(MetaDataKeys.ARTIFACT_ID, artifactId);
        if (groupId != null) {
            contents.put(MetaDataKeys.GROUP_ID, groupId);
        }

        String creationTimeValue = String.valueOf(creationTime);
        contents.put(MetaDataKeys.CREATED_ON, creationTimeValue);
        contents.put(MetaDataKeys.MODIFIED_ON, creationTimeValue);

        contents.put(MetaDataKeys.CREATED_BY, securityIdentity.getPrincipal().getName());

        contents.put(MetaDataKeys.TYPE, artifactType.value());
        ArtifactStateExt.applyState(contents, ArtifactState.ENABLED);

        // Carry over some meta-data from the previous version on an update.
        if (!create) {
            if (prevVersionContentMap != null) {
                if (prevVersionContentMap.containsKey(MetaDataKeys.NAME)) {
                    contents.put(MetaDataKeys.NAME, prevVersionContentMap.get(MetaDataKeys.NAME));
                }
                if (prevVersionContentMap.containsKey(MetaDataKeys.DESCRIPTION)) {
                    contents.put(MetaDataKeys.DESCRIPTION, prevVersionContentMap.get(MetaDataKeys.DESCRIPTION));
                }
                if (prevVersionContentMap.containsKey(MetaDataKeys.LABELS)) {
                    contents.put(MetaDataKeys.LABELS, prevVersionContentMap.get(MetaDataKeys.LABELS));
                }
                if (prevVersionContentMap.containsKey(MetaDataKeys.PROPERTIES)) {
                    contents.put(MetaDataKeys.PROPERTIES, prevVersionContentMap.get(MetaDataKeys.PROPERTIES));
                }
            }
        }

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        ExtractedMetaData emd = extractor.extract(content);
        if (extractor.isExtracted(emd)) {
            if (!isEmpty(emd.getName())) {
                contents.put(MetaDataKeys.NAME, emd.getName());
            }
            if (!isEmpty(emd.getDescription())) {
                contents.put(MetaDataKeys.DESCRIPTION, emd.getDescription());
            }
        }

        // Store in v2c -- make sure version is unique!!
        storage.createVersion(akey, version, contents);

        // Also store in global
        global.put(globalId, new TupleId(groupId, artifactId, version, versionId));

        final ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(contents);
        artifactMetaDataDto.setContentId(storedContent.getContentId());

        if (!create) {
            Map<String, String> firstContentMap = getFirstContentMap(groupId, artifactId);
            ArtifactVersionMetaDataDto firstDto = MetaDataKeys.toArtifactVersionMetaData(firstContentMap);
            artifactMetaDataDto.setCreatedOn(firstDto.getCreatedOn());
        }

        return artifactMetaDataDto;
    }

    protected Map<String, String> getContentMap(long globalId) {
        TupleId mapping = global.get(globalId);
        if (mapping == null) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }
        Map<String, String> content = getContentMap(mapping.getGroupId(), mapping.getId(), mapping.getVersion(), ArtifactStateExt.ACTIVE_STATES);
        if (content == null) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }
        ArtifactStateExt.logIfDeprecated(mapping.getGroupId(), mapping.getId(), content.get(MetaDataKeys.VERSION), ArtifactStateExt.getState(content));
        return content; // already unmodifiable
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) {
        updateArtifactState(groupId, artifactId, null, state);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = null;
        if (version == null) {
            content = getLatestContentMap(groupId, artifactId, null);
            version = content.get(MetaDataKeys.VERSION);
        }
        if (content == null) {
            content = getContentMap(groupId, artifactId, version, null);
        }

        final String fVersion = version;
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        ArtifactStateExt.applyState(s -> storage.put(akey, fVersion, MetaDataKeys.STATE, s.name()), content, state);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(groupId, artifactId, version, artifactType, content, true, nextGlobalId());
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactNotFoundException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metadata)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(groupId, artifactId, version, artifactType, content, true, nextGlobalId());
            updateArtifactMetaData(groupId, artifactId, metadata);
            DtoUtil.setEditableMetaDataInArtifact(amdd, metadata);
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactNotFoundException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        Map<String, Map<String, String>> v2c = storage.remove(akey);
        if (v2c == null) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        v2c.values().forEach(m -> {
            long globalId = Long.parseLong(m.get(MetaDataKeys.GLOBAL_ID));
            global.remove(globalId);
        });
        this.deleteArtifactRulesInternal(groupId, artifactId);

        return v2c.entrySet().stream()
                .sorted((e1, e2) -> versionId(e1.getValue()) - versionId(e2.getValue()))
                .map(e -> e.getValue().get(MetaDataKeys.VERSION)).collect(Collectors.toList());
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        storage.keySet().stream().filter(key -> groupId.equals(key.getGroupId())).forEach(key -> {
            this.deleteArtifact(key.getGroupId(), key.getArtifactId());
        });
    }

    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return toStoredArtifact(getLatestContentMap(groupId, artifactId, ArtifactStateExt.ACTIVE_STATES));
    }

    @Override
    public ContentHandle getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        String contentHash = this.contentHash.get(contentId);
        if (contentHash == null) {
            throw new ContentNotFoundException(String.valueOf(contentId));
        }
        return getArtifactByContentHash(contentHash);
    }

    @Override
    public ContentHandle getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        StoredContent storedContent = this.content.get(contentHash);
        if (storedContent == null) {
            throw new ContentNotFoundException(contentHash);
        }
        return ContentHandle.create(storedContent.getContent());
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(groupId, artifactId, version, artifactType, content, false, nextGlobalId());
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metadata)
            throws ArtifactNotFoundException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(groupId, artifactId, version, artifactType, content, false, nextGlobalId());
            updateArtifactMetaData(groupId, artifactId, metadata);
            DtoUtil.setEditableMetaDataInArtifact(amdd, metadata);
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        if (limit != null) {
            return storage.keySet()
                    .stream()
                    .map(key -> key.getArtifactId())
                    .limit(limit)
                    .collect(Collectors.toSet());
        } else {
            return storage.keySet()
                    .stream()
                    .map(key -> key.getArtifactId())
                    .collect(Collectors.toSet());
        }
    }

    private Set<ArtifactKey> getArtifactKeys() {
        return storage.keySet();
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, int offset, int limit) {
        final LongAdder itemsCount = new LongAdder();
        final List<SearchedArtifactDto> matchedArtifacts = getArtifactKeys()
                .stream()
                .map(this::getOptionalArtifactMetadata)
                .filter(Optional::isPresent)
                .filter(amd -> artifactMatchesFilters(amd.get(), filters))
                .map(Optional::get)
                .peek(artifactId -> itemsCount.increment())
                .sorted(SearchUtil.comparator(orderBy, orderDirection))
                .skip(offset)
                .limit(limit)
                .map(SearchUtil::buildSearchedArtifact)
                .collect(Collectors.toList());

        final ArtifactSearchResultsDto artifactSearchResults = new ArtifactSearchResultsDto();
        artifactSearchResults.setArtifacts(matchedArtifacts);
        artifactSearchResults.setCount(itemsCount.intValue());

        return artifactSearchResults;
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        final Map<String, String> content = getLatestContentMap(groupId, artifactId, ArtifactStateExt.ACTIVE_STATES);

        final ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(content);
        if (artifactMetaDataDto.getVersionId() != ARTIFACT_FIRST_VERSION) {
            Map<String, String> firstContentMap = getFirstContentMap(groupId, artifactId);
            ArtifactVersionMetaDataDto firstVersionContent = MetaDataKeys.toArtifactVersionMetaData(firstContentMap);
            artifactMetaDataDto.setCreatedOn(firstVersionContent.getCreatedOn());
        }

        final Map<String, String> lastContent = getLatestContentMap(groupId, artifactId, null);
        final ArtifactMetaDataDto lastDto = MetaDataKeys.toArtifactMetaData(lastContent);
        if (artifactMetaDataDto.getVersionId() != lastDto.getVersionId()) {
            artifactMetaDataDto.setModifiedOn(lastDto.getCreatedOn());
        }

        String contentHash = content.get(MetaDataKeys.CONTENT_HASH);
        long contentId = this.content.get(contentHash).getContentId();
        artifactMetaDataDto.setContentId(contentId);

        return artifactMetaDataDto;
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto metaData = getArtifactMetaData(groupId, artifactId);

        String contentHash = sha256Hash(content);
        if (canonical) {
            contentHash = sha256Hash(canonicalizeContent(metaData.getType(), content));
        }

        Map<String, Map<String, String>> map = getVersion2ContentMap(groupId, artifactId);
        for (Map<String, String> cMap : map.values()) {
            String candidateHash = cMap.get(MetaDataKeys.CONTENT_HASH);
            if (canonical) {
                candidateHash = this.content.get(candidateHash).getCanonicalHash();
            }

            if (StringUtils.equals(contentHash, candidateHash)) {
                ArtifactStateExt.logIfDeprecated(groupId, artifactId, cMap.get(MetaDataKeys.VERSION), ArtifactStateExt.getState(cMap));
                ArtifactVersionMetaDataDto vmdDto = MetaDataKeys.toArtifactVersionMetaData(cMap);
                long contentId = this.content.get(cMap.get(MetaDataKeys.CONTENT_HASH)).getContentId();
                vmdDto.setContentId(contentId);
                return vmdDto;
            }
        }
        throw new ArtifactNotFoundException(groupId, artifactId);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(id);
        ArtifactMetaDataDto amdDto = MetaDataKeys.toArtifactMetaData(content);
        String contentHash = content.get(MetaDataKeys.CONTENT_HASH);
        long contentId = this.content.get(contentHash).getContentId();
        amdDto.setContentId(contentId);
        return amdDto;
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException, InvalidPropertiesException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        if (metaData.getName() != null) {
            storage.put(akey, MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            storage.put(akey, MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
        if (metaData.getLabels() != null && !metaData.getLabels().isEmpty()) {
            storage.put(akey, MetaDataKeys.LABELS, String.join(",", metaData.getLabels()));
        }
        if (metaData.getProperties() != null && !metaData.getProperties().isEmpty()) {
            try {
                storage.put(akey, MetaDataKeys.PROPERTIES, new ObjectMapper().writeValueAsString(metaData.getProperties()));
            } catch (JsonProcessingException e) {
                throw new InvalidPropertiesException(MetaDataKeys.PROPERTIES + " could not be processed for storage.", e);
            }
        }
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        // check if the artifact exists
        getVersion2ContentMap(groupId, artifactId);
        // get the rules
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        Set<String> arules = artifactRules.keys(akey);
        return arules.stream().map(RuleType::fromValue).collect(Collectors.toList());
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(groupId, artifactId);
        // create a rule for the artifact
        String cdata = config.getConfiguration();
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        String prevValue = artifactRules.putIfAbsent(akey, rule.name(), cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(rule);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(groupId, artifactId);
        this.deleteArtifactRulesInternal(groupId, artifactId);
    }

    /**
     * Internal delete of artifact rules without checking for existence of artifact first.
     * @param groupId
     * @param artifactId
     * @throws RegistryStorageException
     */
    protected void deleteArtifactRulesInternal(String groupId, String artifactId) throws RegistryStorageException {
        // delete rules
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        artifactRules.remove(akey);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(groupId, artifactId);
        // get artifact rule
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        String config = artifactRules.get(akey, rule.name());
        if (config == null) {
            throw new RuleNotFoundException(rule);
        }
        return new RuleConfigurationDto(config);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(groupId, artifactId);
        // update a rule for the artifact
        String cdata = config.getConfiguration();
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        String prevValue = artifactRules.putIfPresent(akey, rule.name(), cdata == null ? "" : cdata);
        if (prevValue == null) {
            throw new RuleNotFoundException(rule);
        }
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // check if artifact exists
        getVersion2ContentMap(groupId, artifactId);
        // delete a rule for the artifact
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        String prevValue = artifactRules.remove(akey, rule.name());
        if (prevValue == null) {
            throw new RuleNotFoundException(rule);
        }
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, Map<String, String>> v2c = getVersion2ContentMap(groupId, artifactId);
        return v2c.entrySet().stream()
                .sorted((e1, e2) -> versionId(e1.getValue()) - versionId(e2.getValue()))
                .map(e -> e.getValue().get(MetaDataKeys.VERSION)).collect(Collectors.toList());
    }

    @Override
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) throws ArtifactNotFoundException, RegistryStorageException {
        final VersionSearchResultsDto versionSearchResults = new VersionSearchResultsDto();
        final Map<String, Map<String, String>> v2c = getVersion2ContentMap(groupId, artifactId);
        final LongAdder itemsCount = new LongAdder();
        final List<SearchedVersionDto> artifactVersions = v2c.entrySet().stream()
                .peek(entry -> itemsCount.increment())
                .sorted((e1, e2) -> versionId(e1.getValue()) - versionId(e2.getValue()))
                .skip(offset)
                .limit(limit)
                .map(entry -> {
                    Map<String, String> versionContentMap = entry.getValue();
                    ArtifactVersionMetaDataDto vmdDto = MetaDataKeys.toArtifactVersionMetaData(versionContentMap);
                    String contentHash = versionContentMap.get(MetaDataKeys.CONTENT_HASH);
                    long contentId = this.content.get(contentHash).getContentId();
                    vmdDto.setContentId(contentId);
                    return vmdDto;
                })
                .map(SearchUtil::buildSearchedVersion)
                .collect(Collectors.toList());

        versionSearchResults.setVersions(artifactVersions);
        versionSearchResults.setCount(itemsCount.intValue());

        return versionSearchResults;
    }

    @Override
    public StoredArtifactDto getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(id);
        return toStoredArtifact(content);
    }

    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(groupId, artifactId, version, ArtifactStateExt.ACTIVE_STATES);
        return toStoredArtifact(content);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        deleteArtifactVersionInternal(groupId, artifactId, version);
    }

    // internal - so we don't call sub-classes method
    private void deleteArtifactVersionInternal(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        Long globalId = storage.remove(akey, version);
        if (globalId == null) {
            throw new VersionNotFoundException(groupId, artifactId, version);
        }
        // remove from global as well
        global.remove(globalId);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        Map<String, String> content = getContentMap(groupId, artifactId, version, null);
        ArtifactVersionMetaDataDto vmdDto = MetaDataKeys.toArtifactVersionMetaData(content);
        String contentHash = content.get(MetaDataKeys.CONTENT_HASH);
        long contentId = this.content.get(contentHash).getContentId();
        vmdDto.setContentId(contentId);
        return vmdDto;
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        if (metaData.getName() != null) {
            storage.put(akey, version, MetaDataKeys.NAME, metaData.getName());
        }
        if (metaData.getDescription() != null) {
            storage.put(akey, version, MetaDataKeys.DESCRIPTION, metaData.getDescription());
        }
        if (metaData.getLabels() != null && !metaData.getLabels().isEmpty()) {
            storage.put(akey, version, MetaDataKeys.LABELS, String.join(",", metaData.getLabels()));
        }
        if (metaData.getProperties() != null && !metaData.getProperties().isEmpty()) {
            try {
                storage.put(akey, version, MetaDataKeys.PROPERTIES, new ObjectMapper().writeValueAsString(metaData.getProperties()));
            } catch (JsonProcessingException e) {
                throw new InvalidPropertiesException(MetaDataKeys.PROPERTIES + " could not be processed for storage.", e);
            }
        }
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        ArtifactKey akey = new ArtifactKey(groupId, artifactId);
        storage.remove(akey, version, MetaDataKeys.NAME);
        storage.remove(akey, version, MetaDataKeys.DESCRIPTION);
        storage.remove(akey, version, MetaDataKeys.LABELS);
        storage.remove(akey, version, MetaDataKeys.PROPERTIES);
        storage.remove(akey, version, MetaDataKeys.CREATED_BY);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return globalRules.keySet().stream().map(RuleType::fromValue).collect(Collectors.toList());
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        String cdata = config.getConfiguration();
        String prevValue = globalRules.putIfAbsent(rule.name(), cdata == null ? "" : cdata);
        if (prevValue != null) {
            throw new RuleAlreadyExistsException(rule);
        }
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        globalRules.clear();
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        String cdata = globalRules.get(rule.name());
        if (cdata == null) {
            throw new RuleNotFoundException(rule);
        }
        return new RuleConfigurationDto(cdata);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        String rname = rule.name();
        if (!globalRules.containsKey(rname)) {
            throw new RuleNotFoundException(rule);
        }
        String cdata = config.getConfiguration();
        globalRules.put(rname, cdata == null ? "" : cdata);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        String prevValue = globalRules.remove(rule.name());
        if (prevValue == null) {
            throw new RuleNotFoundException(rule);
        }
    }

    @Override
    public TenantMetadataDto getTenantMetadata(String tenantId) throws RegistryStorageException {
        throw new UnsupportedOperationException("Multitenancy not supported");
    }

    @Override
    public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        String level = logConfigurations.get(logger);
        if (level == null) {
            return null;
        }
        return new LogConfigurationDto(logger, LogLevel.fromValue(level));
    }

    @Override
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        logConfigurations.put(logConfiguration.getLogger(), logConfiguration.getLogLevel().value());
    }

    @Override
    public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        logConfigurations.remove(logger);
    }

    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        return logConfigurations.entrySet()
                .stream()
                .map(e -> new LogConfigurationDto(e.getKey(), LogLevel.fromValue(e.getValue())))
                .collect(Collectors.toList());
    }

    @Override
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        GroupMetaDataDto prev = groups.putIfAbsent(group.getGroupId(), group);
        if (prev != null) {
            throw new GroupAlreadyExistsException(group.getGroupId());
        }
    }

    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        if (!groups.containsKey(group.getGroupId())) {
            throw new GroupNotFoundException(group.getGroupId());
        }
        groups.put(group.getGroupId(), group);
    }

    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        GroupMetaDataDto prev = groups.remove(groupId);
        if (prev == null) {
            throw new GroupNotFoundException(groupId);
        }
        deleteArtifacts(groupId);
    }

    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        if (limit != null) {
            return groups.keySet()
                    .stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            return groups.keySet()
                    .stream()
                    .collect(Collectors.toList());
        }
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        GroupMetaDataDto group = groups.get(groupId);
        if (group == null) {
            throw new GroupNotFoundException(groupId);
        }
        return group;
    }

    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return storage.keySet()
                .stream()
                .map(this::getOptionalArtifactMetadata)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(m -> contentId == m.getContentId())
                .collect(Collectors.toList());
    }
}
