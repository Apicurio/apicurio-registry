package io.apicurio.registry.storage.impl.sql;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared base class for all sql statements.
 */
public abstract class CommonSqlStatements implements SqlStatements {

    /**
     * Constructor.
     */
    public CommonSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#databaseInitialization()
     */
    @Override
    public List<String> databaseInitialization() {
        DdlParser parser = new DdlParser();
        try (InputStream input = getClass().getResourceAsStream(dbType() + ".ddl")) {
            if (input == null) {
                throw new RuntimeException("DDL not found for dbtype: " + dbType());
            }
            return parser.parse(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#databaseUpgrade(int, int)
     */
    @Override
    public List<String> databaseUpgrade(int fromVersion, int toVersion) {
        List<String> statements = new ArrayList<>();
        DdlParser parser = new DdlParser();

        for (int version = fromVersion + 1; version <= toVersion; version++) {
            try (InputStream input = getClass().getResourceAsStream("upgrades/" + version + "/" + dbType() + ".upgrade.ddl")) {
                statements.addAll(parser.parse(input));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return statements;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getDatabaseVersion()
     */
    @Override
    public String getDatabaseVersion() {
        return "SELECT a.propValue FROM apicurio a WHERE a.propName = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertGlobalRule()
     */
    @Override
    public String insertGlobalRule() {
        return "INSERT INTO global_rules (type, configuration) VALUES (?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGlobalRules()
     */
    @Override
    public String selectGlobalRules() {
        return "SELECT r.type FROM global_rules r ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGlobalRuleByType()
     */
    @Override
    public String selectGlobalRuleByType() {
        return "SELECT r.* FROM global_rules r WHERE r.type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGlobalRule()
     */
    @Override
    public String deleteGlobalRule() {
        return "DELETE FROM global_rules WHERE type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGlobalRules()
     */
    @Override
    public String deleteGlobalRules() {
        return "DELETE FROM global_rules";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateGlobalRule()
     */
    @Override
    public String updateGlobalRule() {
        return "UPDATE global_rules SET configuration = ? WHERE type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertArtifact()
     */
    @Override
    public String insertArtifact() {
        return "INSERT INTO artifacts (groupId, artifactId, type, owner, createdOn, modifiedBy, modifiedOn, name, description, labels) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }


    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#autoUpdateVersionForGlobalId()
     */
    @Override
    public String autoUpdateVersionForGlobalId() {
        return "UPDATE versions SET version = versionOrder WHERE globalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertVersion(boolean)
     */
    @Override
    public String insertVersion(boolean firstVersion) {
        // TODO: Use COALESCE to unify into a single query.
        String query;
        if (firstVersion) {
            query = "INSERT INTO versions (globalId, groupId, artifactId, version, versionOrder, state, name, description, owner, createdOn, labels, contentId) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            // NOTE: Duplicated value of versionOrder is prevented by UQ_versions_2 constraint.
            query = "INSERT INTO versions (globalId, groupId, artifactId, version, versionOrder, state, name, description, owner, createdOn, labels, contentId) VALUES (?, ?, ?, ?, (SELECT MAX(versionOrder) + 1 FROM versions WHERE groupId = ? AND artifactId = ?), ?, ?, ?, ?, ?, ?, ?)";
        }
        return query;
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionMetaDataByGlobalId()
     */
    @Override
    public String selectArtifactVersionMetaDataByGlobalId() {
        return "SELECT v.*, a.type "
                + "FROM versions v "
                + "JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE v.globalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersions()
     */
    @Override
    public String selectArtifactVersions() {
        return "SELECT version FROM versions WHERE groupId = ? AND artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionMetaData()
     */
    @Override
    public String selectArtifactVersionMetaData() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionMetaDataByContentHash()
     */
    @Override
    public String selectArtifactVersionMetaDataByContentHash() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN content c ON v.contentId = c.contentId "
                + "JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND c.contentHash = ? ORDER BY v.globalId DESC";
    }

    @Override
    public String selectArtifactVersionMetaDataByContentId() {
        return "SELECT a.*, v.contentId, v.globalId, v.version, v.versionOrder, v.state, v.name, v.description, v.labels, v.owner AS modifiedBy, v.createdOn AS modifiedOn "
                + "FROM versions v "
                + "JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE v.contentId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionMetaDataByCanonicalHash()
     */
    @Override
    public String selectArtifactVersionMetaDataByCanonicalHash() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN content c ON v.contentId = c.contentId "
                + "JOIN artifacts a ON v.groupId = a.groupId AND v.artifactId = a.artifactId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND c.canonicalHash = ? ORDER BY v.globalId DESC";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionContentByGlobalId()
     */
    @Override
    public String selectArtifactVersionContentByGlobalId() {
        return "SELECT v.globalId, v.version, v.versionOrder, v.contentId, c.content, c.contentType, c.refs FROM versions v "
                + "JOIN content c ON v.contentId = c.contentId "
                + "WHERE v.globalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionContent()
     */
    @Override
    public String selectArtifactVersionContent() {
        return "SELECT v.globalId, v.version, v.versionOrder, c.contentId, c.content, c.contentType, c.refs FROM versions v "
                + "JOIN content c ON v.contentId = c.contentId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactVersionContent()
     */
    @Override
    public String selectArtifactContentIds() {
        return "SELECT v.contentId FROM versions v WHERE v.groupId = ? AND v.artifactId = ? AND v.state != 'DISABLED' ORDER BY v.versionOrder";
    }


    @Override
    public String selectArtifactMetaData() {
        return "SELECT a.* FROM artifacts a WHERE a.groupId = ? AND a.artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectContentIdByHash()
     */
    @Override
    public String selectContentIdByHash() {
        return "SELECT c.contentId FROM content c WHERE c.contentHash = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactRules()
     */
    @Override
    public String selectArtifactRules() {
        return "SELECT r.* FROM artifact_rules r WHERE r.groupId = ? AND r.artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertArtifactRule()
     */
    @Override
    public String insertArtifactRule() {
        return "INSERT INTO artifact_rules (groupId, artifactId, type, configuration) VALUES (?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactRuleByType()
     */
    @Override
    public String selectArtifactRuleByType() {
        return "SELECT r.* FROM artifact_rules r WHERE r.groupId = ? AND r.artifactId = ? AND r.type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactRule()
     */
    @Override
    public String updateArtifactRule() {
        return "UPDATE artifact_rules SET configuration = ? WHERE groupId = ? AND artifactId = ? AND type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactModifiedByOn()
     */
    @Override
    public String updateArtifactModifiedByOn() {
        return "UPDATE artifacts SET modifiedBy = ?, modifiedOn = ? WHERE groupId = ? AND artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactOwner()
     */
    @Override
    public String updateArtifactOwner() {
        return "UPDATE artifacts SET owner = ? WHERE groupId = ? AND artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactName()
     */
    @Override
    public String updateArtifactName() {
        return "UPDATE artifacts SET name = ? WHERE groupId = ? AND artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactDescription()
     */
    @Override
    public String updateArtifactDescription() {
        return "UPDATE artifacts SET description = ? WHERE groupId = ? AND artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactLabels()
     */
    @Override
    public String updateArtifactLabels() {
        return "UPDATE artifacts SET labels = ? WHERE groupId = ? AND artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteArtifactRule()
     */
    @Override
    public String deleteArtifactRule() {
        return "DELETE FROM artifact_rules WHERE groupId = ? AND artifactId = ? AND type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteArtifactRules()
     */
    @Override
    public String deleteArtifactRules() {
        return "DELETE FROM artifact_rules WHERE groupId = ? AND artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteArtifactRulesByGroupId()
     */
    @Override
    public String deleteArtifactRulesByGroupId() {
        return "DELETE FROM artifact_rules WHERE groupId = ?";
    }

    /**
     * @see SqlStatements#deleteAllArtifactRules()
     */
    @Override
    public String deleteAllArtifactRules() {
        return "DELETE FROM artifact_rules";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionMetaData()
     */
    @Override
    public String updateArtifactVersionMetaData() {
        return "UPDATE versions SET name = ?, description = ?, labels = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionNameByGAV()
     */
    @Override
    public String updateArtifactVersionNameByGAV() {
        return "UPDATE versions SET name = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionDescriptionByGAV()
     */
    @Override
    public String updateArtifactVersionDescriptionByGAV() {
        return "UPDATE versions SET description = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionOwnerByGAV()
     */
    @Override
    public String updateArtifactVersionOwnerByGAV() {
        return "UPDATE versions SET owner = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionStateByGAV()
     */
    @Override
    public String updateArtifactVersionStateByGAV() {
        return "UPDATE versions SET state = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionLabelsByGAV()
     */
    @Override
    public String updateArtifactVersionLabelsByGAV() {
        return "UPDATE versions SET labels = ? WHERE groupId = ? AND artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateArtifactVersionState()
     */
    @Override
    public String updateArtifactVersionState() {
        return "UPDATE versions SET state = ? WHERE globalId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGroupLabelsByGroupId()
     */
    @Override
    public String deleteGroupLabelsByGroupId() {
        return "DELETE FROM group_labels WHERE groupId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteArtifactLabels()
     */
    @Override
    public String deleteArtifactLabels() {
        return "DELETE FROM artifact_labels WHERE groupId = ? AND artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteVersionLabelsByGlobalId()
     */
    @Override
    public String deleteVersionLabelsByGlobalId() {
        return "DELETE FROM version_labels WHERE globalId = ?";
    }

    @Override
    public String deleteVersionLabelsByAll() {
        return "DELETE FROM version_labels WHERE globalId IN (SELECT globalId FROM versions)";
    }

    @Override
    public String deleteAllVersionComments() {
        return "DELETE FROM version_comments WHERE globalId IN (SELECT globalId FROM versions)";
    }

    @Override
    public String deleteAllVersions() {
        return "DELETE FROM versions";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteArtifact()
     */
    @Override
    public String deleteArtifact() {
        return "DELETE FROM artifacts WHERE groupId = ? AND artifactId = ?";
    }

    @Override
    public String deleteArtifactsByGroupId() {
        return "DELETE FROM artifacts WHERE groupId = ?";
    }

    @Override
    public String deleteAllArtifacts() {
        return "DELETE FROM artifacts";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactIds()
     */
    @Override
    public String selectArtifactIds() {
        return "SELECT artifactId FROM artifacts LIMIT ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactMetaDataByGlobalId()
     */
    @Override
    public String selectArtifactMetaDataByGlobalId() {
        return "SELECT a.groupId, a.artifactId, a.type, a.owner, a.createdOn, v.contentId, v.globalId, v.version, v.versionOrder, v.state, v.name, v.description, v.labels, v.owner AS modifiedBy, v.createdOn AS modifiedOn "
                + "FROM artifacts a "
                + "JOIN versions v ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE v.globalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteVersion()
     */
    @Override
    public String deleteVersion() {
        return "DELETE FROM versions WHERE groupId = ? AND artifactId = ? AND version = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteVersionLabelsByGAV()
     */
    @Override
    public String deleteVersionLabelsByGAV() {
        return "DELETE FROM version_labels WHERE globalId IN (SELECT v.globalId FROM versions v WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertVersionLabel()
     */
    @Override
    public String insertVersionLabel() {
        return "INSERT INTO version_labels (globalId, labelKey, labelValue) VALUES (?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertArtifactLabel()
     */
    @Override
    public String insertArtifactLabel() {
        return "INSERT INTO artifact_labels (groupId, artifactId, labelKey, labelValue) VALUES (?, ?, ?, ?)";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertGroupLabel()
     */
    @Override
    public String insertGroupLabel() {
        return "INSERT INTO group_labels (groupId, labelKey, labelValue) VALUES (?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactVersions()
     */
    @Override
    public String selectAllArtifactVersions() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.groupId = ? AND a.artifactId = ? "
                + "ORDER BY v.globalId ASC LIMIT ? OFFSET ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactCount()
     */
    @Override
    public String selectAllArtifactCount() {
        return "SELECT COUNT(a.artifactId) FROM artifacts a ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactVersionsCount()
     */
    @Override
    public String selectAllArtifactVersionsCount() {
        return "SELECT COUNT(v.globalId) FROM versions v "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.groupId = ? AND a.artifactId = ? ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectActiveArtifactVersionsCount()
     */
    @Override
    public String selectActiveArtifactVersionsCount() {
        return "SELECT COUNT(v.globalId) FROM versions v "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.groupId = ? AND a.artifactId = ? AND v.state != 'DISABLED'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectTotalArtifactVersionsCount()
     */
    @Override
    public String selectTotalArtifactVersionsCount() {
        return "SELECT COUNT(v.globalId) FROM versions v "
                + "JOIN artifacts a ON a.groupId = v.groupId AND a.artifactId = v.artifactId ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactCountById()
     */
    @Override
    public String selectArtifactCountById() {
        return "SELECT COUNT(a.artifactId) FROM artifacts a WHERE a.groupId = ? AND a.artifactId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactCountById()
     */
    @Override
    public String selectGroupCountById() {
        return "SELECT COUNT(g.groupId) FROM groups g WHERE g.groupId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactRuleCountByType()
     */
    @Override
    public String selectArtifactRuleCountByType() {
        return "SELECT COUNT(r.type) FROM artifact_rules r WHERE r.groupId = ? AND r.artifactId = ? AND r.type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGlobalRuleCountByType()
     */
    @Override
    public String selectGlobalRuleCountByType() {
        return "SELECT COUNT(r.type) FROM global_rules r WHERE r.type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectContentCountByHash()
     */
    @Override
    public String selectContentCountByHash() {
        return "SELECT COUNT(c.contentId) FROM content c WHERE c.contentHash = ? ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectContentById()
     */
    @Override
    public String selectContentById() {
        return "SELECT c.content, c.contentType, c.refs FROM content c "
                + "WHERE c.contentId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectContentByContentHash()
     */
    @Override
    public String selectContentByContentHash() {
        return "SELECT c.content, c.contentType, c.refs FROM content c "
                + "WHERE c.contentHash = ?";
    }

    @Override
    public String deleteAllOrphanedContent() {
        // TODO This may be too slow

        return "DELETE FROM content WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = contentId )";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteAllContent()
     */
    @Override
    public String deleteAllContent() {
        return "DELETE FROM content";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateContentCanonicalHash()
     */
    @Override
    public String updateContentCanonicalHash() {
        return "UPDATE content SET canonicalHash = ? WHERE contentId = ? AND contentHash = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertGroup()
     */
    @Override
    public String insertGroup() {
        return "INSERT INTO groups (groupId, description, artifactsType, owner, createdOn, modifiedBy, modifiedOn, labels) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateGroup()
     */
    @Override
    public String updateGroup() {
        return "UPDATE groups SET description = ? , modifiedBy = ? , modifiedOn = ? , labels = ? WHERE groupId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGroup()
     */
    @Override
    public String deleteGroup() {
        return "DELETE FROM groups WHERE groupId = ?";
    }

    /**
     * @see SqlStatements#deleteAllGroups()
     */
    @Override
    public String deleteAllGroups() {
        return "DELETE FROM groups ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return "SELECT g.* FROM groups g "
                + "ORDER BY g.groupId ASC LIMIT ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroupByGroupId()
     */
    @Override
    public String selectGroupByGroupId() {
        return "SELECT g.* FROM groups g WHERE g.groupId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportArtifactRules()
     */
    @Override
    public String exportArtifactRules() {
        return "SELECT * FROM artifact_rules r";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportArtifactVersions()
     */
    @Override
    public String exportArtifactVersions() {
        return "SELECT v.*, a.type " +
                "FROM versions v " +
                "JOIN artifacts a ON  v.groupId = a.groupId AND v.artifactId = a.artifactId ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportVersionComments()
     */
    @Override
    public String exportVersionComments() {
        return "SELECT * FROM version_comments c ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportContent()
     */
    @Override
    public String exportContent() {
        return "SELECT c.contentId, c.canonicalHash, c.contentHash, c.content, c.refs FROM content c ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportGlobalRules()
     */
    @Override
    public String exportGlobalRules() {
        return "SELECT * FROM global_rules r ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportGroups()
     */
    @Override
    public String exportGroups() {
        return "SELECT * FROM groups g ";
    }


    @Override
    public String exportArtifactBranches() {
        return "SELECT * FROM artifact_branches ab";
    }


    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importArtifactRule()
     */
    @Override
    public String importArtifactRule() {
        return "INSERT INTO artifact_rules (groupId, artifactId, type, configuration) VALUES (?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importArtifactVersion()
     */
    @Override
    public String importArtifactVersion() {
        return "INSERT INTO versions (globalId, groupId, artifactId, version, versionOrder, state, name, description, owner, createdOn, labels, contentId) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importContent()
     */
    @Override
    public String importContent() {
        return "INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES (?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importGlobalRule()
     */
    @Override
    public String importGlobalRule() {
        return "INSERT INTO global_rules (type, configuration) VALUES (?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importGroup()
     */
    @Override
    public String importGroup() {
        return "INSERT INTO groups (groupId, description, artifactsType, owner, createdOn, modifiedBy, modifiedOn, labels) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectMaxContentId()
     */
    @Override
    public String selectMaxContentId() {
        return "SELECT MAX(contentId) FROM content ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectMaxGlobalId()
     */
    @Override
    public String selectMaxGlobalId() {
        return "SELECT MAX(globalId) FROM versions ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectMaxVersionCommentId()
     */
    @Override
    public String selectMaxVersionCommentId() {
        return "SELECT MAX(commentId) FROM version_comments ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectContentExists()
     */
    @Override
    public String selectContentExists() {
        return "SELECT COUNT(contentId) FROM content WHERE contentId = ? ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGlobalIdExists()
     */
    @Override
    public String selectGlobalIdExists() {
        return "SELECT COUNT(globalId) FROM versions WHERE globalId = ? ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertRoleMapping()
     */
    @Override
    public String insertRoleMapping() {
        return "INSERT INTO acls (principalId, role, principalName) VALUES (?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteRoleMapping()
     */
    @Override
    public String deleteRoleMapping() {
        return "DELETE FROM acls WHERE principalId = ?";
    }

    /**
     * @see SqlStatements#deleteAllRoleMappings()
     */
    @Override
    public String deleteAllRoleMappings() {
        return "DELETE FROM acls ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectRoleMappingByPrincipalId()
     */
    @Override
    public String selectRoleMappingByPrincipalId() {
        return "SELECT a.* FROM acls a WHERE a.principalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectRoleByPrincipalId()
     */
    @Override
    public String selectRoleByPrincipalId() {
        return "SELECT a.role FROM acls a WHERE a.principalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectRoleMappings()
     */
    @Override
    public String selectRoleMappings() {
        return "SELECT a.* FROM acls a ";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#countRoleMappings()
     */
    @Override
    public String countRoleMappings() {
        return "SELECT count(a.principalId) FROM acls a ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateRoleMapping()
     */
    @Override
    public String updateRoleMapping() {
        return "UPDATE acls SET role = ? WHERE principalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectRoleMappingCountByPrincipal()
     */
    @Override
    public String selectRoleMappingCountByPrincipal() {
        return "SELECT COUNT(a.principalId) FROM acls a WHERE a.principalId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertDownload()
     */
    @Override
    public String insertDownload() {
        return "INSERT INTO downloads (downloadId, expires, context) VALUES (?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectDownloadContext()
     */
    @Override
    public String selectDownloadContext() {
        return "SELECT d.context FROM downloads d WHERE d.downloadId = ? AND expires > ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteDownload()
     */
    @Override
    public String deleteDownload() {
        return "DELETE FROM downloads WHERE downloadId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteExpiredDownloads()
     */
    @Override
    public String deleteExpiredDownloads() {
        return "DELETE FROM downloads WHERE expires < ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectConfigProperties()
     */
    @Override
    public String selectConfigProperties() {
        return "SELECT c.* FROM config c ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectConfigPropertyByName()
     */
    @Override
    public String selectConfigPropertyByName() {
        return "SELECT c.* FROM config c WHERE c.propName = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteConfigProperty()
     */
    @Override
    public String deleteConfigProperty() {
        return "DELETE FROM config WHERE propName = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertConfigProperty()
     */
    @Override
    public String insertConfigProperty() {
        return "INSERT INTO config (propName, propValue, modifiedOn) VALUES (?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteAllConfigProperties()
     */
    @Override
    public String deleteAllConfigProperties() {
        return "DELETE FROM config ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectStaleConfigProperties()
     */
    @Override
    public String selectStaleConfigProperties() {
        return "SELECT * FROM config c WHERE c.modifiedOn >= ?";
    }

    @Override
    public String deleteAllContentReferences() {
        return "DELETE FROM content_references ";
    }

    @Override
    public String deleteOrphanedContentReferences() {
        return "DELETE FROM content_references WHERE NOT EXISTS (SELECT 1 FROM versions v WHERE v.contentId = contentId)";
    }

    @Override
    public String selectContentIdsReferencingArtifactBy() {
        return "SELECT contentId FROM content_references WHERE groupId=? AND artifactId=? AND version=?";
    }

    @Override
    public String selectGlobalIdsReferencingArtifactBy() {
        return "SELECT DISTINCT v.globalId FROM versions v JOIN content_references ar ON v.contentId=ar.contentId WHERE ar.groupId=? AND ar.artifactId=? AND ar.version=?";
    }

    @Override
    public String selectInboundContentReferencesByGAV() {
        return "SELECT DISTINCT v.groupId, v.artifactId, v.version, ar.name as name FROM versions v JOIN content_references ar ON v.contentId=ar.contentId WHERE ar.groupId=? AND ar.artifactId=? AND ar.version=?";
    }

    @Override
    public String insertSequenceValue() {
        return "INSERT INTO sequences (seqName, seqValue) VALUES (?, ?)";
    }

    @Override
    public String selectCurrentSequenceValue() {
        return "SELECT seqValue FROM sequences WHERE seqName = ? ";
    }

    @Override
    public String insertVersionComment() {
        return "INSERT INTO version_comments (commentId, globalId, owner, createdOn, cvalue) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    public String selectVersionComments() {
        return "SELECT c.* "
                + "FROM version_comments c JOIN versions v ON v.globalId = c.globalId "
                + "WHERE v.groupId = ? AND v.artifactId = ? AND v.version = ? ORDER BY c.createdOn DESC";
    }

    @Override
    public String deleteVersionComment() {
        return "DELETE FROM version_comments WHERE globalId = ? AND commentId = ? AND owner = ?";
    }

    @Override
    public String updateVersionComment() {
        return "UPDATE version_comments SET cvalue = ? WHERE globalId = ? AND commentId = ? AND owner = ?";
    }


    @Override
    public String selectGAVByGlobalId() {
        return "SELECT groupId, artifactId, version FROM versions " +
                "WHERE globalId = ?";
    }


    @Override
    public String selectArtifactBranches() {
        return "SELECT ab.groupId, ab.artifactId, ab.branchId, ab.branchOrder, ab.version FROM artifact_branches ab " +
                "WHERE ab.groupId = ? AND ab.artifactId = ?";
    }


    @Override
    public String selectArtifactBranchOrdered() {
        return "SELECT ab.groupId, ab.artifactId, ab.branchId, ab.branchOrder, ab.version FROM artifact_branches ab " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? " +
                "ORDER BY ab.branchOrder DESC";
    }


    @Override
    public String selectArtifactBranchOrderedNotDisabled() {
        return "SELECT ab.groupId, ab.artifactId, ab.branchId, ab.branchOrder, ab.version FROM artifact_branches ab " +
                "JOIN versions v ON ab.groupId = v.groupId AND ab.artifactId = v.artifactId AND ab.version = v.version " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? AND v.state != 'DISABLED' " +
                "ORDER BY ab.branchOrder DESC";
    }


    @Override
    public String insertArtifactBranch() {
        // Note: Duplicated value of branchOrder is prevented by primary key
        return "INSERT INTO artifact_branches (groupId, artifactId, branchId, branchOrder, version) " +
                "SELECT ?, ?, ?, COALESCE(MAX(ab.branchOrder), 0) + 1, ? FROM artifact_branches ab " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ?";
    }


    @Override
    public String selectArtifactBranchTip() {
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? " +
                "ORDER BY ab.branchOrder DESC LIMIT 1";
    }


    @Override
    public String selectArtifactBranchTipNotDisabled() {
        return "SELECT ab.groupId, ab.artifactId, ab.version FROM artifact_branches ab " +
                "JOIN versions v ON ab.groupId = v.groupId AND ab.artifactId = v.artifactId AND ab.version = v.version " +
                "WHERE ab.groupId = ? AND ab.artifactId = ? AND ab.branchId = ? AND v.state != 'DISABLED' " +
                "ORDER BY ab.branchOrder DESC LIMIT 1";
    }


    @Override
    public String deleteArtifactBranch() {
        return "DELETE FROM artifact_branches " +
                "WHERE groupId = ? AND artifactId = ? AND branchId = ?";
    }


    @Override
    public String deleteAllArtifactBranchesInArtifact() {
        return "DELETE FROM artifact_branches " +
                "WHERE groupId = ? AND artifactId = ?";
    }


    @Override
    public String deleteAllArtifactBranchesInGroup() {
        return "DELETE FROM artifact_branches " +
                "WHERE groupId = ?";
    }


    @Override
    public String deleteAllArtifactBranches() {
        return "DELETE FROM artifact_branches";
    }


    @Override
    public String deleteVersionInArtifactBranches() {
        return "DELETE FROM artifact_branches " +
                "WHERE groupId = ? AND artifactId = ? AND version = ?";
    }


    @Override
    public String selectVersionsWithoutArtifactBranch() {
        return "SELECT DISTINCT v.groupId, v.artifactId, v.version FROM versions v " +
                "LEFT JOIN artifact_branches ab ON v.groupId = ab.groupId AND v.artifactId = ab.artifactId AND v.version = ab.version " +
                "WHERE v.groupId = ? AND v.artifactId = ? AND ab.branchId IS NULL";
    }


    @Override
    public String importArtifactBranch() {
        return "INSERT INTO artifact_branches (groupId, artifactId, branchId, branchOrder, version) " +
                "VALUES(?, ?, ?, ?, ?)";
    }
}
