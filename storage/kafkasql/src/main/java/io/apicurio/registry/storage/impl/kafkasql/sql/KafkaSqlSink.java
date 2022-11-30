package io.apicurio.registry.storage.impl.kafkasql.sql;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;

import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactOwnerKey;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactOwnerValue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.mt.RegistryTenantContext;
import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.mt.TenantContextLoader;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactVersionKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ConfigPropertyKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ContentIdKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ContentKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.DownloadKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GlobalActionKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GlobalIdKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GlobalRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GroupKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.LogConfigKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.RoleMappingKey;
import io.apicurio.registry.storage.impl.kafkasql.values.AbstractMessageValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactRuleValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactVersionValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ConfigPropertyValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentIdValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.DownloadValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GlobalActionValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GlobalIdValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GlobalRuleValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GroupValue;
import io.apicurio.registry.storage.impl.kafkasql.values.LogConfigValue;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.storage.impl.kafkasql.values.RoleMappingValue;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Logged
public class KafkaSqlSink {

    @Inject
    Logger log;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    KafkaSqlStore sqlStore;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    TenantContext tenantContext;

    @Inject
    TenantContextLoader tcl;

    /**
     * Called by the {@link KafkaSqlRegistryStorage} main Kafka consumer loop to process a single
     * message in the topic.  Each message represents some attempt to modify the registry data.  So
     * each message much be consumed and applied to the in-memory SQL data store.
     *
     * This method extracts the UUID from the message headers, delegates the message processing
     * to <code>doProcessMessage()</code>, and handles any exceptions that might occur. Finally
     * it will report the result to any local threads that may be waiting (via the coordinator).
     *
     * @param record
     */
    @ActivateRequestContext
    public void processMessage(ConsumerRecord<MessageKey, MessageValue> record) {
        UUID requestId = extractUuid(record);
        log.debug("Processing Kafka message with UUID: {}", requestId);

        try {
            Object result = doProcessMessage(record);
            log.trace("Processed message key: {} value: {} result: {}", record.key().getType().name(), record.value() != null ? record.value().toString() : "", result != null ? result.toString() : "");
            log.debug("Kafka message successfully processed. Notifying listeners of response.");
            coordinator.notifyResponse(requestId, result);
        } catch (RegistryException e) {
            log.debug("Registry exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, e);
        } catch (Throwable e) {
            log.debug("Unexpected exception detected: {}", e.getMessage());
            coordinator.notifyResponse(requestId, new RegistryException(e));
        }
    }

    /**
     * Extracts the UUID from the message.  The UUID should be found in a message header.
     * @param record
     */
    private UUID extractUuid(ConsumerRecord<MessageKey, MessageValue> record) {
        return Optional.ofNullable(record.headers().headers("req"))
                .map(Iterable::iterator)
                .map(it -> {
                    return it.hasNext() ? it.next() : null;
                })
                .map(Header::value)
                .map(String::new)
                .map(UUID::fromString)
                .orElse(null);
    }

    /**
     * Process the message and return a result.  This method may also throw an exception if something
     * goes wrong.
     * @param record
     */
    private Object doProcessMessage(ConsumerRecord<MessageKey, MessageValue> record) {
        MessageKey key = record.key();
        MessageValue value = record.value();

        String tenantId = key.getTenantId();
        if (tenantId != null) {
            RegistryTenantContext tctx = tcl.loadBatchJobContext(tenantId);
            tenantContext.setContext(tctx);
        }
        try {
            MessageType messageType = key.getType();
            switch (messageType) {
                case Group:
                    return processGroupMessage((GroupKey) key, (GroupValue) value);
                case Artifact:
                    return processArtifactMessage((ArtifactKey) key, (ArtifactValue) value);
                case ArtifactRule:
                    return processArtifactRuleMessage((ArtifactRuleKey) key, (ArtifactRuleValue) value);
                case ArtifactVersion:
                    return processArtifactVersion((ArtifactVersionKey) key, (ArtifactVersionValue) value);
                case Content:
                    return processContent((ContentKey) key, (ContentValue) value);
                case GlobalRule:
                    return processGlobalRule((GlobalRuleKey) key, (GlobalRuleValue) value);
                case GlobalId:
                    return processGlobalId((GlobalIdKey) key, (GlobalIdValue) value);
                case ContentId:
                    return processContentId((ContentIdKey) key, (ContentIdValue) value);
                case LogConfig:
                    return processLogConfig((LogConfigKey) key, (LogConfigValue) value);
                case RoleMapping:
                    return processRoleMapping((RoleMappingKey) key, (RoleMappingValue) value);
                case GlobalAction:
                    return processGlobalAction((GlobalActionKey) key, (GlobalActionValue) value);
                case Download:
                    return processDownload((DownloadKey) key, (DownloadValue) value);
                case ConfigProperty:
                    return processConfigProperty((ConfigPropertyKey) key, (ConfigPropertyValue) value);
                case ArtifactOwner:
                    return processArtifactOwnerMessage((ArtifactOwnerKey) key, (ArtifactOwnerValue) value);
                default:
                    log.warn("Unrecognized message type: {}", record.key());
                    throw new RegistryStorageException("Unexpected message type: " + messageType.name());
            }
        } finally {
            log.debug("Clearing tenant id after message processed");
            tenantContext.clearContext();
        }
    }

    /**
     * Process a Kafka message of type "globalaction".
     * @param key
     * @param value
     */
    private Object processGlobalAction(GlobalActionKey key, GlobalActionValue value) {
        switch (value.getAction()) {
            case DELETE_ALL_USER_DATA:
                sqlStore.deleteAllUserData();
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "download".
     * @param key
     * @param value
     */
    private Object processDownload(DownloadKey key, DownloadValue value) {
        switch (value.getAction()) {
            case CREATE:
                return sqlStore.createDownload(value.getDownloadContext());
            case DELETE:
                return sqlStore.consumeDownload(key.getDownloadId());
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "configProperty".
     * @param key
     * @param value
     */
    private Object processConfigProperty(ConfigPropertyKey key, ConfigPropertyValue value) {
        switch (value.getAction()) {
            case UPDATE:
                DynamicConfigPropertyDto dto = new DynamicConfigPropertyDto(key.getPropertyName(), value.getValue());
                sqlStore.setConfigProperty(dto);
                return null;
            case DELETE:
                sqlStore.deleteConfigProperty(key.getPropertyName());
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "group".  This includes creating, updating, and deleting groups.
     * @param key
     * @param value
     */
    private Object processGroupMessage(GroupKey key, GroupValue value) {
        Supplier<GroupMetaDataDto> buildGroup = () -> {
            return GroupMetaDataDto.builder()
                    .groupId(key.getGroupId())
                    .description(value.getDescription())
                    .artifactsType(value.getArtifactsType())
                    .createdBy(value.getCreatedBy())
                    .createdOn(value.getCreatedOn())
                    .modifiedBy(value.getModifiedBy())
                    .modifiedOn(value.getModifiedOn())
                    .properties(value.getProperties())
                    .build();
        };
        switch (value.getAction()) {
            case CREATE:
                sqlStore.createGroup(buildGroup.get());
                return null;
            case UPDATE:
                sqlStore.updateGroupMetaData(buildGroup.get());
                return null;
            case DELETE:
                if (value.isOnlyArtifacts()) {
                    sqlStore.deleteArtifacts(key.getGroupId());
                } else {
                    sqlStore.deleteGroup(key.getGroupId());
                }
                return null;
            case IMPORT:
                GroupEntity entity = new GroupEntity();
                entity.artifactsType = value.getArtifactsType();
                entity.createdBy = value.getCreatedBy();
                entity.createdOn = value.getCreatedOn();
                entity.description = value.getDescription();
                entity.groupId = key.getGroupId();
                entity.modifiedBy = value.getModifiedBy();
                entity.modifiedOn = value.getModifiedOn();
                entity.properties = value.getProperties();
                sqlStore.importGroup(entity);
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "artifact".  This includes creating, updating, and deleting artifacts.
     * @param key
     * @param value
     */
    private Object processArtifactMessage(ArtifactKey key, ArtifactValue value) throws RegistryStorageException {
        try {
            GlobalIdGenerator globalIdGenerator = new GlobalIdGenerator() {
                @Override
                public Long generate() {
                    return value.getGlobalId();
                }
            };
            switch (value.getAction()) {
                case CREATE:
                    return sqlStore.createArtifactWithMetadata(key.getGroupId(), key.getArtifactId(), value.getVersion(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), globalIdGenerator);
                case UPDATE:
                    return sqlStore.updateArtifactWithMetadata(key.getGroupId(), key.getArtifactId(), value.getVersion(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), globalIdGenerator);
                case DELETE:
                    return sqlStore.deleteArtifact(key.getGroupId(), key.getArtifactId());
                case IMPORT:
                    ArtifactVersionEntity entity = new ArtifactVersionEntity();
                    entity.globalId = value.getGlobalId();
                    entity.groupId = key.getGroupId();
                    entity.artifactId = key.getArtifactId();
                    entity.version = value.getVersion();
                    entity.versionId = value.getVersionId();
                    entity.artifactType = value.getArtifactType();
                    entity.state = value.getState();
                    entity.name = value.getMetaData().getName();
                    entity.description = value.getMetaData().getDescription();
                    entity.createdBy = value.getCreatedBy();
                    entity.createdOn = value.getCreatedOn().getTime();
                    entity.labels = value.getMetaData().getLabels();
                    entity.properties = value.getMetaData().getProperties();
                    entity.isLatest = value.getLatest();
                    entity.contentId = value.getContentId();
                    sqlStore.importArtifactVersion(entity);
                    return null;
                default:
                    return unsupported(key, value);
            }
        } catch (ArtifactNotFoundException | ArtifactAlreadyExistsException e) {
            // Send a tombstone message to clean up the unique Kafka message that caused this failure.  We may be
            // able to do this for other errors, but these two are definitely safe.
            submitter.send(key, null);
            throw e;
        }
    }

    /**
     * Process a Kafka message of type "artifact rule".  This includes creating, updating, and deleting
     * rules for a specific artifact.
     * @param key
     * @param value
     */
    private Object processArtifactRuleMessage(ArtifactRuleKey key, ArtifactRuleValue value) {
        switch (value.getAction()) {
            case CREATE:
                // Note: createArtifactRuleAsync() must be called instead of createArtifactRule() because that's what
                // KafkaSqlRegistryStorage::createArtifactRuleAsync() expects (a return value)
                sqlStore.createArtifactRule(key.getGroupId(), key.getArtifactId(), key.getRuleType(), value.getConfig());
                return null;
            case UPDATE:
                sqlStore.updateArtifactRule(key.getGroupId(), key.getArtifactId(), key.getRuleType(), value.getConfig());
                return null;
            case DELETE:
                sqlStore.deleteArtifactRule(key.getGroupId(), key.getArtifactId(), key.getRuleType());
                return null;
            case IMPORT:
                ArtifactRuleEntity entity = new ArtifactRuleEntity();
                entity.groupId = key.getGroupId();
                entity.artifactId = key.getArtifactId();
                entity.type = key.getRuleType();
                entity.configuration = value.getConfig().getConfiguration();
                sqlStore.importArtifactRule(entity);
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "artifact owner".  This includes updating the owner for
     * a specific artifact.
     * @param key
     * @param value
     */
    private Object processArtifactOwnerMessage(ArtifactOwnerKey key, ArtifactOwnerValue value) {
        switch (value.getAction()) {
            case UPDATE:
                sqlStore.updateArtifactOwner(key.getGroupId(), key.getArtifactId(), new ArtifactOwnerDto(value.getOwner()));
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "artifact version".  This includes:
     *
     *  - Updating version meta-data and state
     *  - Deleting version meta-data and state
     *  - Deleting an artifact version
     *
     * @param key
     * @param value
     */
    private Object processArtifactVersion(ArtifactVersionKey key, ArtifactVersionValue value) {
        switch (value.getAction()) {
            case UPDATE:
                sqlStore.updateArtifactVersionMetaDataAndState(key.getGroupId(), key.getArtifactId(), key.getVersion(), value.getMetaData(), value.getState());
                return null;
            case DELETE:
                sqlStore.deleteArtifactVersion(key.getGroupId(), key.getArtifactId(), key.getVersion());
                return null;
            case CLEAR:
                sqlStore.deleteArtifactVersionMetaData(key.getGroupId(), key.getArtifactId(), key.getVersion());
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "content".  This primarily means creating or updating a row in
     * the content table.
     * @param key
     * @param value
     */
    private Object processContent(ContentKey key, ContentValue value) {
        switch (value.getAction()) {
            case CREATE:
                if (!sqlStore.isContentExists(key.getContentHash())) {
                    sqlStore.storeContent(key.getContentId(), key.getContentHash(), value.getCanonicalHash(), value.getContent(), value.getSerializedReferences());
                }
                return null;
            case IMPORT:
                if (!sqlStore.isContentExists(key.getContentId())) {
                    ContentEntity entity = new ContentEntity();
                    entity.contentId = key.getContentId();
                    entity.contentHash = key.getContentHash();
                    entity.canonicalHash = value.getCanonicalHash();
                    entity.contentBytes = value.getContent().bytes();
                    entity.serializedReferences = value.getSerializedReferences();
                    sqlStore.importContent(entity);
                }
                return null;
            case UPDATE:
                sqlStore.updateContentCanonicalHash(value.getCanonicalHash(), key.getContentId(), key.getContentHash());
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "global rule".  This includes creating, updating, and deleting
     * global rules.
     *
     * @param key
     * @param value
     */
    private Object processGlobalRule(GlobalRuleKey key, GlobalRuleValue value) {
        switch (value.getAction()) {
            case CREATE:
                sqlStore.createGlobalRule(key.getRuleType(), value.getConfig());
                return null;
            case UPDATE:
                sqlStore.updateGlobalRule(key.getRuleType(), value.getConfig());
                return null;
            case DELETE:
                sqlStore.deleteGlobalRule(key.getRuleType());
                return null;
            case IMPORT:
                GlobalRuleEntity entity = new GlobalRuleEntity();
                entity.ruleType = key.getRuleType();
                entity.configuration = value.getConfig().getConfiguration();
                sqlStore.importGlobalRule(entity);
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "role mapping".  This includes creating, updating, and deleting
     * role mappings.
     *
     * @param key
     * @param value
     */
    private Object processRoleMapping(RoleMappingKey key, RoleMappingValue value) {
        switch (value.getAction()) {
            case CREATE:
                sqlStore.createRoleMapping(key.getPrincipalId(), value.getRole(), value.getPrincipalName());
                return null;
            case UPDATE:
                sqlStore.updateRoleMapping(key.getPrincipalId(), value.getRole());
                return null;
            case DELETE:
                sqlStore.deleteRoleMapping(key.getPrincipalId());
                return null;
//            case IMPORT:
// Should we be importing role mappings?
//                GlobalRuleEntity entity = new GlobalRuleEntity();
//                entity.ruleType = key.getRuleType();
//                entity.configuration = value.getConfig().getConfiguration();
//                sqlStore.importGlobalRule(entity);
//                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "global id".  This is typically used to generate a new globalId that
     * is unique and consistent across the cluster.
     * @param key
     * @param value
     */
    private Object processGlobalId(GlobalIdKey key, GlobalIdValue value) {
        switch (value.getAction()) {
            case CREATE:
                return sqlStore.nextGlobalId();
            case RESET:
                sqlStore.resetGlobalId();
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "content id".  This is typically used to generate a new contentId that
     * is unique and consistent across the cluster.
     * @param key
     * @param value
     */
    private Object processContentId(ContentIdKey key, ContentIdValue value) {
        switch (value.getAction()) {
            case CREATE:
                return sqlStore.nextContentId();
            case RESET:
                sqlStore.resetContentId();
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "log config".  This includes updating and deleting
     * log configurations.
     * @param key
     * @param value
     */
    private Object processLogConfig(LogConfigKey key, LogConfigValue value) {
        switch (value.getAction()) {
            case UPDATE:
                sqlStore.setLogConfiguration(value.getConfig());
                return null;
            case DELETE:
                sqlStore.removeLogConfiguration(value.getConfig().getLogger());
                return null;
            default:
                return unsupported(key, value);
        }
    }

    private Object unsupported(MessageKey key, AbstractMessageValue value) {
        final String m = String.format("Unsupported action '%s' for message type '%s'", value.getAction(), key.getType().name());
        log.warn(m);
        throw new RegistryStorageException(m);
    }
}
