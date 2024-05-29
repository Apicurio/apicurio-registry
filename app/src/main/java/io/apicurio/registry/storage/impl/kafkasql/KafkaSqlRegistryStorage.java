package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorReadOnlyBase;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.kafkasql.messages.*;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlSink;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.storage.importing.SqlDataImporter;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.impexp.ArtifactBranchEntity;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.CommentEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.kafka.KafkaUtil;
import io.apicurio.registry.utils.kafka.ProducerActions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of a registry artifactStore that extends the basic SQL artifactStore but federates 'write' operations
 * to other nodes in a cluster using a Kafka topic.  As a result, all reads are performed locally but all
 * writes are published to a topic for consumption by all nodes.
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@StorageMetricsApply
@Logged
public class KafkaSqlRegistryStorage extends RegistryStorageDecoratorReadOnlyBase implements RegistryStorage {

    @Inject
    Logger log;

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
    @Named("KafkaSqlJournalConsumer")
    KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> journalConsumer;

    @Inject
    @Named("KafkaSqlSnapshotsConsumer")
    KafkaConsumer<String, String> snapshotsConsumer;

    @Inject
    @Named("KafkaSqlSnapshotsProducer")
    ProducerActions<String, String> snapshotsProducer;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    Event<StorageEvent> storageEvent;

    private volatile boolean bootstrapped = false;
    private volatile boolean stopped = true;
    private volatile boolean snapshotProcessed = false;

    //The snapshot id used to determine if this replica must process a snapshot message
    private volatile String lastTriggeredSnapshot = null;

    @Override
    public String storageName() {
        return "kafkasql";
    }

    @Override
    public void initialize() {
        log.info("Using Kafka-SQL artifactStore.");

        //First, if needed create the Kafka topics.
        if (configuration.isTopicAutoCreate()) {
            autoCreateTopics();
        }

        //Try to restore the internal database from a snapshot
        final long bootstrapStart = System.currentTimeMillis();
        String snapshotId = consumeSnapshotsTopic(snapshotsConsumer);

        //Once the topics are created, and the snapshots processed, initialize the internal SQL Storage.
        sqlStore.initialize();
        setDelegate(sqlStore);

        //Once the SQL storage has been initialized, start the Kafka consumer thread.
        log.info("SQL store initialized, starting consumer thread.");
        startConsumerThread(journalConsumer, snapshotId, bootstrapStart);
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
        journalConsumer.close();
        snapshotsConsumer.close();
    }

    /**
     * Automatically create the Kafka topics.
     */
    private void autoCreateTopics() {
        Set<String> topicNames = Set.of(configuration.topic(), configuration.snapshotsTopic());
        Map<String, String> topicProperties = new HashMap<>();
        configuration.topicProperties().forEach((key, value) -> topicProperties.put(key.toString(), value.toString()));
        Properties adminProperties = configuration.adminProperties();
        adminProperties.putIfAbsent(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, configuration.bootstrapServers());
        try {
            KafkaUtil.createTopics(adminProperties, topicNames, topicProperties);
        }
        catch (TopicExistsException e) {
            log.info("Topic {} already exists, skipping.", configuration.topic());
        }
    }

    /**
     * Consume the snapshots topic, looking for the most recent snapshots in the topic. Once found, it restores the internal h2 database using the snapshot's content.
     * WARNING: This has the limitation of processing the first 500 snapshots, which should be enough for most deployments.
     */
    private String consumeSnapshotsTopic(KafkaConsumer<String, String> snapshotsConsumer) {
        // Subscribe to the snapshots topic
        Collection<String> topics = Collections.singleton(configuration.snapshotsTopic());
        snapshotsConsumer.subscribe(topics);
        ConsumerRecords<String, String> records = snapshotsConsumer.poll(Duration.ofMillis(configuration.pollTimeout()));
        List<ConsumerRecord<String, String>> snapshots = new ArrayList<>();
        String snapshotRecordKey = null;
        if (records != null && !records.isEmpty()) {
            //collect all snapshots into a list
            records.forEach(snapshots::add);

            //sort snapshots by timestamp
            snapshots.sort(Comparator.comparingLong(ConsumerRecord::timestamp));

            Path mostRecentSnapshotPath = null;
            for (ConsumerRecord<String, String> snapshotFound : snapshots) {
                //Restore database from snapshot
                try {
                    String path = snapshotFound.value();
                    if (null != path && !path.isBlank() && Files.exists(Path.of(snapshotFound.value()))) {
                        log.debug("Snapshot with path {} found.", snapshotFound.value());
                        snapshotRecordKey = snapshotFound.key();
                        mostRecentSnapshotPath = Path.of(snapshotFound.value());
                    }
                }
                catch (IllegalArgumentException ex) {
                    log.warn("Snapshot with path {} ignored, the snapshot is likely invalid or cannot be found", snapshotFound.value());
                }
            }

            //Here we have the most recent snapshot that we can find, try to restore the internal database from it.
            if (null != mostRecentSnapshotPath) {
                log.info("Restoring snapshot {} to the internal database...", mostRecentSnapshotPath);
                sqlStore.restoreFromSnapshot(mostRecentSnapshotPath.toString());
            }
        }

        return snapshotRecordKey;
    }

    /**
     * Start the KSQL Kafka consumer thread which is responsible for subscribing to the kafka topic,
     * consuming JournalRecord entries found on that topic, and applying those journal entries to
     * the internal data model.
     */
    private void startConsumerThread(final KafkaConsumer<KafkaSqlMessageKey, KafkaSqlMessage> consumer, String snapshotId, long bootstrapStart) {
        log.info("Starting KSQL consumer thread on topic: {}", configuration.topic());
        log.info("Bootstrap servers: {}", configuration.bootstrapServers());

        final String bootstrapId = UUID.randomUUID().toString();
        submitter.submitBootstrap(bootstrapId);

        Runnable runner = () -> {
            try (consumer) {
                log.info("Subscribing to {}", configuration.topic());
                // Subscribe to the journal topic
                Collection<String> topics = Collections.singleton(configuration.topic());
                consumer.subscribe(topics);

                // Main consumer loop
                while (!stopped) {
                    final ConsumerRecords<KafkaSqlMessageKey, KafkaSqlMessage> records = consumer.poll(Duration.ofMillis(configuration.pollTimeout()));

                    if (records != null && !records.isEmpty()) {
                        log.debug("Consuming {} journal records.", records.count());

                        if (null != snapshotId && !snapshotProcessed) {
                            //If there is a snapshot key present, we process (and discard) all the messages until we find the snapshot marker that corresponds to the snapshot key.
                            Iterator<ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage>> it = records.iterator();
                            while (it.hasNext() && !snapshotProcessed) {
                                ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = it.next();
                                if (processSnapshot(snapshotId, record)) {
                                    log.debug("Snapshot marker found {} the new messages will be applied on top of the snapshot data.", record.key());
                                    snapshotProcessed = true;
                                    break;
                                }
                                else {
                                    log.debug("Discarding message with key {} as it was sent before a newer snapshot was created.", record.key());
                                }
                            }

                            //If the snapshot marker has not been found, continue with message skipping until we find it.
                            if (snapshotProcessed) {
                                //Once the snapshot marker message has been found, we can process the rest of the messages as usual, applying the new changes on top of the existing ones in the snapshot.
                                while (it.hasNext()) {
                                    ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record = it.next();
                                    processRecord(record, bootstrapId, bootstrapStart);
                                }
                            }
                        }
                        else {
                            //If there is no snapshot, simply process the existing messages in the kafka topic as usual.
                            records.forEach(record -> processRecord(record, bootstrapId, bootstrapStart));
                        }
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

    private boolean processSnapshot(String snapshotId, ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record) {
        return record.value() instanceof CreateSnapshot1Message && snapshotId.equals(((CreateSnapshot1Message) record.value()).getSnapshotId());
    }

    private void processRecord(ConsumerRecord<KafkaSqlMessageKey, KafkaSqlMessage> record, String bootstrapId, long bootstrapStart) {
        // If the key is null, we couldn't deserialize the message
        if (record.key() == null) {
            log.warn("Discarded an unreadable/unrecognized Kafka message.");
            return;
        }

        // If the key is a Bootstrap key, then we have processed all messages and can set bootstrapped to 'true'
        if ("Bootstrap".equals(record.key().getMessageType())) {
            KafkaSqlMessageKey bkey = (KafkaSqlMessageKey) record.key();
            if (bkey.getUuid().equals(bootstrapId)) {
                this.bootstrapped = true;
                storageEvent.fireAsync(StorageEvent.builder()
                        .type(StorageEventType.READY)
                        .build());
                log.info("KafkaSQL storage bootstrapped in {} ms.", System.currentTimeMillis() - bootstrapStart);
            }
            return;
        }

        // If the key is a CreateSnapshotMessage key, but this replica does not have the snapshotId, it means that it wasn't triggered here, so just skip the message.
        if (record.value() instanceof CreateSnapshot1Message && !((CreateSnapshot1Message) record.value()).getSnapshotId().equals(lastTriggeredSnapshot)) {
            log.debug("Snapshot trigger message with id {} being skipped since this replica did not trigger the creation.",
                    ((CreateSnapshot1Message) record.value()).getSnapshotId());
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
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#setConfigProperty(io.apicurio.common.apps.config.DynamicConfigPropertyDto)
     */
    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        var message = new SetConfigProperty1Message(propertyDto);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.common.apps.config.DynamicConfigStorage#deleteConfigProperty(java.lang.String)
     */
    @Override
    public void deleteConfigProperty(String propertyName) {
        var message = new DeleteConfigProperty1Message(propertyName);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId, String artifactId,
                                                                                String artifactType, EditableArtifactMetaDataDto artifactMetaData, String version,
                                                                                ContentWrapperDto versionContent,
                                                                                EditableVersionMetaDataDto versionMetaData, List<String> versionBranches)
            throws RegistryStorageException {
        String content = versionContent != null ? versionContent.getContent().content() : null;
        String contentType = versionContent != null ? versionContent.getContentType() : null;
        List<ArtifactReferenceDto> references = versionContent != null ? versionContent.getReferences() : null;
        var message = new CreateArtifact8Message(groupId, artifactId, artifactType, artifactMetaData, version,
                contentType, content, references, versionMetaData, versionBranches);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto>) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        var message = new DeleteArtifact2Message(groupId, artifactId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (List<String>) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        var message = new DeleteArtifacts1Message(groupId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
                                                            String artifactType, ContentWrapperDto contentDto, EditableVersionMetaDataDto metaData, List<String> branches)
            throws RegistryStorageException {
        String content = contentDto != null ? contentDto.getContent().content() : null;
        String contentType = contentDto != null ? contentDto.getContentType() : null;
        List<ArtifactReferenceDto> references = contentDto != null ? contentDto.getReferences() : null;
        var message = new CreateArtifactVersion7Message(groupId, artifactId, version, artifactType, contentType,
                content, references, metaData, branches);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (ArtifactVersionMetaDataDto) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
                                       EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        var message = new UpdateArtifactMetaData3Message(groupId, artifactId, metaData);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        var message = new DeleteArtifactRules2Message(groupId, artifactId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        var message = new UpdateArtifactRule4Message(groupId, artifactId, rule, config);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        var message = new DeleteArtifactRule3Message(groupId, artifactId, rule);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        var message = new DeleteArtifactVersion3Message(groupId, artifactId, version);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableVersionMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableVersionMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        var message = new UpdateArtifactVersionMetaData4Message(groupId, artifactId, version, metaData);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        var message = new CreateGlobalRule2Message(rule, config);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        var message = new DeleteGlobalRules0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        var message = new UpdateGlobalRule2Message(rule, config);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        var message = new DeleteGlobalRule1Message(rule);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        var message = new CreateGroup1Message(group);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        var message = new DeleteGroup1Message(groupId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(java.lang.String, io.apicurio.registry.storage.dto.EditableGroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        var message = new UpdateGroupMetaData2Message(groupId, dto);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream, boolean, boolean)
     */
    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
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
            }
            catch (Exception e) {
                // Noop
            }
        });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createRoleMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
        var message = new CreateRoleMapping3Message(principalId, role, principalName);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateRoleMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        var message = new UpdateRoleMapping2Message(principalId, role);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteRoleMapping(java.lang.String)
     */
    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        var message = new DeleteRoleMapping1Message(principalId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteAllUserData()
     */
    @Override
    public void deleteAllUserData() {
        var message = new DeleteAllUserData0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createDownload(io.apicurio.registry.storage.dto.DownloadContextDto)
     */
    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        var message = new CreateDownload1Message(context);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (String) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#consumeDownload(java.lang.String)
     */
    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        var message = new ConsumeDownload1Message(downloadId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (DownloadContextDto) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteAllExpiredDownloads()
     */
    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        var message = new DeleteAllExpiredDownloads0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) {
        var message = new CreateArtifactVersionComment4Message(groupId, artifactId, version, value);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (CommentDto) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        var message = new DeleteArtifactVersionComment4Message(groupId, artifactId, version, commentId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) {
        var message = new UpdateArtifactVersionComment5Message(groupId, artifactId, version, commentId, value);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#resetGlobalId()
     */
    @Override
    public void resetGlobalId() {
        var message = new ResetGlobalId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#resetContentId()
     */
    @Override
    public void resetContentId() {
        var message = new ResetContentId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#resetCommentId()
     */
    @Override
    public void resetCommentId() {
        var message = new ResetCommentId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#nextContentId()
     */
    @Override
    public long nextContentId() {
        var message = new NextContentId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (long) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#nextGlobalId()
     */
    @Override
    public long nextGlobalId() {
        var message = new NextGlobalId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (long) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#nextCommentId()
     */
    @Override
    public long nextCommentId() {
        var message = new NextCommentId0Message();
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        return (long) coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importComment(io.apicurio.registry.utils.impexp.CommentEntity)
     */
    @Override
    public void importComment(CommentEntity entity) {
        var message = new ImportComment1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importGroup(io.apicurio.registry.utils.impexp.GroupEntity)
     */
    @Override
    public void importGroup(GroupEntity entity) {
        var message = new ImportGroup1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importGlobalRule(io.apicurio.registry.utils.impexp.GlobalRuleEntity)
     */
    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        var message = new ImportGlobalRule1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importContent(io.apicurio.registry.utils.impexp.ContentEntity)
     */
    @Override
    public void importContent(ContentEntity entity) {
        String content = ContentHandle.create(entity.contentBytes).content();
        var message = new ImportContent1Message(entity, content);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importArtifactVersion(io.apicurio.registry.utils.impexp.ArtifactVersionEntity)
     */
    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        var message = new ImportArtifactVersion1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importArtifactRule(io.apicurio.registry.utils.impexp.ArtifactRuleEntity)
     */
    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        var message = new ImportArtifactRule1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importArtifactBranch(io.apicurio.registry.utils.impexp.ArtifactBranchEntity)
     */
    @Override
    public void importArtifactBranch(ArtifactBranchEntity entity) {
        var message = new ImportArtifactBranch1Message(entity);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateContentCanonicalHash(java.lang.String, long, java.lang.String)
     */
    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        var message = new UpdateContentCanonicalHash3Message(newCanonicalHash, contentId, contentHash);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createOrUpdateArtifactBranch(io.apicurio.registry.model.GAV, io.apicurio.registry.model.BranchId)
     */
    @Override
    public void createOrUpdateArtifactBranch(GAV gav, BranchId branchId) {
        var message = new CreateOrUpdateArtifactBranch2Message(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), branchId.getRawBranchId());
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createOrReplaceArtifactBranch(io.apicurio.registry.model.GA, io.apicurio.registry.model.BranchId, java.util.List)
     */
    @Override
    public void createOrReplaceArtifactBranch(GA ga, BranchId branchId, List<VersionId> versions) {
        List<String> rawVersions = versions == null ? List.of() : versions.stream().map(v -> v.getRawVersionId()).collect(Collectors.toList());
        var message = new CreateOrReplaceArtifactBranch3Message(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(),
                branchId.getRawBranchId(), rawVersions);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactBranch(io.apicurio.registry.model.GA, io.apicurio.registry.model.BranchId)
     */
    @Override
    public void deleteArtifactBranch(GA ga, BranchId branchId) {
        var message = new DeleteArtifactBranch2Message(ga.getRawGroupIdWithNull(), ga.getRawArtifactId(), branchId.getRawBranchId());
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        coordinator.waitForResponse(uuid);
    }

    @Override
    public String triggerSnapshotCreation() throws RegistryStorageException {
        //First we generate an identifier for the snapshot, then we send a snapshot marker to the journal topic.
        String snapshotId = UUID.randomUUID().toString();
        Path path = Path.of(configuration.snapshotLocation(), snapshotId + ".sql");
        var message = new CreateSnapshot1Message(path.toString(), snapshotId);
        this.lastTriggeredSnapshot = snapshotId;
        log.debug("Snapshot with id {} triggered.", snapshotId);
        var uuid = ConcurrentUtil.get(submitter.submitMessage(message));
        String snapshotLocation = (String) coordinator.waitForResponse(uuid);
        //Then we send a new message to the snapshots topic, using the snapshot id as the key of the snapshot message.
        ProducerRecord<String, String> record = new ProducerRecord<>(configuration.snapshotsTopic(), 0, snapshotId, snapshotLocation,
                Collections.emptyList());
        RecordMetadata recordMetadata = ConcurrentUtil.get(snapshotsProducer.apply(record));
        return snapshotLocation;
    }

    @Override
    public String createSnapshot(String snapshotLocation) throws RegistryStorageException {
        throw new IllegalStateException("Directly creating a snapshot is not supported in Kafkasql");
    }
}
