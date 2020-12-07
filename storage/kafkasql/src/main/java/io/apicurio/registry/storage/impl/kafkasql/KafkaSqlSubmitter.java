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

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ArtifactVersionKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.ContentKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.GlobalRuleKey;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactRuleValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ArtifactVersionValue;
import io.apicurio.registry.storage.impl.kafkasql.values.ContentValue;
import io.apicurio.registry.storage.impl.kafkasql.values.GlobalRuleValue;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * @author eric.wittmann@gmail.com
 */
public class KafkaSqlSubmitter {
    
    private final MessageSender sender;
    
    /**
     * Constructor.
     * @param sender
     */
    public KafkaSqlSubmitter(MessageSender sender) {
        this.sender = sender;
    }

    /* ******************************************************************************************
     * Content
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitContent(String artifactId, String contentHash, ArtifactType artifactType, ContentHandle content) {
        ContentKey key = ContentKey.create(artifactId, contentHash);
        ContentValue value = ContentValue.create(ActionType.Create, artifactType, content);
        return sender.send(key, value);
    }

    
    /* ******************************************************************************************
     * Artifact
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifact(String artifactId, ActionType action,
            ArtifactType artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData) {
        ArtifactKey key = ArtifactKey.create(artifactId);
        ArtifactValue value = ArtifactValue.create(action, artifactType, contentHash, createdBy, createdOn, metaData);
        return sender.send(key, value);
    }
    public CompletableFuture<UUID> submitArtifact(String artifactId, ActionType action) {
        return this.submitArtifact(artifactId, action,  null, null, null, null, null);
    }

    
    /* ******************************************************************************************
     * Version
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactVersion(String artifactId, int version, ActionType action, ArtifactState state, 
            EditableArtifactMetaDataDto metaData) {
        ArtifactVersionKey key = ArtifactVersionKey.create(artifactId, version);
        ArtifactVersionValue value = ArtifactVersionValue.create(action, state, metaData);
        return sender.send(key, value);
    }
    public CompletableFuture<UUID> submitVersion(String artifactId, int version, ActionType action) {
        return submitArtifactVersion(artifactId, version, action, null, null);
    }

    
    /* ******************************************************************************************
     * Artifact Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitArtifactRule(String artifactId, RuleType rule, ActionType action,
            RuleConfigurationDto config) {
        ArtifactRuleKey key = ArtifactRuleKey.create(artifactId, rule);
        ArtifactRuleValue value = ArtifactRuleValue.create(action, config);
        return sender.send(key, value);
    }
    public CompletableFuture<UUID> submitArtifactRule(String artifactId, RuleType rule, ActionType action) {
        return submitArtifactRule(artifactId, rule, action, null);
    }

    
    /* ******************************************************************************************
     * Global Rule
     * ****************************************************************************************** */
    public CompletableFuture<UUID> submitGlobalRule(RuleType rule, ActionType action, RuleConfigurationDto config) {
        GlobalRuleKey key = GlobalRuleKey.create(rule);
        GlobalRuleValue value = GlobalRuleValue.create(action, config);
        return sender.send(key, value);
    }
    public CompletableFuture<UUID> submitGlobalRule(RuleType rule, ActionType action) {
        return submitGlobalRule(rule, action, null);
    }
    
}
