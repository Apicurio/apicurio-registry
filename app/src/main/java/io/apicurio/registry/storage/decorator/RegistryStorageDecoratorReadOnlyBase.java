package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
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
    public StoredArtifactDto getArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifact(groupId, artifactId);
    }


    @Override
    public StoredArtifactDto getArtifact(String groupId, String artifactId, ArtifactRetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifact(groupId, artifactId, behavior);
    }


    @Override
    public ContentWrapperDto getArtifactByContentId(long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getArtifactByContentId(contentId);
    }


    @Override
    public ContentWrapperDto getArtifactByContentHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return delegate.getArtifactByContentHash(contentHash);
    }


    @Override
    public List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId) {
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
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId, ArtifactRetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactMetaData(groupId, artifactId, behavior);
    }


    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
                                                                 boolean canonical, ContentHandle content, List<ArtifactReferenceDto> artifactReferences)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(groupId, artifactId, canonical, content, artifactReferences);
    }


    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactMetaData(globalId);
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
    public VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.searchVersions(groupId, artifactId, offset, limit);
    }


    @Override
    public StoredArtifactDto getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersion(globalId);
    }


    @Override
    public StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersion(groupId, artifactId, version);
    }


    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
                                                                 String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersionMetaData(groupId, artifactId, version);
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
    public Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references) {
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
    public List<Long> getArtifactContentIds(String groupId, String artifactId) {
        return delegate.getArtifactContentIds(groupId, artifactId);
    }


    @Override
    public List<Long> getContentIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return delegate.getContentIdsReferencingArtifact(groupId, artifactId, version);
    }


    @Override
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId, String version) {
        return delegate.getGlobalIdsReferencingArtifact(groupId, artifactId, version);
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
    public List<String> getArtifactVersions(String groupId, String artifactId, ArtifactRetrievalBehavior behavior) throws ArtifactNotFoundException, RegistryStorageException {
        return delegate.getArtifactVersions(groupId, artifactId, behavior);
    }


    @Override
    public GAV getArtifactBranchTip(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior) {
        return delegate.getArtifactBranchTip(ga, branchId, behavior);
    }


    @Override
    public Map<BranchId, List<GAV>> getArtifactBranches(GA ga) {
        return delegate.getArtifactBranches(ga);
    }


    @Override
    public List<GAV> getArtifactBranch(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior) {
        return delegate.getArtifactBranch(ga, branchId, behavior);
    }
}
