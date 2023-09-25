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
        return error.getMessage().contains("violates unique constraint");
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
                "WHERE table_name = 'artifacts';");
    }

    /**
     * @see SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return String.join(" ",
                "INSERT IGNORE INTO content",
                "(tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)",
                "VALUES (?, ?, ?, ?, ?, ?);");
    }

    /**
     * @see SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return String.join(" ",
                "INSERT INTO logconfiguration (logger, loglevel)",
                "VALUES (?, ?)",
                "ON DUPLICATE KEY UPDATE loglevel = ?;");
    }

    /**
     * @see SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        //TODO: Create a Stored Procedure to return the VALUE after the UPSERT
        return String.join(" ",
                "INSERT INTO sequences (tenantId, name, value)",
                        "VALUES (?, ?, 1)",
                        "ON DUPLICATE KEY UPDATE value = sequences.value + 1;");
    }

    /**
     * @see SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return String.join(" ",
                "INSERT INTO sequences (tenantId, name, value)",
                "VALUES (?, ?, ?)",
                "ON DUPLICATE KEY UPDATE value = ?;");
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return String.join(" ",
                "INSERT IGNORE INTO artifactreferences",
                "(tenantId, contentId, groupId, artifactId, version, name)",
                "VALUES (?, ?, ?, ?, ?, ?);");
    }

}
