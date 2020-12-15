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

package io.apicurio.registry.storage.impl.kafkasql;

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
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
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

import io.quarkus.security.identity.SecurityIdentity;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeyDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlKeySerializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlPartitioner;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueSerializer;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlSink;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlStore;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.kafka.AsyncProducer;
import io.apicurio.registry.utils.kafka.ProducerActions;
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
public class KafkaSqlRegistryStorage extends AbstractRegistryStorage implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlRegistryStorage.class);

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlSink kafkaSqlSink;

    @Inject
    KafkaSqlStore sqlStore;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.bootstrap.servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.topic", defaultValue = "kafkasql-journal")
    String topic;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.startupLag", defaultValue = "1000")
    Integer startupLag;

    @Inject
    @ConfigProperty(name = "registry.kafkasql.consumer.poll.timeout", defaultValue = "1000")
    Integer pollTimeout;
    
    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    SecurityIdentity securityIdentity;

    private boolean stopped = true;
    private ProducerActions<MessageKey, MessageValue> producer;
    private KafkaConsumer<MessageKey, MessageValue> consumer;
    private KafkaSqlSubmitter submitter;

    void onConstruct(@Observes StartupEvent ev) {
        log.info("Using Kafka-SQL storage.");
        // Start the Kafka Consumer thread
        consumer = createKafkaConsumer();
        startConsumerThread(consumer);

        producer = createKafkaProducer();
        submitter = new KafkaSqlSubmitter(this);
    }

    @PreDestroy
    void onDestroy() {
        stopped = true;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.MessageSender#send(io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey, io.apicurio.registry.storage.impl.kafkasql.values.MessageValue)
     */
    @Override
    public CompletableFuture<UUID> send(MessageKey key, MessageValue value) {
        UUID requestId = coordinator.createUUID();
        RecordHeader header = new RecordHeader("req", requestId.toString().getBytes());
        ProducerRecord<MessageKey, MessageValue> record = new ProducerRecord<>(topic, 0, key, value, Collections.singletonList(header));
        return producer.apply(record).thenApply(rm -> requestId);
    }

    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic,
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     * @param consumer
     */
    private void startConsumerThread(final KafkaConsumer<MessageKey, MessageValue> consumer) {
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
                    final ConsumerRecords<MessageKey, MessageValue> records = consumer.poll(Duration.ofMillis(pollTimeout));
                    if (records != null && !records.isEmpty()) {
                        log.debug("Consuming {} journal records.", records.count());
                        records.forEach(record -> {
                            // TODO instead of processing the journal record directly on the consumer thread, instead queue them and have *another* thread process the queue
                            kafkaSqlSink.processMessage(record);
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
    private ProducerActions<MessageKey, MessageValue> createKafkaProducer() {
        // TODO properties should be injected similar to StreamsRegistryConfiguration#storageProducer
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topic);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaSqlPartitioner.class);

        // Create the Kafka producer
        KafkaSqlKeySerializer keySerializer = new KafkaSqlKeySerializer();
        KafkaSqlValueSerializer valueSerializer = new KafkaSqlValueSerializer();
        return new AsyncProducer<MessageKey, MessageValue>(props, keySerializer, valueSerializer);
    }

    /**
     * Creates the Kafka consumer.
     */
    private KafkaConsumer<MessageKey, MessageValue> createKafkaConsumer() {
        // TODO properties should be injected similar to StreamsRegistryConfiguration#storageProducer
        Properties props = new Properties();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Create the Kafka Consumer
        KafkaSqlKeyDeserializer keyDeserializer = new KafkaSqlKeyDeserializer();
        KafkaSqlValueDeserializer valueDeserializer = new KafkaSqlValueDeserializer();
        KafkaConsumer<MessageKey, MessageValue> consumer = new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
        return consumer;
    }

    /**
     * Ensures that the given content exists in the database.  If it's already in the DB, then this just 
     * returns the content hash.  If the content does not yet exist in the DB, then it is added (by sending
     * the appropriate message to the Kafka topic and awaiting the response).
     * 
     * @param content
     * @param artifactId
     * @param artifactType
     */
    private String ensureContent(ContentHandle content, String artifactId, ArtifactType artifactType) {
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);
        
        if (!sqlStore.isContentExists(contentHash)) {
            CompletableFuture<UUID> future = submitter.submitContent(artifactId, contentHash, artifactType, content);
            UUID uuid = ConcurrentUtil.get(future);
            coordinator.waitForResponse(uuid);
        }
        
        return contentHash;
    }

    
    //TODO implement is Ready and is alive checking if the state is fully updated

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifactWithMetadata(artifactId, artifactType, content, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType,
            ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        if (sqlStore.isArtifactExists(artifactId)) {
            throw new ArtifactAlreadyExistsException(artifactId);
        }
        
        String contentHash = ensureContent(content, artifactId, artifactType);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();
        
        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
        }

        return submitter
                .submitArtifact(artifactId, ActionType.Create, artifactType, contentHash, createdBy, createdOn, metaData)
                .thenCompose(reqId -> (CompletionStage<ArtifactMetaDataDto>) coordinator.waitForResponse(reqId));
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(artifactId)) {
            throw new ArtifactNotFoundException(artifactId);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifact(artifactId, ActionType.Delete));
        SortedSet<Long> versionIds = (SortedSet<Long>) coordinator.waitForResponse(reqId);

        return versionIds;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifact(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(artifactId, artifactType, content, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(artifactId)) {
            throw new ArtifactNotFoundException(artifactId);
        }

        String contentHash = ensureContent(content, artifactId, artifactType);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();
        
        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
        }
        
        return submitter
                .submitArtifact(artifactId, ActionType.Update, artifactType, contentHash, createdBy, createdOn, metaData)
                .thenCompose(reqId -> (CompletionStage<ArtifactMetaDataDto>) coordinator.waitForResponse(reqId));
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return sqlStore.getArtifactIds(limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.lang.String, int, int, io.apicurio.registry.rest.beans.SearchOver, io.apicurio.registry.rest.beans.SortOrder)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver, SortOrder sortOrder) {
        return sqlStore.searchArtifacts(search, offset, limit, searchOver, sortOrder);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactMetaData(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersionMetaData(artifactId, canonical, content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactMetaData(id);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
        ArtifactMetaDataDto metaDataDto = sqlStore.getArtifactMetaData(artifactId);
        
        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(artifactId, metaDataDto.getVersion(), ActionType.Update, metaDataDto.getState(), metaData));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactRules(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        if (sqlStore.isArtifactRuleExists(artifactId, rule)) {
            throw new RuleAlreadyExistsException(rule);
        }

        return submitter
                .submitArtifactRule(artifactId, rule, ActionType.Create, config)
                .thenCompose(reqId -> {
                    CompletionStage<Void> rval = (CompletionStage<Void>) coordinator.waitForResponse(reqId);
                    log.debug("===============> Artifact rule (async) completed.  Rval: {}", rval);
                    return rval;
                });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(artifactId)) {
            throw new ArtifactNotFoundException(artifactId);
        }

        // TODO find a better way to implement deleting all rules?  This isn't very scalable if we add more rules.
        
        submitter.submitArtifactRule(artifactId, RuleType.COMPATIBILITY, ActionType.Delete);
        
        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(artifactId, RuleType.VALIDITY, ActionType.Delete));
        try {
            coordinator.waitForResponse(reqId);
        } catch (RuleNotFoundException e) {
            // Eat this exception - we don't care if the rule didn't exist.
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactRule(artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactRuleExists(artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(artifactId, rule, ActionType.Update, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactRuleExists(artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(artifactId, rule, ActionType.Delete));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersions(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, int, int)
     */
    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        return sqlStore.searchVersions(artifactId, offset, limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersion(id);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersion(artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersionMetaData(artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(artifactId, (int) version, ActionType.Delete));
            coordinator.waitForResponse(reqId);
            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(artifactId, version, ArtifactStateExt.ACTIVE_STATES, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(artifactId, (int) version, ActionType.Update, value.getState(), metaData));
            return coordinator.waitForResponse(reqId);
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(artifactId, (int) version, ActionType.Clear));
            return coordinator.waitForResponse(reqId);
        });
    }

    /**
     * Fetches the meta data for the given artifact version, validates the state (optionally), and then calls back the handler
     * with the metadata.  If the artifact is not found, this will throw an exception.
     * 
     * @param artifactId
     * @param version
     * @param states
     * @param handler
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    private <T> T handleVersion(String artifactId, long version, EnumSet<ArtifactState> states, Function<ArtifactVersionMetaDataDto, T> handler) 
            throws ArtifactNotFoundException, RegistryStorageException {
        
        ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(artifactId, version);

        ArtifactState state = metadata.getState();
        ArtifactStateExt.validateState(states, state, artifactId, version);
        return handler.apply(metadata);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return sqlStore.getGlobalRules();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.Create, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        // TODO find a better way to implement deleting all global rules?  This isn't very scalable if we add more rules.
        
        submitter.submitGlobalRule(RuleType.COMPATIBILITY, ActionType.Delete);
        
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(RuleType.VALIDITY, ActionType.Delete));
        try {
            coordinator.waitForResponse(reqId);
        } catch (RuleNotFoundException e) {
            // Eat this exception - we don't care if the rule didn't exist.
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return sqlStore.getGlobalRule(rule);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isGlobalRuleExists(rule)) {
            throw new RuleNotFoundException(rule);
        }
        
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.Update, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isGlobalRuleExists(rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.Delete));
        coordinator.waitForResponse(reqId);
    }


    private void updateArtifactState(ArtifactState currentState, String artifactId, Integer version, ArtifactState newState, EditableArtifactMetaDataDto metaData) {
        ArtifactStateExt.applyState(
            s ->  {
                UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(artifactId, version, ActionType.Update, newState, metaData));
                coordinator.waitForResponse(reqId);
            },
            currentState,
            newState
        );
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto metadata = sqlStore.getArtifactMetaData(artifactId);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), artifactId, metadata.getVersion(), state, metaDataDto);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(artifactId, version);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), artifactId, version, state, metaDataDto);
    }

    protected EditableArtifactMetaDataDto extractMetaData(ArtifactType artifactType, ContentHandle content) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        EditableMetaData emd = extractor.extract(content);
        EditableArtifactMetaDataDto metaData;
        if (emd != null) {
            metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());
        } else {
            metaData = new EditableArtifactMetaDataDto();
        }
        return metaData;
    }

}
