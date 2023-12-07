package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.keys.*;
import io.apicurio.registry.storage.impl.kafkasql.values.*;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.kafka.ProducerActions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Logged
public class KafkaSqlSubmitter {

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    KafkaSqlCoordinator coordinator;

    @Inject
    ProducerActions<MessageKey, MessageValue> producer;

    /**
     * Constructor.
     */
    public KafkaSqlSubmitter() {
    }

    //Once the application is done, close the producer.
    public void handleShutdown(@Observes Shutdown shutdownEvent) throws Exception {
        producer.close();
    }

    /**
     * Sends a message to the Kafka topic.
     * @param key
     * @param value
     */
    public CompletableFuture<UUID> send(MessageKey key, MessageValue value) {
        UUID requestId = coordinator.createUUID();
        RecordHeader header = new RecordHeader("req", requestId.toString().getBytes()); // TODO: Charset is not specified
        ProducerRecord<MessageKey, MessageValue> record = new ProducerRecord<>(configuration.topic(), 0, key, value, Collections.singletonList(header));
        return producer.apply(record).thenApply(rm -> requestId);
    }


    /* ******************************************************************************************
     * Content
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitContent(long contentId, String contentHash, ActionType action, String canonicalHash, ContentHandle content, String serializedReferences) {
        ContentKey key = ContentKey.create( contentId, contentHash);
        ContentValue value = ContentValue.create(action, canonicalHash, content, serializedReferences);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Group
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGroup(ActionType action, GroupMetaDataDto meta) {
        GroupKey key = GroupKey.create(meta.getGroupId());
        GroupValue value = GroupValue.create(action, meta);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitGroup(String groupId, ActionType action, boolean onlyArtifacts) {
        GroupKey key = GroupKey.create( groupId);
        GroupValue value = GroupValue.create(action, onlyArtifacts);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Artifact
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifact(String groupId, String artifactId, String version, ActionType action,
            Long globalId, String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, Integer versionOrder, ArtifactState state, Long contentId) {
        ArtifactKey key = ArtifactKey.create( groupId, artifactId);
        ArtifactValue value = ArtifactValue.create(action, globalId, version, artifactType, contentHash, createdBy, createdOn, metaData,
                versionOrder, state, contentId);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitArtifact(String groupId, String artifactId, String version, ActionType action,
            Long globalId, String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData) {
        return submitArtifact( groupId, artifactId, version, action, globalId, artifactType, contentHash, createdBy, createdOn,
                metaData, null, null, null);
    }
    public CompletableFuture<UUID> submitArtifact(String groupId, String artifactId, ActionType action) {
        return this.submitArtifact( groupId, artifactId, null, action, null, null, null, null, null, null);
    }


    /* ******************************************************************************************
     * Version
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactVersion(String groupId, String artifactId, String version, ActionType action, ArtifactState state,
            EditableArtifactMetaDataDto metaData) {
        ArtifactVersionKey key = ArtifactVersionKey.create( groupId, artifactId, version);
        ArtifactVersionValue value = ArtifactVersionValue.create(action, state, metaData);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitVersion(String groupId, String artifactId, String version, ActionType action) {
        return submitArtifactVersion( groupId, artifactId, version, action, null, null);
    }


    /* ******************************************************************************************
     * Artifact Owner
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactOwner(String groupId, String artifactId, ActionType action, String owner) {
        ArtifactOwnerKey key = ArtifactOwnerKey.create( groupId, artifactId);
        ArtifactOwnerValue value = ArtifactOwnerValue.create(action, owner);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Artifact Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactRule(String groupId, String artifactId, RuleType rule, ActionType action,
            RuleConfigurationDto config) {
        ArtifactRuleKey key = ArtifactRuleKey.create( groupId, artifactId, rule);
        ArtifactRuleValue value = ArtifactRuleValue.create(action, config);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitArtifactRule(String groupId, String artifactId, RuleType rule, ActionType action) {
        return submitArtifactRule( groupId, artifactId, rule, action, null);
    }


    /* ******************************************************************************************
     * Artifact Version comments
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitComment(String groupId, String artifactId, String version,
            String commentId, ActionType action, long globalId, String createdBy, Date createdOn, String value) {
        CommentKey key = CommentKey.create( groupId, artifactId, version, commentId);
        CommentValue cv = CommentValue.create(action, globalId, createdBy, createdOn, value);
        return send(key, cv);
    }
    public CompletableFuture<UUID> submitComment(String groupId, String artifactId, String version,
            String commentId, ActionType action, String createdBy, Date createdOn, String value) {
        return submitComment( groupId, artifactId, version, commentId, action, -1, createdBy, createdOn, value);
    }
    public CompletableFuture<UUID> submitComment(String groupId, String artifactId, String version,
            String commentId, ActionType action) {
        return submitComment( groupId, artifactId, version, commentId, action, null, null, null);
    }
    public CompletableFuture<UUID> submitComment(String commentId, ActionType action, long globalId,
            String createdBy, Date createdOn, String value) {
        return submitComment( "<import-comments>", "_", "_", commentId, action, globalId, createdBy, createdOn, value);
    }


    /* ******************************************************************************************
     * Global Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalRule(RuleType rule, ActionType action, RuleConfigurationDto config) {
        GlobalRuleKey key = GlobalRuleKey.create(rule);
        GlobalRuleValue value = GlobalRuleValue.create(action, config);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitGlobalRule(RuleType rule, ActionType action) {
        return submitGlobalRule( rule, action, null);
    }


    /* ******************************************************************************************
     * Role Mappings
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitRoleMapping(String principalId, ActionType action, String role, String principalName) {
        RoleMappingKey key = RoleMappingKey.create( principalId);
        RoleMappingValue value = RoleMappingValue.create(action, role, principalName);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitRoleMapping(String principalId, ActionType action) {
        return submitRoleMapping( principalId, action, null, null);
    }


    /* ******************************************************************************************
     * Global ID
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalId(ActionType action) {
        GlobalIdKey key = GlobalIdKey.create();
        GlobalIdValue value = GlobalIdValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Content ID
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitContentId(ActionType action) {
        ContentIdKey key = ContentIdKey.create();
        ContentIdValue value = ContentIdValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Comment ID
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitCommentId(ActionType action) {
        CommentIdKey key = CommentIdKey.create();
        CommentIdValue value = CommentIdValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Downloads
     * ****************************************************************************************** */

    public CompletableFuture<UUID> submitDownload(String downloadId, ActionType action, DownloadContextDto context) {
        DownloadKey key = DownloadKey.create( downloadId);
        DownloadValue value = DownloadValue.create(action, context);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitDownload(String downloadId, ActionType action) {
        return submitDownload( downloadId, action, null);
    }


    /* ******************************************************************************************
     * Config properties
     * ****************************************************************************************** */

    public CompletableFuture<UUID> submitConfigProperty(String propertyName, ActionType action, String propertyValue) {
        ConfigPropertyKey key = ConfigPropertyKey.create( propertyName);
        ConfigPropertyValue value = ConfigPropertyValue.create(action, propertyValue);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitConfigProperty(String propertyName, ActionType action) {
        return submitConfigProperty( propertyName, action, null);
    }


    /* ******************************************************************************************
     * Artifact Branches
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitBranch(ActionType action, GAV gav, BranchId branchId) {
        var key = ArtifactBranchKey.create(gav.getRawGroupId(), gav.getRawArtifactId(), branchId.getRawBranchId());
        var value = ArtifactBranchValue.create(action, gav.getRawVersionId());
        return send(key, value);
    }
    public CompletableFuture<UUID> submitBranch(ActionType action, GA ga, BranchId branchId) {
        var key = ArtifactBranchKey.create(ga.getRawGroupId(), ga.getRawArtifactId(), branchId.getRawBranchId());
        var value = ArtifactBranchValue.create(action, null);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Global actions
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalAction(ActionType action) {
        GlobalActionKey key = GlobalActionKey.create();
        GlobalActionValue value = GlobalActionValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Tombstones
     * ****************************************************************************************** */
    public void submitArtifactVersionTombstone(String groupId, String artifactId, String version) {
        ArtifactVersionKey key = ArtifactVersionKey.create( groupId, artifactId, version);
        send(key, null);
    }
    public void submitArtifactRuleTombstone(String groupId, String artifactId, RuleType rule) {
        ArtifactRuleKey key = ArtifactRuleKey.create(groupId, artifactId, rule);
        send(key, null);
    }
    public void submitBootstrap(String bootstrapId) {
        BootstrapKey key = BootstrapKey.create(bootstrapId);
        send(key, null);
    }

}
