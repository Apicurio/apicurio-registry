package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.ContractRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

@ApplicationScoped
public class ReadOnlyRegistryStorageDecorator extends RegistryStorageDecoratorBase {

    public static final String READ_ONLY_MODE_ENABLED_PROPERTY_NAME = "apicurio.storage.read-only.enabled";

    @Dynamic(label = "Storage read-only mode", description = "When selected, "
            + "Registry will return an error for operations that write to the storage (this property excepted).")
    @ConfigProperty(name = READ_ONLY_MODE_ENABLED_PROPERTY_NAME, defaultValue = "false")
    @Info(category = CATEGORY_STORAGE, description = "Enable Registry storage read-only mode", availableSince = "3.0.0")
    Supplier<Boolean> readOnlyModeEnabled;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.READ_ONLY_DECORATOR;
    }

    private void checkReadOnly() {
        if (isReadOnly()) {
            throw new ReadOnlyStorageException("Unsupported write operation. Storage is in read-only mode.");
        }
    }

    public boolean isReadOnly() {
        return delegate.isReadOnly() || readOnlyModeEnabled.get();
    }

    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean isVersionDraft, boolean dryRun, String owner)
            throws RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifact(groupId, artifactId, artifactType, artifactMetaData, version,
                versionContent, versionMetaData, versionBranches, isVersionDraft, dryRun, owner);
    }

    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.deleteArtifact(groupId, artifactId);
    }

    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifacts(groupId);
    }

    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, boolean dryRun, String owner)
            throws RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactVersion(groupId, artifactId, version, artifactType, content, metaData,
                branches, isDraft, dryRun, owner);
    }

    public ArtifactVersionMetaDataDto createArtifactVersionIfLatest(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content,
            EditableVersionMetaDataDto metaData, List<String> branches, boolean isDraft, String owner,
            int expectedBaseVersionOrder, EditableArtifactMetaDataDto artifactMetaData) {
        checkReadOnly();
        return delegate.createArtifactVersionIfLatest(groupId, artifactId, version, artifactType, content,
                metaData, branches, isDraft, owner, expectedBaseVersionOrder, artifactMetaData);
    }

    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }

    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }

    public void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactRules(groupId, artifactId);
    }

    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactRule(groupId, artifactId, rule, config);
    }

    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactRule(groupId, artifactId, rule);
    }

    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws GroupNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGroupRule(groupId, rule, config);
    }

    public void deleteGroupRules(String groupId) throws GroupNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGroupRules(groupId);
    }

    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws GroupNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateGroupRule(groupId, rule, config);
    }

    public void deleteGroupRule(String groupId, RuleType rule)
            throws GroupNotFoundException, RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGroupRule(groupId, rule);
    }

    public void setArtifactContractRuleset(String groupId, String artifactId,
            ContractRuleSetDto ruleset) throws RegistryStorageException {
        checkReadOnly();
        delegate.setArtifactContractRuleset(groupId, artifactId, ruleset);
    }

    public void deleteArtifactContractRuleset(String groupId, String artifactId)
            throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactContractRuleset(groupId, artifactId);
    }

    public void setVersionContractRuleset(String groupId, String artifactId, String version,
            ContractRuleSetDto ruleset) throws VersionNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.setVersionContractRuleset(groupId, artifactId, version, ruleset);
    }

    public void deleteVersionContractRuleset(String groupId, String artifactId, String version)
            throws VersionNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteVersionContractRuleset(groupId, artifactId, version);
    }

    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersion(groupId, artifactId, version);
    }

    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }

    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
        checkReadOnly();
        delegate.updateArtifactVersionState(groupId, artifactId, version, newState, dryRun);
    }

    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGlobalRule(rule, config);
    }

    public void deleteGlobalRules() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteGlobalRules();
    }

    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateGlobalRule(rule, config);
    }

    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGlobalRule(rule);
    }

    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGroup(group);
    }

    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        checkReadOnly();
        delegate.updateGroupMetaData(groupId, dto);
    }

    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteGroup(groupId);
    }

    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        checkReadOnly();
        delegate.importData(entities, preserveGlobalId, preserveContentId);
    }

    public void upgradeData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        checkReadOnly();
        delegate.upgradeData(entities, preserveGlobalId, preserveContentId);
    }

    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
        checkReadOnly();
        delegate.createRoleMapping(principalId, role, principalName);
    }

    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteRoleMapping(principalId);
    }

    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        checkReadOnly();
        delegate.updateRoleMapping(principalId, role);
    }

    public void deleteAllUserData() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteAllUserData();
    }

    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyDto.getName())) {
            checkReadOnly();
        }
        delegate.setConfigProperty(propertyDto);
    }

    public void deleteConfigProperty(String propertyName) {
        if (delegate.isReadOnly() || !READ_ONLY_MODE_ENABLED_PROPERTY_NAME.equals(propertyName)) {
            checkReadOnly();
        }
        delegate.deleteConfigProperty(propertyName);
    }

    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        checkReadOnly();
        return delegate.createArtifactVersionComment(groupId, artifactId, version, value);
    }

    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        checkReadOnly();
        delegate.deleteArtifactVersionComment(groupId, artifactId, version, commentId);
    }

    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        checkReadOnly();
        delegate.updateArtifactVersionComment(groupId, artifactId, version, commentId, value);
    }

    public void updateArtifactVersionContent(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content) throws RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionContent(groupId, artifactId, version, artifactType, content);
    }

    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        checkReadOnly();
        return delegate.createDownload(context);
    }

    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        checkReadOnly();
        return delegate.consumeDownload(downloadId);
    }

    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteAllExpiredDownloads();
    }

    public void deleteAllOrphanedContent() throws RegistryStorageException {
        checkReadOnly();
        delegate.deleteAllOrphanedContent();
    }

    public void resetGlobalId() {
        checkReadOnly();
        delegate.resetGlobalId();
    }

    public void resetContentId() {
        checkReadOnly();
        delegate.resetContentId();
    }

    public void resetCommentId() {
        checkReadOnly();
        delegate.resetCommentId();
    }

    public void importComment(CommentEntity entity) {
        checkReadOnly();
        delegate.importComment(entity);
    }

    public void importGroup(GroupEntity entity) {
        checkReadOnly();
        delegate.importGroup(entity);
    }

    public void importGlobalRule(GlobalRuleEntity entity) {
        checkReadOnly();
        delegate.importGlobalRule(entity);
    }

    public void importContent(ContentEntity entity) {
        checkReadOnly();
        delegate.importContent(entity);
    }

    public void importArtifactVersion(ArtifactVersionEntity entity) {
        checkReadOnly();
        delegate.importArtifactVersion(entity);
    }

    public void importArtifact(ArtifactEntity entity) {
        checkReadOnly();
        delegate.importArtifact(entity);
    }

    public void importArtifactRule(ArtifactRuleEntity entity) {
        checkReadOnly();
        delegate.importArtifactRule(entity);
    }

    public void importContractRule(ContractRuleEntity entity) {
        checkReadOnly();
        delegate.importContractRule(entity);
    }

    public void importGroupRule(GroupRuleEntity entity) {
        checkReadOnly();
        delegate.importGroupRule(entity);
    }

    public void importBranch(BranchEntity entity) {
        checkReadOnly();
        delegate.importBranch(entity);
    }

    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        checkReadOnly();
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }

    public long nextContentId() {
        checkReadOnly();
        return delegate.nextContentId();
    }

    public long nextGlobalId() {
        checkReadOnly();
        return delegate.nextGlobalId();
    }

    public long nextCommentId() {
        checkReadOnly();
        return delegate.nextCommentId();
    }

    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        checkReadOnly();
        return delegate.createBranch(ga, branchId, description, versions);
    }

    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
        checkReadOnly();
        delegate.updateBranchMetaData(ga, branchId, dto);
    }

    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
        checkReadOnly();
        delegate.appendVersionToBranch(ga, branchId, version);
    }

    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        checkReadOnly();
        delegate.replaceBranchVersions(ga, branchId, versions);
    }

    public void deleteBranch(GA ga, BranchId branchId) {
        checkReadOnly();
        delegate.deleteBranch(ga, branchId);
    }

    public String triggerSnapshotCreation() throws RegistryStorageException {
        checkReadOnly();
        return delegate.triggerSnapshotCreation();
    }

    public String createSnapshot(String snapshotLocation) throws RegistryStorageException {
        checkReadOnly();
        return delegate.createSnapshot(snapshotLocation);
    }

    public String createEvent(OutboxEvent event) {
        checkReadOnly();
        return delegate.createEvent(event);
    }

    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        checkReadOnly();
        return delegate.getContentByReference(reference);
    }
}
