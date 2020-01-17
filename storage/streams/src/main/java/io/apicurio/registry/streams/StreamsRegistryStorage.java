package io.apicurio.registry.streams;

import io.apicurio.registry.content.ContentCanonicalizer;
import io.apicurio.registry.content.ContentCanonicalizerFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
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
import io.apicurio.registry.storage.impl.AbstractMapRegistryStorage;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.streams.diservice.AsyncBiFunctionService;
import io.apicurio.registry.streams.distore.ExtReadOnlyKeyValueStore;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.Submitter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.utils.CloseableIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class StreamsRegistryStorage implements RegistryStorage {

    /* Fake global rules as an artifact */
    public static final String GLOBAL_RULES_ID = "__GLOBAL_RULES__";

    @Inject
    StreamsProperties properties;

    @Inject
    ProducerActions<String, Str.StorageValue> storageProducer;

    @Inject
    ExtReadOnlyKeyValueStore<String, Str.Data> storageStore;

    @Inject
    ReadOnlyKeyValueStore<Long, Str.TupleValue> globalIdStore;

    @Inject
    @Current
    AsyncBiFunctionService<String, Long, Str.Data> storageFunction;

    @Inject
    ContentCanonicalizerFactory ccFactory;

    private Submitter submitter = new Submitter(this::send);

    private CompletableFuture<RecordMetadata> send(Str.StorageValue value) {
        ProducerRecord<String, Str.StorageValue> record = new ProducerRecord<>(
            properties.getStorageTopic(),
            value.getArtifactId(), // MUST be set
            value
        );
        return storageProducer.apply(record);
    }

    private static StoredArtifact addContent(Str.ArtifactValue value) {
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

    private Str.ArtifactValue getLastArtifact(String artifactId, EnumSet<ArtifactState> states) {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            int count = data.getArtifactsCount();
            if (count > 0) {
                List<Str.ArtifactValue> list = data.getArtifactsList();
                int index = count - 1;
                while (index >= 0) {
                    Str.ArtifactValue value = list.get(index);
                    if (isValid(value)) {
                        ArtifactState state = ArtifactStateExt.getState(value.getMetadataMap());
                        if (states.contains(state)) {
                            ArtifactStateExt.logIfDeprecated(artifactId, state, index + 1);
                            return value;
                        }
                    }
                    index--;
                }
            }
        }
        throw new ArtifactNotFoundException(artifactId);
    }

    private <T> T handleVersion(String artifactId, long version, EnumSet<ArtifactState> states, Function<Str.ArtifactValue, T> handler) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            int index = (int) (version - 1);
            List<Str.ArtifactValue> list = data.getArtifactsList();
            if (index < list.size()) {
                Str.ArtifactValue value = list.get(index);
                if (isValid(value)) {
                    ArtifactState state = ArtifactStateExt.getState(value.getMetadataMap());
                    ArtifactStateExt.validateState(states, state, artifactId, version);
                    return handler.apply(value);
                }
            }
            throw new VersionNotFoundException(artifactId, version);
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    private void updateArtifactState(Str.Data data, Integer version, ArtifactState state) {
        String artifactId = data.getArtifactId();
        if (state == ArtifactState.DELETED) {
            deleteArtifactVersion(artifactId, version);
        } else {
            ArtifactState current = handleVersion(
                artifactId,
                version,
                ArtifactStateExt.ALL,
                av -> ArtifactStateExt.getState(av.getMetadataMap())
            );

            ArtifactStateExt.applyState(
                s -> ConcurrentUtil.get(
                    submitter.submitState(data.getArtifactId(),
                                          version.longValue(),
                                          state)
                ),
                current,
                state
            );
        }
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            updateArtifactState(data, data.getArtifactsCount(), state);
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            updateArtifactState(data, version, state);
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            if (data.getArtifactsCount() > 0) {
                throw new ArtifactAlreadyExistsException(artifactId);
            }
        }

        CompletableFuture<RecordMetadata> submitCF = submitter.submitArtifact(Str.ActionType.CREATE, artifactId, -1, artifactType, content.bytes());
        return submitCF.thenCompose(r -> storageFunction.apply(artifactId, r.offset()).thenApply(d -> new RecordData(r, d)))
                       .thenApply(rd -> {
                           RecordMetadata rmd = rd.getRmd();
                           Str.Data d = rd.getData();
                           Str.ArtifactValue first = d.getArtifacts(0);
                           long globalId = properties.toGlobalId(rmd.offset(), rmd.partition());
                           if (first.getId() != globalId) {
                               // somebody beat us to it ...
                               throw new ArtifactAlreadyExistsException(artifactId);
                           }
                           return MetaDataKeys.toArtifactMetaData(first.getMetadataMap());
                       });
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            if (data.getArtifactsCount() == 0) {
                throw new ArtifactNotFoundException(artifactId);
            }

            ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, -1, null, null));

            SortedSet<Long> result = new TreeSet<>();
            List<Str.ArtifactValue> list = data.getArtifactsList();
            for (int i = 0; i < list.size(); i++) {
                if (isValid(list.get(i))) {
                    result.add((long) (i + 1));
                }
            }
            return result;
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return addContent(getLastArtifact(artifactId, ArtifactStateExt.ACTIVE_STATES));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            if (data.getArtifactsCount() == 0) {
                throw new ArtifactNotFoundException(artifactId);
            }
        }

        CompletableFuture<RecordMetadata> submitCF = submitter.submitArtifact(Str.ActionType.UPDATE, artifactId, -1, artifactType, content.bytes());
        return submitCF.thenCompose(r -> storageFunction.apply(artifactId, r.offset()).thenApply(d -> new RecordData(r, d)))
                       .thenApply(rd -> {
                           RecordMetadata rmd = rd.getRmd();
                           Str.Data d = rd.getData();
                           long globalId = properties.toGlobalId(rmd.offset(), rmd.partition());
                           for (int i = d.getArtifactsCount() - 1; i >= 0; i--) {
                               Str.ArtifactValue value = d.getArtifacts(i);
                               if (value.getId() == globalId) {
                                   return MetaDataKeys.toArtifactMetaData(value.getMetadataMap());
                               }
                           }
                           throw new ArtifactNotFoundException(artifactId);
                       });
    }

    @Override
    public Set<String> getArtifactIds() {
        Set<String> ids = new TreeSet<>();
        try (CloseableIterator<String> iter = storageStore.allKeys()) {
            while (iter.hasNext()) {
                ids.add(iter.next());
            }
            ids.remove(GLOBAL_RULES_ID);
            return ids;
        }
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Map<String, String> content = getLastArtifact(artifactId, ArtifactStateExt.ALL).getMetadataMap();
        return MetaDataKeys.toArtifactMetaData(content);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        // Get the meta-data for the artifact
        ArtifactMetaDataDto metaData = getArtifactMetaData(artifactId);

        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            // Create a canonicalizer for the artifact based on its type, and then 
            // canonicalize the inbound content
            ContentCanonicalizer canonicalizer = ccFactory.create(metaData.getType());
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            byte[] canonicalBytes = canonicalContent.bytes();

            for (int i = data.getArtifactsCount() - 1; i >= 0; i--){
                Str.ArtifactValue candidateArtifact = data.getArtifacts(i);
                if (isValid(candidateArtifact)) {
                    ContentHandle candidateContent = ContentHandle.create(candidateArtifact.getContent().toByteArray());
                    ContentHandle canonicalCandidateContent = canonicalizer.canonicalize(candidateContent);
                    byte[] candidateBytes = canonicalCandidateContent.bytes();
                    if (Arrays.equals(canonicalBytes, candidateBytes)) {
                        return MetaDataKeys.toArtifactMetaData(candidateArtifact.getMetadataMap());
                    }
                }
            }
        }
        throw new ArtifactNotFoundException(artifactId);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Str.TupleValue tuple = globalIdStore.get(id);
        if (tuple == null) {
            throw new ArtifactNotFoundException("GlobalId: " + id);
        }
        return handleVersion(tuple.getArtifactId(), tuple.getVersion(), ArtifactStateExt.ALL, value -> MetaDataKeys.toArtifactMetaData(value.getMetadataMap()));
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription()));
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            return data.getRulesList().stream().map(v -> RuleType.fromValue(v.getType().name())).collect(Collectors.toList());
        }
        if (isGlobalRules(artifactId)) {
            return Collections.emptyList();
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            Optional<Str.RuleValue> found = data.getRulesList()
                                                .stream()
                                                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                                                .findFirst();
            if (found.isPresent()) {
                throw new RuleAlreadyExistsException(rule);
            }
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.CREATE, artifactId, rule, config.getConfiguration()));
        } else if (isGlobalRules(artifactId)) {
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.CREATE, artifactId, rule, config.getConfiguration()));
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, artifactId, null, null));
        } else if (!isGlobalRules(artifactId)) {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
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
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            data.getRulesList()
                .stream()
                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                .findFirst()
                .orElseThrow(() -> new RuleNotFoundException(rule));
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.UPDATE, artifactId, rule, config.getConfiguration()));
        } else if (isGlobalRules(artifactId)) {
            throw new RuleNotFoundException(rule);
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            data.getRulesList()
                .stream()
                .filter(v -> RuleType.fromValue(v.getType().name()) == rule)
                .findFirst()
                .orElseThrow(() -> new RuleNotFoundException(rule));
            ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, artifactId, rule, null));
        } else if (isGlobalRules(artifactId)) {
            throw new RuleNotFoundException(rule);
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        Str.Data data = storageStore.get(artifactId);
        if (data != null) {
            SortedSet<Long> result = new TreeSet<>();
            List<Str.ArtifactValue> list = data.getArtifactsList();
            for (int i = 0; i < list.size(); i++) {
                if (isValid(list.get(i))) {
                    result.add((long) (i + 1));
                }
            }
            return result;
        } else {
            throw new ArtifactNotFoundException(artifactId);
        }
    }

    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        Str.TupleValue value = globalIdStore.get(id);
        if (value == null) {
            throw new ArtifactNotFoundException("GlobalId: " + id);
        }
        return getArtifactVersion(value.getArtifactId(), value.getVersion());
    }

    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return handleVersion(artifactId, version, ArtifactStateExt.ACTIVE_STATES, StreamsRegistryStorage::addContent);
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(artifactId, version, ArtifactStateExt.ALL, value -> ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, version, null, null)));
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return handleVersion(artifactId, version, ArtifactStateExt.ALL, value -> MetaDataKeys.toArtifactVersionMetaData(value.getMetadataMap()));
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(
            artifactId,
            version,
            ArtifactStateExt.ACTIVE_STATES,
            value -> ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, version, metaData.getName(), metaData.getDescription()))
        );
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(
            artifactId,
            version,
            ArtifactStateExt.ALL,
            value -> ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.DELETE, artifactId, version, null, null))
        );
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return getArtifactRules(GLOBAL_RULES_ID);
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        createArtifactRule(GLOBAL_RULES_ID, rule, config);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        deleteArtifactRules(GLOBAL_RULES_ID);
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return getArtifactRule(GLOBAL_RULES_ID, rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        updateArtifactRule(GLOBAL_RULES_ID, rule, config);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        deleteArtifactRule(GLOBAL_RULES_ID, rule);
    }

    @AllArgsConstructor
    @Getter
    private static class RecordData {
        private RecordMetadata rmd;
        private Str.Data data;
    }

}
