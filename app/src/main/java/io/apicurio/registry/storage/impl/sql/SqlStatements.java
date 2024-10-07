package io.apicurio.registry.storage.impl.sql;

import java.util.List;

/**
 * Returns SQL statements used by the JDB artifactStore implementation. There are different implementations of
 * this interface depending on the database being used.
 */
public interface SqlStatements {

    /**
     * Gets the database type associated with these statements.
     */
    public String dbType();

    /**
     * Returns true if the given exception represents a primary key violation.
     */
    public boolean isPrimaryKeyViolation(Exception error);

    /**
     * Returns true if the given exception represents a foreign key violation.
     */
    public boolean isForeignKeyViolation(Exception error);

    /**
     * A statement that returns 'true' if the database has already been initialized.
     */
    public String isDatabaseInitialized();

    /**
     * A sequence of statements needed to initialize the database.
     */
    public List<String> databaseInitialization();

    /**
     * A sequence of statements needed to upgrade the DB from one version to another.
     */
    public List<String> databaseUpgrade(int fromVersion, int toVersion);

    /**
     * A statement that returns the current DB version (pulled from the "apicurio" attribute table).
     */
    public String getDatabaseVersion();

    /**
     * A statement used to insert a row into the globalrules table.
     */
    public String insertGlobalRule();

    /**
     * A statement used to select all global rules.
     */
    public String selectGlobalRules();

    /**
     * A statement used to select a single global rule by its type/id.
     */
    public String selectGlobalRuleByType();

    /**
     * A statement used to delete a row from the globalrules table.
     */
    public String deleteGlobalRule();

    /**
     * A statement used to delete all rows in the globalrules table.
     */
    public String deleteGlobalRules();

    /**
     * A statement used to update information about a global rule.
     */
    public String updateGlobalRule();

    /**
     * A statement used to insert a row in the artifacts table.
     */
    public String insertArtifact();

    /**
     * A statement used to update the 'version' column of the 'versions' table by globalId. The value of the
     * "versionOrder" column is copied into the "version" column.
     */
    public String autoUpdateVersionForGlobalId();

    /**
     * A statement used to insert a row in the versions table.
     */
    public String insertVersion(boolean firstVersion);

    /**
     * A statement used when updating artifact version content. Updates the versions table with a new
     * contentId, modifiedBy, and modifiedOn.
     */
    public String updateArtifactVersionContent();

    /**
     * A statement used to select a single row in the versions table by globalId.
     */
    public String selectArtifactVersionMetaDataByGlobalId();

    /**
     * A statement used to select a single row in the versions by artifactId and content hash.
     */
    public String selectArtifactVersionMetaDataByContentHash();

    /**
     * A statement used to select a single row in the versions by artifactId and content id.
     */
    public String selectArtifactVersionMetaDataByContentId();

    /**
     * A statement used to select a single row in the versions by artifactId and canonical content hash.
     */
    public String selectArtifactVersionMetaDataByCanonicalHash();

    /**
     * A statement to select the content of an artifact version from the versions table by globalId.
     */
    public String selectArtifactVersionContentByGlobalId();

    /**
     * A statement used to select all version numbers (only) for a given artifactId.
     */
    public String selectArtifactVersions();

    /**
     * A statement used to select non-disabled version numbers (only) for a given artifactId.
     */
    public String selectArtifactVersionsNotDisabled();

    /**
     * A statement used to select all versions for a given artifactId.
     */
    public String selectAllArtifactVersions();

    /**
     * A statement used to count the total # of artifacts.
     */
    public String selectAllArtifactCount();

    /**
     * A statement used to count the total # of versions for an artifact.
     */
    public String selectAllArtifactVersionsCount();

    /**
     * A statement used to count the total # of non-disabled versions for an artifact.
     */
    public String selectActiveArtifactVersionsCount();

    /**
     * A statement used to count the total # of versions for all artifact.
     */
    public String selectTotalArtifactVersionsCount();

    /**
     * A statement used to select artifact version metadata by artifactId and version.
     */
    public String selectArtifactVersionMetaData();

    /**
     * A statement to select the content of an artifact version from the versions table by artifactId +
     * version.
     */
    public String selectArtifactVersionContent();

    /**
     * A statement to select the content ids of an artifact for all versions.
     */
    public String selectArtifactContentIds();

    /**
     * A statement to insert a row in the "content" table.
     */
    public String insertContent();

    /**
     * A statement to update canonicalHash value in a row in the "content" table
     */
    public String updateContentCanonicalHash();

    /**
     * A statement to get a single artifact (latest version) meta-data by artifactId.
     */
    public String selectArtifactMetaData();

    /**
     * A statement to select the contentId of a row in the content table by hash value.
     */
    public String selectContentIdByHash();

    /**
     * A statement used to select artifact rules by artifactId.
     */
    public String selectArtifactRules();

    /**
     * A statement to insert a row into the 'rules' table (artifact rule).
     */
    public String insertArtifactRule();

    /**
     * A statement to get a single artifact rule from the 'rules' table by artifactId and rule type.
     */
    public String selectArtifactRuleByType();

    /**
     * A statement to update a single artifact rule.
     */
    public String updateArtifactRule();

    /**
     * A statement to update a single artifact name.
     */
    public String updateArtifactName();

    /**
     * A statement to update a single artifact description.
     */
    public String updateArtifactDescription();

    /**
     * A statement to update the modified by and modified on for an artifact.
     */
    public String updateArtifactModifiedByOn();

    /**
     * A statement to update a single artifact owner.
     */
    public String updateArtifactOwner();

    /**
     * A statement to update a single artifact labels.
     */
    public String updateArtifactLabels();

    /**
     * A statement to delete a single artifact rule.
     */
    public String deleteArtifactRule();

    /**
     * A statement to delete all rules for a single artifact.
     */
    public String deleteArtifactRules();

    /**
     * A statement to delete all rules for all artifacts.
     */
    public String deleteAllArtifactRules();

    /*
     * Statements to update the meta-data of a specific artifact version.
     */

    public String updateArtifactVersionNameByGAV();

    public String updateArtifactVersionDescriptionByGAV();

    public String updateArtifactVersionLabelsByGAV();

    public String updateArtifactVersionOwnerByGAV();

    public String updateArtifactVersionStateByGAV();

    /**
     * A statement to delete all rows in the group_labels table for a given group.
     */
    public String deleteGroupLabelsByGroupId();

    /**
     * A statement to delete all rows in the artifact_labels table for a given artifact.
     */
    public String deleteArtifactLabels();

    /**
     * A statement to delete the labels for a single artifact version.
     */
    public String deleteVersionLabelsByGAV();

    /**
     * A statement to delete all labels for a single artifact version by globalId
     */
    public String deleteVersionLabelsByGlobalId();

    /**
     * A statement to delete all labels for all versions for all artifacts
     */
    public String deleteVersionLabelsByAll();

    /**
     * A statement to delete all comments for all versions for all artifacts
     */
    public String deleteAllVersionComments();

    /**
     * A statement to delete all versions for all artifacts.
     */
    public String deleteAllVersions();

    /**
     * A statement to delete a single row from the artifacts table by artifactId.
     */
    public String deleteArtifact();

    /**
     * A statement to delete a all artifacts from the artifacts table by groupId.
     */
    public String deleteArtifactsByGroupId();

    /**
     * A statement to delete a all artifact rules by groupId.
     */
    public String deleteArtifactRulesByGroupId();

    /**
     * A statement to delete a all artifacts.
     */
    public String deleteAllArtifacts();

    /**
     * A statement to get all artifacts IDs.
     */
    public String selectArtifactIds();

    /**
     * A statement to update the state of an artifact version (by globalId);
     */
    public String updateArtifactVersionState();

    /**
     * A statement to delete a single artifact version.
     */
    public String deleteVersion();

    /**
     * A statement to insert a row in the "group_labels" table.
     */
    public String insertGroupLabel();

    /**
     * A statement to insert a row in the "artifact_labels" table.
     */
    public String insertArtifactLabel();

    /**
     * A statement to insert a row in the "version_labels" table.
     */
    public String insertVersionLabel();

    /**
     * A statement to insert a row in the "references" table.
     */
    public String insertContentReference();

    /**
     * A statement to select ids of content referencing artifact
     */
    public String selectContentIdsReferencingArtifactBy();

    /**
     * A statement to select global ids of artifact versions with content referencing an artifact
     */
    public String selectGlobalIdsReferencingArtifactBy();

    /**
     * A statement to select GAV info of artifact versions with content referencing an artifact
     */
    public String selectInboundContentReferencesByGAV();

    /**
     * A statement to select the number of artifacts with a given artifactId (should be 0 or 1).
     */
    public String selectArtifactCountById();

    /**
     * A statement to select the number of groups with a given groupId (should be 0 or 1).
     */
    public String selectGroupCountById();

    /**
     * A statement to select the number of content rows for a given content hash.
     */
    public String selectContentCountByHash();

    /**
     * A statement to select the number of artifact rule rows for a given rule type.
     */
    public String selectArtifactRuleCountByType();

    /**
     * A statement to select the number of global rule rows for a given rule type.
     */
    public String selectGlobalRuleCountByType();

    /**
     * A statement to select the bytes of a content row by contentId.
     */
    public String selectContentById();

    /**
     * A statement to select the bytes of a content row by contentHash
     */
    public String selectContentByContentHash();

    /**
     * A statement to delete content that is no longer being referenced by an artifact version.
     */
    public String deleteAllOrphanedContent();

    /**
     * A statement to delete all content
     */
    public String deleteAllContent();

    /**
     * A statement used to insert a row into the groups table.
     */
    public String insertGroup();

    /**
     * A statement used to update information about a group.
     */
    public String updateGroup();

    /**
     * A statement used to delete a row from the groups table.
     */
    public String deleteGroup();

    /**
     * A statement used to delete all rows from the groups table.
     */
    public String deleteAllGroups();

    /**
     * A statement used to select all rows from groups table.
     */
    public String selectGroups();

    /**
     * A statement used to select a single group in groups table by groupId.
     */
    public String selectGroupByGroupId();

    /*
     * The next few statements support globalId and contentId management.
     */

    public String getNextSequenceValue();

    public String selectCurrentSequenceValue();

    public String resetSequenceValue();

    public String insertSequenceValue();

    /*
     * The next few statements support exporting data from the DB.
     */

    public String exportContent();

    public String exportGlobalRules();

    public String exportGroups();

    public String exportGroupRules();

    public String exportArtifactRules();

    public String exportVersionComments();

    public String exportArtifacts();

    public String exportArtifactVersions();

    public String exportBranches();

    /*
     * The next few statements support importing data into the DB.
     */

    public String importContent();

    public String importGlobalRule();

    public String importGroup();

    public String importBranch();

    public String importGroupRule();

    public String importArtifactRule();

    public String importArtifactVersion();

    public String selectMaxContentId();

    public String selectMaxGlobalId();

    public String selectMaxVersionCommentId();

    public String selectContentExists();

    public String selectGlobalIdExists();

    public String selectAllContentCount();

    /*
     * The next few statements support role mappings
     */

    public String insertRoleMapping();

    public String deleteRoleMapping();

    public String deleteAllRoleMappings();

    public String selectRoleMappingByPrincipalId();

    public String selectRoleByPrincipalId();

    public String selectRoleMappings();

    public String countRoleMappings();

    public String updateRoleMapping();

    public String selectRoleMappingCountByPrincipal();

    /*
     * The next few statements support group rule management.
     */

    public String selectGroupRules();

    public String deleteGroupRules();

    public String insertGroupRule();

    public String selectGroupRuleByType();

    public String updateGroupRule();

    public String deleteGroupRule();

    /*
     * The next few statements support downloads.
     */

    public String insertDownload();

    public String selectDownloadContext();

    public String deleteDownload();

    public String deleteExpiredDownloads();

    /*
     * The next few statements support config properties.
     */

    public String selectConfigProperties();

    public String deleteConfigProperty();

    public String insertConfigProperty();

    public String deleteAllConfigProperties();

    public String selectConfigPropertyByName();

    public String selectStaleConfigProperties();

    public String deleteAllContentReferences();

    public String deleteOrphanedContentReferences();

    /*
     * The next statements relate to comments.
     */

    public String insertVersionComment();

    public String selectVersionComments();

    public String deleteVersionComment();

    public String updateVersionComment();

    // ========== Branches ==========

    public String selectGAVByGlobalId();

    public String insertBranch();

    public String upsertBranch();

    public String updateBranch();

    public String selectBranch();

    public String selectBranchVersionNumbers();

    public String selectBranchTip();

    public String selectBranchTipNotDisabled();

    public String updateBranchModifiedTime();

    public String insertBranchVersion();

    public String appendBranchVersion();

    public String deleteBranchVersions();

    public String deleteBranch();

    public String deleteAllBranches();

    public String createDataSnapshot();

    public String restoreFromSnapshot();

    String createOutboxEvent();

    String deleteOutboxEvent();
}
