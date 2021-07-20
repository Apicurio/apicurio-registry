/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.decorator;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.apicurio.registry.content.ContentHandle;
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
import io.apicurio.registry.storage.dto.RoleMappingDto;
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
public abstract class RegistryStorageDecorator implements RegistryStorage {

    protected RegistryStorage delegate;

    public abstract boolean isEnabled();
    public abstract int order();

    /**
     * @param delegate
     */
    public void setDelegate(RegistryStorage delegate) {
        this.delegate = delegate;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return delegate.storageName();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#supportsMultiTenancy()
     */
    @Override
    public boolean supportsMultiTenancy() {
        return delegate.supportsMultiTenancy();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#isReady()
     */
    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#isAlive()
     */
    @Override
    public boolean isAlive() {
        return delegate.isAlive();
    }

    /**
     * @param groupId
     * @param artifactId
     * @param state
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, state);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param state
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, version, state);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param content
     * @return
     * @throws ArtifactAlreadyExistsException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param content
     * @param metaData
     * @return
     * @throws ArtifactAlreadyExistsException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.deleteArtifact(groupId, artifactId);
    }

    /**
     * @param groupId
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        delegate.deleteArtifacts(groupId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifact(groupId, artifactId);
    }

    /**
     * @param contentId
     * @return
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentId(long)
     */
    @Override
    public ContentHandle getArtifactByContentId(long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getArtifactByContentId(contentId);
    }

    /**
     * @param contentHash
     * @return
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactByContentHash(java.lang.String)
     */
    @Override
    public ContentHandle getArtifactByContentHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getArtifactByContentHash(contentHash);
    }

    /**
     * @param contentId
     * @return
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionsByContentId(long)
     */
    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return delegate.getArtifactVersionsByContentId(contentId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param content
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param content
     * @param metaData
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData);
    }

    /**
     * @param limit
     * @return
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return delegate.getArtifactIds(limit);
    }

    /**
     * @param filters
     * @param orderBy
     * @param orderDirection
     * @param offset
     * @param limit
     * @return
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.util.Set, io.apicurio.registry.storage.dto.OrderBy, io.apicurio.registry.storage.dto.OrderDirection, int, int)
     */
    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return delegate.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactMetaData(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param canonical
     * @param content
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            boolean canonical, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(groupId, artifactId, canonical, content);
    }

    /**
     * @param globalId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactMetaData(globalId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactRules(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }

    /**
     * @param groupId
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRules(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @return
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return delegate.getArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersions(groupId, artifactId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param offset
     * @param limit
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, java.lang.String, int, int)
     */
    @Override
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.searchVersions(groupId, artifactId, offset, limit);
    }

    /**
     * @param globalId
     * @return
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersion(globalId);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }

    /**
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return delegate.getGlobalRules();
    }

    /**
     * @param rule
     * @param config
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        delegate.createGlobalRule(rule, config);
    }

    /**
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        delegate.deleteGlobalRules();
    }

    /**
     * @param rule
     * @return
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        return delegate.getGlobalRule(rule);
    }

    /**
     * @param rule
     * @param config
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.dto.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        delegate.updateGlobalRule(rule, config);
    }

    /**
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        delegate.deleteGlobalRule(rule);
    }

    /**
     * @param logger
     * @return
     * @throws RegistryStorageException
     * @throws LogConfigurationNotFoundException
     * @see io.apicurio.registry.storage.RegistryStorage#getLogConfiguration(java.lang.String)
     */
    @Override
    public LogConfigurationDto getLogConfiguration(String logger)
            throws RegistryStorageException, LogConfigurationNotFoundException {
        return delegate.getLogConfiguration(logger);
    }

    /**
     * @param logConfiguration
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#setLogConfiguration(io.apicurio.registry.storage.dto.LogConfigurationDto)
     */
    @Override
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException {
        delegate.setLogConfiguration(logConfiguration);
    }

    /**
     * @param logger
     * @throws RegistryStorageException
     * @throws LogConfigurationNotFoundException
     * @see io.apicurio.registry.storage.RegistryStorage#removeLogConfiguration(java.lang.String)
     */
    @Override
    public void removeLogConfiguration(String logger)
            throws RegistryStorageException, LogConfigurationNotFoundException {
        delegate.removeLogConfiguration(logger);
    }

    /**
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#listLogConfigurations()
     */
    @Override
    public List<LogConfigurationDto> listLogConfigurations() throws RegistryStorageException {
        return delegate.listLogConfigurations();
    }

    /**
     * @param group
     * @throws GroupAlreadyExistsException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#createGroup(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        delegate.createGroup(group);
    }

    /**
     * @param group
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#updateGroupMetaData(io.apicurio.registry.storage.dto.GroupMetaDataDto)
     */
    @Override
    public void updateGroupMetaData(GroupMetaDataDto group)
            throws GroupNotFoundException, RegistryStorageException {
        delegate.updateGroupMetaData(group);
    }

    /**
     * @param groupId
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        delegate.deleteGroup(groupId);
    }

    /**
     * @param limit
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupIds(java.lang.Integer)
     */
    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return delegate.getGroupIds(limit);
    }

    /**
     * @param groupId
     * @return
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#getGroupMetaData(java.lang.String)
     */
    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId)
            throws GroupNotFoundException, RegistryStorageException {
        return delegate.getGroupMetaData(groupId);
    }

    /**
     * @param handler
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#exportData(java.util.function.Function)
     */
    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        delegate.exportData(handler);
    }

    /**
     * @param entities
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#importData(io.apicurio.registry.storage.impexp.EntityInputStream)
     */
    @Override
    public void importData(EntityInputStream entities) throws RegistryStorageException {
        delegate.importData(entities);
    }

    /**
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#countArtifacts()
     */
    @Override
    public long countArtifacts() throws RegistryStorageException {
        return delegate.countArtifacts();
    }

    /**
     * @param groupId
     * @param artifactId
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#countArtifactVersions(java.lang.String, java.lang.String)
     */
    @Override
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return delegate.countArtifactVersions(groupId, artifactId);
    }

    /**
     * @return
     * @throws RegistryStorageException
     * @see io.apicurio.registry.storage.RegistryStorage#countTotalArtifactVersions()
     */
    @Override
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return delegate.countTotalArtifactVersions();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createRoleMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void createRoleMapping(String principalId, String role) throws RegistryStorageException {
        delegate.createRoleMapping(principalId, role);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteRoleMapping(java.lang.String)
     */
    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        delegate.deleteRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleMapping(java.lang.String)
     */
    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return delegate.getRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleForPrincipal(java.lang.String)
     */
    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return delegate.getRoleForPrincipal(principalId);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getRoleMappings()
     */
    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        return delegate.getRoleMappings();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateRoleMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        delegate.updateRoleMapping(principalId, role);
    }

}
