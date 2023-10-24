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
    public MySQLSqlStatements() {
    }

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
                "(contentId, canonicalHash, contentHash, content, artifactreferences)",
                "VALUES (?, ?, ?, ?, ?, ?);"
        );
    }

    /**
     * @see SqlStatements#getNextSequenceValue()
     * MySQL doesn't have an equivalent to Postgres' RETURNING or SQLServer's OUTPUT,
     * so Store Procedure was required to replicate the behavior
     */
    @Override
    public String getNextSequenceValue() {
        return "CALL GetNextSequenceValue(?, 1)";
    }

    /**
     * @see SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (name, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = ?";
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return String.join(" ",
                "INSERT IGNORE INTO artifactreferences",
                "(contentId, groupId, artifactId, version, name)",
                "VALUES (?, ?, ?, ?, ?);"
        );
    }


    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactCountById()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroupCountById() {
        return "SELECT COUNT(g.groupId) FROM artifactgroups g WHERE g.groupId = ?";
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
                "(groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)",
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#updateGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String updateGroup() {
        return String.join(" ",
                "UPDATE artifactgroups",
                "SET description = ? , artifactsType = ? , modifiedBy = ? , modifiedOn = ? , properties = ?",
                "WHERE groupId = ?"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#deleteGroup()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String deleteGroup() {
        return  "DELETE FROM artifactgroups WHERE groupId = ?";
    }

    /**
     * @see SqlStatements#deleteAllGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String deleteAllGroups() {
        return "DELETE FROM artifactgroups";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return "SELECT g.* FROM artifactgroups g ORDER BY g.groupId ASC LIMIT ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroupByGroupId()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String selectGroupByGroupId() {
        return "SELECT g.* FROM artifactgroups g WHERE g.groupId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#exportGroups()
     * In MySQL, 'groups' is a reserved keyword. We've changed it to artifactgroups,
     * so one doesn't have to bother enclosing the table name with ` backticks
     */
    @Override
    public String exportGroups() {
        return "SELECT * FROM artifactgroups g";
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
                "(groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)",
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#autoUpdateVersionForGlobalId()
     * MySQL doesn't behave well with 'UPDATE... SET version = (SELECT... WHERE) WHERE...',
     * so a CTE was necessary here
     */
    @Override
    public String autoUpdateVersionForGlobalId() {
        return "UPDATE versions SET version = versionId WHERE globalId = ?";
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
                    "(globalId, groupId, artifactId, version, versionId, state, name, description, createdBy, createdOn, labels, properties, contentId)",
                    "VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
        }
        return String.join(" ",
                String.join(" ",
                        "INSERT INTO versions",
                        "(globalId, groupId, artifactId, version, versionId, state, name, description, createdBy, createdOn, labels, properties, contentId)",
                        "SELECT",
                        "? as globalId,", "? as groupId,", "? as artifactId,", "? as version,",
                        "(SELECT MAX(versionId) + 1 FROM versions WHERE groupId = ? AND artifactId = ?) as versionId,",
                        "? as state,", "? as name,", "? as description,", "? as createdBy,", "? as createdOn,",
                        "? as labels,", "? as properties,", "? as contentId"
                )
        );
    }





}
