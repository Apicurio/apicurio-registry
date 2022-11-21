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
     * @see SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "mssql";
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
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts'";
    }

    /**
     * @see SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return "INSERT INTO content (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId, contentHash) DO NOTHING";
    }

    /**
     * @see SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return "INSERT INTO logconfiguration (logger, loglevel) VALUES (?, ?) ON CONFLICT (logger) DO UPDATE SET loglevel = ?";
    }

    /**
     * @see SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "INSERT INTO sequences (tenantId, name, value) VALUES (?, ?, 1) ON CONFLICT (tenantId, name) DO UPDATE SET value = sequences.value + 1 RETURNING value";
    }

    /**
     * @see SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (tenantId, name, value) VALUES (?, ?, ?) ON CONFLICT (tenantId, name) DO UPDATE SET value = ?";
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return "INSERT INTO artifactreferences (tenantId, contentId, groupId, artifactId, version, name) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId, contentId, name) DO NOTHING";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectArtifactIds()
     */
    @Override
    public String selectArtifactIds() {
        return "SELECT artifactId FROM artifacts WHERE tenantId = ? TOP ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectAllArtifactVersions()
     */
    @Override
    public String selectAllArtifactVersions() {
        return "SELECT v.*, a.type FROM versions v "
                + "JOIN artifacts a ON a.tenantId = v.tenantId AND a.groupId = v.groupId AND a.artifactId = v.artifactId "
                + "WHERE a.tenantId = ? AND a.groupId = ? AND a.artifactId = ? "
                + "ORDER BY v.globalId ASC TOP ? OFFSET ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectGroups()
     */
    @Override
    public String selectGroups() {
        //TODO pagination?
        return "SELECT g.* FROM groups g WHERE g.tenantId = ?"
                + "ORDER BY g.groupId ASC TOP ?";
    }

}
