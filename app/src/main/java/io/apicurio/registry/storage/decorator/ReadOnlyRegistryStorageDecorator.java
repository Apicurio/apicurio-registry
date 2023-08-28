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

package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static io.apicurio.registry.types.WrappedRegistryException.wrap;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class ReadOnlyRegistryStorageDecorator extends RegistryStorageDecoratorReadOnlyBase implements RegistryStorageDecorator {


    public static final String READ_ONLY_MODE_ENABLED_PROPERTY_NAME = "registry.storage.read-only";


    @Dynamic(label = "Storage read-only mode", description = "When selected, " +
            "Registry will return an error for operations that write to the storage (this property excepted).")
    @ConfigProperty(name = READ_ONLY_MODE_ENABLED_PROPERTY_NAME, defaultValue = "false")
    @Info(category = "storage", description = "Enable Registry storage read-only mode", availableSince = "2.5.0.Final")
    Supplier<Boolean> readOnlyModeEnabled;


    @Override
    public boolean isEnabled() {
        return true;
    }


    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.READ_ONLY_DECORATOR;
    }


    @Override
    public void setDelegate(RegistryStorage delegate) {
        super.setDelegate(delegate);
    }


    private void checkReadOnly() throws ReadOnlyStorageException {
        if (isReadOnly()) {
            throw new ReadOnlyStorageException("Unsupported write operation. Storage is in read-only mode.");
        }
    }


    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly() || readOnlyModeEnabled.get();
    }


    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType,
                                                          ContentHandle content, EditableArtifactMetaDataDto metaData,
                                                          List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData,
                references);
    }


    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.deleteArtifact(groupId, artifactId);
    }


    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifacts(groupId);
    }


    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
                                              ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType,
                                                          ContentHandle content, EditableArtifactMetaDataDto metaData,
                                                          List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }


    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactOwner(groupId, artifactId, owner);
    }


    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifactRules(groupId, artifactId);
    }


    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }


    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }


    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }


    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }


    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.createGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRules() throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteGlobalRules();
    }


    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteGlobalRule(rule);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactState(groupId, artifactId, state);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactState(groupId, artifactId, version, state);
    }


    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.createGroup(group);
    }


    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateGroupMetaData(group);
    }


    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteGroup(groupId);
    }


    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }


    @Override
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.createRoleMapping(principalId, role, principalName);
    }


    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteRoleMapping(principalId);
    }


    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateRoleMapping(principalId, role);
    }


    @Override
    public void deleteAllUserData() throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteAllUserData();
    }


    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        try {
            if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyDto.getName())) {
                checkReadOnly();
            }
            delegate.setConfigProperty(propertyDto);
        } catch (ReadOnlyStorageException ex) {
            throw wrap(ex); // TODO Declare the exception in io.apicurio.common.apps.config.DynamicConfigStorage.setConfigProperty
        }
    }


    @Override
    public void deleteConfigProperty(String propertyName) {
        try {
            if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyName)) {
                checkReadOnly();
            }
            delegate.deleteConfigProperty(propertyName);
        } catch (ReadOnlyStorageException ex) {
            throw wrap(ex); // TODO Declare the exception in io.apicurio.common.apps.config.DynamicConfigStorage.deleteConfigProperty
        }
    }


    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }


    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }


    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }


    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createDownload(context);
    }


    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.consumeDownload(downloadId);
    }


    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        delegate.deleteAllExpiredDownloads();
    }


    @Override
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version,
                                                      IdGenerator commentId, String createdBy, Date createdOn, String value) throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createArtifactVersionCommentRaw(groupId, artifactId, version, commentId, createdBy, createdOn, value);
    }


    @Override
    public void resetGlobalId() throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.resetGlobalId();
    }


    @Override
    public void resetContentId() throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.resetContentId();
    }


    @Override
    public void resetCommentId() throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.resetCommentId();
    }


    @Override
    public void importComment(CommentEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importComment(entity);
    }


    @Override
    public void importGroup(GroupEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importGroup(entity);
    }


    @Override
    public void importGlobalRule(GlobalRuleEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importGlobalRule(entity);
    }


    @Override
    public void importContent(ContentEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importContent(entity);
    }


    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importArtifactVersion(entity);
    }


    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.importArtifactRule(entity);
    }


    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) throws ReadOnlyStorageException {
        checkReadOnly();
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator) throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, contentHash, createdBy,
                createdOn, metaData, globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException, ReadOnlyStorageException {
        checkReadOnly();
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentHash, createdBy,
                createdOn, metaData, globalIdGenerator);
    }


    @Override
    public long nextContentId() throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.nextContentId();
    }


    @Override
    public long nextGlobalId() throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.nextGlobalId();
    }


    @Override
    public long nextCommentId() throws ReadOnlyStorageException {
        checkReadOnly();
        return delegate.nextCommentId();
    }
}
