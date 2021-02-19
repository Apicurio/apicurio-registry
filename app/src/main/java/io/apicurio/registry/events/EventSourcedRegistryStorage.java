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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletionStage;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ContentNotFoundException;
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
import io.apicurio.registry.storage.dto.LogConfigurationDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

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

    private void fireEvent(RegistryEventType type, Optional<String> artifactId, Object data, Throwable error) {
        if (error == null && data != null) {
            eventsService.triggerEvent(type, artifactId, data);
        }
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
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException {
        storage.updateArtifactState(groupId, artifactId, state);
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, Long version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.updateArtifactState(groupId, artifactId, version, state);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return storage.createArtifact(groupId, artifactId, artifactType, content)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_CREATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return storage.createArtifactWithMetadata(groupId, artifactId, artifactType, content, metaData)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_CREATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.deleteArtifact(groupId, artifactId);
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        storage.deleteArtifacts(groupId);
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
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return storage.updateArtifact(groupId, artifactId, artifactType, content)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_UPDATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.updateArtifactWithMetadata(groupId, artifactId, artifactType, content, metaData)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_UPDATED, Optional.of(artifactId), meta, ex));
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
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactRules(groupId, artifactId);
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        return storage.createArtifactRuleAsync(groupId, artifactId, rule, config);
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        storage.deleteArtifactRules(groupId, artifactId);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return storage.getArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.updateArtifactRule(groupId, artifactId, rule, config);
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.deleteArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public SortedSet<Long> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
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
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, long version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return storage.getGlobalRules();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        storage.createGlobalRule(rule, config);
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        storage.deleteGlobalRules();
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        return storage.getGlobalRule(rule);
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        storage.updateGlobalRule(rule, config);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        storage.deleteGlobalRule(rule);
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

}
