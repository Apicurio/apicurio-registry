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
 * Returns SQL statements used by the JDB storage implementation.  There are different
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
    public String updateArtifactLatestVersion();

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
    public String selectArtifactMetaDataByContentHash();

    /**
     * A statement used to select a single row in the versions by artifactId and canonical content hash.
     */
    public String selectArtifactMetaDataByCanonicalHash();

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
     * A statement used to count the total # of versions for an artifact.
     */
    public String selectAllArtifactVersionsCount();

    /**
     * A statement used to select artifact version metadata by artifactId and version.
     */
    public String selectArtifactVersionMetaData();

    /**
     * A statement to select the content of an artifact version from the versions table by artifactId + version.
     */
    public String selectArtifactVersionContent();

    /**
     * A statement to "upsert" a row in the "content" table.
     */
    public String upsertContent();

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
     * A statement to delete a single artifact rule.
     */
    public String deleteArtifactRule();

    /**
     * A statement to delete all rules for a single artifact.
     */
    public String deleteArtifactRules();

    /**
     * A statement to update the meta-data for the most recent (latest) version of an artifact.
     */
    public String updateArtifactMetaDataLatestVersion();

    /**
     * A statement to update the meta-data of a specific artifact version.
     */
    public String updateArtifactVersionMetaData();

    /**
     * A statement to delete all labels for all versions for a single artifact.
     */
    public String deleteLabels();
    public String deleteLabelsByGlobalId();

    
    /**
     * A statement to delete all properties for all versions for a single artifact.
     */
    public String deleteProperties();
    public String deletePropertiesByGlobalId();

    /**
     * A statement to delete all versions for a single artifact.
     */
    public String deleteVersions();

    /**
     * A statement to delete a single row from the artifacts table by artifactId.
     */
    public String deleteArtifact();

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

}
