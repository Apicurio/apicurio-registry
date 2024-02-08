package io.apicurio.registry.storage.impl.kafkasql.sql;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlCoordinator;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.storage.impl.kafkasql.keys.*;
import io.apicurio.registry.storage.impl.kafkasql.values.*;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.storage.impl.sql.SqlRegistryStorage;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.utils.impexp.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@ApplicationScoped
@Logged
public class KafkaSqlSink {

    @Inject
    Logger log;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    SqlRegistryStorage sqlStore;

    @Inject
    KafkaSqlSubmitter submitter;

    /**
     * Called by the {@link KafkaSqlRegistryStorage} main Kafka consumer loop to process a single
     * message in the topic.  Each message represents some attempt to modify the registry data.  So
     * each message much be consumed and applied to the in-memory SQL data store.
     * <p>
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
            coordinator.notifyResponse(requestId, new RegistryException(e)); // TODO: Any exception (no wrapping)
        }
    }

    /**
     * Extracts the UUID from the message.  The UUID should be found in a message header.
     *
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
     *
     * @param record
     */
    private Object doProcessMessage(ConsumerRecord<MessageKey, MessageValue> record) {
        MessageKey key = record.key();
        MessageValue value = record.value();

        MessageType messageType = key.getType();
        switch (messageType) {
            case Group:
                return processGroupMessage((GroupKey) key, (GroupValue) value);
            case Artifact:
                return processArtifactMessage((ArtifactKey) key, (ArtifactValue) value);
            case ArtifactRule:
                return processArtifactRuleMessage((ArtifactRuleKey) key, (ArtifactRuleValue) value);
            case ArtifactRules:
                return processArtifactRulesMessage((ArtifactRulesKey) key, (ArtifactRulesValue) value);
            case ArtifactVersion:
                return processArtifactVersionMessage((ArtifactVersionKey) key, (ArtifactVersionValue) value);
            case Content:
                return processContentMessage((ContentKey) key, (ContentValue) value);
            case GlobalRule:
                return processGlobalRuleMessage((GlobalRuleKey) key, (GlobalRuleValue) value);
            case GlobalRules:
                return processGlobalRulesMessage((GlobalRulesKey) key, (GlobalRulesValue) value);
            case GlobalId:
                return processGlobalIdMessage((GlobalIdKey) key, (GlobalIdValue) value);
            case ContentId:
                return processContentIdMessage((ContentIdKey) key, (ContentIdValue) value);
            case RoleMapping:
                return processRoleMappingMessage((RoleMappingKey) key, (RoleMappingValue) value);
            case GlobalAction:
                return processGlobalActionMessage((GlobalActionKey) key, (GlobalActionValue) value);
            case Download:
                return processDownloadMessage((DownloadKey) key, (DownloadValue) value);
            case ConfigProperty:
                return processConfigPropertyMessage((ConfigPropertyKey) key, (ConfigPropertyValue) value);
            case ArtifactOwner:
                return processArtifactOwnerMessage((ArtifactOwnerKey) key, (ArtifactOwnerValue) value);
            case CommentId:
                return processCommentIdMessage((CommentIdKey) key, (CommentIdValue) value);
            case Comment:
                return processCommentMessage((CommentKey) key, (CommentValue) value);
            case ArtifactBranch:
                return processArtifactBranchMessage((ArtifactBranchKey) key, (ArtifactBranchValue) value);
            case Bootstrap:
                throw new UnreachableCodeException();
        }
        throw new UnreachableCodeException("Switch statement not exhaustive.");
    }


    /**
     * Process a Kafka message of type "globalaction".
     *
     * @param key
     * @param value
     */
    private Object processGlobalActionMessage(GlobalActionKey key, GlobalActionValue value) {
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
     *
     * @param key
     * @param value
     */
    private Object processDownloadMessage(DownloadKey key, DownloadValue value) {
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
     *
     * @param key
     * @param value
     */
    private Object processConfigPropertyMessage(ConfigPropertyKey key, ConfigPropertyValue value) {
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
     *
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
                    .labels(value.getLabels())
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
                entity.labels = value.getLabels();
                sqlStore.importGroup(entity);
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "artifact".  This includes creating, updating, and deleting artifacts.
     *
     * @param key
     * @param value
     */
    private Object processArtifactMessage(ArtifactKey key, ArtifactValue value) throws RegistryStorageException {
        try {
            switch (value.getAction()) {
                case CREATE:
                    return sqlStore.createArtifactWithMetadata(key.getGroupId(), key.getArtifactId(), value.getVersion(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), IdGenerator.single(value.getGlobalId()));
                case UPDATE:
                    return sqlStore.updateArtifactWithMetadata(key.getGroupId(), key.getArtifactId(), value.getVersion(), value.getArtifactType(),
                            value.getContentHash(), value.getCreatedBy(), value.getCreatedOn(),
                            value.getMetaData(), IdGenerator.single(value.getGlobalId()));
                case DELETE:
                    return sqlStore.deleteArtifact(key.getGroupId(), key.getArtifactId());
                case IMPORT:
                    ArtifactVersionEntity entity = new ArtifactVersionEntity();
                    entity.globalId = value.getGlobalId();
                    entity.groupId = key.getGroupId();
                    entity.artifactId = key.getArtifactId();
                    entity.version = value.getVersion();
                    entity.versionOrder = value.getVersionOrder();
                    entity.artifactType = value.getArtifactType();
                    entity.state = value.getState();
                    entity.name = value.getMetaData().getName();
                    entity.description = value.getMetaData().getDescription();
                    entity.createdBy = value.getCreatedBy();
                    entity.createdOn = value.getCreatedOn().getTime();
                    entity.labels = value.getMetaData().getLabels();
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
     *
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
     * Process a Kafka message of type "artifact rules".  This includes deleting all artifact rules.
     *
     * @param key
     * @param value
     */
    private Object processArtifactRulesMessage(ArtifactRulesKey key, ArtifactRulesValue value) {
        switch (value.getAction()) {
            case DELETE:
                sqlStore.deleteArtifactRules(key.getGroupId(), key.getArtifactId());
                return null;
            default:
                return unsupported(key, value);
        }
    }

    /**
     * Process a Kafka message of type "artifact owner".  This includes updating the owner for
     * a specific artifact.
     *
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
     * <p>
     * - Updating version meta-data and state
     * - Deleting version meta-data and state
     * - Deleting an artifact version
     *
     * @param key
     * @param value
     */
    private Object processArtifactVersionMessage(ArtifactVersionKey key, ArtifactVersionValue value) {
        switch (value.getAction()) {
            case UPDATE:
                sqlStore.updateArtifactVersionMetaData(key.getGroupId(), key.getArtifactId(), key.getVersion(), value.getMetaData());
                sqlStore.updateArtifactState(key.getGroupId(), key.getArtifactId(), key.getVersion(), value.getState());
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
     *
     * @param key
     * @param value
     */
    private Object processContentMessage(ContentKey key, ContentValue value) {
        switch (value.getAction()) {
            case CREATE:
                if (!sqlStore.isContentExists(key.getContentHash())) {

                    var entity = ContentEntity.builder()
                            .contentId(key.getContentId())
                            .contentHash(key.getContentHash())
                            .canonicalHash(value.getCanonicalHash())
                            .contentBytes(value.getContent().bytes())
                            .serializedReferences(value.getSerializedReferences())
                            .build();

                    sqlStore.importContent(entity);
                }
                return null;
            case IMPORT:
                ContentEntity entity = new ContentEntity();
                entity.contentId = key.getContentId();
                entity.contentHash = key.getContentHash();
                entity.canonicalHash = value.getCanonicalHash();
                entity.contentBytes = value.getContent().bytes();
                entity.serializedReferences = value.getSerializedReferences();
                sqlStore.importContent(entity);
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
    private Object processGlobalRuleMessage(GlobalRuleKey key, GlobalRuleValue value) {
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
     * Process a Kafka message of type "global rules".  This includes deleting all global rules.
     *
     * @param key
     * @param value
     */
    private Object processGlobalRulesMessage(GlobalRulesKey key, GlobalRulesValue value) {
        switch (value.getAction()) {
            case DELETE:
                sqlStore.deleteGlobalRules();
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
    private Object processRoleMappingMessage(RoleMappingKey key, RoleMappingValue value) {
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
     *
     * @param key
     * @param value
     */
    private Object processGlobalIdMessage(GlobalIdKey key, GlobalIdValue value) {
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
     *
     * @param key
     * @param value
     */
    private Object processContentIdMessage(ContentIdKey key, ContentIdValue value) {
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
     * Process a Kafka message of type "comment id".  This is typically used to generate a new commentId that
     * is unique and consistent across the cluster.
     *
     * @param key
     * @param value
     */
    private Object processCommentIdMessage(CommentIdKey key, CommentIdValue value) {
        switch (value.getAction()) {
            case CREATE:
                return sqlStore.nextCommentId();
            case RESET:
                sqlStore.resetCommentId();
                return null;
            default:
                return unsupported(key, value);
        }
    }


    private Object unsupported(MessageKey key, AbstractMessageValue value) {
        final String m = String.format("Unsupported action '%s' for message type '%s'", value.getAction(), key.getType().name());
        log.warn(m);
        throw new RuntimeAssertionFailedException(m);
    }

    /**
     * Process a Kafka message of type "comment".  This includes creating, updating, and deleting
     * comments for a specific artifact version.
     *
     * @param key
     * @param value
     */
    private Object processCommentMessage(CommentKey key, CommentValue value) {
        switch (value.getAction()) {
            case CREATE:
                return sqlStore.createArtifactVersionCommentRaw(key.getGroupId(), key.getArtifactId(), key.getVersion(),
                        IdGenerator.single(Long.parseLong(key.getCommentId())),
                        value.getCreatedBy(), value.getCreatedOn(), value.getValue());
            case UPDATE:
                sqlStore.updateArtifactVersionComment(key.getGroupId(), key.getArtifactId(), key.getVersion(), key.getCommentId(), value.getValue());
                return null;
            case DELETE:
                sqlStore.deleteArtifactVersionComment(key.getGroupId(), key.getArtifactId(), key.getVersion(), key.getCommentId());
                return null;
            case IMPORT:
                CommentEntity entity = new CommentEntity();
                entity.commentId = key.getCommentId();
                entity.globalId = value.getGlobalId();
                entity.createdBy = value.getCreatedBy();
                entity.createdOn = value.getCreatedOn().getTime();
                entity.value = value.getValue();
                sqlStore.importComment(entity);
                return null;
            default:
                return unsupported(key, value);
        }
    }


    private Object processArtifactBranchMessage(ArtifactBranchKey key, ArtifactBranchValue value) {
        switch (value.getAction()) {
            case CREATE_OR_UPDATE:
                sqlStore.createOrUpdateArtifactBranch(new GAV(key.getGroupId(), key.getArtifactId(), value.getVersion()), new BranchId(key.getBranchId()));
                return null;
            case CREATE_OR_REPLACE:
                sqlStore.createOrReplaceArtifactBranch(new GA(key.getGroupId(), key.getArtifactId()), new BranchId(key.getBranchId()),
                        value.getVersions().stream().map(VersionId::new).collect(toList()));
                return null;
            case DELETE:
                sqlStore.deleteArtifactBranch(new GA(key.getGroupId(), key.getArtifactId()), new BranchId(key.getBranchId()));
                return null;
            case IMPORT:
                sqlStore.importArtifactBranch(ArtifactBranchEntity.builder()
                        .groupId(key.getGroupId())
                        .artifactId(key.getArtifactId())
                        .branchId(key.getBranchId())
                        .version(value.getVersion())
                        .branchOrder(value.getBranchOrder())
                        .build());
                return null;
            default:
                return unsupported(key, value);
        }
    }
}
