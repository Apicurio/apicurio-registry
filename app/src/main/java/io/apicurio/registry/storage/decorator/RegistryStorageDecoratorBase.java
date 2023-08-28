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

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.*;

import java.util.Date;
import java.util.List;

/**
 * Forwards all method calls to the delegate, extends the read-only base.
 * <p>
 * This class is intended for extension, but is not abstract to catch missing methods.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class RegistryStorageDecoratorBase extends RegistryStorageDecoratorReadOnlyBase {


    protected RegistryStorageDecoratorBase() {
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactState(groupId, artifactId, state);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactState(groupId, artifactId, version, state);
    }


    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId,
                                                          String version, String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData, references);
    }


    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.deleteArtifact(groupId, artifactId);
    }


    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteArtifacts(groupId);
    }


    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId,
                                                          String version, String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData, references);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
                                       EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }


    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteArtifactRules(groupId, artifactId);
    }


    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
                                   RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactOwner(groupId, artifactId, owner);
    }


    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }


    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }


    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
                                              EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }


    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }


    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        delegate.createGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRules() throws RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteGlobalRules();
    }


    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteGlobalRule(rule);
    }


    @Override
    public void setLogConfiguration(LogConfigurationDto logConfiguration) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.setLogConfiguration(logConfiguration);
    }


    @Override
    public void removeLogConfiguration(String logger)
            throws RegistryStorageException, LogConfigurationNotFoundException, ReadOnlyStorageException {
        delegate.removeLogConfiguration(logger);
    }


    @Override
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        delegate.createGroup(group);
    }


    @Override
    public void updateGroupMetaData(GroupMetaDataDto group)
            throws GroupNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.updateGroupMetaData(group);
    }


    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteGroup(groupId);
    }


    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }


    @Override
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.createRoleMapping(principalId, role, principalName);
    }


    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteRoleMapping(principalId);
    }


    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException, ReadOnlyStorageException {
        delegate.updateRoleMapping(principalId, role);
    }


    @Override
    public void deleteAllUserData() throws ReadOnlyStorageException {
        delegate.deleteAllUserData();
    }


    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException, ReadOnlyStorageException {
        return delegate.createDownload(context);
    }


    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException, ReadOnlyStorageException {
        return delegate.consumeDownload(downloadId);
    }


    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException, ReadOnlyStorageException {
        delegate.deleteAllExpiredDownloads();
    }


    @Override
    public void setConfigProperty(DynamicConfigPropertyDto property) throws RegistryStorageException {
        delegate.setConfigProperty(property);
    }


    @Override
    public void deleteConfigProperty(String propertyName) {
        delegate.deleteConfigProperty(propertyName);
    }


    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) throws ReadOnlyStorageException {
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }


    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) throws ReadOnlyStorageException {
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }


    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) throws ReadOnlyStorageException {
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }


    @Override
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version, IdGenerator commentId, String createdBy, Date createdOn, String value) throws ReadOnlyStorageException {
        return delegate.createArtifactVersionCommentRaw(groupId, artifactId, version, commentId,
                createdBy, createdOn, value);
    }


    @Override
    public void resetGlobalId() throws ReadOnlyStorageException {
        delegate.resetGlobalId();
    }


    @Override
    public void resetContentId() throws ReadOnlyStorageException {
        delegate.resetContentId();
    }


    @Override
    public void resetCommentId() throws ReadOnlyStorageException {
        delegate.resetCommentId();
    }


    @Override
    public void importComment(CommentEntity entity) throws ReadOnlyStorageException {
        delegate.importComment(entity);
    }


    @Override
    public void importGroup(GroupEntity entity) throws ReadOnlyStorageException {
        delegate.importGroup(entity);
    }


    @Override
    public void importGlobalRule(GlobalRuleEntity entity) throws ReadOnlyStorageException {
        delegate.importGlobalRule(entity);
    }


    @Override
    public void importContent(ContentEntity entity) throws ReadOnlyStorageException {
        delegate.importContent(entity);
    }


    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) throws ReadOnlyStorageException {
        delegate.importArtifactVersion(entity);
    }


    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) throws ReadOnlyStorageException {
        delegate.importArtifactRule(entity);
    }


    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) throws ReadOnlyStorageException {
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator) throws ReadOnlyStorageException {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version,
                artifactType, contentHash, createdBy, createdOn, metaData, globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version,
                artifactType, contentHash, createdBy, createdOn, metaData, globalIdGenerator);
    }


    @Override
    public long nextContentId() throws ReadOnlyStorageException {
        return delegate.nextContentId();
    }


    @Override
    public long nextGlobalId() throws ReadOnlyStorageException {
        return delegate.nextGlobalId();
    }


    @Override
    public long nextCommentId() throws ReadOnlyStorageException {
        return delegate.nextCommentId();
    }
}
