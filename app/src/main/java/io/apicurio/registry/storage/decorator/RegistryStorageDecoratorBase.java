package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Forwards all method calls to the delegate, extends the read-only base.
 * <p>
 * This class is intended for extension, but is not abstract to catch missing methods.
 *
 */
public class RegistryStorageDecoratorBase extends RegistryStorageDecoratorReadOnlyBase {


    protected RegistryStorageDecoratorBase() {
    }


    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, state);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactState(groupId, artifactId, version, state);
    }


    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId,
                                                          String version, String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData, references);
    }


    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.deleteArtifact(groupId, artifactId);
    }


    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        delegate.deleteArtifacts(groupId);
    }


    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId,
                                                          String version, String artifactType, ContentHandle content,
                                                          EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content,
                metaData, references);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
                                       EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }


    @Override
    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRules(groupId, artifactId);
    }


    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
                                   RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }


    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) throws ArtifactNotFoundException, RegistryStorageException {
        delegate.updateArtifactOwner(groupId, artifactId, owner);
    }


    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }


    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }


    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
                                              EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }


    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.deleteArtifactVersionMetaData(groupId, artifactId, version);
    }


    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        delegate.createGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        delegate.deleteGlobalRules();
    }


    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        delegate.updateGlobalRule(rule, config);
    }


    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        delegate.deleteGlobalRule(rule);
    }


    @Override
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        delegate.createGroup(group);
    }


    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) throws GroupNotFoundException, RegistryStorageException {
        delegate.updateGroupMetaData(groupId, dto);
    }
    
    
    @Override
    public void updateGroupMetaData(String groupId, String description, Map<String, String> labels,
            String modifiedBy, Date modifiedOn) {
        delegate.updateGroupMetaData(groupId, description, labels, modifiedBy, modifiedOn);
    }


    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        delegate.deleteGroup(groupId);
    }


    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) throws RegistryStorageException {
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }


    @Override
    public void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException {
        delegate.createRoleMapping(principalId, role, principalName);
    }


    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        delegate.deleteRoleMapping(principalId);
    }


    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        delegate.updateRoleMapping(principalId, role);
    }


    @Override
    public void deleteAllUserData() {
        delegate.deleteAllUserData();
    }


    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        return delegate.createDownload(context);
    }


    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        return delegate.consumeDownload(downloadId);
    }


    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
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
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value) {
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }


    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId) {
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }


    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value) {
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }


    @Override
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version, IdGenerator commentId, String createdBy, Date createdOn, String value) {
        return delegate.createArtifactVersionCommentRaw(groupId, artifactId, version, commentId,
                createdBy, createdOn, value);
    }


    @Override
    public void resetGlobalId() {
        delegate.resetGlobalId();
    }


    @Override
    public void resetContentId() {
        delegate.resetContentId();
    }


    @Override
    public void resetCommentId() {
        delegate.resetCommentId();
    }


    @Override
    public void importComment(CommentEntity entity) {
        delegate.importComment(entity);
    }


    @Override
    public void importGroup(GroupEntity entity) {
        delegate.importGroup(entity);
    }


    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        delegate.importGlobalRule(entity);
    }


    @Override
    public void importContent(ContentEntity entity) {
        delegate.importContent(entity);
    }


    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        delegate.importArtifactVersion(entity);
    }


    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        delegate.importArtifactRule(entity);
    }


    @Override
    public void importArtifactBranch(ArtifactBranchEntity entity) {
        delegate.importArtifactBranch(entity);
    }


    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator) {
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version,
                artifactType, contentHash, createdBy, createdOn, metaData, globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.createArtifactWithMetadata(groupId, artifactId, version,
                artifactType, contentHash, createdBy, createdOn, metaData, globalIdGenerator);
    }


    @Override
    public long nextContentId() {
        return delegate.nextContentId();
    }


    @Override
    public long nextGlobalId() {
        return delegate.nextGlobalId();
    }


    @Override
    public long nextCommentId() {
        return delegate.nextCommentId();
    }


    @Override
    public void createOrUpdateArtifactBranch(GAV gav, BranchId branchId) {
        delegate.createOrUpdateArtifactBranch(gav, branchId);
    }


    @Override
    public void createOrReplaceArtifactBranch(GA ga, BranchId branchId, List<VersionId> versions) {
        delegate.createOrReplaceArtifactBranch(ga, branchId, versions);
    }


    @Override
    public void deleteArtifactBranch(GA ga, BranchId branchId) {
        delegate.deleteArtifactBranch(ga, branchId);
    }
}
