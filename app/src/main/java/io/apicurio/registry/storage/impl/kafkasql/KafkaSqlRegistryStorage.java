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

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorReadOnlyBase;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RoleMappingNotFoundException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.kafkasql.keys.BootstrapKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlSink;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.SqlStorageEvent;
import io.apicurio.registry.storage.impl.sql.SqlStorageEventType;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.storage.importing.SqlDataImporter;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.CommentEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;

/**
 * An implementation of a registry artifactStore that extends the basic SQL artifactStore but federates 'write' operations
 * to other nodes in a cluster using a Kafka topic.  As a result, all reads are performed locally but all
 * writes are published to a topic for consumption by all nodes.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@StorageMetricsApply
@Logged
@SuppressWarnings("unchecked")
public class KafkaSqlRegistryStorage extends RegistryStorageDecoratorReadOnlyBase implements RegistryStorageDecorator {

    @Inject
    Logger log;

    @ConfigProperty(name = "registry.storage.kind")
    String registryStorageType;

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlSink kafkaSqlSink;

    @Inject
    SqlRegistryStorage sqlStore;

    @Inject
    RegistryStorageContentUtils utils;

    @Inject
    KafkaConsumer<MessageKey, MessageValue> consumer;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ArtifactStateExt artifactStateEx;

    @Inject
    Event<StorageEvent> storageEvent;

    private volatile boolean bootstrapped = false;
    private volatile boolean stopped = true;


    @PostConstruct
    void onConstruct() {
        if (registryStorageType.equals("kafkasql")) {
            log.info("Using Kafka-SQL artifactStore.");

            // Create Kafka topics if needed
            if (configuration.isTopicAutoCreate()) {
                autoCreateTopics();
            }

            setDelegate(sqlStore);
        }
    }

    /**
     * Handles SQL storage CDI events.
     */
    public void handleSqlStorageEvent(@Observes SqlStorageEvent event) {
        if (SqlStorageEventType.READY.equals(event.getType()) && (registryStorageType.equals("kafkasql"))) {
            // Start the Kafka Consumer thread only once the SQL storage is initialized
            log.info("SQL store initialized, starting consumer thread.");
            startConsumerThread(consumer);
        }
    }

    @Override
    public String storageName() {
        return "kafkasql";
    }


    @Override
    public boolean isReady() {
        return bootstrapped;
    }


    @Override
    public boolean isAlive() {
        // TODO: Include readiness of Kafka consumers and producers? What happens if Kafka stops responding?
        return bootstrapped && !stopped;
    }


    @PreDestroy
    void onDestroy() {
        stopped = true;
    }


    /**
     * Automatically create the Kafka topics.
     */
    private void autoCreateTopics() {
        Set<String> topicNames = new LinkedHashSet<>();
        topicNames.add(configuration.topic());
        Map<String, String> topicProperties = new HashMap<>();
        configuration.topicProperties().forEach((key, value) -> topicProperties.put(key.toString(), value.toString()));
        // Use log compaction by default.
        topicProperties.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
        Properties adminProperties = configuration.adminProperties();
        adminProperties.putIfAbsent(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
        try {
            KafkaUtil.createTopics(adminProperties, topicNames, topicProperties);
        } catch (TopicExistsException e) {
            log.info("Topic {} already exists, skipping.", configuration.topic());
        }
    }


    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic,
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     */
    private void startConsumerThread(final KafkaConsumer<MessageKey, MessageValue> consumer) {
        log.info("Starting KSQL consumer thread on topic: {}", configuration.topic());
        log.info("Bootstrap servers: {}", configuration.bootstrapServers());

        final String bootstrapId = UUID.randomUUID().toString();
        submitter.submitBootstrap(bootstrapId);
        final long bootstrapStart = System.currentTimeMillis();

        Runnable runner = () -> {
            try (consumer) {
                log.info("Subscribing to {}", configuration.topic());

                // Subscribe to the journal topic
                Collection<String> topics = Collections.singleton(configuration.topic());
                consumer.subscribe(topics);

                // Main consumer loop
                while (!stopped) {
                    final ConsumerRecords<MessageKey, MessageValue> records = consumer.poll(Duration.ofMillis(configuration.pollTimeout()));
                    if (records != null && !records.isEmpty()) {
                        log.debug("Consuming {} journal records.", records.count());
                        records.forEach(record -> {

                            // If the key is null, we couldn't deserialize the message
                            if (record.key() == null) {
                                log.info("Discarded an unreadable/unrecognized message.");
                                return;
                            }

                            // If the key is a Bootstrap key, then we have processed all messages and can set bootstrapped to 'true'
                            if (record.key().getType() == MessageType.Bootstrap) {
                                BootstrapKey bkey = (BootstrapKey) record.key();
                                if (bkey.getBootstrapId().equals(bootstrapId)) {
                                    this.bootstrapped = true;
                                    storageEvent.fireAsync(StorageEvent.builder()
                                            .type(StorageEventType.READY)
                                            .build());
                                    log.info("KafkaSQL storage bootstrapped in {} ms.", System.currentTimeMillis() - bootstrapStart);
                                }
                                return;
                            }

                            // If the value is null, then this is a tombstone (or unrecognized) message and should not
                            // be processed.
                            if (record.value() == null) {
                                log.info("Discarded a (presumed) tombstone message with key: {}", record.key());
                                return;
                            }

                            // TODO instead of processing the journal record directly on the consumer thread, instead queue them and have *another* thread process the queue
                            kafkaSqlSink.processMessage(record);
                        });
                    }
                }
            }
        };
        stopped = false;
        Thread thread = new Thread(runner);
        thread.setDaemon(true);
        thread.setName("KSQL Kafka Consumer Thread");
        thread.start();
    }


    /**
     * Ensures that the given content exists in the database.  If it's already in the DB, then this just
     * returns the content hash.  If the content does not yet exist in the DB, then it is added (by sending
     * the appropriate message to the Kafka topic and awaiting the response).
     */
    private String ensureContent(ContentHandle content, String artifactType, List<ArtifactReferenceDto> references) {

        String contentHash = utils.getContentHash(content, references);

        if (!delegate.isContentExists(contentHash)) {

            long contentId = nextContentId();
            String canonicalContentHash = utils.getCanonicalContentHash(content, artifactType, references, this::resolveReferences);

            CompletableFuture<UUID> future = submitter.submitContent(contentId, contentHash, ActionType.CREATE, canonicalContentHash, content, SqlUtil.serializeReferences(references));
            UUID uuid = ConcurrentUtil.get(future);
            coordinator.waitForResponse(uuid);
        }

        return contentHash;
    }


    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
                                              ContentHandle content, List<ArtifactReferenceDto> references) {
        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash,
                                                          String createdBy, Date createdOn,
                                                          EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator) {
        var contentDto = getArtifactByContentHash(contentHash);
        return createArtifactWithMetadataRaw(groupId, artifactId, version, artifactType, contentDto.getContent(),
                metaData, contentDto.getReferences(), globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) {
        IdGenerator globalIdGenerator = this::nextGlobalId;
        return createArtifactWithMetadataRaw(groupId, artifactId, version, artifactType, content, metaData, references, globalIdGenerator);
    }


    private ArtifactMetaDataDto createArtifactWithMetadataRaw(String groupId, String artifactId, String version,
                                                              String artifactType, ContentHandle content,
                                                              EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references,
                                                              IdGenerator globalIdGenerator) {

        if (delegate.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }

        String contentHash = ensureContent(content, artifactType, references);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (metaData == null) {
            metaData = utils.extractEditableArtifactMetadata(artifactType, content);
        }

        if (groupId != null && !isGroupExists(groupId)) {
            //Only create group metadata for non-default groups.
            createGroup(GroupMetaDataDto.builder()
                    .groupId(groupId)
                    .createdOn(0)
                    .modifiedOn(0)
                    .createdBy(createdBy)
                    .modifiedBy(createdBy)
                    .build());
        }

        long globalId = globalIdGenerator.generate();

        UUID uuid = ConcurrentUtil.get(
                submitter.submitArtifact(groupId, artifactId, version, ActionType.CREATE,
                        globalId, artifactType, contentHash, createdBy, createdOn, metaData));
        return (ArtifactMetaDataDto) coordinator.waitForResponse(uuid);
    }


    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) {
        if (!delegate.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifact(groupId, artifactId, ActionType.DELETE));
        List<String> versionIds = (List<String>) coordinator.waitForResponse(reqId);

        // Add tombstone messages for all version metadata updates
        versionIds.forEach(vid -> {
            submitter.submitArtifactVersionTombstone(groupId, artifactId, vid);
        });

        // Add tombstone messages for all artifact rules
        RuleType[] ruleTypes = RuleType.values();
        for (RuleType ruleType : ruleTypes) {
            submitter.submitArtifactRuleTombstone(groupId, artifactId, ruleType);
        }

        return versionIds;
    }


    @Override
    public void deleteArtifacts(String groupId) {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(groupId, ActionType.DELETE, true));
        coordinator.waitForResponse(reqId);

        // TODO could possibly add tombstone messages for *all* artifacts that were deleted (version meta-data and artifact rules)
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash,
                                                          String createdBy, Date createdOn,
                                                          EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator) {
        var contentDto = getArtifactByContentHash(contentHash);
        return updateArtifactWithMetadataRaw(groupId, artifactId, version,
                artifactType, contentDto.getContent(), metaData, contentDto.getReferences(), globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) {
        return updateArtifactWithMetadataRaw(groupId, artifactId, version,
                artifactType, content, metaData, references, this::nextGlobalId);
    }


    private ArtifactMetaDataDto updateArtifactWithMetadataRaw(String groupId, String artifactId, String version,
                                                              String artifactType, ContentHandle content,
                                                              EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references,
                                                              IdGenerator globalIdGenerator) {
        if (!delegate.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        if (version != null && delegate.isArtifactVersionExists(groupId, artifactId, version)) {
            throw new VersionAlreadyExistsException(groupId, artifactId, version);
        }

        String contentHash = ensureContent(content, artifactType, references);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (metaData == null) {
            metaData = utils.extractEditableArtifactMetadata(artifactType, content);
        }

        long globalId = globalIdGenerator.generate();

        UUID reqId = ConcurrentUtil.get(
                submitter.submitArtifact(groupId, artifactId, version, ActionType.UPDATE,
                        globalId, artifactType, contentHash, createdBy, createdOn, metaData));
        return (ArtifactMetaDataDto) coordinator.waitForResponse(reqId);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData) {
        // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
        ArtifactMetaDataDto metaDataDto = delegate.getArtifactMetaData(groupId, artifactId);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(groupId, artifactId, metaDataDto.getVersion(),
                ActionType.UPDATE, metaDataDto.getState(), metaData));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) {
        // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
        /*ArtifactMetaDataDto metaDataDto = */
        delegate.getArtifactMetaData(groupId, artifactId, DEFAULT);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactOwner(groupId, artifactId, ActionType.UPDATE, owner.getOwner()));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config) {
        if (delegate.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleAlreadyExistsException(rule);
        }

        UUID reqId = ConcurrentUtil.get(
                submitter.submitArtifactRule(groupId, artifactId, rule, ActionType.CREATE, config));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        if (!delegate.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        submitter.submitArtifactRule(groupId, artifactId, RuleType.COMPATIBILITY, ActionType.DELETE);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(groupId, artifactId, RuleType.VALIDITY, ActionType.DELETE));
        try {
            coordinator.waitForResponse(reqId);
        } catch (RuleNotFoundException e) {
            // Eat this exception - we don't care if the rule didn't exist.
        }
    }


    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config) {
        if (!delegate.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(groupId, artifactId, rule, ActionType.UPDATE, config));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        if (!delegate.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(groupId, artifactId, rule, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        withArtifactVersionMetadataValidateState(groupId, artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(groupId, artifactId, version, ActionType.DELETE));
            coordinator.waitForResponse(reqId);

            // Add a tombstone message for this version's metadata
            submitter.submitArtifactVersionTombstone(groupId, artifactId, version);

            return null;
        });
    }


    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData) {
        withArtifactVersionMetadataValidateState(groupId, artifactId, version, ArtifactStateExt.ACTIVE_STATES, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(groupId, artifactId,
                    version, ActionType.UPDATE, value.getState(), metaData));
            return coordinator.waitForResponse(reqId);
        });
    }


    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        withArtifactVersionMetadataValidateState(groupId, artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(groupId, artifactId, version, ActionType.CLEAR));
            return coordinator.waitForResponse(reqId);
        });
    }


    /**
     * Fetches the metadata for the given artifact version, validates the state (optionally), and then calls back the handler
     * with the metadata.  If the artifact is not found, this will throw an exception.
     */
    private <T> T withArtifactVersionMetadataValidateState(String groupId, String artifactId, String version, EnumSet<ArtifactState> states, Function<ArtifactVersionMetaDataDto, T> handler) {

        ArtifactVersionMetaDataDto metadata = delegate.getArtifactVersionMetaData(groupId, artifactId, version);

        ArtifactState state = metadata.getState();
        artifactStateEx.validateState(states, state, groupId, artifactId, version);
        return handler.apply(metadata);
    }


    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.CREATE, config));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteGlobalRules() {
        // TODO This should use "DELETE FROM" instead of being rule specific

        getGlobalRules().stream()
                .map(r -> ConcurrentUtil.get(submitter.submitGlobalRule(r, ActionType.DELETE)))
                .forEach(reqId -> {
                    try {
                        coordinator.waitForResponse(reqId);
                    } catch (RuleNotFoundException e) {
                        // Eat this exception - we don't care if the rule didn't exist.
                    }
                });
    }


    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) {
        if (!delegate.isGlobalRuleExists(rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.UPDATE, config));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteGlobalRule(RuleType rule) {
        if (!delegate.isGlobalRuleExists(rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(rule, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }


    private void updateArtifactState(ArtifactState currentState, String groupId, String artifactId, String version, ArtifactState newState, EditableArtifactMetaDataDto metaData) {
        artifactStateEx.applyState(
                s -> {
                    UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(groupId, artifactId,
                            version, ActionType.UPDATE, newState, metaData));
                    coordinator.waitForResponse(reqId);
                },
                currentState,
                newState
        );
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) {
        ArtifactMetaDataDto metadata = delegate.getArtifactMetaData(groupId, artifactId, DEFAULT);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), groupId, artifactId, metadata.getVersion(), state, metaDataDto);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state) {
        ArtifactVersionMetaDataDto metadata = delegate.getArtifactVersionMetaData(groupId, artifactId, version);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), groupId, artifactId, version, state, metaDataDto);
    }


    @Override
    public void createGroup(GroupMetaDataDto group) {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(ActionType.CREATE, group));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(ActionType.UPDATE, group));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteGroup(String groupId) {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(groupId, ActionType.DELETE, false));
        coordinator.waitForResponse(reqId);
    }


    @Override
    @Transactional
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) {
        DataImporter dataImporter = new SqlDataImporter(log, utils, this, preserveGlobalId, preserveContentId);
        dataImporter.importData(entities, () -> {
            // Because importing just pushes a bunch of Kafka messages, we may need to
            // wait for a few seconds before we send the reset messages.  Due to partitioning,
            // we can't guarantee ordering of these next two messages, and we NEED them to
            // be consumed after all the import messages.
            // TODO We can wait until the last message is read (a specific one),
            // or create a new message type for this purpose (a sync message).
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                // Noop
            }
        });
    }

    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return sqlStore.getEnabledArtifactContentIds(groupId, artifactId);
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName) {
        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(principalId, ActionType.CREATE, role, principalName));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteRoleMapping(String principalId) {
        if (!delegate.isRoleMappingExists(principalId)) {
            throw new RoleMappingNotFoundException(principalId);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(principalId, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void updateRoleMapping(String principalId, String role) {
        if (!delegate.isRoleMappingExists(principalId)) {
            throw new RoleMappingNotFoundException(principalId, role);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(principalId, ActionType.UPDATE, role, null));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteAllUserData() {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalAction(ActionType.DELETE_ALL_USER_DATA));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public String createDownload(DownloadContextDto context) {
        String downloadId = UUID.randomUUID().toString();
        UUID reqId = ConcurrentUtil.get(submitter.submitDownload(downloadId, ActionType.CREATE, context));
        return (String) coordinator.waitForResponse(reqId);
    }


    @Override
    public DownloadContextDto consumeDownload(String downloadId) {
        UUID reqId = ConcurrentUtil.get(submitter.submitDownload(downloadId, ActionType.DELETE));
        return (DownloadContextDto) coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        // Note: this is OK to do because the only caller of this method is the DownloadReaper, which
        // runs on every node in the cluster.
        delegate.deleteAllExpiredDownloads();
    }


    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        UUID reqId = ConcurrentUtil.get(submitter.submitConfigProperty(propertyDto.getName(), ActionType.UPDATE, propertyDto.getValue()));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void deleteConfigProperty(String propertyName) {
        UUID reqId = ConcurrentUtil.get(submitter.submitConfigProperty(propertyName, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) {
        String theVersion = delegate.normalizeVersion(groupId, artifactId, version);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        return createArtifactVersionCommentRaw(groupId, artifactId, theVersion, this::nextCommentId, createdBy, createdOn, value);
    }


    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        String theVersion = delegate.normalizeVersion(groupId, artifactId, version);

        if (!delegate.isArtifactVersionExists(groupId, artifactId, theVersion)) {
            throw new VersionNotFoundException(groupId, artifactId, theVersion);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitComment(groupId, artifactId, theVersion, commentId, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) {
        String theVersion = delegate.normalizeVersion(groupId, artifactId, version);

        if (!delegate.isArtifactVersionExists(groupId, artifactId, theVersion)) {
            throw new VersionNotFoundException(groupId, artifactId, theVersion);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitComment(groupId, artifactId, theVersion,
                commentId, ActionType.UPDATE, null, null, value));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
        submitter.submitArtifactRule(entity.groupId, entity.artifactId, entity.type, ActionType.IMPORT, config);
    }


    @Override
    public void importComment(CommentEntity entity) {
        submitter.submitComment(entity.commentId, ActionType.IMPORT, entity.globalId,
                entity.createdBy, new Date(entity.createdOn), entity.value);
    }


    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        EditableArtifactMetaDataDto metaData = EditableArtifactMetaDataDto.builder()
                .name(entity.name)
                .description(entity.description)
                .labels(entity.labels)
                .properties(entity.properties)
                .build();
        submitter.submitArtifact(entity.groupId, entity.artifactId, entity.version, ActionType.IMPORT,
                entity.globalId, entity.artifactType, null, entity.createdBy, new Date(entity.createdOn), metaData, entity.versionId,
                entity.state, entity.contentId, entity.isLatest);
    }


    @Override
    public void importContent(ContentEntity entity) {
        submitter.submitContent(entity.contentId, entity.contentHash, ActionType.IMPORT, entity.canonicalHash, ContentHandle.create(entity.contentBytes), entity.serializedReferences);
    }


    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
        submitter.submitGlobalRule(entity.ruleType, ActionType.IMPORT, config);
    }


    @Override
    public void importGroup(GroupEntity entity) {
        GroupMetaDataDto group = new GroupMetaDataDto();
        group.setArtifactsType(entity.artifactsType);
        group.setCreatedBy(entity.createdBy);
        group.setCreatedOn(entity.createdOn);
        group.setDescription(entity.description);
        group.setGroupId(entity.groupId);
        group.setModifiedBy(entity.modifiedBy);
        group.setModifiedOn(entity.modifiedOn);
        group.setProperties(entity.properties);
        submitter.submitGroup(ActionType.IMPORT, group);
    }


    @Override
    public void resetContentId() {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalId(ActionType.RESET));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public void resetGlobalId() {
        UUID reqId = ConcurrentUtil.get(submitter.submitContentId(ActionType.RESET));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version, IdGenerator commentIdGen,
                                                      String createdBy, Date createdOn, String value) {
        String commentId = String.valueOf(commentIdGen.generate());

        UUID reqId = ConcurrentUtil.get(
                submitter.submitComment(groupId, artifactId, version, commentId,
                        ActionType.CREATE, createdBy, createdOn, value));
        coordinator.waitForResponse(reqId);

        return CommentDto.builder()
                .commentId(commentId)
                .createdBy(createdBy)
                .createdOn(createdOn.getTime())
                .value(value)
                .build();
    }


    @Override
    public void resetCommentId() {
        UUID reqId = ConcurrentUtil.get(submitter.submitCommentId(ActionType.RESET));
        coordinator.waitForResponse(reqId);
    }


    @Override
    public long nextContentId() {
        UUID uuid = ConcurrentUtil.get(submitter.submitContentId(ActionType.CREATE));
        return (long) coordinator.waitForResponse(uuid);
    }


    @Override
    public long nextGlobalId() {
        UUID uuid = ConcurrentUtil.get(submitter.submitGlobalId(ActionType.CREATE));
        return (long) coordinator.waitForResponse(uuid);
    }


    @Override
    public long nextCommentId() {
        UUID uuid = ConcurrentUtil.get(submitter.submitCommentId(ActionType.CREATE));
        return (long) coordinator.waitForResponse(uuid);
    }


    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        var contentDto = delegate.getArtifactByContentId(contentId);

        var uuid = ConcurrentUtil.get(submitter.submitContent(
                contentId, contentHash, ActionType.UPDATE,
                newCanonicalHash, contentDto.getContent(), SqlUtil.serializeReferences(contentDto.getReferences())
        ));
        coordinator.waitForResponse(uuid);
    }


    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version,
                                              String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public int order() {
        return 0;
    }
}
