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
 * H2 implementation of the sql statements interface.  Provides sql statements that
 * are specific to PostgreSQL, where applicable.
 * @author eric.wittmann@gmail.com
 */
public class PostgreSQLSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     * @param config
     */
    public PostgreSQLSqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "postgresql";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("violates unique constraint");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("violates foreign key constraint");
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
        return "INSERT INTO content (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId, contentHash) DO NOTHING";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return "INSERT INTO logconfiguration (logger, loglevel) VALUES (?, ?) ON CONFLICT (logger) DO UPDATE SET loglevel = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "INSERT INTO sequences (tenantId, name, value) VALUES (?, ?, 1) ON CONFLICT (tenantId, name) DO UPDATE SET value = sequences.value + 1 RETURNING value";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectCurrentSequenceValue()
     */
    @Override
    public String selectCurrentSequenceValue() {
        return "SELECT value FROM sequences WHERE name = ? AND tenantId = ? ";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "INSERT INTO sequences (tenantId, name, value) VALUES (?, ?, ?) ON CONFLICT (tenantId, name) DO UPDATE SET value = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertSequenceValue()
     */
    @Override
    public String insertSequenceValue() {
        return "INSERT INTO sequences (tenantId, name, value) VALUES (?, ?, ?)";
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return "INSERT INTO artifactreferences (tenantId, contentId, groupId, artifactId, version, name) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId, contentId, name) DO NOTHING";
    }

}
