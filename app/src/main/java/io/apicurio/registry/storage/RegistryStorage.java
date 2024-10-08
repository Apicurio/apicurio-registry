package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.RuleType;
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
 * The artifactStore layer for the registry.
 */
public interface RegistryStorage extends DynamicConfigStorage {

    /**
     * The storage name
     */
    String storageName();

    /**
     * Performs the required operations for initializing the Registry storage
     */
    void initialize();

    /**
     * Is the storage initialized and ready to be used? This state SHOULD NOT change again during operation,
     * and is used for K8s readiness probes, among other things. This operation should be fast.
     *
     * @return true if yes, false if no
     */
    boolean isReady();

    /**
     * Is the storage ready AND alive, meaning able to be used? This state MAY change multiple times during
     * operation, and is used for K8s liveness probes, among other things. This operation should be fast.
     *
     * @return true if yes, false if no
     */
    boolean isAlive();

    /**
     * Is the registry storage set to read-only mode?
     */
    boolean isReadOnly();

    /**
     * Returns true if the storage is empty (and ready for data to be imported).
     */
    boolean isEmpty();

    /**
     * Create a new artifact in the storage, with or without an initial/first version. Throws an exception if
     * the artifact already exists. The first version information can be null, in which case an empty artifact
     * (no versions) is created. Returns the metadata of the newly created artifact and (optionally) the
     * metadata of the first version.
     */
    Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId, String artifactId,
            String artifactType, EditableArtifactMetaDataDto artifactMetaData, String version,
            ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean dryRun)
            throws ArtifactAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes an artifact by its group and unique id. Returns list of artifact versions.
     *
     * @param groupId (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Deletes all artifacts in the given group. DOES NOT delete the group.
     *
     * @param groupId (optional)
     * @throws RegistryStorageException
     */
    void deleteArtifacts(String groupId) throws RegistryStorageException;

    /**
     * Gets some artifact content by the unique contentId. This method of getting content from storage does
     * not allow extra meta-data to be returned, because the contentId only points to a piece of content/data
     * - it is divorced from any artifact version.
     *
     * @param contentId
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     */
    ContentWrapperDto getContentById(long contentId)
            throws ContentNotFoundException, RegistryStorageException;

    /**
     * Gets some artifact content by the SHA-256 hash of that content. This method of getting content from
     * storage does not allow extra meta-data to be returned, because the content hash only points to a piece
     * of content/data - it is divorced from any artifact version.
     *
     * @param contentHash
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     */
    ContentWrapperDto getContentByHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException;

    /**
     * Get a list of all artifact versions that refer to the same content.
     *
     * @param contentId
     */
    List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId);

    /**
     * Get all content IDs for every (non-DISABLED) version of an artifact.
     *
     * @param groupId
     * @param artifactId
     */
    List<Long> getEnabledArtifactContentIds(String groupId, String artifactId);

    /**
     * Creates a new version of an artifact. Returns a map of meta-data generated by the artifactStore layer,
     * such as the generated, globally unique globalId of the new version. Note: the artifactType is passed in
     * because it is needed when generating canonical content hashes.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param artifactType
     * @param content
     * @param metaData
     * @param branches
     * @param dryRun
     */
    ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId, String version,
            String artifactType, ContentWrapperDto content, EditableVersionMetaDataDto metaData,
            List<String> branches, boolean dryRun)
            throws ArtifactNotFoundException, VersionAlreadyExistsException, RegistryStorageException;

    /**
     * Get all artifact ids. --- Note: This should only be used in older APIs such as the registry V1 REST API
     * and the Confluent API ---
     *
     * @param limit the limit of artifacts
     * @return all artifact ids
     */
    Set<String> getArtifactIds(Integer limit);

    /**
     * Search artifacts by given criteria
     *
     * @param filters the set of filters to apply when searching
     * @param orderBy the field to order by
     * @param orderDirection the direction to order the results
     * @param offset the number of artifacts to skip
     * @param limit the result size limit
     */
    ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit);

    /**
     * Get metadata for an artifact using GA information.
     * 
     * @param groupId
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the metadata of the version that matches content.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param canonical
     * @param content
     * @param artifactReferences
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactVersionMetaDataDto getArtifactVersionMetaDataByContent(String groupId, String artifactId,
            boolean canonical, TypedContent content, List<ArtifactReferenceDto> artifactReferences)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Updates the stored meta-data for an artifact by group and ID. Only the client-editable meta-data can be
     * updated. Client editable meta-data includes e.g. name and description
     *
     * @param groupId (optional)
     * @param artifactId
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Get all rules configured for a group.
     *
     * @param groupId
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     */
    List<RuleType> getGroupRules(String groupId) throws GroupNotFoundException, RegistryStorageException;

    /**
     * Create/configure a rule for a group.
     *
     * @param groupId
     * @param rule
     * @param config
     * @throws GroupNotFoundException
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    void createGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws GroupNotFoundException, RuleAlreadyExistsException, RegistryStorageException;

    /**
     * Delete all rules configured for a group.
     *
     * @param groupId
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     */
    void deleteGroupRules(String groupId) throws GroupNotFoundException, RegistryStorageException;

    /**
     * Update the configuration for a specific rule in a group.
     *
     * @param groupId
     * @param rule
     * @param config
     * @throws GroupNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void updateGroupRule(String groupId, RuleType rule, RuleConfigurationDto config)
            throws GroupNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Delete (unconfigure) a single rule for a group.
     *
     * @param groupId
     * @param rule
     * @throws GroupNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void deleteGroupRule(String groupId, RuleType rule)
            throws GroupNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Gets the current configuration of a single rule in the group.
     *
     * @param groupId
     * @param rule
     * @throws GroupNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    RuleConfigurationDto getGroupRule(String groupId, RuleType rule)
            throws GroupNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Gets a list of rules configured for a specific Artifact (by group and ID). This will return only the
     * names of the rules.
     *
     * @param groupId (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<RuleType> getArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Creates an artifact rule for a specific Artifact. If the named rule already exists for the artifact,
     * then this should fail.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    void createArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes all rules stored/configured for the artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactRules(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets all of the information for a single rule configured on a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    RuleConfigurationDto getArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Updates the configuration information for a single rule on a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param rule
     * @param config
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactRule(String groupId, String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Deletes a single stored/configured rule for a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactRule(String groupId, String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Gets a sorted set of all artifact versions that exist for a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> getArtifactVersions(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets a sorted set of all artifact versions that exist for a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> getArtifactVersions(String groupId, String artifactId, RetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Fetch the versions of the given artifact
     *
     * @param filters the search filters
     * @param limit the result size limit
     * @param offset the number of versions to skip
     * @return the artifact versions, limited
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored artifact content for the artifact version with the given unique global ID.
     *
     * @param globalId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    StoredArtifactVersionDto getArtifactVersionContent(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored value for a single version of a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Deletes a single version of a given artifact.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for a single version of an artifact. This will return all meta-data for the
     * version, including any user edited meta-data along with anything generated by the artifactStore.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for a single version of an artifact. This will return all meta-data for the
     * version, including any user edited meta-data along with anything generated by the artifactStore.
     *
     * @param globalId
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId)
            throws VersionNotFoundException, RegistryStorageException;

    /**
     * Updates the user-editable meta-data for a single version of a given artifact. Only the client-editable
     * meta-data can be updated. Client editable meta-data includes e.g. name and description.
     *
     * @param groupId (optional)
     * @param artifactId
     * @param version
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets a list of all global rule names.
     *
     * @throws RegistryStorageException
     */
    List<RuleType> getGlobalRules() throws RegistryStorageException;

    /**
     * Creates a single global rule. Duplicates (by name) are not allowed. Stores the rule name and
     * configuration.
     *
     * @param rule
     * @param config
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes all of the globally configured rules.
     *
     * @throws RegistryStorageException
     */
    void deleteGlobalRules() throws RegistryStorageException;

    /**
     * Gets all information about a single global rule.
     *
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    RuleConfigurationDto getGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Updates the configuration settings for a single global rule.
     *
     * @param rule
     * @param config
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException;

    /**
     * Deletes a single global rule.
     *
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Creates a new empty group and stores it's metadata. When creating an artifact the group is
     * automatically created in it does not exist.
     *
     * @param group
     * @throws GroupAlreadyExistsException
     * @throws RegistryStorageException
     */
    void createGroup(GroupMetaDataDto group) throws GroupAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes a group identified by the given groupId and DELETES ALL resources related to this group
     *
     * @param groupId (optional)
     * @throws GroupNotFoundException
     * @throws RegistryStorageException
     */
    void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException;

    /**
     * Updates the metadata for a group.
     * 
     * @param groupId
     * @param dto
     */
    void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto);

    /**
     * Get all groupIds
     *
     * @param limit
     * @throws RegistryStorageException
     */
    List<String> getGroupIds(Integer limit) throws RegistryStorageException;

    /**
     * Get the metadata information for a group identified by the given groupId
     *
     * @param groupId (optional)
     */
    GroupMetaDataDto getGroupMetaData(String groupId) throws GroupNotFoundException, RegistryStorageException;

    /**
     * Called to export all data in the registry. Caller provides a handle to handle the data/entities. This
     * should be used to stream the data from the storage to some output source (e.g. a HTTP response). It is
     * important that the full dataset is *not* kept in memory.
     *
     * @param handler
     * @throws RegistryStorageException
     */
    void exportData(Function<Entity, Void> handler) throws RegistryStorageException;

    /**
     * Called to import previously exported data into the registry.
     *
     * @param entities
     * @param preserveGlobalId Preserve global ids. If false, global ids will be set to next id in global id
     *            sequence.
     * @param preserveContentId Preserve content id. If false, content ids will be set to the next ids in the
     *            content id sequence. Content-Version mapping will be preserved.
     * @throws RegistryStorageException
     */
    void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId)
            throws RegistryStorageException;

    /**
     * Called to upgrade and import previously exported data into the registry. It upgrades the data structure
     * from v2 to v3 and imports the data into Registry.
     *
     * @param entities
     * @param preserveGlobalId Preserve global ids. If false, global ids will be set to next id in global id
     *            sequence.
     * @param preserveContentId Preserve content id. If false, content ids will be set to the next ids in the
     *            content id sequence. Content-Version mapping will be preserved.
     * @throws RegistryStorageException
     */
    void upgradeData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId);

    /**
     * Counts the total number of artifacts in the registry.
     *
     * @return artifacts count
     * @throws RegistryStorageException
     */
    long countArtifacts() throws RegistryStorageException;

    /**
     * Counts the number of versions for one artifact.
     *
     * @throws RegistryStorageException
     */
    long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException;

    /**
     * Counts the number of active (not disabled) versions of an artifact.
     * 
     * @param groupId
     * @param artifactId
     */
    long countActiveArtifactVersions(String groupId, String artifactId) throws RegistryStorageException;

    /**
     * Counts the total number of versions for all artifacts
     *
     * @throws RegistryStorageException
     */
    long countTotalArtifactVersions() throws RegistryStorageException;

    /**
     * Creates a role mapping for a user.
     *
     * @param principalId
     * @param role
     * @param principalName
     */
    void createRoleMapping(String principalId, String role, String principalName)
            throws RegistryStorageException;

    /**
     * Gets the list of all the role mappings in the registry.
     */
    List<RoleMappingDto> getRoleMappings() throws RegistryStorageException;

    /**
     * Search for role mappings.
     * 
     * @param offset the number of artifacts to skip
     * @param limit the result size limit
     */
    RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit) throws RegistryStorageException;

    /**
     * Gets the details of a single role mapping.
     *
     * @param principalId
     */
    RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException;

    /**
     * Gets the role for a single user. This returns null if there is no role mapped for the given principal.
     *
     * @param principalId
     */
    String getRoleForPrincipal(String principalId) throws RegistryStorageException;

    /**
     * Updates a single role mapping.
     *
     * @param principalId
     * @param role
     */
    void updateRoleMapping(String principalId, String role) throws RegistryStorageException;

    /**
     * Deletes a single role mapping.
     *
     * @param principalId
     */
    void deleteRoleMapping(String principalId) throws RegistryStorageException;

    /**
     * Deletes ALL user data. Does not delete global data, such as log configuration.
     */
    void deleteAllUserData();

    /**
     * Called to create a single-use download "link". This can then be consumed using "consumeDownload()".
     * Used to support browser flows for features like /admin/export.
     *
     * @param context
     * @throws RegistryStorageException
     */
    String createDownload(DownloadContextDto context) throws RegistryStorageException;

    /**
     * Called to consume a download from the DB (single-use) and return its context info.
     *
     * @param downloadId
     */
    DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException;

    /**
     * Called to delete any expired rows in the downloads table. This is basically cleaning up any single-use
     * download links that were never "clicked".
     *
     * @throws RegistryStorageException
     */
    void deleteAllExpiredDownloads() throws RegistryStorageException;

    /**
     * Gets the raw value of a property, bypassing any caching that might be enabled.
     *
     * @param propertyName the name of a property
     * @return the raw value
     */
    DynamicConfigPropertyDto getRawConfigProperty(String propertyName);

    /**
     * Gets a list of properties with stale state. This would inform a caching layer that the cache should be
     * invalidated.
     *
     * @param since instant representing the last time this check was done (has anything changed since)
     * @return a list of stale configs
     */
    List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since);

    ContentWrapperDto getContentByReference(ArtifactReferenceDto reference);

    /**
     * Quickly checks for the existence of a given artifact.
     * 
     * @param groupId
     * @param artifactId
     * @return true if an artifact exists with the coordinates passed as parameters
     * @throws RegistryStorageException
     */
    boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException;

    /**
     * Quickly checks for the existence of a given group.
     * 
     * @param groupId
     * @return true if a group exists with the id passed as parameter
     * @throws RegistryStorageException
     */
    boolean isGroupExists(String groupId) throws RegistryStorageException;

    /**
     * Gets a list of content IDs that have at least one reference to the given artifact version.
     * 
     * @param groupId
     * @param artifactId
     * @param version
     * @return list of content ids of schemas that references artifact
     */
    List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId, String version);

    /**
     * Gets a list of global IDs that have at least one reference to the given artifact version.
     * 
     * @param groupId
     * @param artifactId
     * @param version
     * @return list of global ids of schemas that references artifact
     */
    List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId, String version);

    /**
     * Gets a list of inbound references for a given artifact version.
     * 
     * @param groupId
     * @param artifactId
     * @param version
     * @return the list of inbound references to the given artifact version
     */
    List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version);

    /**
     * Quickly checks for the existence of a specific artifact version.
     * 
     * @param groupId
     * @param artifactId
     * @return true if an artifact version exists with the coordinates passed as parameters
     * @throws RegistryStorageException
     */
    boolean isArtifactVersionExists(String groupId, String artifactId, String version)
            throws RegistryStorageException;

    /**
     * Search groups by given criteria
     *
     * @param filters the set of filters to apply when searching
     * @param orderBy the field to order by
     * @param orderDirection the direction to order the results
     * @param offset the number of artifacts to skip
     * @param limit the result size limit
     */
    GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit);

    /**
     * Creates a new comment for an artifact version.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param value
     */
    CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, String value);

    /**
     * Deletes a single comment for an artifact version.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param commentId
     */
    void deleteArtifactVersionComment(String groupId, String artifactId, String version, String commentId);

    /**
     * Returns all comments for the given artifact version.
     *
     * @param groupId
     * @param artifactId
     * @param version
     */
    List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version);

    /**
     * Updates a single comment.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param commentId
     * @param value
     */
    void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId,
            String value);

    void resetGlobalId();

    void resetContentId();

    void resetCommentId();

    long nextContentId();

    long nextGlobalId();

    long nextCommentId();

    void importComment(CommentEntity entity);

    void importGroup(GroupEntity entity);

    void importGroupRule(GroupRuleEntity entity);

    void importGlobalRule(GlobalRuleEntity entity);

    void importContent(ContentEntity entity);

    void importArtifact(ArtifactEntity entity);

    void importArtifactVersion(ArtifactVersionEntity entity);

    void importArtifactRule(ArtifactRuleEntity entity);

    void importBranch(BranchEntity entity);

    boolean isContentExists(String contentHash) throws RegistryStorageException;

    boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule)
            throws RegistryStorageException;

    boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException;

    boolean isRoleMappingExists(String principalId);

    void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash);

    Optional<Long> contentIdFromHash(String contentHash);

    BranchSearchResultsDto getBranches(GA ga, int offset, int limit);

    BranchMetaDataDto createBranch(GA ga, BranchId branchId, String description, List<String> versions);

    BranchMetaDataDto getBranchMetaData(GA ga, BranchId branchId);

    void updateBranchMetaData(GA ga, BranchId branchId, EditableBranchMetaDataDto dto);

    void deleteBranch(GA ga, BranchId branchId);

    GAV getBranchTip(GA ga, BranchId branchId, RetrievalBehavior behavior);

    VersionSearchResultsDto getBranchVersions(GA ga, BranchId branchId, int offset, int limit);

    void replaceBranchVersions(GA ga, BranchId branchId, List<VersionId> versions);

    void appendVersionToBranch(GA ga, BranchId branchId, VersionId version);

    /**
     * Triggers a snapshot creation of the internal database.
     *
     * @throws RegistryStorageException
     */
    String triggerSnapshotCreation() throws RegistryStorageException;

    /**
     * Creates the snapshot of the internal database based on configuration.
     *
     * @param snapshotLocation
     * @throws RegistryStorageException
     */
    String createSnapshot(String snapshotLocation) throws RegistryStorageException;

    /**
     * Creates a new event row in the outbox table.
     *
     * @throws RegistryStorageException
     */
    String createEvent(OutboxEvent event);

    /**
     * true if the underlying Registry storage supports emitting events to the database.
     *
     * @throws RegistryStorageException
     */
    boolean supportsDatabaseEvents();

    enum RetrievalBehavior {
        DEFAULT,
        /**
         * Skip artifact versions with DISABLED state
         */
        SKIP_DISABLED_LATEST
    }

}
