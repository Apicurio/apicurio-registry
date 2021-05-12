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
package io.apicurio.registry.events;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.events.dto.ArtifactId;
import io.apicurio.registry.events.dto.ArtifactRuleChange;
import io.apicurio.registry.events.dto.ArtifactStateChange;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class EventSourcedRegistryStorage extends RegistryStorageDecorator {

    @Inject
    EventsService eventsService;

    private void fireEvent(RegistryEventType type, String artifactId, Object data, Throwable error) {
        if (error == null && data != null) {
            eventsService.triggerEvent(type, Optional.ofNullable(artifactId), data);
        }
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#isConfigured()
     */
    @Override
    public boolean isConfigured() {
        return eventsService.isConfigured();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#order()
     */
    @Override
    public int order() {
        return 10;
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, state);
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setState(state.value());
        fireEvent(RegistryEventType.ARTIFACT_STATE_CHANGED, artifactId, data, null);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, version, state);
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setState(state.value());
        data.setVersion(version);
        fireEvent(RegistryEventType.ARTIFACT_STATE_CHANGED, artifactId, data, null);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content)
                .whenComplete((meta, ex) -> {
                    ArtifactId data = new ArtifactId();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setVersion(meta.getVersion());
                    fireEvent(RegistryEventType.ARTIFACT_CREATED, artifactId, data, ex);
                });
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData)
                .whenComplete((meta, ex) -> {
                    ArtifactId data = new ArtifactId();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setVersion(meta.getVersion());
                    fireEvent(RegistryEventType.ARTIFACT_CREATED, artifactId, data, ex);
                });
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        List<String> set = delegate.deleteArtifact(groupId, artifactId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
        return set;
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        delegate.deleteArtifacts(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.ARTIFACTS_IN_GROUP_DELETED, groupId, data, null);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content)
                .whenComplete((meta, ex) -> {
                    ArtifactId data = new ArtifactId();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setVersion(meta.getVersion());
                    fireEvent(RegistryEventType.ARTIFACT_UPDATED, artifactId, data, ex);
                });
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData)
                .whenComplete((meta, ex) -> {
                    ArtifactId data = new ArtifactId();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setVersion(meta.getVersion());
                    fireEvent(RegistryEventType.ARTIFACT_UPDATED, artifactId, data, ex);
                });
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
        //no event here, UPDATE_ARTIFACT is for cases where a new version is added
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifactRuleAsync(groupId, artifactId, rule, config)
                .whenComplete((meta, ex) -> {
                    ArtifactRuleChange data = new ArtifactRuleChange();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setRule(rule.value());
                    fireEvent(RegistryEventType.ARTIFACT_RULE_CREATED, artifactId, data, ex);
                });
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRules(groupId, artifactId);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ALL_ARTIFACT_RULES_DELETED, artifactId, data, null);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_UPDATED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRule(groupId, artifactId, rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_DELETED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersion(groupId, artifactId, version);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(version);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        delegate.createGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_CREATED, null, data, null);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        delegate.deleteGlobalRules();
        fireEvent(RegistryEventType.ALL_GLOBAL_RULES_DELETED, null, new HashMap<String, Object>(), null);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        delegate.updateGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_UPDATED, null, data, null);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        delegate.deleteGlobalRule(rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_DELETED, null, data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        delegate.createGroup(group);
        ArtifactId data = new ArtifactId();
        data.setGroupId(group.getGroupId());
        fireEvent(RegistryEventType.GROUP_CREATED, group.getGroupId(), data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        delegate.updateGroupMetaData(group);
        ArtifactId data = new ArtifactId();
        data.setGroupId(group.getGroupId());
        fireEvent(RegistryEventType.GROUP_UPDATED, group.getGroupId(), data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        delegate.deleteGroup(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.GROUP_DELETED, groupId, data, null);
    }

}
