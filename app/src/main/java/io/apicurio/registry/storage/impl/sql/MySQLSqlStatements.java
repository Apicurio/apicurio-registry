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

/**
 * MySQL implementation of the sql statements interface.  Provides sql statements that
 * are specific to MySQL, where applicable.
 * @author bruno.ariev@gmail.com
 */
public class MySQLSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     */
    public MySQLSqlStatements() { }

    /**
     * @see SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "mysql";
    }

    /**
     * @see SqlStatements#isPrimaryKeyViolation(Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("Duplicate entry");
    }

    /**
     * @see SqlStatements#isForeignKeyViolation(Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("violates foreign key constraint");
    }

    /**
     * @see SqlStatements.core.storage.jdbc.ISqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return String.join(" ",
                "SELECT COUNT(*) AS count",
                "FROM information_schema.tables",
                "WHERE table_name = 'artifacts';"
        );
    }

    /**
     * @see SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return String.join(" ",
                "INSERT IGNORE INTO content",
                "(tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)",
                "VALUES (?, ?, ?, ?, ?, ?);"
        );
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return String.join(" ",
                "INSERT IGNORE INTO artifactreferences",
                "(tenantId, contentId, groupId, artifactId, version, name)",
                "VALUES (?, ?, ?, ?, ?, ?);"
        );
    }

    /**
     * @see SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return String.join(" ",
                "INSERT INTO logconfiguration (logger, loglevel)",
                "VALUES (?, ?)",
                "ON DUPLICATE KEY UPDATE loglevel = ?;"
        );
    }

    /**
     * @see SqlStatements#getNextSequenceValue()
     * MySQL doesn't have an equivalent to Postgres' RETURNING or SQLServer's OUTPUT,
     * so Store Procedure was required to replicate the behavior
     */
    @Override
    public String getNextSequenceValue() {
        return "CALL GetNextSequenceValue(?, ?, 1)";
    }

    /**
     * @see SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return String.join(" ",
                "INSERT INTO sequences (tenantId, name, value)",
                "VALUES (?, ?, ?)",
                "ON DUPLICATE KEY UPDATE value = ?;"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertVersion(boolean)
     * MySQL doesn't handle 'INSERT INTO... VALUES (..., (SELECT...), ...)' well,
     * instead use 'INSERT INTO... SELECT...'
     */
    @Override
    public String insertVersion(boolean firstVersion) {
        if (firstVersion) {
            return String.join(" ",
                    "INSERT INTO versions",
                    "(globalId, tenantId, groupId, artifactId, version, versionId, state, name, description, createdBy, createdOn, labels, properties, contentId)",
                    "VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
        }
        return String.join(" ",
                String.join(" ",
                        "INSERT INTO versions",
                        "(globalId, tenantId, groupId, artifactId, version, versionId, state, name, description, createdBy, createdOn, labels, properties, contentId)",
                        "SELECT",
                        "? as globalId,", "? as tenantId,", "? as groupId,", "? as artifactId,", "? as version,",
                        "(SELECT MAX(versionId) + 1 FROM versions WHERE tenantId = ? AND groupId = ? AND artifactId = ?) as versionId,",
                        "? as state,", "? as name,", "? as description,", "? as createdBy,", "? as createdOn,",
                        "? as labels,", "? as properties,", "? as contentId"
                )
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#autoUpdateVersionForGlobalId()
     * MySQL doesn't behave well with 'UPDATE... SET version = (SELECT... WHERE) WHERE...',
     * so a CTE was necessary here
     */
    @Override
    public String autoUpdateVersionForGlobalId() {
        return String.join(" ",
                "WITH v as (SELECT versionId  FROM versions WHERE tenantId = ? AND globalId = ?)",
                "UPDATE versions SET version = (SELECT versionId FROM v)",
                "WHERE tenantId = ? AND globalId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactCountById()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroupCountById() {
        return String.join(" ",
                "SELECT COUNT(g.groupId)",
                "FROM artifactgroups g",
                "WHERE g.tenantId = ? AND g.groupId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String insertGroup() {
        return String.join(" ",
                "INSERT INTO artifactgroups",
                "(tenantId, groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)",
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    @Override
    public String upsertGroup() {
        return "INSERT IGNORE INTO artifactgroups (tenantId, groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)" +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String updateGroup() {
        return String.join(" ",
                "UPDATE artifactgroups ",
                "SET description = ? , artifactsType = ? , modifiedBy = ? , modifiedOn = ? , properties = ? ",
                "WHERE tenantId = ? AND groupId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String deleteGroup() {
        return String.join(" ",
                "DELETE",
                "FROM artifactgroups ",
                "WHERE tenantId = ? AND groupId = ?"
        );
    }

    /**
     * @see SqlStatements#deleteAllGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String deleteAllGroups() {
        return String.join(" ",
                "DELETE",
                "FROM artifactgroups",
                "WHERE tenantId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return String.join(" ",
                "SELECT g.*",
                "FROM artifactgroups g",
                "WHERE g.tenantId = ?",
                "ORDER BY g.groupId ASC",
                "LIMIT ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroupByGroupId()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroupByGroupId() {
        return String.join(" ",
                "SELECT g.*",
                "FROM artifactgroups g",
                "WHERE g.tenantId = ? AND g.groupId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String exportGroups() {
        return String.join(" ",
                "SELECT *",
                "FROM artifactgroups g",
                "WHERE g.tenantId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#importGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String importGroup() {
        return String.join(" ",
                "INSERT INTO artifactgroups",
                "(tenantId, groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)",
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }
}
