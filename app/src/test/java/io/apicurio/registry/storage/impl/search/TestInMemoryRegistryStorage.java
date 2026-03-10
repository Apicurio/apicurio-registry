package io.apicurio.registry.storage.impl.search;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.ContentHandle;
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
import io.apicurio.registry.storage.dto.VersionContentDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Minimal in-memory {@link RegistryStorage} implementation for unit testing. Contains static
 * pre-configured data simulating a populated registry with two groups, three artifacts, and five
 * versions across different artifact types and states.
 *
 * <p>Only implements the methods that the startup indexer calls during reindex:
 * {@link #forEachVersion}. All other methods throw {@link UnsupportedOperationException}.</p>
 */
public class TestInMemoryRegistryStorage implements RegistryStorage {

    private record VersionEntry(ArtifactVersionMetaDataDto metadata, String content) {}

    private final List<VersionEntry> versions;

    /**
     * Creates the storage pre-loaded with test data.
     */
    public TestInMemoryRegistryStorage() {
        long baseTime = System.currentTimeMillis() - 100000;
        List<VersionEntry> entries = new ArrayList<>();

        // Group: test-group-1, Artifact: pet-api (OPENAPI), 2 versions
        entries.add(entry(1001L, 501L, "test-group-1", "pet-api", "1.0.0", 1, "OPENAPI",
                VersionState.ENABLED, "Pet Store API", "An API for managing pets", "alice",
                baseTime, baseTime + 1000, Map.of("env", "production", "team", "pets"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store\"}}"));
        entries.add(entry(1002L, 502L, "test-group-1", "pet-api", "2.0.0", 2, "OPENAPI",
                VersionState.ENABLED, "Pet Store API v2", "Updated pet API", "alice",
                baseTime + 2000, baseTime + 3000, Map.of("env", "staging"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Pet Store v2\"}}"));

        // Group: test-group-1, Artifact: order-schema (AVRO), 1 version
        entries.add(entry(1003L, 503L, "test-group-1", "order-schema", "1.0.0", 1, "AVRO",
                VersionState.DEPRECATED, "Order Schema", "Avro schema for orders", "bob",
                baseTime + 4000, baseTime + 5000, Map.of("env", "production", "team", "orders"),
                "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[]}"));

        // Group: test-group-2, Artifact: user-api (OPENAPI), 1 version
        entries.add(entry(1004L, 504L, "test-group-2", "user-api", "1.0.0", 1, "OPENAPI",
                VersionState.ENABLED, "User Management API", "API for user management", "charlie",
                baseTime + 6000, baseTime + 7000, Map.of("env", "production"),
                "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"User API\"}}"));

        // Group: test-group-2, Artifact: event-schema (JSON), 1 version
        entries.add(entry(1005L, 505L, "test-group-2", "event-schema", "1.0.0", 1, "JSON",
                VersionState.ENABLED, "Event Schema", "JSON schema for events", "bob",
                baseTime + 8000, baseTime + 9000, null,
                "{\"type\":\"object\",\"title\":\"Event\"}"));

        this.versions = Collections.unmodifiableList(entries);
    }

    private static VersionEntry entry(long globalId, long contentId, String groupId,
            String artifactId, String version, int versionOrder, String artifactType,
            VersionState state, String name, String description, String owner,
            long createdOn, long modifiedOn, Map<String, String> labels, String content) {
        return new VersionEntry(
                ArtifactVersionMetaDataDto.builder()
                        .globalId(globalId).contentId(contentId).groupId(groupId)
                        .artifactId(artifactId).version(version).versionOrder(versionOrder)
                        .artifactType(artifactType).state(state).name(name).description(description)
                        .owner(owner).modifiedBy(owner).createdOn(createdOn).modifiedOn(modifiedOn)
                        .labels(labels).build(),
                content);
    }

    /**
     * Returns the total number of pre-configured versions.
     *
     * @return the version count
     */
    public int getVersionCount() {
        return versions.size();
    }

    // ===== Methods used by the startup indexer =====

    @Override
    public void forEachVersion(Consumer<VersionContentDto> consumer) {
        versions.stream()
                .sorted(Comparator.comparingLong(e -> e.metadata().getGlobalId()))
                .forEach(e -> {
                    ArtifactVersionMetaDataDto m = e.metadata();
                    VersionContentDto dto = VersionContentDto.builder()
                            .groupId(m.getGroupId())
                            .artifactId(m.getArtifactId())
                            .version(m.getVersion())
                            .versionOrder(m.getVersionOrder())
                            .globalId(m.getGlobalId())
                            .contentId(m.getContentId())
                            .name(m.getName())
                            .description(m.getDescription())
                            .owner(m.getOwner())
                            .createdOn(m.getCreatedOn())
                            .modifiedBy(m.getModifiedBy())
                            .modifiedOn(m.getModifiedOn())
                            .artifactType(m.getArtifactType())
                            .state(m.getState())
                            .labels(m.getLabels())
                            .content(ContentHandle.create(
                                    e.content().getBytes(StandardCharsets.UTF_8)))
                            .build();
                    consumer.accept(dto);
                });
    }

    @Override
    public void forEachVersion(long sinceTimestamp, Consumer<VersionContentDto> consumer) {
        versions.stream()
                .filter(e -> e.metadata().getModifiedOn() >= sinceTimestamp)
                .sorted(Comparator.comparingLong(e -> e.metadata().getGlobalId()))
                .forEach(e -> {
                    ArtifactVersionMetaDataDto m = e.metadata();
                    VersionContentDto dto = VersionContentDto.builder()
                            .groupId(m.getGroupId())
                            .artifactId(m.getArtifactId())
                            .version(m.getVersion())
                            .versionOrder(m.getVersionOrder())
                            .globalId(m.getGlobalId())
                            .contentId(m.getContentId())
                            .name(m.getName())
                            .description(m.getDescription())
                            .owner(m.getOwner())
                            .createdOn(m.getCreatedOn())
                            .modifiedBy(m.getModifiedBy())
                            .modifiedOn(m.getModifiedOn())
                            .artifactType(m.getArtifactType())
                            .state(m.getState())
                            .labels(m.getLabels())
                            .content(ContentHandle.create(
                                    e.content().getBytes(StandardCharsets.UTF_8)))
                            .build();
                    consumer.accept(dto);
                });
    }

    @Override
    public long countVersionsModifiedSince(long sinceTimestamp) {
        return versions.stream()
                .filter(e -> e.metadata().getModifiedOn() >= sinceTimestamp)
                .count();
    }

    @Override
    public long getLatestVersionTimestamp() {
        return 0;
    }

    @Override
    public List<Long> getAllVersionGlobalIds() {
        return List.of();
    }

    @Override
    public String storageName() {
        return "inmemory";
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
        return true;
    }

    // ===== Unused methods — throw UnsupportedOperationException =====

    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws RegistryStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) throws RegistryStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId,
            String version) throws RegistryStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> deleteArtifact(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteArtifacts(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentWrapperDto getContentById(long contentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content,
            EditableVersionMetaDataDto metaData, List<String> branches, boolean isDraft,
            boolean dryRun, String owner) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId,
            String artifactId, boolean canonical, TypedContent content,
            List<ArtifactReferenceDto> artifactReferences) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuleType> getGroupRules(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGroupRules(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuleConfigurationDto getGroupRule(String groupId, RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteArtifactRules(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactRule(String groupId, String artifactId, RuleType rule,
            RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId,
            Set<VersionState> filterBy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactVersionContent(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RuleType> getGlobalRules() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGlobalRules() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createGroup(GroupMetaDataDto group) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGroup(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getGroupIds(Integer limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void exportData(String groupId, Function<Entity, Void> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importData(EntityInputStream entities, boolean preserveGlobalId,
            boolean preserveContentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void upgradeData(EntityInputStream entities, boolean preserveGlobalId,
            boolean preserveContentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countArtifacts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countArtifactVersions(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countActiveArtifactVersions(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countTotalArtifactVersions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createRoleMapping(String principalId, String role, String principalName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRoleForPrincipal(String principalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRoleMapping(String principalId, String role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRoleMapping(String principalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAllUserData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String createDownload(DownloadContextDto context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DownloadContextDto consumeDownload(String downloadId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAllExpiredDownloads() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAllOrphanedContent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGroupExists(String groupId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CommentDto createArtifactVersionComment(String groupId, String artifactId,
            String version, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId,
            String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetGlobalId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetContentId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetCommentId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long nextContentId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long nextGlobalId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long nextCommentId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importComment(CommentEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importGroup(GroupEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importContent(ContentEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importArtifact(ArtifactEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void importBranch(BranchEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isContentExists(String contentHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId,
            String contentHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description,
            List<String> versions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBranch(GA ga, BranchId branchId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, Set<VersionState> filterBy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset,
            int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendVersionToBranch(GA ga, BranchId branchId, VersionId version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String triggerSnapshotCreation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String createSnapshot(String snapshotLocation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String createEvent(OutboxEvent event) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsDatabaseEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getVersionsModifiedSince(long sinceTimestamp) {
        return List.of();
    }

    // ===== DynamicConfigStorage methods =====

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto propertyDto) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteConfigProperty(String propertyName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() {
        throw new UnsupportedOperationException();
    }
}
