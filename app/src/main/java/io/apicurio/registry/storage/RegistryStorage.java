package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
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
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * The artifactStore layer for the registry.
 *
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
     * Is the storage initialized and ready to be used?
     * This state SHOULD NOT change again during operation,
     * and is used for K8s readiness probes, among other things.
     * This operation should be fast.
     *
     * @return true if yes, false if no
     */
    boolean isReady();

    /**
     * Is the storage ready AND alive, meaning able to be used?
     * This state MAY change multiple times during operation,
     * and is used for K8s liveness probes, among other things.
     * This operation should be fast.
     *
     * @return true if yes, false if no
     */
    boolean isAlive();


    boolean isReadOnly();


    @Transactional
    List<Long> getArtifactContentIds(String groupId, String artifactId);

    /**
     * Update artifact state.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param state
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactState(String groupId, String artifactId, ArtifactState state)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Update artifact state.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @param state
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactState(String groupId, String artifactId, String version, ArtifactState state)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Creates a new artifact (from the given value) in the artifactStore.  The artifactId must be unique
     * within the given artifact group.  Returns a map of meta-data generated by the artifactStore layer, such as the
     * generated, globally unique globalId of the new value.
     * If the group did not exist previously it is created automatically.
     *
     * @param groupId      (optional)
     * @param artifactId
     * @param version      (optional)
     * @param artifactType
     * @param content
     * @param references
     * @throws ArtifactAlreadyExistsException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto createArtifact(String groupId, String artifactId, String version, String artifactType,
                                       ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, RegistryStorageException;

    /**
     * Creates a new artifact (from the given value including metadata) in the artifactStore.  The artifactId must be unique
     * within the given artifact group. Returns a map of meta-data generated by the artifactStore layer, such as the
     * generated, globally unique globalId of the new value.
     *
     * @param groupId      (optional)
     * @param artifactId
     * @param version      (optional)
     * @param artifactType
     * @param content
     * @param metaData
     * @throws ArtifactAlreadyExistsException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                   String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactAlreadyExistsException, RegistryStorageException;

    /**
     * Deletes an artifact by its group and unique id. Returns list of artifact versions.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> deleteArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Deletes all artifacts in the given group. DOES NOT delete the group.
     *
     * @param groupId (optional)
     * @throws RegistryStorageException
     */
    void deleteArtifacts(String groupId) throws RegistryStorageException;

    /**
     * Gets the most recent version of the value of the artifact with the given group and ID.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    StoredArtifactDto getArtifact(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the most recent version of the value of the artifact with the given group and ID.
     */
    StoredArtifactDto getArtifact(String groupId, String artifactId, ArtifactRetrievalBehavior behavior) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets some artifact content by the unique contentId.  This method of getting content
     * from storage does not allow extra meta-data to be returned, because the contentId only
     * points to a piece of content/data - it is divorced from any artifact version.
     *
     * @param contentId
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     */
    ContentWrapperDto getArtifactByContentId(long contentId) throws ContentNotFoundException, RegistryStorageException;

    /**
     * Gets some artifact content by the SHA-256 hash of that content.  This method of getting content
     * from storage does not allow extra meta-data to be returned, because the content hash only
     * points to a piece of content/data - it is divorced from any artifact version.
     *
     * @param contentHash
     * @throws ContentNotFoundException
     * @throws RegistryStorageException
     */
    ContentWrapperDto getArtifactByContentHash(String contentHash) throws ContentNotFoundException, RegistryStorageException;

    /**
     * Get artifact metadata for a given contentId
     *
     * @param contentId
     * @return
     */
    List<ArtifactMetaDataDto> getArtifactVersionsByContentId(long contentId);

    /**
     * Get artifact contentids for a given pair of coordinates
     *
     * @param groupId
     * @param artifactId
     * @return
     */
    List<Long> getEnabledArtifactContentIds(String groupId, String artifactId);

    /**
     * Updates the artifact value by storing the given value as a new version of the artifact.  Previous value
     * is NOT overwitten.  Returns a map of meta-data generated by the artifactStore layer, such as the generated,
     * globally unique globalId of the new value.
     *
     * @param groupId      (optional)
     * @param artifactId
     * @param version      (optional)
     * @param artifactType
     * @param content
     * @param references
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto updateArtifact(String groupId, String artifactId, String version,
                                       String artifactType, ContentHandle content, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Updates the artifact value by storing the given value and metadata as a new version of the artifact.  Previous value
     * is NOT overwitten.  Returns a map of meta-data generated by the artifactStore layer, such as the generated,
     * globally unique globalId of the new value.
     *
     * @param groupId      (optional)
     * @param artifactId
     * @param version      (optional)
     * @param artifactType
     * @param content
     * @param metaData
     * @param references
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                   String artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Get all artifact ids.
     * ---
     * Note: This should only be used in older APIs such as the registry V1 REST API and the Confluent API
     * ---
     *
     * @param limit the limit of artifacts
     * @return all artifact ids
     */
    Set<String> getArtifactIds(Integer limit);

    /**
     * Search artifacts by given criteria
     *
     * @param filters        the set of filters to apply when searching
     * @param orderBy        the field to order by
     * @param orderDirection the direction to order the results
     * @param offset         the number of artifacts to skip
     * @param limit          the result size limit
     */
    ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection,
                                             int offset, int limit);

    ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for an artifact by group and ID.  This will include client-editable meta-data such as
     * name and description, but also generated meta-data such as "modifedOn" and "globalId".
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId, ArtifactRetrievalBehavior behavior)
            throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the metadata of the version that matches content.
     *
     * @param groupId            (optional)
     * @param artifactId
     * @param canonical
     * @param content
     * @param artifactReferences
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, boolean canonical,
                                                          ContentHandle content, List<ArtifactReferenceDto> artifactReferences) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for an artifact by global ID.  This will include client-editable meta-data such as
     * name and description, but also generated meta-data such as "modifedOn" and "globalId".
     *
     * @param globalId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactMetaDataDto getArtifactMetaData(long globalId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Updates the stored meta-data for an artifact by group and ID.  Only the client-editable meta-data can be updated.  Client
     * editable meta-data includes e.g. name and description. TODO what if set to null?
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Updates the owner (created-by) for an artifact by group and ID.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param owner
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactOwner(String groupId, String artifactId, ArtifactOwnerDto owner) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets a list of rules configured for a specific Artifact (by group and ID).  This will return only the names of the
     * rules.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<RuleType> getArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Creates an artifact rule for a specific Artifact.  If the named rule already exists for the artifact, then
     * this should fail.
     *
     * @param groupId    (optional)
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
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactRules(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets all of the information for a single rule configured on a given artifact.
     *
     * @param groupId    (optional)
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
     * @param groupId    (optional)
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
     * @param groupId    (optional)
     * @param artifactId
     * @param rule
     * @throws ArtifactNotFoundException
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactRule(String groupId, String artifactId, RuleType rule) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException;

    /**
     * Gets a sorted set of all artifact versions that exist for a given artifact.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> getArtifactVersions(String groupId, String artifactId) throws ArtifactNotFoundException, RegistryStorageException;


    /**
     * Gets a sorted set of all artifact versions that exist for a given artifact.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    List<String> getArtifactVersions(String groupId, String artifactId, ArtifactRetrievalBehavior behavior) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Fetch the versions of the given artifact
     *
     * @param groupId    (optional)
     * @param artifactId the artifact used to fetch versions
     * @param limit      the result size limit
     * @param offset     the number of versions to skip
     * @return the artifact versions, limited
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    VersionSearchResultsDto searchVersions(String groupId, String artifactId, int offset, int limit) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored artifact content for the artifact version with the given unique global ID.
     *
     * @param globalId
     * @throws ArtifactNotFoundException
     * @throws RegistryStorageException
     */
    StoredArtifactDto getArtifactVersion(long globalId) throws ArtifactNotFoundException, RegistryStorageException;

    /**
     * Gets the stored value for a single version of a given artifact.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    StoredArtifactDto getArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Deletes a single version of a given artifact.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactVersion(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets the stored meta-data for a single version of an artifact.  This will return all meta-data for the
     * version, including any user edited meta-data along with anything generated by the artifactStore.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Updates the user-editable meta-data for a single version of a given artifact.  Only the client-editable
     * meta-data can be updated.  Client editable meta-data includes e.g. name and description.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @param metaData
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void updateArtifactVersionMetaData(String groupId, String artifactId, String version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Deletes the user-editable meta-data for a singel version of a given artifact.  Only the client-editable
     * meta-data is deleted.  Any meta-data generated by the artifactStore is preserved.
     *
     * @param groupId    (optional)
     * @param artifactId
     * @param version
     * @throws ArtifactNotFoundException
     * @throws VersionNotFoundException
     * @throws RegistryStorageException
     */
    void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    /**
     * Gets a list of all global rule names.
     *
     * @throws RegistryStorageException
     */
    List<RuleType> getGlobalRules() throws RegistryStorageException;

    /**
     * Creates a single global rule.  Duplicates (by name) are not allowed.  Stores the rule name and configuration.
     *
     * @param rule
     * @param config
     * @throws RuleAlreadyExistsException
     * @throws RegistryStorageException
     */
    void createGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException;

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
    void updateGlobalRule(RuleType rule, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Deletes a single global rule.
     *
     * @param rule
     * @throws RuleNotFoundException
     * @throws RegistryStorageException
     */
    void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException;

    /**
     * Creates a new empty group and stores it's metadata. When creating an artifact the group is automatically created in it does not exist.
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
     * @param groupId
     * @param dto
     */
    void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto);
    void updateGroupMetaData(String groupId, String description, Map<String, String> labels, String modifiedBy, Date modifiedOn);

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
     * Called to export all data in the registry.  Caller provides a handle to handle the data/entities.  This
     * should be used to stream the data from the storage to some output source (e.g. a HTTP response).  It is
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
     * @param preserveGlobalId  Preserve global ids. If false, global ids will be set to next id in global id sequence.
     * @param preserveContentId Preserve content id. If false, content ids will be set to the next ids in the content id sequence. Content-Version mapping will be preserved.
     * @throws RegistryStorageException
     */
    void importData(EntityInputStream entities, boolean preserveGlobalId, boolean preserveContentId) throws RegistryStorageException;

    /**
     * Counts the total number of artifacts
     *
     * @return artifacts count
     * @throws RegistryStorageException
     */
    long countArtifacts() throws RegistryStorageException;

    /**
     * Counts the total number of versions for one artifact
     *
     * @return
     * @throws RegistryStorageException
     */
    long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException;

    /**
     * Counts the total number of versions for all artifacts
     *
     * @return
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
    void createRoleMapping(String principalId, String role, String principalName) throws RegistryStorageException;

    /**
     * Gets the list of all the role mappings in the registry.
     */
    List<RoleMappingDto> getRoleMappings() throws RegistryStorageException;

    /**
     * Search for role mappings.
     * @param offset         the number of artifacts to skip
     * @param limit          the result size limit
     */
    RoleMappingSearchResultsDto searchRoleMappings(int offset, int limit) throws RegistryStorageException;
    
    /**
     * Gets the details of a single role mapping.
     *
     * @param principalId
     */
    RoleMappingDto getRoleMapping(String principalId) throws RegistryStorageException;

    /**
     * Gets the role for a single user.  This returns null if there is no role mapped for
     * the given principal.
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
     * Called to create a single-use download "link".  This can then be consumed using
     * "consumeDownload()".  Used to support browser flows for features like /admin/export.
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
     * Called to delete any expired rows in the downloads table.  This is basically cleaning up
     * any single-use download links that were never "clicked".
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
     * Gets a list of properties with stale state.  This would inform a caching
     * layer that the cache should be invalidated.
     *
     * @param since instant representing the last time this check was done (has anything changed since)
     * @return a list of stale configs
     */
    List<DynamicConfigPropertyDto> getStaleConfigProperties(Instant since);


    /**
     * @return The artifact references resolved as a map containing the reference name as key and the referenced artifact content.
     */
    Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references);

    /**
     * @param groupId
     * @param artifactId
     * @return true if an artifact exists with the coordinates passed as parameters
     * @throws RegistryStorageException
     */
    boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException;

    /**
     * @param groupId
     * @return true if a group exists with the id passed as parameter
     * @throws RegistryStorageException
     */
    boolean isGroupExists(String groupId) throws RegistryStorageException;

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return list of content ids of schemas that references artifact
     */
    List<Long> getContentIdsReferencingArtifact(String groupId, String artifactId, String version);

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return list of global ids of schemas that references artifact
     */
    List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId, String version);

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @return the list of inbound references to the given artifact version
     */
    List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId, String version);

    /**
     * @param groupId
     * @param artifactId
     * @return true if an artifact version exists with the coordinates passed as parameters
     * @throws RegistryStorageException
     */
    boolean isArtifactVersionExists(String groupId, String artifactId, String version) throws RegistryStorageException;

    /**
     * Search groups by given criteria
     *
     * @param filters        the set of filters to apply when searching
     * @param orderBy        the field to order by
     * @param orderDirection the direction to order the results
     * @param offset         the number of artifacts to skip
     * @param limit          the result size limit
     */
    GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit);


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
    void updateArtifactVersionComment(String groupId, String artifactId, String version, String commentId, String value);


    CommentDto createArtifactVersionCommentRaw(String groupId, String artifactId, String version, IdGenerator commentId,
                                               String owner, Date createdOn, String value);


    void resetGlobalId();


    void resetContentId();


    void resetCommentId();


    long nextContentId();


    long nextGlobalId();


    long nextCommentId();


    void importComment(CommentEntity entity);


    void importGroup(GroupEntity entity);


    void importGlobalRule(GlobalRuleEntity entity);


    void importContent(ContentEntity entity);


    void importArtifactVersion(ArtifactVersionEntity entity);


    void importArtifactRule(ArtifactRuleEntity entity);


    void importArtifactBranch(ArtifactBranchEntity entity);


    boolean isContentExists(String contentHash) throws RegistryStorageException;


    boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) throws RegistryStorageException;


    boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException;


    boolean isRoleMappingExists(String principalId);


    void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash);


    Optional<Long> contentIdFromHash(String contentHash);


    ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                   String artifactType, String contentHash, String owner, Date createdOn,
                                                   EditableArtifactMetaDataDto metaData,
                                                   IdGenerator globalIdGenerator);


    ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
                                                   String artifactType, String contentHash, String owner,
                                                   Date createdOn, EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException;


    /**
     * @return map from an artifact branch to a sorted list of GAVs, branch tip (latest) version first.
     */
    Map<BranchId, List<GAV>> getArtifactBranches(GA ga);


    /**
     * @return sorted list of GAVs, branch tip (latest) version first.
     */
    List<GAV> getArtifactBranch(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior);


    /**
     * Add a version to the artifact branch. The branch is created if it does not exist. The version becomes a new branch tip (latest).
     * Not supported for the "latest" branch.
     */
    void createOrUpdateArtifactBranch(GAV gav, BranchId branchId);


    /**
     * Replace the content of the artifact branch with a new sequence of versions.
     * Not supported for the "latest" branch.
     */
    void createOrReplaceArtifactBranch(GA ga, BranchId branchId, List<VersionId> versions);


    /**
     * @return GAV identifier of the branch tip (latest) version in the artifact branch.
     */
    GAV getArtifactBranchTip(GA ga, BranchId branchId, ArtifactRetrievalBehavior behavior);


    /**
     * Delete artifact branch.
     * Not supported for the "latest" branch.
     */
    void deleteArtifactBranch(GA ga, BranchId branchId);


    enum ArtifactRetrievalBehavior {
        DEFAULT,
        /**
         * Skip artifact versions with DISABLED state
         */
        SKIP_DISABLED_LATEST
    }

}
