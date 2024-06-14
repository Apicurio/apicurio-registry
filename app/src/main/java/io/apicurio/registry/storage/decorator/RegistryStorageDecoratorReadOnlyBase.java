package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.Entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Forwards all read-only method calls to the delegate.
 *
 */
public abstract class RegistryStorageDecoratorReadOnlyBase implements RegistryStorage {

    protected RegistryStorage delegate;

    protected RegistryStorageDecoratorReadOnlyBase() {
    }

    public void setDelegate(RegistryStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public String storageName() {
        return delegate.storageName();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public boolean isAlive() {
        return delegate.isAlive();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public ContentWrapperDto getContentById(long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getContentById(contentId);
    }

    @Override
    public ContentWrapperDto getContentByHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getContentByHash(contentHash);
    }

    @Override
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return delegate.getArtifactVersionsByContentId(contentId);
    }

    @Override
    public Set<String> getArtifactIds(Integer limit) {
        return delegate.getArtifactIds(limit);
    }

    @Override
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                                                    OrderDirection orderDirection, int offset, int limit) {
        return delegate.searchArtifacts(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactMetaData(groupId, artifactId);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId, String artifactId,
                                                                          boolean canonical, TypedContent content, List<ArtifactReferenceDto> artifactReferences)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, content, artifactReferences);
    }

    @Override
    public List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactRules(groupId, artifactId);
    }

    @Override
    public void createArtifactRule(String groupId, String artifactId, RuleType rule,
                                   RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        delegate.createArtifactRule(groupId, artifactId, rule, config);
    }

    @Override
    public RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return delegate.getArtifactRule(groupId, artifactId, rule);
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersions(groupId, artifactId);
    }


    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, int offset, int limit) throws RegistryStorageException {
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionContent(globalId);
    }

    @Override
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionContent(groupId, artifactId, version);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
                                                                 String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(groupId, artifactId, version);
    }

    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId)
            throws VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(globalId);
    }

    @Override
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return delegate.getGlobalRules();
    }

    @Override
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        return delegate.getGlobalRule(rule);
    }

    @Override
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return delegate.getGroupIds(limit);
    }

    @Override
    public GroupMetaDataDto getGroupMetaData(String groupId)
            throws GroupNotFoundException, RegistryStorageException {
        return delegate.getGroupMetaData(groupId);
    }

    @Override
    public void exportData(Function<Entity, Void> handler) throws RegistryStorageException {
        delegate.exportData(handler);
    }

    @Override
    public long countArtifacts() throws RegistryStorageException {
        return delegate.countArtifacts();
    }

    @Override
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return delegate.countArtifactVersions(groupId, artifactId);
    }

    @Override
    public long countActiveArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return delegate.countActiveArtifactVersions(groupId, artifactId);
    }

    @Override
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return delegate.countTotalArtifactVersions();
    }

    @Override
    public RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException {
        return delegate.getRoleMapping(principalId);
    }

    @Override
    public String getRoleForPrincipal(String principalId) throws RegistryStorageException {
        return delegate.getRoleForPrincipal(principalId);
    }

    @Override
    public List<RoleMappingDto> getRoleMappings() throws RegistryStorageException {
        return delegate.getRoleMappings();
    }

    @Override
    public RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit) throws RegistryStorageException {
        return delegate.searchRoleMappings(offset, limit);
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() throws RegistryStorageException {
        return delegate.getConfigProperties();
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        return delegate.getConfigProperty(propertyName);
    }

    @Override
    public List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since) {
        return delegate.getStaleConfigProperties(since);
    }

    @Override
    public DynamicConfigPropertyDto getRawConfigProperty(String propertyName) {
        return delegate.getRawConfigProperty(propertyName);
    }

    @Override
    public Map<String, TypedContent> resolveReferences(List<ArtifactReferenceDto> references) {
        return delegate.resolveReferences(references);
    }

    @Override
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return delegate.isArtifactExists(groupId, artifactId);
    }

    @Override
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return delegate.isGroupExists(groupId);
    }

    @Override
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version) throws RegistryStorageException {
        return delegate.isArtifactVersionExists(groupId, artifactId, version);
    }

    @Override
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId, String version) {
        return delegate.getContentIdsReferencingArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId, String version) {
        return delegate.getGlobalIdsReferencingArtifactVersion(groupId, artifactId, version);
    }

    @Override
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId, String version) {
        return delegate.getInboundArtifactReferences(groupId, artifactId, version);
    }

    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit) {
        return delegate.searchGroups(filters, orderBy, orderDirection, offset, limit);
    }

    @Override
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        return delegate.getArtifactVersionComments(groupId, artifactId, version);
    }

    @Override
    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return delegate.isContentExists(contentHash);
    }

    @Override
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) throws RegistryStorageException {
        return delegate.isArtifactRuleExists(groupId, artifactId, rule);
    }

    @Override
    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return delegate.isGlobalRuleExists(rule);
    }

    @Override
    public boolean isRoleMappingExists(String principalId) {
        return delegate.isRoleMappingExists(principalId);
    }

    @Override
    public Optional<Long> contentIdFromHash(String contentHash) {
        return delegate.contentIdFromHash(contentHash);
    }

    @Override
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return delegate.getEnabledArtifactContentIds(groupId, artifactId);
    }

    @Override
    public List<String> getArtifactVersions(String groupId, String artifactId, RetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersions(groupId, artifactId, behavior);
    }

    @Override
    public GAV getBranchTip(GA ga, BranchId branchId, RetrievalBehavior behavior) {
        return delegate.getBranchTip(ga, branchId, behavior);
    }

    @Override
    public VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit) {
        return delegate.getBranchVersions(ga, branchId, offset, limit);
    }

    @Override
    public BranchSearchResultsDto getBranches(GA ga, int offset, int limit) {
        return delegate.getBranches(ga, offset, limit);
    }

    @Override
    public BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId) {
        return delegate.getBranchMetaData(ga, branchId);
    }
}
