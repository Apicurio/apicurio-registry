package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactOwnerDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.CommentEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;

import java.util.Date;
import java.util.List;

public abstract class AbstractReadOnlyRegistryStorage implements RegistryStorage {

    protected void readOnlyViolation() {
        // This should never happen due to the read-only decorator
        throw new UnreachableCodeException(
                "Storage is in read-only mode. ReadOnlyRegistryStorageDecorator should prevent this call.");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData,
            List<ArtifactReferenceDto> references) throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData,
            List<ArtifactReferenceDto> references) throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableArtifactMetaDataDto metaData) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteGlobalRule(RuleType rule) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void createGroup(GroupMetaDataDto group) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteGroup(String groupId) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteRoleMapping(String principalId) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void updateRoleMapping(String principalId, String role) throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void deleteAllUserData() throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        readOnlyViolation();
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        readOnlyViolation();
    }

    @Override
    public void deleteConfigProperty(String propertyName) {
        readOnlyViolation();
    }

    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        readOnlyViolation();
        return null;
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        readOnlyViolation();
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        readOnlyViolation();
    }

    @Override
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version,
            IdGenerator commentId, String createdBy, Date createdOn, String value) {
        readOnlyViolation();
        return null;
    }

    @Override
    public void resetGlobalId() {
        readOnlyViolation();
    }

    @Override
    public void resetContentId() {
        readOnlyViolation();
    }

    @Override
    public void resetCommentId() {
        readOnlyViolation();
    }

    @Override
    public void importComment(CommentEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void importGroup(GroupEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void importContent(ContentEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        readOnlyViolation();
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        readOnlyViolation();
    }

    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator) {
        readOnlyViolation();
        return null;
    }

    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator)
            throws RegistryStorageException {
        readOnlyViolation();
        return null;
    }

    @Override
    public long nextContentId() {
        readOnlyViolation();
        return 0;
    }

    @Override
    public long nextGlobalId() {
        readOnlyViolation();
        return 0;
    }

    @Override
    public long nextCommentId() {
        readOnlyViolation();
        return 0;
    }
}
