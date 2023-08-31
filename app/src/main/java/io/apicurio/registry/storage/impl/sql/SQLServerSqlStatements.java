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
 * MS SQL Server implementation of the SQL statements interface.  Provides sql statements that
 * are specific to MS SQL Server, where applicable.
 * @author eric.wittmann@gmail.com
 */
public class SQLServerSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     * @param config
     */
    public SQLServerSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "mssql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("Violation of PRIMARY KEY constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("conflicted with the FOREIGN KEY constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements.core.storage.jdbc.ISqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return String.join(" ",
                "MERGE INTO content AS target",
                "USING (VALUES (?, ?, ?, ?, ?, ?)) AS source (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)",
                "ON (target.tenantId = source.tenantId AND target.contentHash = source.contentHash)",
                "WHEN NOT MATCHED THEN",
                    "INSERT (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)",
                    "VALUES (source.tenantId, source.contentId, source.canonicalHash, source.contentHash, source.content, source.artifactreferences);");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return String.join(" ",
                "MERGE INTO sequences AS target",
                "USING (VALUES  (?, ?)) AS source (tenantId, name)",
                "ON (target.tenantId = source.tenantId AND target.name = source.name)",
                "WHEN MATCHED THEN",
                    "UPDATE SET value = target.value + 1",
                "WHEN NOT MATCHED THEN",
                    "INSERT (tenantId, name, value)",
                    "VALUES (source.tenantId, source.name, 1)",
                "OUTPUT INSERTED.value;");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return String.join(" ",
                "MERGE INTO sequences AS target",
                "USING (VALUES (?, ?, ?)) AS source (tenantId, name, value)",
                "ON (target.tenantId = source.tenantId AND target.name = source.name)",
                "WHEN MATCHED THEN",
                    "UPDATE SET value = ?",
                "WHEN NOT MATCHED THEN",
                    "INSERT (tenantId, name, value)",
                    "VALUES (source.tenantId, source.name, source.value)",
                "OUTPUT INSERTED.value;");
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return String.join(" ",
                "MERGE INTO artifactreferences AS target",
                "USING (VALUES (?, ?, ?, ?, ?, ?)) AS source (tenantId, contentId, groupId, artifactId, version, name)",
                "ON (target.tenantId = source.tenantId AND target.contentId = source.contentId AND target.name = source.name)",
                "WHEN NOT MATCHED THEN",
                "INSERT (tenantId, contentId, groupId, artifactId, version, name)",
                "VALUES (source.tenantId, source.contentId, source.groupId, source.artifactId, source.version, source.name);");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactIds()
     */
    @Override
    public String selectArtifactIds() {
        return "SELECT TOP (?) artifactId FROM artifacts WHERE tenantId = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactVersions()
     */
    @Override
    public String selectAllArtifactVersions() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN artifacts a ON a.tenantId = v.tenantId AND a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.tenantId = ? AND a.groupId = ? AND a.artifactId = ? "
                + "ORDER BY v.globalId ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return "SELECT TOP (?) * FROM groups WHERE tenantId = ?"
                + "ORDER BY groupId ASC";
    }

}
