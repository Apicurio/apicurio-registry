/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.ksql;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serdes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.ksql.sql.KafkaSQLSink;
import io.apicurio.registry.storage.proto.Str;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;
import io.apicurio.registry.utils.kafka.ProtoSerde;
import io.apicurio.registry.utils.kafka.Submitter;
import io.quarkus.runtime.StartupEvent;

/**
 * An implementation of a registry storage that extends the basic SQL storage but federates 'write' operations
 * to other nodes in a cluster using a Kafka topic.  As a result, all reads are performed locally but all
 * writes are published to a topic for consumption by all nodes.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
@SuppressWarnings("unchecked")
public class KafkaSqlRegistryStorage extends AbstractRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlRegistryStorage.class);

    /* Fake global rules as an artifact */
    public static final String GLOBAL_RULES_ID = "__GLOBAL_RULES__";

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSQLSink kafkaSqlSink;

    @Inject
    @Named("SQLRegistryStorage")
    RegistryStorage sqlStorage;

    @Inject
    @ConfigProperty(name = "registry.ksql.globalRuleKey", defaultValue = "__global_rule")
    String globalRuleKey;

    @Inject
    @ConfigProperty(name = "registry.ksql.bootstrap.servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "registry.ksql.topic", defaultValue = "storage-topic")
    String topic;

    @Inject
    @ConfigProperty(name = "registry.ksql.consumer.startupLag", defaultValue = "1000")
    Integer startupLag;

    @Inject
    @ConfigProperty(name = "registry.ksql.consumer.poll.timeout", defaultValue = "1000")
    Integer pollTimeout;

    private boolean stopped = true;
    private ProducerActions<String, Str.StorageValue> storageProducer;
    private KafkaConsumer<String, Str.StorageValue> consumer;
    private Submitter<UUID> submitter;

    void onConstruct(@Observes StartupEvent ev) {
        log.info("Using Kafka-SQL storage.");
        // Start the Kafka Consumer thread
        consumer = createKafkaConsumer();
        startConsumerThread(consumer);

        storageProducer = createKafkaProducer();
        submitter = new Submitter<UUID>(this::send);
    }

    @PreDestroy
    void onDestroy() {
        stopped = true;
    }

    private CompletableFuture<UUID> send(Str.StorageValue value) {
        UUID requestId = coordinator.createUUID();
        RecordHeader h = new RecordHeader("req", requestId.toString().getBytes());
        ProducerRecord<String, Str.StorageValue> record = new ProducerRecord<>(
            topic,
            0,
            value.getArtifactId(), // MUST be set
            value,
            Collections.singletonList(h)
        );
        return storageProducer.apply(record).thenApply(rm -> requestId);
    }

    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic,
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     * @param consumer
     */
    private void startConsumerThread(final KafkaConsumer<String, Str.StorageValue> consumer) {
        log.info("Starting KSQL consumer thread on topic: {}", topic);
        log.info("Bootstrap servers: " + bootstrapServers);
        Runnable runner = () -> {
            log.info("KSQL consumer thread startup lag: {}", startupLag);

            try {
                // Startup lag
                try { Thread.sleep(startupLag); } catch (InterruptedException e) { }

                log.info("Subscribing to {}", topic);

                // Subscribe to the journal topic
                Collection<String> topics = Collections.singleton(topic);
                consumer.subscribe(topics);

                // Main consumer loop
                while (!stopped) {
                    final ConsumerRecords<String, Str.StorageValue> records = consumer.poll(Duration.ofMillis(pollTimeout));
                    if (records != null && !records.isEmpty()) {
                        log.debug("Consuming {} journal records.", records.count());
                        records.forEach(record -> {

                            UUID req = Optional.ofNullable(record.headers().headers("req"))
                                .map(Iterable::iterator)
                                .map(it -> {
                                    return it.hasNext() ? it.next() : null;
                                })
                                .map(Header::value)
                                .map(String::new)
                                .map(UUID::fromString)
                                .orElse(null);

                            String artifactId = record.key();
                            Str.StorageValue storageAction = record.value();

                            // TODO instead of processing the journal record directly on the consumer thread, instead queue them and have *another* thread process the queue
                            kafkaSqlSink.processStorageAction(req, artifactId, storageAction);
                        });
                    }
                }
            } finally {
                consumer.close();
            }
        };
        stopped = false;
        Thread thread = new Thread(runner);
        thread.setDaemon(true);
        thread.setName("KSQL Kafka Consumer Thread");
        thread.start();
    }

    /**
     * Creates the Kafka producer.
     */
    private ProducerActions<String, Str.StorageValue> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topic);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");

        // Create the Kafka producer
        return new AsyncProducer<String, Str.StorageValue>(props,
                Serdes.String().serializer(),
                ProtoSerde.parsedWith(Str.StorageValue.parser()));
    }

    /**
     * Creates the Kafka consumer.
     */
    private KafkaConsumer<String, Str.StorageValue> createKafkaConsumer() {
        Properties props = new Properties();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Create the Kafka Consumer
        KafkaConsumer<String, Str.StorageValue> consumer = new KafkaConsumer<>(props,
                Serdes.String().deserializer(),
                ProtoSerde.parsedWith(Str.StorageValue.parser()));
        return consumer;
    }

    private void updateArtifactState(ArtifactState currentState, String artifactId, Integer version, ArtifactState state) {
        ArtifactStateExt.applyState(
            s ->  {
                UUID reqId = ConcurrentUtil.get(submitter.submitState(artifactId, version.longValue(), s));
                coordinator.waitForResponse(reqId);
            },
            currentState,
            state
        );
    }

    //TODO implement is Ready and is alive checking if the state is fully updated

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        ArtifactMetaDataDto metadata = sqlStorage.getArtifactMetaData(artifactId);
        updateArtifactState(metadata.getState(), artifactId, metadata.getVersion(), state);
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        ArtifactVersionMetaDataDto metadata = sqlStorage.getArtifactVersionMetaData(artifactId, version);
        updateArtifactState(metadata.getState(), artifactId, version, state);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {

        try {
            sqlStorage.getArtifactMetaData(artifactId);
            throw new ArtifactAlreadyExistsException(artifactId);
        } catch (ArtifactNotFoundException e) {
            //ignored
            //artifact does not exist, we can create it
        }

        return submitter
                .submitArtifact(Str.ActionType.CREATE, artifactId, -1, artifactType, content.bytes())
                .thenCompose(reqId -> {
                    return (CompletionStage<ArtifactMetaDataDto>) coordinator.waitForResponse(reqId);
                });
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifact(artifactId, artifactType, content)
            .thenCompose(amdd -> submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties())
                .thenApply(v -> DtoUtil.setEditableMetaDataInArtifact(amdd, metaData)));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {

        //to verify artifact exists
        //TODO implement a low level storage api that provides methods like, exists, ...
        sqlStorage.getArtifactMetaData(artifactId);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, -1, null, null));
        SortedSet<Long> versionIds = (SortedSet<Long>) coordinator.waitForResponse(reqId);

        return versionIds;
    }

    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifact(artifactId);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {

        try {
            sqlStorage.getArtifactMetaData(artifactId);
        } catch (ArtifactNotFoundException e) {
            throw e;
        }

        return submitter
                .submitArtifact(Str.ActionType.UPDATE, artifactId, -1, artifactType, content.bytes())
                .thenCompose(reqId -> (CompletionStage<ArtifactMetaDataDto>) coordinator.waitForResponse(reqId));

    }


    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return updateArtifact(artifactId, artifactType, content)
            .thenCompose(amdd -> submitter.submitMetadata(Str.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties())
                .thenApply(v -> DtoUtil.setEditableMetaDataInArtifact(amdd, metaData)));
    }


    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return sqlStorage.getArtifactIds(limit);
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver, SortOrder sortOrder) {
        return sqlStorage.searchArtifacts(search, offset, limit, searchOver, sortOrder);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactMetaData(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactVersionMetaData(artifactId, canonical, content);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactMetaData(id);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

        try {
            sqlStorage.getArtifactMetaData(artifactId);
        } catch (ArtifactNotFoundException e) {
            throw e;
        }

        UUID reqId = ConcurrentUtil.get(submitter
                .submitMetadata(Str.ActionType.UPDATE, artifactId, -1, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties()));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactRules(artifactId);
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {

        try {
            sqlStorage.getArtifactRule(artifactId, rule);
            throw new RuleAlreadyExistsException(rule);
        } catch (RuleNotFoundException e) {
            //rule does not exist, we can create it
        }

        return submitter
                .submitRule(Str.ActionType.CREATE, artifactId, rule, config.getConfiguration())
                .thenCompose(reqId -> (CompletionStage<Void>) coordinator.waitForResponse(reqId));
    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            sqlStorage.getArtifactMetaData(artifactId);
        } catch (ArtifactNotFoundException e) {
            throw e;
        }

        deleteArtifactRulesInternal(artifactId);
    }

    private void deleteArtifactRulesInternal(String artifactId) {
        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, artifactId, null, null));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactRule(artifactId, rule);
    }

    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

        try {
            sqlStorage.getArtifactRule(artifactId, rule);
        } catch (RuleNotFoundException e) {
            throw e;
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.UPDATE, artifactId, rule, config.getConfiguration()));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            sqlStorage.getArtifactRule(artifactId, rule);
        } catch (RuleNotFoundException e) {
            throw e;
        }
        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, artifactId, rule, null));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactVersions(artifactId);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        return sqlStorage.searchVersions(artifactId, offset, limit);
    }

    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactVersion(id);
    }

    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactVersion(artifactId, version);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStorage.getArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitArtifact(Str.ActionType.DELETE, artifactId, version, null, null));
            coordinator.waitForResponse(reqId);
            return null;
        });
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(
            artifactId,
            version,
            ArtifactStateExt.ACTIVE_STATES,
            value -> {
                UUID reqId = ConcurrentUtil.get(submitter
                        .submitMetadata(Str.ActionType.UPDATE, artifactId, version, metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties()));
                coordinator.waitForResponse(reqId);
                return null;
            }
        );
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(
            artifactId,
            version,
            null,
            value -> {
                UUID reqId = ConcurrentUtil.get(submitter.submitMetadata(Str.ActionType.DELETE, artifactId, version, null, null, Collections.emptyList(), Collections.emptyMap()));
                coordinator.waitForResponse(reqId);
                return null;
            }
        );
    }

    private <T> T handleVersion(String artifactId, long version, EnumSet<ArtifactState> states, Function<ArtifactVersionMetaDataDto, T> handler) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactVersionMetaDataDto metadata = sqlStorage.getArtifactVersionMetaData(artifactId, version);

        ArtifactState state = metadata.getState();
        ArtifactStateExt.validateState(states, state, artifactId, version);
        return handler.apply(metadata);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return sqlStorage.getGlobalRules();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.CREATE, GLOBAL_RULES_ID, rule, config.getConfiguration()));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, GLOBAL_RULES_ID, null, null));
        coordinator.waitForResponse(reqId);

    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return sqlStorage.getGlobalRule(rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        try {
            sqlStorage.getGlobalRule(rule);
        } catch (RuleNotFoundException e) {
            throw e;
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.UPDATE, GLOBAL_RULES_ID, rule, config.getConfiguration()));
        coordinator.waitForResponse(reqId);

    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        try {
            sqlStorage.getGlobalRule(rule);
        } catch (RuleNotFoundException e) {
            throw e;
        }
        UUID reqId = ConcurrentUtil.get(submitter.submitRule(Str.ActionType.DELETE, GLOBAL_RULES_ID, rule, null));
        coordinator.waitForResponse(reqId);
    }

}
