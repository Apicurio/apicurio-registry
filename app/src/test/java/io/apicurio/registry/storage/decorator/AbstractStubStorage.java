package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.Entity;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Abstract stub providing no-op/null implementations for all {@link RegistryStorage} methods. Used as a base
 * class in tests.
 */
abstract class AbstractStubStorage implements RegistryStorage {

    @Override
    public String storageName() {
        return null;
    }

    @Override
    public void initialize() {
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner) {
        return null;
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) {
        return null;
    }

    @Override
    public void deleteArtifacts(String groupId) {
    }

    @Override
    public ContentWrapperDto getContentById(long contentId) {
        return null;
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash) {
        return null;
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return null;
    }

    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return null;
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean isDraft, boolean dryRun, String owner) {
        return null;
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return null;
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return null;
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) {
        return null;
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            boolean canonical, TypedContent content, List<ArtifactReferenceDto> artifactReferences) {
        return null;
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) {
    }

    @Override
    public List<RuleType> getGroupRules(String groupId) {
        return null;
    }

    @Override
    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config) {
    }

    @Override
    public void deleteGroupRules(String groupId) {
    }

    @Override
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config) {
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) {
    }

    @Override
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) {
        return null;
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) {
        return null;
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) {
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) {
        return null;
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) {
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) {
        return null;
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId, Set<VersionState> filterBy) {
        return null;
    }

    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        return null;
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId) {
        return null;
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId,
            String version) {
        return null;
    }

    @Override
    public void updateArtifactVersionContent(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content) {
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) {
        return null;
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId) {
        return null;
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData) {
    }

    @Override
    public List<RuleType> getGlobalRules() {
        return null;
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) {
    }

    @Override
    public void deleteGlobalRules() {
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) {
        return null;
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) {
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
    }

    @Override
    public void createGroup(GroupMetaDataDto group) {
    }

    @Override
    public void deleteGroup(String groupId) {
    }

    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
    }

    @Override
    public List<String> getGroupIds(Integer limit) {
        return null;
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) {
        return null;
    }

    @Override
    public void exportData(String groupId, Function<Entity, Void> handler) {
    }

    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId,
            boolean preserveContentId) {
    }

    @Override
    public void upgradeData(EntityInputStream entities, boolean preserveGlobalId,
            boolean preserveContentId) {
    }

    @Override
    public long countArtifacts() {
        return 0;
    }

    @Override
    public long countArtifactVersions(String groupId, String artifactId) {
        return 0;
    }

    @Override
    public long countActiveArtifactVersions(String groupId, String artifactId) {
        return 0;
    }

    @Override
    public long countTotalArtifactVersions() {
        return 0;
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName) {
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() {
        return null;
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit) {
        return null;
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) {
        return null;
    }

    @Override
    public String getRoleForPrincipal(String principalId) {
        return null;
    }

    @Override
    public void updateRoleMapping(String principalId, String role) {
    }

    @Override
    public void deleteRoleMapping(String principalId) {
    }

    @Override
    public void deleteAllUserData() {
    }

    @Override
    public String createDownload(DownloadContextDto context) {
        return null;
    }

    @Override
    public DownloadContextDto consumeDownload(String downloadId) {
        return null;
    }

    @Override
    public void deleteAllExpiredDownloads() {
    }

    @Override
    public void deleteAllOrphanedContent() {
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        return null;
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since) {
        return null;
    }

    @Override
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        return null;
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) {
        return false;
    }

    @Override
    public boolean isGroupExists(String groupId) {
        return false;
    }

    @Override
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return null;
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return null;
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId) {
        return null;
    }

    @Override
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version) {
        return null;
    }

    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) {
        return false;
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit) {
        return null;
    }

    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        return null;
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return null;
    }

    @Override
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        return null;
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
    }

    @Override
    public void resetGlobalId() {
    }

    @Override
    public void resetContentId() {
    }

    @Override
    public void resetCommentId() {
    }

    @Override
    public long nextContentId() {
        return 0;
    }

    @Override
    public long nextGlobalId() {
        return 0;
    }

    @Override
    public long nextCommentId() {
        return 0;
    }

    @Override
    public void importComment(CommentEntity entity) {
    }

    @Override
    public void importGroup(GroupEntity entity) {
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
    }

    @Override
    public void importContent(ContentEntity entity) {
    }

    @Override
    public void importArtifact(ArtifactEntity entity) {
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
    }

    @Override
    public void importBranch(BranchEntity entity) {
    }

    @Override
    public boolean isContentExists(String contentHash) {
        return false;
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) {
        return false;
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) {
        return false;
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        return false;
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        return Optional.empty();
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        return null;
    }

    @Override
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        return null;
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return null;
    }

    @Override
    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
    }

    @Override
    public void deleteBranch(GA ga, BranchId branchId) {
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, Set<VersionState> filterBy) {
        return null;
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        return null;
    }

    @Override
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
    }

    @Override
    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
    }

    @Override
    public String triggerSnapshotCreation() {
        return null;
    }

    @Override
    public String createSnapshot(String snapshotLocation) {
        return null;
    }

    @Override
    public String createEvent(OutboxEvent event) {
        return null;
    }

    @Override
    public boolean supportsDatabaseEvents() {
        return false;
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() {
        return null;
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        return null;
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto property) {
    }

    @Override
    public void deleteConfigProperty(String propertyName) {
    }
}
