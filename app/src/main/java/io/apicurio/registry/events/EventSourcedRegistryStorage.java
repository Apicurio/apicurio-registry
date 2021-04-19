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
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.events.dto.ArtifactId;
import io.apicurio.registry.events.dto.ArtifactRuleChange;
import io.apicurio.registry.events.dto.ArtifactStateChange;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ContentNotFoundException;
import io.apicurio.registry.storage.GroupAlreadyExistsException;
import io.apicurio.registry.storage.GroupNotFoundException;
import io.apicurio.registry.storage.LogConfigurationNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.Entity;

/**
 * @author Fabian Martinez
 */
public class EventSourcedRegistryStorage implements RegistryStorage {

    private RegistryStorage storage;
    private EventsService eventsService;

    public EventSourcedRegistryStorage(RegistryStorage actualStorage, EventsService eventsService) {
        this.storage = actualStorage;
        this.eventsService = eventsService;
    }

    private void fireEvent(RegistryEventType type, String artifactId, Object data, Throwable error) {
        if (error == null && data != null) {
            eventsService.triggerEvent(type, Optional.ofNullable(artifactId), data);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return storage.storageName();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#supportsMultiTenancy()
     */
    @Override
    public boolean supportsMultiTenancy() {
        return storage.supportsMultiTenancy();
    }

    @Override
    public boolean isReady() {
        return storage.isReady();
    }

    @Override
    public boolean isAlive() {
        return storage.isAlive();
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state) throws ArtifactNotFoundException, RegistryStorageException {
        storage.updateArtifactState(groupId, artifactId, state);
        ArtifactStateChange data = new ArtifactStateChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setState(state.value());
        fireEvent(RegistryEventType.ARTIFACT_STATE_CHANGED, artifactId, data, null);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.updateArtifactState(groupId, artifactId, version, state);
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
        return storage.createArtifact(groupId, artifactId, version, artifactType, content)
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
        return storage.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData)
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
        List<String> set = storage.deleteArtifact(groupId, artifactId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
        return set;
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        storage.deleteArtifacts(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.ARTIFACTS_IN_GROUP_DELETED, groupId, data, null);
    }

    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifact(groupId, artifactId);
    }

    @Override
    public ContentHandle getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException {
        return storage.getArtifactByContentHash(contentHash);
    }

    @Override
    public ContentHandle getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException {
        return storage.getArtifactByContentId(contentId);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return storage.updateArtifact(groupId, artifactId, version, artifactType, content)
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
        return storage.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData)
                .whenComplete((meta, ex) -> {
                    ArtifactId data = new ArtifactId();
                    data.setGroupId(groupId);
                    data.setArtifactId(artifactId);
                    data.setVersion(meta.getVersion());
                    fireEvent(RegistryEventType.ARTIFACT_UPDATED, artifactId, data, ex);
                });
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return storage.getArtifactIds(limit);
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return storage.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactMetaData(groupId, artifactId);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersionMetaData(groupId, artifactId, canonical, content);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactMetaData(id);
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        storage.updateArtifactMetaData(groupId, artifactId, metaData);
        //no event here, UPDATE_ARTIFACT is for cases where a new version is added
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactRules(groupId, artifactId);
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        return storage.createArtifactRuleAsync(groupId, artifactId, rule, config)
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
        storage.deleteArtifactRules(groupId, artifactId);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        fireEvent(RegistryEventType.ALL_ARTIFACT_RULES_DELETED, artifactId, data, null);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return storage.getArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.updateArtifactRule(groupId, artifactId, rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_UPDATED, artifactId, data, null);
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.deleteArtifactRule(groupId, artifactId, rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setRule(rule.value());
        fireEvent(RegistryEventType.ARTIFACT_RULE_DELETED, artifactId, data, null);
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersions(groupId, artifactId);
    }

    @Override
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit)
            throws ArtifactNotFoundException, RegistryStorageException {
        return storage.searchVersions(groupId, artifactId, offset, limit);
    }

    @Override
    public StoredArtifactDto getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(id);
    }

    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersion(groupId, artifactId, version);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        data.setArtifactId(artifactId);
        data.setVersion(version);
        fireEvent(RegistryEventType.ARTIFACT_DELETED, artifactId, data, null);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return storage.getGlobalRules();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        storage.createGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_CREATED, null, data, null);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        storage.deleteGlobalRules();
        fireEvent(RegistryEventType.ALL_GLOBAL_RULES_DELETED, null, new HashMap<String, Object>(), null);
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return storage.getGlobalRule(rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        storage.updateGlobalRule(rule, config);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_UPDATED, null, data, null);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        storage.deleteGlobalRule(rule);
        ArtifactRuleChange data = new ArtifactRuleChange();
        data.setRule(rule.value());
        fireEvent(RegistryEventType.GLOBAL_RULE_DELETED, null, data, null);
    }

    @Override
    public LogConfigurationDto getLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        return storage.getLogConfiguration(logger);
    }

    @Override
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        storage.setLogConfiguration(logConfiguration);
    }

    @Override
    public void removeLogConfiguration(String logger) throws RegistryStorageException, LogConfigurationNotFoundException {
        storage.removeLogConfiguration(logger);
    }

    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        return storage.listLogConfigurations();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        storage.createGroup(group);
        ArtifactId data = new ArtifactId();
        data.setGroupId(group.getGroupId());
        fireEvent(RegistryEventType.GROUP_CREATED, group.getGroupId(), data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        storage.updateGroupMetaData(group);
        ArtifactId data = new ArtifactId();
        data.setGroupId(group.getGroupId());
        fireEvent(RegistryEventType.GROUP_UPDATED, group.getGroupId(), data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        storage.deleteGroup(groupId);
        ArtifactId data = new ArtifactId();
        data.setGroupId(groupId);
        fireEvent(RegistryEventType.GROUP_DELETED, groupId, data, null);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupIds(java.lang.Integer)
     */
    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return storage.getGroupIds(limit);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupMetaData(java.lang.String)
     */
    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException {
        return storage.getGroupMetaData(groupId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionsByContentId(long)
     */
    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return storage.getArtifactVersionsByContentId(contentId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#exportData(java.util.function.Function)
     */
    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        storage.exportData(handler);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream)
     */
    @Override
    public void importData(EntityInputStream entities) throws RegistryStorageException {
        storage.importData(entities);
    }

}
