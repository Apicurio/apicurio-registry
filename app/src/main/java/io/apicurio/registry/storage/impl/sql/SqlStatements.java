/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import java.util.List;

/**
 * Returns SQL statements used by the JDB artifactStore implementation.  There are different
 * implementations of this interface depending on the database being used.
 * @author eric.wittmann@gmail.com
 */
public interface SqlStatements {

    /**
     * Gets the database type associated with these statements.
     */
    public String dbType();

    /**
     * Returns true if the given exception represents a primary key violation.
     * @param error
     */
    public boolean isPrimaryKeyViolation(Exception error);

    /**
     * Returns true if the given exception represents a foreign key violation.
     * @param error
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
     *
     * @param fromVersion
     * @param toVersion
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
     * A statement used to update the 'latest' column of the 'artifacts' table.
     */
    public String updateArtifactLatest();

    /**
     * A statement used to update the 'latest' column of the 'artifacts' table to the globalId of the highest remaining version.
     */
    public String updateArtifactLatestGlobalId();

    /**
     * A statement used to update the 'version' column of the 'versions' table by globalId.  The value of the "versionId"
     * column is copied into the "version" column.
     */
    public String autoUpdateVersionForGlobalId();

    /**
     * A statement used to insert a row in the versions table.
     */
    public String insertVersion(boolean firstVersion);

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
     * A statement used to select all version #s for a given artifactId.
     */
    public String selectArtifactVersions();

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
     * A statement used to count the total # of versions for all artifact.
     */
    public String selectTotalArtifactVersionsCount();

    /**
     * A statement used to select artifact version metadata by artifactId and version.
     */
    public String selectArtifactVersionMetaData();

    /**
     * A statement to select the content of an artifact version from the versions table by artifactId + version.
     */
    public String selectArtifactVersionContent();

    /**
     * A statement to select the content ids of an artifact for all versions.
     */
    public String selectArtifactContentIds();

    /**
     * A statement to "upsert" a row in the "content" table.
     */
    public String upsertContent();

    /**
     * A statement to update canonicalHash value in a row in the "content" table
     * The only statement that allows to modify an existing row in the "content" table
     */
    public String updateContentCanonicalHash();

    /**
     * A statement to get a single artifact (latest version) content by artifactId.
     */
    public String selectLatestArtifactContent();

    /**
     * A statement to get a single artifact (latest version) meta-data by artifactId.
     */
    public String selectLatestArtifactMetaData();

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
     * A statement to update a single artifact owner.
     */
    public String updateArtifactOwner();

    /**
     * A statement to delete a single artifact rule.
     */
    public String deleteArtifactRule();

    /**
     * A statement to delete all rules for a single artifact.
     */
    public String deleteArtifactRules();

    /**
     * A statement to delete all rules for a all artifacts.
     */
    String deleteAllArtifactRules();

    /**
     * A statement to delete all rules for all artifacts in a groupId.
     */
    public String deleteArtifactRulesByGroupId();

    /**
     * A statement to update the meta-data of a specific artifact version.
     */
    public String updateArtifactVersionMetaData();

    /**
     * A statement to delete all labels for all versions for a single artifact.
     */
    public String deleteLabels();

    /**
     * A statement to delete all labels for a single artifact version by globalId
     */
    public String deleteLabelsByGlobalId();

    /**
     * A statement to delete all labels for all versions for all artifacts in a groupId.
     */
    public String deleteLabelsByGroupId();

    /**
     * A statement to delete all labels for all versions for all artifacts
     */
    String deleteAllLabels();

    /**
     * A statement to delete all properties for all versions for a single artifact.
     */
    public String deleteProperties();

    /**
     * A statement to delete all properties for a single artifact version by globalId
     */
    public String deletePropertiesByGlobalId();

    /**
     * A statement to delete all properties for all versions for all artifacts in a groupId.
     */
    public String deletePropertiesByGroupId();

    /**
     * A statement to delete all properties for all versions for all artifacts
     */
    public String deleteAllProperties();

    /**
     * A statement to delete all versions for a single artifact.
     */
    public String deleteVersions();

    /**
     * A statement to delete all versions for all artifacts in a groupId.
     */
    public String deleteVersionsByGroupId();

    /**
     * A statement to delete all versions for all artifacts.
     */
    String deleteAllVersions();

    /**
     * A statement to delete a single row from the artifacts table by artifactId.
     */
    public String deleteArtifact();

    /**
     * A statement to delete a all artifacts from the artifacts table by groupId.
     */
    public String deleteArtifactsByGroupId();

    /**
     * A statement to delete a all artifacts.
     */
    String deleteAllArtifacts();

    /**
     * A statement to get all artifacts IDs.
     */
    public String selectArtifactIds();

    /**
     * A statement to get an artifact's meta-data by version globalId.
     */
    public String selectArtifactMetaDataByGlobalId();

    /**
     * A statement to update the state of an artifact version (by globalId);
     */
    public String updateArtifactVersionState();

    /**
     * A statement to delete the labels for a single artifact version.
     */
    public String deleteVersionLabels();

    /**
     * A statement to delete the properties for a single artifact version.
     */
    public String deleteVersionProperties();

    /**
     * A statement to delete a single artifact version.
     */
    public String deleteVersion();

    /**
     * A statement to insert a row in the "labels" table.
     */
    public String insertLabel();

    /**
     * A statement to insert a row in the "properties" table.
     */
    public String insertProperty();

    /**
     * A statement to insert a row in the "references" table.
     */
    public String upsertReference();

    /**
     * A statement to select ids of content referencing artifact
     */
    public String selectContentIdsReferencingArtifactBy();

    /**
     * A statement to select global ids of artifact versions with content referencing artifact
     */
    public String selectGlobalIdsReferencingArtifactBy();

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
     * A statement to delete all content owned by a tenantId
     */
    public String deleteAllContent();

    /**
     * A statement to select the log configuration for a given logger name
     */
    public String selectLogConfigurationByLogger();

    /**
     * A statement to "upsert" a row in the "logconfiguration" table
     */
    public String upsertLogConfiguration();

    /**
     * A statement to delete a row in the "logconfiguration" table
     */
    public String deleteLogConfiguration();

    /**
     * A statement to select all rows in the "logconfiguration" table
     */
    public String selectAllLogConfigurations();

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
     * The next few statements support globalId and contentId management, having into account a multitenant environment
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

    public String exportArtifactRules();

    public String exportArtifactVersions();

    /*
     * The next few statements support importing data into the DB.
     */

    public String importContent();

    public String importGlobalRule();

    public String importGroup();

    public String importArtifactRule();

    public String importArtifactVersion();

    public String selectMaxContentId();

    public String selectMaxGlobalId();

    public String selectContentExists();

    public String selectGlobalIdExists();


    /*
     * The next few statements support role mappings
     */

    public String insertRoleMapping();

    public String deleteRoleMapping();

    String deleteAllRoleMappings();

    public String selectRoleMappingByPrincipalId();

    public String selectRoleByPrincipalId();

    public String selectRoleMappings();

    public String updateRoleMapping();

    public String selectRoleMappingCountByPrincipal();


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

    public String selectTenantIdsByConfigModifiedOn();

    public String deleteAllReferences();

    public String deleteOrphanedReferences();
}
