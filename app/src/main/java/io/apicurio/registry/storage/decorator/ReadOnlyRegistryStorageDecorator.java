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
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

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
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType,
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
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version, String artifactType,
                                              ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.updateArtifact(groupId, artifactId, version, artifactType, content, references);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version, String artifactType,
                                                          ContentHandle content, EditableArtifactMetaDataDto metaData,
                                                          List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references);
    }


    @Override
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactMetaData(groupId, artifactId, metaData);
    }


    @Override
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactOwner(groupId, artifactId, owner);
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
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
    }


    @Override
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.deleteArtifactVersionMetaData(groupId, artifactId, version);
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
    public void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactState(groupId, artifactId, state);
    }


    @Override
    public void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateArtifactState(groupId, artifactId, version, state);
    }


    @Override
    public void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException {
        checkReadOnly();
        delegate.createGroup(group);
    }


    @Override
    public void updateGroupMetaData(GroupMetaDataDto group) throws GroupNotFoundException, RegistryStorageException {
        checkReadOnly();
        delegate.updateGroupMetaData(group);
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
    public CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version,
                                                      IdGenerator commentId, String createdBy, Date createdOn, String value) {
        checkReadOnly();
        return delegate.createArtifactVersionCommentRaw(groupId, artifactId, version, commentId, createdBy, createdOn, value);
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
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        checkReadOnly();
        delegate.updateContentCanonicalHash(newCanonicalHash, contentId, contentHash);
    }


    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator) {
        checkReadOnly();
        return delegate.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, contentHash, createdBy,
                createdOn, metaData, globalIdGenerator);
    }


    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy,
                                                          Date createdOn, EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        checkReadOnly();
        return delegate.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentHash, createdBy,
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
    public void deleteArtifactBranch(GA ga, BranchId branchId) {
        checkReadOnly();
        delegate.deleteArtifactBranch(ga, branchId);
    }
}
