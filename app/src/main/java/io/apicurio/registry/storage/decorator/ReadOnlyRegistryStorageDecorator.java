package io.apicurio.registry.storage.decorator;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.ArtifactBranchEntity;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.CommentEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReadOnlyRegistryStorageDecorator extends RegistryStorageDecoratorReadOnlyBase implements RegistryStorageDecorator {

    public static final String READ_ONLY_MODE_ENABLED_PROPERTY_NAME = "apicurio.storage.read-only.enabled";


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


    private void checkReadOnly() {
        if (isReadOnly()) {
            throw new ReadOnlyStorageException("Unsupported write operation. Storage is in read-only mode.");
        }
    }


    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly() || readOnlyModeEnabled.get();
    }


    @Override
    public ArtifactVersionMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactVersionMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType,
                                                          ContentHandle content, EditableArtifactMetaDataDto metaData,
                                                          List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData,
                references);
    }


    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.deleteArtifact(groupId, artifactId);
    }


    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifacts(groupId);
    }


    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactVersion(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactVersionMetaDataDto createArtifactVersionWithMetadata(String groupId, String artifactId, String version, String artifactType,
            ContentHandle content, EditableVersionMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactVersionWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }


    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactRules(groupId, artifactId);
    }


    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }


    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }


    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableVersionMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }


    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteGlobalRules();
    }


    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGlobalRule(rule);
    }

    
    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGroup(group);
    }

    
    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        checkReadOnly();
        delegate.updateGroupMetaData(groupId, dto);
    }
    

    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGroup(groupId);
    }


    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        checkReadOnly();
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }


    @Override
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException {
        checkReadOnly();
        delegate.createRoleMapping(principalId, role, principalName);
    }


    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteRoleMapping(principalId);
    }


    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        checkReadOnly();
        delegate.updateRoleMapping(principalId, role);
    }


    @Override
    public void deleteAllUserData() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteAllUserData();
    }


    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyDto.getName())) {
            checkReadOnly();
        }
        delegate.setConfigProperty(propertyDto);
    }


    @Override
    public void deleteConfigProperty(String propertyName) {
        if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyName)) {
            checkReadOnly();
        }
        delegate.deleteConfigProperty(propertyName);
    }


    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) {
        checkReadOnly();
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }


    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        checkReadOnly();
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }


    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) {
        checkReadOnly();
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }


    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        checkReadOnly();
        return delegate.createDownload(context);
    }


    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        checkReadOnly();
        return delegate.consumeDownload(downloadId);
    }


    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteAllExpiredDownloads();
    }


    @Override
    public void resetGlobalId() {
        checkReadOnly();
        delegate.resetGlobalId();
    }


    @Override
    public void resetContentId() {
        checkReadOnly();
        delegate.resetContentId();
    }


    @Override
    public void resetCommentId() {
        checkReadOnly();
        delegate.resetCommentId();
    }


    @Override
    public void importComment(CommentEntity entity) {
        checkReadOnly();
        delegate.importComment(entity);
    }


    @Override
    public void importGroup(GroupEntity entity) {
        checkReadOnly();
        delegate.importGroup(entity);
    }


    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        checkReadOnly();
        delegate.importGlobalRule(entity);
    }


    @Override
    public void importContent(ContentEntity entity) {
        checkReadOnly();
        delegate.importContent(entity);
    }


    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        checkReadOnly();
        delegate.importArtifactVersion(entity);
    }


    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        checkReadOnly();
        delegate.importArtifactRule(entity);
    }


    @Override
    public void importArtifactBranch(ArtifactBranchEntity entity) {
        checkReadOnly();
        delegate.importArtifactBranch(entity);
    }


    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        checkReadOnly();
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }


    @Override
    public ArtifactVersionMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String owner,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentHash, owner,
                createdOn, metaData, globalIdGenerator);
    }


    @Override
    public long nextContentId() {
        checkReadOnly();
        return delegate.nextContentId();
    }


    @Override
    public long nextGlobalId() {
        checkReadOnly();
        return delegate.nextGlobalId();
    }


    @Override
    public long nextCommentId() {
        checkReadOnly();
        return delegate.nextCommentId();
    }


    @Override
    public void createOrUpdateArtifactBranch(GAV gav, BranchId branchId) {
        checkReadOnly();
        delegate.createOrUpdateArtifactBranch(gav, branchId);
    }


    @Override
    public void createOrReplaceArtifactBranch(GA ga, BranchId branchId, List<VersionId> versions) {
        checkReadOnly();
        delegate.createOrReplaceArtifactBranch(ga, branchId, versions);
    }


    @Override
    public void deleteArtifactBranch(GA ga, BranchId branchId) {
        checkReadOnly();
        delegate.deleteArtifactBranch(ga, branchId);
    }
}
