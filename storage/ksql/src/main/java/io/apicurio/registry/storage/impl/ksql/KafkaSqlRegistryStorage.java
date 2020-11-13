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
import java.util.Properties;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;

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
public class KafkaSqlRegistryStorage extends AbstractSqlRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlRegistryStorage.class);

    @Inject
    @ConfigProperty(name = "registry.ksql.globalRuleKey", defaultValue = "__global_rule")
    String globalRuleKey;
    
    @Inject
    @ConfigProperty(name = "registry.ksql.bootstrap.servers")
    String bootstrapServers;

    @Inject
    @ConfigProperty(name = "registry.ksql.topic", defaultValue = "ksql-journal")
    String topic;

    @Inject
    @ConfigProperty(name = "registry.ksql.consumer.startupLag", defaultValue = "1000")
    Integer startupLag;

    @Inject
    @ConfigProperty(name = "registry.ksql.consumer.poll.timeout", defaultValue = "1000")
    Integer pollTimeout;
    
    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlDispatcher dispatcher;
    
    private boolean stopped = true;
    private KafkaProducer<String, JournalRecord> producer;
    private KafkaConsumer<String, JournalRecord> consumer;
    private ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @PostConstruct
    void onConstruct() {
        log.info("Using Kafka-SQL storage.");
        // Start the Kafka Consumer thread
        consumer = createKafkaConsumer();
        startConsumerThread(consumer);
        
        producer = createKafkaProducer();
    }
    
    @PreDestroy
    void onDestroy() {
        stop();
    }
    
    public void stop() {
        stopped = true;
    }

    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic, 
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     * @param consumer
     */
    private void startConsumerThread(final KafkaConsumer<String, JournalRecord> consumer) {
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
                    final ConsumerRecords<String, JournalRecord> records = consumer.poll(Duration.ofMillis(pollTimeout));
                    if (records != null && !records.isEmpty()) {
                        log.debug("Consuming {} journal records.", records.count());
                        records.forEach(record -> {
                            JournalRecord journalRecord = record.value();
                            // TODO instead of processing the journal record directly on the consumer thread, instead queue them and have *another* thread process the queue?
                            processJournalRecord(journalRecord);
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
     * Process a single journal record found on the Kafka topic.
     * @param journalRecord
     */
    private void processJournalRecord(JournalRecord journalRecord) {
        log.debug("[{}] Processing journal record of type {}", journalRecord.getUuid(), journalRecord.getMethod());
        applying.set(Boolean.TRUE);
        try {
            Object returnValueOrException = dispatcher.dispatchTo(journalRecord, this);
            coordinator.notifyResponse(journalRecord.getUuid(), returnValueOrException);
        } finally {
            applying.set(Boolean.FALSE);
        }
    }

    /**
     * Creates the Kafka producer.
     */
    private KafkaProducer<String, JournalRecord> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topic);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JournalRecordSerializer.class.getName());

        // Create the Kafka producer
        KafkaProducer<String, JournalRecord> producer = new KafkaProducer<>(props);
        return producer;
    }

    /**
     * Creates the Kafka consumer.
     */
    private KafkaConsumer<String, JournalRecord> createKafkaConsumer() {
        Properties props = new Properties();

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JournalRecordDeserializer.class.getName());

        // Create the Kafka Consumer
        KafkaConsumer<String, JournalRecord> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

    private boolean isApplying() {
        return applying.get();
    }

    /**
     * Create a journal record and publish it to the Kafka topic.
     * @param journalKey
     * @param methodName
     * @param arguments
     * @throws RegistryException
     */
    private Object journalAndWait(String journalKey, String methodName, Object ...arguments) throws RegistryException {
        UUID uuid = coordinator.createUUID();
        log.debug("[{}] Publishing journal record of type {}", uuid, methodName);
        JournalRecord record = JournalRecord.create(uuid, methodName, arguments);
        ProducerRecord<String, JournalRecord> producerRecord = new ProducerRecord<String, JournalRecord>(topic, journalKey, record);
        producer.send(producerRecord);
        Object rval;
        try {
            log.debug("[{}] Waiting for journal record response for type {}", uuid, methodName);
            rval = coordinator.waitForResponse(uuid);
        } catch (InterruptedException e) {
            throw new RegistryStorageException(e);
        }
        if (rval instanceof RegistryException) {
            throw (RegistryException) rval;
        } else {
            return rval;
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        if (isApplying()) {
            super.createGlobalRule(rule, config);
        } else {
            journalAndWait(globalRuleKey, "createGlobalRule", rule, config);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.updateGlobalRule(rule, config);
        } else {
            journalAndWait(globalRuleKey, "updateGlobalRule", rule, config);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        if (isApplying()) {
            super.deleteGlobalRules();
        } else {
            journalAndWait(globalRuleKey, "deleteGlobalRules");
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.deleteGlobalRule(rule);
        } else {
            journalAndWait(globalRuleKey, "deleteGlobalRule", rule);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override
    public SortedSet<Long> deleteArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        if (isApplying()) {
            return super.deleteArtifact(artifactId);
        } else {
            return (SortedSet<Long>) journalAndWait(artifactId, "deleteArtifact", artifactId);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.deleteArtifactRule(artifactId, rule);
        } else {
            journalAndWait(artifactId, "deleteArtifactRule", artifactId, rule);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.deleteArtifactRules(artifactId);
        } else {
            journalAndWait(artifactId, "deleteArtifactRules", artifactId);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.deleteArtifactVersion(artifactId, version);
        } else {
            journalAndWait(artifactId, "deleteArtifactVersion", artifactId, version);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.deleteArtifactVersionMetaData(artifactId, version);
        } else {
            journalAndWait(artifactId, "deleteArtifactVersionMetaData", artifactId, version);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        if (isApplying()) {
            return super.createArtifact(artifactId, artifactType, content);
        } else {
            return (CompletionStage<ArtifactMetaDataDto>) journalAndWait(artifactId, "createArtifact", artifactId, artifactType, content);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        if (isApplying()) {
            return super.createArtifactWithMetadata(artifactId, artifactType, content, metaData);
        } else {
            return (CompletionStage<ArtifactMetaDataDto>) journalAndWait(artifactId, "createArtifactWithMetadata", artifactId, artifactType, content, metaData);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#createArtifactRuleAsync(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        if (isApplying()) {
            return super.createArtifactRuleAsync(artifactId, rule, config);
        } else {
            return (CompletionStage<Void>) journalAndWait(artifactId, "createArtifactRuleAsync", artifactId, rule, config);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        if (isApplying()) {
            return super.updateArtifact(artifactId, artifactType, content);
        } else {
            return (CompletionStage<ArtifactMetaDataDto>) journalAndWait(artifactId, "updateArtifact", artifactId, artifactType, content);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.updateArtifactMetaData(artifactId, metaData);
        } else {
            journalAndWait(artifactId, "updateArtifactMetaData", artifactId, metaData);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.updateArtifactRule(artifactId, rule, config);
        } else {
            journalAndWait(artifactId, "updateArtifactRule", artifactId, rule, config);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state) {
        if (isApplying()) {
            super.updateArtifactState(artifactId, state);
        } else {
            journalAndWait(artifactId, "updateArtifactState", artifactId, state);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        if (isApplying()) {
            super.updateArtifactState(artifactId, state, version);
        } else {
            journalAndWait(artifactId, "updateArtifactState", artifactId, state, version);
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        if (isApplying()) {
            super.updateArtifactVersionMetaData(artifactId, version, metaData);
        } else {
            journalAndWait(artifactId, "updateArtifactVersionMetaData", artifactId, version, metaData);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        if (isApplying()) {
            return super.updateArtifactWithMetadata(artifactId, artifactType, content, metaData);
        } else {
            return (CompletionStage<ArtifactMetaDataDto>) journalAndWait(artifactId, "updateArtifactWithMetadata", artifactId, artifactType, content, metaData);
        }
    }

}
