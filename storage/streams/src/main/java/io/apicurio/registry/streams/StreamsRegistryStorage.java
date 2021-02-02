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
package io.apicurio.registry.streams;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.v1.beans.SearchOver;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.MetaDataKeys;
import io.apicurio.registry.storage.impl.SearchUtil;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.Submitter;
import io.apicurio.registry.utils.streams.diservice.AsyncBiFunctionService;
import io.apicurio.registry.utils.streams.distore.ExtReadOnlyKeyValueStore;
import io.apicurio.registry.utils.streams.distore.FilterPredicate;
import io.quarkus.security.identity.SecurityIdentity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author Ales Justin
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
public class StreamsRegistryStorage extends AbstractRegistryStorage {

    /* Fake global rules as an artifact */
    public static final String GLOBAL_RULES_ID = "__GLOBAL_RULES__";

    /* Fake groupId for legacy artifacts*/
    public static final String LEGACY_GROUP_ID = "";

    private static final int ARTIFACT_FIRST_VERSION = 1;

    @Inject
    KafkaStreams streams;

    @Inject
    StreamsProperties properties;

    @Inject
    ProducerActions<Str.ArtifactKey, Str.StorageValue> storageProducer;

    @Inject
    ExtReadOnlyKeyValueStore<Str.ArtifactKey, Str.Data> storageStore;

    @Inject
    ReadOnlyKeyValueStore<Long, Str.TupleValue> globalIdStore;

    @Inject
    @Current
    AsyncBiFunctionService<Str.ArtifactKey, Long, Str.Data> storageFunction;

    @Inject
    @Current
    AsyncBiFunctionService<Void, Void, KafkaStreams.State> stateFunction;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SecurityIdentity securityIdentity;

    private final Submitter<RecordMetadata> submitter = new Submitter<>(this::send);

    private CompletableFuture<RecordMetadata> send(Str.StorageValue value) {
        ProducerRecord<Str.ArtifactKey, Str.StorageValue> record = new ProducerRecord<>(
            properties.getStorageTopic(),
            value.getKey(), // MUST be set
            value
        );
        return storageProducer.apply(record);
    }

    private static StoredArtifactDto addContent(Str.ArtifactValue value) {
        Map<String, String> contents = new HashMap<>(value.getMetadataMap());
        MetaDataKeys.putContent(contents, value.getContent().toByteArray());
        return AbstractMapRegistryStorage.toStoredArtifact(contents);
    }

    private static boolean isValid(Str.ArtifactValue value) {
        return !value.equals(Str.ArtifactValue.getDefaultInstance());
    }

    private static boolean isGlobalRules(String artifactId) {
        return GLOBAL_RULES_ID.equals(artifactId);
    }

    private Str.ArtifactValue getLastArtifact(String groupId, String artifactId) {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);
        return getLastArtifact(key, data);
    }

    private Str.ArtifactValue getLastArtifact(Str.ArtifactKey key, Str.Data data) {
        if (data != null) {
            int count = data.getArtifactsCount();
            if (count > 0) {
                List<Str.ArtifactValue> list = data.getArtifactsList();
                int index = count - 1;
                while (index >= 0) {
                    Str.ArtifactValue value = list.get(index);
                    if (isValid(value)) {
                        ArtifactState state = ArtifactStateExt.getState(value.getMetadataMap());
                        if (ArtifactStateExt.ACTIVE_STATES.contains(state)) {
                            ArtifactStateExt.logIfDeprecated(key.getGroupId(), key.getArtifactId(), index + 1, state);
                            return value;
                        }
                    }
                    index--;
                }
            }
        }
        throw new ArtifactNotFoundException(key.getGroupId(), key.getArtifactId());
    }

    static FilterPredicate<Str.ArtifactKey, Str.Data> createFilterPredicate() {
        return (filter, over, artifactId, data) -> (findMetadata(filter, over, data) != null);
    }

    private static Map<String, String> findMetadata(String filter, String over, Str.Data data) {
        int count = data.getArtifactsCount();
        if (count > 0) {
            List<Str.ArtifactValue> list = data.getArtifactsList();
            int index = count - 1;
            while (index >= 0) {
                Str.ArtifactValue value = list.get(index);
                if (isValid(value)) {
                    Map<String, String> metadata = value.getMetadataMap();
                    ArtifactState state = ArtifactStateExt.getState(metadata);
                    if (ArtifactStateExt.ACTIVE_STATES.contains(state)) {
                        String artifactId = metadata.get(MetaDataKeys.ARTIFACT_ID);
                        String name = metadata.get(MetaDataKeys.NAME);
                        String desc = metadata.get(MetaDataKeys.DESCRIPTION);
                        String labels = metadata.get(MetaDataKeys.LABELS);
                        SearchOver so = SearchOver.fromValue(over);
                        switch (so) {
                            case name:
                                if (stringMetadataContainsFilter(filter, name) || stringMetadataContainsFilter(filter, artifactId)) {
                                    return metadata;
                                }
                            case description:
                                if (stringMetadataContainsFilter(filter, desc)) {
                                    return metadata;
                                }
                            case labels:
                                if (stringMetadataContainsFilter(filter, labels)) {
                                    return metadata;
                                }
                            default:
                                if (metaDataContainsFilter(filter, metadata.values())) {
                                    return metadata;
                                }
                        }
                    }
                }
                index--;
            }
        }
        return null;
    }

    private static boolean stringMetadataContainsFilter(String filter, String name) {
        return null == filter || (name != null && StringUtils.containsIgnoreCase(name, filter));
    }

    private static boolean metaDataContainsFilter(String filter, Collection<String> metadataValues) {
        return null == filter || metadataValues.stream().anyMatch(value -> stringMetadataContainsFilter(filter, value));
    }

    private <T> T handleVersion(Str.ArtifactKey key, long version, EnumSet<ArtifactState> states, Function<Str.ArtifactValue, T> handler) throws ArtifactNotFoundException, RegistryStorageException {

        Str.Data data = storageStore.get(key);

        if (data != null) {
            int index = (int) (version - 1);
            List<Str.ArtifactValue> list = data.getArtifactsList();
            if (index < list.size()) {
                Str.ArtifactValue value = list.get(index);
                if (isValid(value)) {
                    ArtifactState state = ArtifactStateExt.getState(value.getMetadataMap());
                    ArtifactStateExt.validateState(states, state, key.getGroupId(), key.getArtifactId(), version);
                    return handler.apply(value);
                }
            }
            throw new VersionNotFoundException(key.getGroupId(), key.getArtifactId(), version);
        } else {
            throw new ArtifactNotFoundException(key.getGroupId(), key.getArtifactId());
        }
    }

    private void updateArtifactState(Str.Data data, Integer version, ArtifactState state) {
        Str.ArtifactKey key = data.getKey();
        ArtifactState current = handleVersion(
            key,
            version,
            null,
            av -> ArtifactStateExt.getState(av.getMetadataMap())
        );

        ArtifactStateExt.applyState(
            s -> ConcurrentUtil.get(
                submitter.submitState(data.getKey(),
                                      version.longValue(),
                                      state)
            ),
            current,
            state
        );
    }

    private boolean exists(Str.ArtifactKey key) {
        Str.Data data = storageStore.get(key);
        if (data != null) {
            for (int i = 0; i < data.getArtifactsCount(); i++) {
                Str.ArtifactValue artifact = data.getArtifacts(i);
                if (isValid(artifact)) {
                    return true; // we found a valid one
                }
            }
        }
        return false;
    }

    @Override
    public boolean isReady() {
        // first a quick local check
        if (streams.state() != KafkaStreams.State.RUNNING) {
            return false;
        }
        // then check all
        return stateFunction.apply()
            .map(ConcurrentUtil::result)
            .allMatch(s -> s == KafkaStreams.State.RUNNING);
    }

    @Override
    public boolean isAlive() {
        return (streams.state() != KafkaStreams.State.ERROR);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) {

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            updateArtifactState(data, data.getArtifactsCount(), state);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, Integer version, ArtifactState state) {

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            updateArtifactState(data, version, state);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            if (data.getArtifactsCount() > 0) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
        }

        CompletableFuture<RecordMetadata> submitCF = submitter.submitArtifact(Str.ActionType.CREATE, key, -1, artifactType, content.bytes(), securityIdentity.getPrincipal().getName());
        return submitCF.thenCompose(r -> storageFunction.apply(key, r.offset()).thenApply(d -> new RecordData(r, d)))
                       .thenApply(rd -> {
                           RecordMetadata rmd = rd.getRmd();
                           Str.Data d = rd.getData();
                           Str.ArtifactValue first = d.getArtifacts(0);
                           long globalId = properties.toGlobalId(rmd.offset(), rmd.partition());
                           if (first.getId() != globalId) {
                               // somebody beat us to it ...
                               throw new ArtifactAlreadyExistsException(groupId, artifactId);
                           }
                           return MetaDataKeys.toArtifactMetaData(first.getMetadataMap());
                       });
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        return createArtifact(groupId, artifactId, artifactType, content)
            .thenCompose(amdd -> submitter.submitMetadata(Str.ActionType.UPDATE, key, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties())
                .thenApply(v -> DtoUtil.setEditableMetaDataInArtifact(amdd, metaData)));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            if (data.getArtifactsCount() == 0) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }

            // Delete any rules configured for the artifact.
            this.deleteArtifactRulesInternal(key);

            ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, key, -1, null, null, null));

            SortedSet<Long> result = new TreeSet<>();
            List<Str.ArtifactValue> list = data.getArtifactsList();
            for (int i = 0; i < list.size(); i++) {
                if (isValid(list.get(i))) {
                    result.add((long) (i + 1));
                }
            }

            return result;
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {

        storageStore.allKeys()
                .filter(key -> groupId.equals(key.getGroupId()))
                .forEach(key -> deleteArtifact(key.getGroupId(), key.getArtifactId()));
    }

    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return addContent(getLastArtifact(groupId, artifactId));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            if (data.getArtifactsCount() == 0) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }
        }

        CompletableFuture<RecordMetadata> submitCF = submitter.submitArtifact(Str.ActionType.UPDATE, key, -1, artifactType, content.bytes(), securityIdentity.getPrincipal().getName());
        return submitCF.thenCompose(r -> storageFunction.apply(key, r.offset()).thenApply(d -> new RecordData(r, d)))
                       .thenApply(rd -> {
                           RecordMetadata rmd = rd.getRmd();
                           Str.Data d = rd.getData();
                           long globalId = properties.toGlobalId(rmd.offset(), rmd.partition());
                           for (int i = d.getArtifactsCount() - 1; i >= 0; i--) {
                               Str.ArtifactValue value = d.getArtifacts(i);
                               if (value.getId() == globalId) {
                                   ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(value.getMetadataMap());

                                   if (artifactMetaDataDto.getVersion() != ARTIFACT_FIRST_VERSION) {
                                       ArtifactVersionMetaDataDto firstVersionContent = getArtifactVersionMetaData(groupId, artifactId, ARTIFACT_FIRST_VERSION);
                                       artifactMetaDataDto.setCreatedOn(firstVersionContent.getCreatedOn());
                                   }
                                   return artifactMetaDataDto;
                               }
                           }
                           throw new ArtifactNotFoundException(groupId, artifactId);
                       });
    }


    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        return updateArtifact(groupId, artifactId, artifactType, content)
            .thenCompose(amdd -> submitter.submitMetadata(Str.ActionType.UPDATE, key, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties())
                .thenApply(v -> DtoUtil.setEditableMetaDataInArtifact(amdd, metaData)));
    }


    @Override
    public Set<String> getArtifactIds(Integer limit) {
        Set<String> ids = new ConcurrentSkipListSet<>();
        try (Stream<Str.ArtifactKey> stream = storageStore.allKeys()) {
            // exists can be costly ...
            if (limit != null) {
                stream.filter(this::exists)
                        .limit(limit)
                        .forEach(key -> ids.add(key.getArtifactId()));
            } else {
                stream.filter(this::exists).forEach(key -> ids.add(key.getArtifactId()));
            }
        }
        ids.remove(GLOBAL_RULES_ID);
        return ids;
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection,
                                                    int offset, int limit) {

        /*
        LongAdder itemsCount = new LongAdder();
        List<SearchedArtifact> matchedArtifacts = storageStore.filter(filters, orderBy, orderDirection)
            .peek((kv) -> itemsCount.increment())
            .map(kv -> getArtifactMetaDataOrNull(kv.key))
            .filter(Objects::nonNull)
            .sorted((art1, art2) -> SearchUtil.compare(orderDirection, art1, art2))
            .skip(offset)
            .limit(limit)
            .map(SearchUtil::buildSearchedArtifact)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        final ArtifactSearchResults artifactSearchResults = new ArtifactSearchResults();
        artifactSearchResults.setArtifacts(matchedArtifacts);
        artifactSearchResults.setCount(itemsCount.intValue());

        return artifactSearchResults;
        */

        //FIXME implement!!!!

         return new ArtifactSearchResultsDto();
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        final Map<String, String> content = getLastArtifact(groupId, artifactId).getMetadataMap();

        final ArtifactMetaDataDto artifactMetaDataDto = MetaDataKeys.toArtifactMetaData(content);
        if (artifactMetaDataDto.getVersion() != ARTIFACT_FIRST_VERSION) {
            ArtifactVersionMetaDataDto firstVersionContent = getArtifactVersionMetaData(groupId, artifactId, ARTIFACT_FIRST_VERSION);
            artifactMetaDataDto.setCreatedOn(firstVersionContent.getCreatedOn());
        }

        final SortedSet<Long> versions = getArtifactVersions(groupId, artifactId);
        if (artifactMetaDataDto.getVersion() != versions.last()) {
            final ArtifactVersionMetaDataDto artifactVersionMetaDataDto = getArtifactVersionMetaData(groupId, artifactId, versions.last());
            artifactMetaDataDto.setModifiedOn(artifactVersionMetaDataDto.getCreatedOn());
        }

        return artifactMetaDataDto;
    }

    private ArtifactMetaDataDto getArtifactMetaDataOrNull(String groupId, String artifactId) {
        try {
            return getArtifactMetaData(groupId, artifactId);
        } catch (ArtifactNotFoundException ex) {
            return null;
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        // Get the meta-data for the artifact
        ArtifactMetaDataDto metaData = getArtifactMetaData(groupId, artifactId);

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            // Create a canonicalizer for the artifact based on its type, and then
            // canonicalize the inbound content
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(metaData.getType());
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            
            byte[] contentToCompare;
            if (canonical) {
                ContentHandle canonicalContent = canonicalizer.canonicalize(content);
                contentToCompare = canonicalContent.bytes();
            } else {
                contentToCompare = content.bytes();
            }

            for (int i = data.getArtifactsCount() - 1; i >= 0; i--) {
                Str.ArtifactValue candidateArtifact = data.getArtifacts(i);
                if (isValid(candidateArtifact)) {
                    ContentHandle candidateContent = ContentHandle.create(candidateArtifact.getContent().toByteArray());
                    byte[] candidateBytes;
                    if (canonical) {
                        ContentHandle canonicalCandidateContent = canonicalizer.canonicalize(candidateContent);
                        candidateBytes = canonicalCandidateContent.bytes();
                    } else {
                        candidateBytes = candidateContent.bytes();
                    }
                    if (Arrays.equals(contentToCompare, candidateBytes)) {
                        return MetaDataKeys.toArtifactVersionMetaData(candidateArtifact.getMetadataMap());
                    }
                }
            }
        }
        throw new ArtifactNotFoundException(groupId, artifactId);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Str.TupleValue tuple = globalIdStore.get(id);
        if (tuple == null) {
            //FIXME throw proper exception
            throw new IllegalStateException();
        }
        return handleVersion(tuple.getKey(), tuple.getVersion(), null, value -> MetaDataKeys.toArtifactMetaData(value.getMetadataMap()));
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.UPDATE, key, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties()));
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            return data.getRulesList().stream().map(v -> RuleType.fromValue(v.getType().name())).collect(Collectors.toList());
        }
        if (isGlobalRules(artifactId)) {
            return Collections.emptyList();
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            Optional<Str.RuleValue> found = data.getRulesList()
                    .stream()
                    .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                    .findFirst();
            if (found.isPresent()) {
                throw new RuleAlreadyExistsException(rule);
            }
            return submitter.submitRule(Str.ActionType.CREATE, key, rule, config.getConfiguration()).thenApply(o -> null);
        } else if (isGlobalRules(artifactId)) {
            return submitter.submitRule(Str.ActionType.CREATE, key, rule, config.getConfiguration()).thenApply(o -> null);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            deleteArtifactRulesInternal(key);
        } else if (!isGlobalRules(artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    public void deleteArtifactRulesInternal(Str.ArtifactKey key) {
        ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, key, null, null));
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            return data.getRulesList()
                .stream()
                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                .findFirst()
                .map(r -> new RuleConfigurationDto(r.getConfiguration()))
                .orElseThrow(() -> new RuleNotFoundException(rule));
        } else if (isGlobalRules(artifactId)) {
            throw new RuleNotFoundException(rule);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            data.getRulesList()
                .stream()
                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                .findFirst()
                .orElseThrow(() -> new RuleNotFoundException(rule));
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.UPDATE, key, rule, config.getConfiguration()));
        } else if (isGlobalRules(artifactId)) {
            throw new RuleNotFoundException(rule);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        Str.Data data = storageStore.get(key);

        if (data != null) {
            data.getRulesList()
                .stream()
                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                .findFirst()
                .orElseThrow(() -> new RuleNotFoundException(rule));
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, key, rule, null));
        } else if (isGlobalRules(artifactId)) {
            throw new RuleNotFoundException(rule);
        } else {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
    }

    @Override
    public SortedSet<Long> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        Str.Data data = storageStore.get(buildKey(groupId, artifactId));

        if (data != null) {
            SortedSet<Long> result = new TreeSet<>();
            List<Str.ArtifactValue> list = data.getArtifactsList();
            for (int i = 0; i < list.size(); i++) {
                if (isValid(list.get(i))) {
                    result.add((long) (i + 1));
                }
            }
            if (result.size() > 0) {
                return result;
            }
        }
        throw new ArtifactNotFoundException(groupId, artifactId);
    }

    @Override
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) {
        final VersionSearchResultsDto versionSearchResults = new VersionSearchResultsDto();
        final LongAdder itemsCount = new LongAdder();

        final List<SearchedVersionDto> versions = getArtifactVersions(groupId, artifactId).stream()
                .peek(version -> itemsCount.increment())
                .sorted(Long::compareTo)
                .skip(offset)
                .limit(limit)
                .map(version -> SearchUtil.buildSearchedVersion(getArtifactVersionMetaData(groupId, artifactId, version)))
                .collect(Collectors.toList());

        versionSearchResults.setVersions(versions);
        versionSearchResults.setCount(itemsCount.intValue());

        return versionSearchResults;
    }

    @Override
    public StoredArtifactDto getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Str.TupleValue value = globalIdStore.get(id);
        if (value == null) {
           //FIXME throw proper exception
            throw new IllegalStateException();
        }
        return getArtifactVersion(value.getKey().getGroupId(), value.getKey().getArtifactId(), value.getVersion());
    }

    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return handleVersion(buildKey(groupId, artifactId), version, ArtifactStateExt.ACTIVE_STATES, StreamsRegistryStorage::addContent);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        handleVersion(key, version, null, value -> ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, key, version, null, null, null)));
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return handleVersion(buildKey(groupId, artifactId), version, null, value -> MetaDataKeys.toArtifactVersionMetaData(value.getMetadataMap()));
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        handleVersion(
               key,
                version,
                ArtifactStateExt.ACTIVE_STATES,
                value -> ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.UPDATE, key, version, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties()))
        );
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

        final Str.ArtifactKey key = buildKey(groupId, artifactId);

        handleVersion(
                key,
                version,
                null,
                value -> ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.DELETE, key, version, null, null, Collections.emptyList(), Collections.emptyMap()))
        );
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return getArtifactRules(GLOBAL_RULES_ID, GLOBAL_RULES_ID);
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        createArtifactRule(GLOBAL_RULES_ID, GLOBAL_RULES_ID, rule, config);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        deleteArtifactRules(GLOBAL_RULES_ID, GLOBAL_RULES_ID);
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return getArtifactRule(GLOBAL_RULES_ID, GLOBAL_RULES_ID, rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        updateArtifactRule(GLOBAL_RULES_ID, GLOBAL_RULES_ID,  rule, config);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        deleteArtifactRule(GLOBAL_RULES_ID, GLOBAL_RULES_ID, rule);
    }

    @AllArgsConstructor
    @Getter
    private static class RecordData {
        private RecordMetadata rmd;
        private Str.Data data;
    }

    private static Str.ArtifactKey buildKey(String groupId, String artifactId) {

        if (null == groupId) {
            groupId = LEGACY_GROUP_ID;
        }

        return Str.ArtifactKey.newBuilder()
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .build();
    }

}
