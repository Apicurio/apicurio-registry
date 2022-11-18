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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RoleMappingNotFoundException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionAlreadyExistsException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.keys.BootstrapKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlSink;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlStore;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.DataImporter;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.quarkus.security.identity.SecurityIdentity;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
public class KafkaSqlRegistryStorage extends AbstractRegistryStorage {

    @Inject
    Logger log;

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlSink kafkaSqlSink;

    @Inject
    KafkaSqlStore sqlStore;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    TenantContext tenantContext;

    @Inject
    KafkaConsumer<MessageKey, MessageValue> consumer;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ArtifactStateExt artifactStateEx;

    @Inject
    KafkaSqlUpgrader upgrader;

    private boolean bootstrapped = false;
    private boolean stopped = true;

    @PostConstruct
    void onConstruct() {
        log.info("Using Kafka-SQL artifactStore.");

        // Create Kafka topics if needed
        if (configuration.isTopicAutoCreate()) {
            autoCreateTopics();
        }

        // Start the Kafka Consumer thread
        startConsumerThread(consumer);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "kafkasql";
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#supportsMultiTenancy()
     */
    @Override
    public boolean supportsMultiTenancy() {
        return true;
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractRegistryStorage#isReady()
     */
    @Override
    public boolean isReady() {
        return bootstrapped;
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractRegistryStorage#isAlive()
     */
    @Override
    public boolean isAlive() {
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
        configuration.topicProperties().entrySet().forEach(entry -> topicProperties.put(entry.getKey().toString(), entry.getValue().toString()));
        // Use log compaction by default.
        topicProperties.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
        Properties adminProperties = configuration.adminProperties();
        adminProperties.putIfAbsent(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
        try {
            KafkaUtil.createTopics(adminProperties, topicNames, topicProperties);
        } catch (TopicExistsException e) {
            log.info("Topic already exists, skipping");
        }
    }

    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic,
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     * @param consumer
     */
    private void startConsumerThread(final KafkaConsumer<MessageKey, MessageValue> consumer) {
        log.info("Starting KSQL consumer thread on topic: {}", configuration.topic());
        log.info("Bootstrap servers: " + configuration.bootstrapServers());

        final String bootstrapId = UUID.randomUUID().toString();
        submitter.submitBootstrap(bootstrapId);
        final long bootstrapStart = System.currentTimeMillis();

        Runnable runner = () -> {
            log.info("KSQL consumer thread startup lag: {}", configuration.startupLag());

            try {
                // Startup lag
                try { Thread.sleep(configuration.startupLag()); } catch (InterruptedException e) { }

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
                                    log.info("KafkaSQL storage bootstrapped in " + (System.currentTimeMillis() - bootstrapStart) + "ms.");
                                    upgrader.upgrade();
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
     * Generate a new globalId.  This must be done by sending a message to Kafka so that all nodes in the cluster are
     * guaranteed to generate the same globalId.
     *
     * TODO we can improve performance of this by reserving batches of globalIds instead of doing it one at a time.  Not yet done
     *      due to a desire to avoid premature optimization.
     */
    private long nextClusterGlobalId() {
        UUID uuid = ConcurrentUtil.get(submitter.submitGlobalId(tenantContext.tenantId(), ActionType.CREATE));
        return (long) coordinator.waitForResponse(uuid);
    }

    /**
     * Generate a new contentId.  This must be done by sending a message to Kafka so that all nodes in the cluster are
     * guaranteed to generate the same contentId.
     *
     * TODO we can improve performance of this by reserving batches of contentIds instead of doing it one at a time.  Not yet done
     *      due to a desire to avoid premature optimization.
     */
    private long nextClusterContentId() {
        UUID uuid = ConcurrentUtil.get(submitter.submitContentId(tenantContext.tenantId(), ActionType.CREATE));
        return (long) coordinator.waitForResponse(uuid);
    }

    /**
     * Ensures that the given content exists in the database.  If it's already in the DB, then this just
     * returns the content hash.  If the content does not yet exist in the DB, then it is added (by sending
     * the appropriate message to the Kafka topic and awaiting the response).
     *
     * @param content
     * @param groupId
     * @param artifactId
     * @param artifactType
     */
    private String ensureContent(ContentHandle content, String groupId, String artifactId, String artifactType, List<ArtifactReferenceDto> references) {
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);

        if (!sqlStore.isContentExists(contentHash)) {
            long contentId = nextClusterContentId();

            ContentHandle canonicalContent = this.canonicalizeContent(artifactType, content, references);
            byte[] canonicalContentBytes = canonicalContent.bytes();
            String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

            CompletableFuture<UUID> future = submitter.submitContent(tenantContext.tenantId(), contentId, contentHash, ActionType.CREATE, canonicalContentHash, content, SqlUtil.serializeReferences(references));
            UUID uuid = ConcurrentUtil.get(future);
            coordinator.waitForResponse(uuid);
        }

        return contentHash;
    }

    protected long ensureContentAndGetContentId(ContentHandle content, String canonicalContentHash, List<ArtifactReferenceDto> references) {
        byte[] contentBytes = content.bytes();
        String contentHash = DigestUtils.sha256Hex(contentBytes);

        if (!sqlStore.isContentExists(contentHash)) {
            long contentId = nextClusterContentId();

            CompletableFuture<UUID> future = submitter.submitContent(tenantContext.tenantId(), contentId, contentHash, ActionType.CREATE, canonicalContentHash, content, SqlUtil.serializeReferences(references));
            UUID uuid = ConcurrentUtil.get(future);
            coordinator.waitForResponse(uuid);
            return contentId;
        }

        return sqlStore.contentIdFromHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, RegistryStorageException {
        if (sqlStore.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }

        String contentHash = ensureContent(content, groupId, artifactId, artifactType, references);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
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

        long globalId = nextClusterGlobalId();

        UUID uuid = ConcurrentUtil.get(
                submitter.submitArtifact(tenantContext.tenantId(), groupId, artifactId, version, ActionType.CREATE,
                        globalId, artifactType, contentHash, createdBy, createdOn, metaData));
        return (ArtifactMetaDataDto) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifact(tenantContext.tenantId(), groupId, artifactId, ActionType.DELETE));
        List<String> versionIds = (List<String>) coordinator.waitForResponse(reqId);

        // Add tombstone messages for all version metda-data updates
        versionIds.forEach(vid -> {
            submitter.submitArtifactVersionTombstone(tenantContext.tenantId(), groupId, artifactId, vid);
        });

        // Add tombstone messages for all artifact rules
        RuleType[] ruleTypes = RuleType.values();
        for (RuleType ruleType : ruleTypes) {
            submitter.submitArtifactRuleTombstone(tenantContext.tenantId(), groupId, artifactId, ruleType);
        }

        return versionIds;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(tenantContext.tenantId(), groupId, ActionType.DELETE, true));
        coordinator.waitForResponse(reqId);

        // TODO could possibly add tombstone messages for *all* artifacts that were deleted (version meta-data and artifact rules)
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifact(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public ContentWrapperDto getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactByContentId(contentId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentHash(java.lang.String)
     */
    @Override
    public ContentWrapperDto getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactByContentHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, null, references);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto, java.util.List)
     */
    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        if (version != null && sqlStore.isArtifactVersionExists(groupId, artifactId, version)) {
            throw new VersionAlreadyExistsException(groupId, artifactId, version);
        }

        String contentHash = ensureContent(content, groupId, artifactId, artifactType, references);
        String createdBy = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        if (metaData == null) {
            metaData = extractMetaData(artifactType, content);
        }

        long globalId = nextClusterGlobalId();

        UUID reqId = ConcurrentUtil.get(
                submitter.submitArtifact(tenantContext.tenantId(), groupId, artifactId, version, ActionType.UPDATE,
                        globalId, artifactType, contentHash, createdBy, createdOn, metaData));
        return (ArtifactMetaDataDto) coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return sqlStore.getArtifactIds(limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.util.Set, io.apicurio.registry.storage.dto.OrderBy, io.apicurio.registry.storage.dto.OrderDirection, int, int)
     */
    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, int offset, int limit) {
        return sqlStore.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactMetaData(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersionMetaData(groupId, artifactId, canonical, content);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactMetaData(id);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
        ArtifactMetaDataDto metaDataDto = sqlStore.getArtifactMetaData(groupId, artifactId);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(tenantContext.tenantId(), groupId, artifactId, metaDataDto.getVersion(),
                ActionType.UPDATE, metaDataDto.getState(), metaData));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactOwner(String, String, ArtifactOwnerDto)
     */
    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) throws ArtifactNotFoundException, RegistryStorageException {
        // Note: the next line will throw ArtifactNotFoundException if the artifact does not exist, so there is no need for an extra check.
        ArtifactMetaDataDto metaDataDto = sqlStore.getArtifactMetaData(groupId, artifactId);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactOwner(tenantContext.tenantId(), groupId, artifactId, ActionType.UPDATE, owner.getOwner()));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactRules(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        if (sqlStore.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleAlreadyExistsException(rule);
        }

        UUID reqId = ConcurrentUtil.get(
                submitter.submitArtifactRule(tenantContext.tenantId(), groupId, artifactId, rule, ActionType.CREATE, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactExists(groupId, artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        submitter.submitArtifactRule(tenantContext.tenantId(), groupId, artifactId, RuleType.COMPATIBILITY, ActionType.DELETE);

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(tenantContext.tenantId(), groupId, artifactId, RuleType.VALIDITY, ActionType.DELETE));
        try {
            coordinator.waitForResponse(reqId);
        } catch (RuleNotFoundException e) {
            // Eat this exception - we don't care if the rule didn't exist.
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException,
            RuleNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(tenantContext.tenantId(), groupId, artifactId, rule, ActionType.UPDATE, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException,
            RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isArtifactRuleExists(groupId, artifactId, rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitArtifactRule(tenantContext.tenantId(), groupId, artifactId, rule, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersions(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, java.lang.String, int, int)
     */
    @Override
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit)
            throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.searchVersions(groupId, artifactId, offset, limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersion(id);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException,
            VersionNotFoundException, RegistryStorageException {
        handleVersion(groupId, artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(tenantContext.tenantId(), groupId, artifactId, version, ActionType.DELETE));
            coordinator.waitForResponse(reqId);

            // Add a tombstone message for this version's metadata
            submitter.submitArtifactVersionTombstone(tenantContext.tenantId(), groupId, artifactId, version);

            return null;
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(groupId, artifactId, version, ArtifactStateExt.ACTIVE_STATES, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(tenantContext.tenantId(), groupId, artifactId,
                    version, ActionType.UPDATE, value.getState(), metaData));
            return coordinator.waitForResponse(reqId);
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        handleVersion(groupId, artifactId, version, null, value -> {
            UUID reqId = ConcurrentUtil.get(submitter.submitVersion(tenantContext.tenantId(), groupId, artifactId, version, ActionType.CLEAR));
            return coordinator.waitForResponse(reqId);
        });
    }

    /**
     * Fetches the meta data for the given artifact version, validates the state (optionally), and then calls back the handler
     * with the metadata.  If the artifact is not found, this will throw an exception.
     * @param groupId
     * @param artifactId
     * @param version
     * @param states
     * @param handler
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    private <T> T handleVersion(String groupId, String artifactId, String version, EnumSet<ArtifactState> states, Function<ArtifactVersionMetaDataDto, T> handler)
            throws ArtifactNotFoundException, RegistryStorageException {

        ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);

        ArtifactState state = metadata.getState();
        artifactStateEx.validateState(states, state, groupId, artifactId, version);
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
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(tenantContext.tenantId(), rule, ActionType.CREATE, config));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        // TODO This should use "DELETE FROM" instead of being rule specific
        submitter.submitGlobalRule(tenantContext.tenantId(), RuleType.COMPATIBILITY, ActionType.DELETE);

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(tenantContext.tenantId(), RuleType.VALIDITY, ActionType.DELETE));
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
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        if (!sqlStore.isGlobalRuleExists(rule)) {
            throw new RuleNotFoundException(rule);
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(tenantContext.tenantId(), rule, ActionType.UPDATE, config));
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

        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalRule(tenantContext.tenantId(), rule, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }

    private void updateArtifactState(ArtifactState currentState, String groupId, String artifactId, String version, ArtifactState newState, EditableArtifactMetaDataDto metaData) {
        artifactStateEx.applyState(
            s ->  {
                UUID reqId = ConcurrentUtil.get(submitter.submitArtifactVersion(tenantContext.tenantId(), groupId, artifactId,
                        version, ActionType.UPDATE, newState, metaData));
                coordinator.waitForResponse(reqId);
            },
            currentState,
            newState
        );
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto metadata = sqlStore.getArtifactMetaData(groupId, artifactId);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), groupId, artifactId, metadata.getVersion(), state, metaDataDto);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        ArtifactVersionMetaDataDto metadata = sqlStore.getArtifactVersionMetaData(groupId, artifactId, version);
        EditableArtifactMetaDataDto metaDataDto = new EditableArtifactMetaDataDto();
        metaDataDto.setName(metadata.getName());
        metaDataDto.setDescription(metadata.getDescription());
        metaDataDto.setLabels(metadata.getLabels());
        metaDataDto.setProperties(metadata.getProperties());
        updateArtifactState(metadata.getState(), groupId, artifactId, version, state, metaDataDto);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getLogConfiguration(java.lang.String)
     */
    @Override
    public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        return this.sqlStore.getLogConfiguration(logger);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#listLogConfigurations()
     */
    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        return this.sqlStore.listLogConfigurations();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#removeLogConfiguration(java.lang.String)
     */
    @Override
    public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        LogConfigurationDto dto = new LogConfigurationDto();
        dto.setLogger(logger);
        UUID reqId = ConcurrentUtil.get(submitter.submitLogConfig(tenantContext.tenantId(), ActionType.DELETE, dto));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#setLogConfiguration(io.apicurio.registry.storage.dto.LogConfigurationDto)
     */
    @Override
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitLogConfig(tenantContext.tenantId(), ActionType.UPDATE, logConfiguration));
        coordinator.waitForResponse(reqId);
    }

    protected EditableArtifactMetaDataDto extractMetaData(String artifactType, ContentHandle content) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        ExtractedMetaData emd = extractor.extract(content);
        EditableArtifactMetaDataDto metaData;
        if (emd != null) {
            metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());
        } else {
            metaData = new EditableArtifactMetaDataDto();
        }
        return metaData;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(tenantContext.tenantId(), ActionType.CREATE, group));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(tenantContext.tenantId(), ActionType.UPDATE, group));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGroup(tenantContext.tenantId(), groupId, ActionType.DELETE, false));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupIds(java.lang.Integer)
     */
    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return sqlStore.getGroupIds(limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupMetaData(java.lang.String)
     */
    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        return sqlStore.getGroupMetaData(groupId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionsByContentId(long)
     */
    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return sqlStore.getArtifactVersionsByContentId(contentId);
    }

    @Override
    public List<Long> getArtifactContentIds(String groupId, String artifactId) {
        return sqlStore.getArtifactContentIds(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#exportData(Function)
     */
    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        sqlStore.exportData(handler);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream, boolean, boolean)
     */
    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) throws RegistryStorageException {
        try {
            DataImporter dataImporter;
            if (preserveContentId) {
                dataImporter = new KafkaSqlDataImporter(log, this, preserveGlobalId);
            } else {
                dataImporter = new ContentIdNotPreserveKafkaSqlDataImporter(log, this, preserveGlobalId);
            }

            Entity entity = null;
            while ((entity = entities.nextEntity()) != null) {
                dataImporter.importEntity(entity);
            }

            // Because importing just pushes a bunch of Kafka messages, we may need to
            // wait for a few seconds before we send the reset messages.  Due to partitioning,
            // we can't guarantee ordering of these next two messages, and we NEED them to
            // be consumed after all the import messages.
            try { Thread.sleep(2000); } catch (Exception e) {}

            // Make sure the contentId sequence is set high enough
            resetContentId();

            // Make sure the globalId sequence is set high enough
            resetGlobalId();
        } catch (IOException e) {
            throw new RegistryStorageException("Failed to import data", e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#countArtifacts()
     */
    @Override
    public long countArtifacts() throws RegistryStorageException {
        return sqlStore.countArtifacts();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#countArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return sqlStore.countArtifactVersions(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#countTotalArtifactVersions()
     */
    @Override
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return sqlStore.countTotalArtifactVersions();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createRoleMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(tenantContext.tenantId(), principalId, ActionType.CREATE, role, principalName));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteRoleMapping(java.lang.String)
     */
    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        if (!sqlStore.isRoleMappingExists(principalId)) {
            throw new RoleMappingNotFoundException();
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(tenantContext.tenantId(), principalId, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleMappings()
     */
    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        return sqlStore.getRoleMappings();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleMapping(java.lang.String)
     */
    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return sqlStore.getRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleForPrincipal(java.lang.String)
     */
    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return sqlStore.getRoleForPrincipal(principalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateRoleMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        if (!sqlStore.isRoleMappingExists(principalId)) {
            throw new RoleMappingNotFoundException();
        }

        UUID reqId = ConcurrentUtil.get(submitter.submitRoleMapping(tenantContext.tenantId(), principalId, ActionType.UPDATE, role, null));
        coordinator.waitForResponse(reqId);
    }

    @Override
    public void deleteAllUserData() throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalAction(tenantContext.tenantId(),  ActionType.DELETE_ALL_USER_DATA));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createDownload(io.apicurio.registry.storage.dto.DownloadContextDto)
     */
    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        String downloadId = UUID.randomUUID().toString();
        UUID reqId = ConcurrentUtil.get(submitter.submitDownload(tenantContext.tenantId(), downloadId, ActionType.CREATE, context));
        return (String) coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#consumeDownload(java.lang.String)
     */
    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        UUID reqId = ConcurrentUtil.get(submitter.submitDownload(tenantContext.tenantId(), downloadId, ActionType.DELETE));
        return (DownloadContextDto) coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteAllExpiredDownloads()
     */
    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        // Note: this is OK to do because the only caller of this method is the DownloadReaper, which
        // runs on every node in the cluster.
        sqlStore.deleteAllExpiredDownloads();
    }

    @Override
    public Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references) {
        return sqlStore.resolveReferences(references);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#getConfigProperties()
     */
    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() {
        return sqlStore.getConfigProperties();
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#getConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        return sqlStore.getConfigProperty(propertyName);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRawConfigProperty(java.lang.String)
     */
    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        return sqlStore.getRawConfigProperty(propertyName);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getTenantsWithStaleConfigProperties(java.time.Instant)
     */
    @Override
    public List<String> getTenantsWithStaleConfigProperties(Instant since) {
        return sqlStore.getTenantsWithStaleConfigProperties(since);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#setConfigProperty(io.apicurio.common.apps.config.DynamicConfigPropertyDto)
     */
    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        UUID reqId = ConcurrentUtil.get(submitter.submitConfigProperty(tenantContext.tenantId(), propertyDto.getName(), ActionType.UPDATE, propertyDto.getValue()));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#deleteConfigProperty(java.lang.String)
     */
    @Override
    public void deleteConfigProperty(String propertyName) {
        UUID reqId = ConcurrentUtil.get(submitter.submitConfigProperty(tenantContext.tenantId(), propertyName, ActionType.DELETE));
        coordinator.waitForResponse(reqId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#isArtifactExists(String, String)
     */
    @Override
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return sqlStore.isArtifactExists(groupId, artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#isGroupExists(String)
     */
    @Override
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return sqlStore.isGroupExists(groupId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getContentIdsReferencingArtifact(String, String, String)
     */
    @Override
    public List<Long> getContentIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return sqlStore.getContentIdsReferencingArtifact(groupId, artifactId, version);
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return sqlStore.getGlobalIdsReferencingArtifact(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#isArtifactVersionExists(String, String, String)
     */
    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) throws RegistryStorageException {
        return sqlStore.isArtifactVersionExists(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchGroups(Set, OrderBy, OrderDirection, Integer, Integer)
     */
    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit) {
        return sqlStore.searchGroups(filters, orderBy, orderDirection, offset, limit);
    }

    protected void importEntity(Entity entity) throws RegistryStorageException {
        switch (entity.getEntityType()) {
            case ArtifactRule:
                importArtifactRule((ArtifactRuleEntity) entity);
                break;
            case ArtifactVersion:
                importArtifactVersion((ArtifactVersionEntity) entity);
                break;
            case Content:
                importContent((ContentEntity) entity);
                break;
            case GlobalRule:
                importGlobalRule((GlobalRuleEntity) entity);
                break;
            case Group:
                importGroup((GroupEntity) entity);
                break;
            case Manifest:
                ManifestEntity manifest = (ManifestEntity) entity;
                log.info("---------- Import Info ----------");
                log.info("System Name:    {}", manifest.systemName);
                log.info("System Desc:    {}", manifest.systemDescription);
                log.info("System Version: {}", manifest.systemVersion);
                log.info("Data exported on {} by user {}", manifest.exportedOn, manifest.exportedBy);
                log.info("---------- ----------- ----------");
                // Ignore the manifest for now.
                break;
            default:
                throw new RegistryStorageException("Unhandled entity type during import: " + entity.getEntityType());
        }
    }
    protected void importArtifactRule(ArtifactRuleEntity entity) {
        RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
        submitter.submitArtifactRule(tenantContext.tenantId(), entity.groupId, entity.artifactId, entity.type, ActionType.IMPORT, config);
    }
    protected void importArtifactVersion(ArtifactVersionEntity entity) {
        EditableArtifactMetaDataDto metaData = EditableArtifactMetaDataDto.builder()
                .name(entity.name)
                .description(entity.description)
                .labels(entity.labels)
                .properties(entity.properties)
                .build();
        submitter.submitArtifact(tenantContext.tenantId(), entity.groupId, entity.artifactId, entity.version, ActionType.IMPORT,
                entity.globalId, entity.artifactType, null, entity.createdBy, new Date(entity.createdOn), metaData, entity.versionId,
                entity.state, entity.contentId, entity.isLatest);
    }
    protected void importContent(ContentEntity entity) {
        submitter.submitContent(tenantContext.tenantId(), entity.contentId, entity.contentHash, ActionType.IMPORT, entity.canonicalHash, ContentHandle.create(entity.contentBytes), entity.serializedReferences);
    }
    protected void importGlobalRule(GlobalRuleEntity entity) {
        RuleConfigurationDto config = new RuleConfigurationDto(entity.configuration);
        submitter.submitGlobalRule(tenantContext.tenantId(), entity.ruleType, ActionType.IMPORT, config);
    }
    protected void importGroup(GroupEntity entity) {
        GroupEntity e = entity;
        GroupMetaDataDto group = new GroupMetaDataDto();
        group.setArtifactsType(e.artifactsType);
        group.setCreatedBy(e.createdBy);
        group.setCreatedOn(e.createdOn);
        group.setDescription(e.description);
        group.setGroupId(e.groupId);
        group.setModifiedBy(e.modifiedBy);
        group.setModifiedOn(e.modifiedOn);
        group.setProperties(e.properties);
        submitter.submitGroup(tenantContext.tenantId(), ActionType.IMPORT, group);
    }
    private void resetContentId() {
        UUID reqId = ConcurrentUtil.get(submitter.submitGlobalId(tenantContext.tenantId(), ActionType.RESET));
        coordinator.waitForResponse(reqId);
    }
    private void resetGlobalId() {
        UUID reqId = ConcurrentUtil.get(submitter.submitContentId(tenantContext.tenantId(), ActionType.RESET));
        coordinator.waitForResponse(reqId);
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     * @param artifactType
     * @param content
     */
    protected ContentHandle canonicalizeContent(String artifactType, ContentHandle content) {
        return canonicalizeContent(artifactType, content, Collections.emptyList());
    }

    /**
     * Canonicalize the given content, returns the content unchanged in the case of an error.
     * @param artifactType
     * @param content
     */
    protected ContentHandle canonicalizeContent(String artifactType, ContentHandle content, List<ArtifactReferenceDto> references) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content, this.resolveReferences(references));
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType);
            return content;
        }
    }

}
