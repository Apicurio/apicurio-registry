package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Forwards all method calls to the delegate, extends the read-only base.
 * <p>
 * This class is intended for extension, but is not abstract to catch missing methods.
 */
public class RegistryStorageDecoratorBase extends RegistryStorageDecoratorReadOnlyBase {

    protected RegistryStorageDecoratorBase() {
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean dryRun) throws RegistryStorageException {
        return delegate.createArtifact(groupId, artifactId, artifactType, artifactMetaData, version,
                versionContent, versionMetaData, versionBranches, dryRun);
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
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean dryRun) throws RegistryStorageException {
        return delegate.createArtifactVersion(groupId, artifactId, version, artifactType, content, metaData,
                branches, dryRun);
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
    public void deleteGroupRules(String groupId) throws RegistryStorageException {
        delegate.deleteGroupRules(groupId);
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) throws RegistryStorageException {
        delegate.deleteGroupRule(groupId, rule);
    }

    @Override
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        delegate.updateGroupRule(groupId, rule, config);
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
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
            EditableVersionMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        delegate.createGlobalRule(rule, config);
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) throws RegistryStorageException {
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }

    @Override
    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws RegistryStorageException {
        delegate.createGroupRule(groupId, rule, config);
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
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto)
            throws GroupNotFoundException, RegistryStorageException {
        delegate.updateGroupMetaData(groupId, dto);
    }

    @Override
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        delegate.deleteGroup(groupId);
    }

    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }

    @Override
    public void upgradeData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        delegate.upgradeData(entities, preserveGlobalId, preserveContentId);
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
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
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
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
    public void importArtifact(ArtifactEntity entity) {
        delegate.importArtifact(entity);
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        delegate.importArtifactRule(entity);
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
        delegate.importGroupRule(entity);
    }

    @Override
    public void importBranch(BranchEntity entity) {
        delegate.importBranch(entity);
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
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
    public void deleteBranch(GA ga, BranchId branchId) {
        delegate.deleteBranch(ga, branchId);
    }

    @Override
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        delegate.replaceBranchVersions(ga, branchId, versions);
    }

    @Override
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        return delegate.createBranch(ga, branchId, description, versions);
    }

    @Override
    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
        delegate.appendVersionToBranch(ga, branchId, version);
    }

    @Override
    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
        delegate.updateBranchMetaData(ga, branchId, dto);
    }

    @Override
    public String triggerSnapshotCreation() throws RegistryStorageException {
        return delegate.triggerSnapshotCreation();
    }

    @Override
    public String createSnapshot(String snapshotLocation) throws RegistryStorageException {
        return delegate.createSnapshot(snapshotLocation);
    }
}
