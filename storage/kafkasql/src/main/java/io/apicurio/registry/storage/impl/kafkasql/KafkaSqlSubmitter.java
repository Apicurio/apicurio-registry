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

import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactOwnerKey;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactOwnerValue;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactVersionKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.BootstrapKey;
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
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
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
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.kafka.ProducerActions;

/**
 * @author eric.wittmann@gmail.com
 */
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

    /**
     * Sends a message to the Kafka topic.
     * @param key
     * @param value
     */
    public CompletableFuture<UUID> send(MessageKey key, MessageValue value) {
        UUID requestId = coordinator.createUUID();
        RecordHeader header = new RecordHeader("req", requestId.toString().getBytes());
        ProducerRecord<MessageKey, MessageValue> record = new ProducerRecord<>(configuration.topic(), 0, key, value, Collections.singletonList(header));
        return producer.apply(record).thenApply(rm -> requestId);
    }


    /* ******************************************************************************************
     * Content
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitContent(String tenantId, long contentId, String contentHash, ActionType action, String canonicalHash, ContentHandle content, String serializedReferences) {
        ContentKey key = ContentKey.create(tenantId, contentId, contentHash);
        ContentValue value = ContentValue.create(action, canonicalHash, content, serializedReferences);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Group
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGroup(String tenantId, ActionType action, GroupMetaDataDto meta) {
        GroupKey key = GroupKey.create(tenantId, meta.getGroupId());
        GroupValue value = GroupValue.create(action, meta);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitGroup(String tenantId, String groupId, ActionType action, boolean onlyArtifacts) {
        GroupKey key = GroupKey.create(tenantId, groupId);
        GroupValue value = GroupValue.create(action, onlyArtifacts);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Artifact
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifact(String tenantId, String groupId, String artifactId, String version, ActionType action,
            Long globalId, String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, Integer versionId, ArtifactState state, Long contentId, Boolean latest) {
        ArtifactKey key = ArtifactKey.create(tenantId, groupId, artifactId);
        ArtifactValue value = ArtifactValue.create(action, globalId, version, artifactType, contentHash, createdBy, createdOn, metaData,
                versionId, state, contentId, latest);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitArtifact(String tenantId, String groupId, String artifactId, String version, ActionType action,
            Long globalId, String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData) {
        return submitArtifact(tenantId, groupId, artifactId, version, action, globalId, artifactType, contentHash, createdBy, createdOn,
                metaData, null, null, null, null);
    }
    public CompletableFuture<UUID> submitArtifact(String tenantId, String groupId, String artifactId, ActionType action) {
        return this.submitArtifact(tenantId, groupId, artifactId, null, action, null, null, null, null, null, null);
    }


    /* ******************************************************************************************
     * Version
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactVersion(String tenantId, String groupId, String artifactId, String version, ActionType action, ArtifactState state,
            EditableArtifactMetaDataDto metaData) {
        ArtifactVersionKey key = ArtifactVersionKey.create(tenantId, groupId, artifactId, version);
        ArtifactVersionValue value = ArtifactVersionValue.create(action, state, metaData);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitVersion(String tenantId, String groupId, String artifactId, String version, ActionType action) {
        return submitArtifactVersion(tenantId, groupId, artifactId, version, action, null, null);
    }


    /* ******************************************************************************************
     * Artifact Owner
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactOwner(String tenantId, String groupId, String artifactId, ActionType action, String owner) {
        ArtifactOwnerKey key = ArtifactOwnerKey.create(tenantId, groupId, artifactId);
        ArtifactOwnerValue value = ArtifactOwnerValue.create(action, owner);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Artifact Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactRule(String tenantId, String groupId, String artifactId, RuleType rule, ActionType action,
            RuleConfigurationDto config) {
        ArtifactRuleKey key = ArtifactRuleKey.create(tenantId, groupId, artifactId, rule);
        ArtifactRuleValue value = ArtifactRuleValue.create(action, config);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitArtifactRule(String tenantId, String groupId, String artifactId, RuleType rule, ActionType action) {
        return submitArtifactRule(tenantId, groupId, artifactId, rule, action, null);
    }


    /* ******************************************************************************************
     * Global Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalRule(String tenantId, RuleType rule, ActionType action, RuleConfigurationDto config) {
        GlobalRuleKey key = GlobalRuleKey.create(tenantId, rule);
        GlobalRuleValue value = GlobalRuleValue.create(action, config);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitGlobalRule(String tenantId, RuleType rule, ActionType action) {
        return submitGlobalRule(tenantId, rule, action, null);
    }


    /* ******************************************************************************************
     * Role Mappings
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitRoleMapping(String tenantId, String principalId, ActionType action, String role, String principalName) {
        RoleMappingKey key = RoleMappingKey.create(tenantId, principalId);
        RoleMappingValue value = RoleMappingValue.create(action, role, principalName);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitRoleMapping(String tenantId, String principalId, ActionType action) {
        return submitRoleMapping(tenantId, principalId, action, null, null);
    }


    /* ******************************************************************************************
     * Log Configuration
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitLogConfig(String tenantId, ActionType action, LogConfigurationDto config) {
        LogConfigKey key = LogConfigKey.create(tenantId);
        LogConfigValue value = LogConfigValue.create(action, config);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitLogConfig(String tenantId, ActionType action) {
        return submitLogConfig(tenantId, action, null);
    }


    /* ******************************************************************************************
     * Global ID
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalId(String tenantId, ActionType action) {
        GlobalIdKey key = GlobalIdKey.create(tenantId);
        GlobalIdValue value = GlobalIdValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Content ID
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitContentId(String tenantId, ActionType action) {
        ContentIdKey key = ContentIdKey.create(tenantId);
        ContentIdValue value = ContentIdValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Downloads
     * ****************************************************************************************** */

    public CompletableFuture<UUID> submitDownload(String tenantId, String downloadId, ActionType action, DownloadContextDto context) {
        DownloadKey key = DownloadKey.create(tenantId, downloadId);
        DownloadValue value = DownloadValue.create(action, context);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitDownload(String tenantId, String downloadId, ActionType action) {
        return submitDownload(tenantId, downloadId, action, null);
    }


    /* ******************************************************************************************
     * Config properties
     * ****************************************************************************************** */

    public CompletableFuture<UUID> submitConfigProperty(String tenantId, String propertyName, ActionType action, String propertyValue) {
        ConfigPropertyKey key = ConfigPropertyKey.create(tenantId, propertyName);
        ConfigPropertyValue value = ConfigPropertyValue.create(action, propertyValue);
        return send(key, value);
    }
    public CompletableFuture<UUID> submitConfigProperty(String tenantId, String propertyName, ActionType action) {
        return submitConfigProperty(tenantId, propertyName, action, null);
    }


    /* ******************************************************************************************
     * Global actions
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalAction(String tenantId, ActionType action) {
        GlobalActionKey key = GlobalActionKey.create(tenantId);
        GlobalActionValue value = GlobalActionValue.create(action);
        return send(key, value);
    }


    /* ******************************************************************************************
     * Tombstones
     * ****************************************************************************************** */
    public void submitArtifactVersionTombstone(String tenantId, String groupId, String artifactId, String version) {
        ArtifactVersionKey key = ArtifactVersionKey.create(tenantId, groupId, artifactId, version);
        send(key, null);
    }
    public void submitArtifactRuleTombstone(String tenantId, String groupId, String artifactId, RuleType rule) {
        ArtifactRuleKey key = ArtifactRuleKey.create(tenantId, groupId, artifactId, rule);
        send(key, null);
    }
    public void submitBootstrap(String bootstrapId) {
        BootstrapKey key = BootstrapKey.create(bootstrapId);
        send(key, null);
    }

}
