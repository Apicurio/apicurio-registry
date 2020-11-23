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
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
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
    public void updateArtifactState(String artifactId, ArtifactState state) {
        storage.updateArtifactState(artifactId, state);
    }

    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        storage.updateArtifactState(artifactId, state, version);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return storage.createArtifact(artifactId, artifactType, content)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_CREATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return storage.createArtifactWithMetadata(artifactId, artifactType, content, metaData)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_CREATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.deleteArtifact(artifactId);
    }

    @Override
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifact(artifactId);
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return storage.updateArtifact(artifactId, artifactType, content)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_UPDATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.updateArtifactWithMetadata(artifactId, artifactType, content, metaData)
                .whenComplete((meta, ex) -> fireEvent(RegistryEventType.ARTIFACT_UPDATED, Optional.of(artifactId), meta, ex));
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return storage.getArtifactIds(limit);
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver, SortOrder sortOrder) {
        return storage.searchArtifacts(search, offset, limit, searchOver, sortOrder);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactMetaData(artifactId);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, boolean canonical, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersionMetaData(artifactId, canonical, content);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactMetaData(id);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        storage.updateArtifactMetaData(artifactId, metaData);
    }

    @Override
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactRules(artifactId);
    }

    @Override
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        return storage.createArtifactRuleAsync(artifactId, rule, config);
    }

    @Override
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        storage.deleteArtifactRules(artifactId);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return storage.getArtifactRule(artifactId, rule);
    }

    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.updateArtifactRule(artifactId, rule, config);
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        storage.deleteArtifactRule(artifactId, rule);
    }

    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersions(artifactId);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        return storage.searchVersions(artifactId, offset, limit);
    }

    @Override
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(id);
    }

    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(artifactId, version);
    }

    @Override
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersion(artifactId, version);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return storage.getArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.updateArtifactVersionMetaData(artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        storage.deleteArtifactVersionMetaData(artifactId, version);
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

}
